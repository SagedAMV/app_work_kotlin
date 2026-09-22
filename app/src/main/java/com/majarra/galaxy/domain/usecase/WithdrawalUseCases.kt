package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.domain.model.ItemType
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import javax.inject.Inject

/* ============================================================
 * حالات استخدام دورة السحب والصيانة والإرجاع:
 * النسخة 2.2 أدخلت السحب بوصفه سجلًا مستقلًا، والنسخة 2.12 (تعليمات
 * إعادة تصميم تفاصيل الموقع) أعادت تشكيل الدورة وفق الواجهة الجديدة:
 * يُسحب بند من قسم «المواد» بسبب إلزامي، ثم يُتخذ قرار واحد من اثنين
 * (تم الإصلاح بوصف إلزامي / لم يتم الإصلاح بسبب إلزامي)، ثم يُرجع
 * البند إلى الموقع بختم الدورة. المادة المسحوبة تختفي مؤقتًا من
 * قسم «المواد» (عرض محسوب) وتظهر في واجهة «المسحوبات» حتى الإرجاع.
 * ============================================================ */

/** أقصى طول معقول لاسم مادة مسحوبة */
const val MAX_WITHDRAWN_ITEM_NAME = 60

/** أقصى طول لسبب السحب وملاحظاته */
const val MAX_WITHDRAWAL_TEXT = 300

/** أقصى طول لنص قرار الإصلاح (كيف أُصلحت / سبب عدم الإصلاح) */
const val MAX_WITHDRAWAL_DECISION = 500

/** تطبيع نص حر في دورة السحب: قص الفراغات الزائدة وتوحيدها */
private fun cleanWithdrawalText(text: String): String =
    text.trim().replace(Regex("\\s+"), " ")

/** تطبيع اسم المادة والتحقق منه */
private fun normalizeItemName(name: String): String {
    val clean = cleanWithdrawalText(name)
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
 * سحب مادة من قسم «المواد» في الموقع إلى الصيانة (2.12):
 * 1) يُسجَّل السحب في جدول السحوبات بحالة «مسحوبة» مع سبب إلزامي
 *    وملاحظات اختيارية.
 * 2) المادة تختفي مؤقتًا من قسم «المواد» — العرض في الواجهة يطرح
 *    المواد ذات سحب مفتوح (لم تُرجع بعد)، فلا يُلمس التخزين إطلاقًا،
 * وعند الإرجاع تعود للظهور تلقائيًا.
 *
 * جلسة تعديلات منطق المواد: المعامل الاختياري `parentName` يميز سحب
 * تبعية (ملحق مادة اتصال) عن سحب مادة رئيسية — نفس الدورة ونفس
 * القرارات والإرجاع، لكن التبعية تختفي من قائمة تبعيات أمّها فقط
 * (الفلترة في الواجهة تطابق parentName + الاسم معًا).
 */
class WithdrawMaterialForMaintenanceUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(
        siteId: Long,
        itemName: String,
        withdrawReason: String,
        notes: String,
        parentName: String = ""
    ): Long {
        val name = normalizeItemName(itemName)
        val reason = cleanWithdrawalText(withdrawReason)
        require(reason.isNotEmpty()) { "سبب السحب للصيانة مطلوب" }
        require(reason.length <= MAX_WITHDRAWAL_TEXT) {
            "سبب السحب طويل جدًا (الحد $MAX_WITHDRAWAL_TEXT حرفًا)"
        }
        val cleanNotes = cleanWithdrawalText(notes).take(MAX_WITHDRAWAL_TEXT)
        val parent = cleanWithdrawalText(parentName).take(MAX_WITHDRAWN_ITEM_NAME)
        val id = withdrawalRepo.insert(
            Withdrawal(
                siteId = siteId,
                itemName = name,
                // النوع قيمة قديمة من واجهة السحب السابقة؛ السحب الجديد
                // يبدأ من بنود قسم «المواد» بلا نوع — يُخزَّن OTHER ولا يُعرض.
                itemType = ItemType.OTHER,
                status = WithdrawalStatus.WITHDRAWN,
                notes = cleanNotes,
                withdrawReason = reason,
                parentName = parent
            )
        )
        touchSite(siteRepo, siteId)
        return id
    }
}

/**
 * قرار «تم الإصلاح» (2.12): يُختم البند بوصف إلزامي لطريقة الإصلاح،
 * وتتحدث حالة البطاقة إلى «تم الإصلاح» مع بقاء زر الإرجاع للموقع —
 * المادة لا تعود فورًا إلى «المواد» بل عند الضغط على «إرجاع للموقع».
 */
class MarkWithdrawalFixedUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal, fixedNote: String) {
        require(
            withdrawal.status == WithdrawalStatus.WITHDRAWN ||
                withdrawal.status == WithdrawalStatus.IN_MAINTENANCE
        ) { "هذه المادة ليست بانتظار قرار الإصلاح" }
        val note = cleanWithdrawalText(fixedNote)
        require(note.isNotEmpty()) { "كيف أصلحت المشكلة مطلوب" }
        require(note.length <= MAX_WITHDRAWAL_DECISION) {
            "وصف الإصلاح طويل جدًا (الحد $MAX_WITHDRAWAL_DECISION حرفًا)"
        }
        withdrawalRepo.update(
            withdrawal.copy(status = WithdrawalStatus.FIXED, fixedNote = note)
        )
        touchSite(siteRepo, withdrawal.siteId)
    }
}

/**
 * قرار «لم يتم الإصلاح» (2.12): يُختم البند بسبب إلزامي لعدم الإصلاح،
 * وتتحدث حالة البطاقة إلى «لم يتم الإصلاح» مع بقاء زر إرجاع المادة.
 */
class MarkWithdrawalNotFixedUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal, notFixedReason: String) {
        require(
            withdrawal.status == WithdrawalStatus.WITHDRAWN ||
                withdrawal.status == WithdrawalStatus.IN_MAINTENANCE
        ) { "هذه المادة ليست بانتظار قرار الإصلاح" }
        val reason = cleanWithdrawalText(notFixedReason)
        require(reason.isNotEmpty()) { "سبب عدم الإصلاح مطلوب" }
        require(reason.length <= MAX_WITHDRAWAL_DECISION) {
            "سبب عدم الإصلاح طويل جدًا (الحد $MAX_WITHDRAWAL_DECISION حرفًا)"
        }
        withdrawalRepo.update(
            withdrawal.copy(status = WithdrawalStatus.NOT_FIXED, notFixedReason = reason)
        )
        touchSite(siteRepo, withdrawal.siteId)
    }
}

/**
 * إرجاع المادة المسحوبة إلى الموقع (من أي حالة مفتوحة) وختم الدورة:
 * تتحول الحالة إلى «مُرجعة» ويُختم تاريخ الإرجاع، فتعود المادة إلى
 * الظهور في قسم «المواد» تلقائيًا (العرض المحسوب يطرح المفتوح فقط).
 */
class ReturnWithdrawnItemUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal) {
        if (withdrawal.status == WithdrawalStatus.RETURNED) return
        withdrawalRepo.update(
            withdrawal.copy(
                status = WithdrawalStatus.RETURNED,
                returnedDate = System.currentTimeMillis()
            )
        )
        touchSite(siteRepo, withdrawal.siteId)
    }
}

/** حذف سجل سحب — نهائي ولا يمس قسم «المواد» (بنده لا يتأثر) */
class DeleteWithdrawalUseCase @Inject constructor(
    private val withdrawalRepo: WithdrawalRepository
) {
    suspend operator fun invoke(withdrawal: Withdrawal) = withdrawalRepo.delete(withdrawal)
}
