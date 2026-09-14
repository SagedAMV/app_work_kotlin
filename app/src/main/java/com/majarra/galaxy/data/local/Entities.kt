package com.majarra.galaxy.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.majarra.galaxy.domain.model.AlertType
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.EquipmentCategory
import com.majarra.galaxy.domain.model.EquipmentStatus
import com.majarra.galaxy.domain.model.InventoryUnit
import com.majarra.galaxy.domain.model.LinkPriority
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.domain.model.LinkType
import com.majarra.galaxy.domain.model.MaintenanceType
import com.majarra.galaxy.domain.model.NetworkClass
import com.majarra.galaxy.domain.model.RequirementStatus
import com.majarra.galaxy.domain.model.RequirementType
import com.majarra.galaxy.domain.model.SiteStatus
import com.majarra.galaxy.domain.model.TicketSeverity
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.model.WorkOrderStatus

/* ============================================================
 * كيانات قاعدة بيانات «مجرة» — حسب نموذج البيانات في تعليمات.md
 * جميع المفاتيح الخارجية بحذف متسلسل (CASCADE) للحفاظ على الاتساق.
 * ============================================================ */

/** 3.1 المواقع */
@Entity(tableName = "sites", indices = [Index(value = ["code"], unique = true)])
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: SiteStatus = SiteStatus.ACTIVE,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 3.2 المعدات — أضفنا installDate و lifespanMonths لدعم قاعدة «انتهاء عمر المعدة» */
@Entity(
    tableName = "equipments",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class Equipment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val category: EquipmentCategory,
    val company: String = "",
    val model: String = "",
    val serialNumber: String = "",
    val status: EquipmentStatus = EquipmentStatus.WORKING,
    val notes: String = "",
    val installDate: Long = System.currentTimeMillis(),
    val lifespanMonths: Int = 0
)

/** 3.3 المرفقات */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val type: AttachmentType,
    val uri: String,
    val caption: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

/** 3.4 سجل الموقع التاريخي */
@Entity(
    tableName = "site_history",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class SiteHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val action: String,
    val oldValue: String = "",
    val newValue: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/** 3.5 المخزون المركزي */
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    val unit: InventoryUnit = InventoryUnit.PIECE,
    val quantity: Int = 0,
    val minThreshold: Int = 1,
    val location: String = ""
)

/** 3.6 الاحتياج */
@Entity(
    tableName = "requirements",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class Requirement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val type: RequirementType,
    val status: RequirementStatus = RequirementStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

/** 3.7 بنود الاحتياج */
@Entity(
    tableName = "requirement_items",
    foreignKeys = [
        ForeignKey(
            entity = Requirement::class, parentColumns = ["id"], childColumns = ["requirementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("requirementId")]
)
data class RequirementItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requirementId: Long,
    val inventoryItemId: Long? = null,
    val description: String,
    val quantity: Int = 1,
    val fulfilled: Boolean = false
)

/** 3.8 وثيقة BOQ — تُولَّد تلقائيًا من الاحتياج */
@Entity(
    tableName = "boq_documents",
    foreignKeys = [
        ForeignKey(
            entity = Requirement::class, parentColumns = ["id"], childColumns = ["requirementId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("requirementId")]
)
data class BoqDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requirementId: Long,
    val siteId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** 3.8 ب سطر بند في وثيقة BOQ */
@Entity(
    tableName = "boq_lines",
    foreignKeys = [
        ForeignKey(
            entity = BoqDocument::class, parentColumns = ["id"], childColumns = ["boqId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("boqId")]
)
data class BoqLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val boqId: Long,
    val description: String,
    val quantity: Int,
    val unit: String
)

/** 3.9 الروابط بين المواقع (المجرة) */
@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["sourceSiteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["targetSiteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceSiteId"), Index("targetSiteId")]
)
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSiteId: Long,
    val targetSiteId: Long,
    val type: LinkType,
    val status: LinkStatus = LinkStatus.ACTIVE,
    val networkClass: NetworkClass = NetworkClass.OPERATIONS,
    val priority: LinkPriority = LinkPriority.NORMAL,
    val frequencyMHz: Double = 0.0,
    val distanceKm: Double = 0.0,
    val notes: String = ""
)

/** 3.10 تذاكر الأعطال */
@Entity(
    tableName = "tickets",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val equipmentId: Long? = null,
    val linkId: Long? = null,
    val title: String,
    val description: String = "",
    val severity: TicketSeverity = TicketSeverity.MEDIUM,
    val status: TicketStatus = TicketStatus.OPEN,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val resolutionTimeMinutes: Long? = null
)

/** 3.11 أوامر الشغل — المهام تُخزَّن كنص مفصول بأسطر */
@Entity(
    tableName = "work_orders",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class WorkOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long? = null,
    val siteId: Long,
    val assignedTo: String,
    val tasks: String,
    val status: WorkOrderStatus = WorkOrderStatus.SCHEDULED,
    val scheduledAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/** 3.12 الصيانة الوقائية */
@Entity(
    tableName = "maintenance_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class MaintenanceSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val equipmentId: Long,
    val type: MaintenanceType,
    val intervalDays: Int = 90,
    val lastDone: Long = System.currentTimeMillis(),
    val nextDue: Long,
    val notes: String = ""
)

/** 3.13 التنبيهات */
@Entity(tableName = "alerts", indices = [Index(value = ["type", "refId"])])
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AlertType,
    val refId: Long,
    val message: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

/** 3.14 سجل التدقيق — كل عملية إضافة/تعديل/حذف تُسجَّل هنا */
@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)
