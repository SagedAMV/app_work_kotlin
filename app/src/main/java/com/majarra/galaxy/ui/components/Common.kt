package com.majarra.galaxy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.majarra.galaxy.util.DateFormats

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

/** حالة قائمة فارغة */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** حوار تأكيد عام */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "تأكيد",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
