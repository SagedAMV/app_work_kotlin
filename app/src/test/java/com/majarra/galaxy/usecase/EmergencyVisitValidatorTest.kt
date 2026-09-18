package com.majarra.galaxy.usecase

import com.majarra.galaxy.domain.usecase.EmergencyVisitValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * اختبارات وحدة لمحقق مدخلات النزول الطارئ (النسخة 2.4):
 * سبب النزول إلزامي، والبقية اختيارية مع تطبيع وحدود طول.
 */
class EmergencyVisitValidatorTest {

    @Test
    fun `valid input is normalized`() {
        val result = EmergencyVisitValidator.normalize(
            reason = "  فحص   بلاغ  انقطاع  ",
            problemDescription = "  تعطل   الجهاز ",
            analysis = "",
            usedMaterials = listOf("بطارية")
        )
        assertEquals("فحص بلاغ انقطاع", result.reason)
        assertEquals("تعطل الجهاز", result.problemDescription)
        assertEquals("", result.analysis)
        assertEquals(listOf("بطارية"), result.usedMaterials)
    }

    @Test
    fun `blank reason is rejected`() {
        try {
            EmergencyVisitValidator.normalize("   ", "", "", emptyList())
            fail("سبب النزول الفارغ يجب أن يُرفض")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("سبب النزول مطلوب"))
        }
    }

    @Test
    fun `too long reason is rejected`() {
        val longReason = "س".repeat(EmergencyVisitValidator.MAX_REASON + 1)
        try {
            EmergencyVisitValidator.normalize(longReason, "", "", emptyList())
            fail("سبب النزول الطويل جدًا يجب أن يُرفض")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("طويل جدًا"))
        }
    }

    @Test
    fun `max length reason is accepted`() {
        val reason = "س".repeat(EmergencyVisitValidator.MAX_REASON)
        val result = EmergencyVisitValidator.normalize(reason, "", "", emptyList())
        assertEquals(reason, result.reason)
    }

    @Test
    fun `problem description and analysis are truncated to limits`() {
        val result = EmergencyVisitValidator.normalize(
            reason = "نزول",
            problemDescription = "م".repeat(EmergencyVisitValidator.MAX_PROBLEM + 100),
            analysis = "ح".repeat(EmergencyVisitValidator.MAX_ANALYSIS + 100),
            usedMaterials = emptyList()
        )
        assertEquals(EmergencyVisitValidator.MAX_PROBLEM, result.problemDescription.length)
        assertEquals(EmergencyVisitValidator.MAX_ANALYSIS, result.analysis.length)
    }

    @Test
    fun `optional fields may be empty`() {
        val result = EmergencyVisitValidator.normalize("استكشاف روتيني", "", "", emptyList())
        assertEquals("استكشاف روتيني", result.reason)
        assertEquals("", result.problemDescription)
        assertEquals("", result.analysis)
        assertEquals("", result.usedMaterialsText)
    }

    @Test
    fun `materials are cleaned deduplicated and joined by newline`() {
        val result = EmergencyVisitValidator.normalize(
            reason = "نزول",
            problemDescription = "",
            analysis = "",
            usedMaterials = listOf(" بطارية ", "بطارية", "", "لوح شمسي")
        )
        assertEquals(listOf("بطارية", "لوح شمسي"), result.usedMaterials)
        assertEquals("بطارية\nلوح شمسي", result.usedMaterialsText)
    }
}
