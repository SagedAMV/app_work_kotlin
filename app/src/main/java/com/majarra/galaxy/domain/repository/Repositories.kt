package com.majarra.galaxy.domain.repository

import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.Withdrawal
import kotlinx.coroutines.flow.Flow

/* ============================================================
 * واجهات المستودعات — طبقة الـ Domain لا تعرف تفاصيل Room.
 * النسخة 2.2: مواقع، تصنيفات، تفاصيل، سجل صيانة، مرفقات، إعدادات،
 * كتالوج المواد الموحد، وسجل السحب والإرجاع.
 * النسخة 2.4: سجل النزول الطارئ/الاستكشاف (تعليمات هذه الجلسة).
 * ============================================================ */

interface SiteRepository {
    fun observeSites(): Flow<List<Site>>
    fun observeArchivedSites(): Flow<List<Site>>
    fun observeByCategory(categoryId: Long): Flow<List<Site>>
    fun searchSites(q: String): Flow<List<Site>>
    fun observeSite(id: Long): Flow<Site?>
    suspend fun getSite(id: Long): Site?
    suspend fun getAll(): List<Site>
    suspend fun countActive(): Int
    suspend fun countArchived(): Int
    suspend fun insert(site: Site): Long
    suspend fun update(site: Site)
    suspend fun delete(site: Site)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
}

interface SiteDetailRepository {
    fun observeBySite(siteId: Long): Flow<SiteDetail?>
    suspend fun getBySite(siteId: Long): SiteDetail?
    suspend fun getAll(): List<SiteDetail>
    suspend fun upsert(detail: SiteDetail): Long
}

interface MaintenanceLogRepository {
    fun observeBySite(siteId: Long): Flow<List<MaintenanceLog>>
    suspend fun countAll(): Int
    suspend fun insert(log: MaintenanceLog): Long
    suspend fun delete(log: MaintenanceLog)
}

interface AttachmentRepository {
    fun observeBySite(siteId: Long): Flow<List<Attachment>>
    suspend fun getBySite(siteId: Long): List<Attachment>
    suspend fun countAll(): Int
    suspend fun countByType(typeName: String): Int
    suspend fun insert(a: Attachment): Long
    suspend fun delete(a: Attachment)
}

/** كتالوج المواد الموحد — مصدر وحيد لأسماء المواد تختار منه المواقع */
interface MaterialRepository {
    fun observeAll(): Flow<List<Material>>
    suspend fun getAll(): List<Material>
    suspend fun insert(material: Material): Long
    suspend fun update(material: Material)
    suspend fun delete(material: Material)
}

/** سجل سحب المواد وصيانتها وإرجاعها — دورة كاملة لكل مادة مسحوبة */
interface WithdrawalRepository {
    fun observeBySite(siteId: Long): Flow<List<Withdrawal>>
    suspend fun countOpen(): Int
    suspend fun insert(withdrawal: Withdrawal): Long
    suspend fun update(withdrawal: Withdrawal)
    suspend fun delete(withdrawal: Withdrawal)
}

/** سجل النزول الطارئ/الاستكشاف — إدخال سجل جديد لكل نزول */
interface EmergencyVisitRepository {
    suspend fun insert(visit: EmergencyVisit): Long
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

    /** آخر نسخة احتياطية ناجحة (epoch millis) — أساس تذكير النسخ الدوري */
    fun getLastBackupTs(): Long?
    suspend fun setLastBackupTs(epochMillis: Long)

    /** آخر يوم (epochDay) أُرسل فيه تذكير النسخ الاحتياطي — لعدم الإلحاح اليومي */
    fun getLastBackupReminderDay(): Long?
    suspend fun setLastBackupReminderDay(epochDay: Long)
}
