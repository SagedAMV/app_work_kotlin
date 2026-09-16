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
    @Query("SELECT * FROM sites ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Site>>

    @Query("SELECT * FROM sites WHERE id = :id")
    fun observeById(id: Long): Flow<Site?>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: Long): Site?

    @Query("SELECT * FROM sites WHERE name LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<Site>>

    @Query("SELECT * FROM sites")
    suspend fun getAll(): List<Site>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site): Long

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)
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
