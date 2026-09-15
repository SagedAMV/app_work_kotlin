package com.majarra.galaxy.util

import java.util.Locale

/**
 * تنسيق الأرقام بشكل موحّد ومحايد للغة.
 *
 * كان الكود يستخدم `"%.2f".format(x)` التي تعتمد Locale الجهاز الحالي.
 * على أجهزة ببعض الإعدادات تُنتج أرقامًا بمحارف غير لاتينية (٤٥٫٦٧) وهي
 * **غير قابلة للتحليل** بـ toDoubleOrNull عند إعادة إدخالها في الحقول، كما
 * تختلف فواصلها بين الشاشات والملفات المصدّرة (CSV/Excel/PDF).
 */
fun Double.formatDecimals(digits: Int): String =
    String.format(Locale.US, "%.${digits}f", this)

/** رقم بإشارة صريحة (+/−) — مفيد في هوامش موازنة الرابط */
fun Double.formatSigned(digits: Int = 2): String =
    String.format(Locale.US, "%+.${digits}f", this)

/** مسافة مقروءة بالكيلومتر */
fun Double.formatKm(digits: Int = 2): String = "${formatDecimals(digits)} كم"
