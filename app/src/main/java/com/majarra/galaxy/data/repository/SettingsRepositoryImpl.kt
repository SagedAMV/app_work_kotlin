package com.majarra.galaxy.data.repository

import android.content.Context
import com.majarra.galaxy.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * تفضيلات المستخدم المخزَّنة محليًا.
 *
 * ملاحظة أمنية مهمة: كان التطبيق يقرأ التفضيلات عبر Flow فقط، فتستخدم الشاشة
 * القيمة الافتراضية (قفل = false) في اللحظة الأولى — أي أن القفل البيومتري
 * كان يُتجاوز فعليًا. لذلك أُضيفت `current` للقراءة المتزامنة الصحيحة.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val flow = MutableStateFlow(readPrefs())

    private fun readPrefs() = SettingsRepository.Prefs(
        darkMode = prefs.getBoolean(KEY_DARK, true),
        biometricLock = prefs.getBoolean(KEY_LOCK, false)
    )

    override val preferences: Flow<SettingsRepository.Prefs> get() = flow

    override val current: SettingsRepository.Prefs get() = readPrefs()

    override suspend fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, enabled).apply()
        flow.value = readPrefs()
    }

    override suspend fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK, enabled).apply()
        flow.value = readPrefs()
    }

    companion object {
        const val PREFS_NAME = "galaxy_settings"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_LOCK = "biometric_lock"
    }
}
