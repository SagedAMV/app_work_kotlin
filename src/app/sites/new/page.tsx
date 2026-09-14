/** إضافة موقع جديد */
import { SiteForm } from "@/components/site-form";

export default function NewSitePage() {
  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-5 rise-in">
        <h1 className="text-xl font-extrabold sm:text-2xl">إضافة موقع جديد</h1>
        <p className="mt-1 text-sm text-dim">الاسم والرمز إلزاميان — الإحداثيات عبر GPS أو يدويًا</p>
      </div>
      <SiteForm />
    </div>
  );
}
