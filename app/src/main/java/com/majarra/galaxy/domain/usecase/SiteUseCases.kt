package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.SiteHistoryRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.UriPermissionVault
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** مراقبة قائمة المواقع أو البحث فيها */
class ObserveSitesUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    operator fun invoke(): Flow<List<Site>> = siteRepo.observeSites()
    fun search(q: String): Flow<List<Site>> =
        if (q.isBlank()) siteRepo.observeSites() else siteRepo.searchSites(q.trim())
}

/** مراقبة موقع واحد */
class ObserveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    operator fun invoke(id: Long): Flow<Site?> = siteRepo.observeSite(id)
}

/**
 * تحقق وتطبيع مدخلات الموقع.
 * المشكلة التي يُصلحها: نص فيه فراغات زائدة أو رمز بحروف صغيرة أو إحداثيات
 * خارج النطاق أو (0,0) تُحفظ بصمت، والرمز المكرر كان يصل إلى SQLite فيُرفض
 * بخطأ غامض بدل رسالة واضحة.
 */
object SiteInputValidator {

    const val MAX_NAME = 80
    const val MAX_NOTES = 500
    private val CODE_REGEX = Regex("^[A-Z0-9\\-_]{2,20}$")

    /** يعيد الموقع بعد التطبيع، أو يرمي IllegalArgumentException برسالة عربية واضحة */
    fun normalize(site: Site): Site {
        val name = site.name.trim().replace(Regex("\\s+"), " ")
        val code = site.code.trim().uppercase()
        require(name.isNotEmpty()) { "اسم الموقع مطلوب" }
        require(name.length <= MAX_NAME) { "اسم الموقع طويل جدًا (الحد $MAX_NAME حرفًا)" }
        require(code.isNotEmpty()) { "رمز الموقع مطلوب" }
        require(CODE_REGEX.matches(code)) {
            "الرمز يجب أن يكون بحروف إنجليزية كبيرة/أرقام/شرطة فقط (٢-٢٠ خانة)"
        }
        require(site.latitude in -90.0..90.0) { "خط العرض يجب أن يكون بين -90 و 90" }
        require(site.longitude in -180.0..180.0) { "خط الطول يجب أن يكون بين -180 و 180" }
        require(!(site.latitude == 0.0 && site.longitude == 0.0)) {
            "الإحداثيات (0,0) غير صالحة — استخدم زر تحديد الموقع الحالي"
        }
        return site.copy(
            name = name,
            code = code,
            notes = site.notes.trim().take(MAX_NOTES)
        )
    }
}

/** حفظ موقع (إضافة أو تعديل) مع تسجيل في السجل التاريخي وسجل التدقيق */
class SaveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val historyRepo: SiteHistoryRepository,
    private val auditRepo: AuditRepository
) {
    /** @throws IllegalArgumentException عند مدخلات غير صالحة أو رمز مكرر */
    suspend operator fun invoke(site: Site): Long {
        val clean = SiteInputValidator.normalize(site)

        // تفرد الرمز يُفحص مسبقًا ليعطي رسالة واضحة بدل استثناء قيد SQLite
        val duplicate = if (clean.id == 0L) {
            siteRepo.findByCode(clean.code)
        } else {
            siteRepo.findByCodeExcept(clean.code, clean.id)
        }
        require(duplicate == null) { "الرمز ${clean.code} مستخدم في موقع آخر: ${duplicate?.name}" }

        return if (clean.id == 0L) {
            val id = siteRepo.insert(clean)
            historyRepo.record(id, "إضافة موقع", "", clean.name)
            auditRepo.log("CREATE", "Site", id, clean.name)
            id
        } else {
            val old = siteRepo.getSite(clean.id)
            siteRepo.update(clean.copy(updatedAt = System.currentTimeMillis()))
            val statusChange = if (old != null && old.status != clean.status) {
                " — الحالة: ${old.status.label} ← ${clean.status.label}"
            } else {
                ""
            }
            historyRepo.record(clean.id, "تعديل موقع", old?.name.orEmpty(), clean.name)
            auditRepo.log("UPDATE", "Site", clean.id, clean.name + statusChange)
            clean.id
        }
    }
}

/** نتيجة محاولة حذف موقع */
sealed class DeleteSiteResult {
    object Deleted : DeleteSiteResult()
    data class Blocked(val equipmentCount: Int, val activeLinks: Int) : DeleteSiteResult()
}

/**
 * حذف موقع — قاعدة العمل رقم 1:
 * لا يمكن حذف موقع يحتوي معدات أو روابط نشطة (نعرض الأسباب مفصّلة).
 * عند الحذف تُحرَّر أذونات ملفات المرفقات حتى لا تتراكم أذونات لملفات مهجورة.
 */
class DeleteSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val equipmentRepo: EquipmentRepository,
    private val linkRepo: LinkRepository,
    private val attachmentRepo: AttachmentRepository,
    private val auditRepo: AuditRepository,
    private val uriVault: UriPermissionVault
) {
    suspend operator fun invoke(site: Site): DeleteSiteResult {
        val equipmentCount = equipmentRepo.countBySite(site.id)
        val activeLinks = linkRepo.countActiveBySite(site.id)
        if (equipmentCount > 0 || activeLinks > 0) {
            return DeleteSiteResult.Blocked(equipmentCount, activeLinks)
        }
        val attachmentUris = attachmentRepo.getBySite(site.id).map { it.uri }
        siteRepo.delete(site)
        uriVault.release(attachmentUris)
        auditRepo.log("DELETE", "Site", site.id, site.name)
        return DeleteSiteResult.Deleted
    }
}
