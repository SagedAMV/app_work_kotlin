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

/**
 * إشعارات مجرة — النسخة المبسطة جدًا حسب تعليمات.md:
 *  - قناة واحدة فقط (تنبيهات الصيانة)، بلا قنوات متعددة.
 *  - بلا عمال (WorkManager) ولا جدولة دورية: الفحص يحدث عند فتح التطبيق.
 *  - أوفلاين 100٪: لا FCM ولا أي شبكة.
 */
object GalaxyNotifications {

    const val CHANNEL_MAINTENANCE = "galaxy_maintenance"
    const val CHANNEL_BACKUP = "galaxy_backup_reminder"
    private const val ID_MAINTENANCE_DUE = 2001
    private const val ID_BACKUP_REMINDER = 2002

    /** تُنشأ القناتان مرة واحدة — تُستدعى من GalaxyApplication.onCreate */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val maintenance = NotificationChannel(
            CHANNEL_MAINTENANCE,
            context.getString(R.string.maintenance_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.maintenance_channel_desc)
        }
        val backup = NotificationChannel(
            CHANNEL_BACKUP,
            context.getString(R.string.backup_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.backup_channel_desc)
        }
        manager.createNotificationChannel(maintenance)
        manager.createNotificationChannel(backup)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * إشعار بسيط بقرب موعد صيانة موقع واحد أو أكثر.
     * يعيد هل نُشر فعلًا (قد يُرفض الإذن أو تُسحب الصلاحية).
     */
    fun notifyMaintenanceDue(context: Context, dueSites: List<Pair<String, Long>>): Boolean {
        if (dueSites.isEmpty() || !hasPermission(context)) return false

        val title = "مجرة — صيانة مستحقة"
        val body = if (dueSites.size == 1) {
            "موقع «${dueSites.first().first}» اقترب موعد صيانته"
        } else {
            "${dueSites.size} مواقع اقتربت مواعيد صيانتها — افتح التطبيق للتفاصيل"
        }

        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MAINTENANCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(ID_MAINTENANCE_DUE, notification)
            true
        } catch (e: SecurityException) {
            // يحدث إن سُحب الإذن بين الفحص والنشر
            false
        }
    }

    /**
     * تذكير دوري بأخذ نسخة احتياطية إن وُجدت تغييرات (إجابة الاسئله.md).
     * يعيد هل نُشر فعلًا — الفحص نفسه في BackupReminderUseCase.
     */
    fun notifyBackupReminder(context: Context): Boolean {
        if (!hasPermission(context)) return false

        val intent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("مجرة — حان وقت النسخة الاحتياطية")
            .setContentText("بياناتك تغيّرت منذ آخر نسخة — صدّر نسخة من شاشة الإعدادات")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(ID_BACKUP_REMINDER, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
