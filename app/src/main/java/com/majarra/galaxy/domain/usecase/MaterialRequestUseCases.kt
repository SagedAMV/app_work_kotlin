package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.data.local.MaterialRequest
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.domain.model.RequestStatus
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.MaterialRequestRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.util.MaterialLines
import javax.inject.Inject

/* ============================================================
 * حالات استخدام طلبات احتياج الموقع (النسخة 2.12 — تعليمات إعادة
 * تصميم واجهة تفاصيل الموقع):
 * يرفع المستخدم احتياج الموقع من الكتالوج الموحد (أو يضيف مادة
 * جديدة من نفس المكان) فتصل الطلبات إلى واجهة «الاحتياجات» بانتظار
 * الموافقة، ثم:
 *  - موافقة: تُضاف المادة مباشرة إلى قسم «المواد» الرئيسي للموقع.
 *  - رفض: يبقى الطلب معروضًا مع زرّي (حذف / استرجاع)، والاسترجاع
 *    يعيده إلى حالته الأولية قبل الرفض.
 *  - حذف: يزيل الطلب نهائيًا من الواجهة.
 * ============================================================ */

/** أقصى طول لاسم مادة في طلب الاحتياج (يحاكي حد الكتالوج الموحد) */
const val MAX_REQUEST_MATERIAL_NAME = 60

/** تطبيع اسم مادة الطلب: قص الفراغات الزائدة وتوحيدها */
private fun cleanRequestName(name: String): String =
    name.trim().replace(Regex("\\s+"), " ")

/** تحديث آخر تعديل للموقع حتى يبقى ترتيب «الأحدث تعديلًا» صادقًا */
private suspend fun touchSite(siteRepo: SiteRepository, siteId: Long) {
    siteRepo.getSite(siteId)?.let {
        siteRepo.update(it.copy(lastModified = System.currentTimeMillis()))
    }
}

/**
 * رفع طلبات احتياج لأسماء مواد من الكتالوج المحفوظ مسبقًا:
 * يُتجاهل أي اسم فارغ أو مكرر ضمن الدفعة، وأي اسم له طلب قائم
 * بالفعل بحالة «بانتظار الموافقة» للموقع نفسه — فلا تتضخم الواجهة
 * بطلبات متطابقة.
 * @return عدد الطلبات التي رُفعت فعلًا.
 */
class SubmitMaterialRequestsUseCase @Inject constructor(
    private val requestRepo: MaterialRequestRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(siteId: Long, materialNames: List<String>): Int {
        val cleanNames = materialNames
            .map { cleanRequestName(it) }
            .filter { it.isNotEmpty() && it.length <= MAX_REQUEST_MATERIAL_NAME }
            .distinctBy { it.lowercase() }
        require(cleanNames.isNotEmpty()) { "اختر مادة واحدة على الأقل" }
        val pendingNames = requestRepo.getBySite(siteId)
            .filter { it.status == RequestStatus.PENDING }
            .map { it.materialName.lowercase() }
            .toSet()
        val fresh = cleanNames.filterNot { it.lowercase() in pendingNames }
        fresh.forEach { name ->
            requestRepo.insert(
                MaterialRequest(siteId = siteId, materialName = name)
            )
        }
        if (fresh.isNotEmpty()) touchSite(siteRepo, siteId)
        return fresh.size
    }
}

/**
 * إضافة مادة جديدة غير موجودة بالكتالوج المحفوظ من داخل واجهة
 * «احتياج موقع» مباشرة (تعليمات الجلسة): تُسجَّل المادة في الكتالوج
 * الموحد (بلا تكرار إن كانت موجودة بإملاء مختلف في الحالة الكبيرة/
 * الصغيرة) ثم تُرسل فورًا كطلب احتياج إلى واجهة «الاحتياجات».
 * @return معرف المادة في الكتالوج.
 */
class AddMaterialAsRequestUseCase @Inject constructor(
    private val materialRepo: MaterialRepository,
    private val requestRepo: MaterialRequestRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(siteId: Long, materialName: String): Long {
        val name = cleanRequestName(materialName)
        require(name.isNotEmpty()) { "اسم المادة مطلوب" }
        require(name.length <= MAX_REQUEST_MATERIAL_NAME) {
            "اسم المادة طويل جدًا (الحد $MAX_REQUEST_MATERIAL_NAME حرفًا)"
        }
        // الكتالوج فريد الاسم: أعد الموجود بدل تكراره
        val existing = materialRepo.getAll()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        val materialId = existing?.id ?: materialRepo.insert(Material(name = name))
        // لا ترفع طلبًا مكررًا إن كان بانتظار أصلًا
        val duplicate = requestRepo.getBySite(siteId).any {
            it.status == RequestStatus.PENDING && it.materialName.equals(name, ignoreCase = true)
        }
        if (!duplicate) {
            requestRepo.insert(MaterialRequest(siteId = siteId, materialName = name))
        }
        touchSite(siteRepo, siteId)
        return materialId
    }
}

/**
 * الموافقة على طلب احتياج (تعليمات الجلسة):
 * 1) يتحول الطلب إلى «تمت الموافقة» ويُختم تاريخ البت.
 * 2) تُضاف المادة مباشرة إلى قسم «المواد» الرئيسي للموقع (قائمة
 *    الموجود) إن لم تكن فيه أصلًا — فتظهر في الواجهة الرئيسية فورًا.
 */
class ApproveMaterialRequestUseCase @Inject constructor(
    private val requestRepo: MaterialRequestRepository,
    private val detailRepo: SiteDetailRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(request: MaterialRequest) {
        require(request.status == RequestStatus.PENDING) { "الطلب ليس بانتظار الموافقة" }
        requestRepo.update(
            request.copy(
                status = RequestStatus.APPROVED,
                resolvedDate = System.currentTimeMillis()
            )
        )
        val detail = detailRepo.getBySite(request.siteId)
        val updated = MaterialLines.addUnchecked(
            detail?.availableMaterials.orEmpty(),
            request.materialName
        )
        detailRepo.upsert(
            detail?.copy(availableMaterials = updated)
                ?: SiteDetail(siteId = request.siteId, availableMaterials = updated)
        )
        touchSite(siteRepo, request.siteId)
    }
}

/**
 * رفض طلب احتياج (تعليمات الجلسة): يُرفض الطلب لكنه يبقى معروضًا في
 * واجهة «الاحتياجات» مع زرّي (حذف نهائي / استرجاع إلى الحالة الأولية).
 */
class RejectMaterialRequestUseCase @Inject constructor(
    private val requestRepo: MaterialRequestRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(request: MaterialRequest) {
        require(request.status == RequestStatus.PENDING) { "الطلب ليس بانتظار الموافقة" }
        requestRepo.update(
            request.copy(
                status = RequestStatus.REJECTED,
                resolvedDate = System.currentTimeMillis()
            )
        )
        touchSite(siteRepo, request.siteId)
    }
}

/**
 * استرجاع طلب مرفوض (تعليمات الجلسة): يعيده إلى حالته الأولية قبل
 * الرفض — «بانتظار الموافقة» — فتعود أزرار (موافقة/رفض/حذف) تحته.
 */
class RestoreMaterialRequestUseCase @Inject constructor(
    private val requestRepo: MaterialRequestRepository,
    private val siteRepo: SiteRepository
) {
    suspend operator fun invoke(request: MaterialRequest) {
        require(request.status == RequestStatus.REJECTED) { "الطلب ليس مرفوضًا" }
        requestRepo.update(
            request.copy(status = RequestStatus.PENDING, resolvedDate = null)
        )
        touchSite(siteRepo, request.siteId)
    }
}

/** حذف طلب احتياج نهائيًا من واجهة «الاحتياجات» */
class DeleteMaterialRequestUseCase @Inject constructor(
    private val requestRepo: MaterialRequestRepository
) {
    suspend operator fun invoke(request: MaterialRequest) = requestRepo.delete(request)
}
