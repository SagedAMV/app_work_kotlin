package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.domain.repository.CategoryRepository
import javax.inject.Inject

/* ============================================================
 * حالات استخدام التصنيفات (ميزة النسخة 2.1 حسب الاسئله.md).
 * التصنيف = اسم فريد + لون سداسي، وحذفه لا يحذف المواقع
 * (المفتاح الخارجي في القاعدة ON DELETE SET NULL).
 * ============================================================ */

/** أقصى طول معقول لاسم تصنيف يُعرض في شريحة فلترة */
private const val MAX_CATEGORY_NAME = 30

/** تحقق من صيغة اللون السداسي ‎#RRGGBB حتى لا تُخزَّن قيم تالفة */
private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

/**
 * حفظ تصنيف (إضافة أو تعديل) مع التحقق:
 * اسم غير فارغ وبحد معقول + لون سداسي صالح.
 * @throws IllegalArgumentException برسالة عربية عند مدخلات غير صالحة.
 */
class SaveCategoryUseCase @Inject constructor(
    private val repo: CategoryRepository
) {
    suspend operator fun invoke(category: Category): Long {
        val name = category.name.trim().replace(Regex("\\s+"), " ")
        require(name.isNotEmpty()) { "اسم التصنيف مطلوب" }
        require(name.length <= MAX_CATEGORY_NAME) {
            "اسم التصنيف طويل جدًا (الحد $MAX_CATEGORY_NAME حرفًا)"
        }
        require(HEX_COLOR.matches(category.colorHex)) { "لون التصنيف غير صالح" }
        // منع تكرار اسم قائم: فهرس الاسم فريد في القاعدة لكن الفحص المبكر
        // يعطي رسالة عربية واضحة بدل خطأ قيد من القاعدة.
        val duplicate = repo.getAll().any {
            it.id != category.id && it.name.equals(name, ignoreCase = true)
        }
        require(!duplicate) { "يوجد تصنيف بهذا الاسم بالفعل" }
        return if (category.id == 0L) {
            repo.insert(category.copy(name = name))
        } else {
            repo.update(category.copy(name = name))
            category.id
        }
    }
}

/**
 * حذف تصنيف — المواقع المرتبطة به تبقى وتتحول إلى «بلا تصنيف»
 * بفضل المفتاح الخارجي ذي الحذف المُصفِّر (SET NULL).
 */
class DeleteCategoryUseCase @Inject constructor(
    private val repo: CategoryRepository
) {
    suspend operator fun invoke(category: Category) = repo.delete(category)
}
