package com.majarra.galaxy.data.repository

import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.AttachmentDao
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.CategoryDao
import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.data.local.EmergencyVisitDao
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.MaintenanceLogDao
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.data.local.MaterialDao
import com.majarra.galaxy.data.local.MaterialRequest
import com.majarra.galaxy.data.local.MaterialRequestDao
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDao
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.SiteDetailDao
import com.majarra.galaxy.data.local.SiteLink
import com.majarra.galaxy.data.local.SiteLinkDao
import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.data.local.WithdrawalDao
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.MaterialRequestRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteLinkRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/* ============================================================
 * تطبيقات المستودعات — تفويض مباشر إلى DAOs (لا منطق إضافي؛
 * منطق العمل في حالات الاستخدام).
 * ============================================================ */

@Singleton
class SiteRepositoryImpl @Inject constructor(
    private val dao: SiteDao
) : SiteRepository {
    override fun observeSites(): Flow<List<Site>> = dao.observeAll()
    override fun observeArchivedSites(): Flow<List<Site>> = dao.observeArchived()
    override fun observeByCategory(categoryId: Long): Flow<List<Site>> = dao.observeByCategory(categoryId)
    override fun searchSites(q: String): Flow<List<Site>> = dao.search(q)
    override fun observeSite(id: Long): Flow<Site?> = dao.observeById(id)
    override suspend fun getSite(id: Long): Site? = dao.getById(id)
    override suspend fun getAll(): List<Site> = dao.getAll()
    override suspend fun countActive(): Int = dao.countActive()
    override suspend fun countArchived(): Int = dao.countArchived()
    override suspend fun insert(site: Site): Long = dao.insert(site)
    override suspend fun update(site: Site) = dao.update(site)
    override suspend fun delete(site: Site) = dao.delete(site)
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = dao.observeAll()
    override suspend fun getAll(): List<Category> = dao.getAll()
    override suspend fun insert(category: Category): Long = dao.insert(category)
    override suspend fun update(category: Category) = dao.update(category)
    override suspend fun delete(category: Category) = dao.delete(category)
}

@Singleton
class SiteDetailRepositoryImpl @Inject constructor(
    private val dao: SiteDetailDao
) : SiteDetailRepository {
    override fun observeBySite(siteId: Long): Flow<SiteDetail?> = dao.observeBySite(siteId)
    override suspend fun getBySite(siteId: Long): SiteDetail? = dao.getBySite(siteId)
    override suspend fun getAll(): List<SiteDetail> = dao.getAll()
    override suspend fun upsert(detail: SiteDetail): Long = dao.upsert(detail)
}

@Singleton
class MaintenanceLogRepositoryImpl @Inject constructor(
    private val dao: MaintenanceLogDao
) : MaintenanceLogRepository {
    override fun observeBySite(siteId: Long): Flow<List<MaintenanceLog>> = dao.observeBySite(siteId)
    override suspend fun countAll(): Int = dao.countAll()
    override suspend fun getAll(): List<MaintenanceLog> = dao.getAll()
    override suspend fun insert(log: MaintenanceLog): Long = dao.insert(log)
    override suspend fun delete(log: MaintenanceLog) = dao.delete(log)
}

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val dao: AttachmentDao
) : AttachmentRepository {
    override fun observeBySite(siteId: Long): Flow<List<Attachment>> = dao.observeBySite(siteId)
    override suspend fun getBySite(siteId: Long): List<Attachment> = dao.getBySite(siteId)
    override suspend fun countAll(): Int = dao.countAll()
    override suspend fun countByType(typeName: String): Int = dao.countByType(typeName)
    override suspend fun insert(a: Attachment): Long = dao.insert(a)
    override suspend fun delete(a: Attachment) = dao.delete(a)
}

@Singleton
class MaterialRepositoryImpl @Inject constructor(
    private val dao: MaterialDao
) : MaterialRepository {
    override fun observeAll(): Flow<List<Material>> = dao.observeAll()
    override suspend fun getAll(): List<Material> = dao.getAll()
    override suspend fun insert(material: Material): Long = dao.insert(material)
    override suspend fun update(material: Material) = dao.update(material)
    override suspend fun delete(material: Material) = dao.delete(material)
}

@Singleton
class WithdrawalRepositoryImpl @Inject constructor(
    private val dao: WithdrawalDao
) : WithdrawalRepository {
    override fun observeBySite(siteId: Long): Flow<List<Withdrawal>> = dao.observeBySite(siteId)
    override suspend fun countOpen(): Int = dao.countOpen()
    override suspend fun insert(withdrawal: Withdrawal): Long = dao.insert(withdrawal)
    override suspend fun update(withdrawal: Withdrawal) = dao.update(withdrawal)
    override suspend fun delete(withdrawal: Withdrawal) = dao.delete(withdrawal)
}

@Singleton
class EmergencyVisitRepositoryImpl @Inject constructor(
    private val dao: EmergencyVisitDao
) : EmergencyVisitRepository {
    override fun observeBySite(siteId: Long): Flow<List<EmergencyVisit>> =
        dao.observeBySite(siteId)
    override suspend fun insert(visit: EmergencyVisit): Long = dao.insert(visit)
}

@Singleton
class MaterialRequestRepositoryImpl @Inject constructor(
    private val dao: MaterialRequestDao
) : MaterialRequestRepository {
    override fun observeBySite(siteId: Long): Flow<List<MaterialRequest>> =
        dao.observeBySite(siteId)
    override suspend fun getBySite(siteId: Long): List<MaterialRequest> = dao.getBySite(siteId)
    override suspend fun insert(request: MaterialRequest): Long = dao.insert(request)
    override suspend fun update(request: MaterialRequest) = dao.update(request)
    override suspend fun delete(request: MaterialRequest) = dao.delete(request)
}

@Singleton
class SiteLinkRepositoryImpl @Inject constructor(
    private val dao: SiteLinkDao
) : SiteLinkRepository {
    override fun observeAll(): Flow<List<SiteLink>> = dao.observeAll()
    override suspend fun insert(link: SiteLink): Long = dao.insert(link)
    override suspend fun deleteBetween(a: Long, b: Long) = dao.deleteBetween(a, b)
}
