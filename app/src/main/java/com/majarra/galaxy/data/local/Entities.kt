package com.majarra.galaxy.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.ItemType
import com.majarra.galaxy.domain.model.RequestStatus
import com.majarra.galaxy.domain.model.VisitOutcome
import com.majarra.galaxy.domain.model.WithdrawalStatus

/* ============================================================
 * كيانات قاعدة بيانات «مجرة».
 * الجداول الأساسية حسب تعليمات التبسيط:
 *   sites, site_details, maintenance_logs, attachments, app_settings
 * المضافة في النسخة 2.2 حسب تعليمات جلسة الإضافة/التعديل:
 *   materials (كتالوج المواد الموحد), withdrawals (سجل السحب والإرجاع)
 * المضافة في النسخة 2.4 حسب تعليمات جلستها:
 *   emergency_visits (سجل النزول الطارئ/الاستكشاف لكل موقع)
 * المضافة في النسخة 2.5 حسب تعليمات هذه الجلسة:
 *   site_links (روابط شبكة المجرة بين المواقع)
 * المضافة في النسخة 2.12 حسب تعليمات إعادة تصميم تفاصيل الموقع:
 *   material_requests (طلبات احتياج الموقع: موافقة/رفض/استرجاع)
 * جميع المفاتيح الخارجية بحذف متسلسل (CASCADE): حذف موقع يمسح
 * تفاصيله ومرفقاته وسجل صيانته وسحوباته ونزولاته وروابطه وطلباته
 * تلقائيًا للحفاظ على الاتساق.
 * ============================================================ */

/**
 * جدول المواقع — الاسم والملاحظات (بلا إحداثيات ولا رموز ولا حالات).
 *
 * الحقول المضافة في النسخة 2.1 حسب إجابات الاسئله.md:
 * - `categoryId`: تصنيف اختياري (اسم + لون)، حذف التصنيف يعيد الموقع
 *   إلى «بلا تصنيف» (ON DELETE SET NULL) فلا تُفقد المواقع.
 * - `archived`: الأرشفة تخفي الموقع من القائمة مع إمكانية الاستعادة،
 *   بدل الحذف النهائي المباشر.
 */
@Entity(
    tableName = "sites",
    indices = [Index("name"), Index("categoryId")],
    foreignKeys = [
        ForeignKey(
            entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Site(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val categoryId: Long? = null,
    val archived: Boolean = false
)

/**
 * تصنيفات المواقع (النسخة 2.1): اسم + لون سداسي.
 * اسم التصنيف فريد حتى لا تتكرر مجموعات متطابقة.
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
    val createdDate: Long = System.currentTimeMillis()
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
     * نوع التعداد مباشرة، والتخزين نصًا (اسم القيمة) يتم عبر
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

/**
 * كتالوج المواد الموحد (النسخة 2.2 — تعديل تبويب المواد):
 * المواد تُعرَّف هنا مرة واحدة ثم تختارها المواقع من واجهة اختيار،
 * بدل الكتابة النصية في كل موقع. اسم المادة فريد حتى لا تتكرر
 * مواد متطابقة في قوائم الاختيار.
 */
@Entity(tableName = "materials", indices = [Index(value = ["name"], unique = true)])
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdDate: Long = System.currentTimeMillis()
)

/**
 * سجل سحب المواد من المواقع وصيانتها وإرجاعها (النسخة 2.2 — الإضافة،
 * ومحدثة في 2.12 حسب تعليمات إعادة تصميم تفاصيل الموقع):
 * المستخدم يسحب مادة من قسم «المواد» في موقعها إلى الصيانة بسبب
 * إلزامي، ثم يقرر: تم الإصلاح (بوصف إلزامي لطريقة الإصلاح) أو لم
 * يتم الإصلاح (بسبب إلزامي)، ثم يعيدها إلى الموقع:
 *  - `itemType` + `itemName`: ما الذي سُحب (النوع قيمة قديمة من
 *    واجهة السحب السابقة؛ السجلات الجديدة تُخزَّن OTHER ولا تُعرض).
 *  - `status`: مسحوبة ← (تم الإصلاح | لم يتم الإصلاح) ← مُرجعة.
 *  - `withdrawnDate` / `returnedDate`: طرفا الدورة الزمنيان.
 *  - `withdrawReason`: سبب السحب للصيانة (إلزامي منذ 2.12).
 *  - `fixedNote`: كيف أُصلحت المشكلة (إلزامي عند قرار «تم الإصلاح»).
 *  - `notFixedReason`: سبب عدم الإصلاح (إلزامي عند قرار «لم يتم»).
 * الحذف المتسلسل (CASCADE) يمسح السحوبات مع موقعها تلقائيًا.
 */
@Entity(
    tableName = "withdrawals",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId"), Index("status")]
)
data class Withdrawal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val itemName: String,
    val itemType: ItemType,
    val withdrawnDate: Long = System.currentTimeMillis(),
    val status: WithdrawalStatus = WithdrawalStatus.WITHDRAWN,
    val notes: String = "",
    /** تاريخ الإرجاع الفعلي للموقع، يملأ تلقائيًا عند الإرجاع */
    val returnedDate: Long? = null,
    /** سبب السحب للصيانة — إلزامي منذ النسخة 2.12 */
    val withdrawReason: String = "",
    /** كيف أُصلحت المشكلة — إلزامي عند قرار «تم الإصلاح» (2.12) */
    val fixedNote: String = "",
    /** سبب عدم الإصلاح — إلزامي عند قرار «لم يتم الإصلاح» (2.12) */
    val notFixedReason: String = ""
)

/**
 * سجل النزول الطارئ/الاستكشاف (النسخة 2.4 — تعليمات هذه الجلسة،
 * ومحدثة في 2.12 حسب واجهة «النزول الطارئ» الجديدة):
 * عند نزول المستخدم إلى موقع نزولًا طارئًا يسجل هنا:
 *  - `reason`: سبب النزول أو الغرض منه (إلزامي).
 *  - `outcome`: النتيجة — لا توجد مشكلة / تم حل المشكلة / لم تُحل.
 *  - `notes`: ملاحظات عامة حرة (اختيارية، أُضيفت في 2.12).
 *  - `problemDescription`: وصف المشكلة مع حلها (تم الحل)، أو سبب عدم
 *    حل المشكلة (لم تُحل — إلزامي منذ 2.12).
 *  - `usedMaterials`: المواد التي ساعدت في حل المشكلة (اختيارية،
 *    أسماء من الكتالوج الموحد، اسم في كل سطر).
 *  - `analysis`: تحليلات المشكلة والحلول المتوقعة (اختيارية، قيمة
 *    قديمة تُعرض للسجلات السابقة فقط).
 * الحذف المتسلسل (CASCADE) يمسح النزولات مع موقعها تلقائيًا.
 */
@Entity(
    tableName = "emergency_visits",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId")]
)
data class EmergencyVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val visitDate: Long = System.currentTimeMillis(),
    val reason: String,
    val outcome: VisitOutcome,
    val problemDescription: String = "",
    val usedMaterials: String = "",
    val analysis: String = "",
    /** ملاحظات عامة حرة على النزول (اختيارية — النسخة 2.12) */
    val notes: String = ""
)

/**
 * طلبات احتياج الموقع (النسخة 2.12 — تعليمات إعادة تصميم تفاصيل
 * الموقع): يختار المستخدم مواد من الكتالوج الموحد (أو يضيف مادة
 * جديدة من نفس المكان) فترتفع طلبات هنا بانتظار الموافقة، ثم:
 *  - موافقة: تُضاف المادة إلى قسم «المواد» الرئيسي للموقع.
 *  - رفض: يبقى الطلب معروضًا مع زرّي حذف/استرجاع.
 *  - استرجاع: يعيد الطلب إلى حالته الأولية (بانتظار الموافقة).
 * كل طلب يحمل اسم المادة وحالته وتاريخ الطلب وتاريخ البت (إن بُتّ فيه).
 * الحذف المتسلسل (CASCADE) يمسح الطلبات مع موقعها تلقائيًا.
 */
@Entity(
    tableName = "material_requests",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["siteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("siteId"), Index("status")]
)
data class MaterialRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val siteId: Long,
    val materialName: String,
    val status: RequestStatus = RequestStatus.PENDING,
    val requestedDate: Long = System.currentTimeMillis(),
    /** تاريخ الموافقة أو الرفض — null ما دام الطلب بانتظار */
    val resolvedDate: Long? = null
)

/**
 * روابط شبكة «واجهة المجرة» (النسخة 2.5 — تعليمات هذه الجلسة):
 * كل سجل خط يربط موقعين في شبكة الاتصالات التنظيمية التي يبنيها
 * المستخدم من واجهة المجرة.
 *
 * قواعد الاتساق:
 *  - `fromSiteId` دائمًا الأصغر و`toSiteId` الأكبر (تطبيع الاتجاه في
 *    LinkSitesUseCase)، فالفهرس الفريد على الزوج يمنع تكرار الرابط
 *    مهما كان اتجاه إنشائه.
 *  - مفتاحان خارجيان بحذف متسلسل: حذف أي موقع من الطرفين يمسح كل
 *    روابطه تلقائيًا فلا تبقى خطوط تشير إلى موقع غير موجود.
 */
@Entity(
    tableName = "site_links",
    foreignKeys = [
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["fromSiteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Site::class, parentColumns = ["id"], childColumns = ["toSiteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fromSiteId", "toSiteId"], unique = true),
        Index("toSiteId")
    ]
)
data class SiteLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromSiteId: Long,
    val toSiteId: Long,
    val createdDate: Long = System.currentTimeMillis()
)
