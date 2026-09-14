package com.majarra.galaxy.domain.model

/**
 * جميع التعدادات (Enums) في تطبيق «مجرة».
 * كل تعداد يحمل `label` عربيًا لعرضه في الواجهات،
 * ويُخزَّن في Room باسمه الإنجليزي عبر TypeConverters.
 */

/** حالة الموقع */
enum class SiteStatus(val label: String) {
    ACTIVE("يعمل"),
    DEGRADED("متدهور"),
    DOWN("معطل"),
    PLANNED("مخطط");
}

/** تصنيف المعدة */
enum class EquipmentCategory(val label: String) {
    AERIAL("هوائي"),
    RADIO("راديو"),
    MICROWAVE("ميكروويف"),
    SATELLITE("ساتلايت"),
    SWITCH("سويتش"),
    ROUTER("راوتر"),
    SOLAR("لوح شمسي"),
    BATTERY("بطارية"),
    REGULATOR("منظم"),
    CHARGER("شاحن"),
    CABLE("كابل"),
    OTHER("أخرى");
}

/** حالة المعدة */
enum class EquipmentStatus(val label: String) {
    WORKING("تعمل"),
    FAULTY("معطلة"),
    SPARE("احتياط"),
    RETIRED("خارج الخدمة");
}

/** نوع المرفق */
enum class AttachmentType(val label: String) {
    IMAGE("صورة"),
    PDF("مستند PDF");
}

/** وحدة القياس في المخزون */
enum class InventoryUnit(val label: String) {
    PIECE("قطعة"),
    METER("متر"),
    LITER("لتر"),
    SET("طقم");
}

/** نوع الاحتياج */
enum class RequirementType(val label: String) {
    MATERIALS("مواد"),
    DEVICES("أجهزة"),
    MAINTENANCE("صيانة");
}

/** حالة الاحتياج */
enum class RequirementStatus(val label: String) {
    DRAFT("مسودة"),
    PENDING("قيد الاعتماد"),
    APPROVED("معتمد"),
    FULFILLED("مصروف"),
    CANCELLED("ملغي");
}

/** نوع الرابط في المجرة */
enum class LinkType(val label: String) {
    MICROWAVE("ميكروويف"),
    FIBER("ألياف"),
    SATELLITE("ساتلايت"),
    VHF("VHF"),
    UHF("UHF"),
    CELLULAR("خلوي"),
    IP("IP");
}

/** حالة الرابط */
enum class LinkStatus(val label: String) {
    ACTIVE("نشط"),
    DEGRADED("متدهور"),
    DOWN("معطل"),
    PLANNED("مخطط");
}

/** تصنيف الشبكة */
enum class NetworkClass(val label: String) {
    COMMAND("قيادة"),
    OPERATIONS("عمليات"),
    BACKUP("احتياطية"),
    EMERGENCY("طوارئ");
}

/** أولوية الرابط */
enum class LinkPriority(val label: String) {
    CRITICAL("حرجة"),
    HIGH("عالية"),
    NORMAL("عادية"),
    LOW("منخفضة");
}

/** خطورة التذكرة */
enum class TicketSeverity(val label: String) {
    CRITICAL("حرجة"),
    HIGH("عالية"),
    MEDIUM("متوسطة"),
    LOW("منخفضة");
}

/** حالة التذكرة */
enum class TicketStatus(val label: String) {
    OPEN("مفتوح"),
    IN_PROGRESS("قيد المعالجة"),
    CLOSED("مغلق");
}

/** حالة أمر الشغل */
enum class WorkOrderStatus(val label: String) {
    SCHEDULED("مجدول"),
    IN_PROGRESS("قيد التنفيذ"),
    DONE("مكتمل"),
    CANCELLED("ملغي");
}

/** نوع الصيانة الوقائية */
enum class MaintenanceType(val label: String) {
    PREVENTIVE("وقائية"),
    INSPECTION("فحص دوري"),
    CALIBRATION("معايرة"),
    CLEANING("تنظيف");
}

/** نوع التنبيه */
enum class AlertType(val label: String) {
    MAINTENANCE_DUE("صيانة مستحقة"),
    END_OF_LIFE("انتهاء عمر معدة"),
    LOW_STOCK("نقص مخزون");
}
