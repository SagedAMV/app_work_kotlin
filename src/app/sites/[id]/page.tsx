/** تفاصيل الموقع — بيانات + معدات + مرفقات + سجل + روابط + تذاكر */
import { notFound } from "next/navigation";
import { getSiteFull } from "@/lib/data";
import { SiteDetail } from "@/components/site-detail";

export default async function SitePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const numId = Number(id);
  if (!Number.isInteger(numId) || numId <= 0) notFound();
  const full = await getSiteFull(numId);
  if (!full) notFound();
  return <SiteDetail data={JSON.parse(JSON.stringify(full))} />;
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
