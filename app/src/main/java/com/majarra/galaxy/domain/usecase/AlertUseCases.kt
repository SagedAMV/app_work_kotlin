package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.domain.model.AlertType
import com.majarra.galaxy.domain.model.EquipmentStatus
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.MaintenanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * فحص شامل لإنشاء التنبيهات تلقائيًا — قواعد العمل 4 و 5 و 6:
 * 1) صيانة وقائية تستحق خلال 7 أيام.
 * 2) معدة انتهى عمرها الافتراضي (تاريخ التركيب + العمر).
 * 3) مخزون وصل للحد الأدنى.
 * تُستخدم من عامل WorkManager الدوري ومن زر «افحص الآن».
 *
 * ملاحظة أداء: الأصناف المنخفضة تُقرأ باستعلام SQL مفهرس
 * (quantity <= minThreshold) بدل تحميل كل المخزون في الذاكرة.
 */
class CheckAlertsUseCase @Inject constructor(
    private val maintenanceRepo: MaintenanceRepository,
    private val equipmentRepo: EquipmentRepository,
    private val inventoryRepo: InventoryRepository,
    private val alertRepo: AlertRepository
) {
    suspend operator fun invoke(): Int {
        var created = 0
        val now = System.currentTimeMillis()

        // 1) صيانة تستحق خلال 7 أيام (قاعدة رقم 5)
        val cutoff = now + DUE_WINDOW_MS
        for (m in maintenanceRepo.getDueBefore(cutoff)) {
            if (!alertRepo.hasUnreadFor(AlertType.MAINTENANCE_DUE.name, m.id)) {
                alertRepo.insert(
                    Alert(
                        type = AlertType.MAINTENANCE_DUE,
                        refId = m.id,
                        message = "صيانة ${m.type.label} مستحقة ${if (m.nextDue <= now) "الآن" else "خلال 7 أيام"}"
                    )
                )
                created++
            }
        }

        // 2) انتهاء عمر المعدات (قاعدة رقم 6)
        for (e in equipmentRepo.getAll()) {
            if (e.lifespanMonths > 0 && e.status != EquipmentStatus.RETIRED) {
                val endOfLife = e.installDate + e.lifespanMonths * AVG_MONTH_MS
                if (now >= endOfLife && !alertRepo.hasUnreadFor(AlertType.END_OF_LIFE.name, e.id)) {
                    val name = e.model.ifBlank { e.category.label }
                    alertRepo.insert(
                        Alert(
                            type = AlertType.END_OF_LIFE,
                            refId = e.id,
                            message = "انتهى العمر الافتراضي للمعدة: $name"
                        )
                    )
                    created++
                }
            }
        }

        // 3) نقص المخزون (قاعدة رقم 4)
        for (i in inventoryRepo.getBelowThreshold()) {
            if (!alertRepo.hasUnreadFor(AlertType.LOW_STOCK.name, i.id)) {
                alertRepo.insert(
                    Alert(
                        type = AlertType.LOW_STOCK,
                        refId = i.id,
                        message = "نقص مخزون: ${i.name} — المتبقي ${i.quantity} ${i.unit.label}"
                    )
                )
                created++
            }
        }

        return created
    }

    private companion object {
        const val DUE_WINDOW_MS = 7L * 24 * 3600 * 1000
        const val AVG_MONTH_MS = 30L * 24 * 3600 * 1000
    }
}

/** مراقبة كل التنبيهات */
class ObserveAlertsUseCase @Inject constructor(
    private val repo: AlertRepository
) {
    operator fun invoke(): Flow<List<Alert>> = repo.observeAll()
    fun unreadCount(): Flow<Int> = repo.observeUnreadCount()
}

/** تعليم تنبيه كمقروء */
class MarkAlertReadUseCase @Inject constructor(
    private val repo: AlertRepository
) {
    suspend operator fun invoke(id: Long) = repo.markRead(id)
}

/** تعليم كل التنبيهات كمقروءة */
class MarkAllAlertsReadUseCase @Inject constructor(
    private val repo: AlertRepository
) {
    suspend operator fun invoke() = repo.markAllRead()
}

/** حذف تنبيه واحد (تجاهل) — يستخدم مسار الحذف في المستودع */
class DismissAlertUseCase @Inject constructor(
    private val repo: AlertRepository
) {
    suspend operator fun invoke(id: Long) = repo.delete(id)
}

/** مسح كل التنبيهات */
class ClearAlertsUseCase @Inject constructor(
    private val repo: AlertRepository
) {
    suspend operator fun invoke() = repo.deleteAll()
}
