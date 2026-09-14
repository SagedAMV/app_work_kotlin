/**
 * 📦 الاحتياج + المخزون — مقارنة تلقائية مع المخزون، صرف بخصم تلقائي، توليد BOQ
 */
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { REQUIREMENT_TYPES, REQUIREMENT_STATUS } from "@/lib/constants";
import { Card, Modal, Btn, Label, useToast, Toast, StatusBadge } from "./ui";
import { createRequirement, setRequirementStatus, generateBoq, saveInventoryItem, deleteInventoryItem } from "@/lib/actions";

type Inv = { id: number; name: string; category: string; unit: string; quantity: number; minThreshold: number; location: string };
type ReqItem = { id: number; inventoryItemId: number | null; description: string; quantity: number; fulfilled: boolean };
type Req = { id: number; type: string; status: string; createdAt: string; notes: string; siteName: string; siteId: number; items: ReqItem[] };
type SimpleSite = { id: number; name: string; code: string };

const REQ_COLORS: Record<string, string> = { open: "#56CCF2", approved: "#F5B841", fulfilled: "#3AF08F", cancelled: "#8E9BB3" };
const dt = (s: string) => new Date(s).toLocaleDateString("ar", { dateStyle: "medium" });

export function RequirementsHub({ data }: { data: { reqs: Req[]; inventory: Inv[]; sites: SimpleSite[] } }) {
  const [tab, setTab] = useState<"reqs" | "inv">("reqs");
  const [newReq, setNewReq] = useState(false);
  const [invEdit, setInvEdit] = useState<Inv | null>(null);
  const [busy, setBusy] = useState(false);
  const router = useRouter();
  const { msg, show } = useToast();

  /* بنود الاحتياج قيد الإنشاء */
  const [draftItems, setDraftItems] = useState<{ inventoryItemId: number | null; description: string; quantity: number }[]>([
    { inventoryItemId: null, description: "", quantity: 1 },
  ]);

  const invOf = (id: number | null) => data.inventory.find((i) => i.id === id);
  const lowCount = data.inventory.filter((i) => i.quantity <= i.minThreshold).length;

  const submitReq = async (fd: FormData) => {
    const valid = draftItems.filter((i) => i.description.trim() && i.quantity > 0);
    if (valid.length === 0) {
      show("أكمل بندًا واحدًا على الأقل", false);
      return;
    }
    fd.set("items", JSON.stringify(valid));
    setBusy(true);
    const res = await createRequirement(fd);
    setBusy(false);
    if (res.ok) {
      setNewReq(false);
      setDraftItems([{ inventoryItemId: null, description: "", quantity: 1 }]);
      show("تم إنشاء الاحتياج");
      router.refresh();
    } else show(res.error, false);
  };

  const act = async (fn: () => Promise<{ ok: boolean; error?: string }>, okMsg: string) => {
    setBusy(true);
    const res = await fn();
    setBusy(false);
    if (res.ok) {
      show(okMsg);
      router.refresh();
    } else show(res.error ?? "خطأ", false);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rise-in">
        <div>
          <h1 className="text-xl font-extrabold sm:text-2xl">الاحتياج والمخزون</h1>
          <p className="mt-1 text-sm text-dim">
            {data.reqs.length} احتياج · {data.inventory.length} صنف مخزون
            {lowCount > 0 && <span className="font-bold text-danger"> · {lowCount} تحت الحد الأدنى</span>}
          </p>
        </div>
        <Btn onClick={() => (tab === "reqs" ? setNewReq(true) : setInvEdit({ id: 0, name: "", category: "مواد", unit: "قطعة", quantity: 0, minThreshold: 1, location: "المخزن المركزي" }))}>
          + {tab === "reqs" ? "احتياج جديد" : "صنف جديد"}
        </Btn>
      </div>

      <div className="flex gap-2 rise-in">
        {([["reqs", "الاحتياج"], ["inv", "المخزون"]] as const).map(([k, v]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-full px-5 py-2 text-sm font-bold transition ${tab === k ? "bg-sky text-abyss" : "border border-edge bg-panel text-dim"}`}>
            {v}
          </button>
        ))}
      </div>

      {tab === "reqs" && (
        <div className="space-y-3 fade-in">
          {data.reqs.length === 0 ? (
            <Card className="p-10 text-center text-dim">لا احتياجات مسجلة</Card>
          ) : (
            data.reqs.map((r) => (
              <Card key={r.id} className="p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <div className="font-bold">{r.siteName} <span className="text-xs font-normal text-dim">· {REQUIREMENT_TYPES[r.type as keyof typeof REQUIREMENT_TYPES] ?? r.type} · {dt(r.createdAt)}</span></div>
                    {r.notes && <p className="mt-0.5 text-xs text-dim">{r.notes}</p>}
                  </div>
                  <StatusBadge label={REQUIREMENT_STATUS[r.status as keyof typeof REQUIREMENT_STATUS] ?? r.status} color={REQ_COLORS[r.status] ?? "#8E9BB3"} />
                </div>
                <ul className="mt-3 space-y-1">
                  {r.items.map((it) => {
                    const inv = invOf(it.inventoryItemId);
                    return (
                      <li key={it.id} className="flex items-center justify-between rounded-lg bg-panel2/50 px-3 py-1.5 text-sm">
                        <span className={it.fulfilled ? "text-dim line-through" : ""}>
                          {it.fulfilled && "✓ "}{it.description}
                          {inv && <span className="mr-2 text-[10px] text-dim">(متوفر: {inv.quantity} {inv.unit})</span>}
                        </span>
                        <span className="font-bold">× {it.quantity}</span>
                      </li>
                    );
                  })}
                </ul>
                <div className="mt-3 flex flex-wrap gap-2">
                  {r.status === "open" && (
                    <Btn variant="ghost" disabled={busy} onClick={() => act(() => setRequirementStatus(r.id, "approved"), "تم اعتماد الاحتياج")}>اعتماد</Btn>
                  )}
                  {(r.status === "open" || r.status === "approved") && (
                    <Btn variant="neon" disabled={busy} onClick={() => act(() => setRequirementStatus(r.id, "fulfilled"), "تم الصرف وخصم المخزون تلقائيًا")}>صرف (خصم من المخزون)</Btn>
                  )}
                  {r.status !== "cancelled" && r.status !== "fulfilled" && (
                    <Btn variant="danger" disabled={busy} onClick={() => act(() => setRequirementStatus(r.id, "cancelled"), "تم إلغاء الاحتياج")}>إلغاء</Btn>
                  )}
                  <Btn variant="ghost" disabled={busy} onClick={() => act(() => generateBoq(r.id), "تم توليد BOQ من بنود الاحتياج")}>توليد BOQ</Btn>
                </div>
              </Card>
            ))
          )}
        </div>
      )}

      {tab === "inv" && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 fade-in">
          {data.inventory.map((i) => {
            const low = i.quantity <= i.minThreshold;
            return (
              <Card key={i.id} className={`p-4 ${low ? "border-danger/40" : ""}`}>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="font-bold">{i.name}</div>
                    <div className="text-[11px] text-dim">{i.category} · {i.location}</div>
                  </div>
                  {low && <span className="rounded-full bg-danger/15 px-2 py-0.5 text-[10px] font-bold text-danger">نقص</span>}
                </div>
                <div className="mt-3 flex items-end justify-between">
                  <div>
                    <span className="text-2xl font-extrabold" style={{ color: low ? "#FF5470" : "#3AF08F" }}>{i.quantity}</span>
                    <span className="mr-1 text-xs text-dim">{i.unit}</span>
                  </div>
                  <span className="text-[11px] text-dim">الحد الأدنى: {i.minThreshold}</span>
                </div>
                <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-panel2">
                  <div className="bar-grow h-full rounded-full" style={{ width: `${Math.min(100, (i.quantity / Math.max(i.minThreshold * 3, 1)) * 100)}%`, backgroundColor: low ? "#FF5470" : "#3AF08F" }} />
                </div>
                <div className="mt-3 flex gap-2">
                  <Btn variant="ghost" className="flex-1 !py-1.5 text-xs" onClick={() => setInvEdit(i)}>تعديل</Btn>
                  <Btn variant="danger" className="!py-1.5 text-xs" onClick={() => act(() => deleteInventoryItem(i.id), "تم حذف الصنف")}>حذف</Btn>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* مودال احتياج جديد */}
      <Modal open={newReq} onClose={() => setNewReq(false)} title="احتياج جديد" wide>
        <form action={async (fd) => submitReq(fd)} className="grid gap-3">
          <div className="grid grid-cols-2 gap-3">
            <div><Label>الموقع *</Label>
              <select name="siteId" required className="field">
                <option value="">اختر…</option>
                {data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div><Label>النوع</Label>
              <select name="type" className="field">{Object.entries(REQUIREMENT_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
            </div>
          </div>
          <div><Label>ملاحظات</Label><input name="notes" className="field" placeholder="سبب الاحتياج…" /></div>
          <div>
            <Label>البنود</Label>
            <div className="space-y-2">
              {draftItems.map((it, idx) => (
                <div key={idx} className="grid grid-cols-12 gap-2">
                  <select
                    className="field col-span-4 text-xs"
                    value={it.inventoryItemId ?? ""}
                    onChange={(e) => {
                      const inv = data.inventory.find((x) => x.id === Number(e.target.value));
                      setDraftItems((prev) => prev.map((p, i2) => (i2 === idx ? { ...p, inventoryItemId: inv ? inv.id : null, description: inv ? inv.name : p.description } : p)));
                    }}>
                    <option value="">بند حر…</option>
                    {data.inventory.map((x) => <option key={x.id} value={x.id}>{x.name} (متوفر {x.quantity})</option>)}
                  </select>
                  <input className="field col-span-5" placeholder="الوصف" value={it.description}
                    onChange={(e) => setDraftItems((prev) => prev.map((p, i2) => (i2 === idx ? { ...p, description: e.target.value } : p)))} />
                  <input className="field col-span-2" type="number" min={1} value={it.quantity}
                    onChange={(e) => setDraftItems((prev) => prev.map((p, i2) => (i2 === idx ? { ...p, quantity: Number(e.target.value) || 1 } : p)))} />
                  <button type="button" onClick={() => setDraftItems((prev) => prev.filter((_, i2) => i2 !== idx))}
                    className="col-span-1 rounded-lg bg-danger/10 font-bold text-danger" aria-label="حذف البند">✕</button>
                </div>
              ))}
            </div>
            <button type="button" onClick={() => setDraftItems((p) => [...p, { inventoryItemId: null, description: "", quantity: 1 }])}
              className="mt-2 text-xs font-bold text-sky hover:underline">+ إضافة بند</button>
          </div>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الإنشاء…" : "إنشاء الاحتياج"}</Btn>
        </form>
      </Modal>

      {/* مودال صنف مخزون */}
      <Modal open={!!invEdit} onClose={() => setInvEdit(null)} title={invEdit?.id ? "تعديل صنف" : "صنف جديد"}>
        {invEdit && (
          <form action={async (fd) => { setBusy(true); const res = await saveInventoryItem(fd); setBusy(false); if (res.ok) { setInvEdit(null); show("تم الحفظ"); router.refresh(); } else show(res.error, false); }}
            className="grid gap-3">
            <input type="hidden" name="id" value={invEdit.id} />
            <div><Label>اسم الصنف *</Label><input name="name" required defaultValue={invEdit.name} className="field" /></div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>الفئة</Label><input name="category" defaultValue={invEdit.category} className="field" /></div>
              <div><Label>الوحدة</Label><input name="unit" defaultValue={invEdit.unit} className="field" /></div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div><Label>الكمية</Label><input name="quantity" type="number" min={0} defaultValue={invEdit.quantity} className="field" /></div>
              <div><Label>الحد الأدنى</Label><input name="minThreshold" type="number" min={0} defaultValue={invEdit.minThreshold} className="field" /></div>
            </div>
            <div><Label>الموقع التخزيني</Label><input name="location" defaultValue={invEdit.location} className="field" /></div>
            <Btn type="submit" disabled={busy}>{busy ? "جارٍ الحفظ…" : "حفظ"}</Btn>
          </form>
        )}
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
