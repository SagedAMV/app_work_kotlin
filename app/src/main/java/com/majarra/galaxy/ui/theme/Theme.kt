package com.majarra.galaxy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.majarra.galaxy.R

/* ============================================================
 * الهوية البصرية — أسود عميق + أزرق سماوي + أخضر نيون.
 * الوضع الليلي هو الأساسي والنهاري اختياري.
 * ============================================================ */

val NightBackground = Color(0xFF04060B)
val NightSurface = Color(0xFF0B1220)
val NightSurfaceVariant = Color(0xFF111C31)
val SkyBlue = Color(0xFF38BDF8)
val NeonGreen = Color(0xFF3DFB7F)
val DangerRed = Color(0xFFFF5A5A)
val WarnAmber = Color(0xFFFFC857)
val TextPrimary = Color(0xFFE8EEF7)
val TextSecondary = Color(0xFF93A4BD)
val OutlineSoft = Color(0xFF1E2A44)

/*
 * ملاحظة (جلسة إصلاحات الواجهة): كانت الألوان تُعرَّف جزئيًا فقط، فبقيت
 * بقية أدوار Material 3 (surfaceContainer* للحوارات، inverseSurface
 * للسنابار، errorContainer لزر «طارئ»، secondaryContainer/tertiaryContainer
 * للرقاقات، surfaceTint، outlineVariant، scrim) على لوحات M3 الافتراضية
 * البنفسجية — وهي لا تشبه هوية التطبيق (أسود عميق/كحلي + سماوي) فتظهر
 * الحوارات والسنابار مختلفة الصبغة عن باقي الشاشات. كل الأدوار تُعرَّف
 * الآن صراحةً كسلّم كحلي متصل، فكل سطح في التطبيق من نفس العائلة.
 */

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF0B3B54),
    onPrimaryContainer = TextPrimary,
    inversePrimary = Color(0xFF0284C7),
    secondary = NeonGreen,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF0B3A26),
    onSecondaryContainer = Color(0xFF8CF5B4),
    tertiary = WarnAmber,
    onTertiary = Color(0xFF3A2600),
    tertiaryContainer = Color(0xFF4A3300),
    onTertiaryContainer = Color(0xFFFFE0A3),
    background = NightBackground,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceTint = SkyBlue,
    surfaceDim = Color(0xFF070B14),
    surfaceBright = Color(0xFF1A2438),
    surfaceContainerLowest = Color(0xFF05080F),
    surfaceContainerLow = Color(0xFF0A101C),
    surfaceContainer = Color(0xFF0F1828),
    surfaceContainerHigh = Color(0xFF141F33),
    surfaceContainerHighest = Color(0xFF1A2740),
    inverseSurface = Color(0xFFE3EAF2),
    inverseOnSurface = Color(0xFF0B1220),
    outline = OutlineSoft,
    outlineVariant = Color(0xFF22304D),
    scrim = Color(0xFF000000),
    error = DangerRed,
    onError = Color(0xFF3D0000),
    errorContainer = Color(0xFF54181A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFC),
    onPrimaryContainer = Color(0xFF03314A),
    inversePrimary = SkyBlue,
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF075228),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C2),
    onTertiaryContainer = Color(0xFF4A2E00),
    background = Color(0xFFF2F6FA),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE3EAF2),
    onSurfaceVariant = Color(0xFF46536B),
    surfaceTint = Color(0xFF0284C7),
    surfaceDim = Color(0xFFDCE6F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFD),
    surfaceContainer = Color(0xFFF0F5FA),
    surfaceContainerHigh = Color(0xFFEAF0F7),
    surfaceContainerHighest = Color(0xFFE3EAF2),
    inverseSurface = Color(0xFF0B1220),
    inverseOnSurface = TextPrimary,
    outline = Color(0xFFB9C4D4),
    outlineVariant = Color(0xFFCBD6E3),
    scrim = Color(0xFF000000),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/* ============================================================
 * الخطوط (تحديث هذه الجلسة):
 * كان التطبيق يستخدم «أريف رقعة» للعناوين، لكنه صعب القراءة على
 * شاشات الهاتف في كثرة البيانات.
 *
 * تم توحيد الخط إلى «تجوال (Tajawal)» كخط عربي عصري وواضح للعناوين
 * والنصوص مع الحفاظ على تدرجات الأوزان (Regular/Medium/Bold).
 * ============================================================ */

val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold)
)

private val defaultTypography = Typography()

private val GalaxyTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = AppFont),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = AppFont),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = AppFont),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = AppFont),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = AppFont),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = AppFont),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = AppFont),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = AppFont),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = AppFont),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = AppFont),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = AppFont),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = AppFont),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = AppFont),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = AppFont),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = AppFont)
)

@Composable
fun GalaxyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GalaxyTypography,
        content = content
    )
}
