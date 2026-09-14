/** 🔧 الصيانة والأعطال */
import { getMaintenanceData, getAllSitesSimple } from "@/lib/data";
import { MaintenanceHub } from "@/components/maintenance-hub";

export default async function MaintenancePage() {
  const [data, sitesList] = await Promise.all([getMaintenanceData(), getAllSitesSimple()]);
  return <MaintenanceHub data={JSON.parse(JSON.stringify({ ...data, sites: sitesList }))} />;
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
