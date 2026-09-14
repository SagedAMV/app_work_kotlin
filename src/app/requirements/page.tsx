/** 📦 الاحتياج والمخزون */
import { getRequirementsFull, getInventory, getAllSitesSimple } from "@/lib/data";
import { RequirementsHub } from "@/components/requirements-hub";

export default async function RequirementsPage() {
  const [reqs, inventory, sitesList] = await Promise.all([getRequirementsFull(), getInventory(), getAllSitesSimple()]);
  return <RequirementsHub data={JSON.parse(JSON.stringify({ reqs, inventory, sites: sitesList }))} />;
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
