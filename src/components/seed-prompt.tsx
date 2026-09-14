/**
 * 🌱 تُعرض عند أول تشغيل وقاعدة البيانات فارغة — تولّد 50 موقعًا تجريبيًا بضغطة واحدة
 */
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "./ui";

export function SeedPrompt() {
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  const seed = async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/seed", { method: "POST" });
      const data = await res.json();
      if (data.seeded) {
        router.refresh();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Card className="w-full max-w-lg p-8 text-center pop-in glow-sky">
        <div className="mx-auto mb-6 h-20 w-20 float-y">
          <svg viewBox="0 0 40 40" fill="none" className="h-full w-full">
            <circle cx="20" cy="20" r="3.4" fill="#3AF08F" />
            <ellipse cx="20" cy="20" rx="16" ry="7.5" stroke="#56CCF2" strokeWidth="1.2" transform="rotate(-24 20 20)" />
            <circle cx="33" cy="13" r="1.8" fill="#56CCF2" />
            <circle cx="7" cy="27" r="1.4" fill="#F5B841" />
          </svg>
        </div>
        <h1 className="mb-2 text-2xl font-extrabold">مرحبًا بك في مجرة 🌌</h1>
        <p className="mb-6 leading-relaxed text-dim">
          نظامك لإدارة مواقع الاتصالات جاهز. ابدأ بتوليد بيانات تجريبية كاملة:
          <br />
          <span className="text-ink">50 موقعًا · معدات · روابط · مخزون · تذاكر · جداول صيانة</span>
        </p>
        <button
          onClick={seed}
          disabled={loading}
          className="w-full rounded-xl bg-sky px-6 py-3 font-extrabold text-abyss transition hover:brightness-110 active:scale-[0.98] disabled:opacity-50"
        >
          {loading ? "جارٍ توليد المجرة…" : "🚀 توليد البيانات التجريبية"}
        </button>
        <p className="mt-4 text-[11px] text-dim">أو ابدأ من الصفر عبر إضافة المواقع يدويًا من قسم «المواقع»</p>
      </Card>
    </div>
  );
}
