package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import com.majarra.galaxy.util.MaterialLines
import javax.inject.Inject

/* ============================================================
 * حالات استخدام دورة السحب والصيانة والإرجاع (إضافة النسخة 2.2):
 * المستخدم يسحب مادة من موقع (جهاز/مايك/لوح شمسي/بطارية/جهاز
 * يدوي/أي شيء)، يصونها، ثم يرجعها للموقع. السجل يتتبع الدورة
 * كاملة ويتزامن تلقائيًا مع قائمة «مواد تم سحبها» في الموقع حتى
 * لا يُدخل المستخدم نفس المعلومة مرتين.
 * ============================================================ */

/** أقصى طول معقول لاسم مادة مسحوبة */
const val MAX_WITHDRAWN_ITEM_NAME = 60

/** تطبيع اسم المادة المسحوبة والتحقق منه */
private fun normalizeItemName(name: String): String {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    require(clean.isNotEmpty()) { "اسم المادة المسحوبة مطلوب" }
    require(clean.length <= MAX_WITHDRAWN_ITEM_NAME) {
        "اسم المادة طويل جدًا (الحد $MAX_WITHDRAWN_ITEM_NAME حرفًا)"
    }
    return clean
}

/** تحديث آخر تعديل للموقع حتى يبقى ترتيب «الأحدث تعديلًا» صادقًا */
private suspend fun touchSite(siteRepo: SiteRepository, siteId: Long) {
    siteRepo.getSite(siteId)?.let {
        siteRepo.update(it.copy(lastModified = System.currentTimeMillis()))
    }
}

/**
 * سحب مادة جديدة من الموقع:
 * 1) يُسجَّل السحب في جدول السحوبات بحالة «مسحوبة».
 * 2) تُضاف المادة تلقائيًا إلى قائمة «مواد تم سحبها» في تفاصيل
 *    الموقع (غير محددة) فيتزامن السجلان بلا إدخال مزدوج.
 */
class WithdrawItemUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val detailRepo: SiteDetailRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal): Long {
        val name = normalizeItemName(withdrawal.itemName)
        val id = withdrawalRepo.insert(
            withdrawal.copy(
                itemName = name,
                notes = withdrawal.notes.trim(),
                status = WithdrawalStatus.WITHDRAWN,
                returnedDate = null
            )
        )
        val detail = detailRepo.getBySite(withdrawal.siteId)
        val updatedList = MaterialLines.addUnchecked(detail?.withdrawnMaterials.orEmpty(), name)
        detailRepo.upsert(
            detail?.copy(withdrawnMaterials = updatedList)
                ?: SiteDetail(siteId = withdrawal.siteId, withdrawnMaterials = updatedList)
        )
        touchSite(siteRepo, withdrawal.siteId)
        return id
    }
}

/** نقل المادة المسحوبة إلى حالة «قيد الصيانة» (خطوة واحدة للأمام) */
class StartWithdrawalMaintenanceUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal) {
        if (withdrawal.status == WithdrawalStatus.WITHDRAWN) {
            withdrawalRepo.update(withdrawal.copy(status = WithdrawalStatus.IN_MAINTENANCE))
        }
    }
}

/**
 * إرجاع المادة المسحوبة إلى الموقع:
 * 1) تتحول الحالة إلى «مُرجعة» ويُختم تاريخ الإرجاع.
 * 2) تُوسم المادة المطابقة في قائمة «مواد تم سحبها» بعلامة ✔
 *    دلالة اكتمال دورة السحب. إن لم يوجد سطر مطابق (حُذف يدويًا)
 *    لا يُضاف شيء — لا مفاجآت صامتة.
 */
class ReturnWithdrawnItemUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val detailRepo: SiteDetailRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal) {
        val now = System.currentTimeMillis()
        withdrawalRepo.update(
            withdrawal.copy(status = WithdrawalStatus.RETURNED, returnedDate = now)
        )
        detailRepo.getBySite(withdrawal.siteId)?.let { detail ->
            detailRepo.upsert(
                detail.copy(
                    withdrawnMaterials = MaterialLines.markChecked(
                        detail.withdrawnMaterials,
                        withdrawal.itemName
                    )
                )
            )
        }
        touchSite(siteRepo, withdrawal.siteId)
    }
}

/** حذف سجل سحب — القائمة النصية في تفاصيل الموقع لا تتأثر (تبقى للتوثيق) */
class DeleteWithdrawalUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal) = withdrawalRepo.delete(withdrawal)
}
