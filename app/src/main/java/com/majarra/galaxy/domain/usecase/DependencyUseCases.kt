package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.MaterialDependency
import com.majarra.galaxy.domain.repository.MaterialDependencyRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import javax.inject.Inject

/* ============================================================
 * حالات استخدام تبعيات (ملحقات) مادة الاتصال — جلسة تعديلات منطق
 * المواد: تُضاف التبعيات (واير كهرباء، كواكسل، مايك…) تحت مادة
 * أم معرَّفة بـ«مادة اتصال» داخل موقع واحد، وتُعامل بنفس منطق
 * المواد الخارجية (سحب للصيانة عبر دورة السحوبات بـparentName).
 * ============================================================ */

/** أقصى طول معقول لاسم التبعية */
const val MAX_DEPENDENCY_NAME = 60

/** تطبيع اسم التبعية أو مادة الأم: قص الفراغات الزائدة وتوحيدها */
private fun cleanDependencyName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ")

/** تحديث آخر تعديل للموقع حتى يبقى ترتيب «الأحدث تعديلًا» صادقًا */
private suspend fun touchSite(siteRepo: SiteRepository, siteId: Long) {
    siteRepo.getSite(siteId)?.let {
        siteRepo.update(it.copy(lastModified = System.currentTimeMillis()))
    }
}

/**
 * إضافة تبعية لمادة اتصال في موقع محدد مع التحقق:
 * اسم غير فارغ وبحد معقول + منع تكرار تبعية بنفس الاسم تحت مادة
 * الأم نفسها في الموقع نفسه (الكتالوج لا علاقة له بالتبعيات؛ فهي
 * بيانات على مستوى الموقع مثل مواد الموقع النصية).
 * @throws IllegalArgumentException برسالة عربية عند مدخلات غير صالحة.
 */
class AddSiteDependencyUseCase @Inject constructor(
    private val dependencyRepo: MaterialDependencyRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(siteId: Long, parentName: String, name: String) {
        val parent = cleanDependencyName(parentName)
        require(parent.isNotEmpty()) { "مادة الأم مطلوبة" }
        val clean = cleanDependencyName(name)
        require(clean.isNotEmpty()) { "اسم التبعية مطلوب" }
        require(clean.length <= MAX_DEPENDENCY_NAME) {
            "اسم التبعية طويل جدًا (الحد $MAX_DEPENDENCY_NAME حرفًا)"
        }
        // الفحص المبكر يعطي رسالة عربية واضحة؛ الفهرس الفريد في القاعدة
        // حماية أخيرة ضد السباقات فقط.
        val duplicate = dependencyRepo.getByParent(siteId, parent)
            .any { it.name.equals(clean, ignoreCase = true) }
        require(!duplicate) { "توجد تبعية بهذا الاسم تحت «$parent» بالفعل" }
        dependencyRepo.insert(
            MaterialDependency(siteId = siteId, parentName = parent, name = clean)
        )
        touchSite(siteRepo, siteId)
    }
}

/**
 * حذف تبعية من قائمة أمّها — حذف نهائي للسجل من جدول التبعيات.
 * إن كانت للتبعية سحب مفتوح فسجلها في «المسحوبات» يبقى للتوثيق
 * (نفس فلسفة RemoveSiteMaterialUseCase مع سحوبات المواد الرئيسية).
 */
class RemoveSiteDependencyUseCase @Inject constructor(
    private val dependencyRepo: MaterialDependencyRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(dependency: MaterialDependency) {
        dependencyRepo.delete(dependency)
        touchSite(siteRepo, dependency.siteId)
    }
}
