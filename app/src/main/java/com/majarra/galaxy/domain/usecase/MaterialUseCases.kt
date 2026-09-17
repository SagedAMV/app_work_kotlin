package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
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
 */
class RenameMaterialUseCase @Inject constructor(
    private val detailRepo: SiteDetailRepository,
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
