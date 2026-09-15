package com.majarra.galaxy.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.usecase.CheckAlertsUseCase
import com.majarra.galaxy.notify.AlertNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * عامل التنبيهات الدوري — يفحص:
 * 1) الصيانة المستحقة خلال 7 أيام.
 * 2) أعمار المعدات المنتهية.
 * 3) نقص المخزون.
 * يُجدول كل 12 ساعة من GalaxyApplication، ثم ينشر إشعار نظام مُلخَّصًا
 * بالتنبيهات الجديدة (كان الفحص يكتفي بكتابة صفوف في قاعدة البيانات).
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val checkAlerts: CheckAlertsUseCase,
    private val alertRepo: AlertRepository,
    private val notifier: AlertNotifier
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val created = checkAlerts()
            if (created > 0) {
                val unread = runCatching { alertRepo.observeUnreadCount().first() }.getOrDefault(created)
                notifier.notifyNewAlerts(created, unread)
            }
            Result.success()
        } catch (t: Throwable) {
            // تسجيل السبب الحقيقي بدل ابتلاعه — الفشل الصامت يمنع أي تشخيص
            Log.e(TAG, "alert check failed (attempt ${runAttemptCount + 1})", t)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "galaxy_alerts_worker"
        private const val TAG = "AlertWorker"
        private const val MAX_ATTEMPTS = 3
    }
}
