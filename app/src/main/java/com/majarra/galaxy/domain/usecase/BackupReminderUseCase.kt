package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * تذكير النسخ الاحتياطي الدوري (إجابة الاسئله.md: تذكير كل أسبوعين
 * إن وُجدت تغييرات). يُفحص عند فتح التطبيق فقط كبقية التنبيهات —
 * بلا عمال خلفية.
 *
 * شروط التذكير كلها معًا:
 * 1) مرّ 14 يومًا أو أكثر على آخر نسخة احتياطية ناجحة.
 * 2) وُجدت بيانات تغيّرت بعد تلك النسخة (أي موقع آخر تعديل فيها
 *    أحدث من تاريخ النسخة).
 * 3) لم يُرسل تذكير خلال آخر 14 يومًا (حتى لا يتحول إلى إلحاح يومي
 *    إن بقيت الظروف نفسها).
 *
 * إن لم تُؤخذ نسخة قط، يُثبَّت تاريخ أول فحص كأساس وتبدأ الدورة منه.
 */
class BackupReminderUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val siteRepo: SiteRepository
) {

    suspend operator fun invoke(): Boolean {
        val now = System.currentTimeMillis()
        val lastBackup = settings.getLastBackupTs()
            ?: run {
                // أول تشغيل بعد الترقية: تثبيت الأساس بلا تذكير مفاجئ
                settings.setLastBackupTs(now)
                return false
            }

        if (now - lastBackup < REMINDER_INTERVAL_MS) return false

        // هل تغيّرت البيانات منذ آخر نسخة؟
        val newestChange = siteRepo.getAll().maxOfOrNull { it.lastModified } ?: 0L
        if (newestChange <= lastBackup) return false

        // حارس عدم الإلحاح: لا تذكير إن أُرسل واحد مؤخرًا
        val today = LocalDate.now().toEpochDay()
        val lastReminderDay = settings.getLastBackupReminderDay()
        if (lastReminderDay != null && (today - lastReminderDay) * DAY_MS < REMINDER_INTERVAL_MS) {
            return false
        }
        return true
    }

    companion object {
        private const val DAY_MS = 24L * 3600 * 1000
        private const val REMINDER_INTERVAL_MS = 14 * DAY_MS
    }
}
