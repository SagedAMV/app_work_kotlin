package com.majarra.galaxy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * قاعدة بيانات «مجرة» — أوفلاين بالكامل (Room).
 * الإصدار 2: أُضيفت فهارس أداء (انظر GalaxyMigrations) مع ترحيل غير مدمّر.
 */
@Database(
    entities = [
        Site::class,
        Equipment::class,
        Attachment::class,
        SiteHistory::class,
        InventoryItem::class,
        Requirement::class,
        RequirementItem::class,
        BoqDocument::class,
        BoqLine::class,
        Link::class,
        Ticket::class,
        WorkOrder::class,
        MaintenanceSchedule::class,
        Alert::class,
        AuditLog::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(GalaxyConverters::class)
abstract class GalaxyDatabase : RoomDatabase() {

    abstract fun siteDao(): SiteDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun siteHistoryDao(): SiteHistoryDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun requirementDao(): RequirementDao
    abstract fun requirementItemDao(): RequirementItemDao
    abstract fun boqDao(): BoqDao
    abstract fun linkDao(): LinkDao
    abstract fun ticketDao(): TicketDao
    abstract fun workOrderDao(): WorkOrderDao
    abstract fun maintenanceScheduleDao(): MaintenanceScheduleDao
    abstract fun alertDao(): AlertDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DB_NAME = "galaxy.db"
    }
}
