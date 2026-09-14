/**
 * 🧭 التنقل — شريط جانبي (سطح المكتب) + شريط سفلي (الهاتف)
 */
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const ITEMS = [
  { href: "/", label: "اللوحة", short: "اللوحة" },
  { href: "/sites", label: "المواقع", short: "المواقع" },
  { href: "/galaxy", label: "المجرة", short: "المجرة" },
  { href: "/requirements", label: "الاحتياج والمخزون", short: "الاحتياج" },
  { href: "/maintenance", label: "الصيانة والأعطال", short: "الصيانة" },
  { href: "/reports", label: "التقارير", short: "التقارير" },
  { href: "/settings", label: "الإعدادات", short: "الإعدادات" },
];

function GalaxyLogo({ size = 34 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden>
      <circle cx="20" cy="20" r="3.4" fill="#3AF08F" />
      <circle cx="20" cy="20" r="3.4" fill="#3AF08F" opacity="0.4">
        <animate attributeName="r" values="3.4;6;3.4" dur="2.4s" repeatCount="indefinite" />
        <animate attributeName="opacity" values="0.5;0;0.5" dur="2.4s" repeatCount="indefinite" />
      </circle>
      <ellipse cx="20" cy="20" rx="15" ry="7" stroke="#56CCF2" strokeWidth="1.2" transform="rotate(-24 20 20)" />
      <ellipse cx="20" cy="20" rx="15" ry="7" stroke="#56CCF2" strokeWidth="0.8" opacity="0.45" transform="rotate(42 20 20)" />
      <circle cx="32" cy="13" r="1.6" fill="#56CCF2" />
      <circle cx="8" cy="26" r="1.3" fill="#F5B841" />
    </svg>
  );
}

function Icon({ i }: { i: number }) {
  const cls = "h-5 w-5";
  switch (i) {
    case 0: // لوحة
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <rect x="3" y="3" width="8" height="10" rx="1.5" /><rect x="13" y="3" width="8" height="6" rx="1.5" />
          <rect x="13" y="11" width="8" height="10" rx="1.5" /><rect x="3" y="15" width="8" height="6" rx="1.5" />
        </svg>
      );
    case 1: // مواقع
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M12 21s-7-5.5-7-11a7 7 0 1 1 14 0c0 5.5-7 11-7 11z" /><circle cx="12" cy="10" r="2.6" />
        </svg>
      );
    case 2: // مجرة
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <circle cx="5" cy="6" r="2" /><circle cx="19" cy="6" r="2" /><circle cx="12" cy="18" r="2" />
          <path d="M6.7 7.2 10.6 16M17.3 7.2 13.4 16M7 6h10" />
        </svg>
      );
    case 3: // احتياج
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M4 7h16l-1.5 12a2 2 0 0 1-2 1.8h-9A2 2 0 0 1 5.5 19L4 7z" /><path d="M8 7a4 4 0 1 1 8 0" />
        </svg>
      );
    case 4: // صيانة
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M14.5 6.5a4 4 0 0 0-5.6 4.9L4 16.3V20h3.7l4.9-4.9a4 4 0 0 0 4.9-5.6l-2.8 2.8-2.5-.7-.7-2.5 2.9-2.6z" />
        </svg>
      );
    case 5: // تقارير
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <path d="M4 20V9" /><path d="M10 20V4" /><path d="M16 20v-8" /><path d="M22 20H2" />
        </svg>
      );
    default: // إعدادات
      return (
        <svg className={cls} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <circle cx="12" cy="12" r="3" />
          <path d="M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.5-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.5 2 1.5a7 7 0 0 0 0 2.4l-2 1.5 2 3.5 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.5-2-1.5c.06-.4.1-.8.1-1.2z" />
        </svg>
      );
  }
}

export function SideNav() {
  const pathname = usePathname();
  return (
    <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-l border-edge bg-panel/70 p-4 backdrop-blur lg:flex">
      <Link href="/" className="mb-8 flex items-center gap-3 px-2">
        <GalaxyLogo />
        <div>
          <div className="text-lg font-extrabold leading-tight text-ink">مجرة</div>
          <div className="text-[11px] text-dim">إدارة مواقع الاتصالات</div>
        </div>
      </Link>
      <nav className="flex flex-col gap-1">
        {ITEMS.map((it, i) => {
          const active = it.href === "/" ? pathname === "/" : pathname.startsWith(it.href);
          return (
            <Link
              key={it.href}
              href={it.href}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition-all ${
                active
                  ? "bg-sky/10 text-sky glow-sky"
                  : "text-dim hover:bg-panel2 hover:text-ink"
              }`}
            >
              <Icon i={i} />
              {it.label}
              {active && <span className="mr-auto h-2 w-2 rounded-full bg-sky" />}
            </Link>
          );
        })}
      </nav>
      <div className="mt-auto rounded-xl border border-edge bg-panel2/60 p-3 text-[11px] leading-relaxed text-dim">
        <span className="text-neon">●</span> نظام شخصي — يعمل محليًا بالكامل
      </div>
    </aside>
  );
}

export function BottomNav() {
  const pathname = usePathname();
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-edge bg-panel/90 backdrop-blur lg:hidden">
      <div className="mx-auto flex max-w-lg items-stretch justify-between overflow-x-auto px-1 pb-[env(safe-area-inset-bottom)]">
        {ITEMS.map((it, i) => {
          const active = it.href === "/" ? pathname === "/" : pathname.startsWith(it.href);
          return (
            <Link
              key={it.href}
              href={it.href}
              className={`flex min-w-[52px] flex-1 flex-col items-center gap-0.5 px-1 py-2 text-[10px] font-semibold transition-colors ${
                active ? "text-sky" : "text-dim"
              }`}
            >
              <Icon i={i} />
              {it.short}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
