package com.majarra.galaxy.domain.model

/**
 * التعدادات (Enums) في تطبيق «مجرة» — النسخة المبسطة.
 * بقي نوع المرفقات فقط بعد حذف أنظمة المعدات والروابط والتذاكر
 * والمخزون والصيانة الوقائية.
 */

/** نوع المرفق: صورة تُعرض في المعرض أو مستند PDF يُدرج في القائمة */
enum class AttachmentType(val label: String) {
    IMAGE("صورة"),
    PDF("مستند PDF");
}
