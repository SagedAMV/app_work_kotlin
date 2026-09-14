/** تصدير واستيراد النسخة الاحتياطية الكاملة */
import { NextResponse } from "next/server";
import { exportBackup } from "@/lib/data";
import { importBackup } from "@/lib/actions";

export async function GET() {
  const data = await exportBackup();
  const body = JSON.stringify(data, null, 2);
  return new NextResponse(body, {
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Disposition": `attachment; filename="galaxy-backup-${new Date().toISOString().slice(0, 10)}.json"`,
    },
  });
}

export async function POST(req: Request) {
  try {
    const data = await req.json();
    const res = await importBackup(data);
    if (!res.ok) return NextResponse.json({ error: res.error }, { status: 400 });
    return NextResponse.json({ ok: true });
  } catch {
    return NextResponse.json({ error: "ملف غير صالح" }, { status: 400 });
  }
}
