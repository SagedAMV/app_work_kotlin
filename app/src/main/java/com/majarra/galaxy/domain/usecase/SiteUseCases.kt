package com.majarra.galaxy.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** مراقبة قائمة المواقع أو البحث فيها أو فلترتها (تصنيف/أرشيف) */
class ObserveSitesUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    /** نطاق البحث حسب الاسئله.md: الاسم فقط وعلى المواقع النشطة */
    fun search(q: String): Flow<List<Site>> =
        if (q.isBlank()) siteRepo.observeSites() else siteRepo.searchSites(q.trim())

    /** مواقع تصنيف واحد (النشطة فقط) */
    fun byCategory(categoryId: Long): Flow<List<Site>> =
        siteRepo.observeByCategory(categoryId)

    /** المواقع المؤرشفة */
    fun archived(): Flow<List<Site>> = siteRepo.observeArchivedSites()
}

/** مراقبة موقع واحد */
class ObserveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    operator fun invoke(id: Long): Flow<Site?> = siteRepo.observeSite(id)
}

/**
 * تحقق وتطبيع مدخلات الموقع.
 * المشكلة التي يُصلحها: نص فيه فراغات زائدة كان يُحفظ كما هو،
 * والاسم الفارغ كان يصل إلى القاعدة بصمت.
 */
object SiteInputValidator {

    const val MAX_NAME = 80
    const val MAX_NOTES = 500

    /**
     * يعيد زوج (الاسم المطبّع، الملاحظات المطبّعة)،
     * أو يرمي IllegalArgumentException برسالة عربية واضحة.
     */
    fun normalize(name: String, notes: String): Pair<String, String> {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        val cleanNotes = notes.trim().replace(Regex("\\s+"), " ")
        require(cleanName.isNotEmpty()) { "اسم الموقع مطلوب" }
        require(cleanName.length <= MAX_NAME) { "اسم الموقع طويل جدًا (الحد $MAX_NAME حرفًا)" }
        return cleanName to cleanNotes.take(MAX_NOTES)
    }
}

/** حفظ موقع (إضافة أو تعديل) مع التحقق من المدخلات */
class SaveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    /** @throws IllegalArgumentException عند مدخلات غير صالحة */
    suspend operator fun invoke(site: Site): Long {
        val (name, notes) = SiteInputValidator.normalize(site.name, site.notes)
        val clean = site.copy(name = name, notes = notes)
        return if (clean.id == 0L) {
            siteRepo.insert(clean)
        } else {
            siteRepo.update(clean.copy(lastModified = System.currentTimeMillis()))
            clean.id
        }
    }
}

/**
 * حذف موقع — الحذف المتسلسل (CASCADE) يمسح التفاصيل والمرفقات
 * وسجل الصيانة تلقائيًا. قبل ذلك نحرّر أذونات القراءة الدائمة
 * لمرفقات المحتوى (content://) حتى لا تتراكم أذونات لملفات مهجورة.
 */
class DeleteSiteUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val siteRepo: SiteRepository,
    private val attachmentRepo: AttachmentRepository
) {
    suspend operator fun invoke(site: Site) {
        val attachmentPaths = attachmentRepo.getBySite(site.id).map { it.filePath }
        siteRepo.delete(site)
        attachmentPaths.forEach { path ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(path),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // الفشل هنا غير ضار: يعني فقط أن الإذن لم يكن مثبّتًا أصلًا
        }
    }
}

/**
 * أرشفة موقع (إجابة الاسئله.md): إخفاء الموقع من القائمة النشطة مع
 * إمكانية الاستعادة لاحقًا — بديل آمن عن الحذف النهائي المباشر.
 */
class ArchiveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(site: Site, archived: Boolean) {
        siteRepo.update(
            site.copy(archived = archived, lastModified = System.currentTimeMillis())
        )
    }
}
