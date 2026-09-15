package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import javax.inject.Inject

/** نافذة التنبيه: إشعار قبل موعد الصيانة بسبعة أيام أو عند تجاوزه */
const val DUE_WINDOW_DAYS = 7

private const val DAY_MS = 24L * 3600 * 1000

/** حفظ إدخال جديد في سجل الصيانة مع تطبيع المدخلات */
class SaveMaintenanceLogUseCase @Inject constructor(
    private val logRepo: MaintenanceLogRepository
) {
    /** @throws IllegalArgumentException عند ملاحظات فارغة */
    suspend operator fun invoke(log: MaintenanceLog): Long {
        val notes = log.notes.trim()
        require(notes.isNotEmpty()) { "ملاحظات الصيانة مطلوبة" }
        return logRepo.insert(log.copy(notes = notes, performedBy = log.performedBy.trim()))
    }
}

/** حذف إدخال من سجل الصيانة */
class DeleteMaintenanceLogUseCase @Inject constructor(
    private val logRepo: MaintenanceLogRepository
) {
    suspend operator fun invoke(log: MaintenanceLog) = logRepo.delete(log)
}

/** تحديد موعد الصيانة القادمة لموقع (أو مسحه عند تمرير null) */
class SetNextMaintenanceUseCase @Inject constructor(
    private val detailRepo: SiteDetailRepository
) {
    suspend operator fun invoke(siteId: Long, due: Long?) {
        val existing = detailRepo.getBySite(siteId)
        detailRepo.upsert(
            existing?.copy(nextMaintenanceDue = due)
                ?: SiteDetail(siteId = siteId, nextMaintenanceDue = due)
        )
    }
}

/** موقع اقترب موعد صيانته — الاسم للعرض في الإشعار */
data class DueSite(val siteId: Long, val siteName: String, val dueDate: Long)

/**
 * فحص المواقع التي تستحق تنبيه الصيانة (مستحقة الآن أو خلال 7 أيام).
 * تُستدعى عند فتح التطبيق فقط — بلا عمال خلفية ولا جدولة دورية.
 */
class CheckMaintenanceDueUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val detailRepo: SiteDetailRepository
) {
    suspend operator fun invoke(): List<DueSite> {
        val cutoff = System.currentTimeMillis() + DUE_WINDOW_DAYS * DAY_MS
        val dueDetails = detailRepo.getAll().filter { detail ->
            detail.nextMaintenanceDue != null && detail.nextMaintenanceDue <= cutoff
        }
        if (dueDetails.isEmpty()) return emptyList()

        // قراءة واحدة لكل المواقع بدل استعلام لكل موقع على حدة
        val namesById = siteRepo.getAll().associate { it.id to it.name }

        return dueDetails.mapNotNull { detail ->
            val due = detail.nextMaintenanceDue ?: return@mapNotNull null
            DueSite(
                siteId = detail.siteId,
                siteName = namesById[detail.siteId] ?: return@mapNotNull null,
                dueDate = due
            )
        }
    }
}
