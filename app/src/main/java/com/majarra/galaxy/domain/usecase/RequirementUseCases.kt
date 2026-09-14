package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.BoqDocument
import com.majarra.galaxy.data.local.BoqLine
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.RequirementItem
import com.majarra.galaxy.domain.model.AlertType
import com.majarra.galaxy.domain.model.RequirementStatus
import com.majarra.galaxy.domain.model.RequirementType
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.BoqRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import javax.inject.Inject

/** بند مسودة قبل الإنشاء */
data class DraftItem(
    val inventoryItemId: Long? = null,
    val description: String,
    val quantity: Int = 1
)

/** مسودة احتياج كامل */
data class RequirementDraft(
    val siteId: Long,
    val type: RequirementType,
    val notes: String,
    val items: List<DraftItem>
)

/** إنشاء احتياج + بنوده + توليد BOQ تلقائيًا */
class CreateRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val auditRepo: AuditRepository,
    private val generateBoq: GenerateBoqUseCase
) {
    suspend operator fun invoke(draft: RequirementDraft): Long {
        require(draft.items.isNotEmpty()) { "الاحتياج يحتاج بندًا واحدًا على الأقل" }
        val rid = reqRepo.insert(
            Requirement(
                siteId = draft.siteId,
                type = draft.type,
                status = RequirementStatus.PENDING,
                notes = draft.notes
            )
        )
        reqRepo.insertItems(
            draft.items.map {
                RequirementItem(
                    requirementId = rid,
                    inventoryItemId = it.inventoryItemId,
                    description = it.description,
                    quantity = it.quantity.coerceAtLeast(1)
                )
            }
        )
        auditRepo.log("CREATE", "Requirement", rid, draft.type.label)
        generateBoq(rid)
        return rid
    }
}

/** نتيجة تقييم بند مقابل المخزون */
data class StockAssessment(
    val item: RequirementItem,
    val available: Int,
    val shortage: Int
)

/** مقارنة تلقائية بين بنود الاحتياج والمخزون المركزي وإظهار النقص */
class AssessRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val inventoryRepo: InventoryRepository
) {
    suspend operator fun invoke(requirementId: Long): List<StockAssessment> {
        return reqRepo.getItems(requirementId).map { item ->
            val inv = item.inventoryItemId?.let { inventoryRepo.getById(it) }
            val available = inv?.quantity ?: 0
            StockAssessment(
                item = item,
                available = available,
                shortage = if (item.inventoryItemId == null) 0 else (item.quantity - available).coerceAtLeast(0)
            )
        }
    }
}

/** نتيجة عملية الصرف */
sealed class FulfillResult {
    object Success : FulfillResult()
    data class Failure(val reason: String) : FulfillResult()
    object NotFound : FulfillResult()
}

/**
 * صرف الاحتياج — قاعدة العمل رقم 3:
 * خصم تلقائي من المخزون + تسجيل في Audit Log + تنبيه نقص إن وصل للحد الأدنى.
 */
class FulfillRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val inventoryRepo: InventoryRepository,
    private val auditRepo: AuditRepository,
    private val alertRepo: AlertRepository
) {
    suspend operator fun invoke(requirementId: Long): FulfillResult {
        val req = reqRepo.get(requirementId) ?: return FulfillResult.NotFound
        if (req.status == RequirementStatus.FULFILLED) {
            return FulfillResult.Failure("هذا الاحتياج مصروف مسبقًا")
        }
        val items = reqRepo.getItems(requirementId).filter { !it.fulfilled }

        // 1) التحقق المسبق من توفر الكميات — لا خصم جزئي أبدًا
        for (item in items) {
            val invId = item.inventoryItemId ?: continue
            val inv = inventoryRepo.getById(invId) ?: continue
            if (inv.quantity < item.quantity) {
                return FulfillResult.Failure("المخزون غير كافٍ: ${inv.name} (متوفر ${inv.quantity})")
            }
        }

        // 2) الخصم والتسجيل
        for (item in items) {
            val invId = item.inventoryItemId
            if (invId == null) {
                reqRepo.updateItem(item.copy(fulfilled = true))
                continue
            }
            val inv = inventoryRepo.getById(invId) ?: continue
            val newQty = (inv.quantity - item.quantity).coerceAtLeast(0)
            inventoryRepo.update(inv.copy(quantity = newQty))
            reqRepo.updateItem(item.copy(fulfilled = true))
            auditRepo.log("DISPATCH", "InventoryItem", inv.id, "خصم ${item.quantity} من ${inv.name}")

            // قاعدة العمل رقم 4: تنبيه عند وصول الكمية للحد الأدنى
            if (newQty <= inv.minThreshold &&
                !alertRepo.hasUnreadFor(AlertType.LOW_STOCK.name, inv.id)
            ) {
                alertRepo.insert(
                    Alert(
                        type = AlertType.LOW_STOCK,
                        refId = inv.id,
                        message = "نقص مخزون: ${inv.name} — المتبقي $newQty ${inv.unit.label}"
                    )
                )
            }
        }

        reqRepo.update(req.copy(status = RequirementStatus.FULFILLED))
        auditRepo.log("UPDATE", "Requirement", requirementId, "اكتمال الصرف")
        return FulfillResult.Success
    }
}

/** توليد وثيقة BOQ من احتياج (مرة واحدة لكل احتياج) */
class GenerateBoqUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val boqRepo: BoqRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(requirementId: Long): Long? {
        boqRepo.findByRequirement(requirementId)?.let { return it.id }
        val req = reqRepo.get(requirementId) ?: return null
        val items = reqRepo.getItems(requirementId)
        if (items.isEmpty()) return null
        val site = siteRepo.getSite(req.siteId)
        val docId = boqRepo.insertDocument(
            BoqDocument(
                requirementId = requirementId,
                siteId = req.siteId,
                title = "جدول كميات — ${site?.name ?: "موقع ${req.siteId}"}"
            )
        )
        boqRepo.insertLines(
            items.map { BoqLine(boqId = docId, description = it.description, quantity = it.quantity, unit = "وحدة") }
        )
        return docId
    }
}
