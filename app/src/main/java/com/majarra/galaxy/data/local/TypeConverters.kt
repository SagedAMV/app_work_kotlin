package com.majarra.galaxy.data.local

import android.util.Log
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

    private companion object {
        const val TAG = "GalaxyConverters"
    }


    @TypeConverter fun siteStatusToDb(v: SiteStatus): String = v.name
    @TypeConverter fun siteStatus(v: String): SiteStatus =
        runCatching { SiteStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default ACTIVE")
            SiteStatus.ACTIVE
        }

    @TypeConverter fun equipmentCategoryToDb(v: EquipmentCategory): String = v.name
    @TypeConverter fun equipmentCategory(v: String): EquipmentCategory =
        runCatching { EquipmentCategory.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default OTHER")
            EquipmentCategory.OTHER
        }

    @TypeConverter fun equipmentStatusToDb(v: EquipmentStatus): String = v.name
    @TypeConverter fun equipmentStatus(v: String): EquipmentStatus =
        runCatching { EquipmentStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default WORKING")
            EquipmentStatus.WORKING
        }

    @TypeConverter fun attachmentTypeToDb(v: AttachmentType): String = v.name
    @TypeConverter fun attachmentType(v: String): AttachmentType =
        runCatching { AttachmentType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default IMAGE")
            AttachmentType.IMAGE
        }

    @TypeConverter fun inventoryUnitToDb(v: InventoryUnit): String = v.name
    @TypeConverter fun inventoryUnit(v: String): InventoryUnit =
        runCatching { InventoryUnit.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default PIECE")
            InventoryUnit.PIECE
        }

    @TypeConverter fun requirementTypeToDb(v: RequirementType): String = v.name
    @TypeConverter fun requirementType(v: String): RequirementType =
        runCatching { RequirementType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default MATERIALS")
            RequirementType.MATERIALS
        }

    @TypeConverter fun requirementStatusToDb(v: RequirementStatus): String = v.name
    @TypeConverter fun requirementStatus(v: String): RequirementStatus =
        runCatching { RequirementStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default DRAFT")
            RequirementStatus.DRAFT
        }

    @TypeConverter fun linkTypeToDb(v: LinkType): String = v.name
    @TypeConverter fun linkType(v: String): LinkType =
        runCatching { LinkType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default MICROWAVE")
            LinkType.MICROWAVE
        }

    @TypeConverter fun linkStatusToDb(v: LinkStatus): String = v.name
    @TypeConverter fun linkStatus(v: String): LinkStatus =
        runCatching { LinkStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default ACTIVE")
            LinkStatus.ACTIVE
        }

    @TypeConverter fun networkClassToDb(v: NetworkClass): String = v.name
    @TypeConverter fun networkClass(v: String): NetworkClass =
        runCatching { NetworkClass.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default OPERATIONS")
            NetworkClass.OPERATIONS
        }

    @TypeConverter fun linkPriorityToDb(v: LinkPriority): String = v.name
    @TypeConverter fun linkPriority(v: String): LinkPriority =
        runCatching { LinkPriority.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default NORMAL")
            LinkPriority.NORMAL
        }

    @TypeConverter fun ticketSeverityToDb(v: TicketSeverity): String = v.name
    @TypeConverter fun ticketSeverity(v: String): TicketSeverity =
        runCatching { TicketSeverity.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default MEDIUM")
            TicketSeverity.MEDIUM
        }

    @TypeConverter fun ticketStatusToDb(v: TicketStatus): String = v.name
    @TypeConverter fun ticketStatus(v: String): TicketStatus =
        runCatching { TicketStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default OPEN")
            TicketStatus.OPEN
        }

    @TypeConverter fun workOrderStatusToDb(v: WorkOrderStatus): String = v.name
    @TypeConverter fun workOrderStatus(v: String): WorkOrderStatus =
        runCatching { WorkOrderStatus.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default SCHEDULED")
            WorkOrderStatus.SCHEDULED
        }

    @TypeConverter fun maintenanceTypeToDb(v: MaintenanceType): String = v.name
    @TypeConverter fun maintenanceType(v: String): MaintenanceType =
        runCatching { MaintenanceType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default PREVENTIVE")
            MaintenanceType.PREVENTIVE
        }

    @TypeConverter fun alertTypeToDb(v: AlertType): String = v.name
    @TypeConverter fun alertType(v: String): AlertType =
        runCatching { AlertType.valueOf(v) }.getOrElse {
            // قيمة مجهولة (نسخة أقدم أو تعديل يدوي): لا نسقطها بصمت،
            // بل نسجل تحذيرا واضحا ثم نرجع القيمة الافتراضية الآمنة.
            Log.w(TAG, "enum value unknown in db: $v -> default MAINTENANCE_DUE")
            AlertType.MAINTENANCE_DUE
        }
}
