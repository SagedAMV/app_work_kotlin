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
import com.majarra.galaxy.domain.repository.TransactionRunner
import javax.inject.Inject

/** حد أعلى لنص البند/الملاحظة حتى لا تُغرق القاعدة بإدخال ضخم */
private const val MAX_TEXT = 400

/** حد أعلى لكمية البند */
private const val MAX_QUANTITY = 100_000

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

/**
 * إنشاء احتياج + بنوده + توليد BOQ — داخل معاملة واحدة.
 * إن فشل توليد البنود أو BOQ بعد إنشاء صف الاحتياج تبقى القاعدة بحالة ناقصة
 * (احتياج بلا بنود)؛ المعاملة تُرجع كل شيء أو لا شيء.
 */
class CreateRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val auditRepo: AuditRepository,
    private val generateBoq: GenerateBoqUseCase,
    private val tx: TransactionRunner
) {
    suspend operator fun invoke(draft: RequirementDraft): Long {
        require(draft.siteId > 0L) { "يجب اختيار موقع" }
        val cleanItems = draft.items
            .map { it.copy(description = it.description.trim().take(MAX_TEXT)) }
            .filter { it.description.isNotBlank() || it.inventoryItemId != null }
        require(cleanItems.isNotEmpty()) { "الاحتياج يحتاج بندًا واحدًا على الأقل" }

        return tx.inTransaction {
            val rid = reqRepo.insert(
                Requirement(
                    siteId = draft.siteId,
                    type = draft.type,
                    status = RequirementStatus.PENDING,
                    notes = draft.notes.trim().take(MAX_TEXT)
                )
            )
            reqRepo.insertItems(
                cleanItems.map {
                    RequirementItem(
                        requirementId = rid,
                        inventoryItemId = it.inventoryItemId,
                        description = it.description.ifBlank { "بند بدون وصف" },
                        quantity = it.quantity.coerceIn(1, MAX_QUANTITY)
                    )
                }
            )
            auditRepo.log("CREATE", "Requirement", rid, draft.type.label)
            generateBoq(rid)
            rid
        }
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
 * خصم تلقائي من المخزون + تسجيل في سجل التدقيق + تنبيه نقص عند بلوغ الحد الأدنى.
 *
 * تصليب: الخصم والبنود وحالة الاحتياج والتدقيق والتنبيهات كلها داخل معاملة
 * واحدة؛ سابقًا كانت عمليات منفصلة، فلو توقف التطبيق في المنتصف يبقى المخزون
 * مخصومًا والاحتياج غير مصروف (نقص جزئي صامت). ولا خصم جزئي أبدًا: إن نقص أي
 * صنف تُرفض العملية قبل أي كتابة. ويُعاد التحقق داخل المعاملة لمنع السباق.
 */
class FulfillRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val inventoryRepo: InventoryRepository,
    private val auditRepo: AuditRepository,
    private val alertRepo: AlertRepository,
    private val tx: TransactionRunner
) {
    suspend operator fun invoke(requirementId: Long): FulfillResult {
        val req = reqRepo.get(requirementId) ?: return FulfillResult.NotFound
        if (req.status == RequirementStatus.FULFILLED) {
            return FulfillResult.Failure("هذا الاحتياج مصروف مسبقًا")
        }
        if (req.status == RequirementStatus.CANCELLED) {
            return FulfillResult.Failure("لا يمكن صرف احتياج ملغي")
        }
        val items = reqRepo.getItems(requirementId).filter { !it.fulfilled }

        // 1) التحقق المسبق من توفر الكميات — لا خصم جزئي أبدًا
        for (item in items) {
            val invId = item.inventoryItemId ?: continue
            val inv = inventoryRepo.getById(invId)
                ?: return FulfillResult.Failure("صنف مخزون محذوف (رقم $invId) — صحّح البند أولًا")
            if (inv.quantity < item.quantity) {
                return FulfillResult.Failure(
                    "المخزون غير كافٍ: ${inv.name} (مطلوب ${item.quantity} والمتوفر ${inv.quantity})"
                )
            }
        }

        // 2) التنفيذ الذري
        return tx.inTransaction {
            for (item in items) {
                val invId = item.inventoryItemId
                if (invId == null) {
                    reqRepo.updateItem(item.copy(fulfilled = true))
                    continue
                }
                val inv = inventoryRepo.getById(invId)
                    ?: return@inTransaction FulfillResult.Failure("صنف مخزون محذوف أثناء الصرف")
                // فحص ثانٍ داخل المعاملة: يمنع سباقًا بين فحصٍ وخصمٍ متزامنين
                if (inv.quantity < item.quantity) {
                    return@inTransaction FulfillResult.Failure("تغيّر المخزون أثناء الصرف: ${inv.name}")
                }
                val newQty = inv.quantity - item.quantity
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
            FulfillResult.Success
        }
    }
}

/** إلغاء احتياج (مع منع الإلغاء بعد الصرف) */
class CancelRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(requirementId: Long): Boolean {
        val req = reqRepo.get(requirementId) ?: return false
        if (req.status == RequirementStatus.FULFILLED) return false
        reqRepo.update(req.copy(status = RequirementStatus.CANCELLED))
        auditRepo.log("UPDATE", "Requirement", requirementId, "إلغاء الاحتياج")
        return true
    }
}

/** حذف احتياج (المسودات/الملغاة) — بنودها ووثيقة BOQ تُحذف بالتسلسل تلقائيًا */
class DeleteRequirementUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(requirementId: Long): Boolean {
        val req = reqRepo.get(requirementId) ?: return false
        if (req.status == RequirementStatus.FULFILLED) return false
        reqRepo.delete(req)
        auditRepo.log("DELETE", "Requirement", requirementId, req.type.label)
        return true
    }
}

/** توليد وثيقة BOQ من احتياج (مرة واحدة لكل احتياج) — داخل معاملة واحدة */
class GenerateBoqUseCase @Inject constructor(
    private val reqRepo: RequirementRepository,
    private val boqRepo: BoqRepository,
    private val siteRepo: SiteRepository,
    private val tx: TransactionRunner
) {
    suspend operator fun invoke(requirementId: Long): Long? {
        boqRepo.findByRequirement(requirementId)?.let { return it.id }
        val req = reqRepo.get(requirementId) ?: return null
        val items = reqRepo.getItems(requirementId)
        if (items.isEmpty()) return null
        return tx.inTransaction {
            // فحص ثانٍ داخل المعاملة: يمنع إنشاء وثيقتين عند النقر المزدوج
            boqRepo.findByRequirement(requirementId)?.let { return@inTransaction it.id }
            val site = siteRepo.getSite(req.siteId)
            val docId = boqRepo.insertDocument(
                BoqDocument(
                    requirementId = requirementId,
                    siteId = req.siteId,
                    title = "جدول كميات — ${site?.name ?: "موقع ${req.siteId}"}"
                )
            )
            boqRepo.insertLines(
                items.map {
                    BoqLine(boqId = docId, description = it.description, quantity = it.quantity, unit = "وحدة")
                }
            )
            docId
        }
    }
}
