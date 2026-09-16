package com.majarra.galaxy.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.majarra.galaxy.domain.model.AttachmentType

/* ============================================================
 * كيانات قاعدة بيانات «مجرة» — النسخة المبسطة (2.0)
 * حسب نموذج البيانات في تعليمات.md، الجداول المطلوبة فقط:
 *   sites, site_details, maintenance_logs, attachments, app_settings
 * جميع المفاتيح الخارجية بحذف متسلسل (CASCADE): حذف موقع يمسح
 * تفاصيله ومرفقاته وسجل صيانته تلقائيًا للحفاظ على الاتساق.
 * ============================================================ */

/** جدول المواقع — الاسم والملاحظات فقط (بلا إحداثيات ولا رموز ولا حالات) */
@Entity(tableName = "sites", indices = [Index("name")])
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * البيانات الاختيارية للموقع — صف واحد لكل موقع (موقع فريد).
 * الحقول الأربعة نصوص حرة يضيفها المستخدم إن شاء:
 *  - الموجود حاليًا / الاحتياج / ما يحتاج صيانة / ما تم سحبه.
 *
 * حقل إضافي ضروري ضمنيًا: `nextMaintenanceDue` (تاريخ الصيانة القادمة)
 * لأن تعليمات التبسيط تطلب «تنبيهًا بسيطًا عند قرب موعد الصيانة المجدول»،
 * ولا يمكن تحقيق التنبيه دون مكان يُخزَّن فيه الموعد.
 */
@Entity(
    tableName = "site_details",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["siteId"], unique = true)]
)
data class SiteDetail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val availableMaterials: String = "",
    val neededMaterials: String = "",
    val maintenanceMaterials: String = "",
    val withdrawnMaterials: String = "",
    /** موعد الصيانة القادمة (epoch millis) أو null إن لم يُجدول */
    val nextMaintenanceDue: Long? = null
)

/** سجل الصيانة — سجل منفصل لكل موقع: التاريخ + الملاحظات + المنفّذ (اختياري) */
@Entity(
    tableName = "maintenance_logs",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class MaintenanceLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val maintenanceDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val performedBy: String = ""
)

/** المرفقات — صور/ملفات لكل موقع (لا حاجة لتقارير PDF معقدة) */
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
    val filePath: String,
    /**
     * نوع التعدادโดยตรง، والتخزين نصًا (اسم القيمة) يتم عبر
     * GalaxyConverters — سابقًا كان الحقل نصًا صريحًا مما جعل
     * المحوّلات كودًا ميتًا وعطّل الحماية من القيم التالفة.
     */
    val fileType: AttachmentType,
    val uploadedDate: Long = System.currentTimeMillis()
)

/** إعدادات التطبيق — أزواج مفتاح/قيمة (الوضع الليلي، القفل البسيط…) */
@Entity(
    tableName = "app_settings",
    indices = [Index(value = ["settingKey"], unique = true)]
)
data class AppSetting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settingKey: String,
    val settingValue: String
)
