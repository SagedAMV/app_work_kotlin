package com.majarra.galaxy.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * محرك حسابات المجرة — حسب القسم 6 من ملف التعليمات:
 * - FSPL(dB) = 20log10(d) + 20log10(f) + 32.44   (d كم، f ميجاهرتز)
 * - Link Budget: Received = Tx + TxGain + RxGain − PathLoss − CableLoss
 * - Fresnel: r = 17.32 * sqrt(d1*d2 / (f * D))
 * - LOS: تحقق من الارتفاعات + انحناء الأرض + معامل k
 */
object RadioMath {

    private const val EARTH_RADIUS_KM = 6371.0

    /** المسافة بين نقطتين على الكرة الأرضية (هافرساين) بالكيلومتر */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** فقد المسار في الفضاء الحر (dB) */
    fun fsplDb(distanceKm: Double, freqMHz: Double): Double {
        require(distanceKm > 0) { "المسافة يجب أن تكون موجبة" }
        require(freqMHz > 0) { "التردد يجب أن يكون موجبًا" }
        return 20 * log10(distanceKm) + 20 * log10(freqMHz) + 32.44
    }

    /** نصف قطر منطقة فرينل الأولى (متر) — D المسافة الكلية */
    fun fresnelRadiusM(d1Km: Double, d2Km: Double, freqMHz: Double): Double {
        require(d1Km > 0 && d2Km > 0 && freqMHz > 0) { "قيم المدخلات يجب أن تكون موجبة" }
        val dTotal = d1Km + d2Km
        return 17.32 * sqrt((d1Km * d2Km) / (freqMHz * dTotal))
    }

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
    fun earthBulgeM(d1Km: Double, d2Km: Double, k: Double = 4.0 / 3.0): Double {
        require(d1Km >= 0 && d2Km >= 0 && k > 0) { "قيم المدخلات غير صالحة" }
        return (d1Km * d2Km) / (2 * EARTH_RADIUS_KM * k) * 1000.0
    }

    /** نتيجة فحص خط النظر */
    data class LosResult(val clear: Boolean, val marginM: Double)

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
        k: Double = 4.0 / 3.0
    ): LosResult {
        require(d1Km > 0 && d2Km > 0) { "المسافات يجب أن تكون موجبة" }
        val total = d1Km + d2Km
        val hLos = txHeightM + (rxHeightM - txHeightM) * (d1Km / total)
        val required = obstacleHeightM + earthBulgeM(d1Km, d2Km, k)
        val margin = hLos - required
        return LosResult(clear = margin >= 0, marginM = margin)
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
