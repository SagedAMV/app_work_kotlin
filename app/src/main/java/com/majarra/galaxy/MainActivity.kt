package com.majarra.galaxy

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.majarra.galaxy.ui.navigation.GalaxyRoot
import com.majarra.galaxy.ui.theme.GalaxyTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.setContent
import com.majarra.galaxy.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * النشاط الوحيد في التطبيق.
 * - يفرض اتجاه RTL دائمًا (العربية فقط حسب التعليمات).
 * - يقرأ إعداد الوضع الليلي/النهاري ويقفل الشاشة بالبصمة إن فُعّل القفل.
 * ملاحظة: نرث FragmentActivity لأن BiometricPrompt يتطلبها.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // فرض الاتجاه من اليمين إلى اليسار على مستوى النظام
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by settings.preferences.collectAsState(initial = SettingsRepository.Prefs())
            GalaxyTheme(darkTheme = prefs.darkMode) {
                GalaxyRoot(activity = this@MainActivity, prefs = prefs)
            }
        }
    }
}
