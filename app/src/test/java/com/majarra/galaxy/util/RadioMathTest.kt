package com.majarra.galaxy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات وحدة لمحرك حسابات المجرة — القسم 6 من التعليمات */
class RadioMathTest {

    @Test
    fun `fspl قيمة معروفة`() {
        // d=10كم و f=1000م.هـ → 20 + 60 + 32.44 = 112.44
        assertEquals(112.44, RadioMath.fsplDb(10.0, 1000.0), 0.01)
    }

    @Test
    fun `fspl يرفض القيم غير الصالحة`() {
        var thrown = false
        try {
            RadioMath.fsplDb(0.0, 100.0)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `فرينل منتصف المسار`() {
        // d1=d2=5، f=1000 → 17.32 * sqrt(25/10000) ≈ 0.866 م
        assertEquals(0.866, RadioMath.fresnelRadiusM(5.0, 5.0, 1000.0), 0.01)
    }

    @Test
    fun `موازنة الرابط حساب متسق`() {
        val r = RadioMath.linkBudget(
            txPowerDbm = 23.0,
            txGainDbi = 30.0,
            rxGainDbi = 30.0,
            cableLossDb = 2.0,
            distanceKm = 10.0,
            freqMHz = 5800.0
        )
        // FSPL = 20 + 20log10(5800) + 32.44 ≈ 127.71
        assertEquals(127.71, r.fsplDb, 0.05)
        // Received = 23 + 30 + 30 − 127.71 − 2 ≈ −46.71
        assertEquals(-46.71, r.receivedPowerDbm, 0.05)
        assertTrue(r.viable) // فوق حساسية −75
        assertTrue(r.marginDb > 25.0)
    }

    @Test
    fun `خط النظر صافٍ`() {
        val result = RadioMath.lineOfSight(
            txHeightM = 30.0, rxHeightM = 30.0, obstacleHeightM = 0.0, d1Km = 5.0, d2Km = 5.0
        )
        assertTrue(result.clear)
        assertTrue(result.marginM > 20.0)
    }

    @Test
    fun `خط النظر محجوب بعائق مرتفع`() {
        val result = RadioMath.lineOfSight(
            txHeightM = 5.0, rxHeightM = 5.0, obstacleHeightM = 50.0, d1Km = 5.0, d2Km = 5.0
        )
        assertFalse(result.clear)
    }

    @Test
    fun `هافرساين مسافة صفرية لنفس النقطة`() {
        assertEquals(0.0, RadioMath.haversineKm(33.0, 44.0, 33.0, 44.0), 1e-9)
    }

    @Test
    fun `هافرساين مسافة تقريبية معروفة`() {
        // بغداد ≈ 33.3,44.3 والرمادي ≈ 33.4,43.3 → قرابة 93 كم
        val d = RadioMath.haversineKm(33.3, 44.3, 33.4, 43.3)
        assertTrue("المسافة $d خارج النطاق المتوقع", d in 85.0..100.0)
    }
}
