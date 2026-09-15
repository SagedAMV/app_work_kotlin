package com.majarra.galaxy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * تنسيق التواريخ والأوقات — مصدر واحد لكل الشاشات.
 *
 * كانت الشاشات تُنشئ `SimpleDateFormat(Locale("ar"))` في كل استدعاء داخل
 * القوائم (تحميل ثقيل مع كل إعادة تركيب)، وSimpleDateFormat ليست آمنة
 * للخيوط، والمنطقة الزمنية كانت تعتمد ضمنيًا على الحالة العامة للجهاز.
 *
 * هنا: نسخة لكل خيط (ThreadLocal)، Locale عربي صريح عبر forLanguageTag،
 * وTimeZone الافتراضية تُثبَّت صراحة، وصيغة مدة عربية للاستخدامات التجارية.
 */
object DateFormats {

    private const val PATTERN_DATETIME = "yyyy/MM/dd  HH:mm"
    private const val PATTERN_DATE = "yyyy/MM/dd"
    private const val PATTERN_TIME = "HH:mm"
    private const val PATTERN_BACKUP = "yyyyMMdd_HHmm"

    private val arabic: Locale = Locale.forLanguageTag("ar")

    private val dateTimeFormat = threadLocal(PATTERN_DATETIME)
    private val dateFormat = threadLocal(PATTERN_DATE)
    private val timeFormat = threadLocal(PATTERN_TIME)
    private val backupFormat = threadLocal(PATTERN_BACKUP)

    private fun threadLocal(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat(pattern, arabic).apply { timeZone = TimeZone.getDefault() }
    }

    fun dateTime(epochMillis: Long): String = requireNotNull(dateTimeFormat.get()).format(Date(epochMillis))
    fun date(epochMillis: Long): String = requireNotNull(dateFormat.get()).format(Date(epochMillis))
    fun time(epochMillis: Long): String = requireNotNull(timeFormat.get()).format(Date(epochMillis))

    /** اسم آمن لملفات النسخ الاحتياطي والتقارير: بلا مسافات ولا محارف خاصة */
    fun backupStamp(epochMillis: Long = System.currentTimeMillis()): String =
        requireNotNull(backupFormat.get()).format(Date(epochMillis))

    /** مدة مقروءة عربيًا (تُستخدم في زمن معالجة التذاكر) */
    fun duration(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        if (m < 60) return "$m دقيقة"
        val hours = m / 60
        val rem = m % 60
        return if (hours < 24) {
            if (rem == 0L) "$hours ساعة" else "$hours ساعة و $rem دقيقة"
        } else {
            val days = hours / 24
            val remHours = hours % 24
            if (remHours == 0L) "$days يوم" else "$days يوم و $remHours ساعة"
        }
    }
}

/** عدد الأيام بين لحظتين (يُستخدم في استحقاق الصيانة) */
fun Long.daysFromNow(now: Long = System.currentTimeMillis()): Long =
    (this - now) / (24L * 3600 * 1000)
