package com.majarra.galaxy.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ترحيلات قاعدة بيانات «مجرة».
 *
 * كان المشروع يستخدم الطريقة التدميرية فقط (fallbackToDestructiveMigration)،
 * أي أن أي تغيير في المخطط يؤدي إلى حذف بيانات المستخدم بالكامل (مواقع،
 * معدات، تذاكر…). الآن هناك ترحيل حقيقي غير مدمّر، ويبقى الحذف التدميري
 * للتدهور فقط لأن الرجوع لإصدار أقدم لا يمكن ترحيله بأمان.
 *
 * 1 → 2: إضافة فهارس تُسرّع الاستعلامات الأكثر تكرارًا في التطبيق.
 * ملاحظة مهمة: أسماء الفهارس يجب أن تطابق ما يولّده Room من تعريفات الكيانات
 * (index_<الجدول>_<العمود>) وإلا فشل التحقق من المخطط.
 */
object GalaxyMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sites_name` ON `sites` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_name` ON `inventory_items` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_isRead` ON `alerts` (`isRead`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_createdAt` ON `alerts` (`createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tickets_status` ON `tickets` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_links_status` ON `links` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_requirements_status` ON `requirements` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_orders_siteId` ON `work_orders` (`siteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_orders_status` ON `work_orders` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_schedules_nextDue` ON `maintenance_schedules` (`nextDue`)")
        }
    }

    /** كل الترحيلات بالترتيب — تُمرَّر إلى Room.databaseBuilder */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
