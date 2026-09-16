package com.majarra.galaxy.util

/* ============================================================
 * قوائم المواد بعلامات ✔ (إجابة الاسئله.md).
 *
 * التخزين يبقى نصًا في نفس أعمدة النسخ السابقة (بلا ترحيل إضافي):
 *   - سطر «[x] مادة» = عنصر محدد
 *   - سطر «[ ] مادة» = عنصر غير محدد
 *   - سطر بلا بادئة (بيانات قديمة) = عنصر غير محدد تلقائيًا،
 *     فيبقى كل ما كتبه المستخدم سابقًا محفوظًا ومقروءًا.
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

    /** عدد العناصر غير المحددة (ما زال مطلوبًا/موجودًا فعليًا) */
    fun remaining(items: List<MaterialItem>): Int = items.count { !it.checked }
}
