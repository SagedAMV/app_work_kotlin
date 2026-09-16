package com.majarra.galaxy.domain.repository

import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDetail
import kotlinx.coroutines.flow.Flow

/* ============================================================
 * واجهات المستودعات — طبقة الـ Domain لا تعرف تفاصيل Room.
 * النسخة المبسطة: مواقع، تفاصيل، سجل صيانة، مرفقات، إعدادات.
 * ============================================================ */

interface SiteRepository {
    fun observeSites(): Flow<List<Site>>
    fun searchSites(q: String): Flow<List<Site>>
    fun observeSite(id: Long): Flow<Site?>
    suspend fun getSite(id: Long): Site?
    suspend fun getAll(): List<Site>
    suspend fun insert(site: Site): Long
    suspend fun update(site: Site)
    suspend fun delete(site: Site)
}

interface SiteDetailRepository {
    fun observeBySite(siteId: Long): Flow<SiteDetail?>
    suspend fun getBySite(siteId: Long): SiteDetail?
    suspend fun getAll(): List<SiteDetail>
    suspend fun upsert(detail: SiteDetail): Long
}

interface MaintenanceLogRepository {
    fun observeBySite(siteId: Long): Flow<List<MaintenanceLog>>
    suspend fun insert(log: MaintenanceLog): Long
    suspend fun delete(log: MaintenanceLog)
}

interface AttachmentRepository {
    fun observeBySite(siteId: Long): Flow<List<Attachment>>
    suspend fun getBySite(siteId: Long): List<Attachment>
    suspend fun insert(a: Attachment): Long
    suspend fun delete(a: Attachment)
}

/**
 * إعدادات التطبيق (جدول app_settings):
 * الوضع الليلي + القفل البسيط برمز سري.
 */
interface SettingsRepository {
    data class Prefs(
        val darkMode: Boolean = true,
        val lockEnabled: Boolean = false,
        val hasPin: Boolean = false
    )

    val preferences: Flow<Prefs>

    /**
     * القيمة الحالية المخزَّنة فعليًا (قراءة متزامنة).
     * تُستخدم كقيمة ابتدائية للواجهة حتى لا يظهر التطبيق مفتوحًا
     * لحظة الإقلاع بينما القفل مفعّل (تجاوز صامت للقفل).
     */
    val current: Prefs

    suspend fun setDarkMode(enabled: Boolean)

    /** تفعيل القفل يتطلب وجود رمز سري محفوظ مسبقًا */
    suspend fun setLockEnabled(enabled: Boolean)

    /** حفظ رمز سري جديد — يُخزَّن كبصمة (تجزئة) لا نصًا صريحًا، ويُفعَّل القفل معه */
    suspend fun setPin(pin: String)

    /** تحقق متزامن من الرمز — يُستخدم في شاشة القفل */
    fun verifyPin(pin: String): Boolean

    /** آخر يوم (epochDay) أُرسل فيه إشعار قرب الصيانة — لمنع تكرار الإشعار كل فتح */
    fun getLastDueNoticeDay(): Long?
    suspend fun setLastDueNoticeDay(epochDay: Long)
}
