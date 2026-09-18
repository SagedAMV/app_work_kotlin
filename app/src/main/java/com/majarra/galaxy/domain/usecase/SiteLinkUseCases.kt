package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.SiteLink
import com.majarra.galaxy.domain.repository.SiteLinkRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/* ============================================================
 * حالات استخدام روابط شبكة «واجهة المجرة» (النسخة 2.5 — تعليمات
 * هذه الجلسة). الرابط خط بين موقعين يُنشئه المستخدم من الواجهة
 * ويثبَّت في جدول site_links.
 * ============================================================ */

/** مراقبة كل روابط الشبكة — تُرسم خطوط المجرة منها */
class ObserveSiteLinksUseCase @Inject constructor(
    private val linkRepo: SiteLinkRepository
) {
    operator fun invoke(): Flow<List<SiteLink>> = linkRepo.observeAll()
}

/** نتيجة محاولة إنشاء رابط بين موقعين */
sealed interface LinkResult {
    /** أُنشئ الرابط وثُبّت في الشبكة */
    data object Created : LinkResult

    /** الرابط موجود مسبقًا بأي اتجاه — لا ننشئ مكررًا */
    data object AlreadyLinked : LinkResult

    /** الموقعان هما نفس الموقع — الربط الذاتي ممنوع */
    data object SelfLink : LinkResult

    /** أحد الموقعين لم يعد موجودًا (حُذف أثناء العملية) */
    data object SiteMissing : LinkResult
}

/**
 * إنشاء رابط بين موقعين مع كل التحققات.
 *
 * لماذا تطبيع الاتجاه (الأصغر ← الأكبر)؟
 *   الرابط "أ←ب" و"ب←أ" شيء واحد بصريًا ومنطقيًا. بتخزين الأصغر
 *   دائمًا في `fromSiteId` يصبح الفهرس الفريد على الزوج كافيًا
 *   لمنع التكرار بأي اتجاه، ويصبح البحث عن الروابط متطابقًا.
 */
class LinkSitesUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val linkRepo: SiteLinkRepository
) {
    suspend operator fun invoke(siteIdA: Long, siteIdB: Long): LinkResult {
        // الحالة 1: نقر نفس الموقع في وضع الربط = إلغاء، ليس خطأ
        if (siteIdA == siteIdB) return LinkResult.SelfLink

        // الحالة 2: أحد الطرفين اختفى (حُذف) أثناء فتح وضع الربط
        if (siteRepo.getSite(siteIdA) == null || siteRepo.getSite(siteIdB) == null) {
            return LinkResult.SiteMissing
        }

        // الحالة 3: إدراج بتطبيع الاتجاه؛ -1 تعني رابطًا قائمًا مسبقًا
        val from = minOf(siteIdA, siteIdB)
        val to = maxOf(siteIdA, siteIdB)
        val rowId = linkRepo.insert(SiteLink(fromSiteId = from, toSiteId = to))
        return if (rowId == -1L) LinkResult.AlreadyLinked else LinkResult.Created
    }
}

/**
 * فك رابط بين موقعين — يُتاح من قائمة الموقع داخل المجرة لتصحيح
 * الأخطاء أو إعادة تشكيل الشبكة. الحذف بأي اتجاه.
 */
class UnlinkSitesUseCase @Inject constructor(
    private val linkRepo: SiteLinkRepository
) {
    suspend operator fun invoke(siteIdA: Long, siteIdB: Long) {
        if (siteIdA == siteIdB) return
        linkRepo.deleteBetween(siteIdA, siteIdB)
    }
}
