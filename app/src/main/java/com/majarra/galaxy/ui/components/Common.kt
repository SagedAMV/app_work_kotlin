package com.majarra.galaxy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.majarra.galaxy.ui.anim.GalaxyDialogShell
import com.majarra.galaxy.util.DateFormats
import kotlinx.coroutines.delay

/* ============================================================
 * مكونات مشتركة تُستخدم في كل الشاشات للحفاظ على الاتساق.
 * ============================================================ */

/**
 * تنسيق التاريخ والوقت بالعربية — التفاصيل (المنطقة الزمنية، الأمان بين
 * الخيوط، Locale الصحيح) في DateFormats حتى لا تُنشأ SimpleDateFormat
 * جديدة مع كل عنصر قائمة.
 */
fun Long.formatDateTime(): String = DateFormats.dateTime(this)

fun Long.formatDate(): String = DateFormats.date(this)

/** بطاقة عامة بخلفية السطح */
@Composable
fun GalaxyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        // بطاقة قابلة للنقر فقط عند وجود فعل حقيقي — تمرير onClick فارغًا
        // يجعل كل بطاقة تستهلك النقرات وتُظهر تأثيرًا وهميًا.
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, onClick = onClick) {
            content()
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            content()
        }
    }
}

/** عنوان قسم */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

/**
 * حالة قائمة فارغة (انيميشن اختيارات 2.3 — مقترح 19):
 * الأيقونة تتنفس (تكبر وتصغر بهدوء) والنصان يظهران متتابعين.
 */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String = "") {
    // تنفس الأيقونة: دورة هادئة لا نهائية بين 100% و107%
    val breath = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breath.animateTo(1f, tween(1300))
            breath.animateTo(0f, tween(1300))
        }
    }
    // ظهور النصوص متتابعًا بعد أيقونة التنفس
    var textsShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(180)
        textsShown = true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.graphicsLayer {
                val scale = 1f + 0.07f * breath.value
                scaleX = scale
                scaleY = scale
                alpha = 0.8f + 0.2f * breath.value
            }
        )
        AnimatedVisibility(
            visible = textsShown,
            enter = fadeIn(tween(350))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * حوار تأكيد عام (انيميشن اختيارات 2.3 — مقترح 10): يظهر من 90%
 * بنوابض لطيفة مع تعتيم تدريجي، ويخرج مصغّرًا قبل تنفيذ الإجراء.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "تأكيد",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GalaxyDialogShell(onDismissRequest = onDismiss) { requestClose ->
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { requestClose(onDismiss) }) { Text("إلغاء") }
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { requestClose(onConfirm) }) {
                        Text(confirmText, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** نقطة لون دائرية لعرض لون التصنيف */
@Composable
fun ColorDot(colorHex: String, modifier: Modifier = Modifier, sizeDp: Int = 12) {
    // اللون الاحتياطي يُقرأ في سياق التركيب (لا داخل remember) لأن
    // MaterialTheme.colorScheme دالة @Composable.
    val fallback = MaterialTheme.colorScheme.primary
    val color = remember(colorHex, fallback) {
        runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
            .getOrDefault(fallback)
    }
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * عارض صور ملء الشاشة (إجابة الاسئله.md): تكبير/تصغير بإصبعين وتحريك
 * بسحب الإصبع، ونقرة مزدوجة لإعادة الضبط. الحذف من زر سلة المهملات.
 */
@Composable
fun FullscreenImageViewer(
    uri: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "صورة مرفقة",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(uri) {
                    // تكبير/تصغير بإصبعين + تحريك بإصبع واحد
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                        // عند العودة للحجم الطبيعي نعيد الصورة للمركز
                        if (scale == 1f) offset = Offset.Zero
                    }
                }
                .pointerInput(uri) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        onTap = { onDismiss() }
                    )
                }
        )

        // شريط التحكم العلوي فوق الصورة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف الصورة", tint = Color.White)
            }
        }

        // تلميح سريع لطريقة الاستخدام
        Text(
            "تكبير بإصبعين — نقرة مزدوجة لإعادة الضبط",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xB3FFFFFF),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
