package com.majarra.galaxy.usecase

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.AuditLog
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
import com.majarra.galaxy.domain.repository.TransactionRunner
import com.majarra.galaxy.domain.usecase.FulfillRequirementUseCase
import com.majarra.galaxy.domain.usecase.FulfillResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        override suspend fun getBelowThreshold(): List<InventoryItem> =
            items.values.filter { it.quantity <= it.minThreshold }
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
        override suspend fun delete(r: Requirement) {
            requirements.remove(r.id)
            items.removeAll { it.requirementId == r.id }
        }
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
        override suspend fun delete(id: Long) { inserted.removeAll { it.id == id } }
        override suspend fun countAll(): Int = inserted.size
        override suspend fun deleteAll() { inserted.clear() }
    }

    private class FakeAudit : AuditRepository {
        val logs = mutableListOf<AuditLog>()
        override fun observeRecent(): Flow<List<AuditLog>> = flowOf(logs)
        override suspend fun log(action: String, entityType: String, entityId: Long?, details: String) {
            logs += AuditLog(action = action, entityType = entityType, entityId = entityId, details = details)
        }
        override suspend fun clear() { logs.clear() }
    }

    /** منفّذ معاملة وهمي — نتحقق أن العمليات الكتابية تُغلَّف فعلًا بمعاملة */
    private class FakeTxRunner : TransactionRunner {
        var started = 0
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            started++
            return block()
        }
    }

    private data class Fixture(
        val useCase: FulfillRequirementUseCase,
        val inventory: FakeInventory,
        val requirements: FakeRequirements,
        val alerts: FakeAlerts,
        val audit: FakeAudit,
        val tx: FakeTxRunner,
        val reqId: Long,
        val invId: Long
    )

    private suspend fun fixture(
        stockQuantity: Int,
        needed: Int,
        status: RequirementStatus = RequirementStatus.APPROVED,
        minThreshold: Int = 2
    ): Fixture {
        val inventory = FakeInventory()
        val requirements = FakeRequirements()
        val alerts = FakeAlerts()
        val audit = FakeAudit()
        val tx = FakeTxRunner()

        val invId = inventory.insert(
            InventoryItem(name = "كابل", quantity = stockQuantity, minThreshold = minThreshold)
        )
        val reqId = requirements.insert(
            Requirement(siteId = 1, type = RequirementType.MATERIALS, status = status)
        )
        requirements.insertItems(
            listOf(
                RequirementItem(
                    id = 100,
                    requirementId = reqId,
                    inventoryItemId = invId,
                    description = "كابل",
                    quantity = needed
                )
            )
        )
        return Fixture(
            FulfillRequirementUseCase(requirements, inventory, audit, alerts, tx),
            inventory, requirements, alerts, audit, tx, reqId, invId
        )
    }

    @Test
    fun `الصرف يخصم المخزون ويسجل في التدقيق داخل معاملة`() = runTest {
        val f = fixture(stockQuantity = 10, needed = 4)

        val result = f.useCase(f.reqId)

        assertTrue(result is FulfillResult.Success)
        assertEquals(6, f.inventory.items[f.invId]?.quantity)
        assertEquals(RequirementStatus.FULFILLED, f.requirements.requirements[f.reqId]?.status)
        assertTrue(f.requirements.items.first().fulfilled)
        assertEquals(1, f.tx.started)
        assertTrue(f.audit.logs.any { it.action == "DISPATCH" })
    }

    @Test
    fun `المخزون غير الكافي يمنع الصرف بالكامل`() = runTest {
        val f = fixture(stockQuantity = 2, needed = 5)

        val result = f.useCase(f.reqId)

        assertTrue(result is FulfillResult.Failure)
        // لا خصم جزئي أبدًا
        assertEquals(2, f.inventory.items[f.invId]?.quantity)
        assertEquals(RequirementStatus.APPROVED, f.requirements.requirements[f.reqId]?.status)
        assertFalse(f.requirements.items.first().fulfilled)
        // لم تُفتح معاملة إطلاقًا لأن الرفض حدث قبل أي كتابة
        assertEquals(0, f.tx.started)
    }

    @Test
    fun `وصول المخزون للحد الأدنى ينشئ تنبيهًا واحدًا فقط`() = runTest {
        val f = fixture(stockQuantity = 5, needed = 3, minThreshold = 2)

        f.useCase(f.reqId)
        assertEquals(1, f.alerts.inserted.size)
        assertEquals(AlertType.LOW_STOCK.name, f.alerts.inserted.first().type.name)

        // إعادة الصرف ممنوعة، فالتنبيه لا يتكرر
        assertTrue(f.useCase(f.reqId) is FulfillResult.Failure)
        assertEquals(1, f.alerts.inserted.size)
    }

    @Test
    fun `لا يُصرف احتياج ملغي`() = runTest {
        val f = fixture(stockQuantity = 10, needed = 1, status = RequirementStatus.CANCELLED)

        assertTrue(f.useCase(f.reqId) is FulfillResult.Failure)
        assertEquals(10, f.inventory.items[f.invId]?.quantity)
        assertEquals(0, f.tx.started)
    }

    @Test
    fun `لا يُصرف احتياج مصروف مسبقًا`() = runTest {
        val f = fixture(stockQuantity = 10, needed = 1, status = RequirementStatus.FULFILLED)

        assertTrue(f.useCase(f.reqId) is FulfillResult.Failure)
        assertEquals(10, f.inventory.items[f.invId]?.quantity)
    }

    @Test
    fun `احتياج غير موجود يعيد NotFound`() = runTest {
        val f = fixture(stockQuantity = 10, needed = 1)

        assertTrue(f.useCase(9999L) is FulfillResult.NotFound)
    }

    @Test
    fun `بند مرتبط بصنف مخزون محذوف يُرفض برسالة واضحة`() = runTest {
        val f = fixture(stockQuantity = 10, needed = 1)
        f.inventory.items.remove(f.invId)

        assertTrue(f.useCase(f.reqId) is FulfillResult.Failure)
        assertEquals(0, f.tx.started)
    }
}
