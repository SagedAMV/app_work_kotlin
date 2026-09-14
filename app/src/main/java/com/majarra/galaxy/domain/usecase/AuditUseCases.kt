package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.AuditLog
import com.majarra.galaxy.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** مراقبة آخر 500 عملية في سجل التدقيق */
class ObserveAuditUseCase @Inject constructor(
    private val repo: AuditRepository
) {
    operator fun invoke(): Flow<List<AuditLog>> = repo.observeRecent()
}

/** مسح سجل التدقيق بالكامل (من الإعدادات فقط) */
class ClearAuditUseCase @Inject constructor(
    private val repo: AuditRepository
) {
    suspend operator fun invoke() = repo.clear()
}
