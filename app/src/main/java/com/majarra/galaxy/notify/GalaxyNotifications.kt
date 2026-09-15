package com.majarra.galaxy.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.majarra.galaxy.MainActivity
import com.majarra.galaxy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * إشعارات مجرة — أوفلاين 100٪ (بلا FCM ولا أي شبكة).
 *
 * سبب الإضافة: كان التطبيق يُنشئ صفوف تنبيهات في قاعدة البيانات فقط، ولا تصل
 * أي إشعارات نظام للمستخدم (لا قناة، ولا إذن POST_NOTIFICATIONS، ولا نشر).
 * النتيجة: «محرك التنبيهات» موجود لكنه صامت عمليًا. الآن:
 *  - قناة إشعارات معرَّفة (إلزامية من Android 8+ وهدف التطبيق 34).
 *  - نشر إشعار مُلخَّص بعد كل فحص أنشأ تنبيهات جديدة.
 *  - فحص إذن POST_NOTIFICATIONS قبل النشر.
 */
object GalaxyNotifications {

    const val CHANNEL_ALERTS = "galaxy_alerts"
    internal const val ID_ALERT_SUMMARY = 1001

    /** يُنشئ القناة مرة واحدة — يُستدعى من GalaxyApplication.onCreate */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.alerts_channel_desc)
            enableLights(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}

/** ناشر التنبيهات — يُستخدم من العامل الدوري ومن زر «افحص الآن» */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** ينشر إشعارًا مُلخَّصًا بعدد التنبيهات الجديدة؛ يعيد هل نُشر فعلًا */
    fun notifyNewAlerts(created: Int, unreadTotal: Int = 0): Boolean {
        if (created <= 0) return false
        if (!GalaxyNotifications.hasPermission(context)) return false

        val title = "مجرة — $created تنبيه جديد"
        val body = if (unreadTotal > created) {
            "إجمالي التنبيهات غير المقروءة: $unreadTotal"
        } else {
            "صيانة مستحقة أو نقص مخزون أو انتهاء عمر معدة — افتح التطبيق للتفاصيل"
        }

        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, GalaxyNotifications.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify(GalaxyNotifications.ID_ALERT_SUMMARY, notification)
            true
        } catch (e: SecurityException) {
            // يحدث إن سُحب الإذن بين الفحص والنشر
            false
        }
    }
}
