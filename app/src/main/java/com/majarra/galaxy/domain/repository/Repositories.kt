package com.majarra.galaxy.domain.repository

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.AuditLog
import com.majarra.galaxy.data.local.BoqDocument
import com.majarra.galaxy.data.local.BoqLine
import com.majarra.galaxy.data.local.Equipment
import com.majarra.galaxy.data.local.InventoryItem
import com.majarra.galaxy.data.local.Link
import com.majarra.galaxy.data.local.MaintenanceSchedule
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.RequirementItem
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteHistory
import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.WorkOrder
import kotlinx.coroutines.flow.Flow

/* ============================================================
 * واجهات المستودعات — طبقة الـ Domain لا تعرف تفاصيل Room.
 * ============================================================ */

interface SiteRepository {
    fun observeSites(): Flow<List<Site>>
    suspend fun findByCode(code: String): Site?
    suspend fun findByCodeExcept(code: String, excludeId: Long): Site?
    fun searchSites(q: String): Flow<List<Site>>
    fun observeSite(id: Long): Flow<Site?>
    suspend fun getSite(id: Long): Site?
    suspend fun insert(site: Site): Long
    suspend fun update(site: Site)
    suspend fun delete(site: Site)
    suspend fun count(): Int
}

interface EquipmentRepository {
    fun observeBySite(siteId: Long): Flow<List<Equipment>>
    fun observeAll(): Flow<List<Equipment>>
    suspend fun getBySite(siteId: Long): List<Equipment>
    suspend fun getAll(): List<Equipment>
    suspend fun countBySite(siteId: Long): Int
    suspend fun insert(e: Equipment): Long
    suspend fun update(e: Equipment)
    suspend fun delete(e: Equipment)
}

interface AttachmentRepository {
    fun observeBySite(siteId: Long): Flow<List<Attachment>>
    suspend fun getBySite(siteId: Long): List<Attachment>
    suspend fun insert(a: Attachment): Long
    suspend fun delete(a: Attachment)
}

interface SiteHistoryRepository {
    fun observeBySite(siteId: Long): Flow<List<SiteHistory>>
    suspend fun record(siteId: Long, action: String, oldValue: String = "", newValue: String = "")
}

interface InventoryRepository {
    fun observeAll(): Flow<List<InventoryItem>>
    suspend fun getAll(): List<InventoryItem>
    suspend fun getBelowThreshold(): List<InventoryItem>
    suspend fun getById(id: Long): InventoryItem?
    suspend fun count(): Int
    suspend fun insert(i: InventoryItem): Long
    suspend fun update(i: InventoryItem)
    suspend fun delete(i: InventoryItem)
}

interface RequirementRepository {
    fun observeAll(): Flow<List<Requirement>>
    fun observeItems(requirementId: Long): Flow<List<RequirementItem>>
    suspend fun get(id: Long): Requirement?
    suspend fun getItems(requirementId: Long): List<RequirementItem>
    suspend fun insert(r: Requirement): Long
    suspend fun update(r: Requirement)
    suspend fun delete(r: Requirement)
    suspend fun insertItems(items: List<RequirementItem>)
    suspend fun updateItem(item: RequirementItem)
}

interface BoqRepository {
    fun observeDocuments(): Flow<List<BoqDocument>>
    fun observeLines(boqId: Long): Flow<List<BoqLine>>
    suspend fun findByRequirement(rid: Long): BoqDocument?
    suspend fun insertDocument(d: BoqDocument): Long
    suspend fun insertLines(lines: List<BoqLine>)
}

interface LinkRepository {
    fun observeAll(): Flow<List<Link>>
    suspend fun getAll(): List<Link>
    suspend fun getById(id: Long): Link?
    fun observeBySite(siteId: Long): Flow<List<Link>>
    suspend fun countActiveBySite(siteId: Long): Int
    suspend fun countBetween(a: Long, b: Long): Int
    suspend fun insert(l: Link): Long
    suspend fun update(l: Link)
    suspend fun delete(l: Link)
}

interface TicketRepository {
    fun observeAll(): Flow<List<Ticket>>
    fun observeBySite(siteId: Long): Flow<List<Ticket>>
    suspend fun getById(id: Long): Ticket?
    suspend fun getAll(): List<Ticket>
    suspend fun countOpen(): Int
    suspend fun insert(t: Ticket): Long
    suspend fun update(t: Ticket)
}

interface WorkOrderRepository {
    fun observeAll(): Flow<List<WorkOrder>>
    fun observeBySite(siteId: Long): Flow<List<WorkOrder>>
    suspend fun getAll(): List<WorkOrder>
    suspend fun insert(w: WorkOrder): Long
    suspend fun update(w: WorkOrder)
    suspend fun delete(w: WorkOrder)
}

interface MaintenanceRepository {
    fun observeAll(): Flow<List<MaintenanceSchedule>>
    suspend fun getAll(): List<MaintenanceSchedule>
    suspend fun getDueBefore(cutoff: Long): List<MaintenanceSchedule>
    suspend fun insert(m: MaintenanceSchedule): Long
    suspend fun update(m: MaintenanceSchedule)
}

interface AlertRepository {
    fun observeAll(): Flow<List<Alert>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun hasUnreadFor(type: String, refId: Long): Boolean
    suspend fun insert(a: Alert)
    suspend fun markRead(id: Long)
    suspend fun delete(id: Long)
    suspend fun countAll(): Int
    suspend fun markAllRead()
    suspend fun deleteAll()
}

interface AuditRepository {
    fun observeRecent(): Flow<List<AuditLog>>
    suspend fun log(action: String, entityType: String, entityId: Long?, details: String = "")
    suspend fun clear()
}

/** تفضيلات المستخدم: الوضع الليلي والقفل البيومتري */
interface SettingsRepository {
    data class Prefs(val darkMode: Boolean = true, val biometricLock: Boolean = false)

    val preferences: Flow<Prefs>

    /**
     * القيمة الحالية المخزَّنة فعليًا (قراءة متزامنة).
     * تُستخدم كقيمة ابتدائية للواجهة، لأن الاعتماد على `Prefs()` افتراضيًا كان
     * يجعل «القفل البيومتري = false» في اللحظة الأولى حتى لو كان مفعّلًا،
     * أي تجاوز صامت للقفل.
     */
    val current: Prefs
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setBiometricLock(enabled: Boolean)
}
