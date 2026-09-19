package com.majarra.galaxy.ui.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
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
import com.majarra.galaxy.ui.anim.GalaxySnackbarHost
import com.majarra.galaxy.ui.anim.PinDots
import com.majarra.galaxy.ui.anim.galaxyPressGlow
import com.majarra.galaxy.ui.screens.categories.CategoriesScreen
import com.majarra.galaxy.ui.screens.emergency.EmergencyVisitScreen
import com.majarra.galaxy.ui.screens.galaxy.GalaxyNetworkScreen
import com.majarra.galaxy.ui.screens.materials.MaterialsCatalogScreen
import com.majarra.galaxy.ui.screens.settings.SettingsScreen
import com.majarra.galaxy.ui.screens.sites.SiteDetailsScreen
import com.majarra.galaxy.ui.screens.sites.SitesScreen
import com.majarra.galaxy.ui.screens.stats.StatsScreen

/**
 * مسارات التنقل — النسخة 2.5: المواقع، المجرة (شبكة الروابط)،
 * التفاصيل، التصنيفات، المواد الموحدة، الإحصائيات، الإعدادات،
 * والنزول الطارئ.
 */
object Routes {
    const val SITES = "sites"
    const val GALAXY_NETWORK = "galaxy_network"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val MATERIALS = "materials"
    const val SITE_DETAILS = "site_details/{siteId}"
    const val EMERGENCY_VISIT = "emergency_visit/{siteId}"

    fun siteDetails(id: Long) = "site_details/$id"
    fun emergencyVisit(id: Long) = "emergency_visit/$id"

    val topLevel = setOf(SITES, GALAXY_NETWORK, STATS, SETTINGS)
}

/** عنصر تبويب */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.SITES, "المواقع", Icons.Filled.CellTower),
    TabItem(Routes.GALAXY_NETWORK, "المجرة", Icons.Filled.Hub),
    TabItem(Routes.STATS, "الإحصائيات", Icons.Filled.BarChart),
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

    // معاينة الرجوع التكبيرية (اختيار 33 = خيار 4 من اختيارات الجولة
    // الثالثة): أثناء إيماءة الرجوع تتقلص الشاشة الحالية قليلًا
    // وتخفت، فإن اكتملت الإيماءة يحدث الرجوع فعليًا وإلا تعود
    // بالنابض. يتطلب `android:enableOnBackInvokedCallback` في المنفست
    // (مضاف) ويعمل على أندرويد 13+ ويتجاهله الأقدم بهدوء.
    //
    // ملاحظة تركيبية مهمة: المعالج يُركَّب «بعد» الـ Scaffold حتى
    // يُسجَّل عند مشتت الرجوع بعد معالج مضيف التنقل نفسه (الأخير
    // تسجيلًا يُقدَّم)، فيأخذ أسبقية الإيماءة ويعرض المعاينة ثم ينفذ
    // الرجوع بنفسه. يُعطَّل في جذر المكدس حتى يخرج التطبيق طبيعيًا.
    var backProgress by remember { mutableStateOf(0f) }
    val smoothBack by animateFloatAsState(
        targetValue = backProgress,
        animationSpec = tween(130),
        label = "back-progress-smooth"
    )

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.graphicsLayer {
            val p = smoothBack
            scaleX = 1f - 0.08f * p
            scaleY = 1f - 0.08f * p
            translationY = -8.dp.toPx() * p
            alpha = 1f - 0.2f * p
            if (p > 0f) {
                clip = true
                shape = RoundedCornerShape(24.dp * p)
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        // سنابار بشريط مهلة متناقص (اختيارات 2.3 — مقترح 12)
        snackbarHost = { GalaxySnackbarHost(snackbarHostState) },
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
    PredictiveBackHandler(enabled = navController.previousBackStackEntry != null) { progressFlow ->
        try {
            progressFlow.collect { event -> backProgress = event.progress }
            // اكتملت الإيماءة: رجوع خطوة
            backProgress = 0f
            navController.popBackStack()
        } catch (e: CancellationException) {
            // أُلغيت الإيماءة في منتصفها — الشاشة تعود لنصابها
            backProgress = 0f
        }
    }
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
            // كوكبة الرمز: كل رقم يُدخل يُشعل نجمته ويمد خطًا نحو
            // التالية حتى تكتمل فتومض (اختيار 47 — ترقية مقترح 4)
            PinDots(pin = pin)
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
            // زر الفتح مع توهج الضغط (اختيارات 2.3 — مقترح 1)
            val lockInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    if (verifyPin(pin)) onUnlock() else error = wrongPinMessage
                },
                enabled = pin.length >= 4,
                interactionSource = lockInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .galaxyPressGlow(lockInteraction)
            ) {
                Text(stringResource(R.string.lock_button))
            }
        }
    }
}

/**
 * الشريط السفلي — أربعة تبويبات منذ النسخة 2.5 (المواقع، المجرة،
 * الإحصائيات، الإعدادات) مع مؤشر الجولة الثالثة (اختيار 31 = خيار 2):
 * حبة سماوية متوهجة تنزلق بنابض بين التبويبات، والأيقونة النشطة
 * تتحول للون داكن فوق المؤشر. المواقع تُقاس فعليًا عند التخطيط
 * (`onPlaced`) فيصح الانزلاق في اتجاهي العرض دون افتراضات.
 */
@Composable
private fun GalaxyBottomBar(navController: NavHostController, currentRoute: String?) {
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val density = LocalDensity.current

    // قياسات التخطيط: موضع الشريط ثم موضع كل تبويب وعرضه (بكسل جذر)
    val barLeftPx = remember { mutableStateOf(0f) }
    val itemLayouts = remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) }

    val item = itemLayouts.value[selectedIndex]
    val relativeLeftPx = (item?.first ?: 0f) - barLeftPx.value
    val itemWidthPx = item?.second ?: 0f
    val pillWidth = 56.dp
    val indicatorLeft by animateDpAsState(
        targetValue = with(density) { relativeLeftPx.toDp() } +
            (with(density) { itemWidthPx.toDp() } - pillWidth) / 2,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "nav-indicator-left"
    )
    val sky = MaterialTheme.colorScheme.primary
    val activeTint = MaterialTheme.colorScheme.onPrimary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier
                .fillMaxWidth()
                .onPlaced { barLeftPx.value = it.positionInRoot().x }
        ) {
            // الحبة المنزلاقة المتوهجة — تحت المحتوى (ترتيب الأشقاء).
            // المرتكز TopEnd: في اتجاه التطبيق العربي (القسري) هو الزاوية
            // العليا اليسرى فعليًا، فتصبح إزاحة `indicatorLeft` المحسوبة
            // بالبكسل من الحافة اليسرى صحيحة فيزيائيًا.
            if (item != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .absoluteOffset(x = indicatorLeft, y = 6.dp)
                        .width(pillWidth)
                        .height(32.dp)
                        .shadow(
                            elevation = 9.dp,
                            shape = RoundedCornerShape(99.dp),
                            ambientColor = sky.copy(alpha = 0.55f),
                            spotColor = sky.copy(alpha = 0.55f)
                        )
                        .clip(RoundedCornerShape(99.dp))
                        .background(sky)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) activeTint else idleTint,
                        animationSpec = tween(220),
                        label = "nav-tint-$index"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onPlaced { c ->
                                val measured = c.positionInRoot().x to c.size.width.toFloat()
                                if (itemLayouts.value[index] != measured) {
                                    itemLayouts.value = itemLayouts.value + (index to measured)
                                }
                            }
                            // إصلاح UX: النقر على التبويب المفتوح أصلًا كان
                            // يعيد التنقل فيُعاد بناء الشاشة وتظهر حركة
                            // الدخول مرة أخرى بلا فائدة. النقر الآن معطّل
                            // على التبويب النشط (بلا تأثير لمعان كاذب).
                            .clickable(enabled = !selected) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Icon(tab.icon, contentDescription = tab.label, tint = tint)
                            }
                            Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * مضيف التنقل — انتقالات «تلاشي وتحجيم» بين كل الوجهات
 * (اختيارات 2.3 — مقترح 7): الشاشة القديمة تتلاشى وتصغر 96%
 * بينما الجديدة تدخل من 96% إلى حجمها الكامل.
 *
 * ملاحظة (تنفيذ تعليمات هذه الجلسة): فتح الموقع صار يتم بانميشن واحد
 * فقط — انتقال التنقل هذا. طبقة «تحول الحاوية» السابقة كانت تضيف
 * انميشنًا ثانيًا فوقه مع اختفاء مفاجئ بينهما فأُزيلت.
 */
@Composable
private fun GalaxyNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SITES,
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)) },
        exitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)) },
        popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)) }
    ) {
        composable(Routes.SITES) {
            SitesScreen(
                onOpenSite = { navController.navigate(Routes.siteDetails(it)) },
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onOpenMaterials = { navController.navigate(Routes.MATERIALS) }
            )
        }
        // واجهة المجرة (النسخة 2.5 — تعليمات هذه الجلسة): شبكة المواقع
        // وروابطها؛ «الانتقال للموقع» يفتح صفحة التفاصيل نفسها.
        composable(Routes.GALAXY_NETWORK) {
            GalaxyNetworkScreen(
                onOpenSite = { navController.navigate(Routes.siteDetails(it)) },
                snackbarHostState = snackbarHostState
            )
        }
        composable(Routes.STATS) { StatsScreen() }
        // الإعدادات تتشارك مضيف السنابار العام حتى تُعرض رسائل نجاحها
        // كسنابار خفيفة بدل حوار يوقف المستخدم
        composable(Routes.SETTINGS) { SettingsScreen(snackbarHostState = snackbarHostState) }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MATERIALS) {
            MaterialsCatalogScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.SITE_DETAILS,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) {
            SiteDetailsScreen(
                onBack = { navController.popBackStack() },
                onOpenEmergency = { navController.navigate(Routes.emergencyVisit(it)) },
                snackbarHostState = snackbarHostState
            )
        }
        // شاشة النزول الطارئ/الاستكشاف (النسخة 2.4 — تعليمات هذه الجلسة)
        composable(
            route = Routes.EMERGENCY_VISIT,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) {
            EmergencyVisitScreen(
                onBack = { navController.popBackStack() },
                snackbarHostState = snackbarHostState
            )
        }
    }
}
