/** 📊 التقارير — جرد، حالة الشبكة، أعطال، مخزون + تصدير CSV/JSON */
import { getReportsData } from "@/lib/data";
import { SITE_STATUS, EQUIPMENT_CATEGORIES, EQUIPMENT_STATUS, LINK_TYPES, LINK_STATUS, NETWORK_CLASSES, TICKET_SEVERITY, TICKET_STATUS, STATUS_COLORS } from "@/lib/constants";
import { Card } from "@/components/ui";
import { ExportBar } from "@/components/export-bar";

function Bars({ rows }: { rows: { label: string; value: number; color: string }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <div className="space-y-2.5">
      {rows.map((r, i) => (
        <div key={r.label}>
          <div className="mb-1 flex justify-between text-xs">
            <span className="font-semibold">{r.label}</span>
            <span className="font-bold" style={{ color: r.color }}>{r.value}</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-panel2">
            <div className="bar-grow h-full rounded-full" style={{ width: `${(r.value / max) * 100}%`, backgroundColor: r.color, animationDelay: `${i * 60}ms` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export default async function ReportsPage() {
  const r = await getReportsData();
  const palette = ["#56CCF2", "#3AF08F", "#F5B841", "#FF5470", "#B78CFF", "#8E9BB3"];
  const lowStock = r.inventory.filter((i) => i.quantity <= i.minThreshold);

  return (
    <div className="space-y-5">
      <div className="rise-in">
        <h1 className="text-xl font-extrabold sm:text-2xl">التقارير</h1>
        <p className="mt-1 text-sm text-dim">مؤشرات إجمالية مع تصدير جاهز للطباعة اليدوية</p>
      </div>

      <ExportBar />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="p-5 rise-in">
          <h2 className="mb-4 font-extrabold">المواقع حسب الحالة</h2>
          <Bars rows={r.byStatus.map((x) => ({ label: SITE_STATUS[x.status as keyof typeof SITE_STATUS] ?? x.status, value: x.v, color: STATUS_COLORS[x.status] ?? "#8E9BB3" }))} />
        </Card>

        <Card className="p-5 rise-in">
          <h2 className="mb-4 font-extrabold">جرد المعدات حسب الفئة</h2>
          <Bars rows={r.eqByCategory.map((x, i) => ({ label: EQUIPMENT_CATEGORIES[x.category as keyof typeof EQUIPMENT_CATEGORIES] ?? x.category, value: x.v, color: palette[i % palette.length] }))} />
        </Card>

        <Card className="p-5 rise-in">
          <h2 className="mb-4 font-extrabold">حالة المعدات</h2>
          <Bars rows={r.eqByStatus.map((x) => ({ label: EQUIPMENT_STATUS[x.status as keyof typeof EQUIPMENT_STATUS] ?? x.status, value: x.v, color: STATUS_COLORS[x.status] ?? "#8E9BB3" }))} />
        </Card>

        <Card className="p-5 rise-in">
          <h2 className="mb-4 font-extrabold">الأعطال حسب الخطورة والحالة</h2>
          {r.ticketsBySeverity.length === 0 ? (
            <p className="py-4 text-center text-sm text-dim">لا تذاكر مسجلة</p>
          ) : (
            <Bars rows={r.ticketsBySeverity.map((x, i) => ({
              label: `${TICKET_SEVERITY[x.severity as keyof typeof TICKET_SEVERITY] ?? x.severity} — ${TICKET_STATUS[x.status as keyof typeof TICKET_STATUS] ?? x.status}`,
              value: x.v,
              color: x.severity === "critical" ? "#FF5470" : x.severity === "major" ? "#F5B841" : palette[i % palette.length],
            }))} />
          )}
        </Card>

        <Card className="p-5 rise-in lg:col-span-2">
          <h2 className="mb-4 font-extrabold">روابط المجرة حسب النوع والحالة</h2>
          {r.linksByType.length === 0 ? (
            <p className="py-4 text-center text-sm text-dim">لا روابط</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[480px] text-sm">
                <thead>
                  <tr className="border-b border-edge text-right text-xs text-dim">
                    <th className="pb-2">النوع</th><th className="pb-2">التصنيف</th><th className="pb-2">الحالة</th><th className="pb-2">العدد</th>
                  </tr>
                </thead>
                <tbody>
                  {r.linksByType.map((x, i) => (
                    <tr key={i} className="border-b border-edge/40">
                      <td className="py-2 font-semibold">{LINK_TYPES[x.type as keyof typeof LINK_TYPES] ?? x.type}</td>
                      <td className="py-2 text-dim">—</td>
                      <td className="py-2"><span style={{ color: STATUS_COLORS[x.status] ?? "#8E9BB3" }} className="font-bold">{LINK_STATUS[x.status as keyof typeof LINK_STATUS] ?? x.status}</span></td>
                      <td className="py-2 font-extrabold">{x.v}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card className={`p-5 rise-in lg:col-span-2 ${lowStock.length ? "border-danger/30" : ""}`}>
          <h2 className="mb-4 font-extrabold">تقرير المخزون {lowStock.length > 0 && <span className="text-danger">— {lowStock.length} صنف تحت الحد الأدنى</span>}</h2>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-sm">
              <thead>
                <tr className="border-b border-edge text-right text-xs text-dim">
                  <th className="pb-2">الصنف</th><th className="pb-2">الفئة</th><th className="pb-2">الكمية</th><th className="pb-2">الحد الأدنى</th><th className="pb-2">الحالة</th>
                </tr>
              </thead>
              <tbody>
                {r.inventory.map((i) => {
                  const low = i.quantity <= i.minThreshold;
                  return (
                    <tr key={i.id} className="border-b border-edge/40">
                      <td className="py-2 font-semibold">{i.name}</td>
                      <td className="py-2 text-dim">{i.category}</td>
                      <td className="py-2 font-bold" style={{ color: low ? "#FF5470" : "#3AF08F" }}>{i.quantity} {i.unit}</td>
                      <td className="py-2 text-dim">{i.minThreshold}</td>
                      <td className="py-2">{low ? <span className="rounded-full bg-danger/15 px-2 py-0.5 text-[10px] font-bold text-danger">نقص</span> : <span className="rounded-full bg-neon/10 px-2 py-0.5 text-[10px] font-bold text-neon">سليم</span>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
      <p className="text-center text-[11px] text-dim">التصنيفات المتاحة للشبكة: {Object.values(NETWORK_CLASSES).join(" · ")}</p>
    </div>
  );
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
