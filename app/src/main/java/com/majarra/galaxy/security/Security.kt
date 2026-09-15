package com.majarra.galaxy.security

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.room.withTransaction
import com.majarra.galaxy.GalaxyApplication
import com.majarra.galaxy.MainActivity
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.notify.GalaxyNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** نتيجة عملية النسخ الاحتياطي أو المسح */
sealed class BackupResult {
    object Exported : BackupResult()
    object Imported : BackupResult()
    data class Failure(val reason: String) : BackupResult()
}

/**
 * مدير النسخ الاحتياطي — نسخة محلية بسيطة وموثوقة عبر SAF:
 * تصدير/استيراد ملف قاعدة البيانات بالكامل، بلا تشفير معقد.
 *
 * الموثوقية أهم من التعقيد، لذلك بقيت ثلاث حمايات أساسية:
 * 1) تفريغ سجل الكتابة (WAL checkpoint) قبل التصدير حتى لا يُنسخ الملف ناقصًا.
 * 2) التحقق من صيغة الملف المستورد (ترويسة SQLite + جداول مجرة)
 *    حتى لا تُستبدل البيانات بملف تالف.
 * 3) استيراد شبه ذرّي: نسخة من القاعدة الحالية قبل الاستبدال،
 *    واسترجاعها إن فشل التحقق النهائي.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GalaxyDatabase
) {

    private val dbFile: File get() = context.getDatabasePath(GalaxyDatabase.DB_NAME)

    /** تصدير قاعدة البيانات إلى ملف يختاره المستخدم */
    suspend fun exportBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        if (!dbFile.exists()) return@withContext BackupResult.Failure("ملف قاعدة البيانات غير موجود")

        val checkpoint = runCatching {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }.getOrElse { error ->
            Log.e(TAG, "checkpoint failed", error)
            return@withContext BackupResult.Failure("فشل تفريغ سجل قاعدة البيانات قبل النسخ")
        }
        if (checkpoint != 0) {
            return@withContext BackupResult.Failure("قاعدة البيانات مشغولة بالكتابة — أعد المحاولة بعد لحظات")
        }

        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                dbFile.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            } ?: return@withContext BackupResult.Failure("تعذّر فتح الملف المحدد للكتابة")
            BackupResult.Exported
        } catch (e: Exception) {
            Log.e(TAG, "export failed", e)
            BackupResult.Failure("فشل التصدير: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * استيراد نسخة احتياطية — يجب إعادة تشغيل التطبيق بعد الاستيراد
     * لأن Room يحتفظ باتصال مفتوح بالملف الذي استبدلناه.
     */
    suspend fun importBackup(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}.db")
        val previous = File(context.cacheDir, "previous.db")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext BackupResult.Failure("تعذّر قراءة الملف المحدد")

            validateBackup(temp)?.let { reason ->
                temp.delete()
                return@withContext BackupResult.Failure(reason)
            }

            // نقطة استرجاع: نسخة من القاعدة الحالية قبل الاستبدال
            if (dbFile.exists()) dbFile.copyTo(previous, overwrite = true)

            runCatching { database.close() }
                .onFailure { Log.w(TAG, "close before import", it) }

            File(dbFile.parentFile, "${GalaxyDatabase.DB_NAME}-wal").delete()
            File(dbFile.parentFile, "${GalaxyDatabase.DB_NAME}-shm").delete()

            if (!temp.renameTo(dbFile)) {
                // بعض أنظمة الملفات لا تدعم إعادة التسمية عبر الحدود: نسخ ثم حذف
                temp.copyTo(dbFile, overwrite = true)
                temp.delete()
            }

            if (validateBackup(dbFile) != null) {
                if (previous.exists()) previous.copyTo(dbFile, overwrite = true)
                return@withContext BackupResult.Failure("الملف المُستورد تالف — تمت استعادة البيانات السابقة")
            }
            BackupResult.Imported
        } catch (e: Exception) {
            Log.e(TAG, "import failed", e)
            BackupResult.Failure("فشل الاستيراد: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /** يتحقق أن الملف قاعدة بيانات SQLite سليمة تحتوي جداول «مجرة» المبسطة */
    private fun validateBackup(file: File): String? {
        if (!file.exists() || file.length() < MIN_DB_BYTES) return "حجم الملف أصغر من قاعدة بيانات صالحة"
        val header = ByteArray(16)
        try {
            file.inputStream().use { it.read(header) }
        } catch (e: Exception) {
            return "تعذّر قراءة الملف"
        }
        if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            return "الملف ليس قاعدة بيانات SQLite صالحة"
        }
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' " +
                        "AND name IN ('sites','site_details','maintenance_logs')",
                    null
                ).use { cursor ->
                    val found = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                    if (found < 3) "الملف لا يحتوي جداول تطبيق مجرة" else null
                }
            }
        } catch (e: Exception) {
            "قاعدة البيانات في الملف غير قابلة للفتح (تالفة أو غير متوافقة)"
        }
    }

    private companion object {
        const val TAG = "BackupManager"
        const val MIN_DB_BYTES = 4096L
    }
}

/**
 * المسح الكامل — يمسح كل الجداول (المواقع والتفاصيل والسجلات
 * والمرفقات والإعدادات) في معاملة واحدة، ويلغي الإشعارات الظاهرة.
 */
@Singleton
class SafeWipe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GalaxyDatabase
) {
    suspend fun wipeAll(): BackupResult = withContext(Dispatchers.IO) {
        try {
            database.withTransaction { database.clearAllTables() }
            GalaxyNotifications.cancelAll(context)
            BackupResult.Imported
        } catch (e: Exception) {
            Log.e(TAG, "wipe failed", e)
            BackupResult.Failure("فشل المسح: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SafeWipe"
    }
}

/**
 * إعادة تشغيل التطبيق بعد استيراد نسخة احتياطية أو مسح كامل،
 * لأن Room لا يمكنه إعادة فتح قاعدة بيانات أُغلق كائنها.
 */
object AppRestarter {
    fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (context is GalaxyApplication) {
            context.startActivity(intent)
        } else {
            context.applicationContext.startActivity(intent)
        }
        Process.killProcess(Process.myPid())
    }
}
