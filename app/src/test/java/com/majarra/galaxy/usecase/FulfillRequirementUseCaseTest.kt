package com.majarra.galaxy.usecase

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.BoqDocument
import com.majarra.galaxy.data.local.BoqLine
import com.majarra.galaxy.data.local.InventoryItem
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.RequirementItem
import com.majarra.galaxy.domain.model.AlertType
import com.majarra.galaxy.domain.model.RequirementStatus
import com.majarra.galaxy.domain.model.RequirementType
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.usecase.FulfillRequirementUseCase
import com.majarra.galaxy.domain.usecase.FulfillResult
import com.majarra.galaxy.data.local.AuditLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** اختبارات وحدة لحالة استخدام صرف الاحتياج — قاعدة العمل رقم 3 */
class FulfillRequirementUseCaseTest {

    /* ── مستودعات وهمية ── */

    private class FakeInventory : InventoryRepository {
        val items = mutableMapOf<Long, InventoryItem>()
        private var nextId = 1L
        override fun observeAll(): Flow<List<InventoryItem>> = flowOf(items.values.toList())
        override suspend fun getAll(): List<InventoryItem> = items.values.toList()
        override suspend fun getById(id: Long): InventoryItem? = items[id]
        override suspend fun count(): Int = items.size
        override suspend fun insert(i: InventoryItem): Long {
            val saved = if (i.id == 0L) i.copy(id = nextId++) else i
            items[saved.id] = saved
            return saved.id
        }
        override suspend fun update(i: InventoryItem) { items[i.id] = i }
        override suspend fun delete(i: InventoryItem) { items.remove(i.id) }
    }

    private class FakeRequirements : RequirementRepository {
        val requirements = mutableMapOf<Long, Requirement>()
        val items = mutableListOf<RequirementItem>()
        private var nextId = 1L
        override fun observeAll(): Flow<List<Requirement>> = flowOf(requirements.values.toList())
        override fun observeItems(requirementId: Long): Flow<List<RequirementItem>> =
            flowOf(items.filter { it.requirementId == requirementId })
        override suspend fun get(id: Long): Requirement? = requirements[id]
        override suspend fun getItems(requirementId: Long): List<RequirementItem> =
            items.filter { it.requirementId == requirementId }
        override suspend fun insert(r: Requirement): Long {
            val saved = if (r.id == 0L) r.copy(id = nextId++) else r
            requirements[saved.id] = saved
            return saved.id
        }
        override suspend fun update(r: Requirement) { requirements[r.id] = r }
        override suspend fun delete(r: Requirement) { requirements.remove(r.id) }
        override suspend fun insertItems(newItems: List<RequirementItem>) { items += newItems }
        override suspend fun updateItem(item: RequirementItem) {
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) items[idx] = item
        }
    }

    private class FakeAlerts : AlertRepository {
        val inserted = mutableListOf<Alert>()
        override fun observeAll(): Flow<List<Alert>> = flowOf(inserted)
        override fun observeUnreadCount(): Flow<Int> = flowOf(inserted.count { !it.isRead })
        override suspend fun hasUnreadFor(type: String, refId: Long): Boolean =
            inserted.any { it.type.name == type && it.refId == refId && !it.isRead }
        override suspend fun insert(a: Alert) { inserted += a }
        override suspend fun markRead(id: Long) {}
        override suspend fun markAllRead() {}
        override suspend fun deleteAll() {}
    }

    private class FakeAudit : AuditRepository {
        val logs = mutableListOf<AuditLog>()
        override fun observeRecent(): Flow<List<AuditLog>> = flowOf(logs)
        override suspend fun log(action: String, entityType: String, entityId: Long?, details: String) {
            logs += AuditLog(action = action, entityType = entityType, entityId = entityId, details = details)
        }
        override suspend fun clear() {}
    }

    /* ── تجهيز مشترك ── */

    private fun fixture(stockQuantity: Int, needed: Int): Triple<FulfillRequirementUseCase, Triple<FakeInventory, FakeRequirements, FakeAlerts>, Pair<Long, Long>> {
        val inventory = FakeInventory()
        val requirements = FakeRequirements()
        val alerts = FakeAlerts()
        val audit = FakeAudit()

        val invId = kotlinx.coroutines.runBlocking {
            inventory.insert(InventoryItem(name = "كابل", quantity = stockQuantity, minThreshold = 2))
        }
        val reqId = kotlinx.coroutines.runBlocking {
            requirements.insert(
                Requirement(siteId = 1, type = RequirementType.MATERIALS, status = RequirementStatus.APPROVED)
            )
        }
        kotlinx.coroutines.runBlocking {
            requirements.insertItems(
                listOf(
                    RequirementItem(id = 100, requirementId = reqId, inventoryItemId = invId, description = "كابل", quantity = needed)
                )
            )
        }

        val useCase = FulfillRequirementUseCase(requirements, inventory, audit, alerts)
        return useCase to Triple(inventory, requirements, alerts) to (reqId to invId)
    }

    @Test
    fun `الصرف يخصم المخزون ويسجل في التدقيق`() = runTest {
        val (useCase, repos, ids) = fixture(stockQuantity = 10, needed = 4)
        val (inventory, requirements, _) = repos
        val (reqId, invId) = ids

        val result = useCase(reqId)

        assertTrue(result is FulfillResult.Success)
        assertEquals(6, inventory.items[invId]?.quantity)
        assertEquals(RequirementStatus.FULFILLED, requirements.requirements[reqId]?.status)
        assertTrue(requirements.items.first().fulfilled)
    }

    @Test
    fun `المخزون غير الكافي يمنع الصرف بالكامل`() = runTest {
        val (useCase, repos, ids) = fixture(stockQuantity = 2, needed = 5)
        val (inventory, requirements, _) = repos
        val (reqId, invId) = ids

        val result = useCase(reqId)

        assertTrue(result is FulfillResult.Failure)
        // لا خصم جزئي أبدًا
        assertEquals(2, inventory.items[invId]?.quantity)
        assertEquals(RequirementStatus.APPROVED, requirements.requirements[reqId]?.status)
    }

    @Test
    fun `الوصول للحد الأدنى ينشئ تنبيه نقص`() = runTest {
        val (useCase, repos, ids) = fixture(stockQuantity = 4, needed = 2)
        val (_, _, alerts) = repos
        val (reqId, invId) = ids

        useCase(reqId)

        // المتبقي = 2 = الحد الأدنى → يجب إنشاء تنبيه
        assertTrue(alerts.inserted.any { it.type == AlertType.LOW_STOCK && it.refId == invId })
    }
}
