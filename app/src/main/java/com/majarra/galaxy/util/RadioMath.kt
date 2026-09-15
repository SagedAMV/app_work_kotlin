package com.majarra.galaxy.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * محرك حسابات المجرة — حسب القسم 6 من ملف التعليمات:
 * - FSPL(dB) = 20log10(d) + 20log10(f) + 32.44   (d كم، f ميجاهرتز)
 * - Link Budget: Received = Tx + TxGain + RxGain − PathLoss − CableLoss
 * - Fresnel: r = 17.32 * sqrt(d1*d2 / (f_GHz * D))  ← الثابت 17.32 صالح عندما
 *   يكون التردد بالجيجاهرتز، لذلك نُحوّل من م.هـ داخل الدالة.
 * - LOS: تحقق من الارتفاعات + انحناء الأرض + معامل k (4/3).
 *
 * تصليب جلسة الفحص:
 * 1) نصف قطر فرينل كان يُحسب بالتردد بالميجاهرتز مع ثابت الجيجاهرتز ⇒ النتيجة
 *    أصغر من الصحيح 1000 مرة (قيمة غير فيزيائية). صُحّح التحويل داخليًا.
 * 2) هافرساين كان يستخدم asin(sqrt(a)) وهو يفقد الدقة ويُرجع NaN عند a ≈ 1
 *    (مسافات كبيرة جدًا / نقاط متناظرة)؛ استُبدل بـ atan2 وهو مستقر عدديًا.
 * 3) أُضيف تحقق من نطاق الإحداثيات والقيم غير المنتهية (NaN/∞).
 * 4) أُضيف خلوص فرينل المطلوب (60٪) كمعيار قرار لخط النظر عند تمرير التردد.
 */
object RadioMath {

    private const val EARTH_RADIUS_KM = 6371.0

    /** معامل انحناء الأرض القياسي للاتصالات (k = 4/3) */
    const val STANDARD_K = 4.0 / 3.0

    /** الحد الأدنى الموصى به لخلوص منطقة فرينل الأولى (60٪ من نصف القطر) */
    const val FRESNEL_CLEARANCE_RATIO = 0.6

    /** المسافة بين نقطتين على الكرة الأرضية (هافرساين) بالكيلومتر */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(lat1 in -90.0..90.0 && lat2 in -90.0..90.0) { "خط العرض خارج النطاق (-90..90)" }
        require(lon1 in -180.0..180.0 && lon2 in -180.0..180.0) { "خط الطول خارج النطاق (-180..180)" }
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val a = (sinLat * sinLat) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * (sinLon * sinLon)
        val clamped = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(clamped), sqrt((1 - clamped).coerceAtLeast(0.0)))
        return EARTH_RADIUS_KM * c
    }

    /** فقد المسار في الفضاء الحر (dB) — d بالكيلومتر و f بالميجاهرتز */
    fun fsplDb(distanceKm: Double, freqMHz: Double): Double {
        require(distanceKm.isFinite() && distanceKm > 0) { "المسافة يجب أن تكون موجبة" }
        require(freqMHz.isFinite() && freqMHz > 0) { "التردد يجب أن يكون موجبًا" }
        return 20 * log10(distanceKm) + 20 * log10(freqMHz) + 32.44
    }

    /**
     * نصف قطر منطقة فرينل الأولى (متر).
     * @param d1Km مسافة الطرف الأول إلى نقطة القياس
     * @param d2Km مسافة الطرف الثاني إلى نقطة القياس
     * @param freqMHz التردد بالميجاهرتز (يُحوَّل داخليًا إلى جيجاهرتز)
     */
    fun fresnelRadiusM(d1Km: Double, d2Km: Double, freqMHz: Double): Double {
        require(d1Km > 0 && d2Km > 0 && freqMHz > 0) { "قيم المدخلات يجب أن تكون موجبة" }
        val dTotal = d1Km + d2Km
        val freqGHz = freqMHz / 1000.0
        return 17.32 * sqrt((d1Km * d2Km) / (freqGHz * dTotal))
    }

    /** خلوص فرينل المطلوب عمليًا (متر) — 60٪ من نصف قطر المنطقة الأولى */
    fun fresnelRequiredM(d1Km: Double, d2Km: Double, freqMHz: Double): Double =
        FRESNEL_CLEARANCE_RATIO * fresnelRadiusM(d1Km, d2Km, freqMHz)

    /** القدرة المستلمة (dBm) حسب معادلة موازنة الرابط */
    fun receivedPowerDbm(
        txPowerDbm: Double,
        txGainDbi: Double,
        rxGainDbi: Double,
        distanceKm: Double,
        freqMHz: Double,
        cableLossDb: Double = 0.0
    ): Double {
        return txPowerDbm + txGainDbi + rxGainDbi - fsplDb(distanceKm, freqMHz) - cableLossDb
    }

    /** انتفاخ الأرض (متر) عند نقطة بين هوائيين — الافتراض k = 4/3 */
    fun earthBulgeM(d1Km: Double, d2Km: Double, k: Double = STANDARD_K): Double {
        require(d1Km >= 0 && d2Km >= 0 && k > 0) { "قيم المدخلات غير صالحة" }
        return (d1Km * d2Km) / (2 * EARTH_RADIUS_KM * k) * 1000.0
    }

    /** نتيجة فحص خط النظر */
    data class LosResult(
        val clear: Boolean,
        val marginM: Double,
        val requiredClearanceM: Double = 0.0
    )

    /**
     * فحص خط النظر عند نقطة عائق:
     * ارتفاع خط النظر المستقيم بين الهوائيين − (ارتفاع العائق + انتفاخ الأرض).
     */
    fun lineOfSight(
        txHeightM: Double,
        rxHeightM: Double,
        obstacleHeightM: Double,
        d1Km: Double,
        d2Km: Double,
        k: Double = STANDARD_K,
        freqMHz: Double? = null
    ): LosResult {
        require(d1Km > 0 && d2Km > 0) { "المسافات يجب أن تكون موجبة" }
        require(txHeightM.isFinite() && rxHeightM.isFinite() && obstacleHeightM.isFinite()) {
            "ارتفاعات غير صالحة"
        }
        require(k > 0) { "معامل انحناء الأرض يجب أن يكون موجبًا" }
        val total = d1Km + d2Km
        val hLos = txHeightM + (rxHeightM - txHeightM) * (d1Km / total)
        val required = obstacleHeightM + earthBulgeM(d1Km, d2Km, k)
        val margin = hLos - required
        val needed = if (freqMHz != null && freqMHz > 0) fresnelRequiredM(d1Km, d2Km, freqMHz) else 0.0
        return LosResult(clear = (margin - needed) >= 0, marginM = margin, requiredClearanceM = needed)
    }

    /** نتيجة موازنة الرابط الكاملة */
    data class LinkBudgetResult(
        val fsplDb: Double,
        val receivedPowerDbm: Double,
        val fresnelRadiusM: Double,
        val marginDb: Double,
        val viable: Boolean
    )

    /** موازنة رابط كاملة مع هامش مقابل حساسية المستقبل */
    fun linkBudget(
        txPowerDbm: Double,
        txGainDbi: Double,
        rxGainDbi: Double,
        cableLossDb: Double,
        distanceKm: Double,
        freqMHz: Double,
        rxSensitivityDbm: Double = -75.0
    ): LinkBudgetResult {
        val fspl = fsplDb(distanceKm, freqMHz)
        val rx = receivedPowerDbm(txPowerDbm, txGainDbi, rxGainDbi, distanceKm, freqMHz, cableLossDb)
        return LinkBudgetResult(
            fsplDb = fspl,
            receivedPowerDbm = rx,
            fresnelRadiusM = fresnelRadiusM(distanceKm / 2, distanceKm / 2, freqMHz),
            marginDb = rx - rxSensitivityDbm,
            viable = rx >= rxSensitivityDbm
        )
    }
}
