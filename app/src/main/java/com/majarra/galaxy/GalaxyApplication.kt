package com.majarra.galaxy

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.data.seed.DemoDataSeeder
import com.majarra.galaxy.notify.GalaxyNotifications
import com.majarra.galaxy.worker.AlertWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * نقطة بداية التطبيق.
 * 1) تهيّئ WorkManager يدويًا مع Hilt (تهيئة عند الطلب).
 * 2) تنشئ قناة الإشعارات (Android 8+ تحتاجها قبل أي نشر).
 * 3) تزرع البيانات التجريبية (50 موقعًا) عند أول تشغيل فقط.
 * 4) تجدول عامل التنبيهات الدوري (كل 12 ساعة) بقيود موفّرة للبطارية.
 */
@HiltAndroidApp
class GalaxyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var database: GalaxyDatabase
    @Inject lateinit var seeder: DemoDataSeeder

    /** نطاق عمل خاص بالتطبيق للمهام الخلفية البسيطة (البذر) */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        GalaxyNotifications.ensureChannels(this)
        seedIfEmpty()
        scheduleAlertWorker()
    }

    /** زرع البيانات التجريبية مرة واحدة فقط */
    private fun seedIfEmpty() {
        appScope.launch {
            try {
                if (database.siteDao().countSync() == 0) seeder.seed()
            } catch (t: Throwable) {
                // الفشل في البذر لا يجب أن يسبب كراش للتطبيق
                Log.e(TAG, "seed failed", t)
            }
        }
    }

    /**
     * جدولة عامل التنبيهات: صيانة مستحقة + أعمار المعدات + نقص المخزون.
     * - قيد «البطارية غير منخفضة» حتى لا يعمل الفحص في ظروف حرجة.
     * - تأجيل خطّي (30 ثانية تصاعديًا) بدل إعادة فورية عند الفشل.
     * - KEEP: لا تُكرَّر الجدولة عند كل فتح للتطبيق.
     */
    private fun scheduleAlertWorker() {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AlertWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    // ملاحظة حرجة (سبب الكراش عند فتح التطبيق):
    // كانت هذه الخاصية معرَّفة كـ "val" بمهيّئ مباشر، أي أنها كانت تُنفَّذ
    // ضمن بانية الكائن (constructor) فور إنشاء GalaxyApplication — أي قبل أن
    // يقوم Hilt بحقن `workerFactory` (الحقن الفعلي يحدث داخل onCreate()).
    // هذا يسبب UninitializedPropertyAccessException فورًا عند إنشاء التطبيق
    // (كراش قبل ظهور أي واجهة مستخدم). الحل: خاصية تُحسب عند الطلب (get).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    companion object {
        private const val TAG = "GalaxyApp"
    }
}
