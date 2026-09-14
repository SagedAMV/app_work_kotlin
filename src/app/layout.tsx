import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { SideNav, BottomNav } from "@/components/nav";
import { LockGate } from "@/components/lock-screen";
import { getLockState } from "@/lib/actions";

export const metadata: Metadata = {
  title: "مجرة — نظام إدارة مواقع الاتصالات",
  description: "نظام شخصي لإدارة دورة حياة مواقع الاتصالات: المواقع، المعدات، المجرة، الاحتياج، الصيانة والتقارير.",
};

export const viewport: Viewport = {
  themeColor: "#05080f",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const lock = await getLockState();
  return (
    <html lang="ar" dir="rtl">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700;800&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="antialiased">
        <div className="starfield" aria-hidden />
        {lock.enabled ? (
          <LockGate />
        ) : (
          <div className="relative z-10 flex min-h-screen">
            <SideNav />
            <main className="min-w-0 flex-1 px-4 pb-24 pt-6 sm:px-6 lg:pb-10">
              <div className="mx-auto max-w-6xl">{children}</div>
            </main>
          </div>
        )}
        {!lock.enabled && <BottomNav />}
      </body>
    </html>
  );
}
