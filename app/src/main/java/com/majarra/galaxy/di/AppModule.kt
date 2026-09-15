package com.majarra.galaxy.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.data.local.GalaxyMigrations
import com.majarra.galaxy.data.repository.AlertRepositoryImpl
import com.majarra.galaxy.data.repository.AttachmentRepositoryImpl
import com.majarra.galaxy.data.repository.AuditRepositoryImpl
import com.majarra.galaxy.data.repository.BoqRepositoryImpl
import com.majarra.galaxy.data.repository.EquipmentRepositoryImpl
import com.majarra.galaxy.data.repository.InventoryRepositoryImpl
import com.majarra.galaxy.data.repository.LinkRepositoryImpl
import com.majarra.galaxy.data.repository.MaintenanceRepositoryImpl
import com.majarra.galaxy.data.repository.RequirementRepositoryImpl
import com.majarra.galaxy.data.repository.RoomTransactionRunner
import com.majarra.galaxy.data.repository.SettingsRepositoryImpl
import com.majarra.galaxy.data.repository.SiteHistoryRepositoryImpl
import com.majarra.galaxy.data.repository.SiteRepositoryImpl
import com.majarra.galaxy.data.repository.TicketRepositoryImpl
import com.majarra.galaxy.data.repository.WorkOrderRepositoryImpl
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.BoqRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.MaintenanceRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.domain.repository.SiteHistoryRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.domain.repository.TransactionRunner
import com.majarra.galaxy.domain.repository.UriPermissionVault
import com.majarra.galaxy.domain.repository.WorkOrderRepository
import com.majarra.galaxy.security.UriPermissionVaultImpl
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
            // ترحيلات حقيقية غير مدمّرة (فهارس الأداء) — لا حذف لبيانات المستخدم
            .addMigrations(*GalaxyMigrations.ALL)
            // الحذف التدميري يبقى فقط للتدهور لإصدار أقدم (لا يمكن ترحيله بأمان)
            .fallbackToDestructiveMigrationOnDowngrade()
            // WAL: قراءة وكتابة بلا قفل كامل + checkpoint أسهل قبل النسخ الاحتياطي
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()

    @Provides fun provideSiteDao(db: GalaxyDatabase) = db.siteDao()
    @Provides fun provideEquipmentDao(db: GalaxyDatabase) = db.equipmentDao()
    @Provides fun provideAttachmentDao(db: GalaxyDatabase) = db.attachmentDao()
    @Provides fun provideSiteHistoryDao(db: GalaxyDatabase) = db.siteHistoryDao()
    @Provides fun provideInventoryItemDao(db: GalaxyDatabase) = db.inventoryItemDao()
    @Provides fun provideRequirementDao(db: GalaxyDatabase) = db.requirementDao()
    @Provides fun provideRequirementItemDao(db: GalaxyDatabase) = db.requirementItemDao()
    @Provides fun provideBoqDao(db: GalaxyDatabase) = db.boqDao()
    @Provides fun provideLinkDao(db: GalaxyDatabase) = db.linkDao()
    @Provides fun provideTicketDao(db: GalaxyDatabase) = db.ticketDao()
    @Provides fun provideWorkOrderDao(db: GalaxyDatabase) = db.workOrderDao()
    @Provides fun provideMaintenanceScheduleDao(db: GalaxyDatabase) = db.maintenanceScheduleDao()
    @Provides fun provideAlertDao(db: GalaxyDatabase) = db.alertDao()
    @Provides fun provideAuditLogDao(db: GalaxyDatabase) = db.auditLogDao()
}

/** ربط واجهات المستودعات بتطبيقاتها */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSiteRepo(impl: SiteRepositoryImpl): SiteRepository

    @Binds @Singleton
    abstract fun bindEquipmentRepo(impl: EquipmentRepositoryImpl): EquipmentRepository

    @Binds @Singleton
    abstract fun bindAttachmentRepo(impl: AttachmentRepositoryImpl): AttachmentRepository

    @Binds @Singleton
    abstract fun bindSiteHistoryRepo(impl: SiteHistoryRepositoryImpl): SiteHistoryRepository

    @Binds @Singleton
    abstract fun bindInventoryRepo(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds @Singleton
    abstract fun bindRequirementRepo(impl: RequirementRepositoryImpl): RequirementRepository

    @Binds @Singleton
    abstract fun bindBoqRepo(impl: BoqRepositoryImpl): BoqRepository

    @Binds @Singleton
    abstract fun bindLinkRepo(impl: LinkRepositoryImpl): LinkRepository

    @Binds @Singleton
    abstract fun bindTicketRepo(impl: TicketRepositoryImpl): TicketRepository

    @Binds @Singleton
    abstract fun bindWorkOrderRepo(impl: WorkOrderRepositoryImpl): WorkOrderRepository

    @Binds @Singleton
    abstract fun bindMaintenanceRepo(impl: MaintenanceRepositoryImpl): MaintenanceRepository

    @Binds @Singleton
    abstract fun bindAlertRepo(impl: AlertRepositoryImpl): AlertRepository

    @Binds @Singleton
    abstract fun bindAuditRepo(impl: AuditRepositoryImpl): AuditRepository

    @Binds @Singleton
    abstract fun bindSettingsRepo(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    @Binds @Singleton
    abstract fun bindUriPermissionVault(impl: UriPermissionVaultImpl): UriPermissionVault
}
