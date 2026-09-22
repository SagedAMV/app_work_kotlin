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
 * الإصدار 8 (النسخة 2.12 — إعادة تصميم تفاصيل الموقع):
 *   - جدول جديد `material_requests`: طلبات احتياج الموقع ودورة
 *     الموافقة/الرفض/الاسترجاع لواجهة «الاحتياجات».
 *   - أعمدة سبب السحب وقرار الإصلاح في `withdrawals`، وعمود ملاحظات
 *     في `emergency_visits`.
 * الترحيل 7 → 8 في GalaxyMigrations إضافة فقط (غير مدمّر إطلاقًا).
 *
 * الإصدار 9 (جلسة تعديلات منطق المواد والإحصائيات):
 *   - عمود `type` في `materials`: نوع المادة (عادية/مادة اتصال).
 *   - جدول جديد `material_dependencies`: تبعيات مادة الاتصال في
 *     كل موقع (واير، كواكسل، مايك…) بمفتاح خارجي بحذف متسلسل.
 *   - عمود `parentName` في `withdrawals`: يميز سحب التبعية عن سحب
 *     المادة الرئيسية فيعاد استخدام دورة السحب نفسها للتبعيات.
 * الترحيل 8 → 9 في GalaxyMigrations إضافة فقط (غير مدمّر إطلاقًا).
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
        SiteLink::class,
        MaterialRequest::class,
        MaterialDependency::class
    ],
    version = 9,
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
    abstract fun materialRequestDao(): MaterialRequestDao
    abstract fun materialDependencyDao(): MaterialDependencyDao

    companion object {
        const val DB_NAME = "galaxy.db"
    }
}
