/** شريط أزرار التصدير — CSV لكل تقرير + نسخة JSON كاملة */
"use client";

import { Card } from "./ui";

const EXPORTS = [
  { href: "/api/export/sites", label: "المواقع", icon: "📍" },
  { href: "/api/export/equipment", label: "المعدات", icon: "⚙️" },
  { href: "/api/export/inventory", label: "المخزون", icon: "📦" },
  { href: "/api/export/tickets", label: "الأعطال", icon: "🎫" },
  { href: "/api/export/links", label: "الروابط", icon: "🔗" },
  { href: "/api/export/requirements", label: "الاحتياج", icon: "🧾" },
  { href: "/api/backup", label: "نسخة كاملة (JSON)", icon: "💾" },
];

export function ExportBar() {
  return (
    <Card className="p-4 rise-in">
      <h3 className="mb-3 text-sm font-extrabold text-dim">⬇ تصدير التقارير</h3>
      <div className="flex flex-wrap gap-2">
        {EXPORTS.map((e) => (
          <a key={e.href} href={e.href} download
            className="rounded-xl border border-edge bg-panel2/60 px-3.5 py-2 text-xs font-bold text-ink transition hover:border-sky/50 hover:text-sky active:scale-95">
            {e.icon} {e.label}
          </a>
        ))}
      </div>
    </Card>
  );
}
