/**
 * ⚙️ الإعدادات — أمان + تنبيهات + تدقيق + نسخ احتياطي + حذف آمن ثلاثي التأكيد
 */
"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ALERT_TYPES } from "@/lib/constants";
import { Card, Modal, Btn, Label, useToast, Toast } from "./ui";
import { setLock, markAlertRead, markAllAlertsRead, safeWipe } from "@/lib/actions";

type AuditRow = { id: number; action: string; entityType: string; entityId: number | null; at: string; details: string };
type AlertRow = { id: number; type: string; refId: number | null; message: string; createdAt: string; isRead: boolean };

const dt = (s: string) => new Date(s).toLocaleString("ar", { dateStyle: "short", timeStyle: "short" });

export function SettingsHub({ data }: { data: { audit: AuditRow[]; alerts: AlertRow[]; lock: { enabled: boolean } } }) {
  const [tab, setTab] = useState<"alerts" | "audit" | "safety">("alerts");
  const [lockModal, setLockModal] = useState(false);
  const [wipeModal, setWipeModal] = useState(false);
  const [wipeCheck, setWipeCheck] = useState(false);
  const [phrase, setPhrase] = useState("");
  const [pin, setPin] = useState("");
  const [busy, setBusy] = useState(false);
  const [importing, setImporting] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const router = useRouter();
  const { msg, show } = useToast();

  const unread = data.alerts.filter((a) => !a.isRead).length;

  const doSetLock = async (enabled: boolean) => {
    if (enabled && !/^\d{4,6}$/.test(pin)) {
      show("أدخل رمزًا من 4-6 أرقام", false);
      return;
    }
    setBusy(true);
    const res = await setLock(enabled, pin);
    setBusy(false);
    if (res.ok) {
      setLockModal(false);
      show(enabled ? "تم تفعيل القفل — سيُطلب الرمز عند كل فتح" : "تم إلغاء القفل");
      router.refresh();
    } else show(res.error, false);
  };

  const doImport = async (f: File | null) => {
    if (!f) return;
    setImporting(true);
    try {
      const text = await f.text();
      const res = await fetch("/api/backup", { method: "POST", headers: { "Content-Type": "application/json" }, body: text });
      const j = await res.json();
      if (j.ok) {
        show("تم استيراد النسخة الاحتياطية بنجاح");
        router.refresh();
      } else show(j.error ?? "ملف غير صالح", false);
    } catch {
      show("تعذر قراءة الملف", false);
    } finally {
      setImporting(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const doWipe = async () => {
    setBusy(true);
    const res = await safeWipe(phrase);
    setBusy(false);
    if (res.ok) {
      setWipeModal(false);
      show("تم المسح الكامل الآمن");
      router.refresh();
    } else show(res.error, false);
  };

  return (
    <div className="space-y-4">
      <div className="rise-in">
        <h1 className="text-xl font-extrabold sm:text-2xl">الإعدادات</h1>
        <p className="mt-1 text-sm text-dim">الأمان والتنبيهات والتدقيق والنسخ الاحتياطي</p>
      </div>

      {/* بطاقات الأمان */}
      <div className="grid gap-3 sm:grid-cols-3 rise-in">
        <Card className="p-4">
          <div className="text-sm font-extrabold">🔒 القفل برمز سري</div>
          <p className="mt-1 text-xs text-dim">{data.lock.enabled ? "مفعّل — يُطلب الرمز عند فتح النظام" : "غير مفعّل"}</p>
          <Btn variant={data.lock.enabled ? "danger" : "primary"} className="mt-3 w-full" onClick={() => { setPin(""); setLockModal(true); }}>
            {data.lock.enabled ? "إلغاء القفل" : "تفعيل القفل"}
          </Btn>
        </Card>
        <Card className="p-4">
          <div className="text-sm font-extrabold">💾 النسخ الاحتياطي</div>
          <p className="mt-1 text-xs text-dim">تصدير كامل البيانات أو استيراد نسخة سابقة</p>
          <div className="mt-3 flex gap-2">
            <a href="/api/backup" download className="flex-1"><Btn variant="ghost" className="w-full">تصدير</Btn></a>
            <Btn variant="ghost" className="flex-1" onClick={() => fileRef.current?.click()}>{importing ? "جارٍ…" : "استيراد"}</Btn>
            <input ref={fileRef} type="file" accept=".json" className="hidden" onChange={(e) => doImport(e.target.files?.[0] ?? null)} />
          </div>
        </Card>
        <Card className="border-danger/30 p-4">
          <div className="text-sm font-extrabold text-danger">🗑 الحذف الآمن الكامل</div>
          <p className="mt-1 text-xs text-dim">مسح كل البيانات بتأكيد ثلاثي — لا رجعة فيه</p>
          <Btn variant="danger" className="mt-3 w-full" onClick={() => { setWipeCheck(false); setPhrase(""); setWipeModal(true); }}>مسح كامل</Btn>
        </Card>
      </div>

      <div className="flex gap-2 rise-in">
        {([["alerts", `التنبيهات${unread ? ` (${unread} جديد)` : ""}`], ["audit", "سجل التدقيق"], ["safety", "معلومات"]] as const).map(([k, v]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-full px-4 py-2 text-xs font-bold transition sm:text-sm ${tab === k ? "bg-sky text-abyss" : "border border-edge bg-panel text-dim"}`}>
            {v}
          </button>
        ))}
      </div>

      {tab === "alerts" && (
        <Card className="p-4 fade-in">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="font-extrabold">التنبيهات ({data.alerts.length})</h3>
            {unread > 0 && <Btn variant="ghost" onClick={async () => { await markAllAlertsRead(); show("عُلّمت كلها كمقروءة"); router.refresh(); }}>تعليم الكل كمقروء</Btn>}
          </div>
          {data.alerts.length === 0 ? <p className="py-6 text-center text-sm text-dim">لا تنبيهات</p> : (
            <ul className="space-y-2">
              {data.alerts.map((a) => (
                <li key={a.id} className={`flex items-start justify-between gap-2 rounded-xl border px-3 py-2 text-sm ${a.isRead ? "border-edge text-dim" : "border-warn/30 bg-warn/5"}`}>
                  <div>
                    <span className="ml-2 rounded-full bg-panel2 px-2 py-0.5 text-[10px] font-bold text-sky">{ALERT_TYPES[a.type as keyof typeof ALERT_TYPES] ?? a.type}</span>
                    {a.message}
                    <span className="mr-2 text-[10px] text-dim">· {dt(a.createdAt)}</span>
                  </div>
                  {!a.isRead && (
                    <button onClick={async () => { await markAlertRead(a.id); router.refresh(); }} className="shrink-0 text-xs font-bold text-sky hover:underline">مقروء ✓</button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      {tab === "audit" && (
        <Card className="p-4 fade-in">
          <h3 className="mb-3 font-extrabold">سجل التدقيق — كل عملية موثقة ({data.audit.length})</h3>
          {data.audit.length === 0 ? <p className="py-6 text-center text-sm text-dim">السجل فارغ</p> : (
            <div className="max-h-[480px] space-y-1.5 overflow-y-auto pl-1">
              {data.audit.map((a) => (
                <div key={a.id} className="flex items-start gap-2 rounded-lg bg-panel2/50 px-3 py-2 text-xs">
                  <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky" />
                  <div className="min-w-0 flex-1">
                    <span className="font-bold text-sky">{a.action}</span>
                    <span className="mx-1 text-dim">· {a.entityType}{a.entityId ? ` #${a.entityId}` : ""} —</span>
                    {a.details}
                  </div>
                  <span className="shrink-0 text-[10px] text-dim">{dt(a.at)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      )}

      {tab === "safety" && (
        <Card className="p-5 text-sm leading-relaxed text-dim fade-in">
          <h3 className="mb-2 font-extrabold text-ink">قواعد الحماية المطبقة في النظام</h3>
          <ul className="list-inside list-disc space-y-1.5">
            <li>لا يُحذف موقع مرتبط بمعدات أو روابط إلا بتأكيد مضاعف، وتُعرض التبعات قبل التنفيذ.</li>
            <li>إغلاق أي تذكرة يسجل زمن الاستجابة تلقائيًا بالدقائق.</li>
            <li>صرف الاحتياج يتحقق من توفر المخزون ثم يخصمه تلقائيًا مع تسجيل التدقيق.</li>
            <li>تنبيهات تلقائية: نقص المخزون، الصيانة خلال 7 أيام، انتهاء عمر المعدات.</li>
            <li>كل إضافة وتعديل وحذف يُوثق في سجل التدقيق.</li>
            <li>الحذف الكامل يتطلب ثلاثة تأكيدات متتالية وعبارة مكتوبة.</li>
          </ul>
        </Card>
      )}

      {/* مودال القفل */}
      <Modal open={lockModal} onClose={() => setLockModal(false)} title={data.lock.enabled ? "إلغاء القفل" : "تفعيل القفل برمز سري"}>
        <div className="space-y-3">
          {!data.lock.enabled && (
            <>
              <Label>الرمز السري (4-6 أرقام)</Label>
              <input type="password" inputMode="numeric" value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 6))} className="field text-center text-xl tracking-[0.4em]" placeholder="••••" />
            </>
          )}
          {data.lock.enabled && <p className="text-sm text-dim">سيتمكن أي شخص يفتح المتصفح من الدخول مباشرة بعد الإلغاء.</p>}
          <div className="flex gap-2">
            <Btn variant="ghost" className="flex-1" onClick={() => setLockModal(false)}>إلغاء</Btn>
            <Btn className="flex-1" disabled={busy} onClick={() => doSetLock(!data.lock.enabled)}>{busy ? "جارٍ…" : "تأكيد"}</Btn>
          </div>
        </div>
      </Modal>

      {/* مودال الحذف الآمن — ثلاث مراحل */}
      <Modal open={wipeModal} onClose={() => setWipeModal(false)} title="🗑 الحذف الآمن الكامل">
        <div className="space-y-4">
          <div className="rounded-xl border border-danger/40 bg-danger/10 p-4 text-sm leading-relaxed text-danger">
            سيتم مسح <b>جميع</b> المواقع والمعدات والروابط والتذاكر والمخزون نهائيًا. هذا الإجراء لا يمكن التراجع عنه.
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={wipeCheck} onChange={(e) => setWipeCheck(e.target.checked)} className="h-4 w-4 accent-red-500" />
            التأكيد الأول: أفهم أن البيانات ستُمسح نهائيًا
          </label>
          <div>
            <Label>التأكيد الثاني والثالث: اكتب العبارة التالية بالضبط</Label>
            <input value={phrase} onChange={(e) => setPhrase(e.target.value)} className="field" placeholder="احذف كل شيء" />
            <p className="mt-1 text-[10px] text-dim">اكتب: احذف كل شيء</p>
          </div>
          <div className="flex gap-2">
            <Btn variant="ghost" className="flex-1" onClick={() => setWipeModal(false)}>تراجع</Btn>
            <Btn variant="danger" className="flex-1" disabled={!wipeCheck || phrase !== "احذف كل شيء" || busy} onClick={doWipe}>
              {busy ? "جارٍ المسح…" : "تنفيذ المسح الكامل"}
            </Btn>
          </div>
        </div>
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
