package com.majarra.galaxy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.majarra.galaxy.R
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.ui.screens.settings.SettingsScreen
import com.majarra.galaxy.ui.screens.sites.SiteDetailsScreen
import com.majarra.galaxy.ui.screens.sites.SitesScreen

/** مسارات التنقل — ثلاثة فقط بعد التبسيط: المواقع، التفاصيل، الإعدادات */
object Routes {
    const val SITES = "sites"
    const val SETTINGS = "settings"
    const val SITE_DETAILS = "site_details/{siteId}"

    fun siteDetails(id: Long) = "site_details/$id"

    val topLevel = setOf(SITES, SETTINGS)
}

/** عنصر تبويب */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.SITES, "المواقع", Icons.Filled.CellTower),
    TabItem(Routes.SETTINGS, "الإعدادات", Icons.Filled.Settings)
)

/**
 * جذر التطبيق: القفل البسيط (رمز سري) + الشريط السفلي + مضيف التنقل.
 * `verifyPin` تُمرَّر من النشاط الرئيسي لأن التحقق يحتاج المستودع
 * ولا نريد إنشاء ViewModel لشاشة القفل.
 */
@Composable
fun GalaxyRoot(
    prefs: SettingsRepository.Prefs,
    verifyPin: (String) -> Boolean
) {
    // القيمة الابتدائية تأتي من التخزين الفعلي (SettingsRepository.current)
    var unlocked by rememberSaveable { mutableStateOf(!prefs.lockEnabled) }

    // تغيير إعداد القفل يُطبَّق فورًا في الاتجاهين (تفعيل ⇒ قفل، إلغاء ⇒ فتح)
    LaunchedEffect(prefs.lockEnabled) {
        unlocked = !prefs.lockEnabled
    }

    // إعادة القفل عند انتقال التطبيق للخلفية: تطبيق إدارة مواقع لا يبقى
    // مفتوحًا إذا تركه المستخدم وانتقل لتطبيق آخر.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, prefs.lockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && prefs.lockEnabled) unlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!unlocked) {
        PinLockScreen(verifyPin = verifyPin, onUnlock = { unlocked = true })
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                GalaxyBottomBar(navController, currentRoute)
            }
        }
    ) { padding ->
        GalaxyNavHost(
            navController = navController,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * شاشة القفل البسيط — رمز سري من 4 إلى 8 أرقام (بدون بيومتري).
 * الرمز يُقارن ببصمته المخزونة في جدول الإعدادات.
 */
@Composable
private fun PinLockScreen(verifyPin: (String) -> Boolean, onUnlock: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // تُقرأ النصوص هنا في سياق التركيب؛ لا يمكن استدعاء stringResource
    // داخل لامدا النقر لأنها دالة @Composable.
    val wrongPinMessage = stringResource(R.string.lock_wrong_pin)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { value ->
                    pin = value.filter { it.isDigit() }.take(8)
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.lock_pin_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation()
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {
                    if (verifyPin(pin)) onUnlock() else error = wrongPinMessage
                },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.lock_button))
            }
        }
    }
}

/** الشريط السفلي — تبويبان فقط بعد التبسيط */
@Composable
private fun GalaxyBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

/** مضيف التنقل المبسط */
@Composable
private fun GalaxyNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SITES,
        modifier = modifier
    ) {
        composable(Routes.SITES) {
            SitesScreen(onOpenSite = { navController.navigate(Routes.siteDetails(it)) })
        }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(
            route = Routes.SITE_DETAILS,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) { entry ->
            SiteDetailsScreen(
                siteId = entry.arguments?.getLong("siteId") ?: 0L,
                onBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }
    }
}
