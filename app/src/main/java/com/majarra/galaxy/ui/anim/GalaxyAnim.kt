package com.majarra.galaxy.ui.anim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================================================
 * مكتبة انيميشنات «مجرة» — الإصدار 2.3
 * تنفيذ اختيارات جلسة المقترحات (ملف اختيارات.md):
 *  1) نبض توهج للأزرار        2) تمدد شريطي لزر الإضافة
 *  3) تحول زر الحالة لدائرة   4) قفز أرقام القفل
 *  5) رسم خط علامة الصح       6) حلقة منزلقة للألوان
 * 10) حوار بتكبير نابض       12) سنابار بشريط مهلة
 * 13) ظهور متتابع للقوائم    15) عجلة أرقام الإحصائيات
 * 16) تنفس لوني للشارات       8) تحول حاوي لفتح التفاصيل
 * ============================================================ */

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

/* ═══════════════ 4) شاشة القفل — قفز أرقام الرمز ═══════════════ */

/**
 * خانات الرمز السري: كل رقم يُدخل تظهر نقطته بقفزة نابضة
 * (0 → 125% → 100%) في خانته (اختيار 4-2).
 */
@Composable
fun PinDots(pin: String, maxLen: Int = 8, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLen) { index ->
            val filled = index < pin.length
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
                // النقطة الداخلية تقفز بنوابض عند امتلاء الخانة
                // (بديل مقياس الرسم بدل AnimatedVisibility — أمتن داخل الصفوف)
                val dotScale by animateFloatAsState(
                    targetValue = if (filled) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pin-dot-$index"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = dotScale
                            scaleY = dotScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/* ═══════════════ 5) علامات الصح — رسم الخط ═══════════════ */

/**
 * مربع تحديد تُرسم داخله علامة الصح خطًا متحركًا كأنها كُتبت
 * باليد (اختيار 5-1) — بديل بصري لـ Checkbox الافتراضي.
 */
@Composable
fun DrawnCheck(
    checked: Boolean,
    onToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val fraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(240),
        label = "check-draw"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "check-border"
    )
    val checkColor = MaterialTheme.colorScheme.secondary
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
    val rowHeight = 38.dp
    val cap = 40
    val start = (value - cap).coerceAtLeast(0)
    val offset by animateDpAsState(
        targetValue = rowHeight * (value - start),
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
        label = "odometer"
    )
    Box(
        modifier = modifier
            .height(rowHeight)
            .clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.CenterStart
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

/* ═══════════════ 8) تحول الحاوية — فتح تفاصيل الموقع ═══════════════ */

/** وصف عملية فتح موقع: البطاقة المصدر ومستطيلها في إحداثيات النافذة */
data class SiteLaunchTarget(
    val siteId: Long,
    val siteName: String,
    val from: Rect
)

/**
 * طبقة «تحول الحاوية» (اختيار 8-1 بنسخة يدوية متوافقة مع BOM 2024.02):
 * سطح بلون الشاشة يتمدد من مستطيل البطاقة حتى يملأ الشاشة خلال
 * 330 مللي ثانية، ثم يُستدعى `onArrived` للتنقل الفعلي — فيبدو
 * أن البطاقة نفسها تحولت إلى شاشة التفاصيل.
 */
@Composable
fun SiteLaunchOverlay(
    target: SiteLaunchTarget,
    container: Rect,
    onArrived: () -> Unit
) {
    val density = LocalDensity.current
    val t = remember { Animatable(0f) }
    var arrived by remember { mutableStateOf(false) }

    LaunchedEffect(target.siteId) {
        t.snapTo(0f)
        t.animateTo(1f, animationSpec = tween(330, easing = FastOutSlowInEasing))
        if (!arrived) {
            arrived = true
            onArrived()
        }
    }

    val rect = lerp(target.from, container, t.value)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val titleColor = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        // تعتيم خفيف يركز النظر على السطح المتمدد
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * t.value)))
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { rect.left.toDp() },
                    y = with(density) { rect.top.toDp() }
                )
                .width(with(density) { rect.width.toDp() })
                .height(with(density) { rect.height.toDp() })
                .background(surfaceColor, RoundedCornerShape(lerp(16f, 0f, t.value).dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                target.siteName,
                style = MaterialTheme.typography.titleLarge,
                color = titleColor,
                modifier = Modifier.graphicsLayer { alpha = t.value }
            )
        }
    }
}

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
