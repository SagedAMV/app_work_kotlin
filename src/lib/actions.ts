/**
 * ⚙️ أفعال الخادم — كل عمليات الكتابة مع قواعد العمل الكاملة:
 * 1. لا حذف لموقع له معدات أو روابط بدون تأكيد مضاعف
 * 2. إغلاق التذكرة يسجل زمن المعالجة تلقائيًا
 * 3. صرف الاحتياج يخصم المخزون + سجل تدقيق
 * 4. نقص المخزون عن الحد الأدنى → تنبيه تلقائي
 * 5. صيانة وقائية خلال 7 أيام → تنبيه
 * 6. انتهاء عمر معدة → تنبيه
 * 7. كل عملية تسجل في AuditLog
 */
"use server";

import { db } from "@/db";
import {
  sites,
  equipment,
  attachments,
  siteHistory,
  inventoryItems,
  requirements,
  requirementItems,
  boqDocuments,
  boqLines,
  links,
  tickets,
  workOrders,
  maintenanceSchedules,
  alerts,
  auditLog,
  settings,
} from "@/db/schema";
import { eq, and, sql } from "drizzle-orm";
import { revalidatePath } from "next/cache";
import { createHash } from "crypto";
import { haversineKm } from "./galaxy-math";

type Result = { ok: true; id?: number } | { ok: false; error: string };

function clean(v: string, max = 500): string {
  return v.trim().slice(0, max);
}

async function audit(action: string, entityType: string, entityId: number | null, details: string) {
  await db.insert(auditLog).values({ action, entityType, entityId, details: details.slice(0, 1000) });
}

/* ــــــــــــــــــــ محرك التنبيهات التلقائي ــــــــــــــــــــ */
export async function runAlertEngine() {
  // نقص المخزون
  const low = await db
    .select()
    .from(inventoryItems)
    .where(sql`${inventoryItems.quantity} <= ${inventoryItems.minThreshold}`);
  for (const item of low) {
    const exists = await db
      .select({ id: alerts.id })
      .from(alerts)
      .where(and(eq(alerts.type, "inventory"), eq(alerts.refId, item.id), eq(alerts.isRead, false)))
      .limit(1);
    if (exists.length === 0) {
      await db.insert(alerts).values({
        type: "inventory",
        refId: item.id,
        message: `نقص مخزون: «${item.name}» بلغ ${item.quantity} ${item.unit} (الحد الأدنى ${item.minThreshold})`,
      });
    }
  }
  // الصيانة الوقائية خلال 7 أيام
  const soon = new Date(Date.now() + 7 * 864e5);
  const dueScheds = await db
    .select()
    .from(maintenanceSchedules)
    .where(sql`${maintenanceSchedules.nextDue} <= ${soon}`);
  for (const s of dueScheds) {
    const exists = await db
      .select({ id: alerts.id })
      .from(alerts)
      .where(and(eq(alerts.type, "maintenance"), eq(alerts.refId, s.id), eq(alerts.isRead, false)))
      .limit(1);
    if (exists.length === 0) {
      const overdue = s.nextDue.getTime() < Date.now();
      await db.insert(alerts).values({
        type: "maintenance",
        refId: s.id,
        message: `${overdue ? "تجاوزت موعد" : "اقتربت"} الصيانة الوقائية «${s.type}» ${overdue ? "منذ" : "بعد"} ${Math.abs(Math.round((s.nextDue.getTime() - Date.now()) / 864e5))} يوم`,
      });
    }
  }
  // انتهاء عمر المعدات (خلال 30 يومًا)
  const horizon = new Date(Date.now() + 30 * 864e5);
  const allEq = await db.select().from(equipment);
  for (const e of allEq) {
    if (e.status === "retired") continue;
    const endOfLife = new Date(e.installDate.getTime() + e.lifespanYears * 365 * 864e5);
    if (endOfLife.getTime() <= horizon.getTime()) {
      const exists = await db
        .select({ id: alerts.id })
        .from(alerts)
        .where(and(eq(alerts.type, "lifespan"), eq(alerts.refId, e.id), eq(alerts.isRead, false)))
        .limit(1);
      if (exists.length === 0) {
        await db.insert(alerts).values({
          type: "lifespan",
          refId: e.id,
          message: `المعدة «${e.company} ${e.model}» بلغت نهاية عمرها الافتراضي (${e.lifespanYears} سنة)`,
        });
      }
    }
  }
}

/* ــــــــــــــــــــ المواقع ــــــــــــــــــــ */
export async function createSite(fd: FormData): Promise<Result> {
  const name = clean(String(fd.get("name") ?? ""));
  const code = clean(String(fd.get("code") ?? ""));
  if (!name || !code) return { ok: false, error: "الاسم والرمز حقلان إلزاميان" };
  const dup = await db.select({ id: sites.id }).from(sites).where(eq(sites.code, code)).limit(1);
  if (dup.length > 0) return { ok: false, error: `الرمز ${code} مستخدم مسبقًا` };
  const lat = Number(fd.get("latitude") ?? 0) || 0;
  const lon = Number(fd.get("longitude") ?? 0) || 0;
  const row = await db
    .insert(sites)
    .values({
      name,
      code,
      latitude: lat,
      longitude: lon,
      status: clean(String(fd.get("status") ?? "active"), 20),
      siteType: clean(String(fd.get("siteType") ?? "mountain"), 20),
      notes: clean(String(fd.get("notes") ?? ""), 2000),
    })
    .returning({ id: sites.id });
  await db.insert(siteHistory).values({ siteId: row[0].id, action: "إنشاء", newValue: `${name} (${code})` });
  await audit("إنشاء", "موقع", row[0].id, `إنشاء موقع ${name} [${code}]`);
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

export async function updateSite(fd: FormData): Promise<Result> {
  const id = Number(fd.get("id"));
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  const before = await db.select().from(sites).where(eq(sites.id, id)).then((r) => r[0]);
  if (!before) return { ok: false, error: "الموقع غير موجود" };
  const name = clean(String(fd.get("name") ?? before.name));
  const status = clean(String(fd.get("status") ?? before.status), 20);
  await db
    .update(sites)
    .set({
      name,
      status,
      siteType: clean(String(fd.get("siteType") ?? before.siteType), 20),
      latitude: Number(fd.get("latitude") ?? before.latitude) || before.latitude,
      longitude: Number(fd.get("longitude") ?? before.longitude) || before.longitude,
      notes: clean(String(fd.get("notes") ?? before.notes), 2000),
      updatedAt: new Date(),
    })
    .where(eq(sites.id, id));
  if (before.status !== status) {
    await db.insert(siteHistory).values({ siteId: id, action: "تغيير حالة", oldValue: before.status, newValue: status });
  }
  await audit("تعديل", "موقع", id, `تعديل بيانات موقع ${name}`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

/** حذف آمن: يُرفض إن وجدت معدات أو روابط مرتبطة إلا بتأكيد مضاعف */
export async function deleteSite(id: number, force: boolean): Promise<Result | { ok: false; error: "has-dependencies"; equipment: number; links: number }> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  const [eqCount] = await db.select({ v: sql<number>`count(*)::int` }).from(equipment).where(eq(equipment.siteId, id));
  const [linkCount] = await db
    .select({ v: sql<number>`count(*)::int` })
    .from(links)
    .where(sql`${links.sourceSiteId} = ${id} or ${links.targetSiteId} = ${id}`);
  if ((eqCount.v > 0 || linkCount.v > 0) && !force) {
    return { ok: false, error: "has-dependencies", equipment: eqCount.v, links: linkCount.v };
  }
  const site = await db.select({ name: sites.name, code: sites.code }).from(sites).where(eq(sites.id, id)).then((r) => r[0]);
  await db.delete(links).where(sql`${links.sourceSiteId} = ${id} or ${links.targetSiteId} = ${id}`);
  await db.delete(sites).where(eq(sites.id, id));
  await audit("حذف", "موقع", id, `حذف موقع ${site?.name ?? id} [${site?.code ?? ""}] مع ${eqCount.v} معدة و${linkCount.v} رابط`);
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ المعدات ــــــــــــــــــــ */
export async function addEquipment(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  if (!Number.isInteger(siteId) || siteId <= 0) return { ok: false, error: "موقع غير صالح" };
  const category = clean(String(fd.get("category") ?? "other"), 30);
  const row = await db
    .insert(equipment)
    .values({
      siteId,
      category,
      company: clean(String(fd.get("company") ?? "")),
      model: clean(String(fd.get("model") ?? "")),
      serialNumber: clean(String(fd.get("serialNumber") ?? "")),
      status: clean(String(fd.get("status") ?? "working"), 20),
      lifespanYears: Math.max(1, Number(fd.get("lifespanYears") ?? 10) || 10),
      notes: clean(String(fd.get("notes") ?? ""), 1000),
    })
    .returning({ id: equipment.id });
  await db.insert(siteHistory).values({ siteId, action: "إضافة معدة", newValue: `${category} — ${String(fd.get("company") ?? "")} ${String(fd.get("model") ?? "")}` });
  await audit("إضافة", "معدة", row[0].id, `إضافة معدة ${category} للموقع رقم ${siteId}`);
  await runAlertEngine();
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

export async function setEquipmentStatus(id: number, status: string): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.update(equipment).set({ status: clean(status, 20) }).where(eq(equipment.id, id));
  await audit("تعديل", "معدة", id, `تغيير حالة المعدة إلى ${status}`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

export async function deleteEquipment(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.delete(equipment).where(eq(equipment.id, id));
  await audit("حذف", "معدة", id, `حذف المعدة رقم ${id}`);
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ المرفقات ــــــــــــــــــــ */
export async function addAttachment(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  const dataUrl = String(fd.get("dataUrl") ?? "");
  if (!Number.isInteger(siteId) || siteId <= 0) return { ok: false, error: "موقع غير صالح" };
  if (!dataUrl.startsWith("data:image/") && !dataUrl.startsWith("data:application/pdf")) {
    return { ok: false, error: "الملف يجب أن يكون صورة أو PDF" };
  }
  if (dataUrl.length > 3_500_000) return { ok: false, error: "حجم الملف كبير جدًا (الحد 2.5م.ب)" };
  const row = await db
    .insert(attachments)
    .values({ siteId, type: dataUrl.startsWith("data:image/") ? "image" : "pdf", dataUrl, caption: clean(String(fd.get("caption") ?? "")) })
    .returning({ id: attachments.id });
  await db.insert(siteHistory).values({ siteId, action: "إضافة مرفق", newValue: String(fd.get("caption") ?? "مرفق") });
  await audit("إضافة", "مرفق", row[0].id, `إضافة مرفق للموقع ${siteId}`);
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

export async function deleteAttachment(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.delete(attachments).where(eq(attachments.id, id));
  await audit("حذف", "مرفق", id, `حذف المرفق رقم ${id}`);
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ الروابط (المجرة) ــــــــــــــــــــ */
export async function createLink(fd: FormData): Promise<Result> {
  const src = Number(fd.get("sourceSiteId"));
  const tgt = Number(fd.get("targetSiteId"));
  if (!Number.isInteger(src) || !Number.isInteger(tgt) || src <= 0 || tgt <= 0) return { ok: false, error: "اختر موقعي الرابط" };
  if (src === tgt) return { ok: false, error: "لا يمكن ربط الموقع بنفسه" };
  const dup = await db
    .select({ id: links.id })
    .from(links)
    .where(sql`(${links.sourceSiteId}=${src} and ${links.targetSiteId}=${tgt}) or (${links.sourceSiteId}=${tgt} and ${links.targetSiteId}=${src})`)
    .limit(1);
  if (dup.length > 0) return { ok: false, error: "يوجد رابط بين هذين الموقعين مسبقًا" };
  const [s, t] = await Promise.all([
    db.select().from(sites).where(eq(sites.id, src)).then((r) => r[0]),
    db.select().from(sites).where(eq(sites.id, tgt)).then((r) => r[0]),
  ]);
  if (!s || !t) return { ok: false, error: "أحد الموقعين غير موجود" };
  const dist = haversineKm(s.latitude, s.longitude, t.latitude, t.longitude);
  const row = await db
    .insert(links)
    .values({
      sourceSiteId: src,
      targetSiteId: tgt,
      type: clean(String(fd.get("type") ?? "microwave"), 20),
      status: clean(String(fd.get("status") ?? "active"), 20),
      networkClass: clean(String(fd.get("networkClass") ?? "operations"), 20),
      priority: Math.min(5, Math.max(1, Number(fd.get("priority") ?? 3) || 3)),
      frequencyMhz: Number(fd.get("frequencyMhz") ?? 5800) || 5800,
      txPowerDbm: Number(fd.get("txPowerDbm") ?? 20) || 20,
      txGainDbi: Number(fd.get("txGainDbi") ?? 24) || 24,
      rxGainDbi: Number(fd.get("rxGainDbi") ?? 24) || 24,
      cableLossDb: Number(fd.get("cableLossDb") ?? 1) || 1,
      distanceKm: Number(dist.toFixed(3)),
      notes: clean(String(fd.get("notes") ?? ""), 500),
    })
    .returning({ id: links.id });
  await audit("إنشاء", "رابط", row[0].id, `رابط ${s.name} ↔ ${t.name} (مسافة ${dist.toFixed(1)} كم)`);
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

export async function setLinkStatus(id: number, status: string): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.update(links).set({ status: clean(status, 20) }).where(eq(links.id, id));
  await audit("تعديل", "رابط", id, `تغيير حالة الرابط إلى ${status}`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

export async function deleteLink(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.delete(links).where(eq(links.id, id));
  await audit("حذف", "رابط", id, `حذف الرابط رقم ${id}`);
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ التذاكر وأوامر الشغل ــــــــــــــــــــ */
export async function createTicket(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  const title = clean(String(fd.get("title") ?? ""));
  if (!Number.isInteger(siteId) || siteId <= 0 || !title) return { ok: false, error: "الموقع والعنوان إلزاميان" };
  const row = await db
    .insert(tickets)
    .values({
      siteId,
      title,
      description: clean(String(fd.get("description") ?? ""), 2000),
      severity: clean(String(fd.get("severity") ?? "minor"), 20),
      equipmentId: Number(fd.get("equipmentId")) > 0 ? Number(fd.get("equipmentId")) : null,
    })
    .returning({ id: tickets.id });
  await audit("إنشاء", "تذكرة", row[0].id, `فتح تذكرة «${title}»`);
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

/** قاعدة العمل 2: عند الإغلاق يُحسب زمن المعالجة تلقائيًا */
export async function setTicketStatus(id: number, status: string): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  if (status === "closed") {
    const t = await db.select().from(tickets).where(eq(tickets.id, id)).then((r) => r[0]);
    if (!t) return { ok: false, error: "التذكرة غير موجودة" };
    const mins = Math.max(0, Math.round((Date.now() - t.openedAt.getTime()) / 60000));
    await db.update(tickets).set({ status: "closed", closedAt: new Date(), resolutionTimeMinutes: mins }).where(eq(tickets.id, id));
    await audit("إغلاق", "تذكرة", id, `إغلاق التذكرة بعد ${mins} دقيقة`);
  } else {
    await db.update(tickets).set({ status: clean(status, 20) }).where(eq(tickets.id, id));
    await audit("تعديل", "تذكرة", id, `تغيير حالة التذكرة إلى ${status}`);
  }
  revalidatePath("/", "layout");
  return { ok: true, id };
}

export async function createWorkOrder(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  if (!Number.isInteger(siteId) || siteId <= 0) return { ok: false, error: "الموقع إلزامي" };
  const row = await db
    .insert(workOrders)
    .values({
      siteId,
      assignedTo: clean(String(fd.get("assignedTo") ?? "")),
      tasks: clean(String(fd.get("tasks") ?? ""), 1000),
      ticketId: Number(fd.get("ticketId")) > 0 ? Number(fd.get("ticketId")) : null,
      scheduledAt: new Date(String(fd.get("scheduledAt") || new Date().toISOString()) || Date.now()),
    })
    .returning({ id: workOrders.id });
  await audit("إنشاء", "أمر شغل", row[0].id, `أمر شغل للموقع ${siteId} — ${String(fd.get("assignedTo") ?? "")}`);
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

export async function setWorkOrderStatus(id: number, status: string): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  const patch: { status: string; completedAt?: Date } = { status: clean(status, 20) };
  if (status === "done") patch.completedAt = new Date();
  await db.update(workOrders).set(patch).where(eq(workOrders.id, id));
  await audit("تعديل", "أمر شغل", id, `تغيير حالة أمر الشغل إلى ${status}`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

/* ــــــــــــــــــــ الصيانة الوقائية ــــــــــــــــــــ */
export async function createSchedule(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  if (!Number.isInteger(siteId) || siteId <= 0) return { ok: false, error: "الموقع إلزامي" };
  const interval = Math.max(1, Number(fd.get("intervalDays") ?? 90) || 90);
  const row = await db
    .insert(maintenanceSchedules)
    .values({
      siteId,
      type: clean(String(fd.get("type") ?? "فحص دوري")),
      intervalDays: interval,
      lastDone: new Date(),
      nextDue: new Date(Date.now() + interval * 864e5),
      notes: clean(String(fd.get("notes") ?? ""), 500),
    })
    .returning({ id: maintenanceSchedules.id });
  await audit("إنشاء", "جدول صيانة", row[0].id, `جدول «${String(fd.get("type") ?? "")}» كل ${interval} يوم`);
  await runAlertEngine();
  revalidatePath("/", "layout");
  return { ok: true, id: row[0].id };
}

/** تنفيذ صيانة: تحديث آخر تنفيذ والموعد التالي */
export async function completeMaintenance(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  const s = await db.select().from(maintenanceSchedules).where(eq(maintenanceSchedules.id, id)).then((r) => r[0]);
  if (!s) return { ok: false, error: "الجدول غير موجود" };
  await db
    .update(maintenanceSchedules)
    .set({ lastDone: new Date(), nextDue: new Date(Date.now() + s.intervalDays * 864e5) })
    .where(eq(maintenanceSchedules.id, id));
  await audit("تنفيذ", "صيانة وقائية", id, `تنفيذ «${s.type}» وترحيل الموعد ${s.intervalDays} يوم`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

/* ــــــــــــــــــــ المخزون ــــــــــــــــــــ */
export async function saveInventoryItem(fd: FormData): Promise<Result> {
  const name = clean(String(fd.get("name") ?? ""));
  if (!name) return { ok: false, error: "اسم الصنف إلزامي" };
  const qty = Math.max(0, Number(fd.get("quantity") ?? 0) || 0);
  const minT = Math.max(0, Number(fd.get("minThreshold") ?? 1) || 0);
  const id = Number(fd.get("id") ?? 0);
  if (id > 0) {
    await db
      .update(inventoryItems)
      .set({ name, category: clean(String(fd.get("category") ?? "مواد")), unit: clean(String(fd.get("unit") ?? "قطعة")), quantity: qty, minThreshold: minT, location: clean(String(fd.get("location") ?? "المخزن المركزي")) })
      .where(eq(inventoryItems.id, id));
    await audit("تعديل", "مخزون", id, `تعديل الصنف «${name}» — الكمية ${qty}`);
  } else {
    const row = await db
      .insert(inventoryItems)
      .values({ name, category: clean(String(fd.get("category") ?? "مواد")), unit: clean(String(fd.get("unit") ?? "قطعة")), quantity: qty, minThreshold: minT, location: clean(String(fd.get("location") ?? "المخزن المركزي")) })
      .returning({ id: inventoryItems.id });
    await audit("إضافة", "مخزون", row[0].id, `إضافة الصنف «${name}» — الكمية ${qty}`);
  }
  await runAlertEngine();
  revalidatePath("/", "layout");
  return { ok: true };
}

export async function deleteInventoryItem(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  await db.delete(inventoryItems).where(eq(inventoryItems.id, id));
  await audit("حذف", "مخزون", id, `حذف الصنف رقم ${id}`);
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ الاحتياج وBOQ ــــــــــــــــــــ */
export async function createRequirement(fd: FormData): Promise<Result> {
  const siteId = Number(fd.get("siteId"));
  if (!Number.isInteger(siteId) || siteId <= 0) return { ok: false, error: "الموقع إلزامي" };
  let items: { inventoryItemId: number | null; description: string; quantity: number }[] = [];
  try {
    items = JSON.parse(String(fd.get("items") ?? "[]"));
  } catch {
    return { ok: false, error: "بنود الاحتياج غير صالحة" };
  }
  if (!Array.isArray(items) || items.length === 0) return { ok: false, error: "أضف بندًا واحدًا على الأقل" };
  const req = await db
    .insert(requirements)
    .values({ siteId, type: clean(String(fd.get("type") ?? "materials"), 20), notes: clean(String(fd.get("notes") ?? ""), 500) })
    .returning({ id: requirements.id });
  for (const it of items) {
    await db.insert(requirementItems).values({
      requirementId: req[0].id,
      inventoryItemId: it.inventoryItemId && it.inventoryItemId > 0 ? it.inventoryItemId : null,
      description: String(it.description ?? "").slice(0, 300),
      quantity: Math.max(1, Number(it.quantity) || 1),
    });
  }
  await audit("إنشاء", "احتياج", req[0].id, `احتياج ${items.length} بند للموقع ${siteId}`);
  revalidatePath("/", "layout");
  return { ok: true, id: req[0].id };
}

/** قاعدة العمل 3: الصرف يخصم المخزون تلقائيًا + تدقيق كامل */
export async function fulfillRequirement(id: number): Promise<Result> {
  if (!Number.isInteger(id) || id <= 0) return { ok: false, error: "معرّف غير صالح" };
  const req = await db.select().from(requirements).where(eq(requirements.id, id)).then((r) => r[0]);
  if (!req) return { ok: false, error: "الاحتياج غير موجود" };
  if (req.status === "fulfilled") return { ok: false, error: "مصروف مسبقًا" };
  const items = await db.select().from(requirementItems).where(eq(requirementItems.requirementId, id));
  // تحقق من توفر المخزون أولًا
  for (const it of items) {
    if (it.fulfilled || !it.inventoryItemId) continue;
    const [inv] = await db.select().from(inventoryItems).where(eq(inventoryItems.id, it.inventoryItemId));
    if (!inv) continue;
    if (inv.quantity < it.quantity) {
      return { ok: false, error: `المخزون لا يكفي: «${inv.name}» متوفر ${inv.quantity} والمطلوب ${it.quantity}` };
    }
  }
  for (const it of items) {
    if (it.fulfilled) continue;
    if (it.inventoryItemId) {
      await db
        .update(inventoryItems)
        .set({ quantity: sql`${inventoryItems.quantity} - ${it.quantity}` })
        .where(eq(inventoryItems.id, it.inventoryItemId));
      await audit("صرف", "مخزون", it.inventoryItemId, `خصم ${it.quantity} بسبب الاحتياج رقم ${id}`);
    }
    await db.update(requirementItems).set({ fulfilled: true }).where(eq(requirementItems.id, it.id));
  }
  await db.update(requirements).set({ status: "fulfilled" }).where(eq(requirements.id, id));
  await audit("صرف", "احتياج", id, `صرف الاحتياج رقم ${id} وخصم المخزون`);
  await runAlertEngine();
  revalidatePath("/", "layout");
  return { ok: true, id };
}

export async function setRequirementStatus(id: number, status: string): Promise<Result> {
  if (status === "fulfilled") return fulfillRequirement(id);
  await db.update(requirements).set({ status: clean(status, 20) }).where(eq(requirements.id, id));
  await audit("تعديل", "احتياج", id, `تغيير حالة الاحتياج إلى ${status}`);
  revalidatePath("/", "layout");
  return { ok: true, id };
}

/** قاعدة العمل: توليد BOQ تلقائيًا من الاحتياج */
export async function generateBoq(requirementId: number): Promise<Result> {
  if (!Number.isInteger(requirementId) || requirementId <= 0) return { ok: false, error: "معرّف غير صالح" };
  const items = await db.select().from(requirementItems).where(eq(requirementItems.requirementId, requirementId));
  if (items.length === 0) return { ok: false, error: "لا بنود لهذا الاحتياج" };
  const existing = await db.select({ id: boqDocuments.id }).from(boqDocuments).where(eq(boqDocuments.requirementId, requirementId));
  if (existing.length > 0) return { ok: false, error: "يوجد BOQ مسبقًا لهذا الاحتياج" };
  const doc = await db.insert(boqDocuments).values({ requirementId }).returning({ id: boqDocuments.id });
  for (const it of items) {
    await db.insert(boqLines).values({ boqId: doc[0].id, description: it.description, quantity: it.quantity, unit: "قطعة", unitCost: "0" });
  }
  await audit("إنشاء", "BOQ", doc[0].id, `توليد BOQ بـ ${items.length} بند من الاحتياج ${requirementId}`);
  revalidatePath("/", "layout");
  return { ok: true, id: doc[0].id };
}

/* ــــــــــــــــــــ التنبيهات ــــــــــــــــــــ */
export async function markAlertRead(id: number): Promise<Result> {
  await db.update(alerts).set({ isRead: true }).where(eq(alerts.id, id));
  revalidatePath("/", "layout");
  return { ok: true };
}

export async function markAllAlertsRead(): Promise<Result> {
  await db.update(alerts).set({ isRead: true }).where(eq(alerts.isRead, false));
  await audit("تعديل", "تنبيهات", null, "تعليم جميع التنبيهات كمقروءة");
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ الأمان: القفل برمز سري ــــــــــــــــــــ */
function hashPin(pin: string): string {
  return createHash("sha256").update(`galaxy:${pin}`).digest("hex");
}

export async function setLock(enabled: boolean, pin: string): Promise<Result> {
  if (enabled) {
    if (!/^\d{4,6}$/.test(pin)) return { ok: false, error: "الرمز يجب أن يكون 4-6 أرقام" };
    await db.insert(settings).values({ key: "pin", value: hashPin(pin) }).onConflictDoUpdate({ target: settings.key, set: { value: hashPin(pin) } });
    await db.insert(settings).values({ key: "lockEnabled", value: "1" }).onConflictDoUpdate({ target: settings.key, set: { value: "1" } });
    await audit("تعديل", "إعدادات", null, "تفعيل القفل برمز سري");
  } else {
    await db.insert(settings).values({ key: "lockEnabled", value: "0" }).onConflictDoUpdate({ target: settings.key, set: { value: "0" } });
    await audit("تعديل", "إعدادات", null, "إلغاء القفل");
  }
  revalidatePath("/", "layout");
  return { ok: true };
}

export async function verifyPin(pin: string): Promise<{ ok: boolean }> {
  const row = await db.select().from(settings).where(eq(settings.key, "pin")).then((r) => r[0]);
  return { ok: !!row && row.value === hashPin(pin) };
}

export async function getLockState(): Promise<{ enabled: boolean }> {
  const row = await db.select().from(settings).where(eq(settings.key, "lockEnabled")).then((r) => r[0]);
  return { enabled: row?.value === "1" };
}

/* ــــــــــــــــــــ الحذف الآمن الكامل ــــــــــــــــــــ */
export async function safeWipe(phrase: string): Promise<Result> {
  if (phrase !== "احذف كل شيء") return { ok: false, error: "عبارة التأكيد غير مطابقة" };
  const tables = [boqLines, boqDocuments, requirementItems, requirements, workOrders, maintenanceSchedules, tickets, links, equipment, attachments, siteHistory, sites, inventoryItems, alerts];
  for (const t of tables) await db.delete(t);
  await db.insert(alerts).values({ type: "system", message: "تم تنفيذ مسح كامل آمن لقاعدة البيانات" });
  await audit("حذف", "نظام", null, "مسح كامل آمن لقاعدة البيانات بتأكيد ثلاثي");
  revalidatePath("/", "layout");
  return { ok: true };
}

/* ــــــــــــــــــــ استيراد نسخة احتياطية ــــــــــــــــــــ */
export async function importBackup(data: unknown): Promise<Result> {
  if (!data || typeof data !== "object") return { ok: false, error: "ملف غير صالح" };
  const d = data as Record<string, unknown[]>;
  if (!Array.isArray(d.sites)) return { ok: false, error: "بنية النسخة غير صالحة" };
  // حذف بالترتيب الصحيح لقيود المفاتيح الأجنبية
  await db.delete(boqLines);
  await db.delete(boqDocuments);
  await db.delete(requirementItems);
  await db.delete(requirements);
  await db.delete(workOrders);
  await db.delete(maintenanceSchedules);
  await db.delete(tickets);
  await db.delete(links);
  await db.delete(equipment);
  await db.delete(attachments);
  await db.delete(siteHistory);
  await db.delete(sites);
  await db.delete(inventoryItems);
  await db.delete(alerts);
  if (Array.isArray(d.sites) && d.sites.length) await db.insert(sites).values(d.sites as never[]);
  if (Array.isArray(d.equipment) && d.equipment.length) await db.insert(equipment).values(d.equipment as never[]);
  if (Array.isArray(d.attachments) && d.attachments.length) await db.insert(attachments).values(d.attachments as never[]);
  if (Array.isArray(d.siteHistory) && d.siteHistory.length) await db.insert(siteHistory).values(d.siteHistory as never[]);
  if (Array.isArray(d.inventoryItems) && d.inventoryItems.length) await db.insert(inventoryItems).values(d.inventoryItems as never[]);
  if (Array.isArray(d.requirements) && d.requirements.length) await db.insert(requirements).values(d.requirements as never[]);
  if (Array.isArray(d.requirementItems) && d.requirementItems.length) await db.insert(requirementItems).values(d.requirementItems as never[]);
  if (Array.isArray(d.links) && d.links.length) await db.insert(links).values(d.links as never[]);
  if (Array.isArray(d.tickets) && d.tickets.length) await db.insert(tickets).values(d.tickets as never[]);
  if (Array.isArray(d.workOrders) && d.workOrders.length) await db.insert(workOrders).values(d.workOrders as never[]);
  if (Array.isArray(d.maintenanceSchedules) && d.maintenanceSchedules.length) await db.insert(maintenanceSchedules).values(d.maintenanceSchedules as never[]);
  if (Array.isArray(d.alerts) && d.alerts.length) await db.insert(alerts).values(d.alerts as never[]);
  await audit("استيراد", "نسخة احتياطية", null, `استيراد نسخة: ${d.sites.length} موقع`);
  revalidatePath("/", "layout");
  return { ok: true };
}
