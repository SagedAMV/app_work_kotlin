/** بذر البيانات التجريبية — آمن ومتكرر (لا يكرر البذر إن وُجدت بيانات) */
import { NextResponse } from "next/server";
import { seedIfNeeded } from "@/lib/seed";

export async function POST() {
  try {
    const res = await seedIfNeeded();
    return NextResponse.json(res);
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 500 });
  }
}

export async function GET() {
  return POST();
}
