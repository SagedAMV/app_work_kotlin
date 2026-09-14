/**
 * 🧮 محرك حسابات المجرة
 * Link Budget + FSPL + Fresnel Zone + Line of Sight + Haversine
 * كل دالة تفحص مدخلاتها قبل الحساب (لا ثقة بأي مدخل).
 */

/** فحص رقم صالح ومحدود — يعيد القيمة أو يبطلها */
function num(v: unknown, fallback: number): number {
  const n = typeof v === "string" ? parseFloat(v) : typeof v === "number" ? v : NaN;
  return Number.isFinite(n) ? n : fallback;
}

/** المسافة بين إحداثيين (كم) — صيغة هافرساين */
export function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** فقد المسار الحر: FSPL(dB) = 20log10(d) + 20log10(f) + 32.44 — d كم، f ميجاهرتز */
export function fsplDb(distanceKm: number, freqMhz: number): number {
  const d = Math.max(num(distanceKm, 0), 0.01);
  const f = Math.max(num(freqMhz, 1), 0.001);
  return 20 * Math.log10(d) + 20 * Math.log10(f) + 32.44;
}

/** القدرة المستلمة = قدرة الإرسال + كسب المرسل + كسب المستقبل − الفقد − فقد الكيابل */
export function receivedPowerDbm(p: {
  txPowerDbm: number;
  txGainDbi: number;
  rxGainDbi: number;
  distanceKm: number;
  freqMhz: number;
  cableLossDb: number;
}): { rxPower: number; fspl: number; margin: number; verdict: "excellent" | "good" | "weak" | "fail" } {
  const fspl = fsplDb(p.distanceKm, p.freqMhz);
  const rxPower =
    num(p.txPowerDbm, 20) + num(p.txGainDbi, 24) + num(p.rxGainDbi, 24) - fspl - num(p.cableLossDb, 0);
  const margin = rxPower - -75; // حساسية مرجعية −75dBm
  const verdict = margin >= 20 ? "excellent" : margin >= 10 ? "good" : margin >= 0 ? "weak" : "fail";
  return { rxPower, fspl, margin, verdict };
}

/** نصف قطر منطقة فريسنل الأولى عند المنتصف (متر): r = 17.32 * √(d1·d2 / (f·D)) — f جيجاهرتز، المسافات كم */
export function fresnelRadiusM(distanceKm: number, freqMhz: number): number {
  const D = Math.max(num(distanceKm, 0), 0.01);
  const fGhz = Math.max(num(freqMhz, 1000), 1) / 1000;
  const d1 = D / 2;
  const d2 = D / 2;
  return 17.32 * Math.sqrt((d1 * d2) / (fGhz * D));
}

/**
 * فحص خط البصر — يراعي انحناء الأرض ومعامل k=0.667
 * ارتفاع الانتفاخ الأرضي: h = d1*d2 / (2*R*k)
 */
export function lineOfSight(p: {
  distanceKm: number;
  heightA: number;
  heightB: number;
  freqMhz: number;
}): { earthBulgeM: number; requiredClearanceM: number; blocked: boolean; losAtMidM: number } {
  const D = Math.max(num(p.distanceKm, 0), 0.01);
  const k = 0.667;
  const R = 6371; // كم
  const d1 = D / 2;
  const d2 = D / 2;
  const earthBulgeM = (d1 * d2) / (2 * R * k) * 1000; // تحويل إلى متر
  const fresnel = fresnelRadiusM(D, p.freqMhz);
  const clearance = earthBulgeM + fresnel * 0.6; // تخلّص 60% من فريسنل
  const losAtMidM = (num(p.heightA, 0) + num(p.heightB, 0)) / 2;
  return {
    earthBulgeM,
    requiredClearanceM: clearance,
    losAtMidM,
    blocked: losAtMidM < clearance,
  };
}

/** تنسيق الأرقام للعرض */
export function fmt(n: number, digits = 2): string {
  if (!Number.isFinite(n)) return "—";
  return n.toLocaleString("ar-EG", { maximumFractionDigits: digits, useGrouping: false });
}
