/**
 * 🌱 مولّد البيانات التجريبية — 50 موقعًا + معدات + روابط + مخزون + تذاكر
 * مولّد حتمي (نفس النتيجة في كل مرة) — يُنفذ مرة واحدة فقط إن كانت القاعدة فارغة.
 */
import { db } from "@/db";
import {
  sites,
  equipment,
  inventoryItems,
  requirements,
  requirementItems,
  links,
  tickets,
  workOrders,
  maintenanceSchedules,
  auditLog,
} from "@/db/schema";
import { sql } from "drizzle-orm";
import { haversineKm } from "./galaxy-math";
import { runAlertEngine } from "./actions";

/* مولّد عشوائي حتمي */
function rng(seed: number) {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

const NAMES = ["القمة", "النسر", "الفجر", "الشهاب", "المرقب", "الرادع", "السهم", "الصقر", "البدر", "العقاب", "المنار", "الحصن", "الطود", "الشعاع", "الظفرة", "الرائد", "الوثبة", "العزيز", "البنيان", "الذروة", "السامق", "المتين", "المنيع", "البتار", "السديد", "الرقيبة", "الخالدي", "العامرية", "الفرسان", "النهضة"];
const EQ_CATS = ["antenna", "radio", "microwave", "satellite", "switch", "router", "solar", "battery", "regulator", "charger"];
const COMPANIES = ["هواوي", "إريكسون", "موتورولا", "سيمنس", "نوكيا", "زد تي إي"];
const INV_ITEMS: [string, string, string, number, number][] = [
  ["كيبل محوري RG-213", "كوابل", "متر", 420, 100],
  ["كيبل ألياف 12 شعيرة", "كوابل", "متر", 60, 150],
  ["بطارية ليثيوم 48V", "طاقة", "قطعة", 8, 4],
  ["لوح شمسي 450W", "طاقة", "قطعة", 3, 6],
  ["منظم شحن 60A", "طاقة", "قطعة", 12, 5],
  ["وصلة ميكروويف 5.8GHz", "إرسال", "طقم", 5, 2],
  ["هوائي قطاعي 120°", "هوائيات", "قطعة", 14, 4],
  ["راديو VHF محمول", "لاسلكي", "جهاز", 22, 6],
  ["سويتش 24 منفذ", "شبكات", "قطعة", 4, 2],
  ["راوتر صناعي", "شبكات", "قطعة", 7, 3],
  ["موصل N-Type", "قطع غيار", "قطعة", 350, 120],
  ["لاقط LNB ساتلايت", "قطع غيار", "قطعة", 9, 10],
];

export async function seedIfNeeded(): Promise<{ seeded: boolean; sites: number }> {
  const [{ v }] = await db.select({ v: sql<number>`count(*)::int` }).from(sites);
  if (v > 0) return { seeded: false, sites: v };

  const rand = rng(20260214);
  const pick = <T,>(arr: T[]): T => arr[Math.floor(rand() * arr.length)];
  const N = 50;

  /* المواقع: توزيع حول منطقة عمليات واسعة */
  const centerLat = 24.65;
  const centerLon = 45.35;
  const siteRows: { id: number; lat: number; lon: number; status: string }[] = [];
  for (let i = 1; i <= N; i++) {
    const angle = (i / N) * Math.PI * 2;
    const radius = 0.25 + rand() * 1.1;
    const lat = centerLat + Math.sin(angle) * radius * 0.7 + (rand() - 0.5) * 0.2;
    const lon = centerLon + Math.cos(angle) * radius + (rand() - 0.5) * 0.2;
    const r = rand();
    const status = r < 0.68 ? "active" : r < 0.84 ? "degraded" : r < 0.94 ? "down" : "planned";
    const inserted = await db
      .insert(sites)
      .values({
        name: `موقع ${NAMES[i % NAMES.length]} ${String(Math.floor(i / NAMES.length) + 1).padStart(2, "0")}`,
        code: `ST-${String(i).padStart(2, "0")}`,
        latitude: Number(lat.toFixed(5)),
        longitude: Number(lon.toFixed(5)),
        status,
        siteType: rand() < 0.45 ? "mountain" : rand() < 0.6 ? "city" : rand() < 0.85 ? "camp" : "mobile",
        notes: rand() < 0.3 ? "موقع مرتفع يغطي القطاع الشمالي" : "",
      })
      .returning({ id: sites.id });
    siteRows.push({ id: inserted[0].id, lat, lon, status });
  }

  /* المعدات: 2-5 لكل موقع */
  for (const s of siteRows) {
    const count = 2 + Math.floor(rand() * 4);
    for (let k = 0; k < count; k++) {
      const cat = pick(EQ_CATS);
      const r = rand();
      await db.insert(equipment).values({
        siteId: s.id,
        category: cat,
        company: pick(COMPANIES),
        model: `${pick(["MX", "GT", "PR", "LN"])}-${100 + Math.floor(rand() * 900)}`,
        serialNumber: `SN${Math.floor(rand() * 9e6 + 1e6)}`,
        status: s.status === "planned" ? "working" : r < 0.82 ? "working" : r < 0.92 ? "faulty" : "maintenance",
        installDate: new Date(Date.now() - Math.floor(rand() * 11 * 365) * 864e5),
        lifespanYears: cat === "battery" ? 5 : cat === "solar" ? 15 : 10,
      });
    }
  }

  /* الروابط: حلقة رئيسية + وصلات محورية */
  const linkStatus = () => {
    const r = rand();
    return r < 0.78 ? "active" : r < 0.9 ? "degraded" : "down";
  };
  const classes = ["command", "operations", "backup", "emergency"] as const;
  for (let i = 0; i < N; i++) {
    const a = siteRows[i];
    const b = siteRows[(i + 1) % N];
    const dist = haversineKm(a.lat, a.lon, b.lat, b.lon);
    await db.insert(links).values({
      sourceSiteId: a.id,
      targetSiteId: b.id,
      type: dist > 90 ? "satellite" : dist > 40 ? "microwave" : "uhf",
      status: linkStatus(),
      networkClass: classes[Math.floor(rand() * classes.length)],
      priority: 1 + Math.floor(rand() * 5),
      frequencyMhz: pick([2400, 5800, 6000, 11000]),
      distanceKm: Number(dist.toFixed(2)),
      txPowerDbm: 20 + Math.floor(rand() * 8),
      txGainDbi: 21 + Math.floor(rand() * 8),
      rxGainDbi: 21 + Math.floor(rand() * 8),
      cableLossDb: Number((rand() * 3).toFixed(1)),
    });
    // وصلات إضافية قصيرة بين مواقع متقاربة في الترتيب
    if (i % 3 === 0) {
      const c = siteRows[(i + 7) % N];
      const d2 = haversineKm(a.lat, a.lon, c.lat, c.lon);
      if (d2 < 120) {
        await db.insert(links).values({
          sourceSiteId: a.id,
          targetSiteId: c.id,
          type: d2 > 60 ? "microwave" : pick(["fiber", "ip", "vhf"]),
          status: linkStatus(),
          networkClass: classes[Math.floor(rand() * classes.length)],
          priority: 1 + Math.floor(rand() * 5),
          frequencyMhz: pick([2400, 5800, 14500]),
          distanceKm: Number(d2.toFixed(2)),
        });
      }
    }
  }

  /* المخزون */
  const invIds: number[] = [];
  for (const [name, category, unit, quantity, minThreshold] of INV_ITEMS) {
    const row = await db.insert(inventoryItems).values({ name, category, unit, quantity, minThreshold }).returning({ id: inventoryItems.id });
    invIds.push(row[0].id);
  }

  /* الاحتياج */
  for (let i = 0; i < 6; i++) {
    const site = pick(siteRows);
    const req = await db
      .insert(requirements)
      .values({
        siteId: site.id,
        type: pick(["materials", "devices", "maintenance"]),
        status: i < 2 ? "approved" : "open",
        createdAt: new Date(Date.now() - Math.floor(rand() * 20) * 864e5),
        notes: "احتياج عاجل لدعم استمرارية الموقع",
      })
      .returning({ id: requirements.id });
    const itemCount = 1 + Math.floor(rand() * 3);
    for (let k = 0; k < itemCount; k++) {
      const invIdx = Math.floor(rand() * invIds.length);
      await db.insert(requirementItems).values({
        requirementId: req[0].id,
        inventoryItemId: invIds[invIdx],
        description: INV_ITEMS[invIdx][0],
        quantity: 1 + Math.floor(rand() * 8),
      });
    }
  }

  /* التذاكر */
  const severities = ["critical", "major", "minor"] as const;
  for (let i = 0; i < 12; i++) {
    const site = pick(siteRows.filter((s) => s.status !== "planned"));
    const r = rand();
    const status = r < 0.4 ? "open" : r < 0.65 ? "in_progress" : "closed";
    const opened = new Date(Date.now() - Math.floor(rand() * 30 + 1) * 864e5);
    await db.insert(tickets).values({
      siteId: site.id,
      title: pick(["انقطاع تغذية الطاقة", "تدهور إشارة الميكروويف", "عطل في وحدة التبريد", "إنذار بطارية منخفض", "فقدان المزامنة", "عطل لوحة شمسية"]),
      description: "بلاغ وارد من فريق المراقبة الميداني ويتطلب تدخلًا فنيًا.",
      severity: site.status === "down" ? "critical" : severities[Math.floor(rand() * 3)],
      status,
      openedAt: opened,
      closedAt: status === "closed" ? new Date(opened.getTime() + Math.floor(rand() * 600 + 60) * 60000) : null,
      resolutionTimeMinutes: status === "closed" ? Math.floor(rand() * 600 + 60) : null,
    });
  }

  /* أوامر الشغل */
  for (let i = 0; i < 7; i++) {
    const site = pick(siteRows);
    await db.insert(workOrders).values({
      siteId: site.id,
      assignedTo: pick(["فريق الصيانة أ", "فريق الصيانة ب", "مفرزة الاتصالات 3", "فريق الطاقة"]),
      tasks: pick(["استبدال بطاريات", "محاذاة هوائي الميكروويف", "فحص كيابل التغذية", "تنظيف الألواح الشمسية", "تحديث برنامج الراوتر"]),
      status: pick(["planned", "in_progress", "done"]),
      scheduledAt: new Date(Date.now() + (Math.floor(rand() * 10) - 4) * 864e5),
    });
  }

  /* جداول الصيانة الوقائية — بعضها مستحق قريبًا لتوليد تنبيهات */
  for (let i = 0; i < 14; i++) {
    const site = pick(siteRows);
    const interval = pick([30, 60, 90, 180]);
    const nextDue = new Date(Date.now() + (Math.floor(rand() * 20) - 4) * 864e5);
    await db.insert(maintenanceSchedules).values({
      siteId: site.id,
      type: pick(["فحص دوري شامل", "فحص منظومة الطاقة", "معايرة الهوائيات", "فحص التأريض", "تنظيف وتبريد"]),
      intervalDays: interval,
      lastDone: new Date(nextDue.getTime() - interval * 864e5),
      nextDue,
    });
  }

  await db.insert(auditLog).values({ action: "إنشاء", entityType: "نظام", entityId: null, details: `توليد بيانات تجريبية: ${N} موقعًا مع المعدات والروابط` });
  await runAlertEngine();
  return { seeded: true, sites: N };
}
