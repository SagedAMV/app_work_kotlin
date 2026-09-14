/** 📍 المواقع — قائمة + بحث + فلترة حسب الحالة */
import Link from "next/link";
import { getSitesList } from "@/lib/data";
import { SitesExplorer } from "@/components/sites-explorer";
import { Btn } from "@/components/ui";

export default async function SitesPage() {
  const { rows, linkCounts } = await getSitesList();
  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3 rise-in">
        <div>
          <h1 className="text-xl font-extrabold sm:text-2xl">المواقع</h1>
          <p className="mt-1 text-sm text-dim">{rows.length} موقعًا — إدارة البيانات والمعدات والمرفقات</p>
        </div>
        <Link href="/sites/new">
          <Btn>+ إضافة موقع</Btn>
        </Link>
      </div>
      <SitesExplorer rows={rows} linkCounts={linkCounts} />
    </div>
  );
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
