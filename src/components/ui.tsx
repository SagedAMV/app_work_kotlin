/**
 * 🧩 مكونات واجهة مشتركة — بطاقات، شارات حالة، أزرار، مودالات
 */
"use client";

import { useEffect, useState, type ReactNode, type FormEvent } from "react";
export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl border border-edge bg-panel/80 backdrop-blur ${className}`}>{children}</div>;
}

export function StatusBadge({ label, color, pulse = false }: { label: string; color: string; pulse?: boolean }) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-bold"
      style={{ backgroundColor: `${color}1a`, color }}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${pulse ? "animate-pulse" : ""}`} style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}

export function SectionTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3 rise-in">
      <div>
        <h1 className="text-xl font-extrabold sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-dim">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function Btn({
  children,
  onClick,
  type = "button",
  variant = "primary",
  disabled = false,
  className = "",
}: {
  children: ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  variant?: "primary" | "ghost" | "danger" | "neon";
  disabled?: boolean;
  className?: string;
}) {
  const base = "inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-bold transition active:scale-[0.97] disabled:opacity-50 disabled:pointer-events-none";
  const styles = {
    primary: "bg-sky text-abyss hover:brightness-110",
    neon: "bg-neon/15 text-neon border border-neon/40 hover:bg-neon/25",
    ghost: "border border-edge bg-panel2/60 text-ink hover:border-sky/50",
    danger: "bg-danger/15 text-danger border border-danger/40 hover:bg-danger/25",
  };
  return (
    <button type={type} onClick={onClick} disabled={disabled} className={`${base} ${styles[variant]} ${className}`}>
      {children}
    </button>
  );
}

export function Modal({ open, onClose, title, children, wide = false }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-abyss/70 p-0 backdrop-blur-sm sm:items-center sm:p-6 fade-in" onClick={onClose}>
      <div
        className={`max-h-[92vh] w-full overflow-y-auto rounded-t-2xl border border-edge bg-panel p-5 pop-in sm:rounded-2xl ${wide ? "sm:max-w-2xl" : "sm:max-w-md"}`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-extrabold">{title}</h3>
          <button onClick={onClose} className="rounded-lg p-1.5 text-dim transition hover:bg-panel2 hover:text-ink" aria-label="إغلاق">
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** رسالة نجاح/خطأ عائمة بعد الأفعال */
export function Toast({ msg }: { msg: { text: string; ok: boolean } | null }) {
  if (!msg) return null;
  return (
    <div className={`fixed bottom-20 left-1/2 z-[60] -translate-x-1/2 rounded-xl border px-4 py-2.5 text-sm font-bold pop-in lg:bottom-8 ${
      msg.ok ? "border-neon/40 bg-panel text-neon glow-neon" : "border-danger/40 bg-panel text-danger glow-danger"
    }`}>
      {msg.text}
    </div>
  );
}

export function useToast() {
  const [msg, setMsg] = useState<{ text: string; ok: boolean } | null>(null);
  const show = (text: string, ok = true) => {
    setMsg({ text, ok });
    setTimeout(() => setMsg(null), 3200);
  };
  return { msg, show };
}

/** نموذج بحالة تحميل */
export function FormShell({
  onSubmit,
  children,
  submitLabel,
  loading,
}: {
  onSubmit: (fd: FormData) => void | Promise<void>;
  children: ReactNode;
  submitLabel: string;
  loading: boolean;
}) {
  const handle = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    void onSubmit(new FormData(e.currentTarget));
  };
  return (
    <form onSubmit={handle} className="flex flex-col gap-3">
      {children}
      <Btn type="submit" disabled={loading} className="mt-2 w-full">
        {loading ? "جارٍ الحفظ…" : submitLabel}
      </Btn>
    </form>
  );
}

export function Label({ children }: { children: ReactNode }) {
  return <label className="mb-1 block text-xs font-bold text-dim">{children}</label>;
}
