package com.majarra.galaxy.data.repository

import android.content.Context
import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.AlertDao
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.AttachmentDao
import com.majarra.galaxy.data.local.AuditLog
import com.majarra.galaxy.data.local.AuditLogDao
import com.majarra.galaxy.data.local.BoqDao
import com.majarra.galaxy.data.local.BoqDocument
import com.majarra.galaxy.data.local.BoqLine
import com.majarra.galaxy.data.local.Equipment
import com.majarra.galaxy.data.local.EquipmentDao
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.data.local.InventoryItem
import com.majarra.galaxy.data.local.InventoryItemDao
import com.majarra.galaxy.data.local.Link
import com.majarra.galaxy.data.local.LinkDao
import com.majarra.galaxy.data.local.MaintenanceSchedule
import com.majarra.galaxy.data.local.MaintenanceScheduleDao
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.RequirementDao
import com.majarra.galaxy.data.local.RequirementItem
import com.majarra.galaxy.data.local.RequirementItemDao
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDao
import com.majarra.galaxy.data.local.SiteHistory
import com.majarra.galaxy.data.local.SiteHistoryDao
import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.TicketDao
import com.majarra.galaxy.data.local.WorkOrder
import com.majarra.galaxy.data.local.WorkOrderDao
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.BoqRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.MaintenanceRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.repository.SiteHistoryRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.domain.repository.WorkOrderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/* ============================================================
 * تطبيقات المستودعات — تفويض مباشر إلى DAOs مع لمسات العمل.
 * ============================================================ */

@Singleton
class SiteRepositoryImpl @Inject constructor(
    private val dao: SiteDao
) : SiteRepository {
    override fun observeSites(): Flow<List<Site>> = dao.observeAll()
    override suspend fun findByCode(code: String): Site? = dao.findByCode(code)
    override suspend fun findByCodeExcept(code: String, excludeId: Long): Site? =
        dao.findByCodeExcept(code, excludeId)
    override fun searchSites(q: String): Flow<List<Site>> = dao.search(q)
    override fun observeSite(id: Long): Flow<Site?> = dao.observeById(id)
    override suspend fun getSite(id: Long): Site? = dao.getById(id)
    override suspend fun insert(site: Site): Long = dao.insert(site)
    override suspend fun update(site: Site) = dao.update(site)
    override suspend fun delete(site: Site) = dao.delete(site)
    override suspend fun count(): Int = dao.countSync()
}

@Singleton
class EquipmentRepositoryImpl @Inject constructor(
    private val dao: EquipmentDao
) : EquipmentRepository {
    override fun observeBySite(siteId: Long): Flow<List<Equipment>> = dao.observeBySite(siteId)
    override fun observeAll(): Flow<List<Equipment>> = dao.observeAll()
    override suspend fun getBySite(siteId: Long): List<Equipment> = dao.getBySite(siteId)
    override suspend fun getAll(): List<Equipment> = dao.getAll()
    override suspend fun countBySite(siteId: Long): Int = dao.countBySite(siteId)
    override suspend fun insert(e: Equipment): Long = dao.insert(e)
    override suspend fun update(e: Equipment) = dao.update(e)
    override suspend fun delete(e: Equipment) = dao.delete(e)
}

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val dao: AttachmentDao
) : AttachmentRepository {
    override fun observeBySite(siteId: Long): Flow<List<Attachment>> = dao.observeBySite(siteId)
    override suspend fun getBySite(siteId: Long): List<Attachment> = dao.getBySite(siteId)
    override suspend fun insert(a: Attachment): Long = dao.insert(a)
    override suspend fun delete(a: Attachment) = dao.delete(a)
}

@Singleton
class SiteHistoryRepositoryImpl @Inject constructor(
    private val dao: SiteHistoryDao
) : SiteHistoryRepository {
    override fun observeBySite(siteId: Long): Flow<List<SiteHistory>> = dao.observeBySite(siteId)
    override suspend fun record(siteId: Long, action: String, oldValue: String, newValue: String) {
        dao.insert(SiteHistory(siteId = siteId, action = action, oldValue = oldValue, newValue = newValue))
    }
}

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val dao: InventoryItemDao
) : InventoryRepository {
    override fun observeAll(): Flow<List<InventoryItem>> = dao.observeAll()
    override suspend fun getAll(): List<InventoryItem> = dao.getAll()
    override suspend fun getBelowThreshold(): List<InventoryItem> = dao.getBelowThreshold()
    override suspend fun getById(id: Long): InventoryItem? = dao.getById(id)
    override suspend fun count(): Int = dao.countSync()
    override suspend fun insert(i: InventoryItem): Long = dao.insert(i)
    override suspend fun update(i: InventoryItem) = dao.update(i)
    override suspend fun delete(i: InventoryItem) = dao.delete(i)
}

@Singleton
class RequirementRepositoryImpl @Inject constructor(
    private val dao: RequirementDao,
    private val itemDao: RequirementItemDao
) : RequirementRepository {
    override fun observeAll(): Flow<List<Requirement>> = dao.observeAll()
    override fun observeItems(requirementId: Long): Flow<List<RequirementItem>> =
        itemDao.observeByRequirement(requirementId)
    override suspend fun get(id: Long): Requirement? = dao.getById(id)
    override suspend fun getItems(requirementId: Long): List<RequirementItem> =
        itemDao.getByRequirement(requirementId)
    override suspend fun insert(r: Requirement): Long = dao.insert(r)
    override suspend fun update(r: Requirement) = dao.update(r)
    override suspend fun delete(r: Requirement) = dao.delete(r)
    override suspend fun insertItems(items: List<RequirementItem>) = itemDao.insertAll(items)
    override suspend fun updateItem(item: RequirementItem) = itemDao.update(item)
}

@Singleton
class BoqRepositoryImpl @Inject constructor(
    private val dao: BoqDao
) : BoqRepository {
    override fun observeDocuments(): Flow<List<BoqDocument>> = dao.observeDocuments()
    override fun observeLines(boqId: Long): Flow<List<BoqLine>> = dao.observeLines(boqId)
    override suspend fun findByRequirement(rid: Long): BoqDocument? = dao.findByRequirement(rid)
    override suspend fun insertDocument(d: BoqDocument): Long = dao.insertDocument(d)
    override suspend fun insertLines(lines: List<BoqLine>) = dao.insertLines(lines)
}

@Singleton
class LinkRepositoryImpl @Inject constructor(
    private val dao: LinkDao
) : LinkRepository {
    override fun observeAll(): Flow<List<Link>> = dao.observeAll()
    override suspend fun getAll(): List<Link> = dao.getAll()
    override suspend fun getById(id: Long): Link? = dao.getById(id)
    override fun observeBySite(siteId: Long): Flow<List<Link>> = dao.observeBySite(siteId)
    override suspend fun countActiveBySite(siteId: Long): Int = dao.countActiveBySite(siteId)
    override suspend fun countBetween(a: Long, b: Long): Int = dao.countBetween(a, b)
    override suspend fun insert(l: Link): Long = dao.insert(l)
    override suspend fun update(l: Link) = dao.update(l)
    override suspend fun delete(l: Link) = dao.delete(l)
}

@Singleton
class TicketRepositoryImpl @Inject constructor(
    private val dao: TicketDao
) : TicketRepository {
    override fun observeAll(): Flow<List<Ticket>> = dao.observeAll()
    override fun observeBySite(siteId: Long): Flow<List<Ticket>> = dao.observeBySite(siteId)
    override suspend fun getById(id: Long): Ticket? = dao.getById(id)
    override suspend fun getAll(): List<Ticket> = dao.getAll()
    override suspend fun countOpen(): Int = dao.countOpen()
    override suspend fun insert(t: Ticket): Long = dao.insert(t)
    override suspend fun update(t: Ticket) = dao.update(t)
}

@Singleton
class WorkOrderRepositoryImpl @Inject constructor(
    private val dao: WorkOrderDao
) : WorkOrderRepository {
    override fun observeAll(): Flow<List<WorkOrder>> = dao.observeAll()
    override fun observeBySite(siteId: Long): Flow<List<WorkOrder>> = dao.observeBySite(siteId)
    override suspend fun getAll(): List<WorkOrder> = dao.getAll()
    override suspend fun insert(w: WorkOrder): Long = dao.insert(w)
    override suspend fun update(w: WorkOrder) = dao.update(w)
    override suspend fun delete(w: WorkOrder) = dao.delete(w)
}

@Singleton
class MaintenanceRepositoryImpl @Inject constructor(
    private val dao: MaintenanceScheduleDao
) : MaintenanceRepository {
    override fun observeAll(): Flow<List<MaintenanceSchedule>> = dao.observeAll()
    override suspend fun getAll(): List<MaintenanceSchedule> = dao.getAll()
    override suspend fun getDueBefore(cutoff: Long): List<MaintenanceSchedule> = dao.getDueBefore(cutoff)
    override suspend fun insert(m: MaintenanceSchedule): Long = dao.insert(m)
    override suspend fun update(m: MaintenanceSchedule) = dao.update(m)
}

@Singleton
class AlertRepositoryImpl @Inject constructor(
    private val dao: AlertDao
) : AlertRepository {
    override fun observeAll(): Flow<List<Alert>> = dao.observeAll()
    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()
    override suspend fun hasUnreadFor(type: String, refId: Long): Boolean =
        dao.countUnreadByRef(type, refId) > 0
    override suspend fun insert(a: Alert) = dao.insert(a)
    override suspend fun markRead(id: Long) = dao.markRead(id)
    override suspend fun delete(id: Long) = dao.deleteById(id)
    override suspend fun countAll(): Int = dao.countAll()
    override suspend fun markAllRead() = dao.markAllRead()
    override suspend fun deleteAll() = dao.deleteAll()
}

@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val dao: AuditLogDao
) : AuditRepository {
    override fun observeRecent(): Flow<List<AuditLog>> = dao.observeRecent()
    override suspend fun log(action: String, entityType: String, entityId: Long?, details: String) {
        dao.insert(
            AuditLog(
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }
    override suspend fun clear() = dao.clear()
}

