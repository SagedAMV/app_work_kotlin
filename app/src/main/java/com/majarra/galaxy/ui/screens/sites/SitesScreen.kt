package com.majarra.galaxy.ui.screens.sites

import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.usecase.ArchiveSiteUseCase
import com.majarra.galaxy.domain.usecase.CheckMaintenanceDueUseCase
import com.majarra.galaxy.domain.usecase.DeleteSiteUseCase
import com.majarra.galaxy.domain.usecase.DueSite
import com.majarra.galaxy.domain.usecase.ObserveSitesUseCase
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import com.majarra.galaxy.domain.usecase.SiteInputValidator
import com.majarra.galaxy.ui.anim.BreathingDueBadge
import com.majarra.galaxy.ui.anim.DueCountdownRing
import com.majarra.galaxy.ui.anim.GalaxyExpandingFab
import com.majarra.galaxy.ui.anim.GalaxyRevealDialog
import com.majarra.galaxy.ui.anim.GalaxyStarBurst
import com.majarra.galaxy.ui.anim.GlowButton
import com.majarra.galaxy.ui.anim.StaggeredItem
import com.majarra.galaxy.ui.anim.rememberAlertPulse
import com.majarra.galaxy.ui.components.ColorDot
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.formatDate
import com.majarra.galaxy.util.daysFromNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * مرشح القائمة: الكل / تصنيف محدد / المؤرشفة.
 * غير خاص لأن الـ ViewModel العام يعرّضه في توقيعه.
 */
sealed class ListFilter {
    object Active : ListFilter()
    data class ByCategory(val categoryId: Long) : ListFilter()
    object Archived : ListFilter()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SitesViewModel @Inject constructor(
    private val observeSites: ObserveSitesUseCase,
    private val saveSite: SaveSiteUseCase,
    private val archiveSite: ArchiveSiteUseCase,
    private val deleteSite: DeleteSiteUseCase,
    categoryRepo: CategoryRepository,
    private val checkMaintenanceDue: CheckMaintenanceDueUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** المرشح الحالي: نشطة/حسب تصنيف/مؤرشفة */
    private val _filter = MutableStateFlow<ListFilter>(ListFilter.Active)
    val filter: StateFlow<ListFilter> = _filter

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * القائمة المعروضة تتبدل حسب المرشح: البحث يعمل على المواقع النشطة
     * فقط (نطاق البحث حسب الاسئله.md: الاسم فقط).
     */
    val sites: StateFlow<List<Site>> = combine(_filter, _query) { f, q -> f to q }
        .flatMapLatest { (f, q) -> sourceFor(f, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sourceFor(filter: ListFilter, q: String): Flow<List<Site>> = when (filter) {
        is ListFilter.Active -> observeSites.search(q)
        is ListFilter.ByCategory -> observeSites.byCategory(filter.categoryId)
        is ListFilter.Archived -> observeSites.archived()
    }

    /** المواقع المستحقة للصيانة — للشريط العلوي في الشاشة الرئيسية */
    private val _dueSites = MutableStateFlow<List<DueSite>>(emptyList())
    val dueSites: StateFlow<List<DueSite>> = _dueSites

    /**
     * معرّف الموقع المُضاف للتو (اختيارا مواقع-01 و04): تلتقطه البطاقة
     * لتشغيل نبض الوصول ولمعة الاكتشاف ثم تطلب مسحه. الحفظ يعيد معرّف
     * الصف الجديد من `SaveSiteUseCase` مباشرة.
     */
    private val _justAddedId = MutableStateFlow<Long?>(null)
    val justAddedId: StateFlow<Long?> = _justAddedId

    init {
        refreshDue()
    }

    /** (إعادة) حساب الاستحقاق — البداية والسحب المداري للتحديث (مواقع-28) */
    fun refreshDue() {
        viewModelScope.launch {
            runCatching { _dueSites.value = checkMaintenanceDue() }
        }
    }

    fun clearJustAdded() {
        _justAddedId.value = null
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(filter: ListFilter) {
        _filter.value = filter
        _query.value = "" // البحث لا يشمل المؤرشفة/التصنيفات: نبدأ نظيفًا
    }

    /**
     * إضافة موقع جديد — الاسم إلزامي والملاحظات والتصنيف اختيارية.
     * رسائل أخطاء التحقق تُعاد للواجهة لتظهر تحت الحقول.
     */
    fun addSite(name: String, notes: String, categoryId: Long?, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val id = saveSite(Site(name = name, notes = notes, categoryId = categoryId))
                _justAddedId.value = id
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** استعادة موقع من الأرشيف */
    fun restore(site: Site) {
        viewModelScope.launch { archiveSite(site, archived = false) }
    }

    /** أرشفة موقع — تُستدعى بعد اكتمال الطيران المداري (مواقع-09) */
    fun archive(site: Site) {
        viewModelScope.launch { archiveSite(site, archived = true) }
    }

    /** حذف نهائي بعد تأكيد (سحب البطاقة — اختيار 42) */
    fun delete(site: Site) {
        viewModelScope.launch { deleteSite(site) }
    }
}

/**
 * شاشة قائمة المواقع — جلسة تنفيذ اختيارات واجهة المواقع
 * (اختيارات_واجهة_المواقع.md): نُفذت الاختيارات التسعة عشر على بنية
 * الشاشة القائمة دون تغيير قاعدة البيانات أو منطق الأعمال:
 * 01 نبض الوصول + 04 لمعة الاكتشاف (معًا على البطاقة الجديدة)،
 * 02 الشريط الجانبي النامي، 03 تنفس إطار المتأخر، 05 انكماش اللمس،
 * 09 الأرشفة المدارية نحو شريحة «المؤرشفة»، 10 خط التقدم الزمني،
 * 13 العداد القلابي (في `BreathingDueBadge`)، 16 طي الشريط لحبة،
 * 17 حقل البحث المتحول، 18 المؤشر المنزلق بين الشرائح،
 * 20 التلاشي السياقي للنتائج (قائمة الاتحاد أدناه)، 22 موجة المسح،
 * 24 حقول الحوار المتتابعة، 25 نجوم الحفظ، 26 دخول الزر المزدوج،
 * 28 السحب المداري للتحديث، 29 كوكبة الحالة الفارغة، 30 سديم الخلفية.
 */
@Composable
fun SitesScreen(
    onOpenSite: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenMaterials: () -> Unit,
    viewModel: SitesViewModel = hiltViewModel()
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val dueSites by viewModel.dueSites.collectAsStateWithLifecycle()
    val justAddedId by viewModel.justAddedId.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    // نسخة محلية: الخصائص المفوّضة (by) لا تدعم الإسناد الذكي في
    // الوصول للحقول الفرعية، فننسخ إلى val محلي أولًا.
    val currentFilter = filter
    val showArchived = currentFilter is ListFilter.Archived

    // ── حالة الانيميشنات ──
    // انهيار ارتفاع العنصر قبل إزالته من البيانات (مقترح 14 — مسار
    // الاستعادة فقط؛ الأرشفة صارت طيرانًا مداريًا اختيار مواقع-09)
    var collapsingId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    // أيام الاستحقاق لكل موقع — للحلقات العدّادة وشارات الحالة على
    // البطاقات (اختيارا 37 و45 من الجولة الثالثة). المؤرشفة بلا مواعيد.
    val dueDaysById = remember(dueSites) {
        dueSites.associate { it.siteId to it.dueDate.daysFromNow() }
    }
    // موقع بانتظار تأكيد الحذف بعد سحبه (اختيار 42)
    var siteToDelete by remember { mutableStateOf<Site?>(null) }
    // مفتاح الظهور المتتابع: يتغير مع الفلتر فتعاد حركة الدخول.
    // ملاحظة (اختيار مواقع-20): النص لم يعد جزءًا من المفتاح — مع
    // التلاشي السياقي للنتائج لا معنى لإعادة إدخال البطاقات الباقية
    // مع كل ضغطة مفتاح؛ البطاقات الجديدة تدخل بحركتها تلقائيًا.
    val staggerTrigger = when (currentFilter) {
        is ListFilter.Active -> "all"
        is ListFilter.ByCategory -> "cat-${currentFilter.categoryId}"
        is ListFilter.Archived -> "archived"
    }
    // إصلاح تعثّر التمرير (2.9.3): حالة القائمة تُقرأ منها
    // `isScrollInProgress` — أي عنصر يُركَّب أثناء تمرير نشط يظهر
    // فورًا في `StaggeredItem` بلا تأخير ولا حركة.
    val listState = rememberLazyListState()

    // ── إشارة التمرير (اختيارا مواقع-16 و17): انهيار الرأس عند
    // النزول وعودته عند الصعود أو بلوغ القمة ──
    var headerCompact by remember { mutableStateOf(false) }
    var lastScroll by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offsetPx) ->
                val (prevIndex, prevOffset) = lastScroll
                // إشارة الاتجاه من فرق الفهرس أولًا ثم الإزاحة
                val delta = if (index != prevIndex) {
                    (index - prevIndex) * 4000 + (offsetPx - prevOffset)
                } else {
                    offsetPx - prevOffset
                }
                if (delta > 30) headerCompact = true
                else if (delta < -30 || index == 0) headerCompact = false
                lastScroll = index to offsetPx
            }
    }

    // ── الأرشفة المدارية (مواقع-09): موضع شريحة «المؤرشفة» ونبضها ──
    var archiveTarget by remember { mutableStateOf<Offset?>(null) }
    // حد الطيران: قمة القائمة (الـ LazyColumn يقصّ ما يخرج عن حدوده،
    // فيُقصَد أعلى نقطة داخل الحدود وتكمل نبضة الشريحة القصة)
    var listTopY by remember { mutableStateOf(0f) }
    var chipPulse by remember { mutableIntStateOf(0) }
    val chipPulseScale = remember { Animatable(1f) }
    LaunchedEffect(chipPulse) {
        if (chipPulse > 0) {
            chipPulseScale.snapTo(1.22f)
            chipPulseScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    // ── المؤشر المنزلق بين الشرائح (مواقع-18) ──
    val chipBounds = remember { mutableStateMapOf<String, Rect>() }
    var chipsOrigin by remember { mutableStateOf(Offset.Zero) }
    val selectedChipKey = when (currentFilter) {
        is ListFilter.Active -> "all"
        is ListFilter.ByCategory -> "cat-${currentFilter.categoryId}"
        is ListFilter.Archived -> "archived"
    }
    val indicatorX = remember { Animatable(0f) }
    val indicatorW = remember { Animatable(0f) }
    val indicatorTarget = chipBounds[selectedChipKey]
    LaunchedEffect(indicatorTarget, chipsOrigin) {
        val target = indicatorTarget ?: return@LaunchedEffect
        coroutineScope {
            launch {
                indicatorX.animateTo(
                    target.left - chipsOrigin.x,
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                )
            }
            launch {
                indicatorW.animateTo(
                    target.width,
                    spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }
    val density = LocalDensity.current

    // ── التلاشي السياقي للنتائج (مواقع-20): قائمة الاتحاد تحتفظ
    // بالعناصر الخارجة من البحث 420 مللي ثانية تتلاشى فيها بمكانها
    // قبل إزالتها فعليًا — الغربلة تُرى ولا تقفز. الباقون يحافظون
    // على ترتيبهم السابق والجديد يُلحق بالنهاية. يسري في وضع «الكل»
    // فقط؛ تبديل الفلتر يبقى بظهوره المتتابع المعتاد. ──
    var unionSites by remember { mutableStateOf<List<Site>>(emptyList()) }
    val activeMode = currentFilter is ListFilter.Active
    LaunchedEffect(sites, activeMode) {
        if (!activeMode || sites.isEmpty() && unionSites.isEmpty()) {
            unionSites = sites
            return@LaunchedEffect
        }
        val currentIds = sites.mapTo(HashSet()) { it.id }
        val prev = unionSites
        if (prev.isEmpty()) {
            unionSites = sites
            return@LaunchedEffect
        }
        val dying = prev.filter { it.id !in currentIds }
        if (dying.isEmpty()) {
            unionSites = sites
            return@LaunchedEffect
        }
        val prevOrder = HashMap<Long, Int>(prev.size)
        prev.forEachIndexed { i, s -> prevOrder[s.id] = i }
        unionSites = (sites + dying).distinctBy { it.id }
            .sortedBy { prevOrder[it.id] ?: Int.MAX_VALUE }
        delay(420)
        // التنظيف بعد اكتمال التلاشي — إن لم تُلغِ الحلقةَ ضغطةٌ جديدة
        val latestIds = sites.mapTo(HashSet()) { it.id }
        unionSites = unionSites.filter { it.id in latestIds }
    }
    // `ifEmpty`: الإطار الأول قبل امتلاء الاتحاد يعرض القائمة الفعلية
    // فلا تومض الحالة الفارغة عند فتح الشاشة
    val displaySites = if (activeMode) unionSites.ifEmpty { sites } else sites
    val currentSiteIds = remember(sites) { sites.mapTo(HashSet()) { it.id } }

    // ── موجة مسح البحث (مواقع-22) ──
    var clearWave by remember { mutableIntStateOf(0) }
    val waveProgress = remember { Animatable(2f) }
    LaunchedEffect(clearWave) {
        if (clearWave > 0) {
            waveProgress.snapTo(-0.6f)
            waveProgress.animateTo(1.6f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    // ── السحب المداري للتحديث (مواقع-28) ──
    val pull = remember { Animatable(0f) }
    var refreshing by remember { mutableStateOf(false) }
    val spinTransition = rememberInfiniteTransition(label = "orbit-spin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000), LinearEasing),
        label = "orbit-angle"
    )
    // اكتمال التحديث: وصول بيانات جديدة أو مهلة أمان إن تساوت النتيجة
    LaunchedEffect(refreshing, dueSites) {
        if (refreshing) {
            delay(700)
            refreshing = false
            pull.animateTo(0f, tween(240))
        }
    }

    // ── سديم الخلفية المتفاعل (مواقع-30): إزاحة النجوم من حالة
    // التمرير مباشرة — القراءة في طور الرسم فقط ──
    val nebulaOffset by remember {
        derivedStateOf { -(listState.firstVisibleItemScrollOffset * 0.15f) }
    }

    Scaffold(
        floatingActionButton = {
            if (!showArchived) {
                // زر إضافة متمدّد بدخول نابض (مواقع-26): نقرة تفتح شريطه
                // ونقرة تنفّذ (مقترح 2)
                GalaxyExpandingFab(
                    icon = Icons.Filled.Add,
                    primaryLabel = "موقع جديد",
                    onPrimary = { showAdd = true },
                    secondaryLabel = "المواد الموحدة",
                    onSecondary = onOpenMaterials,
                    entrance = true
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // صف رأس متوازن على أي عرض شاشة (إصلاح 2.9.0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showArchived) "المواقع المؤرشفة" else "المواقع",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp)
                )
                // إدارة التصنيفات + كتالوج المواد الموحد (النسخة 2.2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenMaterials) {
                        Icon(Icons.Filled.Inventory2, contentDescription = "المواد الموحدة")
                    }
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.Filled.Category, contentDescription = "إدارة التصنيفات")
                    }
                }
            }

            // الشريط العلوي للمواقع المستحقة — ينطوي لحبة عند النزول
            // (مواقع-16) ويعود عند الصعود
            DueSitesBanner(
                dueSites = dueSites,
                visible = !showArchived,
                compact = headerCompact,
                onOpenSite = onOpenSite,
                onExpand = {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            )

            val searchEnabled = currentFilter is ListFilter.Active
            // حقل البحث المتحول (مواقع-17): ينضغط لدائرة عدسة عند
            // النزول ويتمدد عند الصعود أو لمسه (النقر يعيد للقمة)
            Box(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                AnimatedContent(
                    targetState = headerCompact && searchEnabled && query.isBlank(),
                    label = "search-mode",
                    transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(150))) }
                ) { collapsed ->
                    if (collapsed) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = "ابحث بالاسم",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // موجة المسح (مواقع-22) تُرسم خلف الحقل نفسه —
                        // حاوية الحقل شفافة فتظهر الموجة من خلالها
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    val f = waveProgress.value
                                    if (f > -0.5f && f < 1.6f) {
                                        val band = size.width * 0.55f
                                        val x = size.width * f - band / 2f
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color(0x3393A4BD),
                                                    Color.Transparent
                                                ),
                                                startX = x,
                                                endX = x + band
                                            )
                                        )
                                    }
                                }
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = viewModel::setQuery,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        when {
                                            showArchived -> "البحث غير متاح داخل المؤرشفة"
                                            currentFilter is ListFilter.ByCategory -> "البحث متاح في تبويب «الكل» فقط"
                                            else -> "ابحث بالاسم…"
                                        }
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchEnabled && query.isNotBlank()) {
                                        IconButton(onClick = {
                                            viewModel.setQuery("")
                                            clearWave++
                                        }) {
                                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                                        }
                                    }
                                },
                                singleLine = true,
                                enabled = searchEnabled
                            )
                        }
                    }
                }
            }

            // شرائح الفلترة + المؤشر المنزلق (مواقع-18)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp)
                    .onGloballyPositioned { chipsOrigin = it.positionInRoot() }
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = currentFilter is ListFilter.Active,
                            onClick = { viewModel.setFilter(ListFilter.Active) },
                            label = { Text("الكل") },
                            modifier = Modifier.onGloballyPositioned {
                                chipBounds["all"] = it.boundsInRoot()
                            }
                        )
                    }
                    items(categories, key = { "cat-${it.id}" }) { category ->
                        FilterChip(
                            selected = currentFilter is ListFilter.ByCategory && currentFilter.categoryId == category.id,
                            onClick = { viewModel.setFilter(ListFilter.ByCategory(category.id)) },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(category.colorHex) },
                            modifier = Modifier.onGloballyPositioned {
                                chipBounds["cat-${category.id}"] = it.boundsInRoot()
                            }
                        )
                    }
                    item {
                        // شريحة المؤرشفة — هدف الطيران المداري (مواقع-09):
                        // يُقاس مركزها وتتلقى نبضة الاستقبال
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned {
                                    archiveTarget = it.positionInRoot() +
                                        Offset(it.size.width / 2f, it.size.height / 2f)
                                    chipBounds["archived"] = it.boundsInRoot()
                                }
                                .graphicsLayer {
                                    scaleX = chipPulseScale.value
                                    scaleY = chipPulseScale.value
                                }
                        ) {
                            FilterChip(
                                selected = showArchived,
                                onClick = { viewModel.setFilter(ListFilter.Archived) },
                                label = { Text("المؤرشفة") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                // الخط المتوهج المنزلق أسفل الشريحة النشطة
                if (indicatorTarget != null && indicatorW.value > 1f) {
                    val indicatorWidthDp = with(density) { indicatorW.value.toDp() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    indicatorX.value.roundToInt(),
                                    (indicatorTarget.bottom - chipsOrigin.y).roundToInt() + 2.dp.roundToPx()
                                )
                            }
                            .requiredSize(indicatorWidthDp, 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .drawBehind {
                                drawRect(color = MaterialTheme.colorScheme.primary)
                                drawRect(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                    )
                }
            }

            // مؤشر السحب المداري للتحديث (مواقع-28) — ارتفاعه يتبع
            // مسافة السحب ويتموضع بين الشرائح والقائمة
            if (!showArchived) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { pull.value.toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    if (pull.value > 8f) {
                        OrbitalRefreshIndicator(
                            angle = pull.value * 2.6f + if (refreshing) spinAngle else 0f,
                            armed = pull.value > 64f || refreshing
                        )
                    }
                }
            }

            if (displaySites.isEmpty()) {
                when {
                    showArchived -> EmptyState(
                        icon = Icons.Filled.Archive,
                        title = "لا توجد مواقع مؤرشفة",
                        subtitle = "المواقع المؤرشفة تظهر هنا ويمكن استعادتها"
                    )
                    query.isNotBlank() -> EmptyState(
                        icon = Icons.Filled.CellTower,
                        title = "لا توجد مواقع مطابقة",
                        subtitle = "غيّر كلمة البحث"
                    )
                    else -> ConstellationEmptyState(
                        title = "لا توجد مواقع هنا بعد",
                        subtitle = "أضف موقعًا جديدًا بزر «موقع جديد»"
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { listTopY = it.positionInRoot().y }
                ) {
                    // سديم الخلفية (مواقع-30) خلف القائمة — نجوم ثابتة
                    // الإحداثيات تنزلق عكس التمرير بعمق خفيف
                    NebulaStars(offsetY = nebulaOffset)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        // السحب المداري (مواقع-28): يُعترض السحب للأسفل
                        // عند القمة فقط، وكل حركة أخرى تصل للقائمة كاملة
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var accX = 0f
                                    var accY = 0f
                                    var pulling = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) break
                                        if (!pulling) {
                                            if (change.isConsumed) break
                                            accX += change.position.x - change.previousPosition.x
                                            accY += change.position.y - change.previousPosition.y
                                            val atTop = !listState.canScrollBackward
                                            if (atTop && !refreshing && accY > touchSlop && accY > abs(accX)) {
                                                pulling = true
                                                change.consume()
                                            } else if (abs(accX) > touchSlop || accY < -touchSlop) {
                                                break
                                            }
                                        } else {
                                            val dy = change.position.y - change.previousPosition.y
                                            pull.snapTo(
                                                (pull.value + dy * 0.55f).coerceIn(0f, 130f)
                                            )
                                            change.consume()
                                        }
                                    }
                                    if (pulling) {
                                        if (pull.value > 64f && !refreshing) {
                                            refreshing = true
                                            viewModel.refreshDue()
                                            scope.launch { pull.animateTo(56f, tween(180)) }
                                        } else if (!refreshing) {
                                            scope.launch { pull.animateTo(0f, tween(220)) }
                                        } else {
                                            scope.launch { pull.animateTo(56f, tween(180)) }
                                        }
                                    }
                                }
                            }
                    ) {
                        itemsIndexed(displaySites, key = { _, site -> site.id }) { index, site ->
                            // التلاشي السياقي (مواقع-20): العنصر الخارج من
                            // نتائج البحث يبقى في قائمة الاتحاد ويخرج
                            // بتلاشٍ وتقليص لطيفين بدل الاختفاء اللحظي
                            val alive = !activeMode || site.id in currentSiteIds
                            AnimatedVisibility(
                                visible = alive,
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(240)) + scaleOut(
                                    targetScale = 0.97f,
                                    animationSpec = tween(240)
                                )
                            ) {
                                // ظهور متتابع عند الفتح/تبديل الفلتر، وانهيار
                                // ارتفاع عند الاستعادة قبل خروج العنصر.
                                // `immediate`: أثناء التمرير النشط يظهر فورًا.
                                StaggeredItem(
                                    index = index,
                                    trigger = staggerTrigger,
                                    immediate = listState.isScrollInProgress
                                ) {
                                    AnimatedVisibility(
                                        visible = collapsingId != site.id,
                                        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) + fadeIn(tween(200)),
                                        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(240)) + fadeOut(tween(200))
                                    ) {
                                        SiteRow(
                                            site = site,
                                            categories = categories,
                                            archived = showArchived,
                                            dueDays = if (showArchived) null else dueDaysById[site.id],
                                            entryIndex = index,
                                            immediateEntry = listState.isScrollInProgress,
                                            justAdded = !showArchived && justAddedId == site.id,
                                            onJustAddedShown = viewModel::clearJustAdded,
                                            archiveTarget = archiveTarget,
                                            listTopY = listTopY,
                                            onArchivePulse = { chipPulse++ },
                                            onClick = {
                                                // فتح التفاصيل مباشرة — انميشن واحد هو انتقال
                                                // التنقل نفسه (إصلاح الانميشن المزدوج)
                                                onOpenSite(site.id)
                                            },
                                            onRestore = {
                                                collapsingId = site.id
                                                scope.launch {
                                                    delay(280)
                                                    viewModel.restore(site)
                                                    collapsingId = null
                                                }
                                            },
                                            // الأرشفة المدارية (مواقع-09): تلتزم البطاقة
                                            // الطيران ثم تُؤرشف — لا انهيار رأسي هنا
                                            onSwipeArchive = { doomed ->
                                                viewModel.archive(doomed)
                                            },
                                            onSwipeDelete = { siteToDelete = site }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSiteDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { name, notes, categoryId, onSaved, onError ->
                viewModel.addSite(
                    name = name,
                    notes = notes,
                    categoryId = categoryId,
                    onSaved = onSaved,
                    onError = onError
                )
            }
        )
    }

    // تأكيد الحذف النهائي بعد سحبة اليمين (اختيار 42)
    siteToDelete?.let { doomed ->
        ConfirmDialog(
            title = "حذف الموقع",
            text = "سيُحذف الموقع «${doomed.name}» مع كل تفاصيله ومرفقاته وسجل صيانته نهائيًا.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.delete(doomed)
                siteToDelete = null
            },
            onDismiss = { siteToDelete = null }
        )
    }
}

/* ═══════════════ الشريط العلوي للصيانة المستحقة ═══════════════ */

/**
 * شريط علوي يعرض المواقع المتأخرة والقريبة الصيانة (إجابة الاسئله.md).
 * الأحمر = متأخر، الكهرماني = قريب. النقر يفتح الموقع مباشرة.
 * انيميشن 2.3 (مقترح 11): ينزلق من الأعلى ثم ينبض مرة واحدة بهالة
 * حمراء إن وُجد موقع متجاوز للموعد، وشارات الأيام تتنفس لونيًا
 * (مقترح 16). جلسة الاختيارات (مواقع-16): ينطوي عند النزول في
 * التمرير إلى حبة عائمة بعدّاد المستحقين، والنقر عليها يعيد للقمة
 * ويتمدد الشريط كاملًا.
 */
@Composable
private fun DueSitesBanner(
    dueSites: List<DueSite>,
    visible: Boolean,
    compact: Boolean,
    onOpenSite: (Long) -> Unit,
    onExpand: () -> Unit
) {
    val bannerVisible = visible && dueSites.isNotEmpty()
    val hasOverdue = dueSites.any { it.dueDate.daysFromNow() < 0 }
    val pulseAlpha = rememberAlertPulse(active = bannerVisible && hasOverdue)
    AnimatedVisibility(
        visible = bannerVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .animateContentSize(animationSpec = tween(260))
        ) {
            AnimatedContent(
                targetState = compact,
                label = "banner-mode",
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(150))) }
            ) { compacted ->
                if (compacted) {
                    // الحبة العائمة: عدّاد المستحقين بلون الخطورة
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = onExpand,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (hasOverdue) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "صيانة مستحقة: ${dueSites.size}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                } else {
                    GalaxyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (pulseAlpha > 0f) {
                                    drawRoundRect(
                                        color = Color(0xFFFF5A5A).copy(alpha = 0.6f * pulseAlpha),
                                        cornerRadius = CornerRadius(16.dp.toPx()),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("صيانة مستحقة", style = MaterialTheme.typography.titleMedium)
                            }
                            dueSites.take(4).forEach { due ->
                                val days = due.dueDate.daysFromNow()
                                Row(
                                    // النقر يفتح الموقع مباشرة (السلوك الموثق في تعليق
                                    // الشريط — كان المعطل غير موصولًا قبل 2.6)
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenSite(due.siteId) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // حلقة عد تنازلي تستنزف وتتغير ألوانها حسب
                                    // الخطورة (اختيار 37 من الجولة الثالثة)
                                    DueCountdownRing(days = days, size = 38.dp)
                                    Text(
                                        due.siteName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )
                                    // الشارة تتنفس لونيًا للمتأخر (مقترح 16) مع
                                    // العداد القلابي للأيام (مواقع-13)
                                    BreathingDueBadge(days)
                                }
                            }
                            if (dueSites.size > 4) {
                                Text(
                                    "و${dueSites.size - 4} مواقع أخرى…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════ صف موقع في القائمة ═══════════════ */

/* ألوان الخلفية المنكشفة أثناء السحب — ثوابت ملف لأنها تُستعمل في
 * صندوقَي الكشف وفي توهج إطار الموقع المتأخر معًا. */
private val SwipeDeleteColor = Color(0xFFFF5A5A)
private val SwipeArchiveColor = Color(0xFFFFC857)
/* لون اللمعة والخط الزمني البعيد — سماوي الهوية (مواقع-04 و10) */
private val DiscoverySky = Color(0xFF38BDF8)

/* ── ثوابت سحب بطاقة الموقع (إصلاح 2.10.3 الجذري) ──
 * العتبة: 28٪ من عرض البطاقة بحدّ أقصى 110dp.
 * النفضة: حركة سريعة (600dp/s) تتجاوز 8٪ من العرض في اتجاه الفعل
 * تفعّله فورًا دون إكمال مسافة العتبة. */
private const val SWIPE_THRESHOLD_FRACTION = 0.28f
private val SWIPE_THRESHOLD_CAP = 110.dp
private val SWIPE_FLING_VELOCITY = 600.dp
private const val SWIPE_FLING_MIN_FRACTION = 0.08f

/** عتبة تفعيل الفعل بالبكسل — نسبة من العرض الكامل للبطاقة بحدّ أعلى */
private fun swipeThresholdPx(fullWidthPx: Float, density: Density): Float =
    minOf(fullWidthPx * SWIPE_THRESHOLD_FRACTION, with(density) { SWIPE_THRESHOLD_CAP.toPx() })

/**
 * نسبة انكشاف الفعل (0..1) من حالة الإزاحة — تُستدعى داخل كتل الرسم
 * والطبقات فقط (DrawScope/GraphicsLayerScope) فلا تُعيد تركيب الشاشة
 * أثناء السحب إطلاقًا.
 */
private fun swipeReveal(offsetPx: Float, fullWidthPx: Float, density: Density): Float =
    (offsetPx / swipeThresholdPx(fullWidthPx, density)).coerceIn(0f, 1f)

/**
 * نصف خلفية «الحذف» المنكشفة — شفافيته تتبع مسافة السحب الموجبة.
 * القراءة داخل `drawBehind`/`graphicsLayer` مباشرة من حالة الإزاحة:
 * تحديث السحب يُبطل الرسم فقط دون أي إعادة تخطيط أو تركيب.
 */
@Composable
private fun RowScope.SwipeDeleteReveal(offsetX: MutableFloatState) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .drawBehind {
                drawRect(SwipeDeleteColor.copy(alpha = 0.12f + 0.78f * swipeReveal(offsetX.floatValue, size.width * 2f, this)))
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer {
                alpha = swipeReveal(offsetX.floatValue, size.width * 2f, this)
            }
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("حذف", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** نصف خلفية «الأرشفة» المنكشفة — شفافيته تتبع مسافة السحب السالبة */
@Composable
private fun RowScope.SwipeArchiveReveal(offsetX: MutableFloatState) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .drawBehind {
                drawRect(SwipeArchiveColor.copy(alpha = 0.10f + 0.72f * swipeReveal(-offsetX.floatValue, size.width * 2f, this)))
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer {
                alpha = swipeReveal(-offsetX.floatValue, size.width * 2f, this)
            }
        ) {
            Icon(Icons.Filled.Archive, contentDescription = null, tint = Color(0xFF3A2A00), modifier = Modifier.size(18.dp))
            Text("أرشفة", color = Color(0xFF3A2A00), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * صف موقع — جلسة اختيارات واجهة المواقع فوق إصلاح 2.10.3 الجذري
 * (كاشف الإيماءة على الحاوية الثابتة، `graphicsLayer` بدل
 * `absoluteOffset`، قفل المحور الأفقي، عتبة 28٪/نفضة 600dp/s).
 *
 * المضاف في هذه الجلسة:
 * - مواقع-01/04: نبض الوصول (هبوط نابض + هالة التصنيف) ولمعة
 *   الاكتشاف للمسح الضوئي لمرة واحدة — على البطاقة الجديدة فقط.
 * - مواقع-02: الشريط الجانبي ينمو من الأعلى عند الدخول بتتابع.
 * - مواقع-03: إطار المتأخر يتنفس بين ألفا 0.25 و0.75.
 * - مواقع-05: انكماش اللمس 97.5٪ بإطار سماوي خافت.
 * - مواقع-09: سحبة اليسار تُطلق طيرانًا مداريًا نحو شريحة
 *   «المؤرشفة» (مسار مقوس + دوران + تصغير) بدل الانزلاق الأفقي.
 * - مواقع-10: خط تقدم زمني أسفل البطاقة بنافذة الثلاثين يومًا.
 */
@Composable
private fun SiteRow(
    site: Site,
    categories: List<Category>,
    archived: Boolean,
    dueDays: Long?,
    entryIndex: Int,
    immediateEntry: Boolean,
    justAdded: Boolean,
    onJustAddedShown: () -> Unit,
    archiveTarget: Offset?,
    listTopY: Float,
    onArchivePulse: () -> Unit,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onSwipeArchive: (Site) -> Unit,
    onSwipeDelete: () -> Unit
) {
    val category = categories.firstOrNull { it.id == site.categoryId }
    val categoryColor = category?.let { c ->
        runCatching { Color(android.graphics.Color.parseColor(c.colorHex)) }
            .getOrDefault(MaterialTheme.colorScheme.primary)
    } ?: MaterialTheme.colorScheme.outline
    // إصلاح اتساق: «مستحق اليوم» ليس «متأخرًا». الأحمر للتأخر الفعلي
    // فقط، والاستحقاق القريب يبقى بالكهرماني (حلقة العدّاد).
    val overdue = dueDays != null && dueDays < 0

    if (archived) {
        // المؤرشفة: بطاقة عادية بزر استعادة — لا سحب هنا
        GalaxyCard(onClick = onClick) {
            SiteCardContent(
                site = site,
                category = category,
                dueDays = null,
                archived = true,
                onRestore = onRestore
            )
        }
        return
    }

    // ── حالة السحب (2.10.3) ──
    val offsetXState = remember(site.id) { mutableFloatStateOf(0f) }
    var settleJob by remember(site.id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val layoutDirection = LocalLayoutDirection.current

    // ── مواقع-01/04: الوصول واللمعة — البطاقة الجديدة فقط ──
    val arrival = remember(site.id) { Animatable(if (justAdded) 0f else 1f) }
    val halo = remember(site.id) { Animatable(0f) }
    val sweep = remember(site.id) { Animatable(-1f) }
    LaunchedEffect(justAdded) {
        if (justAdded) {
            arrival.snapTo(0f)
            sweep.snapTo(-1f)
            arrival.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
            // الهالة واللمعة معًا بعد الاستقرار
            launch {
                halo.animateTo(1f, tween(480))
                halo.animateTo(0f, tween(420))
            }
            sweep.animateTo(1.6f, tween(900, easing = FastOutSlowInEasing))
            onJustAddedShown()
        }
    }

    // ── مواقع-02: نمو الشريط الجانبي بتتابع الدخول ──
    var barStarted by remember(site.id) { mutableStateOf(immediateEntry) }
    LaunchedEffect(site.id) {
        if (!barStarted) {
            delay(minOf(entryIndex, 7) * 60L)
            barStarted = true
        }
    }
    val barFraction by animateFloatAsState(
        targetValue = if (barStarted) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "side-bar"
    )

    // ── مواقع-03: تنفس إطار المتأخر ──
    val breathTransition = rememberInfiniteTransition(label = "overdue-breath")
    val breathAlpha by breathTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath-alpha"
    )

    // ── مواقع-05: انكماش اللمس العميق ──
    val pressInteraction = remember { MutableInteractionSource() }
    val pressed by pressInteraction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(120),
        label = "press-scale"
    )

    // ── مواقع-09: الطيران المداري نحو شريحة المؤرشفة ──
    val flight = remember(site.id) { Animatable(0f) }
    var launching by remember(site.id) { mutableStateOf(false) }
    var cardCenter by remember(site.id) { mutableStateOf(Offset.Zero) }

    // ── مواقع-10: خط التقدم الزمني (نافذة 30 يومًا كلون الحلقة) ──
    val lineColor = when {
        dueDays == null -> Color.Transparent
        dueDays < 0 -> SwipeDeleteColor
        dueDays <= 7 -> SwipeArchiveColor
        else -> DiscoverySky
    }
    val lineFraction = if (dueDays == null) 0f
    else (dueDays.coerceAtLeast(0).toFloat() / 30f).coerceIn(0f, 1f)
    var lineStarted by remember(site.id) { mutableStateOf(false) }
    LaunchedEffect(site.id) {
        delay(200)
        lineStarted = true
    }
    val lineReveal by animateFloatAsState(
        targetValue = if (lineStarted) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "due-line"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                cardCenter = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)
            }
            // ═══ إصلاح السحب الجذري 2.10.3 (كاشف الإيماءة على الحاوية
            // الثابتة) — يُعلَّق أثناء الطيران المداري ═══
            .pointerInput(site.id) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    if (launching) return@awaitEachGesture
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // لمسة جديدة: أوقف أي حركة استقرار جارية فنُمسك
                    // البطاقة حيث هي فورًا
                    settleJob?.cancel()
                    var accX = 0f
                    var accY = 0f
                    var dragging = false
                    var lastX = down.position.x
                    val velocityTracker = VelocityTracker()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        if (launching) break
                        if (!dragging) {
                            if (change.isConsumed) break
                            accX += change.position.x - change.previousPosition.x
                            accY += change.position.y - change.previousPosition.y
                            if (abs(accX) > touchSlop && abs(accX) > abs(accY)) {
                                dragging = true
                                lastX = change.position.x
                                velocityTracker.resetTracking()
                                change.consume()
                            } else if (abs(accY) > touchSlop) {
                                break
                            }
                        } else {
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val limit = size.width * 0.6f
                            offsetXState.floatValue =
                                (offsetXState.floatValue + dx).coerceIn(-limit, limit)
                            change.consume()
                        }
                    }
                    if (dragging && !launching) {
                        val x = offsetXState.floatValue
                        val width = size.width.toFloat()
                        val threshold = swipeThresholdPx(width, this)
                        val velocity = velocityTracker.calculateVelocity().x
                        val flingVelocity = SWIPE_FLING_VELOCITY.toPx()
                        val flingDelete = velocity > flingVelocity && x > width * SWIPE_FLING_MIN_FRACTION
                        val flingArchive = velocity < -flingVelocity && x < -width * SWIPE_FLING_MIN_FRACTION
                        settleJob = scope.launch {
                            when {
                                x > threshold || flingDelete -> {
                                    animate(x, width * 0.85f, animationSpec = tween(180)) { value, _ ->
                                        offsetXState.floatValue = value
                                    }
                                    onSwipeDelete()
                                    animate(offsetXState.floatValue, 0f, animationSpec = tween(220)) { value, _ ->
                                        offsetXState.floatValue = value
                                    }
                                }
                                x < -threshold || flingArchive -> {
                                    // ═══ مواقع-09: الأرشفة المدارية ═══
                                    // بدل الانزلاق الأفقي: البطاقة تطير في
                                    // مسار مقوس نحو شريحة «المؤرشفة»
                                    offsetXState.floatValue = 0f
                                    launching = true
                                    flight.snapTo(0f)
                                    flight.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                                    onArchivePulse()
                                    onSwipeArchive(site)
                                }
                                else -> animate(
                                    x,
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) { value, _ ->
                                    offsetXState.floatValue = value
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // الخلفية المنكشفة — ترتيب نصفيها حسب اتجاه التخطيط (ثابت من 2.7)
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            if (layoutDirection == LayoutDirection.Rtl) {
                SwipeArchiveReveal(offsetXState)
                SwipeDeleteReveal(offsetXState)
            } else {
                SwipeDeleteReveal(offsetXState)
                SwipeArchiveReveal(offsetXState)
            }
        }

        // البطاقة الأمامية — إزاحة طور-الرسم. تُدمج في نفس الطبقة:
        // إزاحة السحب، انكماش اللمس (مواقع-05)، هبوط الوصول
        // (مواقع-01)، ومسار الطيران المداري (مواقع-09).
        Box(
            modifier = Modifier.graphicsLayer {
                val t = flight.value
                if (launching && t > 0f) {
                    val target = archiveTarget
                    if (target != null) {
                        val dx = target.x - cardCenter.x
                        // لا نتجاوز قمة القائمة (القص) — نبضة الشريحة
                        // المنفصلة تكمل الإحساس بالوصول
                        val targetY = maxOf(target.y, listTopY + 10.dp.toPx())
                        val dy = targetY - cardCenter.y
                        translationX = dx * t
                        // قوس صاعد: رفع في منتصف المسار فوق الخط المستقيم
                        translationY = dy * t - 54.dp.toPx() * sin(PI.toFloat() * t).toFloat()
                        val s = 1f - 0.6f * t
                        scaleX = s
                        scaleY = s
                        rotationZ = -9f * t
                        alpha = 1f - 0.45f * t
                    } else {
                        // هدف غير مُقاس (حالة نادرة): خروج أفقي بسيط
                        translationX = -size.width * t
                        alpha = 1f - t
                    }
                } else {
                    translationX = offsetXState.floatValue
                    val drop = (1f - arrival.value) * -26.dp.toPx()
                    translationY = drop
                    val s = (0.97f + 0.03f * arrival.value.coerceAtMost(1f)) * pressScale
                    scaleX = s
                    scaleY = s
                }
            }
        ) {
            GalaxyCard(
                onClick = onClick,
                interactionSource = pressInteraction,
                modifier = Modifier
                    .drawBehind {
                        // زجاجية خفيفة من لون التصنيف (اختيار 45)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(categoryColor.copy(alpha = 0.10f), Color.Transparent),
                                start = Offset(0f, 0f),
                                end = Offset(size.width * 0.65f, size.height)
                            )
                        )
                        // مواقع-02: الشريط الجانبي النامي على جهة البداية
                        // (يمين البطاقة في الاتجاه العربي)
                        val barWidth = 4.dp.toPx()
                        val barHeight = (size.height - 20.dp.toPx()).coerceAtLeast(0f) * barFraction
                        drawRoundRect(
                            color = categoryColor,
                            topLeft = Offset(size.width - barWidth, 10.dp.toPx()),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f)
                        )
                        // مواقع-03: إطار المتأخر يتنفس بدل التوهج الثابت
                        if (overdue) {
                            drawRoundRect(
                                color = SwipeDeleteColor.copy(alpha = breathAlpha),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                        // مواقع-05: إطار سماوي خافت لحظة الضغط
                        if (pressed) {
                            drawRoundRect(
                                color = DiscoverySky.copy(alpha = 0.30f),
                                cornerRadius = CornerRadius(16.dp.toPx()),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }
                        // مواقع-01: هالة الوصول بلون التصنيف — مرة واحدة
                        val haloP = halo.value
                        if (haloP > 0f) {
                            drawRoundRect(
                                color = categoryColor.copy(alpha = 0.65f * (1f - haloP)),
                                cornerRadius = CornerRadius((16.dp + 3.dp * haloP).toPx()),
                                style = Stroke(width = 2.4.dp.toPx())
                            )
                        }
                        // مواقع-10: خط التقدم الزمني — يبدأ من جهة
                        // البداية (اليمين في RTL) بلون الخطورة نفسه
                        if (dueDays != null && lineReveal > 0f && lineFraction > 0f) {
                            val lineW = size.width * lineFraction * lineReveal
                            drawRoundRect(
                                color = lineColor.copy(alpha = 0.85f),
                                topLeft = Offset(size.width - lineW, size.height - 3.dp.toPx()),
                                size = Size(lineW, 3.dp.toPx()),
                                cornerRadius = CornerRadius(1.5.dp.toPx())
                            )
                        }
                    }
                    // مواقع-04: لمعة الاكتشاف — مسح ضوئي واحد فوق المحتوى
                    .drawWithContent {
                        drawContent()
                        val f = sweep.value
                        if (f > -0.6f && f < 1.7f) {
                            val band = size.width * 0.45f
                            val x = size.width * f - band / 2f
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        DiscoverySky.copy(alpha = 0.16f),
                                        DiscoverySky.copy(alpha = 0.30f),
                                        DiscoverySky.copy(alpha = 0.16f),
                                        Color.Transparent
                                    ),
                                    start = Offset(x, 0f),
                                    end = Offset(x + band, size.height * 0.4f)
                                )
                            )
                        }
                    }
            ) {
                SiteCardContent(
                    site = site,
                    category = category,
                    dueDays = dueDays,
                    archived = false,
                    onRestore = null
                )
            }
        }
    }
}

/** محتوى بطاقة الموقع — مشترك بين النشطة القابلة للسحب والمؤرشفة */
@Composable
private fun SiteCardContent(
    site: Site,
    category: Category?,
    dueDays: Long?,
    archived: Boolean,
    onRestore: (() -> Unit)?
) {
    // الأحمر للتأخر الفعلي فقط، ومستحق اليوم كهرماني (حلقة العدّاد).
    val overdue = dueDays != null && dueDays < 0
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (category != null) ColorDot(category.colorHex)
            Text(
                site.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (archived && onRestore != null) {
                OutlinedButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                    Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("استعادة", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            // حلقة العد التنازلي للاستحقاق (اختيار 37)
            if (dueDays != null) {
                DueCountdownRing(days = dueDays, size = 40.dp)
            }
        }
        if (site.notes.isNotBlank()) {
            Text(
                site.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "آخر تعديل: ${site.lastModified.formatDate()}" +
                    if (category != null) " — ${category.name}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f, fill = false)
            )
            // الشارة المتنفسة للمتأخر فقط — الحلقة تكفي لغيره (اختيار 45)
            if (overdue && dueDays != null) {
                BreathingDueBadge(dueDays)
            }
        }
    }
}

/* ═══════════════ مؤشر السحب المداري للتحديث (مواقع-28) ═══════════════ */

/**
 * نجمة مركزية يدور حولها كوكب صغير — الزاوية تتبع مسافة السحب أثناء
 * الجرّ ثم تدور ذاتيًا أثناء التحديث الفعلي.
 */
@Composable
private fun OrbitalRefreshIndicator(angle: Float, armed: Boolean) {
    Canvas(modifier = Modifier.size(34.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // هالة النجمة المركزية
        drawCircle(
            color = Color(0xFFFFC857).copy(alpha = if (armed) 0.35f else 0.18f),
            radius = 8.dp.toPx(),
            center = center
        )
        drawCircle(color = Color(0xFFFFC857), radius = 3.6.dp.toPx(), center = center)
        // الكوكب على مداره
        val rad = angle * PI.toFloat() / 180f
        val orbitR = 12.dp.toPx()
        drawCircle(
            color = DiscoverySky,
            radius = 2.4.dp.toPx(),
            center = Offset(
                center.x + kotlin.math.cos(rad) * orbitR,
                center.y + sin(rad) * orbitR
            )
        )
    }
}

/* ═══════════════ كوكبة الحالة الفارغة (مواقع-29) ═══════════════ */

/**
 * برج اتصالات من نقاط تضيء متتابعة وتُمد الخطوط بينها تدريجيًا —
 * إحداثيات ثابتة بلا عشوائية كعادة التطبيق. النصوص تظهر بعد اكتمال
 * الرسم بنمط `EmptyState` نفسه.
 */
@Composable
private fun ConstellationEmptyState(title: String, subtitle: String) {
    // نقاط البرج في صندوق 120×120 (قمة البرج، الكتف، المنتصف، القاعدة)
    val points = remember {
        listOf(
            Offset(60f, 14f), Offset(42f, 44f), Offset(78f, 44f),
            Offset(60f, 64f), Offset(44f, 98f), Offset(76f, 98f)
        )
    }
    // أضلاع البرج: قمة-كتفان-مثلث علوي، عمود فقري، وقاعدة
    val edges = remember {
        listOf(0 to 1, 0 to 2, 1 to 2, 0 to 3, 3 to 4, 3 to 5, 4 to 5)
    }
    var lit by remember { mutableIntStateOf(0) }
    val lineProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // إشعال النقاط متتابعة ثم مدّ الخطوط
        while (lit < points.size) {
            delay(230)
            lit++
        }
        lineProgress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
    }
    val measure = remember { PathMeasure() }

    var textsShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1200)
        textsShown = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val scaleF = size.width / 120f
            // المسار يُبنى بإحداثيات مقيّسة مباشرة — بلا مصفوفات
            val scaledPath = Path().apply {
                edges.forEach { (a, b) ->
                    moveTo(points[a].x * scaleF, points[a].y * scaleF)
                    lineTo(points[b].x * scaleF, points[b].y * scaleF)
                }
            }
            // الخطوط التدريجية عبر قياس المسار
            val fraction = lineProgress.value
            if (fraction > 0f) {
                measure.setPath(scaledPath, false)
                val segment = Path()
                measure.getSegment(0f, measure.length * fraction, segment, true)
                drawPath(
                    path = segment,
                    color = DiscoverySky.copy(alpha = 0.4f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            // النقاط: تُشعل واحدة تلو الأخرى بهالة خفيفة
            points.forEachIndexed { i, p ->
                if (i < lit) {
                    val c = Offset(p.x * scaleF, p.y * scaleF)
                    drawCircle(color = DiscoverySky.copy(alpha = 0.25f), radius = 6f * scaleF, center = c)
                    drawCircle(color = DiscoverySky, radius = 3f * scaleF, center = c)
                }
            }
        }
        AnimatedVisibility(visible = textsShown, enter = fadeIn(tween(350))) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* ═══════════════ سديم الخلفية المتفاعل (مواقع-30) ═══════════════ */

/**
 * نجوم خافتة ثابتة الإحداثيات (بلا عشوائية) تُسجَّل مرة واحدة في
 * `Picture` ثم تُزاح عكس التمرير بعمق خفيف — القراءة من حالة التمرير
 * في طور الرسم فقط فلا إعادة تركيب أثناء التمرير.
 */
@Composable
private fun NebulaStars(offsetY: Float) {
    val picture = remember {
        Picture().apply {
            val canvas = beginRecording(1080, 2600)
            val paint = Paint().apply { isAntiAlias = true }
            for (i in 0 until 72) {
                val x = ((i * 149 + 37) % 1080).toFloat()
                val y = ((i * 263 + 91) % 2600).toFloat()
                val radius = 1.1f + (i % 3) * 0.7f
                paint.color = when (i % 6) {
                    0 -> 0x5538BDF8 // سماوي خافت
                    1 -> 0x443DFB7F // أخضر خافت
                    2 -> 0x44FFC857 // كهرماني خافت
                    else -> 0x33FFFFFF // أبيض خافت
                }
                canvas.drawCircle(x, y, radius, paint)
            }
            endRecording()
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(0f, offsetY)
            canvas.nativeCanvas.drawPicture(picture)
            canvas.nativeCanvas.restore()
        }
    }
}

/* ═══════════════ حوار إضافة موقع ═══════════════ */

/**
 * حوار إضافة موقع — الاسم إلزامي، والملاحظات والتصنيف اختياريان.
 * أخطاء التحقق تظهر داخل الحوار بدل إغلاقه.
 * يظهر بكشف دائري يتمدد من أسفل الشاشة (اختيار 32) عبر
 * `GalaxyRevealDialog`. جلسة الاختيارات: عناصره تظهر متتابعة
 * (مواقع-24) مع تركيز تلقائي على حقل الاسم بعد اكتمال الدخول،
 * وعند نجاح الحفظ تتطاير 12 نجمة من زر الحفظ (مواقع-25) قبل
 * الإغلاق المتحرك.
 */
@Composable
private fun AddSiteDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, categoryId: Long?, onSaved: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var burst by remember { mutableIntStateOf(0) }
    val nameFocus = remember { FocusRequester() }
    val dialogScope = rememberCoroutineScope()

    val cleanName = remember(name) { name.trim().replace(Regex("\\s+"), " ") }
    val cleanNotes = remember(notes) { notes.trim().replace(Regex("\\s+"), " ") }
    val nameTooLong = cleanName.length > SiteInputValidator.MAX_NAME
    val notesTooLong = cleanNotes.length > SiteInputValidator.MAX_NOTES

    val selectedCategory = categories.firstOrNull { it.id == categoryId }

    GalaxyRevealDialog(onDismissRequest = onDismiss) { requestClose ->
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // مواقع-24: كل عنصر يصعد بعد سابقه — نفس مكوّن الظهور
                // المتتابع للقوائم بمفاتيح تركيبه الأولى
                StaggeredItem(index = 0, trigger = "dlg") {
                    Text("موقع جديد", style = MaterialTheme.typography.headlineSmall)
                }
                StaggeredItem(index = 1, trigger = "dlg") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            error = null
                        },
                        label = { Text("اسم الموقع") },
                        singleLine = true,
                        isError = error != null || nameTooLong,
                        supportingText = {
                            when {
                                error != null -> Text(error!!)
                                nameTooLong -> Text("الحد الأقصى ${SiteInputValidator.MAX_NAME} حرفًا")
                                else -> Text("${cleanName.length}/${SiteInputValidator.MAX_NAME}")
                            }
                        },
                        modifier = Modifier.focusRequester(nameFocus)
                    )
                }
                StaggeredItem(index = 2, trigger = "dlg") {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات (اختياري)") },
                        minLines = 2,
                        maxLines = 4,
                        isError = notesTooLong,
                        supportingText = {
                            if (notesTooLong) Text("الحد الأقصى ${SiteInputValidator.MAX_NOTES} حرفًا")
                            else Text("${cleanNotes.length}/${SiteInputValidator.MAX_NOTES}")
                        }
                    )
                }
                StaggeredItem(index = 3, trigger = "dlg") {
                    // اختيار التصنيف (اختياري) — القائمة تظهر فوق الزر
                    Box {
                        OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (selectedCategory != null) "التصنيف: ${selectedCategory.name}"
                                else "التصنيف: بلا تصنيف"
                            )
                        }
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("بلا تصنيف") },
                                onClick = {
                                    categoryId = null
                                    showCategoryMenu = false
                                }
                            )
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ColorDot(category.colorHex)
                                            Text(category.name)
                                        }
                                    },
                                    onClick = {
                                        categoryId = category.id
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                StaggeredItem(index = 4, trigger = "dlg") {
                    // أزرار الحوار — توهج الضغط على الحفظ (مقترح 1)
                    // ونجوم النجاح فوقه (مواقع-25)
                    Box(contentAlignment = Alignment.CenterEnd) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { requestClose(onDismiss) }) { Text("إلغاء") }
                            GlowButton(
                                onClick = {
                                    saving = true
                                    onSave(
                                        name, notes, categoryId,
                                        onSaved = {
                                            // مواقع-25: نجوم النجاح ثم الإغلاق
                                            // المتحرك بعد اكتمالها
                                            burst++
                                            dialogScope.launch {
                                                delay(620)
                                                requestClose(onDismiss)
                                            }
                                        },
                                        onError = { message ->
                                            error = message
                                            saving = false
                                        }
                                    )
                                },
                                enabled = cleanName.isNotBlank() && !nameTooLong && !notesTooLong && !saving
                            ) { Text("حفظ") }
                        }
                        GalaxyStarBurst(trigger = burst, modifier = Modifier.matchParentSize())
                    }
                }
            }
        }

        // التركيز على الاسم بعد استقرار الحقول المتتابعة
        LaunchedEffect(Unit) {
            delay(480)
            runCatching { nameFocus.requestFocus() }
        }
    }
}
