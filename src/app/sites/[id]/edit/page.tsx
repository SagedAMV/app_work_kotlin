/** تعديل موقع */
import { notFound } from "next/navigation";
import Link from "next/link";
import { db } from "@/db";
import { sites } from "@/db/schema";
import { eq } from "drizzle-orm";
import { SiteForm } from "@/components/site-form";

export default async function EditSitePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const numId = Number(id);
  if (!Number.isInteger(numId) || numId <= 0) notFound();
  const site = await db.select().from(sites).where(eq(sites.id, numId)).then((r) => r[0]);
  if (!site) notFound();
  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-5 flex items-center justify-between rise-in">
        <div>
          <h1 className="text-xl font-extrabold sm:text-2xl">تعديل: {site.name}</h1>
          <p className="mt-1 text-sm text-dim">{site.code} — آخر تحديث {new Date(site.updatedAt).toLocaleDateString("ar")}</p>
        </div>
        <Link href={`/sites/${site.id}`} className="text-sm font-bold text-sky hover:underline">رجوع ←</Link>
      </div>
      <SiteForm draft={site} />
    </div>
  );
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
