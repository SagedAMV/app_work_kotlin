package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.WorkOrder
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.model.WorkOrderStatus
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.domain.repository.WorkOrderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** مراقبة التذاكر */
class ObserveTicketsUseCase @Inject constructor(
    private val repo: TicketRepository
) {
    operator fun invoke(): Flow<List<Ticket>> = repo.observeAll()
}

/** فتح تذكرة جديدة */
class OpenTicketUseCase @Inject constructor(
    private val repo: TicketRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(ticket: Ticket): Long {
        require(ticket.title.isNotBlank()) { "عنوان التذكرة مطلوب" }
        val id = repo.insert(ticket.copy(status = TicketStatus.OPEN))
        auditRepo.log("CREATE", "Ticket", id, ticket.title)
        return id
    }
}

/** بدء معالجة تذكرة */
class StartTicketUseCase @Inject constructor(
    private val repo: TicketRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(ticketId: Long) {
        val t = repo.getById(ticketId) ?: return
        if (t.status == TicketStatus.CLOSED) return
        repo.update(t.copy(status = TicketStatus.IN_PROGRESS))
        auditRepo.log("UPDATE", "Ticket", ticketId, "بدء المعالجة")
    }
}

/**
 * إغلاق تذكرة — قاعدة العمل رقم 2:
 * تسجيل resolutionTimeMinutes تلقائيًا لحظة الإغلاق.
 */
class CloseTicketUseCase @Inject constructor(
    private val repo: TicketRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(ticketId: Long): Boolean {
        val t = repo.getById(ticketId) ?: return false
        if (t.status == TicketStatus.CLOSED) return false
        val now = System.currentTimeMillis()
        val minutes = ((now - t.openedAt) / 60_000L).coerceAtLeast(0)
        repo.update(
            t.copy(
                status = TicketStatus.CLOSED,
                closedAt = now,
                resolutionTimeMinutes = minutes
            )
        )
        auditRepo.log("CLOSE", "Ticket", ticketId, "زمن المعالجة: $minutes دقيقة")
        return true
    }
}

/** مراقبة أوامر الشغل */
class ObserveWorkOrdersUseCase @Inject constructor(
    private val repo: WorkOrderRepository
) {
    operator fun invoke(): Flow<List<WorkOrder>> = repo.observeAll()
}

/** حفظ أمر شغل جديد أو تحديثه */
class SaveWorkOrderUseCase @Inject constructor(
    private val repo: WorkOrderRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(order: WorkOrder): Long {
        return if (order.id == 0L) {
            require(order.assignedTo.isNotBlank()) { "اسم المنفذ مطلوب" }
            val id = repo.insert(order)
            auditRepo.log("CREATE", "WorkOrder", id, order.assignedTo)
            id
        } else {
            if (order.status == WorkOrderStatus.DONE && order.completedAt == null) {
                repo.update(order.copy(completedAt = System.currentTimeMillis()))
            } else {
                repo.update(order)
            }
            auditRepo.log("UPDATE", "WorkOrder", order.id, order.status.label)
            order.id
        }
    }
}
