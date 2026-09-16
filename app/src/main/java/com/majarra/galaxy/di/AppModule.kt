package com.majarra.galaxy.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.data.local.GalaxyMigrations
import com.majarra.galaxy.data.repository.AttachmentRepositoryImpl
import com.majarra.galaxy.data.repository.CategoryRepositoryImpl
import com.majarra.galaxy.data.repository.MaintenanceLogRepositoryImpl
import com.majarra.galaxy.data.repository.SettingsRepositoryImpl
import com.majarra.galaxy.data.repository.SiteDetailRepositoryImpl
import com.majarra.galaxy.data.repository.SiteRepositoryImpl
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** توفير قاعدة البيانات وجميع DAOs */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalaxyDatabase =
        Room.databaseBuilder(context, GalaxyDatabase::class.java, GalaxyDatabase.DB_NAME)
            // ترحيلات حقيقية غير مدمّرة — لا حذف لبيانات المستخدم عند الترقية
            .addMigrations(*GalaxyMigrations.ALL)
            // الحذف التدميري يبقى فقط للتدهور لإصدار أقدم (لا يمكن ترحيله بأمان)
            .fallbackToDestructiveMigrationOnDowngrade()
            // WAL: قراءة وكتابة بلا قفل كامل + checkpoint أسهل قبل النسخ الاحتياطي
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // مقصود ومحدود النطاق: جدول الإعدادات صفوف معدودة، وقراءة القفل
            // يجب أن تكون متزامنة لحظة الإقلاع قبل أي واجهة (تجاوز القيمة
            // الافتراضية للقفل ثغرة أمنية، والقفل البسيط لا يستحق تعقيد
            // مسارات غير متزامنة إضافية).
            .allowMainThreadQueries()
            .build()

    @Provides fun provideSiteDao(db: GalaxyDatabase) = db.siteDao()
    @Provides fun provideCategoryDao(db: GalaxyDatabase) = db.categoryDao()
    @Provides fun provideSiteDetailDao(db: GalaxyDatabase) = db.siteDetailDao()
    @Provides fun provideMaintenanceLogDao(db: GalaxyDatabase) = db.maintenanceLogDao()
    @Provides fun provideAttachmentDao(db: GalaxyDatabase) = db.attachmentDao()
    @Provides fun provideAppSettingDao(db: GalaxyDatabase) = db.appSettingDao()
}

/** ربط واجهات المستودعات بتطبيقاتها */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSiteRepo(impl: SiteRepositoryImpl): SiteRepository

    @Binds @Singleton
    abstract fun bindCategoryRepo(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds @Singleton
    abstract fun bindSiteDetailRepo(impl: SiteDetailRepositoryImpl): SiteDetailRepository

    @Binds @Singleton
    abstract fun bindMaintenanceLogRepo(impl: MaintenanceLogRepositoryImpl): MaintenanceLogRepository

    @Binds @Singleton
    abstract fun bindAttachmentRepo(impl: AttachmentRepositoryImpl): AttachmentRepository

    @Binds @Singleton
    abstract fun bindSettingsRepo(impl: SettingsRepositoryImpl): SettingsRepository
}
