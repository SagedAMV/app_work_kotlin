package com.majarra.galaxy.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.majarra.galaxy.domain.usecase.CheckAlertsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * عامل التنبيهات الدوري — يفحص:
 * 1) الصيانة المستحقة خلال 7 أيام.
 * 2) أعمار المعدات المنتهية.
 * 3) نقص المخزون.
 * يُجدول كل 12 ساعة من GalaxyApplication.
 */
@HiltWorker
class AlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val checkAlerts: CheckAlertsUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            checkAlerts()
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "galaxy_alerts_worker"
    }
}
