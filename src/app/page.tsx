/**
 * 🖥️ لوحة المؤشرات — نظرة لحظية على حالة الشبكة كاملة
 */
import Link from "next/link";
import { getDashboard } from "@/lib/data";
import { SITE_STATUS, TICKET_SEVERITY, ALERT_TYPES, STATUS_COLORS } from "@/lib/constants";
import { Card, StatusBadge } from "@/components/ui";
import { SeedPrompt } from "@/components/seed-prompt";

const fmtDate = new Intl.DateTimeFormat("ar", { day: "numeric", month: "short" });

function StatCard({ label, value, hint, tone }: { label: string; value: string | number; hint?: string; tone: string }) {
  return (
    <Card className="p-4 rise-in">
      <div className="text-xs font-bold text-dim">{label}</div>
      <div className="mt-1 text-3xl font-extrabold" style={{ color: tone }}>{value}</div>
      {hint && <div className="mt-1 text-[11px] text-dim">{hint}</div>}
    </Card>
  );
}

export default async function DashboardPage() {
  const d = await getDashboard();

  if (d.totalSites === 0) {
    return <SeedPrompt />;
  }

  const openTicketsTotal = d.openTickets.reduce((s, r) => s + r.v, 0);
  const activeLinks = d.linksByStatus.find((l) => l.status === "active")?.v ?? 0;
  const downLinks = d.linksByStatus.find((l) => l.status === "down")?.v ?? 0;
  const totalLinks = d.linksByStatus.reduce((s, r) => s + r.v, 0);
  const maxStatus = Math.max(1, ...d.byStatus.map((r) => r.v));

  return (
    <div className="space-y-6">
      <div className="rise-in">
        <h1 className="text-2xl font-extrabold">لوحة المؤشرات</h1>
        <p className="mt-1 text-sm text-dim">الحالة اللحظية لشبكة الاتصالات — {d.totalSites} موقعًا</p>
      </div>

      {/* بطاقات الأرقام */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard label="إجمالي المواقع" value={d.totalSites} hint={`${d.byStatus.find((s) => s.status === "active")?.v ?? 0} يعمل`} tone="#56CCF2" />
        <StatCard label="أعطال مفتوحة" value={openTicketsTotal} hint={openTicketsTotal > 0 ? "تتطلب متابعة" : "لا أعطال 🎉"} tone={openTicketsTotal > 0 ? "#FF5470" : "#3AF08F"} />
        <StatCard label="تنبيهات غير مقروءة" value={d.unreadAlerts} tone={d.unreadAlerts > 0 ? "#F5B841" : "#3AF08F"} />
        <StatCard label="روابط نشطة" value={`${activeLinks}/${totalLinks}`} hint={downLinks > 0 ? `${downLinks} معطل` : "الشبكة مستقرة"} tone={downLinks > 0 ? "#F5B841" : "#3AF08F"} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* توزيع حالة المواقع */}
        <Card className="p-5 rise-in">
          <h2 className="mb-4 font-extrabold">حالة المواقع</h2>
          <div className="space-y-3">
            {d.byStatus.map((r) => (
              <div key={r.status}>
                <div className="mb-1 flex items-center justify-between text-sm">
                  <span className="font-semibold">{SITE_STATUS[r.status as keyof typeof SITE_STATUS] ?? r.status}</span>
                  <span className="font-bold" style={{ color: STATUS_COLORS[r.status] }}>{r.v}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-panel2">
                  <div
                    className="bar-grow h-full rounded-full"
                    style={{ width: `${(r.v / maxStatus) * 100}%`, backgroundColor: STATUS_COLORS[r.status] ?? "#8E9BB3" }}
                  />
                </div>
              </div>
            ))}
          </div>
          <Link href="/galaxy" className="mt-5 flex items-center gap-2 rounded-xl border border-sky/30 bg-sky/5 px-4 py-3 text-sm font-bold text-sky transition hover:bg-sky/10">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-neon opacity-60" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-neon" />
            </span>
            افتح المجرة — العرض الحي للشبكة
          </Link>
        </Card>

        {/* التنبيهات الأخيرة */}
        <Card className="p-5 rise-in">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-extrabold">أحدث التنبيهات</h2>
            <Link href="/settings" className="text-xs font-bold text-sky hover:underline">عرض الكل</Link>
          </div>
          {d.recentAlerts.length === 0 ? (
            <p className="py-6 text-center text-sm text-dim">لا تنبيهات — الوضع مستقر ✅</p>
          ) : (
            <ul className="space-y-2">
              {d.recentAlerts.slice(0, 5).map((a) => (
                <li key={a.id} className={`flex items-start gap-2 rounded-xl border px-3 py-2 text-sm ${a.isRead ? "border-edge text-dim" : "border-warn/30 bg-warn/5 text-ink"}`}>
                  <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full" style={{ backgroundColor: a.isRead ? "#8E9BB3" : "#F5B841" }} />
                  <div className="min-w-0">
                    <div className="text-[10px] font-bold text-warn">{ALERT_TYPES[a.type as keyof typeof ALERT_TYPES] ?? a.type}</div>
                    {a.message}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* الأعطال الأخيرة */}
        <Card className="p-5 rise-in">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-extrabold">أحدث الأعطال</h2>
            <Link href="/maintenance" className="text-xs font-bold text-sky hover:underline">إدارة الصيانة</Link>
          </div>
          {d.recentTickets.length === 0 ? (
            <p className="py-6 text-center text-sm text-dim">لا تذاكر مفتوحة</p>
          ) : (
            <ul className="space-y-2">
              {d.recentTickets.map((t) => (
                <li key={t.id} className="flex items-center justify-between gap-2 rounded-xl border border-edge px-3 py-2 text-sm">
                  <div className="min-w-0">
                    <div className="truncate font-semibold">{t.title}</div>
                    <div className="text-[11px] text-dim">{t.siteName} · {fmtDate.format(t.openedAt)}</div>
                  </div>
                  <StatusBadge
                    label={TICKET_SEVERITY[t.severity as keyof typeof TICKET_SEVERITY] ?? t.severity}
                    color={
                      t.status === "closed" ? "#3AF08F" : t.severity === "critical" ? "#FF5470" : t.severity === "major" ? "#F5B841" : "#8E9BB3"
                    }
                  />
                </li>
              ))}
            </ul>
          )}
        </Card>

        {/* الصيانة القادمة + نقص المخزون */}
        <div className="space-y-4">
          <Card className="p-5 rise-in">
            <h2 className="mb-3 font-extrabold">صيانة وقائية قادمة</h2>
            {d.upcomingMaintenance.length === 0 ? (
              <p className="py-3 text-center text-sm text-dim">لا جداول صيانة</p>
            ) : (
              <ul className="space-y-1.5 text-sm">
                {d.upcomingMaintenance.map((m) => {
                  const days = Math.ceil((m.nextDue.getTime() - Date.now()) / 864e5);
                  return (
                    <li key={m.id} className="flex items-center justify-between gap-2 rounded-lg bg-panel2/60 px-3 py-1.5">
                      <span className="truncate">{m.type} — {m.siteName}</span>
                      <span className={`shrink-0 text-xs font-bold ${days <= 0 ? "text-danger" : days <= 7 ? "text-warn" : "text-dim"}`}>
                        {days <= 0 ? "متأخرة" : `بعد ${days} يوم`}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
          {d.lowStock.length > 0 && (
            <Card className="border-danger/30 p-5 rise-in">
              <h2 className="mb-3 font-extrabold text-danger">⚠ نقص المخزون</h2>
              <ul className="space-y-1.5 text-sm">
                {d.lowStock.slice(0, 4).map((s) => (
                  <li key={s.id} className="flex items-center justify-between rounded-lg bg-danger/5 px-3 py-1.5">
                    <span className="truncate">{s.name}</span>
                    <span className="font-bold text-danger">{s.quantity} {s.unit}</span>
                  </li>
                ))}
              </ul>
              <Link href="/requirements" className="mt-3 block text-center text-xs font-bold text-sky hover:underline">إدارة المخزون ←</Link>
            </Card>
          )}
        </div>
      </div>

      <div className="text-center">
        <Link href="/improvements.html" className="inline-flex items-center gap-2 rounded-full border border-edge bg-panel px-4 py-2 text-xs font-bold text-dim transition hover:border-sky/40 hover:text-sky">
          ✨ سجل التحسينات — قبل وبعد (عرض هاتف)
        </Link>
      </div>
    </div>
  );
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
