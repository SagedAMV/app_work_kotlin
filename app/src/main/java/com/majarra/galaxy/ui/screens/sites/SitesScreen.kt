package com.majarra.galaxy.ui.screens.sites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs
import kotlin.math.roundToInt
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

    init {
        viewModelScope.launch {
            runCatching { _dueSites.value = checkMaintenanceDue() }
        }
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
                saveSite(Site(name = name, notes = notes, categoryId = categoryId))
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

    /** أرشفة موقع من القائمة مباشرة (سحب البطاقة — اختيار 42) */
    fun archive(site: Site) {
        viewModelScope.launch { archiveSite(site, archived = true) }
    }

    /** حذف نهائي بعد تأكيد (سحب البطاقة — اختيار 42) */
    fun delete(site: Site) {
        viewModelScope.launch { deleteSite(site) }
    }
}

/** شاشة قائمة المواقع: شريط مستحق + بحث + فلاتر تصنيف/أرشيف + قائمة */
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
    var showAdd by rememberSaveable { mutableStateOf(false) }

    // نسخة محلية: الخصائص المفوّضة (by) لا تدعم الإسناد الذكي في
    // الوصول للحقول الفرعية، فننسخ إلى val محلي أولًا.
    val currentFilter = filter
    val showArchived = currentFilter is ListFilter.Archived

    // ── حالة انيميشنات الإصدار 2.3 (اختيارات المستخدم) ──
    // ملاحظة (تنفيذ تعليمات هذه الجلسة): «تحول الحاوية» السابق كان يمدد
    // البطاقة حتى تملأ الشاشة ثم يحدث التنقل بانتقاله الخاص — انميشنان
    // متتابعان مع اختفاء مفاجئ. أُزيل بالكامل: فتح الموقع الآن يتم
    // بانميشن واحد فقط (انتقال التنقل في المضيف).
    // انهيار ارتفاع العنصر قبل إزالته من البيانات (مقترح 14)
    var collapsingId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    // أيام الاستحقاق لكل موقع — للحلقات العدّادة وشارات الحالة على
    // البطاقات (اختيارا 37 و45 من الجولة الثالثة). المؤرشفة بلا مواعيد.
    val dueDaysById = remember(dueSites) {
        dueSites.associate { it.siteId to it.dueDate.daysFromNow() }
    }
    // موقع بانتظار تأكيد الحذف بعد سحبه (اختيار 42)
    var siteToDelete by remember { mutableStateOf<Site?>(null) }
    // مفتاح الظهور المتتابع: يتغير مع البحث/الفلتر فتعاد حركة الدخول (مقترح 13)
    val staggerTrigger = "$query|" + when (currentFilter) {
        is ListFilter.Active -> "all"
        is ListFilter.ByCategory -> "cat-${currentFilter.categoryId}"
        is ListFilter.Archived -> "archived"
    }
    // إصلاح تعثّر التمرير (2.9.3): حالة القائمة تُقرأ منها
    // `isScrollInProgress` — أي عنصر يُركَّب أثناء تمرير نشط يظهر
    // فورًا في `StaggeredItem` بلا تأخير ولا حركة (تتبّع «العناصر
    // الظاهرة» القديم في مجموعة خارجية صار غير ضروري وحُذف).
    val listState = rememberLazyListState()

    Scaffold(
        floatingActionButton = {
            if (!showArchived) {
                // زر إضافة متمدّد: نقرة تفتح شريطه ونقرة تنفّذ (مقترح 2)
                GalaxyExpandingFab(
                    icon = Icons.Filled.Add,
                    primaryLabel = "موقع جديد",
                    onPrimary = { showAdd = true },
                    secondaryLabel = "المواد الموحدة",
                    onSecondary = onOpenMaterials
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
            // إصلاح واجهة: كان العنوان وزرّا الرأس أبناءَ Row مباشرين مع
            // SpaceBetween، فيتوزع الثلاثة على كامل العرض ويبقى أحد الزرين
            // معلقًا في منتصف الشاشة. الآن الزرّان مجموعة واحدة في النهاية
            // والعنوان في البداية — صف رأس متوازن على أي عرض شاشة.
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

            // الشريط العلوي للمواقع المستحقة (إجابة الاسئله.md)
            DueSitesBanner(
                dueSites = dueSites,
                visible = !showArchived,
                onOpenSite = onOpenSite
            )

            val searchEnabled = currentFilter is ListFilter.Active
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
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                enabled = searchEnabled
            )

            // شرائح الفلترة: الكل + التصنيفات + المؤرشفة
            LazyRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = currentFilter is ListFilter.Active,
                        onClick = { viewModel.setFilter(ListFilter.Active) },
                        label = { Text("الكل") }
                    )
                }
                items(categories, key = { "cat-${it.id}" }) { category ->
                    FilterChip(
                        selected = currentFilter is ListFilter.ByCategory && currentFilter.categoryId == category.id,
                        onClick = { viewModel.setFilter(ListFilter.ByCategory(category.id)) },
                        label = { Text(category.name) },
                        leadingIcon = { ColorDot(category.colorHex) }
                    )
                }
                item {
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

            if (sites.isEmpty()) {
                EmptyState(
                    icon = if (showArchived) Icons.Filled.Archive else Icons.Filled.CellTower,
                    title = when {
                        showArchived -> "لا توجد مواقع مؤرشفة"
                        query.isBlank() -> "لا توجد مواقع هنا بعد"
                        else -> "لا توجد مواقع مطابقة"
                    },
                    subtitle = when {
                        showArchived -> "المواقع المؤرشفة تظهر هنا ويمكن استعادتها"
                        query.isBlank() -> "أضف موقعًا جديدًا بزر «موقع جديد»"
                        else -> "غيّر كلمة البحث"
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(sites, key = { _, site -> site.id }) { index, site ->
                        // ظهور متتابع عند الفتح/البحث (مقترح 13)، وانهيار
                        // ارتفاع عند الاستعادة قبل خروج العنصر (مقترح 14).
                        // `immediate`: أثناء التمرير النشط يظهر العنصر فورًا —
                        // لا تأخير ولا انيميشن (إصلاح تعثّر التمرير 2.9.3).
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
                                    // سحب البطاقة (اختيار 42): يسارًا أرشفة،
                                    // يمينًا حذف بعد تأكيد
                                    onSwipeArchive = {
                                        collapsingId = site.id
                                        scope.launch {
                                            delay(260)
                                            viewModel.archive(site)
                                            collapsingId = null
                                        }
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

    if (showAdd) {
        AddSiteDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { name, notes, categoryId, reportError ->
                viewModel.addSite(
                    name = name,
                    notes = notes,
                    categoryId = categoryId,
                    onSaved = { showAdd = false },
                    onError = reportError
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

/* ═══════════════════ الشريط العلوي للصيانة المستحقة ═══════════════════ */

/**
 * شريط علوي يعرض المواقع المتأخرة والقريبة الصيانة (إجابة الاسئله.md).
 * الأحمر = متأخر، الكهرماني = قريب. النقر يفتح الموقع مباشرة.
 * انيميشن 2.3 (مقترح 11): ينزلق من الأعلى ثم ينبض مرة واحدة بهالة
 * حمراء إن وُجد موقع متجاوز للموعد، وشارات الأيام تتنفس لونيًا (مقترح 16).
 */
@Composable
private fun DueSitesBanner(
    dueSites: List<DueSite>,
    visible: Boolean,
    onOpenSite: (Long) -> Unit
) {
    val bannerVisible = visible && dueSites.isNotEmpty()
    val hasOverdue = dueSites.any { it.dueDate.daysFromNow() < 0 }
    val pulseAlpha = rememberAlertPulse(active = bannerVisible && hasOverdue)
    AnimatedVisibility(
        visible = bannerVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        GalaxyCard(
            modifier = Modifier
                .padding(bottom = 10.dp)
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
                        // شارة تتنفس لونيًا للمتأخر (مقترح 16)
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

/* ═══════════════════ صف موقع في القائمة ═══════════════════ */

/* ألوان الخلفية المنكشفة أثناء السحب — ثوابت ملف لأنها تُستعمل في
 * صندوقَي الكشف وفي توهج إطار الموقع المتأخر معًا. */
private val SwipeDeleteColor = Color(0xFFFF5A5A)
private val SwipeArchiveColor = Color(0xFFFFC857)

/** نصف خلفية «الحذف» المنكشفة — شفافته تتبع مسافة السحب الموجبة */
@Composable
private fun RowScope.SwipeDeleteReveal(reveal: Float) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(SwipeDeleteColor.copy(alpha = 0.12f + 0.78f * reveal)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer { alpha = reveal }
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("حذف", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** نصف خلفية «الأرشفة» المنكشفة — شفافته تتبع مسافة السحب السالبة */
@Composable
private fun RowScope.SwipeArchiveReveal(reveal: Float) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(SwipeArchiveColor.copy(alpha = 0.10f + 0.72f * reveal)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer { alpha = reveal }
        ) {
            Icon(Icons.Filled.Archive, contentDescription = null, tint = Color(0xFF3A2A00), modifier = Modifier.size(18.dp))
            Text("أرشفة", color = Color(0xFF3A2A00), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * صف موقع — النسخة 2.7 (جلسة تنفيذ اختيارات الاختيار 1 ثلاثي):
 * - المشكلة 1 (تمرير القائمة ينحرف): السحب الجانبي أصبح مقفل المحور —
 *   لا يبدأ إلا بعد أن تتجاوز الحركة الأفقية عتبة النظام وهي الغالبة؛
 *   الحركة العمودية لا تُستهلك في البطاقة أبدًا فتصل كاملة لقائمة
 *   `LazyColumn`. كان `detectDragGestures` القديم يلتقط أي اتجاه
 *   ويستهلك الحدث فيسرق النزول في القائمة.
 * - المشكلة 2 (السحب عكس الإصبع): الإزاحة الآن بـ `absoluteOffset`
 *   الفيزيائية التي لا تعكسها اتجاهات التخطيط — كانت `offset` تقلب
 *   القيمة الموجبة في اتجاه العربية (الموجب يدفع يسارًا في RTL)
 *   بينما إشارات اللمس فيزيائية دائمًا. كما رُتّب نصفا الخلفية
 *   المنكشفة حسب اتجاه التخطيط حتى يطابق الفعل المنكشف جهة السحب.
 * - إصلاح 2.9.4 (سحب أقصر وبلا ارتعاش): الإزاحة صارت حالة سنكرونية
 *   تُكتب مباشرة في حلقة الإيماءة بدل إطلاق كوروتين `snapTo` لكل حدث
 *   لمس (كان إلغاء كل مهمة قبل اكتمالها يُضيع دلتات حركة فيلزم سحب
 *   أطول بكثير من العتبة الاسمية ويرتعش). أُضيفت أيضًا عتبة «نفضة»:
 *   حركة سريعة قصيرة تفعل الفعل دون إكمال مسافة العتبة، وخُفّضت عتبة
 *   المسافة قليلًا. الحركات بعد الإفلات (تجاوز ثم حذف/أرشفة، أو عودة
 *   بنابض) تبقى متحركة عبر مهمة واحدة قابلة للإلغاء.
 * - ثوابت من الإصدارات السابقة: بطاقة بزجاجية خفيفة (اختيار 45)،
 *   شريط جانبي بلون التصنيف، إطار متوهج للمتأخر، حلقة عد تنازلي
 *   (اختيار 37)، مقاومة بعد الحد الأقصى وعودة بنابض، والمؤرشفة بلا
 *   سحب (زر استعادة فقط).
 */
@Composable
private fun SiteRow(
    site: Site,
    categories: List<Category>,
    archived: Boolean,
    dueDays: Long?,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val category = categories.firstOrNull { it.id == site.categoryId }
    val categoryColor = category?.let { c ->
        runCatching { Color(android.graphics.Color.parseColor(c.colorHex)) }
            .getOrDefault(MaterialTheme.colorScheme.primary)
    } ?: MaterialTheme.colorScheme.outline
    // إصلاح اتساق: «مستحق اليوم» ليس «متأخرًا». الشريط العلوي وتبويب
    // الصيانة يعتبران التأخر بسبب سالب فقط، أما البطاقة فكانت تصبغ
    // اليوم الحالي بالأحمر وتُشعل التنبيه. الآن الأحمر للتأخر الفعلي
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

    val density = LocalDensity.current
    val threshold = with(density) { 120.dp.toPx() }
    // عتبة النفضة: حركة سريعة قصيرة تكفي لتفعيل الفعل دون إكمال مسافة
    // العتبة — بالبكسل/ثانية مشتقة من dp فتتناسب مع كثافة الشاشة.
    // تُقارن بها سرعة الإصبع اللحظية عند الإفلات، مع تجاوز أدنى 12٪ من
    // عرض البطاقة في اتجاه الفعل حتى لا تُفعّل نفضة عابرة في مكانها.
    val flingVelocity = with(density) { 750.dp.toPx() }
    // إصلاح جذري (2.9.4) لمشكلتَي السحب: «يجب السحب من طرف الشاشة للطرف»
    // و«الارتعاش السريع الغريب». السبب الجذري واحد: الإزاحة كانت تُكتب
    // عبر `scope.launch { offsetX.snapTo(...) }` لكل حدث لمس — إلغاء المهمة
    // السابقة قبل اكتمالها كان يُضيع دلتات حركة (فتتزحزح البطاقة أبطأ
    // بكثير من الإصبع ويلزم سحب أطول بكثير من العتبة الاسمية) والتحديث
    // غير المتزامن كان يُرّعش البطاقة والخلفية المنكشفة معًا. الحل:
    // الإزاحة حالة سنكرونية تُكتب مباشرة داخل حلقة الإيماءة فتلصق
    // البطاقة بالإصبع 1:1، بينما تبقى حركات ما بعد الإفلات (التجاوز
    // ثم الحذف/الأرشفة، أو العودة بنابض) متحركة عبر مهمة واحدة قابلة
    // للإلغاء. ملاحظة: `snapTo` مُعلّقة ولا تُستدعى مباشرة داخل نطاق
    // الإيماءات المقيّد التعليق، لذا كان هذا التصميم البديل لازمًا.
    var offsetX by remember(site.id) { mutableFloatStateOf(0f) }
    var settleJob by remember(site.id) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // نسب انكشاف الفعلين حسب مسافة السحب (0..1)
    val deleteReveal = (offsetX / threshold).coerceIn(0f, 1f)
    val archiveReveal = (-offsetX / threshold).coerceIn(0f, 1f)

    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = Modifier.fillMaxWidth()) {
        // الخلفية المنكشفة — ترتيب نصفيها حسب اتجاه التخطيط (تتمة
        // اختيار المشكلة 2): بالإزاحة المطلقة السحب يمينًا يزيح البطاقة
        // يمينًا فيكشف النصف الأيسر دائمًا، وعتبة السحب الموجبة تعني
        // «حذفًا» — فيجب أن يحمل النصف الأيسر صندوق الحذف. الأيسر هو
        // آخر أبناء `Row` في الاتجاه العربي وأولهم في اللاتيني، لذا
        // يُعكس الترتيب هنا. السحب يسارًا يكشف الأرشفة بالتماثل.
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            if (layoutDirection == LayoutDirection.Rtl) {
                SwipeArchiveReveal(archiveReveal)
                SwipeDeleteReveal(deleteReveal)
            } else {
                SwipeDeleteReveal(deleteReveal)
                SwipeArchiveReveal(archiveReveal)
            }
        }

        // البطاقة الأمامية المنزلقة
        Box(
            modifier = Modifier
                // اختيار المشكلة 2: إزاحة مطلقة فيزيائية لا تعكسها
                // اتجاهات التخطيط — البطاقة تتبع الإصبع في العربية
                // واللاتينية معًا (كانت `offset` تقلب الموجب في RTL).
                .absoluteOffset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(site.id) {
                    // اختيار المشكلة 1: قفل المحور الأفقي. السحب الجانبي
                    // يبدأ فقط بعد أن تتجاوز الحركة الأفقية عتبة النظام
                    // وهي الغالبة؛ أي حركة عمودية لا تُستهلك هنا أبدًا
                    // فتصل كاملة لقائمة التمرير وتنزل القائمة طبيعيًا.
                    // (بدل `detectDragGestures` التي كانت تلتقط كل اتجاه
                    // وتستهلك الحدث فيسرق النزول في القائمة.)
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // لمسة جديدة: أوقف أي حركة استقرار جارية (عودة
                        // بنابض أو تجاوز) فنُمسك البطاقة حيث هي فورًا
                        settleJob?.cancel()
                        var accX = 0f
                        var accY = 0f
                        var dragging = false
                        var lastX = down.position.x
                        var lastTime = down.uptimeMillis
                        // سرعة الإصبع اللحظية (متوسط أسّي) لكشف النفضات
                        var velocity = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            // نتتبع إصبع البداية فقط ونهمل أي أصابع أخرى
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) break
                            if (!dragging) {
                                // قبل التسليح: إن خطفت جهة أخرى الحدث
                                // (القائمة بدأت تمريرًا) ننسحب بلا أثر
                                if (change.isConsumed) break
                                accX += change.position.x - change.previousPosition.x
                                accY += change.position.y - change.previousPosition.y
                                if (abs(accX) > touchSlop && abs(accX) > abs(accY)) {
                                    // غلبة أفقية: بدأ سحب البطاقة
                                    dragging = true
                                    lastX = change.position.x
                                    lastTime = change.uptimeMillis
                                    velocity = 0f
                                    change.consume()
                                } else if (abs(accY) > touchSlop) {
                                    // غلبة عمودية: الإيماءة للقائمة نهائيًا
                                    break
                                }
                            } else {
                                val dx = change.position.x - lastX
                                lastX = change.position.x
                                // تقدير السرعة: إزاحة الحدث على زمنه، مع
                                // تنعيم أسّي يمتص اهتزاز القياسات الفردية
                                val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                                velocity = velocity * 0.72f + (dx * 1000f / dt) * 0.28f
                                lastTime = change.uptimeMillis
                                // مقاومة خفيفة بعد 60٪ من عرض البطاقة
                                val limit = size.width * 0.6f
                                // كتابة سنكرونية مباشرة داخل حلقة الإيماءة
                                // (2.9.4): لا إلغاء/إطلاق كوروتين لكل حدث،
                                // فلا تضيع دلتات حركة ولا ارتعاش — البطاقة
                                // تلتصق بالإصبع 1:1 والسحب القصير قصير فعلًا.
                                offsetX = (offsetX + dx).coerceIn(-limit, limit)
                                change.consume()
                            }
                        }
                        if (dragging) {
                            // نهاية السحب: عتبة مسافة أو نفضة سريعة قصيرة —
                            // الاتجاه الموجب حذف والسالب أرشفة كما قبل.
                            // النفضة تُشترط مع تجاوز أدنى 12٪ من العرض حتى
                            // لا تُفعّل حركة عابرة في مكانها.
                            val x = offsetX
                            val v = velocity
                            val flingDelete = v > flingVelocity && x > size.width * 0.12f
                            val flingArchive = v < -flingVelocity && x < -size.width * 0.12f
                            settleJob = scope.launch {
                                when {
                                    x > threshold || flingDelete -> {
                                        animate(x, size.width.toFloat() * 0.85f, animationSpec = tween(180)) { value, _ ->
                                            offsetX = value
                                        }
                                        onSwipeDelete()
                                        // إن أُلغي الحذف من حوار التأكيد تعود
                                        // البطاقة لمكانها بدل بقائها مزاحة
                                        offsetX = 0f
                                    }
                                    x < -threshold || flingArchive -> {
                                        animate(x, -size.width.toFloat() * 0.85f, animationSpec = tween(180)) { value, _ ->
                                            offsetX = value
                                        }
                                        onSwipeArchive()
                                    }
                                    else -> animate(
                                        x,
                                        0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            GalaxyCard(
                onClick = onClick,
                modifier = Modifier.drawBehind {
                    // زجاجية خفيفة من لون التصنيف (اختيار 45)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(categoryColor.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.65f, size.height)
                        )
                    )
                    // شريط جانبي بلون التصنيف على جهة البداية
                    // (يمين البطاقة في الاتجاه العربي)
                    val barWidth = 4.dp.toPx()
                    drawRoundRect(
                        color = categoryColor,
                        topLeft = Offset(size.width - barWidth, 10.dp.toPx()),
                        size = Size(barWidth, (size.height - 20.dp.toPx()).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(barWidth / 2f)
                    )
                    // توهج إطار أحمر للمتأخر عن موعده
                    if (overdue) {
                        drawRoundRect(
                            color = SwipeDeleteColor.copy(alpha = 0.55f),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(width = 1.8.dp.toPx())
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
    // إصلاح اتساق: «مستحق اليوم» ليس «متأخرًا». الشريط العلوي وتبويب
    // الصيانة يعتبران التأخر بسبب سالب فقط، أما البطاقة فكانت تصبغ
    // اليوم الحالي بالأحمر وتُشعل التنبيه. الآن الأحمر للتأخر الفعلي
    // فقط، والاستحقاق القريب يبقى بالكهرماني (حلقة العدّاد).
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

/* ═══════════════════ حوار إضافة موقع ═══════════════════ */

/**
 * حوار إضافة موقع — الاسم إلزامي، والملاحظات والتصنيف اختياريان.
 * أخطاء التحقق تظهر داخل الحوار بدل إغلاقه.
 * يظهر بكشف دائري يتمدد من أسفل الشاشة (اختيار 32 من الجولة
 * الثالثة) عبر `GalaxyRevealDialog` بدل الظهور المفاجئ.
 */
@Composable
private fun AddSiteDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, categoryId: Long?, reportError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                Text("موقع جديد", style = MaterialTheme.typography.headlineSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                )
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
                // اختيار التصنيف (اختياري) — القائمة تظهر فوق الزر
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
                // أزرار الحوار — توهج الضغط على الحفظ (مقترح 1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { requestClose(onDismiss) }) { Text("إلغاء") }
                    GlowButton(
                        onClick = { onSave(name, notes, categoryId) { message -> error = message } },
                        enabled = cleanName.isNotBlank() && !nameTooLong && !notesTooLong
                    ) { Text("حفظ") }
                }
            }
        }
    }
}
