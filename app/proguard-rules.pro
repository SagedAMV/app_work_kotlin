# قواعد ProGuard لتطبيق «مجرة»
# إبقاء كيانات Room لأغراض الانعكاس إن لزم
-keep class com.majarra.galaxy.data.local.** { *; }
# Compose
-dontwarn androidx.compose.**
