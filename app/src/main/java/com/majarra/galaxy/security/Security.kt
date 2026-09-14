package com.majarra.galaxy.security

import android.content.Context
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.majarra.galaxy.data.local.GalaxyDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** مساعد القفل البيومتري (بصمة/وجه) */
@Singleton
class BiometricAuthHelper @Inject constructor() {

    /** هل يتوفر مستشعر بيومتري مسجّل؟ */
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** إظهار نافذة التحقق */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح قفل مجرة")
            .setSubtitle("استخدم البصمة أو الوجه")
            .setNegativeButtonText("إلغاء")
            .build()
        prompt.authenticate(info)
    }
}

/** مدير النسخ الاحتياطي: تصدير/استيراد ملف قاعدة البيانات عبر SAF */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GalaxyDatabase
) {

    /** تصدير قاعدة البيانات إلى ملف يختاره المستخدم */
    suspend fun exportBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // تفريغ سجل WAL حتى يكون الملف مكتملًا قبل النسخ
            runCatching {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            }
            val dbFile = context.getDatabasePath(GalaxyDatabase.DB_NAME)
            if (!dbFile.exists()) return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                dbFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * استيراد نسخة احتياطية — يجب إعادة تشغيل التطبيق بعد الاستيراد
     * لأن Room يحتفظ باتصال مفتوح.
     */
    suspend fun importBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            database.close()
            val dbFile = context.getDatabasePath(GalaxyDatabase.DB_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dbFile.outputStream().use { input.copyTo(it) }
            } ?: return@withContext false
            // إزالة ملفات الملحقات القديمة إن وجدت
            File(dbFile.parentFile, "${GalaxyDatabase.DB_NAME}-wal").delete()
            File(dbFile.parentFile, "${GalaxyDatabase.DB_NAME}-shm").delete()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** الحذف الآمن الكامل — لا يُستدعى إلا بعد تأكيد ثلاثي من الواجهة */
@Singleton
class SafeWipe @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GalaxyDatabase
) {
    suspend fun wipeAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            database.clearAllTables()
            true
        } catch (_: Exception) {
            false
        }
    }
}
