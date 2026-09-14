/**
 * 🏔️ تفاصيل الموقع — تبويبات: نظرة عامة، المعدات، المرفقات، الروابط، السجل، التذاكر
 */
"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  SITE_STATUS, SITE_TYPES, EQUIPMENT_CATEGORIES, EQUIPMENT_STATUS,
  LINK_TYPES, LINK_STATUS, NETWORK_CLASSES, TICKET_SEVERITY, TICKET_STATUS, STATUS_COLORS,
} from "@/lib/constants";
import { Card, StatusBadge, Modal, Btn, Label, useToast, Toast } from "./ui";
import { addEquipment, setEquipmentStatus, deleteEquipment, addAttachment, deleteAttachment } from "@/lib/actions";

type Full = {
  site: { id: number; name: string; code: string; latitude: number; longitude: number; status: string; siteType: string; notes: string; createdAt: string; updatedAt: string };
  equipment: { id: number; category: string; company: string; model: string; serialNumber: string; status: string; installDate: string; lifespanYears: number; notes: string }[];
  attachments: { id: number; type: string; dataUrl: string; caption: string; addedAt: string }[];
  history: { id: number; action: string; oldValue: string; newValue: string; at: string }[];
  tickets: { id: number; title: string; severity: string; status: string; openedAt: string }[];
  schedules: { id: number; type: string; nextDue: string }[];
  links: { id: number; type: string; status: string; networkClass: string; distanceKm: number; frequencyMhz: number; otherSiteName: string; sourceSiteId: number; targetSiteId: number }[];
};

const TABS = ["نظرة عامة", "المعدات", "المرفقات", "الروابط", "السجل", "التذاكر"];
const dt = (s: string) => new Date(s).toLocaleString("ar", { dateStyle: "medium", timeStyle: "short" });
const dOnly = (s: string) => new Date(s).toLocaleDateString("ar", { dateStyle: "medium" });

export function SiteDetail({ data }: { data: Full }) {
  const [tab, setTab] = useState(0);
  const [eqModal, setEqModal] = useState(false);
  const [attModal, setAttModal] = useState(false);
  const [busy, setBusy] = useState(false);
  const [attPreview, setAttPreview] = useState<string>("");
  const fileRef = useRef<HTMLInputElement>(null);
  const router = useRouter();
  const { msg, show } = useToast();
  const s = data.site;
  const color = STATUS_COLORS[s.status] ?? "#8E9BB3";

  const saveEquipment = async (fd: FormData) => {
    setBusy(true);
    const res = await addEquipment(fd);
    setBusy(false);
    if (res.ok) {
      setEqModal(false);
      show("تمت إضافة المعدة");
      router.refresh();
    } else show(res.error, false);
  };

  const saveAttachment = async (fd: FormData) => {
    fd.set("siteId", String(s.id));
    fd.set("dataUrl", attPreview);
    setBusy(true);
    const res = await addAttachment(fd);
    setBusy(false);
    if (res.ok) {
      setAttModal(false);
      setAttPreview("");
      show("تم رفع المرفق");
      router.refresh();
    } else show(res.error, false);
  };

  const pickFile = (f: File | null) => {
    if (!f) return;
    if (f.size > 2_500_000) {
      show("حجم الملف أكبر من 2.5 م.ب", false);
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setAttPreview(String(reader.result));
    reader.readAsDataURL(f);
  };

  return (
    <div className="space-y-4">
      {/* الترويسة */}
      <Card className="relative overflow-hidden p-5 rise-in">
        <div className="absolute inset-x-0 top-0 h-1" style={{ backgroundColor: color }} />
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-extrabold sm:text-2xl">{s.name}</h1>
              <StatusBadge label={SITE_STATUS[s.status as keyof typeof SITE_STATUS] ?? s.status} color={color} pulse={s.status === "down"} />
            </div>
            <p className="mt-1 text-sm text-dim">
              {s.code} · {SITE_TYPES[s.siteType as keyof typeof SITE_TYPES] ?? s.siteType} · 📍 {s.latitude.toFixed(4)}, {s.longitude.toFixed(4)}
            </p>
            {s.notes && <p className="mt-2 max-w-xl text-sm leading-relaxed text-dim">{s.notes}</p>}
          </div>
          <Link href={`/sites/${s.id}/edit`}>
            <Btn variant="ghost">✏️ تعديل</Btn>
          </Link>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-2 text-center text-xs sm:max-w-md">
          <div className="rounded-xl bg-panel2/70 p-2"><div className="text-lg font-extrabold text-sky">{data.equipment.length}</div>معدة</div>
          <div className="rounded-xl bg-panel2/70 p-2"><div className="text-lg font-extrabold text-neon">{data.links.length}</div>رابط</div>
          <div className="rounded-xl bg-panel2/70 p-2"><div className="text-lg font-extrabold text-warn">{data.tickets.filter((t) => t.status !== "closed").length}</div>عطل مفتوح</div>
        </div>
      </Card>

      {/* التبويبات */}
      <div className="flex gap-1.5 overflow-x-auto pb-1 rise-in">
        {TABS.map((t, i) => (
          <button key={t} onClick={() => setTab(i)}
            className={`shrink-0 rounded-full px-4 py-1.5 text-xs font-bold transition ${tab === i ? "bg-sky text-abyss" : "border border-edge bg-panel text-dim hover:text-ink"}`}>
            {t}
          </button>
        ))}
      </div>

      {/* نظرة عامة */}
      {tab === 0 && (
        <div className="grid gap-4 sm:grid-cols-2 fade-in">
          <Card className="p-5">
            <h3 className="mb-3 font-extrabold">بيانات الموقع</h3>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-dim">أُنشئ في</dt><dd>{dt(s.createdAt)}</dd></div>
              <div className="flex justify-between"><dt className="text-dim">آخر تحديث</dt><dd>{dt(s.updatedAt)}</dd></div>
              <div className="flex justify-between"><dt className="text-dim">جداول الصيانة</dt><dd>{data.schedules.length}</dd></div>
              <div className="flex justify-between"><dt className="text-dim">أقرب صيانة</dt>
                <dd>{data.schedules.length ? dOnly([...data.schedules].sort((a, b) => a.nextDue.localeCompare(b.nextDue))[0].nextDue) : "—"}</dd>
              </div>
            </dl>
          </Card>
          <Card className="p-5">
            <h3 className="mb-3 font-extrabold">حالة المعدات</h3>
            {data.equipment.length === 0 ? <p className="text-sm text-dim">لا معدات بعد</p> : (
              <div className="space-y-1.5">
                {Object.entries(EQUIPMENT_STATUS).map(([k, v]) => {
                  const c = data.equipment.filter((e) => e.status === k).length;
                  if (c === 0) return null;
                  return (
                    <div key={k} className="flex items-center justify-between rounded-lg bg-panel2/60 px-3 py-1.5 text-sm">
                      <span>{v}</span>
                      <span className="font-bold" style={{ color: STATUS_COLORS[k] }}>{c}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </div>
      )}

      {/* المعدات */}
      {tab === 1 && (
        <div className="space-y-3 fade-in">
          <div className="flex justify-end">
            <Btn onClick={() => setEqModal(true)}>+ إضافة معدة</Btn>
          </div>
          {data.equipment.length === 0 ? (
            <Card className="p-8 text-center text-dim">لا معدات مسجلة</Card>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {data.equipment.map((e) => (
                <Card key={e.id} className="p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="font-bold">{EQUIPMENT_CATEGORIES[e.category as keyof typeof EQUIPMENT_CATEGORIES] ?? e.category}</div>
                      <div className="text-xs text-dim">{e.company} {e.model} · SN {e.serialNumber || "—"}</div>
                      <div className="mt-1 text-[11px] text-dim">تركيب: {dOnly(e.installDate)} · عمر افتراضي {e.lifespanYears} سنة</div>
                    </div>
                    <StatusBadge label={EQUIPMENT_STATUS[e.status as keyof typeof EQUIPMENT_STATUS] ?? e.status} color={STATUS_COLORS[e.status] ?? "#8E9BB3"} />
                  </div>
                  <div className="mt-3 flex items-center gap-2">
                    <select value={e.status} onChange={async (ev) => { await setEquipmentStatus(e.id, ev.target.value); show("تم تحديث حالة المعدة"); router.refresh(); }}
                      className="field !w-auto flex-1 !py-1.5 text-xs">
                      {Object.entries(EQUIPMENT_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </select>
                    <button onClick={async () => { await deleteEquipment(e.id); show("تم حذف المعدة"); router.refresh(); }}
                      className="rounded-lg bg-danger/10 px-2.5 py-1.5 text-xs font-bold text-danger">حذف</button>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* المرفقات */}
      {tab === 2 && (
        <div className="space-y-3 fade-in">
          <div className="flex justify-end">
            <Btn onClick={() => setAttModal(true)}>+ رفع صورة / PDF</Btn>
          </div>
          {data.attachments.length === 0 ? (
            <Card className="p-8 text-center text-dim">لا مرفقات — ارفع صور الموقع أو مخططات PDF</Card>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {data.attachments.map((a) => (
                <Card key={a.id} className="overflow-hidden">
                  {a.type === "image" ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={a.dataUrl} alt={a.caption || "مرفق"} className="h-28 w-full object-cover" />
                  ) : (
                    <a href={a.dataUrl} download className="flex h-28 items-center justify-center bg-panel2 text-3xl">📄</a>
                  )}
                  <div className="flex items-center justify-between gap-1 p-2 text-[11px]">
                    <span className="truncate">{a.caption || (a.type === "image" ? "صورة" : "PDF")}</span>
                    <button onClick={async () => { await deleteAttachment(a.id); show("تم حذف المرفق"); router.refresh(); }} className="shrink-0 font-bold text-danger">✕</button>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* الروابط */}
      {tab === 3 && (
        <div className="fade-in">
          {data.links.length === 0 ? (
            <Card className="p-8 text-center text-dim">لا روابط — أضفها من شاشة المجرة</Card>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {data.links.map((l) => (
                <Card key={l.id} className="p-4">
                  <div className="flex items-center justify-between">
                    <div className="font-bold">↔ {l.otherSiteName}</div>
                    <StatusBadge label={LINK_STATUS[l.status as keyof typeof LINK_STATUS] ?? l.status} color={STATUS_COLORS[l.status] ?? "#8E9BB3"} pulse={l.status === "down"} />
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-1 text-xs text-dim">
                    <span>النوع: {LINK_TYPES[l.type as keyof typeof LINK_TYPES] ?? l.type}</span>
                    <span>التصنيف: {NETWORK_CLASSES[l.networkClass as keyof typeof NETWORK_CLASSES] ?? l.networkClass}</span>
                    <span>المسافة: {l.distanceKm} كم</span>
                    <span>التردد: {l.frequencyMhz} م.هـ</span>
                  </div>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* السجل */}
      {tab === 4 && (
        <Card className="p-5 fade-in">
          {data.history.length === 0 ? <p className="text-center text-sm text-dim">لا أحداث</p> : (
            <ol className="relative space-y-4 border-r border-edge pr-5">
              {data.history.map((h) => (
                <li key={h.id} className="relative">
                  <span className="absolute -right-[27px] top-1 h-2.5 w-2.5 rounded-full bg-sky" />
                  <div className="text-sm font-bold">{h.action}</div>
                  <div className="text-xs text-dim">
                    {h.oldValue && <span className="line-through">{h.oldValue} ← </span>}
                    {h.newValue} · {dt(h.at)}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </Card>
      )}

      {/* التذاكر */}
      {tab === 5 && (
        <div className="space-y-3 fade-in">
          <div className="flex justify-end">
            <Link href="/maintenance"><Btn variant="ghost">فتح تذكرة من قسم الصيانة</Btn></Link>
          </div>
          {data.tickets.length === 0 ? (
            <Card className="p-8 text-center text-dim">لا تذاكر لهذا الموقع</Card>
          ) : (
            data.tickets.map((t) => (
              <Card key={t.id} className="flex items-center justify-between gap-2 p-4">
                <div>
                  <div className="text-sm font-bold">{t.title}</div>
                  <div className="text-[11px] text-dim">{dt(t.openedAt)}</div>
                </div>
                <div className="flex gap-1.5">
                  <StatusBadge label={TICKET_SEVERITY[t.severity as keyof typeof TICKET_SEVERITY] ?? t.severity} color={t.severity === "critical" ? "#FF5470" : t.severity === "major" ? "#F5B841" : "#8E9BB3"} />
                  <StatusBadge label={TICKET_STATUS[t.status as keyof typeof TICKET_STATUS] ?? t.status} color={t.status === "closed" ? "#3AF08F" : t.status === "in_progress" ? "#56CCF2" : "#FF5470"} />
                </div>
              </Card>
            ))
          )}
        </div>
      )}

      {/* مودال إضافة معدة */}
      <Modal open={eqModal} onClose={() => setEqModal(false)} title="إضافة معدة">
        <form action={async (fd) => saveEquipment(fd)} className="grid gap-3">
          <input type="hidden" name="siteId" value={s.id} />
          <div className="grid grid-cols-2 gap-3">
            <div><Label>الفئة *</Label>
              <select name="category" className="field">{Object.entries(EQUIPMENT_CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
            </div>
            <div><Label>الحالة</Label>
              <select name="status" className="field">{Object.entries(EQUIPMENT_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><Label>الشركة</Label><input name="company" className="field" placeholder="هواوي" /></div>
            <div><Label>الطراز</Label><input name="model" className="field" placeholder="MX-500" /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><Label>الرقم التسلسلي</Label><input name="serialNumber" className="field" /></div>
            <div><Label>العمر الافتراضي (سنة)</Label><input name="lifespanYears" type="number" min={1} max={40} defaultValue={10} className="field" /></div>
          </div>
          <div><Label>ملاحظات</Label><input name="notes" className="field" /></div>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الحفظ…" : "إضافة المعدة"}</Btn>
        </form>
      </Modal>

      {/* مودال رفع مرفق */}
      <Modal open={attModal} onClose={() => { setAttModal(false); setAttPreview(""); }} title="رفع صورة أو PDF">
        <form action={async (fd) => saveAttachment(fd)} className="grid gap-3">
          <input ref={fileRef} type="file" accept="image/*,application/pdf" onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
            className="field file:ml-3 file:rounded-lg file:border-0 file:bg-sky file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-abyss" />
          {attPreview && attPreview.startsWith("data:image") && (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={attPreview} alt="معاينة" className="max-h-40 w-full rounded-xl object-cover" />
          )}
          {attPreview && attPreview.startsWith("data:application/pdf") && <p className="text-sm text-neon">📄 ملف PDF جاهز للرفع</p>}
          <div><Label>وصف المرفق</Label><input name="caption" className="field" placeholder="مثال: مخطط الموقع الشمالي" /></div>
          <Btn type="submit" disabled={busy || !attPreview}>{busy ? "جارٍ الرفع…" : "حفظ المرفق"}</Btn>
        </form>
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
