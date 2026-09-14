# ملاحظات البناء الفعلي — أول APK released (v1.0.0)

> أول بناء حقيقي للمشروع جرى في بيئة سطر أوامر (Linux، JDK 17، Android SDK 34، Gradle 8.5).
> النتيجة: `BUILD SUCCESSFUL` + 11/11 اختبار وحدة ناجح.

## عدد أخطاء الترجمة الحقيقية: 55 (لا 47)
| المرحلة | عدد الأخطاء `e:` | أبرز الأسباب |
|---|---|---|
| `:app:kspReleaseKotlin` (1) | 5 | قوس `AlertDialog(` غير مغلق في MaintenanceScreen:502 + 4 أخطاء KSP/Hilt متولّدة عنه |
| `:app:kspReleaseKotlin` (2) | 3 | استيراد `SettingsRepository` من `data.repository` بدل `domain.repository` |
| `:app:compileReleaseKotlin` | 47 | `AssistChip(selected=…)` ×20 (المعامل غير موجود في Material3 ← استُخدم `FilterChip`)، `PairRow` ×20 (ترتيب المعاملات كان يبتلع اللامبدا الأخيرة)، استيرادات ناقصة، `width/height` بدل `size.*` داخل `DrawScope`، `TicketDao.getById` مفقود، `Configuration.Provider` صار `val` في WorkManager 2.9 |
| `:app:compileReleaseUnitTestKotlin` | 1 | إرجاع `fixture` كان `Pair` بدل `Triple` |

## ملاحظات تقنية للبناء القادم
- **الجرّار غير موجود:** المستودع لا يحوي `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` — البناء تم بـ Gradle 8.5 مثبّتًا يدويًا (نفس إصدار الـ wrapper المعلن في `gradle-wrapper.properties`).
- `apksigner`/`keytool` يحتاجان **JDK 17**؛ JDK 11 الافتراضي يفشل بـ `HmacPBESHA256 not available` عند قراءة الـ keystore (PKCS12).
- ذاكرة الجهاز 1.9GB: فُعّل swap بحجم 3GB وشغّل البناء بـ `-Dorg.gradle.workers.max=1`.
- تسمية الاختبارات بالعربية تتطلب ترميز UTF-8؛ بلغة `POSIX/ASCII` يفشل `compileReleaseUnitTestKotlin` برسالة `InvalidPathException: Malformed input…`. الحل: `LANG=C.utf8 LC_ALL=C.utf8`.
- `versionCode` ما زال **1** — يُرفع قبل البناء القادم لتمييز النسخ.
- الأيقونة أصبحت `@mipmap/ic_launcher` (adaptive) بدل `@android:drawable/sym_def_app_icon` الخاص.

## الإصدار الموقّع
- الملف: `majarra-galaxy-v1.0.0-first-release-build-2026-09-15.apk` (4,345,254 بايت)
- SHA-256 للملف: `b9510a1c1beba9c2d662c58d9d001f9ca84c5bd205c4b5bfb0d6cf88a83b53c9`
- بصمة التوقيع SHA-256: `43:7E:47:A7:C9:93:D9:8B:A0:D0:A6:53:A0:FD:83:74:37:EA:22:93:E8:8B:C7:07:68:BA:90:AB:74:8B:37:A4`
