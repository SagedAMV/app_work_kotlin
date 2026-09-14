/**
 * 🔒 شاشة القفل — تُعرض عند تفعيل القفل برمز سري من الإعدادات
 */
"use client";

import { useState, useTransition } from "react";
import { verifyPin } from "@/lib/actions";

export function LockGate() {
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");
  const [unlocked, setUnlocked] = useState(false);
  const [shaking, setShaking] = useState(false);
  const [, startTransition] = useTransition();

  const submit = () => {
    if (!/^\d{4,6}$/.test(pin)) {
      setError("أدخل الرمز السري (4-6 أرقام)");
      return;
    }
    startTransition(async () => {
      const res = await verifyPin(pin);
      if (res.ok) {
        sessionStorage.setItem("galaxy-unlocked", "1");
        setUnlocked(true);
      } else {
        setError("رمز غير صحيح — حاول مجددًا");
        setShaking(true);
        setTimeout(() => setShaking(false), 500);
        setPin("");
      }
    });
  };

  if (unlocked) {
    // إعادة تحميل لعرض النظام كاملًا بعد الفتح
    if (typeof window !== "undefined") window.location.reload();
    return null;
  }

  return (
    <div className="relative z-10 flex min-h-screen items-center justify-center p-6">
      <div className={`w-full max-w-sm rounded-2xl border border-edge bg-panel/90 p-8 text-center backdrop-blur pop-in ${shaking ? "translate-x-0" : ""}`}
        style={shaking ? { animation: "shake 0.45s" } : undefined}
      >
        <style>{`@keyframes shake { 0%,100%{transform:translateX(0)} 25%{transform:translateX(-8px)} 75%{transform:translateX(8px)} }`}</style>
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-sky/10 glow-sky">
          <svg className="h-8 w-8 text-sky" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
            <rect x="5" y="10" width="14" height="10" rx="2" />
            <path d="M8 10V7a4 4 0 1 1 8 0v3" />
            <circle cx="12" cy="15" r="1.5" fill="currentColor" />
          </svg>
        </div>
        <h1 className="mb-1 text-xl font-extrabold">مجرة مقفلة</h1>
        <p className="mb-6 text-sm text-dim">أدخل الرمز السري للمتابعة</p>
        <input
          type="password"
          inputMode="numeric"
          autoFocus
          value={pin}
          onChange={(e) => {
            setPin(e.target.value.replace(/\D/g, "").slice(0, 6));
            setError("");
          }}
          onKeyDown={(e) => e.key === "Enter" && submit()}
          className="field mb-3 text-center text-2xl tracking-[0.5em]"
          placeholder="••••"
          aria-label="الرمز السري"
        />
        {error && <p className="mb-3 text-sm font-semibold text-danger">{error}</p>}
        <button
          onClick={submit}
          className="w-full rounded-xl bg-sky px-4 py-2.5 font-bold text-abyss transition hover:brightness-110 active:scale-[0.98]"
        >
          فتح
        </button>
      </div>
    </div>
  );
}
