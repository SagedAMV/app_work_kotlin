package com.majarra.galaxy.util

/* ============================================================
 * قوائم المواد بعلامات ✔ (إجابة الاسئله.md).
 *
 * التخزين يبقى نصًا في نفس أعمدة النسخ السابقة (بلا ترحيل إضافي):
 *   - سطر «[x] مادة» = عنصر محدد
 *   - سطر «[ ] مادة» = عنصر غير محدد
 *   - سطر بلا بادئة (بيانات قديمة) = عنصر غير محدد تلقائيًا،
 *     فيبقى كل ما كتبه المستخدم سابقًا محفوظًا ومقروءًا.
 *
 * النسخة 2.2: دوال المزامنة مع سجل السحوبات (إضافة/تحديد/إعادة
 * تسمية) — دوال نصية خالصة قابلة للاختبار الوحدوي.
 * ============================================================ */

data class MaterialItem(val text: String, val checked: Boolean)

object MaterialLines {

    private const val CHECKED = "[x] "
    private const val UNCHECKED = "[ ] "

    /** تحليل نص الحقل إلى عناصر، متجاهلة الأسطر الفارغة */
    fun parse(raw: String): List<MaterialItem> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.startsWith(CHECKED, ignoreCase = true) ->
                        MaterialItem(line.substring(CHECKED.length).trim(), true)
                    line.startsWith(UNCHECKED) ->
                        MaterialItem(line.substring(UNCHECKED.length).trim(), false)
                    else -> MaterialItem(line, false)
                }
            }

    /** إعادة العناصر إلى نص التخزين الموحّد */
    fun serialize(items: List<MaterialItem>): String =
        items.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.checked) CHECKED else UNCHECKED) + it.text }

    /** هل يوجد عنصر بهذا الاسم (محددًا كان أم لا)؟ */
    fun hasItem(raw: String, name: String): Boolean =
        parse(raw).any { it.text.equals(name, ignoreCase = true) }

    /**
     * إضافة عنصر جديد غير محدد إن لم يكن موجودًا — تُستخدم عند سحب
     * مادة من موقع لتظهر تلقائيًا في قائمة «مواد تم سحبها».
     */
    fun addUnchecked(raw: String, name: String): String {
        if (name.isBlank() || hasItem(raw, name)) return raw
        return serialize(parse(raw) + MaterialItem(name.trim(), checked = false))
    }

    /**
     * تحديد العنصر المطابق للاسم بعلامة ✔ إن وُجد — تُستخدم عند
     * إرجاع المادة المسحوبة للموقع فتُوسم دورتها بالاكتمال.
     * إن لم يوجد العنصر يبقى النص كما هو (لا إضافة صامتة).
     */
    fun markChecked(raw: String, name: String): String {
        val items = parse(raw)
        if (items.none { it.text.equals(name, ignoreCase = true) }) return raw
        return serialize(
            items.map { if (it.text.equals(name, ignoreCase = true)) it.copy(checked = true) else it }
        )
    }

    /**
     * إعادة تسمية عنصر في النص الخام (إعادة تسمية مادة من الكتالوج
     * الموحد تنعكس على كل المواقع). إن كان الاسم الجديد موجودًا
     * مسبقًا يُحذف سطر الاسم القديم بدل تكرار العنصر.
     */
    fun renameItem(raw: String, oldName: String, newName: String): String {
        if (newName.isBlank()) return raw
        val items = parse(raw)
        if (items.none { it.text.equals(oldName, ignoreCase = true) }) return raw
        val hasNew = items.any { it.text.equals(newName, ignoreCase = true) }
        val updated = items.mapNotNull { item ->
            when {
                !item.text.equals(oldName, ignoreCase = true) -> item
                hasNew -> null // الاسم الجديد موجود: حذف القديم يمنع التكرار
                else -> item.copy(text = newName.trim())
            }
        }
        return serialize(updated)
    }
}
