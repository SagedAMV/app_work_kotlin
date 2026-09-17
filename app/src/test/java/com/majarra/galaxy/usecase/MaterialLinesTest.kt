package com.majarra.galaxy.usecase

import com.majarra.galaxy.util.MaterialLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة لدوال قوائم المواد — التنسيق النصي «[ ] / [x]»
 * ودوال المزامنة المضافة في النسخة 2.2 (سحب/إرجاع/إعادة تسمية).
 */
class MaterialLinesTest {

    @Test
    fun `parse and serialize round trip preserves state`() {
        val raw = "[x] بطارية 12 فولت\n[ ] لوح شمسي 100 واط"
        val items = MaterialLines.parse(raw)
        assertEquals(2, items.size)
        assertTrue(items[0].checked)
        assertEquals("بطارية 12 فولت", items[0].text)
        assertFalse(items[1].checked)
        assertEquals(raw, MaterialLines.serialize(items))
    }

    @Test
    fun `legacy lines without prefix parse as unchecked`() {
        val items = MaterialLines.parse("كيبل قديم")
        assertEquals(1, items.size)
        assertFalse(items[0].checked)
        assertEquals("كيبل قديم", items[0].text)
    }

    @Test
    fun `addUnchecked appends new item only once`() {
        val once = MaterialLines.addUnchecked("", "مايك لاسلكي")
        assertEquals("[ ] مايك لاسلكي", once)
        val twice = MaterialLines.addUnchecked(once, "مايك لاسلكي")
        assertEquals(once, twice)
    }

    @Test
    fun `addUnchecked keeps existing items`() {
        val raw = "[x] بطارية"
        val updated = MaterialLines.addUnchecked(raw, "جهاز يدوي")
        assertEquals("[x] بطارية\n[ ] جهاز يدوي", updated)
    }

    @Test
    fun `addUnchecked ignores blank name`() {
        assertEquals("[ ] موجود", MaterialLines.addUnchecked("[ ] موجود", "   "))
    }

    @Test
    fun `markChecked checks matching item case-insensitively`() {
        val raw = "[ ] لوح شمسي"
        val updated = MaterialLines.markChecked(raw, "لوح شمسي")
        assertEquals("[x] لوح شمسي", updated)
    }

    @Test
    fun `markChecked without match leaves text unchanged`() {
        val raw = "[ ] بطارية"
        assertEquals(raw, MaterialLines.markChecked(raw, "غير موجودة"))
    }

    @Test
    fun `renameItem renames and preserves checked state`() {
        val raw = "[x] بطارية قديمة"
        val updated = MaterialLines.renameItem(raw, "بطارية قديمة", "بطارية جديدة")
        assertEquals("[x] بطارية جديدة", updated)
    }

    @Test
    fun `renameItem removes old line when new name already exists`() {
        val raw = "[ ] بطارية\n[ ] بطارية ليثيوم"
        val updated = MaterialLines.renameItem(raw, "بطارية", "بطارية ليثيوم")
        assertEquals("[ ] بطارية ليثيوم", updated)
    }

    @Test
    fun `renameItem without match leaves text unchanged`() {
        val raw = "[x] لوح شمسي"
        assertEquals(raw, MaterialLines.renameItem(raw, "غير موجود", "اسم جديد"))
    }

    @Test
    fun `hasItem detects items regardless of checked state`() {
        val raw = "[x] مايك"
        assertTrue(MaterialLines.hasItem(raw, "مايك"))
        assertFalse(MaterialLines.hasItem(raw, "جهاز"))
    }
}
