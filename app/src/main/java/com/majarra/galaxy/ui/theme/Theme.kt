package com.majarra.galaxy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.majarra.galaxy.domain.model.EquipmentStatus
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.domain.model.SiteStatus
import com.majarra.galaxy.domain.model.TicketSeverity

/* ============================================================
 * الهوية البصرية — حسب القسم 7 من التعليمات:
 * أسود عميق + أزرق سماوي + أخضر نيون للروابط النشطة.
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

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF0B3B54),
    onPrimaryContainer = TextPrimary,
    secondary = NeonGreen,
    onSecondary = Color(0xFF00391A),
    tertiary = WarnAmber,
    background = NightBackground,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    error = DangerRed,
    onError = Color(0xFF3D0000)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEFC),
    onPrimaryContainer = Color(0xFF03314A),
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF2F6FA),
    onBackground = Color(0xFF0B1220),
    surface = Color.White,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE3EAF2),
    onSurfaceVariant = Color(0xFF46536B),
    outline = Color(0xFFB9C4D4),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val GalaxyTypography = Typography()

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

/** ألوان الحالات — تُستخدم في الشاشات وفي محرك رسم المجرة معًا */
object GalaxyColors {
    fun siteStatusColor(status: SiteStatus): Color = when (status) {
        SiteStatus.ACTIVE -> NeonGreen
        SiteStatus.DEGRADED -> WarnAmber
        SiteStatus.DOWN -> DangerRed
        SiteStatus.PLANNED -> TextSecondary
    }

    fun linkStatusColor(status: LinkStatus): Color = when (status) {
        LinkStatus.ACTIVE -> NeonGreen
        LinkStatus.DEGRADED -> WarnAmber
        LinkStatus.DOWN -> DangerRed
        LinkStatus.PLANNED -> TextSecondary
    }

    fun equipmentStatusColor(status: EquipmentStatus): Color = when (status) {
        EquipmentStatus.WORKING -> NeonGreen
        EquipmentStatus.FAULTY -> DangerRed
        EquipmentStatus.SPARE -> SkyBlue
        EquipmentStatus.RETIRED -> TextSecondary
    }

    fun severityColor(severity: TicketSeverity): Color = when (severity) {
        TicketSeverity.CRITICAL -> DangerRed
        TicketSeverity.HIGH -> WarnAmber
        TicketSeverity.MEDIUM -> SkyBlue
        TicketSeverity.LOW -> TextSecondary
    }
}
