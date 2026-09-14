/** نموذج إضافة / تعديل موقع — GPS تلقائي أو إدخال يدوي */
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { SITE_STATUS, SITE_TYPES } from "@/lib/constants";
import { Card, Label, Btn, useToast, Toast } from "./ui";
import { createSite, updateSite } from "@/lib/actions";

export type SiteDraft = {
  id?: number;
  name: string;
  code: string;
  latitude: number;
  longitude: number;
  status: string;
  siteType: string;
  notes: string;
};

export function SiteForm({ draft }: { draft?: Partial<SiteDraft> }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [locating, setLocating] = useState(false);
  const [lat, setLat] = useState(String(draft?.latitude ?? ""));
  const [lon, setLon] = useState(String(draft?.longitude ?? ""));
  const { msg, show } = useToast();

  const useGps = () => {
    if (!navigator.geolocation) {
      setError("الجهاز لا يدعم تحديد الموقع");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude.toFixed(5));
        setLon(pos.coords.longitude.toFixed(5));
        setLocating(false);
      },
      () => {
        setLocating(false);
        setError("تعذر تحديد الموقع — أدخل الإحداثيات يدويًا");
      },
      { timeout: 8000 }
    );
  };

  const submit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    const fd = new FormData(e.currentTarget);
    const res = draft?.id ? await updateSite(fd) : await createSite(fd);
    setLoading(false);
    if (res.ok) {
      show(draft?.id ? "تم تحديث الموقع" : "تم إنشاء الموقع");
      setTimeout(() => router.push(res.id ? `/sites/${res.id}` : "/sites"), 600);
    } else {
      setError(res.error);
    }
  };

  return (
    <Card className="p-6 rise-in">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        {draft?.id && <input type="hidden" name="id" value={draft.id} />}
        <div>
          <Label>اسم الموقع *</Label>
          <input name="name" required defaultValue={draft?.name ?? ""} className="field" placeholder="مثال: موقع القمة 01" />
        </div>
        <div>
          <Label>رمز الموقع *</Label>
          <input name="code" required defaultValue={draft?.code ?? ""} className="field" placeholder="مثال: ST-51" disabled={!!draft?.id && draft.code !== ""} />
          {draft?.id && <p className="mt-1 text-[10px] text-dim">الرمز ثابت بعد الإنشاء</p>}
        </div>
        <div>
          <Label>الحالة</Label>
          <select name="status" defaultValue={draft?.status ?? "active"} className="field">
            {Object.entries(SITE_STATUS).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div>
          <Label>نوع الموقع</Label>
          <select name="siteType" defaultValue={draft?.siteType ?? "mountain"} className="field">
            {Object.entries(SITE_TYPES).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-2">
          <div className="mb-1 flex items-center justify-between">
            <Label>الإحداثيات (خط العرض / خط الطول)</Label>
            <button type="button" onClick={useGps} className="text-xs font-bold text-sky hover:underline" disabled={locating}>
              {locating ? "جارٍ التحديد…" : "📍 استخدام موقعي الحالي"}
            </button>
          </div>
          <div className="flex gap-2">
            <input name="latitude" value={lat} onChange={(e) => setLat(e.target.value)} className="field" placeholder="خط العرض 24.65" inputMode="decimal" />
            <input name="longitude" value={lon} onChange={(e) => setLon(e.target.value)} className="field" placeholder="خط الطول 45.35" inputMode="decimal" />
          </div>
        </div>
        <div className="sm:col-span-2">
          <Label>ملاحظات</Label>
          <textarea name="notes" defaultValue={draft?.notes ?? ""} rows={3} className="field" placeholder="أي تفاصيل إضافية عن الموقع…" />
        </div>
        {error && <p className="text-sm font-bold text-danger sm:col-span-2">{error}</p>}
        <div className="flex gap-2 sm:col-span-2">
          <Btn type="submit" disabled={loading} className="flex-1">{loading ? "جارٍ الحفظ…" : draft?.id ? "حفظ التعديلات" : "إنشاء الموقع"}</Btn>
          <Link href={draft?.id ? `/sites/${draft.id}` : "/sites"} className="flex-1">
            <Btn variant="ghost" className="w-full">إلغاء</Btn>
          </Link>
        </div>
      </form>
      <Toast msg={msg} />
    </Card>
  );
}
