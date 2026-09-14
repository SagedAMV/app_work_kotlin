package com.majarra.galaxy.data.seed

import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.data.local.AuditLog
import com.majarra.galaxy.data.local.Equipment
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.data.local.InventoryItem
import com.majarra.galaxy.data.local.Link
import com.majarra.galaxy.data.local.MaintenanceSchedule
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.RequirementItem
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.WorkOrder
import com.majarra.galaxy.domain.model.AlertType
import com.majarra.galaxy.domain.model.EquipmentCategory
import com.majarra.galaxy.domain.model.EquipmentStatus
import com.majarra.galaxy.domain.model.InventoryUnit
import com.majarra.galaxy.domain.model.LinkPriority
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.domain.model.LinkType
import com.majarra.galaxy.domain.model.MaintenanceType
import com.majarra.galaxy.domain.model.NetworkClass
import com.majarra.galaxy.domain.model.RequirementStatus
import com.majarra.galaxy.domain.model.RequirementType
import com.majarra.galaxy.domain.model.SiteStatus
import com.majarra.galaxy.domain.model.TicketSeverity
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.model.WorkOrderStatus
import com.majarra.galaxy.util.RadioMath
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * زارع البيانات التجريبية — حسب التعليمات: 50 موقعًا + معدات + روابط.
 * عشوائية ببذرة ثابتة (42) حتى تكون البيانات قابلة للتكرار والاختبار.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val db: GalaxyDatabase
) {
    suspend fun seed() {
        if (db.siteDao().countSync() > 0) return
        val rnd = Random(42)
        val day = 24L * 3600 * 1000

        // 1) 50 موقعًا موزعة حول منطقة افتراضية
        val regions = listOf("القيادة", "العمليات", "الشمالي", "الجنوبي", "الشرقي", "الغربي", "المركز", "المرصد")
        val sites = (1..50).map { i ->
            Site(
                name = "موقع ${regions[rnd.nextInt(regions.size)]} $i",
                code = "ST-%03d".format(i),
                latitude = 33.0 + rnd.nextDouble() * 1.2,
                longitude = 43.8 + rnd.nextDouble() * 1.6,
                status = when (rnd.nextInt(10)) {
                    0 -> SiteStatus.DOWN
                    1 -> SiteStatus.DEGRADED
                    else -> SiteStatus.ACTIVE
                },
                notes = "موقع تجريبي أُنشئ تلقائيًا"
            )
        }
        val siteIds = sites.map { db.siteDao().insert(it) }

        // 2) معدات: 3 إلى 6 لكل موقع
        val companies = listOf("هواوي", "نوكيا", "إريكسون", "محلي", "زيوتكس")
        val equipmentIds = mutableListOf<Long>()
        for (sid in siteIds) {
            val n = 3 + rnd.nextInt(4)
            repeat(n) { k ->
                val category = EquipmentCategory.values()[rnd.nextInt(EquipmentCategory.values().size)]
                val faulty = rnd.nextInt(12) == 0
                val id = db.equipmentDao().insert(
                    Equipment(
                        siteId = sid,
                        category = category,
                        company = companies[rnd.nextInt(companies.size)],
                        model = "MDL-${100 + rnd.nextInt(900)}",
                        serialNumber = "SN-${100000 + rnd.nextInt(900000)}",
                        status = if (faulty) EquipmentStatus.FAULTY else EquipmentStatus.WORKING,
                        installDate = System.currentTimeMillis() - (30L + rnd.nextInt(1000)) * day,
                        lifespanMonths = if (rnd.nextBoolean()) 24 + rnd.nextInt(36) else 0
                    )
                )
                equipmentIds += id
                // جدول صيانة وقائية لمعدة واحدة في كل موقع
                if (k == 0) {
                    db.maintenanceScheduleDao().insert(
                        MaintenanceSchedule(
                            siteId = sid,
                            equipmentId = id,
                            type = MaintenanceType.values()[rnd.nextInt(MaintenanceType.values().size)],
                            intervalDays = 30 + rnd.nextInt(150),
                            lastDone = System.currentTimeMillis() - rnd.nextInt(120) * day,
                            nextDue = System.currentTimeMillis() + (rnd.nextInt(30) - 10) * day,
                            notes = "صيانة دورية"
                        )
                    )
                }
            }
        }

        // 3) روابط: كل موقع يتصل بواحد أو اثنين من أقرب المواقع إليه
        val siteRows = db.siteDao().getAll()
        var linkCount = 0
        for (i in siteRows.indices) {
            val src = siteRows[i]
            val candidates = siteRows
                .filter { it.id != src.id }
                .sortedBy { RadioMath.haversineKm(src.latitude, src.longitude, it.latitude, it.longitude) }
                .take(3)
            val targets = candidates.shuffled(java.util.Random(42L + i)).take(1 + rnd.nextInt(2))
            for (t in targets) {
                if (linkCount >= 70) break
                db.linkDao().insert(
                    Link(
                        sourceSiteId = src.id,
                        targetSiteId = t.id,
                        type = LinkType.values()[rnd.nextInt(LinkType.values().size)],
                        status = when (rnd.nextInt(10)) {
                            0 -> LinkStatus.DOWN
                            1 -> LinkStatus.DEGRADED
                            else -> LinkStatus.ACTIVE
                        },
                        networkClass = NetworkClass.values()[rnd.nextInt(NetworkClass.values().size)],
                        priority = LinkPriority.values()[rnd.nextInt(LinkPriority.values().size)],
                        frequencyMHz = listOf(900.0, 1800.0, 2400.0, 5800.0, 11000.0)[rnd.nextInt(5)],
                        distanceKm = RadioMath.haversineKm(src.latitude, src.longitude, t.latitude, t.longitude),
                        notes = ""
                    )
                )
                linkCount++
            }
        }

        // 4) مخزون مركزي: 15 صنفًا بعضها تحت الحد الأدنى
        val inventoryNames = listOf(
            "كابل coaxial", "كابل ألياف 12 شعيرة", "بطارية 200 أمبير", "لوح شمسي 450 واط",
            "هوائي قطاعي", "راديو ميكروويف 5.8", "مبدل شبكة 24 منفذ", "منظم جهد 48 فولت",
            "شاحن ذكي", "موصل RJ45", "صندوق توزيع", "سارية 6 متر", "طقم عدة صيانة",
            "مرشح ترددات", "وحدة تبريد"
        )
        val inventoryIds = inventoryNames.mapIndexed { idx, name ->
            db.inventoryItemDao().insert(
                InventoryItem(
                    name = name,
                    category = "عام",
                    unit = if (name.startsWith("كابل")) InventoryUnit.METER else InventoryUnit.PIECE,
                    quantity = if (idx % 5 == 0) rnd.nextInt(3) else 5 + rnd.nextInt(40),
                    minThreshold = 3 + rnd.nextInt(5),
                    location = "مستودع مركزي — رف ${1 + rnd.nextInt(20)}"
                )
            )
        }

        // 5) احتياجان مع بنود + تذاكر + أوامر شغل
        for (r in 0 until 2) {
            val rid = db.requirementDao().insert(
                Requirement(
                    siteId = siteIds[r],
                    type = if (r == 0) RequirementType.MATERIALS else RequirementType.DEVICES,
                    status = RequirementStatus.PENDING,
                    notes = "احتياج تجريبي"
                )
            )
            db.requirementItemDao().insertAll(
                listOf(
                    RequirementItem(
                        requirementId = rid,
                        inventoryItemId = inventoryIds[rnd.nextInt(inventoryIds.size)],
                        description = "بند أول للاحتياج التجريبي",
                        quantity = 2 + rnd.nextInt(3)
                    ),
                    RequirementItem(
                        requirementId = rid,
                        inventoryItemId = null,
                        description = "بند غير مرتبط بالمخزون",
                        quantity = 1
                    )
                )
            )
        }
        for (i in 0 until 10) {
            val open = i % 3 != 0
            db.ticketDao().insert(
                Ticket(
                    siteId = siteIds[rnd.nextInt(siteIds.size)],
                    equipmentId = equipmentIds[rnd.nextInt(equipmentIds.size)],
                    title = "عطل تجريبي رقم ${i + 1}",
                    description = "وصف عطل تجريبي لأغراض العرض",
                    severity = TicketSeverity.values()[rnd.nextInt(TicketSeverity.values().size)],
                    status = if (open) TicketStatus.OPEN else TicketStatus.CLOSED,
                    openedAt = System.currentTimeMillis() - rnd.nextInt(10) * day,
                    closedAt = if (open) null else System.currentTimeMillis() - rnd.nextInt(2) * day,
                    resolutionTimeMinutes = if (open) null else 30L + rnd.nextInt(600)
                )
            )
        }
        db.workOrderDao().insert(
            WorkOrder(
                siteId = siteIds.first(),
                assignedTo = "فريق الصيانة الأول",
                tasks = "فحص الطاقة\nمعايرة الهوائي",
                status = WorkOrderStatus.SCHEDULED
            )
        )

        // 6) تنبيه أولي + سجل تدقيق للبذر
        db.alertDao().insert(
            Alert(
                type = AlertType.MAINTENANCE_DUE,
                refId = 1,
                message = "مرحبًا بك في مجرة — افحص التنبيهات من شاشة الصيانة"
            )
        )
        db.auditLogDao().insert(
            AuditLog(
                action = "SEED",
                entityType = "Database",
                entityId = null,
                details = "زرع 50 موقعًا تجريبيًا"
            )
        )
    }
}
