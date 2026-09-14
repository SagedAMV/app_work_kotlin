/** تصدير CSV بترميز UTF-8 BOM ليدعم العربية في Excel */
import { NextResponse } from "next/server";
import { db } from "@/db";
import {
  sites,
  equipment,
  inventoryItems,
  tickets,
  links,
  requirements,
} from "@/db/schema";
import { SITE_STATUS, EQUIPMENT_CATEGORIES, LINK_TYPES, LINK_STATUS, NETWORK_CLASSES, TICKET_STATUS, TICKET_SEVERITY, REQUIREMENT_STATUS, EQUIPMENT_STATUS } from "@/lib/constants";

type Row = Record<string, unknown>;

function csv(rows: Row[], headers: [string, keyof Row][]): string {
  const esc = (v: unknown) => {
    const s = String(v ?? "");
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const head = headers.map(([h]) => h).join(",");
  const body = rows.map((r) => headers.map(([, k]) => esc(r[k])).join(",")).join("\n");
  return "\uFEFF" + head + "\n" + body;
}

async function build(kind: string): Promise<{ csvText: string; name: string } | null> {
  switch (kind) {
    case "sites": {
      const rows = await db.select().from(sites);
      return {
        name: "المواقع",
        csvText: csv(
          rows.map((r) => ({ ...r, status: SITE_STATUS[r.status as keyof typeof SITE_STATUS] ?? r.status })),
          [["الرمز", "code"], ["الاسم", "name"], ["الحالة", "status"], ["خط العرض", "latitude"], ["خط الطول", "longitude"], ["ملاحظات", "notes"]]
        ),
      };
    }
    case "equipment": {
      const rows = await db.select().from(equipment);
      return {
        name: "جرد-المعدات",
        csvText: csv(
          rows.map((r) => ({
            ...r,
            category: EQUIPMENT_CATEGORIES[r.category as keyof typeof EQUIPMENT_CATEGORIES] ?? r.category,
            status: EQUIPMENT_STATUS[r.status as keyof typeof EQUIPMENT_STATUS] ?? r.status,
          })),
          [["الموقع", "siteId"], ["الفئة", "category"], ["الشركة", "company"], ["الطراز", "model"], ["الرقم التسلسلي", "serialNumber"], ["الحالة", "status"], ["ملاحظات", "notes"]]
        ),
      };
    }
    case "inventory": {
      const rows = await db.select().from(inventoryItems);
      return {
        name: "المخزون",
        csvText: csv(rows, [["الصنف", "name"], ["الفئة", "category"], ["الكمية", "quantity"], ["الحد الأدنى", "minThreshold"], ["الوحدة", "unit"], ["الموقع", "location"]]),
      };
    }
    case "tickets": {
      const rows = await db.select().from(tickets);
      return {
        name: "الأعطال",
        csvText: csv(
          rows.map((r) => ({
            ...r,
            severity: TICKET_SEVERITY[r.severity as keyof typeof TICKET_SEVERITY] ?? r.severity,
            status: TICKET_STATUS[r.status as keyof typeof TICKET_STATUS] ?? r.status,
          })),
          [["العنوان", "title"], ["الخطورة", "severity"], ["الحالة", "status"], ["الموقع", "siteId"], ["زمن المعالجة (د)", "resolutionTimeMinutes"]]
        ),
      };
    }
    case "links": {
      const rows = await db.select().from(links);
      return {
        name: "روابط-المجرة",
        csvText: csv(
          rows.map((r) => ({
            ...r,
            type: LINK_TYPES[r.type as keyof typeof LINK_TYPES] ?? r.type,
            status: LINK_STATUS[r.status as keyof typeof LINK_STATUS] ?? r.status,
            networkClass: NETWORK_CLASSES[r.networkClass as keyof typeof NETWORK_CLASSES] ?? r.networkClass,
          })),
          [["من", "sourceSiteId"], ["إلى", "targetSiteId"], ["النوع", "type"], ["الحالة", "status"], ["التصنيف", "networkClass"], ["التردد (م.هـ)", "frequencyMhz"], ["المسافة (كم)", "distanceKm"]]
        ),
      };
    }
    case "requirements": {
      const rows = await db.select().from(requirements);
      return {
        name: "الاحتياج",
        csvText: csv(
          rows.map((r) => ({ ...r, status: REQUIREMENT_STATUS[r.status as keyof typeof REQUIREMENT_STATUS] ?? r.status })),
          [["الموقع", "siteId"], ["النوع", "type"], ["الحالة", "status"], ["ملاحظات", "notes"]]
        ),
      };
    }
    default:
      return null;
  }
}

export async function GET(_req: Request, ctx: { params: Promise<{ kind: string }> }) {
  const { kind } = await ctx.params;
  const built = await build(kind);
  if (!built) return NextResponse.json({ error: "نوع غير معروف" }, { status: 404 });
  // اسم ASCII للترويسة + اسم عربي بترميز RFC 5987
  const disposition = `attachment; filename="galaxy-${kind}.csv"; filename*=UTF-8''${encodeURIComponent(`مجرة-${built.name}.csv`)}`;
  return new NextResponse(built.csvText, {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": disposition,
    },
  });
}
