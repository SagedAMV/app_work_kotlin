package com.majarra.galaxy.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.majarra.galaxy.data.repository.SettingsRepository
import com.majarra.galaxy.security.BiometricAuthHelper
import com.majarra.galaxy.ui.screens.dashboard.DashboardScreen
import com.majarra.galaxy.ui.screens.galaxy.GalaxyScreen
import com.majarra.galaxy.ui.screens.galaxy.LinkCalculatorScreen
import com.majarra.galaxy.ui.screens.maintenance.MaintenanceScreen
import com.majarra.galaxy.ui.screens.needs.NeedsScreen
import com.majarra.galaxy.ui.screens.reports.ReportsScreen
import com.majarra.galaxy.ui.screens.settings.SettingsScreen
import com.majarra.galaxy.ui.screens.sites.SiteDetailsScreen
import com.majarra.galaxy.ui.screens.sites.SiteFormScreen
import com.majarra.galaxy.ui.screens.sites.SitesScreen

/** مسارات التنقل */
object Routes {
    const val DASHBOARD = "dashboard"
    const val SITES = "sites"
    const val GALAXY = "galaxy"
    const val NEEDS = "needs"
    const val MAINTENANCE = "maintenance"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val SITE_DETAILS = "site_details/{siteId}"
    const val SITE_FORM = "site_form?siteId={siteId}"
    const val LINK_CALCULATOR = "link_calculator?linkId={linkId}"

    fun siteDetails(id: Long) = "site_details/$id"
    fun siteForm(id: Long? = null) = if (id == null) "site_form" else "site_form?siteId=$id"
    fun linkCalculator(id: Long? = null) =
        if (id == null) "link_calculator" else "link_calculator?linkId=$id"

    val topLevel = setOf(DASHBOARD, SITES, GALAXY, NEEDS, MAINTENANCE, REPORTS, SETTINGS)
}

/** عنصر تبويب */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.DASHBOARD, "الرئيسية", Icons.Filled.SpaceDashboard),
    TabItem(Routes.SITES, "المواقع", Icons.Filled.CellTower),
    TabItem(Routes.GALAXY, "المجرة", Icons.Filled.Hub),
    TabItem(Routes.NEEDS, "الاحتياج", Icons.Filled.Inventory2),
    TabItem(Routes.MAINTENANCE, "الصيانة", Icons.Filled.Build),
    TabItem(Routes.REPORTS, "التقارير", Icons.Filled.Assessment),
    TabItem(Routes.SETTINGS, "الإعدادات", Icons.Filled.Settings)
)

/** جذر التطبيق: القفل البيومتري + الشريط السفلي + مضيف التنقل */
@Composable
fun GalaxyRoot(
    activity: FragmentActivity,
    prefs: SettingsRepository.Prefs
) {
    var unlocked by remember { mutableStateOf(!prefs.biometricLock) }

    // إذا أُلغي القفل من الإعدادات تُفتح الشاشة فورًا
    LaunchedEffect(prefs.biometricLock) {
        if (!prefs.biometricLock) unlocked = true
    }

    if (!unlocked) {
        LockScreen(activity = activity, onUnlock = { unlocked = true })
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
            activity = activity,
            navController = navController,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.padding(padding)
        )
    }
}

/** شاشة القفل البيومتري */
@Composable
private fun LockScreen(activity: FragmentActivity, onUnlock: () -> Unit) {
    val helper = remember { BiometricAuthHelper() }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
            Text("التطبيق مقفل", style = MaterialTheme.typography.headlineSmall)
            Text(
                "استخدم البصمة أو الوجه للمتابعة",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = {
                if (helper.canAuthenticate(activity)) {
                    helper.authenticate(activity, onSuccess = onUnlock, onError = { error = it })
                } else {
                    error = "لا يتوفر مستشعر بيومتري مسجل"
                }
            }) {
                Text("فتح القفل")
            }
        }
    }
}

/** الشريط السفلي — 7 شاشات رئيسية حسب التعليمات */
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

/** مضيف التنقل الكامل */
@Composable
private fun GalaxyNavHost(
    activity: FragmentActivity,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenSites = { navController.navigate(Routes.SITES) },
                onOpenGalaxy = { navController.navigate(Routes.GALAXY) }
            )
        }
        composable(Routes.SITES) {
            SitesScreen(
                onOpenSite = { navController.navigate(Routes.siteDetails(it)) },
                onAddSite = { navController.navigate(Routes.siteForm()) }
            )
        }
        composable(Routes.GALAXY) {
            GalaxyScreen(
                onOpenSite = { navController.navigate(Routes.siteDetails(it)) },
                onOpenCalculator = { navController.navigate(Routes.linkCalculator(it)) }
            )
        }
        composable(Routes.NEEDS) { NeedsScreen() }
        composable(Routes.MAINTENANCE) { MaintenanceScreen() }
        composable(Routes.REPORTS) { ReportsScreen() }
        composable(Routes.SETTINGS) { SettingsScreen(activity) }
        composable(
            route = Routes.SITE_DETAILS,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) { entry ->
            SiteDetailsScreen(
                siteId = entry.arguments?.getLong("siteId") ?: 0L,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.siteForm(it)) }
            )
        }
        composable(
            route = Routes.SITE_FORM,
            arguments = listOf(navArgument("siteId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            SiteFormScreen(
                siteId = entry.arguments?.getLong("siteId") ?: -1L,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.LINK_CALCULATOR,
            arguments = listOf(navArgument("linkId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { entry ->
            LinkCalculatorScreen(
                linkId = entry.arguments?.getLong("linkId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
