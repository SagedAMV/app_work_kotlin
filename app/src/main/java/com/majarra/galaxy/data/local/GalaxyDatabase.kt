package com.majarra.galaxy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * قاعدة بيانات «مجرة» — أوفلاين بالكامل (Room).
 *
 * الإصدار 3: التبسيط الكبير حسب تعليمات.md — خمسة جداول فقط
 * (مواقع، تفاصيل، سجل صيانة، مرفقات، إعدادات). الترحيل من 2 → 3
 * في GalaxyMigrations يحافظ على بيانات المواقع والمرفقات.
 */
@Database(
    entities = [
        Site::class,
        SiteDetail::class,
        MaintenanceLog::class,
        Attachment::class,
        AppSetting::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(GalaxyConverters::class)
abstract class GalaxyDatabase : RoomDatabase() {

    abstract fun siteDao(): SiteDao
    abstract fun siteDetailDao(): SiteDetailDao
    abstract fun maintenanceLogDao(): MaintenanceLogDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DB_NAME = "galaxy.db"
    }
}
