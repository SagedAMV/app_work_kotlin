package com.majarra.galaxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.majarra.galaxy.ui.navigation.GalaxyRoot
import com.majarra.galaxy.ui.theme.GalaxyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * النشاط الوحيد في التطبيق.
 * - يفرض اتجاه RTL دائمًا (العربية فقط حسب التعليمات): مرة على مستوى النافذة
 *   ومرة على مستوى Compose عبر LocalLayoutDirection، لأن تعديل النافذة وحده
 *   لا يضمن اتجاه التخطيط داخل Compose.
 * - يطلب إذن الإشعارات (Android 13+) حتى تعمل تنبيهات قرب الصيانة.
 * - يقرأ الوضع الليلي/القفل من التخزين الفعلي (لا قيمة افتراضية وهمية).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: com.majarra.galaxy.domain.repository.SettingsRepository

    /** طلب إذن الإشعارات — يظهر مرة واحدة فقط إن لم يُمنح بعد */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* التطبيق يعمل بلا إشعارات إن رُفض الإذن */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        requestNotificationPermissionIfNeeded()

        setContent {
            // القيمة الابتدائية من التخزين الفعلي: تمنع تجاوز القفل لحظة الإقلاع
            val prefs by settings.preferences.collectAsState(initial = settings.current)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GalaxyTheme(darkTheme = prefs.darkMode) {
                    GalaxyRoot(prefs = prefs, verifyPin = settings::verifyPin)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
