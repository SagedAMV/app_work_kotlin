package com.majarra.galaxy.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/* ============================================================
 * DAOs — كل الواجهات اللازمة لخزائن التطبيق.
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

    @Query("SELECT * FROM sites WHERE name LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<Site>>

    @Query("SELECT * FROM sites")
    suspend fun getAll(): List<Site>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(site: Site): Long

    @Update
    suspend fun update(site: Site)

    @Delete
    suspend fun delete(site: Site)

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun countSync(): Int
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipments WHERE siteId = :siteId ORDER BY category ASC")
    fun observeBySite(siteId: Long): Flow<List<Equipment>>

    @Query("SELECT * FROM equipments ORDER BY id DESC")
    fun observeAll(): Flow<List<Equipment>>

    @Query("SELECT * FROM equipments WHERE siteId = :siteId")
    suspend fun getBySite(siteId: Long): List<Equipment>

    @Query("SELECT * FROM equipments")
    suspend fun getAll(): List<Equipment>

    @Query("SELECT COUNT(*) FROM equipments WHERE siteId = :siteId")
    suspend fun countBySite(siteId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: Equipment): Long

    @Update
    suspend fun update(e: Equipment)

    @Delete
    suspend fun delete(e: Equipment)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE siteId = :siteId ORDER BY addedAt DESC")
    fun observeBySite(siteId: Long): Flow<List<Attachment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: Attachment): Long

    @Delete
    suspend fun delete(a: Attachment)
}

@Dao
interface SiteHistoryDao {
    @Query("SELECT * FROM site_history WHERE siteId = :siteId ORDER BY timestamp DESC")
    fun observeBySite(siteId: Long): Flow<List<SiteHistory>>

    @Insert
    suspend fun insert(h: SiteHistory)
}

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items")
    suspend fun getAll(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getById(id: Long): InventoryItem?

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun countSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(i: InventoryItem): Long

    @Update
    suspend fun update(i: InventoryItem)

    @Delete
    suspend fun delete(i: InventoryItem)
}

@Dao
interface RequirementDao {
    @Query("SELECT * FROM requirements ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Requirement>>

    @Query("SELECT * FROM requirements WHERE id = :id")
    suspend fun getById(id: Long): Requirement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: Requirement): Long

    @Update
    suspend fun update(r: Requirement)

    @Delete
    suspend fun delete(r: Requirement)
}

@Dao
interface RequirementItemDao {
    @Query("SELECT * FROM requirement_items WHERE requirementId = :rid")
    fun observeByRequirement(rid: Long): Flow<List<RequirementItem>>

    @Query("SELECT * FROM requirement_items WHERE requirementId = :rid")
    suspend fun getByRequirement(rid: Long): List<RequirementItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RequirementItem>)

    @Update
    suspend fun update(item: RequirementItem)
}

@Dao
interface BoqDao {
    @Query("SELECT * FROM boq_documents ORDER BY createdAt DESC")
    fun observeDocuments(): Flow<List<BoqDocument>>

    @Query("SELECT * FROM boq_lines WHERE boqId = :boqId")
    fun observeLines(boqId: Long): Flow<List<BoqLine>>

    @Query("SELECT * FROM boq_documents WHERE requirementId = :rid LIMIT 1")
    suspend fun findByRequirement(rid: Long): BoqDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(d: BoqDocument): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<BoqLine>)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY id DESC")
    fun observeAll(): Flow<List<Link>>

    @Query("SELECT * FROM links")
    suspend fun getAll(): List<Link>

    @Query("SELECT * FROM links WHERE id = :id")
    suspend fun getById(id: Long): Link?

    @Query("SELECT * FROM links WHERE sourceSiteId = :siteId OR targetSiteId = :siteId")
    fun observeBySite(siteId: Long): Flow<List<Link>>

    @Query("SELECT COUNT(*) FROM links WHERE status = 'ACTIVE' AND (sourceSiteId = :siteId OR targetSiteId = :siteId)")
    suspend fun countActiveBySite(siteId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(l: Link): Long

    @Update
    suspend fun update(l: Link)

    @Delete
    suspend fun delete(l: Link)
}

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<Ticket>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: Long): Ticket?

    @Query("SELECT * FROM tickets WHERE siteId = :siteId ORDER BY openedAt DESC")
    fun observeBySite(siteId: Long): Flow<List<Ticket>>

    @Query("SELECT COUNT(*) FROM tickets WHERE status != 'CLOSED'")
    suspend fun countOpen(): Int

    @Query("SELECT * FROM tickets")
    suspend fun getAll(): List<Ticket>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Ticket): Long

    @Update
    suspend fun update(t: Ticket)

    @Delete
    suspend fun delete(t: Ticket)
}

@Dao
interface WorkOrderDao {
    @Query("SELECT * FROM work_orders ORDER BY scheduledAt DESC")
    fun observeAll(): Flow<List<WorkOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(w: WorkOrder): Long

    @Update
    suspend fun update(w: WorkOrder)
}

@Dao
interface MaintenanceScheduleDao {
    @Query("SELECT * FROM maintenance_schedules ORDER BY nextDue ASC")
    fun observeAll(): Flow<List<MaintenanceSchedule>>

    @Query("SELECT * FROM maintenance_schedules")
    suspend fun getAll(): List<MaintenanceSchedule>

    @Query("SELECT * FROM maintenance_schedules WHERE nextDue <= :cutoff")
    suspend fun getDueBefore(cutoff: Long): List<MaintenanceSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MaintenanceSchedule): Long

    @Update
    suspend fun update(m: MaintenanceSchedule)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Alert>>

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM alerts WHERE type = :type AND refId = :refId AND isRead = 0")
    suspend fun countUnreadByRef(type: String, refId: Long): Int

    @Insert
    suspend fun insert(a: Alert)

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 500")
    fun observeRecent(): Flow<List<AuditLog>>

    @Insert
    suspend fun insert(l: AuditLog)

    @Query("DELETE FROM audit_log")
    suspend fun clear()
}
