/**
 * 🔧 الصيانة والأعطال — تذاكر (مع قياس زمن الاستجابة) + أوامر شغل + صيانة وقائية
 */
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { TICKET_SEVERITY, TICKET_STATUS, WORK_ORDER_STATUS } from "@/lib/constants";
import { Card, Modal, Btn, Label, useToast, Toast, StatusBadge } from "./ui";
import { createTicket, setTicketStatus, createWorkOrder, setWorkOrderStatus, createSchedule, completeMaintenance } from "@/lib/actions";

type Ticket = { id: number; title: string; severity: string; status: string; openedAt: string; closedAt: string | null; resolutionTimeMinutes: number | null; siteName: string; siteId: number };
type WO = { id: number; assignedTo: string; tasks: string; status: string; scheduledAt: string; completedAt: string | null; siteName: string; siteId: number };
type Sched = { id: number; type: string; intervalDays: number; lastDone: string; nextDue: string; notes: string; siteName: string; siteId: number };
type SimpleSite = { id: number; name: string; code: string };

const SEV_COLOR: Record<string, string> = { critical: "#FF5470", major: "#F5B841", minor: "#8E9BB3" };
const ST_COLOR: Record<string, string> = { open: "#FF5470", in_progress: "#56CCF2", closed: "#3AF08F" };
const WO_COLOR: Record<string, string> = { planned: "#8E9BB3", in_progress: "#56CCF2", done: "#3AF08F" };
const dt = (s: string) => new Date(s).toLocaleDateString("ar", { dateStyle: "medium" });

function fmtMins(m: number): string {
  if (m < 60) return `${m} دقيقة`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} ساعة`;
  return `${Math.floor(h / 24)} يوم و ${h % 24} ساعة`;
}

export function MaintenanceHub({ data }: { data: { tickets: Ticket[]; workOrders: WO[]; schedules: Sched[]; sites: SimpleSite[] } }) {
  const [tab, setTab] = useState<"tix" | "wo" | "sched">("tix");
  const [modal, setModal] = useState<"ticket" | "wo" | "sched" | null>(null);
  const [busy, setBusy] = useState(false);
  const router = useRouter();
  const { msg, show } = useToast();

  const openCount = data.tickets.filter((t) => t.status !== "closed").length;
  const dueCount = data.schedules.filter((s) => new Date(s.nextDue).getTime() < Date.now() + 7 * 864e5).length;

  const run = async (fn: () => Promise<{ ok: boolean; error?: string }>, okMsg: string) => {
    setBusy(true);
    const res = await fn();
    setBusy(false);
    if (res.ok) { show(okMsg); router.refresh(); } else show(res.error ?? "خطأ", false);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rise-in">
        <div>
          <h1 className="text-xl font-extrabold sm:text-2xl">الصيانة والأعطال</h1>
          <p className="mt-1 text-sm text-dim">
            <span className={openCount > 0 ? "font-bold text-danger" : "text-neon"}>{openCount} عطل مفتوح</span> · {dueCount} صيانة مستحقة خلال أسبوع
          </p>
        </div>
        <Btn onClick={() => setModal(tab === "tix" ? "ticket" : tab === "wo" ? "wo" : "sched")}>
          + {tab === "tix" ? "تذكرة عطل" : tab === "wo" ? "أمر شغل" : "جدول وقائي"}
        </Btn>
      </div>

      <div className="flex gap-2 rise-in">
        {([["tix", `التذاكر (${data.tickets.length})`], ["wo", `أوامر الشغل (${data.workOrders.length})`], ["sched", `الصيانة الوقائية (${data.schedules.length})`]] as const).map(([k, v]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-full px-4 py-2 text-xs font-bold transition sm:text-sm ${tab === k ? "bg-sky text-abyss" : "border border-edge bg-panel text-dim"}`}>
            {v}
          </button>
        ))}
      </div>

      {tab === "tix" && (
        <div className="space-y-3 fade-in">
          {data.tickets.length === 0 ? <Card className="p-10 text-center text-dim">لا تذاكر — الشبكة مستقرة ✅</Card> : data.tickets.map((t) => (
            <Card key={t.id} className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="font-bold">{t.title}</div>
                  <div className="mt-0.5 text-xs text-dim">{t.siteName} · فُتحت {dt(t.openedAt)}</div>
                  {t.status === "closed" && t.resolutionTimeMinutes != null && (
                    <div className="mt-1 inline-block rounded-full bg-neon/10 px-2 py-0.5 text-[10px] font-bold text-neon">
                      ⏱ زمن المعالجة: {fmtMins(t.resolutionTimeMinutes)}
                    </div>
                  )}
                </div>
                <div className="flex gap-1.5">
                  <StatusBadge label={TICKET_SEVERITY[t.severity as keyof typeof TICKET_SEVERITY] ?? t.severity} color={SEV_COLOR[t.severity] ?? "#8E9BB3"} />
                  <StatusBadge label={TICKET_STATUS[t.status as keyof typeof TICKET_STATUS] ?? t.status} color={ST_COLOR[t.status] ?? "#8E9BB3"} pulse={t.status === "open" && t.severity === "critical"} />
                </div>
              </div>
              {t.status !== "closed" && (
                <div className="mt-3 flex gap-2">
                  {t.status === "open" && (
                    <Btn variant="ghost" disabled={busy} onClick={() => run(() => setTicketStatus(t.id, "in_progress"), "التذكرة قيد المعالجة")}>بدء المعالجة</Btn>
                  )}
                  <Btn variant="neon" disabled={busy} onClick={() => run(() => setTicketStatus(t.id, "closed"), "أُغلقت التذكرة وسُجل زمن المعالجة تلقائيًا")}>إغلاق التذكرة</Btn>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {tab === "wo" && (
        <div className="space-y-3 fade-in">
          {data.workOrders.length === 0 ? <Card className="p-10 text-center text-dim">لا أوامر شغل</Card> : data.workOrders.map((w) => (
            <Card key={w.id} className="p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <div className="font-bold">{w.tasks || "أمر شغل"}</div>
                  <div className="mt-0.5 text-xs text-dim">{w.siteName} · {w.assignedTo || "غير مُسند"} · {dt(w.scheduledAt)}</div>
                </div>
                <StatusBadge label={WORK_ORDER_STATUS[w.status as keyof typeof WORK_ORDER_STATUS] ?? w.status} color={WO_COLOR[w.status] ?? "#8E9BB3"} />
              </div>
              {w.status !== "done" && (
                <div className="mt-3 flex gap-2">
                  {w.status === "planned" && <Btn variant="ghost" disabled={busy} onClick={() => run(() => setWorkOrderStatus(w.id, "in_progress"), "بدأ التنفيذ")}>بدء التنفيذ</Btn>}
                  <Btn variant="neon" disabled={busy} onClick={() => run(() => setWorkOrderStatus(w.id, "done"), "أُنجز أمر الشغل")}>إنجاز</Btn>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {tab === "sched" && (
        <div className="space-y-3 fade-in">
          {data.schedules.length === 0 ? <Card className="p-10 text-center text-dim">لا جداول صيانة وقائية</Card> : data.schedules.map((s) => {
            const days = Math.ceil((new Date(s.nextDue).getTime() - Date.now()) / 864e5);
            return (
              <Card key={s.id} className={`p-4 ${days <= 0 ? "border-danger/40" : days <= 7 ? "border-warn/40" : ""}`}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <div className="font-bold">{s.type}</div>
                    <div className="mt-0.5 text-xs text-dim">{s.siteName} · كل {s.intervalDays} يوم · آخر تنفيذ {dt(s.lastDone)}</div>
                  </div>
                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${days <= 0 ? "bg-danger/15 text-danger" : days <= 7 ? "bg-warn/15 text-warn" : "bg-panel2 text-dim"}`}>
                    {days <= 0 ? "متأخرة!" : `بعد ${days} يوم`}
                  </span>
                </div>
                <div className="mt-3">
                  <Btn variant="neon" disabled={busy} onClick={() => run(() => completeMaintenance(s.id), "نُفذت الصيانة ورُحّل الموعد التالي")}>✓ نُفذت اليوم</Btn>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* مودال تذكرة */}
      <Modal open={modal === "ticket"} onClose={() => setModal(null)} title="فتح تذكرة عطل">
        <form action={async (fd) => { setBusy(true); const r = await createTicket(fd); setBusy(false); if (r.ok) { setModal(null); show("فُتحت التذكرة"); router.refresh(); } else show(r.error, false); }} className="grid gap-3">
          <div><Label>الموقع *</Label>
            <select name="siteId" required className="field"><option value="">اختر…</option>{data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          </div>
          <div><Label>عنوان العطل *</Label><input name="title" required className="field" placeholder="مثال: انقطاع تغذية الطاقة" /></div>
          <div><Label>الوصف</Label><textarea name="description" rows={2} className="field" /></div>
          <div><Label>الخطورة</Label>
            <select name="severity" className="field">{Object.entries(TICKET_SEVERITY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          </div>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الفتح…" : "فتح التذكرة"}</Btn>
        </form>
      </Modal>

      {/* مودال أمر شغل */}
      <Modal open={modal === "wo"} onClose={() => setModal(null)} title="أمر شغل جديد">
        <form action={async (fd) => { setBusy(true); const r = await createWorkOrder(fd); setBusy(false); if (r.ok) { setModal(null); show("أُنشئ أمر الشغل"); router.refresh(); } else show(r.error, false); }} className="grid gap-3">
          <div><Label>الموقع *</Label>
            <select name="siteId" required className="field"><option value="">اختر…</option>{data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          </div>
          <div><Label>المهام</Label><input name="tasks" className="field" placeholder="مثال: استبدال بطاريات وفحص الألواح" /></div>
          <div><Label>الجهة المنفذة</Label><input name="assignedTo" className="field" placeholder="فريق الصيانة أ" /></div>
          <div><Label>التاريخ المخطط</Label><input name="scheduledAt" type="date" className="field" /></div>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الإنشاء…" : "إنشاء أمر الشغل"}</Btn>
        </form>
      </Modal>

      {/* مودال جدول وقائي */}
      <Modal open={modal === "sched"} onClose={() => setModal(null)} title="جدول صيانة وقائية">
        <form action={async (fd) => { setBusy(true); const r = await createSchedule(fd); setBusy(false); if (r.ok) { setModal(null); show("أُنشئ الجدول"); router.refresh(); } else show(r.error, false); }} className="grid gap-3">
          <div><Label>الموقع *</Label>
            <select name="siteId" required className="field"><option value="">اختر…</option>{data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
          </div>
          <div><Label>نوع الصيانة</Label><input name="type" className="field" placeholder="فحص دوري شامل" /></div>
          <div><Label>الفاصل بالأيام</Label><input name="intervalDays" type="number" min={1} defaultValue={90} className="field" /></div>
          <div><Label>ملاحظات</Label><input name="notes" className="field" /></div>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الإنشاء…" : "إنشاء الجدول"}</Btn>
        </form>
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
