package com.majarra.galaxy.ui.screens.galaxy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.majarra.galaxy.data.local.Link
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.SkyBlue
import com.majarra.galaxy.ui.theme.TextSecondary
import com.majarra.galaxy.ui.theme.WarnAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * محرك رسم المجرة — القسم 4.3 من التعليمات:
 * - Nodes & Edges على Canvas.
 * - نبض متحرك على الروابط النشطة، وأحمر متقطع وامض للمعطلة.
 * - ألوان حسب الحالة + سماكة حسب الأولوية.
 * - وضع 3D مبسط: دوران قرص الشبكة حول محوره مع إسقاط منظوري.
 */

/** نقطة معروضة لموقع بعد الإسقاط */
private data class NodePoint(
    val offset: Offset,
    val scale: Float,
    val depth: Float
)

/** تخطيط المواقع على اللوحة: 2D إحداثيات مباشرة، 3D دوران + إسقاط */
private fun computeLayout(
    sites: List<Site>,
    width: Float,
    height: Float,
    rotationRad: Float
): Map<Long, NodePoint> {
    if (sites.isEmpty() || width <= 0f || height <= 0f) return emptyMap()
    val padX = width * 0.12f
    val padY = height * 0.12f

    val minLat = sites.minOf { it.latitude }
    val maxLat = sites.maxOf { it.latitude }
    val minLng = sites.minOf { it.longitude }
    val maxLng = sites.maxOf { it.longitude }
    val latRange = max(maxLat - minLat, 1e-6)
    val lngRange = max(maxLng - minLng, 1e-6)

    val cx = width / 2f
    val cy = height / 2f

    return sites.associate { s ->
        if (rotationRad == 0f) {
            // عرض ثنائي الأبعاد: الشمال أعلى اللوحة
            val u = ((s.longitude - minLng) / lngRange).toFloat()
            val v = ((s.latitude - minLat) / latRange).toFloat()
            s.id to NodePoint(
                offset = Offset(padX + u * (width - 2 * padX), padY + (1f - v) * (height - 2 * padY)),
                scale = 1f,
                depth = 0f
            )
        } else {
            // عرض ثلاثي الأبعاد مبسط: قرص دوّار بمنظور مائل
            val u = ((s.longitude - minLng) / lngRange * 2 - 1).toFloat()
            val v = ((s.latitude - minLat) / latRange * 2 - 1).toFloat()
            val x3 = u * cos(rotationRad) + v * sin(rotationRad)
            val z3 = -u * sin(rotationRad) + v * cos(rotationRad)
            val f = 2.4f
            val persp = f / (f + z3)
            val radiusX = width / 2f - padX
            val radiusY = height / 2f - padY
            s.id to NodePoint(
                offset = Offset(
                    cx + x3 * radiusX * persp,
                    cy + z3 * radiusY * 0.55f * persp
                ),
                scale = persp,
                depth = z3
            )
        }
    }
}

private fun statusColor(status: LinkStatus): Color = when (status) {
    LinkStatus.ACTIVE -> NeonGreen
    LinkStatus.DEGRADED -> WarnAmber
    LinkStatus.DOWN -> DangerRed
    LinkStatus.PLANNED -> TextSecondary
}

private fun siteColor(site: Site): Color = when (site.status) {
    com.majarra.galaxy.domain.model.SiteStatus.ACTIVE -> NeonGreen
    com.majarra.galaxy.domain.model.SiteStatus.DEGRADED -> WarnAmber
    com.majarra.galaxy.domain.model.SiteStatus.DOWN -> DangerRed
    com.majarra.galaxy.domain.model.SiteStatus.PLANNED -> TextSecondary
}

/** مسافة نقطة إلى قطعة مستقيمة — لالتقاط النقر على الروابط */
private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val ap = p - a
    val abLen2 = ab.x * ab.x + ab.y * ab.y
    if (abLen2 == 0f) return (p - a).getDistance()
    val t = ((ap.x * ab.x + ap.y * ab.y) / abLen2).coerceIn(0f, 1f)
    val proj = Offset(a.x + ab.x * t, a.y + ab.y * t)
    return (p - proj).getDistance()
}

@Composable
fun GalaxyCanvas(
    sites: List<Site>,
    links: List<Link>,
    is3D: Boolean,
    onSiteClick: (Long) -> Unit,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "galaxy-engine")

    // نبض الروابط النشطة — كرة ضوء تجري على الخط
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    // وميض الروابط المعطلة
    val blink by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "blink"
    )
    // دوران القرص في وضع 3D
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    val density = LocalDensity.current
    val tapRadiusPx = with(density) { 26.dp.toPx() }
    val edgeTapPx = with(density) { 18.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val activeRotation = if (is3D) rotation else 0f

        val positions = remember(sites, w, h, activeRotation) {
            computeLayout(sites, w, h, activeRotation)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(positions, links, tapRadiusPx, edgeTapPx) {
                    detectTapGestures { tap ->
                        // أولًا: هل النقر على موقع؟
                        val node = positions.entries.minByOrNull {
                            (tap - it.value.offset).getDistance()
                        }
                        if (node != null && (tap - node.value.offset).getDistance() <= tapRadiusPx * node.value.scale.coerceAtLeast(0.6f)) {
                            onSiteClick(node.key)
                            return@detectTapGestures
                        }
                        // ثانيًا: هل النقر على رابط؟
                        var best: Link? = null
                        var bestDist = edgeTapPx
                        for (link in links) {
                            val a = positions[link.sourceSiteId] ?: continue
                            val b = positions[link.targetSiteId] ?: continue
                            val d = distanceToSegment(tap, a.offset, b.offset)
                            if (d < bestDist) {
                                bestDist = d
                                best = link
                            }
                        }
                        if (best != null) onLinkClick(best)
                    }
                }
        ) {
            // خلفية نجوم خفيفة تعطي عمقًا بصريًا بدون إثقال الأداء
            drawStarfield(size.width, size.height)

            // 1) الروابط تحت العقد
            for (link in links) {
                val a = positions[link.sourceSiteId] ?: continue
                val b = positions[link.targetSiteId] ?: continue
                val color = statusColor(link.status)
                val strokeWidth = when (link.priority) {
                    com.majarra.galaxy.domain.model.LinkPriority.CRITICAL -> 5f
                    com.majarra.galaxy.domain.model.LinkPriority.HIGH -> 3.5f
                    com.majarra.galaxy.domain.model.LinkPriority.NORMAL -> 2.2f
                    com.majarra.galaxy.domain.model.LinkPriority.LOW -> 1.4f
                } * ((a.scale + b.scale) / 2f)

                when (link.status) {
                    LinkStatus.ACTIVE -> {
                        drawLine(color.copy(alpha = 0.85f), a.offset, b.offset, strokeWidth)
                        // نبض ضوئي يجري على الرابط النشط
                        val px = a.offset.x + (b.offset.x - a.offset.x) * pulse
                        val py = a.offset.y + (b.offset.y - a.offset.y) * pulse
                        drawCircle(NeonGreen, 4.5f * ((a.scale + b.scale) / 2f), Offset(px, py))
                        drawCircle(NeonGreen.copy(alpha = 0.3f), 9f, Offset(px, py))
                    }
                    LinkStatus.DOWN -> {
                        // أحمر متقطع وامض
                        drawLine(
                            DangerRed.copy(alpha = blink),
                            a.offset, b.offset,
                            strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    }
                    LinkStatus.DEGRADED -> {
                        drawLine(WarnAmber.copy(alpha = 0.9f), a.offset, b.offset, strokeWidth)
                    }
                    LinkStatus.PLANNED -> {
                        drawLine(
                            SkyBlue.copy(alpha = 0.5f),
                            a.offset, b.offset,
                            strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
                        )
                    }
                }
            }

            // 2) العقد: تُرسم مرتبة حسب العمق في وضع 3D
            val orderedSites = if (is3D) {
                sites.sortedBy { positions[it.id]?.depth ?: 0f }
            } else sites

            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 26f
                textAlign = android.graphics.Paint.Align.CENTER
                color = android.graphics.Color.parseColor("#93A4BD")
            }

            for (site in orderedSites) {
                val p = positions[site.id] ?: continue
                val r = 9f * p.scale
                val color = siteColor(site)
                // هالة الموقع المعطل
                if (site.status == com.majarra.galaxy.domain.model.SiteStatus.DOWN) {
                    drawCircle(DangerRed.copy(alpha = 0.18f + blink * 0.12f), r * 2.4f, p.offset)
                }
                drawCircle(Color.Black.copy(alpha = 0.9f), r, p.offset)
                drawCircle(color, r * 0.55f, p.offset)
                drawCircle(color, r, p.offset, style = Stroke(width = 2.5f * p.scale))
                // اسم الموقع
                drawContext.canvas.nativeCanvas.drawText(
                    site.name,
                    p.offset.x,
                    p.offset.y + r + 30f * p.scale,
                    textPaint
                )
            }
        }
    }
}

/** نجوم خلفية ثابتة — مولدة حتميًا بدون عشوائيات وقت التشغيل */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarfield(width: Float, height: Float) {
    var seed = 1234567L
    fun next(): Float {
        seed = (seed * 1103515245L + 12345L) % 2147483648L
        if (seed < 0) seed += 2147483648L
        return seed / 2147483648f
    }
    repeat(40) {
        val x = next() * width
        val y = next() * height
        val alpha = 0.05f + next() * 0.12f
        drawCircle(Color.White.copy(alpha = alpha), 1.2f + next() * 1.6f, Offset(x, y), style = Fill)
    }
}
