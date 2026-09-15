package com.majarra.galaxy.data.repository

import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.AttachmentDao
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.MaintenanceLogDao
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDao
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.SiteDetailDao
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/* ============================================================
 * تطبيقات المستودعات — تفويض مباشر إلى DAOs (لا منطق إضافي
 * بعد التبسيط؛ منطق العمل انتقل إلى حالات الاستخدام).
 * ============================================================ */

@Singleton
class SiteRepositoryImpl @Inject constructor(
    private val dao: SiteDao
) : SiteRepository {
    override fun observeSites(): Flow<List<Site>> = dao.observeAll()
    override fun searchSites(q: String): Flow<List<Site>> = dao.search(q)
    override fun observeSite(id: Long): Flow<Site?> = dao.observeById(id)
    override suspend fun getSite(id: Long): Site? = dao.getById(id)
    override suspend fun getAll(): List<Site> = dao.getAll()
    override suspend fun insert(site: Site): Long = dao.insert(site)
    override suspend fun update(site: Site) = dao.update(site)
    override suspend fun delete(site: Site) = dao.delete(site)
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
    override suspend fun insert(log: MaintenanceLog): Long = dao.insert(log)
    override suspend fun update(log: MaintenanceLog) = dao.update(log)
    override suspend fun delete(log: MaintenanceLog) = dao.delete(log)
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
