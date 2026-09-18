package com.majarra.galaxy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * قاعدة بيانات «مجرة» — أوفلاين بالكامل (Room).
 *
 * الإصدار 7 (النسخة 2.5): جدول جديد حسب تعليمات هذه الجلسة:
 *   - `site_links`: روابط شبكة «واجهة المجرة» بين المواقع.
 * الترحيل 6 → 7 في GalaxyMigrations إضافة فقط (غير مدمّر إطلاقًا).
 *
 * الإصدار 6 (النسخة 2.4): جدول حسب تعليمات جلستها:
 *   - `emergency_visits`: سجل النزول الطارئ/الاستكشاف لكل موقع.
 * الترحيل 5 → 6 إضافة فقط (غير مدمّر إطلاقًا).
 *
 * الإصدار 5 (النسخة 2.2): جدولان حسب تعليمات جلسة الإضافة/التعديل:
 *   - `materials`: كتالوج المواد الموحد (تعديل تبويب المواد).
 *   - `withdrawals`: سجل سحب المواد وصيانتها وإرجاعها.
 */
@Database(
    entities = [
        Site::class,
        Category::class,
        SiteDetail::class,
        MaintenanceLog::class,
        Attachment::class,
        AppSetting::class,
        Material::class,
        Withdrawal::class,
        EmergencyVisit::class,
        SiteLink::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(GalaxyConverters::class)
abstract class GalaxyDatabase : RoomDatabase() {

    abstract fun siteDao(): SiteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun siteDetailDao(): SiteDetailDao
    abstract fun maintenanceLogDao(): MaintenanceLogDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun materialDao(): MaterialDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun emergencyVisitDao(): EmergencyVisitDao
    abstract fun siteLinkDao(): SiteLinkDao

    companion object {
        const val DB_NAME = "galaxy.db"
    }
}
