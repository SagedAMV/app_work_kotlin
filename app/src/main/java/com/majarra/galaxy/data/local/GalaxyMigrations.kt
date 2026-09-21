package com.majarra.galaxy.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * ترحيلات قاعدة بيانات «مجرة».
 *
 * 1 → 2: إضافة فهارس أداء (بقيت لمن يرقّي من الإصدار الأول).
 *
 * 2 → 3: التبسيط الكبير حسب تعليمات.md:
 *   - إسقاط جداول: المعدات، سجل الموقع، المخزون، الاحتياج وبنوده،
 *     وثائق BOQ وأسطرها، الروابط، التذاكر، أوامر الشغل، جداول الصيانة
 *     الوقائية، التنبيهات، وسجل التدقيق.
 *   - إعادة بناء المواقع بلا إحداثيات/رمز/حالة مع الحفاظ على البيانات
 *     (الاسم والملاحظات وتواريخ الإنشاء/التعديل).
 *   - إعادة بناء المرفقات بحقولها الجديدة (مسار الملف/النوع/تاريخ الرفع)
 *     مع ترحيل الصفوف القديمة.
 *   - إنشاء الجداول الجديدة: تفاصيل المواقع، سجل الصيانة، الإعدادات.
 *
 * ملاحظات تنفيذية (حماية من فقدان البيانات):
 *   - المواقع والمرفقات تُنسخ أولًا إلى جداول *_new ثم تُسقط القديمة
 *     ويُعاد التسمية، فلا تُفقد بيانات المستخدم.
 *   - إسقاط الجداول يبدأ بالأبناء (بنود الاحتياج، أسطر BOQ) قبل الآباء.
 *   - أسماء الفهارس تطابق ما يولّده Room (index_<الجدول>_<العمود>)
 *     وإلا فشل التحقق من المخطط بعد الترحيل.
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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ── 1) الجداول الجديدة (مفاتيحها الخارجية تشير إلى sites القائمة) ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `site_details` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`availableMaterials` TEXT NOT NULL, " +
                    "`neededMaterials` TEXT NOT NULL, " +
                    "`maintenanceMaterials` TEXT NOT NULL, " +
                    "`withdrawnMaterials` TEXT NOT NULL, " +
                    "`nextMaintenanceDue` INTEGER, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_site_details_siteId` ON `site_details` (`siteId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `maintenance_logs` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`maintenanceDate` INTEGER NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`performedBy` TEXT NOT NULL, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_maintenance_logs_siteId` ON `maintenance_logs` (`siteId`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`settingKey` TEXT NOT NULL, " +
                    "`settingValue` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_settings_settingKey` ON `app_settings` (`settingKey`)"
            )

            // ── 2) إعادة بناء المواقع بلا إحداثيات/رمز/حالة مع نسخ البيانات ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sites_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `sites_new` (`id`, `name`, `notes`, `createdDate`, `lastModified`) " +
                    "SELECT `id`, `name`, `notes`, `createdAt`, `updatedAt` FROM `sites`"
            )

            // ── 3) إعادة بناء المرفقات بالشكل الجديد مع ترحيل الصفوف ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `attachments_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`filePath` TEXT NOT NULL, " +
                    "`fileType` TEXT NOT NULL, " +
                    "`uploadedDate` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "INSERT INTO `attachments_new` (`id`, `siteId`, `filePath`, `fileType`, `uploadedDate`) " +
                    "SELECT `id`, `siteId`, `uri`, `type`, `addedAt` FROM `attachments`"
            )

            // ── 4) إسقاط الجداول الملغاة (الأبناء أولًا) والجداول القديمة ──
            db.execSQL("DROP TABLE IF EXISTS `requirement_items`")
            db.execSQL("DROP TABLE IF EXISTS `boq_lines`")
            db.execSQL("DROP TABLE IF EXISTS `boq_documents`")
            db.execSQL("DROP TABLE IF EXISTS `requirements`")
            db.execSQL("DROP TABLE IF EXISTS `equipments`")
            db.execSQL("DROP TABLE IF EXISTS `site_history`")
            db.execSQL("DROP TABLE IF EXISTS `inventory_items`")
            db.execSQL("DROP TABLE IF EXISTS `links`")
            db.execSQL("DROP TABLE IF EXISTS `tickets`")
            db.execSQL("DROP TABLE IF EXISTS `work_orders`")
            db.execSQL("DROP TABLE IF EXISTS `maintenance_schedules`")
            db.execSQL("DROP TABLE IF EXISTS `alerts`")
            db.execSQL("DROP TABLE IF EXISTS `audit_log`")
            db.execSQL("DROP TABLE IF EXISTS `attachments`")
            db.execSQL("DROP TABLE IF EXISTS `sites`")

            // ── 5) إعادة التسمية ثم الفهارس بأسماء Room القياسية ──
            db.execSQL("ALTER TABLE `sites_new` RENAME TO `sites`")
            db.execSQL("ALTER TABLE `attachments_new` RENAME TO `attachments`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sites_name` ON `sites` (`name`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_attachments_siteId` ON `attachments` (`siteId`)"
            )
        }
    }

    /**
     * 3 → 4: ميزات النسخة 2.1 حسب إجابات الاسئله.md:
     *   - جدول تصنيفات جديد (اسم فريد + لون).
     *   - إعادة بناء المواقع لإضافة `categoryId` (مفتاح خارجي اختياري،
     *     حذف التصنيف يُرجع الموقع إلى «بلا تصنيف» بدل حذفه) و`archived`
     *     للأرشفة مع الاستعادة.
     *
     * حماية البيانات بنفس أسلوب 2 → 3: نسخ إلى جدول *_new ثم إسقاط
     * القديم وإعادة التسمية، فلا يُفقد أي موقع أو ملاحظة أو تاريخ.
     * ملاحظة: مفاتيح أجنبية جديدة لا يمكن إضافتها بـ ALTER TABLE في SQLite،
     * لذلك إعادة البناء هي الطريق الآمن الوحيد.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ── 1) جدول التصنيفات الجديد ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`colorHex` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)"
            )

            // ── 2) إعادة بناء المواقع بالحقول الجديدة مع نسخ البيانات ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sites_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, " +
                    "`categoryId` INTEGER, " +
                    "`archived` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL(
                "INSERT INTO `sites_new` (`id`, `name`, `notes`, `createdDate`, `lastModified`, `categoryId`, `archived`) " +
                    "SELECT `id`, `name`, `notes`, `createdDate`, `lastModified`, NULL, 0 FROM `sites`"
            )

            // ── 3) إسقاط القديم وإعادة التسمية ثم الفهارس بأسماء Room ──
            db.execSQL("DROP TABLE IF EXISTS `sites`")
            db.execSQL("ALTER TABLE `sites_new` RENAME TO `sites`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sites_name` ON `sites` (`name`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sites_categoryId` ON `sites` (`categoryId`)"
            )
        }
    }

    /**
     * 4 → 5: ميزات النسخة 2.2 حسب تعليمات جلسة الإضافة/التعديل:
     *   - جدول `materials`: كتالوج المواد الموحد — الاسم فريد حتى لا
     *     تتكرر مواد متطابقة في واجهات الاختيار داخل المواقع.
     *   - جدول `withdrawals`: سجل سحب المواد من المواقع وصيانتها
     *     وإرجاعها (مفتاح خارجي بحذف متسلسل مع موقعه).
     *
     * ترحيل إضافة فقط: لا يُلمس أي جدول قائم ولا تُنقل أي بيانات،
     * لذلك هو غير مدمّر بطبيعته.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {

            // ── 1) كتالوج المواد الموحد ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `materials` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_materials_name` ON `materials` (`name`)"
            )

            // ── 2) سجل السحب والإرجاع ──
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `withdrawals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`itemName` TEXT NOT NULL, " +
                    "`itemType` TEXT NOT NULL, " +
                    "`withdrawnDate` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`notes` TEXT NOT NULL, " +
                    "`returnedDate` INTEGER, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_withdrawals_siteId` ON `withdrawals` (`siteId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_withdrawals_status` ON `withdrawals` (`status`)"
            )
        }
    }

    /**
     * 5 → 6: ميزات النسخة 2.4 حسب تعليمات هذه الجلسة:
     *   - جدول `emergency_visits`: سجل النزول الطارئ/الاستكشاف لكل موقع
     *     (سبب النزول + النتيجة + وصف المشكلة + المواد المستخدمة +
     *     التحليلات)، بمفتاح خارجي بحذف متسلسل مع موقعه.
     *
     * ترحيل إضافة فقط: لا يُلمس أي جدول قائم ولا تُنقل أي بيانات،
     * لذلك هو غير مدمّر بطبيعته. أسماء الأعمدة والفهارس تطابق حرفيًا
     * ما يولّده Room من كيان EmergencyVisit وإلا فشل التحقق من المخطط.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `emergency_visits` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`visitDate` INTEGER NOT NULL, " +
                    "`reason` TEXT NOT NULL, " +
                    "`outcome` TEXT NOT NULL, " +
                    "`problemDescription` TEXT NOT NULL, " +
                    "`usedMaterials` TEXT NOT NULL, " +
                    "`analysis` TEXT NOT NULL, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_emergency_visits_siteId` ON `emergency_visits` (`siteId`)"
            )
        }
    }

    /**
     * 6 → 7: ميزة «واجهة المجرة» حسب تعليمات هذه الجلسة:
     *   - جدول `site_links`: روابط شبكة الاتصالات بين المواقع، بمفتاحين
     *     خارجيين بحذف متسلسل (حذف أي موقع يمسح كل روابطه).
     *
     * ترحيل إضافة فقط: لا يُلمس أي جدول قائم ولا تُنقل أي بيانات،
     * لذلك هو غير مدمّر بطبيعته. أسماء الأعمدة والفهارس تطابق حرفيًا
     * ما يولّده Room من كيان SiteLink وإلا فشل التحقق من المخطط:
     * فهارس `index_site_links_fromSiteId_toSiteId` (فريد) و
     * `index_site_links_toSiteId`.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `site_links` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fromSiteId` INTEGER NOT NULL, " +
                    "`toSiteId` INTEGER NOT NULL, " +
                    "`createdDate` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`fromSiteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`toSiteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_site_links_fromSiteId_toSiteId` " +
                    "ON `site_links` (`fromSiteId`, `toSiteId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_site_links_toSiteId` ON `site_links` (`toSiteId`)"
            )
        }
    }

    /**
     * 7 → 8: ميزات النسخة 2.12 حسب تعليمات إعادة تصميم تفاصيل الموقع:
     *  - جدول `material_requests`: طلبات احتياج الموقع ودورة الموافقة
     *    والرفض والاسترجاع (مفتاح خارجي بحذف متسلسل مع موقعه).
     *  - أعمدة جديدة في `withdrawals`: `withdrawReason` (سبب السحب
     *    للصيانة) و`fixedNote` (كيف أُصلحت) و`notFixedReason` (سبب عدم
     *    الإصلاح) — بأمان افتراضي نص فارغ حتى لا تُفقد أي بيانات.
     *  - عمود جديد في `emergency_visits`: `notes` (ملاحظات النزول).
     *
     * ترحيل إضافة فقط: لا يُسقط أي جدول ولا يعدّل أي بيانات قائمة،
     * لذلك هو غير مدمّر بطبيعته. أسماء الأعمدة والفهارس تطابق حرفيًا
     * ما يولّده Room من الكيانات وإلا فشل التحقق من المخطط.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `material_requests` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`siteId` INTEGER NOT NULL, " +
                    "`materialName` TEXT NOT NULL, " +
                    "`status` TEXT NOT NULL, " +
                    "`requestedDate` INTEGER NOT NULL, " +
                    "`resolvedDate` INTEGER, " +
                    "FOREIGN KEY(`siteId`) REFERENCES `sites`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_requests_siteId` ON `material_requests` (`siteId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_material_requests_status` ON `material_requests` (`status`)"
            )
            db.execSQL("ALTER TABLE `withdrawals` ADD COLUMN `withdrawReason` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `withdrawals` ADD COLUMN `fixedNote` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `withdrawals` ADD COLUMN `notFixedReason` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `emergency_visits` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        }
    }

    /** كل الترحيلات بالترتيب — تُمرَّر إلى Room.databaseBuilder */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        MIGRATION_7_8
    )
}
