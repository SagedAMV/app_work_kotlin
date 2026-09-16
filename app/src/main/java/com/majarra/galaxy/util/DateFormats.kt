package com.majarra.galaxy.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * تنسيق التواريخ والأوقات — مصدر واحد لكل الشاشات.
 *
 * نسخة لكل خيط (ThreadLocal) لأن SimpleDateFormat ليست آمنة للخيوط،
 * وLocale عربي صريح، والمنطقة الزمنية الافتراضية تُثبَّت صراحة.
 */
object DateFormats {

    private const val PATTERN_DATETIME = "yyyy/MM/dd  HH:mm"
    private const val PATTERN_DATE = "yyyy/MM/dd"
    private const val PATTERN_BACKUP = "yyyyMMdd_HHmm"

    private val arabic: Locale = Locale.forLanguageTag("ar")

    private val dateTimeFormat = threadLocal(PATTERN_DATETIME)
    private val dateFormat = threadLocal(PATTERN_DATE)
    private val backupFormat = threadLocal(PATTERN_BACKUP)

    private fun threadLocal(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat(pattern, arabic).apply { timeZone = TimeZone.getDefault() }
    }

    fun dateTime(epochMillis: Long): String = requireNotNull(dateTimeFormat.get()).format(Date(epochMillis))
    fun date(epochMillis: Long): String = requireNotNull(dateFormat.get()).format(Date(epochMillis))

    /** اسم آمن لملفات النسخ الاحتياطي: بلا مسافات ولا محارف خاصة */
    fun backupStamp(epochMillis: Long = System.currentTimeMillis()): String =
        requireNotNull(backupFormat.get()).format(Date(epochMillis))
}

/**
 * عدد الأيام من الآن حتى هذه اللحظة (سالب إن كانت في الماضي).
 *
 * الحساب بين أيام تقويمية كاملة لا بين مللي ثواني خام: القسمة القديمة
 * على 24 ساعة كانت تُرجع نتائج خاطئة قرب حدود اليوم لأن منتقي التاريخ
 * يخزّن منتصف الليل بالتوقيت العالمي بينما الجهاز يعمل بالتوقيت المحلي.
 */
fun Long.daysFromNow(now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val targetDay = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return targetDay.toEpochDay() - today.toEpochDay()
}
