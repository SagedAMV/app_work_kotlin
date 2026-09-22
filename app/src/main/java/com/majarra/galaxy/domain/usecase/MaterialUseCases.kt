package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.domain.repository.MaterialDependencyRepository
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import com.majarra.galaxy.util.MaterialLines
import javax.inject.Inject

/* ============================================================
 * حالات استخدام كتالوج المواد الموحد (تعديل النسخة 2.2):
 * المواد تُعرَّف مرة واحدة في الكتالوج ثم تختارها المواقع من
 * واجهة اختيار، وإلغاء الكتابة النصية في كل موقع. إعادة تسمية
 * مادة تنعكس على كل المواقع حتى تبقى المواد موحدة فعلًا.
 * ============================================================ */

/** أقصى طول معقول لاسم مادة يظهر في واجهات الاختيار */
const val MAX_MATERIAL_NAME = 40

/**
 * حفظ مادة في الكتالوج الموحد (إضافة أو تعديل) مع التحقق:
 * اسم غير فارغ وبحد معقول + منع تكرار اسم قائم (بحيث لا يختلف
 * الرسم الكبير/الصغير فقط).
 * @throws IllegalArgumentException برسالة عربية عند مدخلات غير صالحة.
 */
class SaveMaterialUseCase @Inject constructor(
    private val repo: MaterialRepository
) {
    suspend operator fun invoke(material: Material): Long {
        val name = material.name.trim().replace(Regex("\\s+"), " ")
        require(name.isNotEmpty()) { "اسم المادة مطلوب" }
        require(name.length <= MAX_MATERIAL_NAME) {
            "اسم المادة طويل جدًا (الحد $MAX_MATERIAL_NAME حرفًا)"
        }
        // منع تكرار اسم قائم: فهرس الاسم فريد في القاعدة لكن الفحص المبكر
        // يعطي رسالة عربية واضحة بدل خطأ قيد من القاعدة.
        val duplicate = repo.getAll().any {
            it.id != material.id && it.name.equals(name, ignoreCase = true)
        }
        require(!duplicate) { "توجد مادة بهذا الاسم بالفعل" }
        return if (material.id == 0L) {
            repo.insert(material.copy(name = name))
        } else {
            repo.update(material.copy(name = name))
            material.id
        }
    }
}

/**
 * إعادة تسمية مادة موحدة — لأن المواقع تخزّن أسماء المواد نصًا في
 * أسطر تفاصيلها، تُرحَّل التسمية الجديدة إلى كل أعمدة المواد في كل
 * المواقع (الموجودة/الاحتياج/تحتاج صيانة/المسحوبة) فتبقى المواد
 * موحدة عبر التطبيق كله، لا في الكتالوج فقط.
 *
 * جلسة تعديلات منطق المواد: الترحيل يشمل الآن مراجع الاسم في
 * `material_dependencies.parentName` وفي `withdrawals.parentName`
 * (سحوبات التبعيات) — وإلا بقيت تبعيات وسحوبات تشير إلى أم قديم
 * الاسم فتضيع من واجهة التبعيات.
 */
class RenameMaterialUseCase @Inject constructor(
    private val detailRepo: SiteDetailRepository,
    private val dependencyRepo: MaterialDependencyRepository,
    private val withdrawalRepo: WithdrawalRepository,
    private val saveMaterial: SaveMaterialUseCase
) {
    suspend operator fun invoke(material: Material, newName: String): Long {
        val oldName = material.name
        // الحفظ أولًا: يتولى التحقق ومنع التكرار ويرمي عند الخطأ
        val id = saveMaterial(material.copy(name = newName))
        val cleanNew = newName.trim().replace(Regex("\\s+"), " ")
        if (oldName.equals(cleanNew, ignoreCase = true)) return id

        detailRepo.getAll().forEach { detail ->
            val available = MaterialLines.renameItem(detail.availableMaterials, oldName, cleanNew)
            val needed = MaterialLines.renameItem(detail.neededMaterials, oldName, cleanNew)
            val maintenance = MaterialLines.renameItem(detail.maintenanceMaterials, oldName, cleanNew)
            val withdrawn = MaterialLines.renameItem(detail.withdrawnMaterials, oldName, cleanNew)
            if (available != detail.availableMaterials ||
                needed != detail.neededMaterials ||
                maintenance != detail.maintenanceMaterials ||
                withdrawn != detail.withdrawnMaterials
            ) {
                detailRepo.upsert(
                    detail.copy(
                        availableMaterials = available,
                        neededMaterials = needed,
                        maintenanceMaterials = maintenance,
                        withdrawnMaterials = withdrawn
                    )
                )
            }
        }

        // ترحيل مراجع الأم في التبعيات وسحوباتها (مطابقة بالاسم بلا حالة)
        // ملاحظة مهمة: لا يجوز إدراج نفس السجل بنفس الـ id القديم لأن @Insert
        // سيتعارض مع المفتاح الأساسي الموجود ثم يُهمل الإدراج (IGNORE)؛ بعدها
        // حذف السجل القديم يعني فقدان التبعية بالكامل. لذلك نُنشئ صفًا جديدًا
        // بـ id = 0 ونحذف القديم بعد محاولة الإدراج.
        dependencyRepo.getAll().forEach { dep ->
            if (dep.parentName.equals(oldName, ignoreCase = true)) {
                // نحاول إدراج صف جديد بـ id = 0 حتى لا نتعارض مع المفتاح الأساسي الحالي.
                // إذا كان هناك صف مطابق موجود مسبقًا تحت الاسم الجديد فسيُهمل الإدراج (IGNORE)
                // ومع ذلك حذف السجل القديم يبقى آمنًا لأنه تكرار فعلي لنفس التبعية.
                dependencyRepo.insert(dep.copy(id = 0, parentName = cleanNew))
                dependencyRepo.delete(dep)
            }
        }
        withdrawalRepo.getAll().forEach { w ->
            if (w.parentName.equals(oldName, ignoreCase = true)) {
                withdrawalRepo.update(w.copy(parentName = cleanNew))
            }
        }
        return id
    }
}

/**
 * حذف مادة من الكتالوج الموحد — الحذف من الكتالوج فقط: العناصر
 * المضافة سابقًا في قوائم المواقع تبقى كنصوص محفوظة (لا فقدان
 * بيانات)، لكنها لن تظهر في واجهات الاختيار بعد الآن.
 */
class DeleteMaterialUseCase @Inject constructor(
    private val repo: MaterialRepository
) {
    suspend operator fun invoke(material: Material) = repo.delete(material)
}

/**
 * إزالة مادة من قسم «المواد» في موقع واحد (النسخة 2.12 — إعادة تصميم
 * تفاصيل الموقع): النظافة المهنية لقائمة الجرد؛ إن كان للسحوبات
 * المفتوحة بند بنفس الاسم فسجلاتها تبقى كما هي في «المسحوبات» للتوثيق
 * ولا تُحذف معها.
 */
class RemoveSiteMaterialUseCase @Inject constructor(
    private val detailRepo: SiteDetailRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(siteId: Long, materialName: String) {
        val detail = detailRepo.getBySite(siteId) ?: return
        val remaining = MaterialLines.parse(detail.availableMaterials)
            .filterNot { it.text.equals(materialName, ignoreCase = true) }
        detailRepo.upsert(
            detail.copy(availableMaterials = MaterialLines.serialize(remaining))
        )
        siteRepo.getSite(siteId)?.let {
            siteRepo.update(it.copy(lastModified = System.currentTimeMillis()))
        }
    }
}
