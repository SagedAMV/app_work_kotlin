package com.majarra.galaxy.domain.usecase

import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.SiteHistoryRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** مراقبة قائمة المواقع أو البحث فيها */
class ObserveSitesUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    operator fun invoke(): Flow<List<Site>> = siteRepo.observeSites()
    fun search(q: String): Flow<List<Site>> =
        if (q.isBlank()) siteRepo.observeSites() else siteRepo.searchSites(q.trim())
}

/** مراقبة موقع واحد */
class ObserveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository
) {
    operator fun invoke(id: Long): Flow<Site?> = siteRepo.observeSite(id)
}

/** حفظ موقع (إضافة أو تعديل) مع تسجيل في السجل التاريخي وسجل التدقيق */
class SaveSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val historyRepo: SiteHistoryRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(site: Site): Long {
        return if (site.id == 0L) {
            val id = siteRepo.insert(site)
            historyRepo.record(id, "إضافة موقع", "", site.name)
            auditRepo.log("CREATE", "Site", id, site.name)
            id
        } else {
            val old = siteRepo.getSite(site.id)
            siteRepo.update(site.copy(updatedAt = System.currentTimeMillis()))
            historyRepo.record(site.id, "تعديل موقع", old?.name.orEmpty(), site.name)
            auditRepo.log("UPDATE", "Site", site.id, site.name)
            site.id
        }
    }
}

/** نتيجة محاولة حذف موقع */
sealed class DeleteSiteResult {
    object Deleted : DeleteSiteResult()
    data class Blocked(val equipmentCount: Int, val activeLinks: Int) : DeleteSiteResult()
}

/**
 * حذف موقع — قاعدة العمل رقم 1:
 * لا يمكن حذف موقع يحتوي معدات أو روابط نشطة.
 */
class DeleteSiteUseCase @Inject constructor(
    private val siteRepo: SiteRepository,
    private val equipmentRepo: EquipmentRepository,
    private val linkRepo: LinkRepository,
    private val auditRepo: AuditRepository
) {
    suspend operator fun invoke(site: Site): DeleteSiteResult {
        val equipmentCount = equipmentRepo.countBySite(site.id)
        val activeLinks = linkRepo.countActiveBySite(site.id)
        if (equipmentCount > 0 || activeLinks > 0) {
            return DeleteSiteResult.Blocked(equipmentCount, activeLinks)
        }
        siteRepo.delete(site)
        auditRepo.log("DELETE", "Site", site.id, site.name)
        return DeleteSiteResult.Deleted
    }
}
