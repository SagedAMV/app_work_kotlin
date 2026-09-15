package com.majarra.galaxy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertThrows(IllegalArgumentException::class.java) { RadioMath.fsplDb(0.0, 100.0) }
        assertThrows(IllegalArgumentException::class.java) { RadioMath.fsplDb(10.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { RadioMath.fsplDb(10.0, -5.0) }
        // قيم غير منتهية (NaN / لانهاية) يجب أن تُرفض أيضًا
        assertThrows(IllegalArgumentException::class.java) { RadioMath.fsplDb(Double.NaN, 100.0) }
        assertThrows(IllegalArgumentException::class.java) {
            RadioMath.fsplDb(10.0, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `فرينل منتصف المسار عند 1 جيجاهرتز`() {
        // d1=d2=5 و D=10 و f=1GHz → 17.32 * sqrt(25/10) ≈ 27.38 م
        assertEquals(27.38, RadioMath.fresnelRadiusM(5.0, 5.0, 1000.0), 0.05)
    }

    @Test
    fun `فرينل يتناسب عكسيًا مع جذر التردد`() {
        val at1GHz = RadioMath.fresnelRadiusM(5.0, 5.0, 1000.0)
        val at4GHz = RadioMath.fresnelRadiusM(5.0, 5.0, 4000.0)
        // مضاعفة التردد 4 مرات تقلّل نصف القطر للنصف (جذر 4 = 2)
        assertEquals(at1GHz / 2, at4GHz, 0.01)
    }

    @Test
    fun `خلوص فرينل المطلوب يساوي 60 بالمئة من نصف القطر`() {
        val radius = RadioMath.fresnelRadiusM(5.0, 5.0, 5800.0)
        assertEquals(0.6 * radius, RadioMath.fresnelRequiredM(5.0, 5.0, 5800.0), 1e-9)
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
    fun `خط النظر يحسب خلوص فرينل عند تمرير التردد`() {
        val geometric = RadioMath.lineOfSight(20.0, 20.0, 10.0, 1.0, 1.0)
        assertTrue(geometric.clear)
        assertEquals(0.0, geometric.requiredClearanceM, 1e-9)

        val withFresnel = RadioMath.lineOfSight(20.0, 20.0, 10.0, 1.0, 1.0, freqMHz = 10000.0)
        assertTrue(withFresnel.requiredClearanceM > 0.0)
        // الهامش الهندسي كما هو، لكن القرار يعتمد على الخلوص المطلوب
        assertEquals(geometric.marginM, withFresnel.marginM, 1e-9)
    }

    @Test
    fun `انتفاخ الأرض يزيد بازدياد المسافة`() {
        val near = RadioMath.earthBulgeM(1.0, 1.0)
        val far = RadioMath.earthBulgeM(5.0, 5.0)
        assertTrue(far > near)
        assertTrue(near in 0.05..0.2)
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

    @Test
    fun `هافرساين يرفض إحداثيات خارج النطاق`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadioMath.haversineKm(100.0, 44.0, 33.0, 44.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioMath.haversineKm(33.0, 200.0, 33.0, 44.0)
        }
    }

    @Test
    fun `هافرساين يبقى مستقرًا عند النقاط المتناظرة`() {
        // نقاط متناظرة تمامًا (نصف محيط الأرض) — لا NaN ولا فقدان دقة
        val d = RadioMath.haversineKm(0.0, 0.0, 0.0, 180.0)
        assertTrue(d.isFinite())
        assertTrue(d in 20000.0..20020.0)
    }
}
