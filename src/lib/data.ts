/**
 * طبقة القراءة — كل الاستعلامات التي تحتاجها الشاشات
 */
import { db } from "@/db";
import {
  sites,
  equipment,
  attachments,
  siteHistory,
  inventoryItems,
  requirements,
  requirementItems,
  links,
  tickets,
  workOrders,
  maintenanceSchedules,
  alerts,
  auditLog,
  boqDocuments,
  boqLines,
} from "@/db/schema";
import { eq, desc, asc, sql, inArray } from "drizzle-orm";

/* ــــــــــــــــــــ لوحة المؤشرات ــــــــــــــــــــ */
export async function getDashboard() {
  const totalSites = await db.select({ v: sql<number>`count(*)::int` }).from(sites);
  const byStatus = await db
    .select({ status: sites.status, v: sql<number>`count(*)::int` })
    .from(sites)
    .groupBy(sites.status);
  const openTickets = await db
    .select({ severity: tickets.severity, v: sql<number>`count(*)::int` })
    .from(tickets)
    .where(sql`${tickets.status} != 'closed'`)
    .groupBy(tickets.severity);
  const unreadAlerts = await db
    .select({ v: sql<number>`count(*)::int` })
    .from(alerts)
    .where(eq(alerts.isRead, false));
  const linksByStatus = await db
    .select({ status: links.status, v: sql<number>`count(*)::int` })
    .from(links)
    .groupBy(links.status);
  const lowStock = await db
    .select({ id: inventoryItems.id, name: inventoryItems.name, quantity: inventoryItems.quantity, minThreshold: inventoryItems.minThreshold, unit: inventoryItems.unit })
    .from(inventoryItems)
    .where(sql`${inventoryItems.quantity} <= ${inventoryItems.minThreshold}`)
    .orderBy(asc(inventoryItems.quantity));
  const recentAlerts = await db.select().from(alerts).orderBy(desc(alerts.createdAt)).limit(8);
  const upcomingMaintenance = await db
    .select({
      id: maintenanceSchedules.id,
      type: maintenanceSchedules.type,
      nextDue: maintenanceSchedules.nextDue,
      siteName: sites.name,
    })
    .from(maintenanceSchedules)
    .innerJoin(sites, eq(maintenanceSchedules.siteId, sites.id))
    .orderBy(asc(maintenanceSchedules.nextDue))
    .limit(6);
  const recentTickets = await db
    .select({
      id: tickets.id,
      title: tickets.title,
      severity: tickets.severity,
      status: tickets.status,
      siteName: sites.name,
      openedAt: tickets.openedAt,
    })
    .from(tickets)
    .innerJoin(sites, eq(tickets.siteId, sites.id))
    .orderBy(desc(tickets.openedAt))
    .limit(6);

  return {
    totalSites: totalSites[0]?.v ?? 0,
    byStatus,
    openTickets,
    unreadAlerts: unreadAlerts[0]?.v ?? 0,
    linksByStatus,
    lowStock,
    recentAlerts,
    upcomingMaintenance,
    recentTickets,
  };
}

/* ــــــــــــــــــــ المواقع ــــــــــــــــــــ */
export async function getSitesList() {
  const rows = await db
    .select({
      id: sites.id,
      name: sites.name,
      code: sites.code,
      status: sites.status,
      siteType: sites.siteType,
      latitude: sites.latitude,
      longitude: sites.longitude,
      equipmentCount: sql<number>`count(${equipment.id})::int`,
    })
    .from(sites)
    .leftJoin(equipment, eq(equipment.siteId, sites.id))
    .groupBy(sites.id)
    .orderBy(asc(sites.code));
  const linkCounts = await db
    .select({
      siteId: sites.id,
      active: sql<number>`count(*) filter (where ${links.status} = 'active')::int`,
      down: sql<number>`count(*) filter (where ${links.status} = 'down')::int`,
    })
    .from(sites)
    .leftJoin(links, sql`${links.sourceSiteId} = ${sites.id} or ${links.targetSiteId} = ${sites.id}`)
    .groupBy(sites.id);
  return { rows, linkCounts };
}

export async function getSiteFull(id: number) {
  const site = await db.select().from(sites).where(eq(sites.id, id)).then((r) => r[0]);
  if (!site) return null;
  const [eqRows, att, hist, tix, scheds] = await Promise.all([
    db.select().from(equipment).where(eq(equipment.siteId, id)).orderBy(asc(equipment.category)),
    db.select().from(attachments).where(eq(attachments.siteId, id)).orderBy(desc(attachments.addedAt)),
    db.select().from(siteHistory).where(eq(siteHistory.siteId, id)).orderBy(desc(siteHistory.at)).limit(60),
    db.select().from(tickets).where(eq(tickets.siteId, id)).orderBy(desc(tickets.openedAt)).limit(30),
    db.select().from(maintenanceSchedules).where(eq(maintenanceSchedules.siteId, id)),
  ]);
  // الروابط التي يدخل فيها الموقع كمصدر أو هدف مع اسم الطرف الآخر
  const allLinks = await db.select().from(links);
  const allSites = await db.select({ id: sites.id, name: sites.name }).from(sites);
  const nameOf = new Map(allSites.map((s) => [s.id, s.name]));
  const linked = allLinks
    .filter((l) => l.sourceSiteId === id || l.targetSiteId === id)
    .map((l) => ({
      ...l,
      otherSiteName: nameOf.get(l.sourceSiteId === id ? l.targetSiteId : l.sourceSiteId) ?? "؟",
    }));
  return { site, equipment: eqRows, attachments: att, history: hist, tickets: tix, schedules: scheds, links: linked };
}

/* ــــــــــــــــــــ المجرة ــــــــــــــــــــ */
export async function getGalaxyData() {
  const [siteRows, linkRows] = await Promise.all([
    db
      .select({
        id: sites.id,
        name: sites.name,
        code: sites.code,
        status: sites.status,
        latitude: sites.latitude,
        longitude: sites.longitude,
      })
      .from(sites)
      .orderBy(asc(sites.code)),
    db.select().from(links),
  ]);
  return { sites: siteRows, links: linkRows };
}

/* ــــــــــــــــــــ المخزون والاحتياج ــــــــــــــــــــ */
export async function getInventory() {
  return db.select().from(inventoryItems).orderBy(asc(inventoryItems.name));
}

export async function getRequirementsFull() {
  const reqs = await db
    .select({
      id: requirements.id,
      type: requirements.type,
      status: requirements.status,
      createdAt: requirements.createdAt,
      notes: requirements.notes,
      siteName: sites.name,
      siteId: requirements.siteId,
    })
    .from(requirements)
    .innerJoin(sites, eq(requirements.siteId, sites.id))
    .orderBy(desc(requirements.createdAt));
  if (reqs.length === 0) return [];
  const items = await db
    .select()
    .from(requirementItems)
    .where(inArray(requirementItems.requirementId, reqs.map((r) => r.id)));
  return reqs.map((r) => ({ ...r, items: items.filter((i) => i.requirementId === r.id) }));
}

/* ــــــــــــــــــــ الصيانة ــــــــــــــــــــ */
export async function getMaintenanceData() {
  const [tix, wos, scheds] = await Promise.all([
    db
      .select({
        id: tickets.id,
        title: tickets.title,
        severity: tickets.severity,
        status: tickets.status,
        openedAt: tickets.openedAt,
        closedAt: tickets.closedAt,
        resolutionTimeMinutes: tickets.resolutionTimeMinutes,
        siteName: sites.name,
        siteId: tickets.siteId,
      })
      .from(tickets)
      .innerJoin(sites, eq(tickets.siteId, sites.id))
      .orderBy(desc(tickets.openedAt)),
    db
      .select({
        id: workOrders.id,
        assignedTo: workOrders.assignedTo,
        tasks: workOrders.tasks,
        status: workOrders.status,
        scheduledAt: workOrders.scheduledAt,
        completedAt: workOrders.completedAt,
        siteName: sites.name,
        siteId: workOrders.siteId,
      })
      .from(workOrders)
      .innerJoin(sites, eq(workOrders.siteId, sites.id))
      .orderBy(desc(workOrders.scheduledAt)),
    db
      .select({
        id: maintenanceSchedules.id,
        type: maintenanceSchedules.type,
        intervalDays: maintenanceSchedules.intervalDays,
        lastDone: maintenanceSchedules.lastDone,
        nextDue: maintenanceSchedules.nextDue,
        notes: maintenanceSchedules.notes,
        siteName: sites.name,
        siteId: maintenanceSchedules.siteId,
      })
      .from(maintenanceSchedules)
      .innerJoin(sites, eq(maintenanceSchedules.siteId, sites.id))
      .orderBy(asc(maintenanceSchedules.nextDue)),
  ]);
  return { tickets: tix, workOrders: wos, schedules: scheds };
}

/* ــــــــــــــــــــ التنبيهات والتدقيق ــــــــــــــــــــ */
export async function getAlerts() {
  return db.select().from(alerts).orderBy(desc(alerts.createdAt)).limit(100);
}

export async function getAuditLog() {
  return db.select().from(auditLog).orderBy(desc(auditLog.at)).limit(300);
}

export async function getAllSitesSimple() {
  return db.select({ id: sites.id, name: sites.name, code: sites.code }).from(sites).orderBy(asc(sites.code));
}

/* ــــــــــــــــــــ التقارير ــــــــــــــــــــ */
export async function getReportsData() {
  const [byStatus, eqByCategory, eqByStatus, linksByType, ticketsBySeverity, inventory] = await Promise.all([
    db.select({ status: sites.status, v: sql<number>`count(*)::int` }).from(sites).groupBy(sites.status),
    db
      .select({ category: equipment.category, v: sql<number>`count(*)::int` })
      .from(equipment)
      .groupBy(equipment.category)
      .orderBy(desc(sql`count(*)`)),
    db
      .select({ status: equipment.status, v: sql<number>`count(*)::int` })
      .from(equipment)
      .groupBy(equipment.status),
    db
      .select({ type: links.type, status: links.status, v: sql<number>`count(*)::int` })
      .from(links)
      .groupBy(links.type, links.status),
    db
      .select({ severity: tickets.severity, status: tickets.status, v: sql<number>`count(*)::int` })
      .from(tickets)
      .groupBy(tickets.severity, tickets.status),
    db.select().from(inventoryItems).orderBy(asc(inventoryItems.name)),
  ]);
  return { byStatus, eqByCategory, eqByStatus, linksByType, ticketsBySeverity, inventory };
}

/* ــــــــــــــــــــ نسخ احتياطي ــــــــــــــــــــ */
export async function exportBackup() {
  const [s, e, a, h, inv, req, ri, l, t, wo, ms, al, bd, bl] = await Promise.all([
    db.select().from(sites),
    db.select().from(equipment),
    db.select().from(attachments),
    db.select().from(siteHistory),
    db.select().from(inventoryItems),
    db.select().from(requirements),
    db.select().from(requirementItems),
    db.select().from(links),
    db.select().from(tickets),
    db.select().from(workOrders),
    db.select().from(maintenanceSchedules),
    db.select().from(alerts),
    db.select().from(boqDocuments),
    db.select().from(boqLines),
  ]);
  return { sites: s, equipment: e, attachments: a, siteHistory: h, inventoryItems: inv, requirements: req, requirementItems: ri, links: l, tickets: t, workOrders: wo, maintenanceSchedules: ms, alerts: al, boqDocuments: bd, boqLines: bl, exportedAt: new Date().toISOString() };
}
