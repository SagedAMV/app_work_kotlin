/**
 * 🌌 محرك المجرة — عرض العقد والروابط مع أنيميشن نابض
 * • نبض متحرك على الروابط النشطة · وميض أحمر على المعطلة
 * • نقرة على عقدة → بطاقة الموقع · نقرة على رابط → التفاصيل + الحاسبة
 * • حاسبة: Link Budget · FSPL · Fresnel · LOS
 */
"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { LINK_TYPES, LINK_STATUS, NETWORK_CLASSES, SITE_STATUS, STATUS_COLORS } from "@/lib/constants";
import { fsplDb, receivedPowerDbm, fresnelRadiusM, lineOfSight, fmt } from "@/lib/galaxy-math";
import { Card, Modal, Btn, Label, useToast, Toast, StatusBadge } from "./ui";
import { createLink, setLinkStatus, deleteLink } from "@/lib/actions";

type GSite = { id: number; name: string; code: string; status: string; latitude: number; longitude: number };
type GLink = {
  id: number; sourceSiteId: number; targetSiteId: number; type: string; status: string;
  networkClass: string; priority: number; frequencyMhz: number; distanceKm: number;
  txPowerDbm: number; txGainDbi: number; rxGainDbi: number; cableLossDb: number; notes: string;
};

const W = 1000;
const H = 680;
const PAD = 70;

export function GalaxyView({ data }: { data: { sites: GSite[]; links: GLink[] } }) {
  const router = useRouter();
  const { msg, show } = useToast();
  const [filterType, setFilterType] = useState("all");
  const [filterClass, setFilterClass] = useState("all");
  const [filterStatus, setFilterStatus] = useState("all");
  const [selectedSite, setSelectedSite] = useState<GSite | null>(null);
  const [selectedLink, setSelectedLink] = useState<GLink | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [view3d, setView3d] = useState(false);
  const [busy, setBusy] = useState(false);
  const [heightA, setHeightA] = useState(30);
  const [heightB, setHeightB] = useState(30);

  /* إسقاط الإحداثيات الجغرافية على مستوى الرسم */
  const positions = useMemo(() => {
    const map = new Map<number, { x: number; y: number }>();
    if (data.sites.length === 0) return map;
    const lats = data.sites.map((s) => s.latitude);
    const lons = data.sites.map((s) => s.longitude);
    const minLat = Math.min(...lats), maxLat = Math.max(...lats);
    const minLon = Math.min(...lons), maxLon = Math.max(...lons);
    const spanLat = Math.max(maxLat - minLat, 0.0001);
    const spanLon = Math.max(maxLon - minLon, 0.0001);
    for (const s of data.sites) {
      map.set(s.id, {
        x: PAD + ((s.longitude - minLon) / spanLon) * (W - PAD * 2),
        y: H - PAD - ((s.latitude - minLat) / spanLat) * (H - PAD * 2),
      });
    }
    return map;
  }, [data.sites]);

  const visibleLinks = useMemo(
    () =>
      data.links.filter(
        (l) =>
          (filterType === "all" || l.type === filterType) &&
          (filterClass === "all" || l.networkClass === filterClass) &&
          (filterStatus === "all" || l.status === filterStatus)
      ),
    [data.links, filterType, filterClass, filterStatus]
  );

  const siteOf = (id: number) => data.sites.find((s) => s.id === id);
  const linkSiteLinks = selectedSite
    ? data.links.filter((l) => l.sourceSiteId === selectedSite.id || l.targetSiteId === selectedSite.id)
    : [];

  const submitLink = async (fd: FormData) => {
    setBusy(true);
    const res = await createLink(fd);
    setBusy(false);
    if (res.ok) {
      setAddOpen(false);
      show("تم إنشاء الرابط وحساب المسافة تلقائيًا");
      router.refresh();
    } else show(res.error, false);
  };

  const activeCount = data.links.filter((l) => l.status === "active").length;
  const downCount = data.links.filter((l) => l.status === "down").length;

  /* حاسبة الرابط المحدد */
  const calc = selectedLink
    ? receivedPowerDbm({
        txPowerDbm: selectedLink.txPowerDbm,
        txGainDbi: selectedLink.txGainDbi,
        rxGainDbi: selectedLink.rxGainDbi,
        distanceKm: selectedLink.distanceKm,
        freqMhz: selectedLink.frequencyMhz,
        cableLossDb: selectedLink.cableLossDb,
      })
    : null;
  const los = selectedLink ? lineOfSight({ distanceKm: selectedLink.distanceKm, heightA, heightB, freqMhz: selectedLink.frequencyMhz }) : null;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3 rise-in">
        <div>
          <h1 className="text-xl font-extrabold sm:text-2xl">المجرة 🌌</h1>
          <p className="mt-1 text-sm text-dim">
            {data.sites.length} موقعًا · {data.links.length} رابطًا · <span className="text-neon">{activeCount} نشط</span>
            {downCount > 0 && <span className="text-danger"> · {downCount} معطل</span>}
          </p>
        </div>
        <div className="flex gap-2">
          <Btn variant={view3d ? "primary" : "ghost"} onClick={() => setView3d(!view3d)}>
            {view3d ? "عرض 2D" : "عرض 3D"}
          </Btn>
          <Btn variant="neon" onClick={() => setAddOpen(true)}>+ إضافة رابط</Btn>
        </div>
      </div>

      {/* فلاتر */}
      <div className="flex flex-wrap gap-2 rise-in">
        <select value={filterType} onChange={(e) => setFilterType(e.target.value)} className="field !w-auto text-xs">
          <option value="all">كل الأنواع</option>
          {Object.entries(LINK_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filterClass} onChange={(e) => setFilterClass(e.target.value)} className="field !w-auto text-xs">
          <option value="all">كل التصنيفات</option>
          {Object.entries(NETWORK_CLASSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} className="field !w-auto text-xs">
          <option value="all">كل الحالات</option>
          {Object.entries(LINK_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </div>

      {/* الخريطة النجمية */}
      <Card className="overflow-hidden rise-in" >
        <div style={{ perspective: "1200px" }}>
          <svg
            viewBox={`0 0 ${W} ${H}`}
            className="w-full transition-transform duration-700"
            style={{ transform: view3d ? "rotateX(48deg) scale(1.08)" : "none", transformOrigin: "center 40%" }}
          >
            <defs>
              <radialGradient id="nodeGlow" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#56CCF2" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#56CCF2" stopOpacity="0" />
              </radialGradient>
            </defs>

            {/* الروابط */}
            {visibleLinks.map((l) => {
              const a = positions.get(l.sourceSiteId);
              const b = positions.get(l.targetSiteId);
              if (!a || !b) return null;
              const color = l.status === "active" ? "#3AF08F" : l.status === "degraded" ? "#F5B841" : "#FF5470";
              return (
                <g key={l.id} className="cursor-pointer" onClick={() => setSelectedLink(l)}>
                  <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="transparent" strokeWidth={14} />
                  <line
                    x1={a.x} y1={a.y} x2={b.x} y2={b.y}
                    stroke={color}
                    strokeWidth={selectedLink?.id === l.id ? 3 : 1.6}
                    opacity={selectedLink && selectedLink.id !== l.id ? 0.35 : 0.9}
                    className={l.status === "active" ? "edge-active" : l.status === "down" ? "edge-down" : ""}
                  />
                </g>
              );
            })}

            {/* العقد */}
            {data.sites.map((s) => {
              const p = positions.get(s.id);
              if (!p) return null;
              const color = STATUS_COLORS[s.status] ?? "#8E9BB3";
              return (
                <g key={s.id} className="cursor-pointer" onClick={() => setSelectedSite(s)}>
                  <circle cx={p.x} cy={p.y} r={26} fill="url(#nodeGlow)" />
                  {s.status === "active" && <circle cx={p.x} cy={p.y} fill="none" stroke={color} strokeWidth={1} className="node-pulse" />}
                  <circle cx={p.x} cy={p.y} r={selectedSite?.id === s.id ? 8 : 6} fill={color} stroke="#05080F" strokeWidth={2} />
                  <text x={p.x} y={p.y - 13} textAnchor="middle" fill={selectedSite?.id === s.id ? "#56CCF2" : "#8E9BB3"} fontSize={12} fontWeight={700}>
                    {s.code}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
        {/* وسيلة الإيضاح */}
        <div className="flex flex-wrap items-center gap-4 border-t border-edge px-4 py-2.5 text-[11px] text-dim">
          {Object.entries(SITE_STATUS).map(([k, v]) => (
            <span key={k} className="flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full" style={{ backgroundColor: STATUS_COLORS[k] }} /> {v}
            </span>
          ))}
          <span className="mr-auto hidden sm:inline">انقر عقدة للموقع · انقر رابطًا للتفاصيل والحسابات</span>
        </div>
      </Card>

      {/* بطاقة موقع */}
      <Modal open={!!selectedSite} onClose={() => setSelectedSite(null)} title={selectedSite ? `📍 ${selectedSite.name}` : ""}>
        {selectedSite && (
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <StatusBadge label={SITE_STATUS[selectedSite.status as keyof typeof SITE_STATUS] ?? selectedSite.status} color={STATUS_COLORS[selectedSite.status] ?? "#8E9BB3"} />
              <span className="text-xs text-dim">{selectedSite.code} · {linkSiteLinks.length} رابط</span>
            </div>
            <div className="rounded-xl bg-panel2/70 p-3 text-xs text-dim">
              📍 {selectedSite.latitude.toFixed(5)}, {selectedSite.longitude.toFixed(5)}
            </div>
            {linkSiteLinks.length > 0 && (
              <ul className="space-y-1.5 text-sm">
                {linkSiteLinks.map((l) => (
                  <li key={l.id} className="flex items-center justify-between rounded-lg bg-panel2/50 px-3 py-1.5">
                    <span>↔ {siteOf(l.sourceSiteId === selectedSite.id ? l.targetSiteId : l.sourceSiteId)?.name ?? "؟"}</span>
                    <span className="text-[11px]" style={{ color: STATUS_COLORS[l.status] }}>{LINK_STATUS[l.status as keyof typeof LINK_STATUS]}</span>
                  </li>
                ))}
              </ul>
            )}
            <Link href={`/sites/${selectedSite.id}`} className="block">
              <Btn className="w-full">فتح صفحة الموقع</Btn>
            </Link>
          </div>
        )}
      </Modal>

      {/* لوحة رابط + الحاسبات */}
      <Modal open={!!selectedLink} onClose={() => setSelectedLink(null)} title="🔗 تفاصيل الرابط" wide>
        {selectedLink && calc && los && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="font-bold">
                {siteOf(selectedLink.sourceSiteId)?.name ?? "؟"} ↔ {siteOf(selectedLink.targetSiteId)?.name ?? "؟"}
              </div>
              <StatusBadge label={LINK_STATUS[selectedLink.status as keyof typeof LINK_STATUS] ?? selectedLink.status} color={STATUS_COLORS[selectedLink.status] ?? "#8E9BB3"} pulse={selectedLink.status === "down"} />
            </div>
            <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
              <div className="rounded-lg bg-panel2/70 p-2 text-center"><div className="text-dim">النوع</div><div className="font-bold">{LINK_TYPES[selectedLink.type as keyof typeof LINK_TYPES] ?? selectedLink.type}</div></div>
              <div className="rounded-lg bg-panel2/70 p-2 text-center"><div className="text-dim">التصنيف</div><div className="font-bold">{NETWORK_CLASSES[selectedLink.networkClass as keyof typeof NETWORK_CLASSES] ?? selectedLink.networkClass}</div></div>
              <div className="rounded-lg bg-panel2/70 p-2 text-center"><div className="text-dim">المسافة</div><div className="font-bold">{fmt(selectedLink.distanceKm, 1)} كم</div></div>
              <div className="rounded-lg bg-panel2/70 p-2 text-center"><div className="text-dim">الأولوية</div><div className="font-bold">P{selectedLink.priority}</div></div>
            </div>

            <div className="rounded-xl border border-sky/25 bg-sky/5 p-4">
              <h4 className="mb-3 text-sm font-extrabold text-sky">🧮 Link Budget — التردد {selectedLink.frequencyMhz} م.هـ</h4>
              <div className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-4">
                <div className="text-center"><div className="text-dim">FSPL</div><div className="text-base font-extrabold">{fmt(calc.fspl, 1)} dB</div></div>
                <div className="text-center"><div className="text-dim">القدرة المستلمة</div><div className="text-base font-extrabold">{fmt(calc.rxPower, 1)} dBm</div></div>
                <div className="text-center"><div className="text-dim">الهامش</div><div className="text-base font-extrabold" style={{ color: calc.margin >= 10 ? "#3AF08F" : calc.margin >= 0 ? "#F5B841" : "#FF5470" }}>{fmt(calc.margin, 1)} dB</div></div>
                <div className="text-center"><div className="text-dim">فريسنل (نصف قطر)</div><div className="text-base font-extrabold">{fmt(fresnelRadiusM(selectedLink.distanceKm, selectedLink.frequencyMhz), 1)} م</div></div>
              </div>
              <p className="mt-3 text-center text-xs font-bold" style={{ color: calc.verdict === "excellent" || calc.verdict === "good" ? "#3AF08F" : calc.verdict === "weak" ? "#F5B841" : "#FF5470" }}>
                {calc.verdict === "excellent" ? "✅ وصلة ممتازة" : calc.verdict === "good" ? "👍 وصلة جيدة" : calc.verdict === "weak" ? "⚠️ وصلة ضعيفة — الهامش منخفض" : "❌ الوصلة غير قابلة للتحقيق"}
              </p>
            </div>

            <div className="rounded-xl border border-edge bg-panel2/40 p-4">
              <h4 className="mb-3 text-sm font-extrabold">📐 فحص خط البصر (LOS)</h4>
              <div className="mb-3 grid grid-cols-2 gap-2">
                <div><Label>ارتفاع الطرف الأول (م)</Label><input type="number" className="field" value={heightA} onChange={(e) => setHeightA(Number(e.target.value) || 0)} /></div>
                <div><Label>ارتفاع الطرف الثاني (م)</Label><input type="number" className="field" value={heightB} onChange={(e) => setHeightB(Number(e.target.value) || 0)} /></div>
              </div>
              <div className="grid grid-cols-3 gap-2 text-center text-xs">
                <div><div className="text-dim">انتفاخ الأرض</div><div className="font-bold">{fmt(los.earthBulgeM, 1)} م</div></div>
                <div><div className="text-dim">التخلّص المطلوب</div><div className="font-bold">{fmt(los.requiredClearanceM, 1)} م</div></div>
                <div><div className="text-dim">النتيجة</div><div className={`font-extrabold ${los.blocked ? "text-danger" : "text-neon"}`}>{los.blocked ? "مسدود ✕" : "مفتوح ✓"}</div></div>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <select defaultValue={selectedLink.status} onChange={async (e) => { await setLinkStatus(selectedLink.id, e.target.value); show("تم تحديث حالة الرابط"); setSelectedLink(null); router.refresh(); }}
                className="field !w-auto flex-1 text-xs">
                {Object.entries(LINK_STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              <Btn variant="danger" onClick={async () => { await deleteLink(selectedLink.id); show("تم حذف الرابط"); setSelectedLink(null); router.refresh(); }}>حذف الرابط</Btn>
            </div>
          </div>
        )}
      </Modal>

      {/* مودال إضافة رابط */}
      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="➕ إضافة رابط جديد">
        <form action={async (fd) => submitLink(fd)} className="grid gap-3">
          <div className="grid grid-cols-2 gap-3">
            <div><Label>من موقع *</Label>
              <select name="sourceSiteId" required className="field">
                <option value="">اختر…</option>
                {data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div><Label>إلى موقع *</Label>
              <select name="targetSiteId" required className="field">
                <option value="">اختر…</option>
                {data.sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><Label>النوع</Label>
              <select name="type" className="field">{Object.entries(LINK_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
            </div>
            <div><Label>تصنيف الشبكة</Label>
              <select name="networkClass" className="field">{Object.entries(NETWORK_CLASSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><Label>التردد (م.هـ)</Label><input name="frequencyMhz" type="number" defaultValue={5800} className="field" /></div>
            <div><Label>الأولوية (1-5)</Label><input name="priority" type="number" min={1} max={5} defaultValue={3} className="field" /></div>
          </div>
          <p className="text-[11px] text-dim">تُحسب المسافة تلقائيًا من إحداثيات الموقعين، ثم تُستخدم في حسابات الميزانية وفريسنل.</p>
          <Btn type="submit" disabled={busy}>{busy ? "جارٍ الإنشاء…" : "إنشاء الرابط"}</Btn>
        </form>
      </Modal>
      <Toast msg={msg} />
    </div>
  );
}
