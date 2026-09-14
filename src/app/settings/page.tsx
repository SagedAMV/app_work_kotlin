/** ⚙️ الإعدادات — القفل، التنبيهات، سجل التدقيق، النسخ الاحتياطي، الحذف الآمن */
import { getAuditLog, getAlerts } from "@/lib/data";
import { getLockState } from "@/lib/actions";
import { SettingsHub } from "@/components/settings-hub";

export default async function SettingsPage() {
  const [audit, alertsList, lock] = await Promise.all([getAuditLog(), getAlerts(), getLockState()]);
  return <SettingsHub data={JSON.parse(JSON.stringify({ audit, alerts: alertsList, lock }))} />;
}

/* الصفحة ديناميكية — تعتمد على قاعدة البيانات مباشرة */
export const dynamic = "force-dynamic";
