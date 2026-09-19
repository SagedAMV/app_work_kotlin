package com.majarra.galaxy.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/* ============================================================
 * DAOs — النسخة المبسطة: استعلامات قليلة وواضحة.
 * المراقبة عبر Flow والعمليات الفردية عبر suspend.
 * ============================================================ */

@Dao
interface SiteDao {
    /**
     * المواقع النشطة فقط (المؤرشفة تُعرض في مسار منفصل).
     * الترتيب حسب إجابة الاسئله.md: الأحدث تعديلًا أولًا.
     */
    @Query("SELECT * FROM sites WHERE archived = 0 ORDER BY lastModified DESC")
    fun observeAll(): Flow<List<Site>>

    /** المواقع المؤرشفة — الأحدث تعديلًا أولًا */
    @Query("SELECT * FROM sites WHERE archived = 1 ORDER BY lastModified DESC")
    fun observeArchived(): Flow<List<Site>>

    /** مواقع تصنيف واحد (غير المؤرشفة) */
    @Query("SELECT * FROM sites WHERE archived = 0 AND categoryId = :categoryId ORDER BY lastModified DESC")
    fun observeByCategory(categoryId: Long): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE id = :id")
    fun observeById(id: Long): Flow<Site?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: Long): Site?

    @Query("SELECT * FROM sites WHERE archived = 0 AND name LIKE '%' || :q || '%' ORDER BY lastModified DESC")
    fun search(q: String): Flow<List<Site>>

    @Query("SELECT * FROM sites")
    suspend fun getAll(): List<Site>

    @Query("SELECT COUNT(*) FROM sites WHERE archived = 0")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM sites WHERE archived = 1")
    suspend fun countArchived(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site): Long

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)
}

/** تصنيفات المواقع — مراقبة كاملة لأن القائمة صغيرة وتُعرض في أكثر من شاشة */
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface SiteDetailDao {
    @Query("SELECT * FROM site_details WHERE siteId = :siteId")
    fun observeBySite(siteId: Long): Flow<SiteDetail?>

    @Query("SELECT * FROM site_details WHERE siteId = :siteId")
    suspend fun getBySite(siteId: Long): SiteDetail?

    @Query("SELECT * FROM site_details")
    suspend fun getAll(): List<SiteDetail>

    /** صف واحد لكل موقع: الموقع الفريد يجعل الإحلال آمنًا */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detail: SiteDetail): Long
}

@Dao
interface MaintenanceLogDao {
    @Query("SELECT * FROM maintenance_logs WHERE siteId = :siteId ORDER BY maintenanceDate DESC")
    fun observeBySite(siteId: Long): Flow<List<MaintenanceLog>>

    @Query("SELECT COUNT(*) FROM maintenance_logs")
    suspend fun countAll(): Int

    @Query("SELECT * FROM maintenance_logs")
    suspend fun getAll(): List<MaintenanceLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MaintenanceLog): Long

    @Delete
    suspend fun delete(log: MaintenanceLog)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE siteId = :siteId ORDER BY uploadedDate DESC")
    fun observeBySite(siteId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE siteId = :siteId")
    suspend fun getBySite(siteId: Long): List<Attachment>

    @Query("SELECT COUNT(*) FROM attachments")
    suspend fun countAll(): Int

    /** النوع يُمرَّر باسم التعداد نصًا كما يُخزَّن في العمود */
    @Query("SELECT COUNT(*) FROM attachments WHERE fileType = :typeName")
    suspend fun countByType(typeName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: Attachment): Long

    @Delete
    suspend fun delete(a: Attachment)
}

@Dao
interface AppSettingDao {
    /** قراءة متزامنة مقصودة: تُستخدم عند الإقلاع لفحص القفل قبل أي واجهة */
    @Query("SELECT * FROM app_settings")
    fun getAllSync(): List<AppSetting>

    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key")
    fun getValueSync(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSetting)
}

/**
 * كتالوج المواد الموحد — مراقبة كاملة لأن القائمة تُعرض في أكثر من
 * شاشة (إدارة الكتالوج + واجهات الاختيار في كل موقع).
 */
@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Material>>

    @Query("SELECT * FROM materials ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Material>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: Material): Long

    @Update
    suspend fun update(material: Material)

    @Delete
    suspend fun delete(material: Material)
}

/**
 * سجل النزول الطارئ/الاستكشاف (النسخة 2.4).
 * النسخة 2.8 (اختيار 21 من جلسة المواد والسحوبات والطوارئ): أُضيفت
 * المراقبة حسب الموقع حتى يعرض تبويب «الطوارئ» في تفاصيل الموقع
 * سجل النزولات — السجلات كانت تُحفظ بلا أي وسيلة قراءة. الحذف ما زال
 * يتم متسلسلًا مع حذف موقعه ولا حاجة لتحديث سجل محفوظ كما هو.
 */
@Dao
interface EmergencyVisitDao {
    /** نزولات موقع واحد بترتيب الأحدث أولًا (تبويب الطوارئ) */
    @Query("SELECT * FROM emergency_visits WHERE siteId = :siteId ORDER BY visitDate DESC")
    fun observeBySite(siteId: Long): Flow<List<EmergencyVisit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: EmergencyVisit): Long
}

/**
 * روابط شبكة المجرة (النسخة 2.5) — مراقبة كاملة لأن الشبكة تُرسم
 * دفعة واحدة، والإدخال بـ IGNORE حتى يعود -1 عند محاولة تكرار رابط
 * قائم (بدل استثناء يقطع التدفق).
 */
@Dao
interface SiteLinkDao {
    @Query("SELECT * FROM site_links ORDER BY createdDate ASC")
    fun observeAll(): Flow<List<SiteLink>>

    /** @return معرف الصف الجديد، أو -1 إن كان الرابط موجودًا مسبقًا */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: SiteLink): Long

    /** حذف الرابط بين موقعين بأي اتجاه كان */
    @Query(
        "DELETE FROM site_links WHERE " +
            "(fromSiteId = :a AND toSiteId = :b) OR (fromSiteId = :b AND toSiteId = :a)"
    )
    suspend fun deleteBetween(a: Long, b: Long)
}

/** سجل السحب والإرجاع — يُعرض داخل موقعه ويُعدّ المفتوح منه للإحصائيات */
@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE siteId = :siteId ORDER BY withdrawnDate DESC")
    fun observeBySite(siteId: Long): Flow<List<Withdrawal>>

    /** عدد المواد المسحوبة التي لم تُرجع بعد (لكل المواقع) */
    @Query("SELECT COUNT(*) FROM withdrawals WHERE status != 'RETURNED'")
    suspend fun countOpen(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(withdrawal: Withdrawal): Long

    @Update
    suspend fun update(withdrawal: Withdrawal)

    @Delete
    suspend fun delete(withdrawal: Withdrawal)
}
