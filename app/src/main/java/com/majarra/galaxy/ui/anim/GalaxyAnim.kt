package com.majarra.galaxy.ui.anim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ============================================================
 * مكتبة انيميشنات «مجرة» — الإصدار 2.6
 * الجولة الأولى (2.3 — اختيارات الجلسة الأولى):
 *  1) نبض توهج للأزرار        2) تمدد شريطي لزر الإضافة
 *  3) تحول زر الحالة لدائرة   4) قفز أرقام القفل
 *  5) رسم خط علامة الصح       6) حلقة منزلقة للألوان
 * 10) حوار بتكبير نابض       12) سنابار بشريط مهلة
 * 13) ظهور متتابع للقوائم    15) عجلة أرقام الإحصائيات
 * 16) تنفس لوني للشارات       8) فتح التفاصيل بانتقال واحد
 * ----------------------------------------------------------
 * الجولة الثالثة (2.6 — اختيارات_الجولة_الثالثة، ترقيم 31-47):
 * 32) كشف دائري لحوارات الإضافة     34) مروف الأرقام عند التغير
 * 37) حلقة عد تنازلي للاستحقاق      39) منزلق بفقاعة قيمة
 * 40) مفتاح الوضع ليلي بشمس وقمر    41) جزيئات علامة الصح
 * 43) شيمر سديمي للتحميل            44) انفجار نجوم النجاح
 * 47) قفل الكوكبات (ترقية خانات الرمز)
 * (31 شريط التنقل المنزلق و33 الرجوع التوقعي في ملف التنقل،
 *  و35/36/38/42/45/46 تصاميم منفذة داخل شاشاتها مباشرة)
 * ============================================================
 * ملاحظة: «تحول الحاوية» القديم (مقترح 8) أُزيل سابقًا حسب
 * تعليمات إصلاح الانميشن المزدوج — فتح الموقع انميشن واحد. */

/* ═══════════════ 1) نبض توهج — توهج سماوي حول الزر أثناء الضغط ═══════════════ */

/**
 * توهج يُرسم كظل ملوّن يكبر لحظة الضغط ويخفت عند الإفلات
 * (اختيار 1-2). يُمرَّر نفس `interactionSource` الذي يأخذه الزر
 * حتى تُلتقط حالة الضغط دون اعتراض اللمس.
 */
fun Modifier.galaxyPressGlow(
    interactionSource: InteractionSource,
    glowColor: Color = Color(0xFF38BDF8)
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(170),
        label = "press-glow"
    )
    Modifier.shadow(
        elevation = 14.dp * glow,
        shape = RoundedCornerShape(14.dp),
        ambientColor = glowColor.copy(alpha = 0.65f),
        spotColor = glowColor.copy(alpha = 0.65f)
    )
}

/** زر M3 عادي مع توهج الضغط مدمجًا — للأزرار الرئيسية في التطبيق */
@Composable
fun GlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.galaxyPressGlow(interaction),
        content = content
    )
}

/* ═══════════════ 2) زر الإضافة العائم — تمدد شريطي ═══════════════ */

/**
 * زر إضافة يبدأ مكتومًا بأيقونته، ونقرة واحدة تمده عرضيًا كاشفًا
 * الفعل الرئيسي (وزرًا ثانويًا اختياريًا) بدل أي قائمة منبثقة
 * (اختيار 2-3). النقر بعد التمدد ينفّذ الفعل الرئيسي.
 */
@Composable
fun GalaxyExpandingFab(
    icon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = {
            if (!expanded) {
                expanded = true
            } else {
                expanded = false
                onPrimary()
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = primaryLabel)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
                exit = fadeOut(tween(120))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(primaryLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (secondaryLabel != null && onSecondary != null) {
                        Surface(
                            onClick = {
                                expanded = false
                                onSecondary()
                            },
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                secondaryLabel,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════ 3) زر الحالة المتدرج — تحول لدائرة تحميل ═══════════════ */

private enum class MorphPhase { IDLE, LOADING, DONE }

/**
 * زر يغيّر حالة عنصر (بدء صيانة/إرجاع): عند النقر ينكمش لدائرة
 * تدور أثناء المعالجة، ثم يتلون بالأخضر مع علامة نجاح قبل أن يعود
 * (اختيار 3-2). الارتفاع ثابت حتى لا تقفز البطاقة أثناء التحول.
 */
@Composable
fun MorphingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    var phase by remember { mutableStateOf(MorphPhase.IDLE) }
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(phase) {
        if (phase == MorphPhase.LOADING) {
            rotation.snapTo(0f)
            rotation.animateTo(540f, tween(900))
        }
    }

    Box(modifier = modifier.height(40.dp), contentAlignment = Alignment.Center) {
        when (phase) {
            MorphPhase.IDLE -> {
                val start: () -> Unit = {
                    phase = MorphPhase.LOADING
                    onClick()
                    scope.launch {
                        delay(480)
                        phase = MorphPhase.DONE
                        delay(700)
                        phase = MorphPhase.IDLE
                    }
                }
                if (outlined) {
                    OutlinedButton(onClick = start) { Text(label) }
                } else {
                    Button(onClick = start) { Text(label) }
                }
            }
            MorphPhase.LOADING -> Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer { rotationZ = rotation.value }
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            MorphPhase.DONE -> Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}

/* ═══════════════ 4+47) شاشة القفل — كوكبة الرمز ═══════════════ */

/**
 * خانات الرمز السري على هيئة كوكبة (اختيار 4-2 ثم ترقية اختيار 47
 * من اختيارات الجولة الثالثة): كل رقم يُدخل يُشعل نجمته بتكبير نابض
 * ويمد خطًا نحو النجمة التالية حتى تكتمل الكوكبة فتومض بهالة جماعية.
 *
 * الترقية أبقت نفس التوقيع القديم `PinDots` حتى لا يتغير أي مستدعٍ،
 * فالتنفيذ استُبدل بالكامل من نقاط جامدة إلى سماء مصغرة. المواقع
 * النسبية للنجوم محسوبة بثبات (بلا عشوائية) كعادة التطبيق.
 */
@Composable
fun PinDots(pin: String, modifier: Modifier = Modifier, maxLen: Int = 8) {
    // ارتفاع الموجة لكل نجمة — نمط ثابت يعطي شكل كوكبة متعرجة
    val wave = remember { floatArrayOf(0.22f, -0.26f, 0.12f, -0.30f, 0.24f, -0.14f, 0.28f, -0.22f) }
    // تقدم التعبئة يمشي بنابض مع طول الرمز فيتحرك «رأس» الكوكبة
    val fill by animateFloatAsState(
        targetValue = pin.length.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "constellation-fill"
    )
    // وميض الاكتمال: هالة جماعية عندما تمتلئ كل الخانات
    val flash = remember { Animatable(0f) }
    LaunchedEffect(pin.length) {
        if (pin.length >= maxLen) {
            flash.snapTo(0f)
            flash.animateTo(1f, animationSpec = tween(550))
        } else {
            flash.snapTo(0f)
        }
    }
    val starColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val w = size.width
        val h = size.height
        val count = maxLen.coerceAtLeast(2)
        fun starCenter(i: Int): Offset {
            val t = i / (count - 1f)
            val x = w * (0.07f + 0.86f * t)
            val y = h * (0.5f + 0.32f * wave[i % wave.size])
            return Offset(x, y)
        }
        // خطوط الكوكبة بين النجوم المضاءة — آخر خط يُرسم مع التقدم
        for (i in 0 until count - 1) {
            val seg = (fill - i).coerceIn(0f, 1f)
            if (seg <= 0f) continue
            val from = starCenter(i)
            val to = starCenter(i + 1)
            val end = Offset(from.x + (to.x - from.x) * seg, from.y + (to.y - from.y) * seg)
            drawLine(
                color = starColor.copy(alpha = 0.45f),
                start = from,
                end = end,
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        // النجوم: مضاءة (متوهجة) أو خافتة بحلقة
        for (i in 0 until count) {
            val lit = (fill - i).coerceIn(0f, 1f)
            val center = starCenter(i)
            if (lit > 0f) {
                // هالة النجمة المضاءة — تكبر مع وميض الاكتمال
                drawCircle(
                    color = starColor.copy(alpha = (0.20f + 0.30f * flash.value) * lit),
                    radius = (10.dp + 4.dp * flash.value).toPx() * lit,
                    center = center
                )
                drawCircle(
                    color = starColor,
                    radius = 4.5.dp.toPx() * (0.4f + 0.6f * lit),
                    center = center
                )
            } else {
                drawCircle(
                    color = dimColor.copy(alpha = 0.8f),
                    radius = 3.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }
    }
}

/* ═══════════════ 5) علامات الصح — رسم الخط ═══════════════ */

/**
 * مربع تحديد تُرسم داخله علامة الصح خطًا متحركًا كأنها كُتبت
 * باليد (اختيار 5-1) — بديل بصري لـ Checkbox الافتراضي.
 *
 * ترقية الجولة الثالثة (اختيار 41 — جزيئات): عند اكتمال رسم الصح
 * تتطاير 6 شظايا نجمية صغيرة بألوان الهوية من مركز المربع. الجزيئات
 * تعمل فقط عند التحوّل من غير محدد إلى محدد (وليس عند أول تركيب
 * لعنصر محدد مسبقًا). `celebrate = false` يعطّل الجزيئات لمن يريد
 * العلامة المرسومة وحدها.
 */
@Composable
fun DrawnCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: ((Boolean) -> Unit)? = null,
    celebrate: Boolean = true
) {
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(240),
        label = "check-draw"
    )
    // جزيئات الاحتفال (اختيار 41): تقدم 0→1 عند اكتمال التحديد
    val burst = remember { Animatable(0f) }
    val composedOnce = remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        val firstComposition = !composedOnce.value
        composedOnce.value = true
        if (checked && !firstComposition && celebrate) {
            burst.snapTo(0f)
            burst.animateTo(1f, animationSpec = tween(420))
        } else if (!checked) {
            burst.snapTo(0f)
        }
    }
    val borderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "check-border"
    )
    val checkColor = MaterialTheme.colorScheme.secondary
    val sparkColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val path = remember {
        Path().apply {
            moveTo(4.5f, 11f)
            lineTo(9.5f, 16f)
            lineTo(17.5f, 5.5f)
        }
    }
    val measure = remember { PathMeasure() }

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .border(2.dp, borderColor, RoundedCornerShape(7.dp))
            .background(
                if (checked) checkColor.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(7.dp)
            )
            .then(if (onToggle != null) Modifier.clickable { onToggle(!checked) } else Modifier)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (fraction > 0f) {
                measure.setPath(path, false)
                val segment = Path()
                measure.getSegment(0f, measure.length * fraction, segment, true)
                drawPath(
                    path = segment,
                    color = checkColor,
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            // جزيئات الاختيار 41: 6 شظايا تنطلق من المركز بزوايا ثابتة
            if (burst.value > 0f && burst.value < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = 16.dp.toPx()
                repeat(6) { i ->
                    val angle = (i * 60f + 18f) * PI.toFloat() / 180f
                    val radius = maxRadius * burst.value
                    drawCircle(
                        color = sparkColors[i % 3].copy(alpha = 1f - burst.value),
                        radius = 1.4.dp.toPx() * (1f - burst.value * 0.5f),
                        center = Offset(
                            x = center.x + cos(angle) * radius,
                            y = center.y + sin(angle) * radius
                        )
                    )
                }
            }
        }
    }
}

/* ═══════════════ 6) لوحة الألوان — حلقة منزلقة ═══════════════ */

/**
 * لوحة ألوان التصنيف: حلقة بيضاء تنزلق بسلاسة من اللون السابق
 * إلى الجديد بدل القفز الفوري (اختيار 6-2).
 * تخطيط ثابت معروف الحساب: صفوف من 4 (34 نقطة + فجوة 12/10).
 */
@Composable
fun SlidingColorPalette(
    palette: List<String>,
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchSize = 34.dp
    val hGap = 12.dp
    val vGap = 10.dp
    val selectedIndex = palette.indexOf(selectedHex).coerceAtLeast(0)
    // ملاحظة: الضرب يبدأ بـ Dp لأن (Int × Dp) غير معرّف في Kotlin
    val ringX by animateDpAsState(
        targetValue = (swatchSize + hGap) * (selectedIndex % 4),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ring-x"
    )
    val ringY by animateDpAsState(
        targetValue = (swatchSize + vGap) * (selectedIndex / 4),
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "ring-y"
    )

    Box(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
            palette.chunked(4).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
                    rowColors.forEach { hex ->
                        val selected = hex == selectedHex
                        val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                            .getOrDefault(MaterialTheme.colorScheme.primary)
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.08f else 1f,
                            animationSpec = tween(200),
                            label = "swatch-scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(swatchSize)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(CircleShape)
                                .background(parsed)
                                .clickable { onSelect(hex) }
                        )
                    }
                }
            }
        }
        // الحلقة المنزلقة — تكبر قليلًا عن العينة لتحيط بها
        Box(
            modifier = Modifier
                .offset(x = ringX - 4.dp, y = ringY - 4.dp)
                .size(swatchSize + 8.dp)
                .border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
        )
    }
}

/* ═══════════════ 13) ظهور متتابع لعناصر القوائم ═══════════════ */

/**
 * غلاف ظهور متتابع: كل عنصر يدخل بتلاشي وصعود خفيف بعد تأخير
 * `40 + 30×الفهرس` مللي ثانية. تغيير `trigger` يعيد تشغيل الظهور
 * (تبديل تبويب، تغيير بحث…). يُستخدم أيضًا لعناصر الخط الزمني
 * وشبكة الصور (الاختيارات 9 و13 و18 و20).
 */
@Composable
fun StaggeredItem(
    index: Int,
    trigger: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember(trigger) { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        shown = false
        delay(40L + index * 30L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 10 },
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        content()
    }
}

/* ═══════════════ 10) الحوارات — تكبير نابض ═══════════════ */

/**
 * غلاف حوار يظهر من 90% بنوابض لطيفة مع تعتيم تدريجي، ويغلق
 * بحركة عكسية قصيرة قبل تنفيذ أي إجراء (اختيار 10-1).
 * `requestClose(الإجراء)` يضمن خروجًا متحركًا قبل التنفيذ.
 */
@Composable
fun GalaxyDialogShell(
    onDismissRequest: () -> Unit,
    content: @Composable (requestClose: ((() -> Unit) -> Unit)) -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val requestClose: ((() -> Unit) -> Unit) = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                delay(170)
                action()
            }
        }
    }

    LaunchedEffect(Unit) { entered = true }

    Dialog(onDismissRequest = { requestClose(onDismissRequest) }) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = entered && !closing,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
                ) + fadeIn(tween(160)),
                exit = scaleOutLike() + fadeOut(tween(140))
            ) {
                content(requestClose)
            }
        }
    }
}

/** خروج مصغَّر مقابل دخول التكبير النابض */
private fun scaleOutLike() = androidx.compose.animation.scaleOut(
    animationSpec = tween(150),
    targetScale = 0.9f
)

/* ═══════════════ 12) السنابار — شريط المهلة ═══════════════ */

/**
 * مضيف سنابار بخيط زمني رفيع يتناقص أعلى الرسالة طوال مدة عرضها
 * (اختيار 12-2) — يوضح للمستخدم كم بقي قبل الاختفاء.
 */
@Composable
fun GalaxySnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val durationMillis = when (data.visuals.duration) {
            SnackbarDuration.Short -> 4000
            SnackbarDuration.Long -> 10000
            SnackbarDuration.Indefinite -> 8000
        }
        val progress = remember(data) { Animatable(1f) }
        LaunchedEffect(data) {
            progress.snapTo(1f)
            progress.animateTo(0f, animationSpec = tween(durationMillis))
        }
        val lineColor = MaterialTheme.colorScheme.primary
        Snackbar(
            snackbarData = data,
            modifier = Modifier.drawWithContent {
                drawContent()
                if (progress.value > 0f) {
                    drawRect(
                        color = lineColor,
                        topLeft = androidx.compose.ui.geometry.Offset(12.dp.toPx(), 0f),
                        size = androidx.compose.ui.geometry.Size(
                            width = (size.width - 24.dp.toPx()) * progress.value,
                            height = 3.dp.toPx()
                        )
                    )
                }
            }
        )
    }
}

/* ═══════════════ 15) عجلة أرقام الإحصائيات ═══════════════ */

/**
 * رقم يدور عموديًا كعجلة عداد السيارة من قيمة البداية حتى القيمة
 * النهائية عند ظهور الشاشة (اختيار 15-3). يُقصّ المدى عند 40
 * منزلة حتى لا تُركَّب مئات النصوص للأرقام الكبيرة.
 */
@Composable
fun OdometerNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    // ارتفاع أكبر قليلاً لمنع قص الأرقام الكبيرة بأنماط عنوان متوسطة
    val rowHeight = 44.dp
    val cap = 40
    val start = (value - cap).coerceAtLeast(0)
    val offset by animateDpAsState(
        targetValue = rowHeight * (value - start),
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "odometer"
    )
    // إصلاح تعليمات هذه الجلسة: المحاذاة يجب أن تكون من الأعلى (TopStart)
    // لا من المنتصف — فمع أي قيمة أكبر من صفر يصبح عمود الأرقام أطول من
    // نافذة العرض، والمحاذاة الوسطى كانت تزاحه للأعلى بمقدار نصف الفرق
    // فيخرج الرقم النهائي عن النافذة ويبدو الرقم «مختفيًا» بعد الانميشن.
    Box(
        modifier = modifier
            .height(rowHeight)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.TopStart
    ) {
        Column(Modifier.offset(y = -offset)) {
            for (i in start..value) {
                Box(
                    modifier = Modifier.height(rowHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(i.toString(), style = style, color = color)
                }
            }
        }
    }
}

/* ═══════════════ 16) شارة الاستحقاق — تنفس لوني ═══════════════ */

/**
 * شارة «متأخر N يوم» تتنفس بين درجة باهتة ومشبعة من الأحمر بهدوء
 * (اختيار 16-2)؛ القريب من الموعد يبقى بلون ثابت بلا حركة.
 */
@Composable
fun BreathingDueBadge(days: Long, modifier: Modifier = Modifier) {
    val overdue = days < 0
    val text = when {
        overdue -> "متأخر ${-days} يوم"
        days == 0L -> "اليوم"
        else -> "بعد $days يوم"
    }
    if (!overdue) {
        Text(
            text,
            modifier = modifier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        return
    }
    // تنفس لوني: ذهاب وإياب بين درجتي الأحمر بلا مكتبات إضافية
    val breath = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breath.animateTo(1f, tween(1300))
            breath.animateTo(0f, tween(1300))
        }
    }
    val base = Color(0xFFFF5A5A)
    val bg = androidx.compose.ui.graphics.lerp(
        base.copy(alpha = 0.10f),
        base.copy(alpha = 0.38f),
        breath.value
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.graphicsLayer { alpha = 0.75f + 0.25f * breath.value }
        )
    }
}

/* ═══════════════ 8) فتح تفاصيل الموقع ═══════════════
 * ملاحظة (تنفيذ تعليمات هذه الجلسة): كانت هنا طبقة «تحول الحاوية»
 * (SiteLaunchOverlay/SiteLaunchTarget) تتمدد من البطاقة حتى تملأ الشاشة
 * ثم يحدث التنقل بانتقاله الخاص — فكان المستخدم يرى انميشنين متتابعين
 * مع اختفاء مفاجئ بينهما. حُذفت الطبقة بالكامل وأصبح فتح الموقع يتم
 * بانميشن واحد فقط: انتقال التنقل نفسه (تلاشي + تحجيم في مضيف
 * التنقل). الحذف هنا أيضًا إزالة كود ميت بعد فك استخدامه من قائمة
 * المواقع.
 */

/* ═══════════════ 11) نبضة شريط الصيانة بعد النزول ═══════════════ */

/**
 * نبضة هالة حمراء واحدة تُشغَّل بعد استقرار الشريط العلوي إن وُجد
 * موقع متجاوز للموعد (اختيار 11-3). تُرجع قيمة ألفا للهالة
 * يرسمها الشريط خلف حدوده.
 */
@Composable
fun rememberAlertPulse(active: Boolean): Float {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            pulse.snapTo(0f)
            delay(320)
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 700
                    0.9f at 180
                    0f at 700
                }
            )
        } else {
            pulse.snapTo(0f)
        }
    }
    return pulse.value
}

/* ═══════════════ الجولة الثالثة — اختيارات 2.6 ═══════════════ */

/* ═══════════════ 32) كشف دائري لحوارات الإضافة ═══════════════ */

/**
 * غلاف حوار «كشف دائري» (اختيار 32 = خيار 3 من اختيارات الجولة
 * الثالثة): يتمدد محتوى الحوار من نقطة أسفل الشاشة (ناحية الزر
 * العائم الذي فتحه) بتكبير من `TransformOrigin(0.5f, 1f)` بدل
 * الظهور المفاجئ، ويغلق بانكماش قصير قبل تنفيذ أي إجراء.
 * نفس عقد `GalaxyDialogShell` (`requestClose`) حتى يسهل الاستبدال.
 */
@Composable
fun GalaxyRevealDialog(
    onDismissRequest: () -> Unit,
    content: @Composable (requestClose: ((() -> Unit) -> Unit)) -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val requestClose: ((() -> Unit) -> Unit) = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                delay(200)
                action()
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (entered && !closing) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (entered) 380 else 180,
            easing = FastOutSlowInEasing
        ),
        label = "reveal-scale"
    )

    LaunchedEffect(Unit) { entered = true }

    Dialog(onDismissRequest = { requestClose(onDismissRequest) }) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
                contentAlignment = Alignment.Center
            ) {
                content(requestClose)
            }
        }
    }
}

/* ═══════════════ 34) مروف الأرقام عند تغيّر القيم ═══════════════ */

/**
 * رقم ينزلق قديمه للأعلى ويدخل الجديد من الأسفل عند تغيّر القيمة
 * (اختيار 34 = خيار 6) — حياة في البيانات دون عجلة العدّاد الكاملة
 * (تلك لظهور الشاشة أول مرة في `OdometerNumber`).
 */
@Composable
fun GalaxyNumberMorph(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(260)) { it } + fadeIn(tween(220)))
                .togetherWith(
                    slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(180))
                )
        },
        label = "number-morph"
    ) { v ->
        Text(v.toString() + suffix, style = style, color = color)
    }
}

/* ═══════════════ 37) حلقة عد تنازلي لاستحقاق الصيانة ═══════════════ */

/**
 * حلقة تستنزف مع اقتراب موعد الصيانة وتتلون حسب الخطورة:
 * سماوي (> 14 يومًا) ← كهرماني (1-14) ← أحمر (تجاوز/اليوم)،
 * وفي مركزها عدد الأيام (اختيار 37 = خيار 10). نافذة الحساب 30
 * يومًا كنسبة الاستنزاف، والسالب يُقص عند الصفر مع بقاء اللون الأحمر.
 */
@Composable
fun DueCountdownRing(
    days: Long,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val windowDays = 30f
    val targetFraction = (days.coerceAtLeast(0).toFloat() / windowDays).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "due-ring-fraction"
    )
    // إصلاح اتساق الحالات: الأحمر للتأخر الفعلي (يوم سالب) فقط،
    // ومستحق اليوم يدخل في النطاق الكهرماني «قريب» مثل بقية الشاشات
    // (كان `days <= 0` يجعل مستحق اليوم أحمر كما لو أنه متأخر).
    val ringColor by animateColorAsState(
        targetValue = when {
            days < 0 -> Color(0xFFFF5A5A)
            days <= 7 -> Color(0xFFFFC857)
            else -> Color(0xFF38BDF8)
        },
        animationSpec = tween(400),
        label = "due-ring-color"
    )
    val numberColor = ringColor

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.5.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(
                color = ringColor.copy(alpha = 0.15f),
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            if (fraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            kotlin.math.abs(days).toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = numberColor
        )
    }
}

/* ═══════════════ 39) منزلق بفقاعة قيمة طافية ═══════════════ */

/**
 * منزلق تظهر فوق مقبضه فقاعة بالقيمة الحالية أثناء السحب وتختفي
 * بعد الإفلات بنابض (اختيار 39 = خيار 13). موضع الفقاعة محسوب من
 * عرض المنزلق الفعلي ونسبة القيمة، صحيح في اتجاهي العرض (يُحسب
 * فيزيائيًا من المنتصف فلا يعتمد على اتجاه التخطيط).
 */
@Composable
fun GalaxyBubbleSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() }
) {
    val scope = rememberCoroutineScope()
    var bubbleVisible by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var widthPx by remember { mutableFloatState() }
    val density = LocalDensity.current

    val span = (valueRange.last - valueRange.first).coerceAtLeast(1)
    val fraction = (value - valueRange.first).toFloat() / span
    // في الاتجاهين: المقبض في المنتصف عند 0.5 — الإزاحة من المنتصف
    // تصح فيزيائيًا سواء كان التخطيط RTL أم LTR. (toPx يحتاج كثافة)
    val thumbTravel = widthPx - with(density) { 20.dp.toPx() }
    val offsetPx = (0.5f - fraction) * thumbTravel

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { f ->
                onValueChange(f.toInt())
                bubbleVisible = true
                hideJob?.cancel()
                hideJob = scope.launch {
                    delay(900)
                    bubbleVisible = false
                }
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp)
        )
        AnimatedVisibility(
            visible = bubbleVisible && widthPx > 0f,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .absoluteOffset { IntOffset(offsetPx.toInt(), 0) },
            enter = fadeIn(tween(150)) + scaleIn(
                initialScale = 0.6f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(250))
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp
            ) {
                Text(
                    format(value),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** حالة `mutableFloatStateOf` لـ Float بأداء أفضل عند تتبع القيم العائمة */
private fun mutableFloatState() = androidx.compose.runtime.mutableFloatStateOf(0f)

/* ═══════════════ 40) مفتاح الوضع الليلي بشمس وقمر ═══════════════ */

/**
 * مفتاح تبديل الوضع: المقبض يحمل قمرًا ينقلب شمسًا أثناء الانزلاق
 * والمسار يتلون بينهما، مع شرارات نجمية صغيرة لحظة التبديل
 * (اختيار 40 = خيار 14). `checked = true` يعني الوضع الليلي.
 */
@Composable
fun GalaxySunMoonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF13294A) else Color(0xFFBFD9F0),
        animationSpec = tween(450),
        label = "switch-track"
    )
    val iconSpin by animateFloatAsState(
        targetValue = if (checked) 0f else 180f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "switch-spin"
    )
    // شرارات لحظة التبديل (وليس عند أول تركيب)
    val spark = remember { Animatable(0f) }
    val composedOnce = remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        val first = !composedOnce.value
        composedOnce.value = true
        if (!first) {
            spark.snapTo(0f)
            spark.animateTo(1f, animationSpec = tween(520))
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // الشرارات حول المفتاح أثناء التبديل
        Canvas(Modifier.matchParentSize()) {
            val p = spark.value
            if (p > 0f && p < 1f) {
                val center = Offset(size.width / 2f, size.height / 2f)
                repeat(7) { i ->
                    val angle = (i * 51f + 14f) * PI.toFloat() / 180f
                    val radius = (26.dp + 10.dp * (i % 3)).toPx() * p
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFF38BDF8).copy(alpha = 1f - p) else Color(0xFFFFC857).copy(alpha = 1f - p),
                        radius = 1.6.dp.toPx(),
                        center = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = trackColor,
                uncheckedTrackColor = trackColor,
                checkedThumbColor = Color(0xFF0B1220),
                uncheckedThumbColor = Color(0xFFFFC857)
            ),
            thumbContent = {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.DarkMode,
                        contentDescription = null,
                        tint = Color(0xFFCFE6FF),
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                rotationZ = iconSpin
                                alpha = if (checked) 1f else 0f
                                scaleX = if (checked) 1f else 0.5f
                                scaleY = if (checked) 1f else 0.5f
                            }
                    )
                    Icon(
                        Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = Color(0xFF7A4D00),
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                rotationZ = iconSpin - 180f
                                alpha = if (checked) 0f else 1f
                                scaleX = if (checked) 0.5f else 1f
                                scaleY = if (checked) 0.5f else 1f
                            }
                    )
                }
            }
        )
    }
}

/* ═══════════════ 43) شيمر سديمي أثناء التحميل ═══════════════ */

/**
 * هيكل تحميل «سديمي»: سطح بلون متغير يلمع عليه تدرج منجرف بلا
 * نهاية بألوان الهوية (اختيار 43 = خيار 18) — الانتظار يبقى داخل
 * عالم المجرة بدل السبينر العام.
 */
@Composable
fun GalaxyShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-shift"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val glowA = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val glowB = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    Box(
        modifier = modifier.drawBehind {
            drawRect(color = base)
            val bandWidth = this.size.width * 0.9f
            val startX = -bandWidth + (this.size.width + bandWidth * 2f) * shift
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, glowA, glowB, Color.Transparent),
                    start = Offset(startX, 0f),
                    end = Offset(startX + bandWidth, this.size.height * 0.5f)
                )
            )
        }
    )
}

/* ═══════════════ 44) انفجار نجوم عند نجاح العمليات ═══════════════ */

/**
 * طبقة نجوم تتطاير من المركز لحظة نجاح عملية مهمة (اختيار 44 =
 * خيار 20): 12 نجمة بألوان الهوية بزوايا وأنصاف أقطار ثابتة
 * (بلا عشوائية كعادة التطبيق). تُشغَّل عند تغيّر `trigger` لقيمة
 * أكبر من صفر — يزود المستدعي عدادًا يرفعه بعد كل نجاح.
 * تُوضع فوق الهدف بمقاس يكفي انتشار النجوم (120-160 نقطة).
 */
@Composable
fun GalaxyStarBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(850))
        }
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    Canvas(modifier = modifier) {
        val p = progress.value
        if (trigger > 0 && p > 0f && p < 1f) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            repeat(12) { i ->
                val angle = (i * 30f + 12f) * PI.toFloat() / 180f
                val spread = (34.dp + 22.dp * ((i * 7) % 5) / 5f).toPx()
                val radius = spread * p
                drawCircle(
                    color = colors[i % 3].copy(alpha = 1f - p),
                    radius = 2.4.dp.toPx() * (1f - p * 0.5f),
                    center = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                )
            }
        }
    }
}
