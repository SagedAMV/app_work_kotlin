package com.majarra.galaxy.data.repository

import com.majarra.galaxy.data.local.AppSetting
import com.majarra.galaxy.data.local.AppSettingDao
import com.majarra.galaxy.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * إعدادات التطبيق مخزَّنة في جدول app_settings (مفتاح/قيمة).
 *
 * ملاحظات تصميمية:
 * - القراءة الأولى متزامنة (`getAllSync`) لأن شاشة القفل تحتاج القيمة
 *   الحقيقية لحظة الإقلاع، وأي اعتماد على قيم افتراضية يعني تجاوزًا
 *   صامتًا للقفل. الاستعلام صغير (صفوف معدودة) ولذلك فعّلت وحدة
 *   قاعدة البيانات `allowMainThreadQueries` لهذا الغرض تحديدًا.
 * - الرمز السري لا يُخزَّن نصًا صريحًا بل بصمة SHA-256 (بلا ملح لأن
 *   التطبيق شخصي محلي ولا توجد مزامنة — التعقيد الإضافي غير مطلوب).
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: AppSettingDao
) : SettingsRepository {

    private val flow = MutableStateFlow(readPrefs())

    private fun readPrefs(): SettingsRepository.Prefs {
        val values = dao.getAllSync().associate { it.settingKey to it.settingValue }
        return SettingsRepository.Prefs(
            darkMode = values[KEY_DARK]?.toBoolean() ?: true,
            lockEnabled = values[KEY_LOCK]?.toBoolean() ?: false,
            hasPin = !values[KEY_PIN_HASH].isNullOrBlank()
        )
    }

    private suspend fun put(key: String, value: String) {
        dao.upsert(AppSetting(id = 0, settingKey = key, settingValue = value))
        flow.value = readPrefs()
    }

    override val preferences: Flow<SettingsRepository.Prefs> get() = flow

    override val current: SettingsRepository.Prefs get() = readPrefs()

    override suspend fun setDarkMode(enabled: Boolean) = put(KEY_DARK, enabled.toString())

    override suspend fun setLockEnabled(enabled: Boolean) {
        // لا معنى لتفعيل قفل بلا رمز محفوظ — الواجهة تمنع ذلك أيضًا،
        // وهذا فحص دفاعي ثانٍ ضد استدعاء خاطئ.
        if (enabled && !current.hasPin) return
        put(KEY_LOCK, enabled.toString())
    }

    override suspend fun setPin(pin: String) {
        val trimmed = pin.trim()
        require(trimmed.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH) {
            "الرمز السري يجب أن يكون بين $PIN_MIN_LENGTH و$PIN_MAX_LENGTH أرقام"
        }
        require(trimmed.all { it.isDigit() }) { "الرمز السري أرقام فقط" }
        put(KEY_PIN_HASH, sha256(trimmed))
        put(KEY_LOCK, true.toString())
    }

    override fun verifyPin(pin: String): Boolean {
        val stored = dao.getValueSync(KEY_PIN_HASH) ?: return false
        return sha256(pin.trim()) == stored
    }

    override fun getLastDueNoticeDay(): Long? =
        dao.getValueSync(KEY_LAST_DUE_NOTICE)?.toLongOrNull()

    override suspend fun setLastDueNoticeDay(epochDay: Long) =
        put(KEY_LAST_DUE_NOTICE, epochDay.toString())

    override fun getLastBackupTs(): Long? =
        dao.getValueSync(KEY_LAST_BACKUP_TS)?.toLongOrNull()

    override suspend fun setLastBackupTs(epochMillis: Long) =
        put(KEY_LAST_BACKUP_TS, epochMillis.toString())

    override fun getLastBackupReminderDay(): Long? =
        dao.getValueSync(KEY_LAST_BACKUP_REMINDER)?.toLongOrNull()

    override suspend fun setLastBackupReminderDay(epochDay: Long) =
        put(KEY_LAST_BACKUP_REMINDER, epochDay.toString())

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val KEY_DARK = "dark_mode"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_PIN_HASH = "lock_pin_hash"
        const val KEY_LAST_DUE_NOTICE = "last_due_notice_day"
        const val KEY_LAST_BACKUP_TS = "last_backup_ts"
        const val KEY_LAST_BACKUP_REMINDER = "last_backup_reminder_day"
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 8
    }
}
