/** قائمة المواقع التفاعلية: بحث فوري + فلترة + حذف آمن بتأكيد مزدوج */
"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { SITE_STATUS, SITE_TYPES, STATUS_COLORS } from "@/lib/constants";
import { Card, StatusBadge, Modal, Btn, useToast, Toast } from "./ui";
import { deleteSite } from "@/lib/actions";

type Row = {
  id: number;
  name: string;
  code: string;
  status: string;
  siteType: string;
  latitude: number;
  longitude: number;
  equipmentCount: number;
};
type LinkCount = { siteId: number; active: number; down: number };

export function SitesExplorer({ rows, linkCounts }: { rows: Row[]; linkCounts: LinkCount[] }) {
  const [q, setQ] = useState("");
  const [status, setStatus] = useState("all");
  const [confirm, setConfirm] = useState<{ site: Row; step: 1 | 2; deps?: { equipment: number; links: number } } | null>(null);
  const [busy, setBusy] = useState(false);
  const { msg, show } = useToast();

  const counts = useMemo(() => new Map(linkCounts.map((c) => [c.siteId, c])), [linkCounts]);

  const filtered = useMemo(() => {
    const needle = q.trim();
    return rows.filter((r) => {
      if (status !== "all" && r.status !== status) return false;
      if (!needle) return true;
      return r.name.includes(needle) || r.code.includes(needle);
    });
  }, [rows, q, status]);

  const doDelete = async () => {
    if (!confirm) return;
    setBusy(true);
    const res = await deleteSite(confirm.site.id, confirm.step === 2);
    setBusy(false);
    if (res.ok) {
      setConfirm(null);
      show("تم حذف الموقع بأمان");
    } else if (res.error === "has-dependencies" && "equipment" in res) {
      setConfirm({ site: confirm.site, step: 2, deps: { equipment: res.equipment, links: res.links } });
    } else {
      show(res.error, false);
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row rise-in">
        <input value={q} onChange={(e) => setQ(e.target.value)} className="field sm:max-w-xs" placeholder="🔍 بحث بالاسم أو الرمز…" />
        <div className="flex gap-1.5 overflow-x-auto">
          {[["all", "الكل"], ...Object.entries(SITE_STATUS)].map(([k, v]) => (
            <button
              key={k}
              onClick={() => setStatus(k)}
              className={`shrink-0 rounded-full border px-3 py-1.5 text-xs font-bold transition ${
                status === k ? "border-sky bg-sky/10 text-sky" : "border-edge bg-panel text-dim hover:text-ink"
              }`}
            >
              {v}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 ? (
        <Card className="p-10 text-center text-dim">لا مواقع مطابقة</Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((r, i) => {
            const lc = counts.get(r.id);
            const color = STATUS_COLORS[r.status] ?? "#8E9BB3";
            return (
              <Card key={r.id} className="group relative overflow-hidden p-4 transition hover:border-sky/40 rise-in" >
                <div className="absolute inset-y-0 right-0 w-1" style={{ backgroundColor: color }} />
                <div className="flex items-start justify-between gap-2">
                  <Link href={`/sites/${r.id}`} className="min-w-0">
                    <div className="font-extrabold leading-tight group-hover:text-sky transition-colors">{r.name}</div>
                    <div className="mt-0.5 text-[11px] text-dim">{r.code} · {SITE_TYPES[r.siteType as keyof typeof SITE_TYPES] ?? r.siteType}</div>
                  </Link>
                  <StatusBadge label={SITE_STATUS[r.status as keyof typeof SITE_STATUS] ?? r.status} color={color} pulse={r.status === "down"} />
                </div>
                <div className="mt-3 flex items-center justify-between text-[11px] text-dim">
                  <span>⚙ {r.equipmentCount} معدة · 🔗 {lc ? lc.active + lc.down : 0} رابط</span>
                  <div className="flex gap-1">
                    <Link href={`/sites/${r.id}`} className="rounded-lg bg-panel2 px-2.5 py-1 font-bold text-sky transition hover:bg-sky/10">التفاصيل</Link>
                    <button onClick={() => setConfirm({ site: r, step: 1 })} className="rounded-lg bg-panel2 px-2.5 py-1 font-bold text-danger transition hover:bg-danger/10">حذف</button>
                  </div>
                </div>
                <div className="mt-2 h-1 overflow-hidden rounded-full bg-panel2">
                  <div className="bar-grow h-full rounded-full" style={{ width: `${((i % 5) + 2) * 20}%`, backgroundColor: `${color}66`, animationDelay: `${i * 40}ms` }} />
                </div>
              </Card>
            );
          })}
        </div>
      )}

      <Modal open={!!confirm} onClose={() => setConfirm(null)} title={confirm?.step === 2 ? "⚠ تأكيد نهائي" : "حذف الموقع"}>
        {confirm && (
          <div className="space-y-4">
            {confirm.step === 1 ? (
              <p className="text-sm leading-relaxed text-dim">
                هل تريد حذف <span className="font-bold text-ink">{confirm.site.name}</span>؟
                <br />التحقق من التبعات يتم تلقائيًا قبل الحذف.
              </p>
            ) : (
              <div className="rounded-xl border border-danger/40 bg-danger/10 p-4 text-sm leading-relaxed">
                <p className="font-extrabold text-danger">الموقع مرتبط ببيانات أخرى:</p>
                <p className="mt-1 text-ink">{confirm.deps?.equipment ?? 0} معدة · {confirm.deps?.links ?? 0} رابط</p>
                <p className="mt-2 text-xs text-dim">الحذف الآن سيزيل كل هذه البيانات نهائيًا. هل أنت متأكد تمامًا؟</p>
              </div>
            )}
            <div className="flex gap-2">
              <Btn variant="ghost" onClick={() => (confirm.step === 2 ? setConfirm(null) : setConfirm(null))} className="flex-1">إلغاء</Btn>
              <Btn variant="danger" onClick={doDelete} disabled={busy} className="flex-1">{busy ? "جارٍ…" : confirm.step === 2 ? "حذف نهائي" : "متابعة"}</Btn>
            </div>
          </div>
        )}
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
