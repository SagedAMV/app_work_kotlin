package com.majarra.galaxy.usecase

import com.majarra.galaxy.domain.usecase.SiteInputValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات وحدة لمحقق مدخلات الموقع — التحقق من الحدود والتطبيع.
 * هذه هي الطبقة الوحيدة التي تستحق اختبارات بعد التبسيط؛ بقية المنطق
 * تفويض مباشر إلى Room.
 */
class SiteInputValidatorTest {

    @Test
    fun `valid input is normalized`() {
        val (name, notes) = SiteInputValidator.normalize("  موقع   الرياض  ", "  ملاحظة  هنا ")
        assertEquals("موقع الرياض", name)
        assertEquals("ملاحظة هنا", notes)
    }

    @Test
    fun `blank name is rejected`() {
        try {
            SiteInputValidator.normalize("   ", "")
            fail("الاسم الفارغ يجب أن يُرفض")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("اسم الموقع مطلوب"))
        }
    }

    @Test
    fun `too long name is rejected`() {
        val longName = "م".repeat(SiteInputValidator.MAX_NAME + 1)
        try {
            SiteInputValidator.normalize(longName, "")
            fail("الاسم الطويل جدًا يجب أن يُرفض")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("طويل جدًا"))
        }
    }

    @Test
    fun `max length name is accepted`() {
        val name = "م".repeat(SiteInputValidator.MAX_NAME)
        val (normalized, _) = SiteInputValidator.normalize(name, "")
        assertEquals(name, normalized)
    }

    @Test
    fun `notes are truncated to the limit`() {
        val longNotes = "س".repeat(SiteInputValidator.MAX_NOTES + 100)
        val (_, notes) = SiteInputValidator.normalize("موقع", longNotes)
        assertEquals(SiteInputValidator.MAX_NOTES, notes.length)
    }

    @Test
    fun `empty notes are allowed`() {
        val (name, notes) = SiteInputValidator.normalize("موقع", "")
        assertEquals("موقع", name)
        assertEquals("", notes)
    }
}
