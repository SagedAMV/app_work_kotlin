package com.majarra.galaxy.util

import java.text.SimpleDateFormat
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

    /** اسم آمن لملفات النسخ الاحتياطي: بلا مسافات ولا محارف خاصة */
    fun backupStamp(epochMillis: Long = System.currentTimeMillis()): String =
        requireNotNull(backupFormat.get()).format(Date(epochMillis))
}

/** عدد الأيام من الآن حتى هذه اللحظة (سالب إن كانت في الماضي) */
fun Long.daysFromNow(now: Long = System.currentTimeMillis()): Long =
    (this - now) / (24L * 3600 * 1000)
