package com.majarra.galaxy

import android.app.Application
import android.util.Log
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.domain.usecase.CheckMaintenanceDueUseCase
import com.majarra.galaxy.notify.GalaxyNotifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * نقطة بداية التطبيق — النسخة المبسطة:
 * 1) تُنشئ قناة الإشعارات الوحيدة (الصيانة) — إلزامية من Android 8+.
 * 2) تفحص مواعيد الصيانة المستحقة عند الفتح وترسل إشعارًا بسيطًا،
 *    مرة واحدة في اليوم كحد أقصى (حارس التاريخ في الإعدادات).
 *
 * لا يوجد هنا أي عامل خلفي (WorkManager) ولا جدولة دورية حسب تعليمات
 * التبسيط، ولا بيانات تجريبية — المستخدم يبدأ بقائمة فارغة ويضيف
 * مواقعه بنفسه.
 */
@HiltAndroidApp
class GalaxyApplication : Application() {

    @Inject lateinit var checkMaintenanceDue: CheckMaintenanceDueUseCase
    @Inject lateinit var settings: SettingsRepository

    /** نطاق عمل خاص بالتطبيق لمهمة فحص الصيانة الخفيفة */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        GalaxyNotifications.ensureChannel(this)
        checkMaintenanceDueOncePerDay()
    }

    /**
     * إشعار قرب الصيانة: بسيط ومحدود — عند الفحص تُوجد مواقع مستحقة
     * ولم يُرسل إشعار اليوم بعد، يُنشر إشعار واحد ويُختم اليوم.
     */
    private fun checkMaintenanceDueOncePerDay() {
        appScope.launch {
            try {
                val today = LocalDate.now().toEpochDay()
                if (settings.getLastDueNoticeDay() == today) return@launch
                val dueSites = checkMaintenanceDue()
                if (dueSites.isEmpty()) return@launch
                val published = GalaxyNotifications.notifyMaintenanceDue(
                    this@GalaxyApplication,
                    dueSites.map { it.siteName to it.dueDate }
                )
                if (published) settings.setLastDueNoticeDay(today)
            } catch (t: Throwable) {
                // فشل التذكير لا يجب أن يسبب كراش للتطبيق
                Log.e(TAG, "maintenance due check failed", t)
            }
        }
    }

    companion object {
        private const val TAG = "GalaxyApp"
    }
}
