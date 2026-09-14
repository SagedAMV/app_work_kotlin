package com.majarra.galaxy.data.local

import androidx.room.TypeConverter
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

/**
 * محوّلات Room: تخزَّن التعدادات بأسمائها الإنجليزية،
 * مع قيمة افتراضية آمنة عند القراءة لحماية التطبيق من بيانات تالفة.
 */
class GalaxyConverters {

    @TypeConverter fun siteStatusToDb(v: SiteStatus): String = v.name
    @TypeConverter fun siteStatusFromDb(v: String): SiteStatus =
        runCatching { SiteStatus.valueOf(v) }.getOrDefault(SiteStatus.ACTIVE)

    @TypeConverter fun equipmentCategoryToDb(v: EquipmentCategory): String = v.name
    @TypeConverter fun equipmentCategoryFromDb(v: String): EquipmentCategory =
        runCatching { EquipmentCategory.valueOf(v) }.getOrDefault(EquipmentCategory.OTHER)

    @TypeConverter fun equipmentStatusToDb(v: EquipmentStatus): String = v.name
    @TypeConverter fun equipmentStatusFromDb(v: String): EquipmentStatus =
        runCatching { EquipmentStatus.valueOf(v) }.getOrDefault(EquipmentStatus.WORKING)

    @TypeConverter fun attachmentTypeToDb(v: AttachmentType): String = v.name
    @TypeConverter fun attachmentTypeFromDb(v: String): AttachmentType =
        runCatching { AttachmentType.valueOf(v) }.getOrDefault(AttachmentType.IMAGE)

    @TypeConverter fun inventoryUnitToDb(v: InventoryUnit): String = v.name
    @TypeConverter fun inventoryUnitFromDb(v: String): InventoryUnit =
        runCatching { InventoryUnit.valueOf(v) }.getOrDefault(InventoryUnit.PIECE)

    @TypeConverter fun requirementTypeToDb(v: RequirementType): String = v.name
    @TypeConverter fun requirementTypeFromDb(v: String): RequirementType =
        runCatching { RequirementType.valueOf(v) }.getOrDefault(RequirementType.MATERIALS)

    @TypeConverter fun requirementStatusToDb(v: RequirementStatus): String = v.name
    @TypeConverter fun requirementStatusFromDb(v: String): RequirementStatus =
        runCatching { RequirementStatus.valueOf(v) }.getOrDefault(RequirementStatus.DRAFT)

    @TypeConverter fun linkTypeToDb(v: LinkType): String = v.name
    @TypeConverter fun linkTypeFromDb(v: String): LinkType =
        runCatching { LinkType.valueOf(v) }.getOrDefault(LinkType.MICROWAVE)

    @TypeConverter fun linkStatusToDb(v: LinkStatus): String = v.name
    @TypeConverter fun linkStatusFromDb(v: String): LinkStatus =
        runCatching { LinkStatus.valueOf(v) }.getOrDefault(LinkStatus.ACTIVE)

    @TypeConverter fun networkClassToDb(v: NetworkClass): String = v.name
    @TypeConverter fun networkClassFromDb(v: String): NetworkClass =
        runCatching { NetworkClass.valueOf(v) }.getOrDefault(NetworkClass.OPERATIONS)

    @TypeConverter fun linkPriorityToDb(v: LinkPriority): String = v.name
    @TypeConverter fun linkPriorityFromDb(v: String): LinkPriority =
        runCatching { LinkPriority.valueOf(v) }.getOrDefault(LinkPriority.NORMAL)

    @TypeConverter fun ticketSeverityToDb(v: TicketSeverity): String = v.name
    @TypeConverter fun ticketSeverityFromDb(v: String): TicketSeverity =
        runCatching { TicketSeverity.valueOf(v) }.getOrDefault(TicketSeverity.MEDIUM)

    @TypeConverter fun ticketStatusToDb(v: TicketStatus): String = v.name
    @TypeConverter fun ticketStatusFromDb(v: String): TicketStatus =
        runCatching { TicketStatus.valueOf(v) }.getOrDefault(TicketStatus.OPEN)

    @TypeConverter fun workOrderStatusToDb(v: WorkOrderStatus): String = v.name
    @TypeConverter fun workOrderStatusFromDb(v: String): WorkOrderStatus =
        runCatching { WorkOrderStatus.valueOf(v) }.getOrDefault(WorkOrderStatus.SCHEDULED)

    @TypeConverter fun maintenanceTypeToDb(v: MaintenanceType): String = v.name
    @TypeConverter fun maintenanceTypeFromDb(v: String): MaintenanceType =
        runCatching { MaintenanceType.valueOf(v) }.getOrDefault(MaintenanceType.PREVENTIVE)

    @TypeConverter fun alertTypeToDb(v: AlertType): String = v.name
    @TypeConverter fun alertTypeFromDb(v: String): AlertType =
        runCatching { AlertType.valueOf(v) }.getOrDefault(AlertType.MAINTENANCE_DUE)
}
