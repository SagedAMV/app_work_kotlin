/** 🌌 المجرة — العرض الحي للشبكة */
import { getGalaxyData } from "@/lib/data";
import { GalaxyView } from "@/components/galaxy-view";

export default async function GalaxyPage() {
  const data = await getGalaxyData();
  return <GalaxyView data={JSON.parse(JSON.stringify(data))} />;
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
