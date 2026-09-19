package com.majarra.galaxy.ui.screens.galaxy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteLink
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.LinkResult
import com.majarra.galaxy.domain.usecase.LinkSitesUseCase
import com.majarra.galaxy.domain.usecase.ObserveSiteLinksUseCase
import com.majarra.galaxy.domain.usecase.UnlinkSitesUseCase
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.util.NetworkLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/* ============================================================
 * واجهة المجرة (النسخة 2.5 — تعليمات هذه الجلسة):
 * عرض المواقع نقاطًا مترابطة بخطوط كشبكة اتصالات، مع تحريك وتكبير،
 * ونقرة على الموقع تفتح قائمة: «الانتقال للموقع» أو «ربط موقع».
 *
 * الرسم كله داخل Canvas واحد بإحداثيات «العالم» الافتراضية، ثم
 * تحويل واحد (إزاحة + تكبير) يطبَّق على الخطوط والعقد والأسماء
 * معًا، فتبقى متلاصقة مهما تحرك المستخدم أو كبّر.
 *
 * جلسة مقترحات واجهة المجرة (ما بعد 2.5): تنفيذ المقترحات الثلاثين
 * من «مقترحات_واجهة_المجرة.html» — كل مقترح مرقّم هنا برقمه في
 * ملف المقترحات ليسهل تعطيل أي اختيار مستقلًا:
 *  العقد: 1 تنفس ذاتي، 2 ذيل سحب، 3 هالة تصنيف، 4 موجة نقر،
 *        5 ظهور متدرج، 6 تمايل مرشحين، 7 نبضة قلب، 8 انهيار حذف
 *  الخطوط: 9 شرطات متدفقة، 10 تدرج لوني، 11 إشارة عابرة،
 *        12 رسم عند الدخول، 13 سماكة أثناء السحب، 14 امتداد الجديد
 *  السماء: 15 نجوم ثابتة، 16 وميض، 17 سدم، 18 عمق التحريك،
 *        19 توهج مركزي، 20 حلقات رادار
 *  الحركة: 21 دخول بدوران، 22 ملاءمة نابضة، 23 أسماء حسب التكبير،
 *        24 غبار الربط، 25 مدار شعار الربط، 26 شهاب النجاح
 *  التصميم: 27+30 عقد بحلقات ملونة (دمج)، 28 حجم حسب الروابط،
 *        29 أقواس منحنية
 * ============================================================ */

/** عقدة غادر موقعها (حُذف) حديثًا — تُرسم وهي تذبل بدل الاختفاء المفاجئ (مقترح 8) */
data class DepartingNode(
    val node: NetworkLayout.Node,
    val removedAt: Long
)

/** حالة واجهة المجرة — المواقع والروابط والمواضع ووضع الربط الجاري */
data class GalaxyNetworkUiState(
    val sites: List<Site> = emptyList(),
    val links: List<SiteLink> = emptyList(),
    val positions: Map<Long, NetworkLayout.Node> = emptyMap(),
    val linkingFromId: Long? = null,
    val isLoading: Boolean = true,
    val departing: Map<Long, DepartingNode> = emptyMap()
)

@HiltViewModel
class GalaxyNetworkViewModel @Inject constructor(
    siteRepository: SiteRepository,
    categoryRepository: CategoryRepository,
    observeSiteLinks: ObserveSiteLinksUseCase,
    private val linkSites: LinkSitesUseCase,
    private val unlinkSites: UnlinkSitesUseCase
) : ViewModel() {

    /** رسائل العمليات (نجاح الربط/فكه/رابط قائم…) تظهر على السنابار */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events

    /** تصنيفات المواقع — لتلوين عقدة كل موقع بلون تصنيفها */
    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * آخر ربط ناجح: المصدر ثم الهدف ثم لحظة النجاح (مقترحات 7 و14 و26).
     * المصدر يُحفظ قبل تصفير وضع الربط حتى يعرف الرسم اتجاه الامتداد.
     */
    private val _lastLinkEvent = MutableStateFlow<Triple<Long, Long, Long>?>(null)
    val lastLinkEvent: StateFlow<Triple<Long, Long, Long>?> = _lastLinkEvent

    /** إزاحات المستخدم: مواضع العقد التي سحبها يدويًا فوق التوزيع المحسوب */
    private val _overrides = MutableStateFlow<Map<Long, NetworkLayout.Node>>(emptyMap())

    /** مصدر وضع الربط الجاري، أو خارج الوضع */
    private val _linkingFromId = MutableStateFlow<Long?>(null)

    /** آخر مجموعة معرفات شوهدت — لاكتشاف المواقع المحذوفة بين إصدارين (مقترح 8) */
    private var lastSiteIds: Set<Long> = emptySet()

    /** العقد المغادرة حديثًا — تذبل خلال 400 مللي ثانية ثم تُنسى */
    private val departing = HashMap<Long, DepartingNode>()

    // التوزيع الأساسي وعلامته — يُعاد حسابه فقط عند تغيّر طوبولوجيا
    // الشبكة (المواقع أو الروابط)، لا عند كل سحب عقدة أو إعادة تركيب.
    // التكلفة محسوبة: شبكة شخصية بعدة عشرات من المواقع، والحساب
    // التربيعي هنا يبقى في حدود أجزاء من الثانية.
    private var layoutKey: Pair<List<Long>, List<Pair<Long, Long>>>? = null
    private var baseLayout: Map<Long, NetworkLayout.Node> = emptyMap()

    val state: StateFlow<GalaxyNetworkUiState> = combine(
        siteRepository.observeSites(),
        observeSiteLinks(),
        _overrides,
        _linkingFromId
    ) { sites, links, overrides, linkingFrom ->
        // Pair لا يقارن مباشرة: ترتيب صريح بالطرفين لعلامة التوزيع
        val linkPairs = links.map { it.fromSiteId to it.toSiteId }
            .sortedWith(compareBy({ it.first }, { it.second }))
        val key = sites.map { it.id }.sorted() to linkPairs
        if (key != layoutKey) {
            layoutKey = key
            baseLayout = NetworkLayout.compute(
                nodeIds = sites.map { it.id },
                edges = links.map { it.fromSiteId to it.toSiteId }
            )
        }
        // مواضع المواقع الحالية فقط: موقع محذوف يفقد إزاحته اليدوية
        val liveIds = sites.mapTo(HashSet()) { it.id }
        val positions = baseLayout + overrides.filterKeys { it in liveIds }

        // مقترح 8: من اختفى منذ الإصدار السابق يُحفظ موضعُه ليذبل على الشاشة
        val nowMs = System.currentTimeMillis()
        for (id in lastSiteIds - liveIds) {
            positions[id]?.let { departing[id] = DepartingNode(it, nowMs) }
        }
        lastSiteIds = liveIds
        departing.entries.removeAll { nowMs - it.value.removedAt > 700L }

        GalaxyNetworkUiState(
            sites = sites,
            links = links,
            positions = positions,
            linkingFromId = linkingFrom,
            isLoading = false,
            departing = departing.toMap()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalaxyNetworkUiState())

    /** بدء وضع الربط من قائمة موقع — يظهر الشعار العلوي فورًا */
    fun startLinking(siteId: Long) {
        _linkingFromId.value = siteId
    }

    /** إلغاء وضع الربط (زر الإلغاء في الشعار العلوي) */
    fun cancelLinking() {
        _linkingFromId.value = null
    }

    /**
     * نقر موقع أثناء وضع الربط:
     *  - نفس المصدر = إلغاء الوضع.
     *  - موقع آخر = محاولة إنشاء الرابط وتثبيته.
     */
    fun linkTarget(targetId: Long) {
        val source = _linkingFromId.value ?: return
        viewModelScope.launch {
            when (linkSites(source, targetId)) {
                LinkResult.Created -> {
                    _linkingFromId.value = null
                    // يوقّد نبضة القلب (7) وامتداد الخط (14) وشهاب النجاح (26)
                    _lastLinkEvent.value = Triple(source, targetId, System.currentTimeMillis())
                    message("تم ربط الموقعين وتثبيت الخط في الشبكة")
                }
                LinkResult.AlreadyLinked -> message("الموقعان مرتبطان مسبقًا")
                LinkResult.SelfLink -> {
                    _linkingFromId.value = null
                    message("تم إلغاء وضع الربط")
                }
                LinkResult.SiteMissing -> {
                    _linkingFromId.value = null
                    message("الموقع لم يعد موجودًا")
                }
            }
        }
    }

    /** فك رابط بين موقعين من قائمة الموقع داخل المجرة */
    fun unlink(siteId: Long, otherId: Long) {
        viewModelScope.launch {
            unlinkSites(siteId, otherId)
            message("تم فك الربط بين الموقعين")
        }
    }

    /** سحب عقدة بإصبع — إزاحة يدوية فوق التوزيع المحسوب */
    fun dragNodeBy(siteId: Long, dxWorld: Float, dyWorld: Float) {
        val current = state.value.positions[siteId] ?: return
        _overrides.update {
            it + (siteId to NetworkLayout.Node(current.x + dxWorld, current.y + dyWorld))
        }
    }

    private fun message(text: String) {
        _events.tryEmit(text)
    }
}

/**
 * تحويلات العرض (تحريك/تكبير) — تُقرأ في طبقة الرسم والإيماءات معًا.
 * العلاقة: نقطة الشاشة = نقطة العالم × التكبير + الإزاحة.
 * كل الوحدات بكسل، فالتكبير هنا «بكسل لكل وحدة عالم».
 */
private class GalaxyTransform {
    var zoom by mutableFloatStateOf(0.4f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set

    /** هدف ملاءمة العالم كاملًا داخل الشاشة مع توسيطه — بلا تطبيق (مقترح 22) */
    fun fitTarget(viewport: IntSize): Pair<Offset, Float> {
        if (viewport.width <= 0 || viewport.height <= 0) return pan to zoom
        val scale = min(
            viewport.width / NetworkLayout.WORLD_WIDTH,
            viewport.height / NetworkLayout.WORLD_HEIGHT
        ) * 0.92f
        val z = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val p = Offset(
            (viewport.width - NetworkLayout.WORLD_WIDTH * z) / 2f,
            (viewport.height - NetworkLayout.WORLD_HEIGHT * z) / 2f
        )
        return p to z
    }

    /** ملاءمة فورية — لأول قياس ولدوران الجهاز */
    fun fit(viewport: IntSize) {
        val (p, z) = fitTarget(viewport)
        pan = p
        zoom = z
    }

    /** تطبيق قيم محسوبة (نهاية انيميشن الملاءمة الناعمة) */
    fun apply(newPan: Offset, newZoom: Float) {
        pan = newPan
        zoom = newZoom
    }

    fun panBy(delta: Offset) {
        pan += delta
    }

    /** تكبير حول نقطة محورية — تبقى النقطة تحت الإصبع ثابتة */
    fun zoomAbout(pivot: Offset, factor: Float) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        if (applied == 1f) return
        pan = pivot - (pivot - pan) * applied
        zoom = newZoom
    }

    /** من إحداثيات الشاشة (بكسل) إلى إحداثيات العالم */
    fun screenToWorld(screen: Offset): Offset = (screen - pan) / zoom

    companion object {
        const val MIN_ZOOM = 0.12f
        const val MAX_ZOOM = 3.5f
    }
}

/** حالة انيميشن ملاءمة العرض الجارية (مقترح 22) */
private data class FitAnim(
    val fromPan: Offset,
    val toPan: Offset,
    val fromZoom: Float,
    val toZoom: Float,
    val progress: Animatable<Float, AnimationVector1D>
)

/** نقطة في ذيل المذنّب أثناء سحب عقدة (مقترح 2) */
private data class TrailPoint(val x: Float, val y: Float, val at: Long)

/** موجة نقر تنطلق من عقدة (مقترح 4) */
private data class RipplePoint(val x: Float, val y: Float, val at: Long)

/** نجمة خلفية حتمية — مواضع نسبية 0..1 (مقترح 15) */
private data class GalaxyStar(
    val nx: Float,
    val ny: Float,
    val radius: Float,
    val alpha: Float,
    val phase: Float
)

/** دورة كاملة بالراديان — للساعات الدائرية أدناه */
private const val TWO_PI = 6.2831853f

/** نصف قطر عقدة الموقع حسب عدد روابطها — المحورية أكبر (مقترح 28) */
private fun nodeRadiusFor(degree: Int): Float =
    NetworkLayout.NODE_RADIUS * (1f + 0.12f * min(degree, 4))

/**
 * نجوم حتمية بلا أي عشوائية: مولّد خطي ثابت يُعيد نفس السماء في
 * كل جلسة — نفس فلسفة `NetworkLayout` (مقترح 15).
 */
private fun galaxyStars(count: Int): List<GalaxyStar> {
    var seed = 1234567L
    fun next(): Float {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return ((seed ushr 33) % 1000L).toFloat() / 1000f
    }
    return List(count) {
        GalaxyStar(
            nx = next(),
            ny = next(),
            radius = 0.7f + next() * 1.3f,
            alpha = 0.22f + next() * 0.5f,
            phase = next() * TWO_PI
        )
    }
}

/** منحنى تربيعي: نقطة عند المعامل t بين بداية ونهاية عبر نقطة التحكم */
private fun quadPoint(
    ax: Float, ay: Float,
    cx: Float, cy: Float,
    bx: Float, by: Float,
    t: Float
): Offset {
    val u = 1f - t
    return Offset(
        u * u * ax + 2f * u * t * cx + t * t * bx,
        u * u * ay + 2f * u * t * cy + t * t * by
    )
}

/** دالة ظهور بارتداد خفيف: 0 → تتجاوز الواحد قليلًا → تستقر على 1 (مقترح 5) */
private fun easeOutBack(s: Float): Float {
    if (s <= 0f) return 0f
    if (s >= 1f) return 1f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = s - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyNetworkScreen(
    onOpenSite: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: GalaxyNetworkViewModel = hiltViewModel()
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val lastLinkEvent by viewModel.lastLinkEvent.collectAsStateWithLifecycle()

    val transform = remember { GalaxyTransform() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var menuSiteId by remember { mutableStateOf<Long?>(null) }
    var hintSeen by rememberSaveable { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val layoutDirection = LocalLayoutDirection.current

    // ألوان الواجهة — تُقرأ في سياق التركيب لا داخل الرسم
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val nodeFallback = primaryColor
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val candidateBorder = primaryColor.copy(alpha = 0.55f)
    // لون حلقة كل موقع من لون تصنيفه إن وُجد (الدمج 27+30: الحلقة تحمل اللون)
    val nodeColors = remember(categories, ui.sites) {
        val byId = categories.associate { it.id to it.colorHex }
        ui.sites.associate { site ->
            val hex = site.categoryId?.let { byId[it] }
            val color = if (hex.isNullOrBlank()) nodeFallback
            else runCatching { Color(android.graphics.Color.parseColor(hex)) }
                .getOrDefault(nodeFallback)
            site.id to color
        }
    }

    // عدد روابط كل موقع — لأحجام العقد المحورية (مقترح 28)
    val degrees = remember(ui.links) {
        val m = HashMap<Long, Int>()
        for (l in ui.links) {
            m[l.fromSiteId] = (m[l.fromSiteId] ?: 0) + 1
            m[l.toSiteId] = (m[l.toSiteId] ?: 0) + 1
        }
        m
    }
    val radiusOf: (Long) -> Float = { id -> nodeRadiusFor(degrees[id] ?: 0) }

    /* ── الساعات الدائرية: قيم لانهائية تغذي كل الانيميشنات المستمرة ──
     * كل المضاعفات أعداد صحيحة حتى تبقى الدورة سلسة عند إعادة الدوران. */
    val clockTransition = rememberInfiniteTransition()
    val tRad by clockTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing))
    )
    val tLin by clockTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing))
    )
    val tSlow by clockTransition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing))
    )

    // رسائل العمليات على سنابار الشاشة الرئيسية (قادمة من الجذر)
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    /* ── مقترح 22: ملاءمة عرض ناعمة بنابض بدل القفزة الفورية ── */
    val scope = rememberCoroutineScope()
    var fitAnim by remember { mutableStateOf<FitAnim?>(null) }
    val fa = fitAnim
    val renderZoom = if (fa != null) {
        fa.fromZoom + (fa.toZoom - fa.fromZoom) * fa.progress.value
    } else transform.zoom
    val renderPan = if (fa != null) {
        Offset(
            fa.fromPan.x + (fa.toPan.x - fa.fromPan.x) * fa.progress.value,
            fa.fromPan.y + (fa.toPan.y - fa.fromPan.y) * fa.progress.value
        )
    } else transform.pan

    // ملاءمة العرض عند أول قياس وعند دوران الجهاز (فورية هنا، والزر يلينها)
    LaunchedEffect(viewport) {
        if (viewport != IntSize.Zero) {
            fitAnim = null
            transform.fit(viewport)
        }
    }

    /* ── مقترح 5+12+21: تقدم الدخول الواحد (عقد، خطوط، ودوران الشاشة) ── */
    val entry = remember { Animatable(0f) }
    LaunchedEffect(ui.sites.size) {
        if (ui.sites.isNotEmpty()) {
            entry.snapTo(0f)
            entry.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val entryValue = entry.value

    /* ── مقترح 13: توهج الخطوط الموصولة بعقدة تُسحب الآن ── */
    var draggingId by remember { mutableStateOf<Long?>(null) }
    val dragGlow = remember { Animatable(0f) }
    LaunchedEffect(draggingId) {
        dragGlow.animateTo(if (draggingId != null) 1f else 0f, tween(180))
    }

    // ذيل المذنّب وموجات النقر — تُغذى من طبقة الإيماءات وتُرسم في الـ Canvas
    val trail = remember { mutableStateListOf<TrailPoint>() }
    val ripples = remember { mutableStateListOf<RipplePoint>() }

    // نبض مستمر لهالة مصدر الربط ما دام الوضع مفعّلًا
    val linkPulse = remember { Animatable(0f) }
    val linkingActive = ui.linkingFromId != null
    LaunchedEffect(linkingActive) {
        if (linkingActive) {
            while (true) {
                linkPulse.animateTo(1f, tween(750))
                linkPulse.animateTo(0f, tween(750))
            }
        } else {
            linkPulse.snapTo(0f)
        }
    }

    // مقترح 23: الأسماء تتلاشى عند التصغير الشديد وتعود مع الاقتراب
    val labelAlpha = ((renderZoom - 0.30f) / 0.14f).coerceIn(0f, 1f)
    val labelStyle = MaterialTheme.typography.titleLarge.copy(
        color = onSurfaceColor.copy(alpha = labelAlpha),
        textAlign = TextAlign.Center
    )

    // نجوم السماء — نفس السماء كل مرة (مقترح 15)
    val stars = remember { galaxyStars(110) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { viewport = it }
    ) {
        // ── الشبكة كاملة: سماء + خطوط + عقد + أسماء في Canvas واحد ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        // القراءة من الـ ViewModel مباشرة لا من قيمة معروضة
                        // مُلتقطة: طبقة الإيماءات تُركَّب مرة واحدة ويجب أن
                        // ترى أحدث حالة دومًا (المواضع تتغير مع كل سحب).
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val radiusFn: (Long) -> Float = { id ->
                            val s = viewModel.state.value
                            val d = s.links.count { it.fromSiteId == id || it.toSiteId == id }
                            nodeRadiusFor(d)
                        }
                        val hitId = hitNode(down.position, transform, viewModel.state.value, radiusFn)
                        var dragAmount = 0f
                        var moved = false
                        var pinched = false
                        var lastCentroid = down.position
                        var lastSpan = 0f

                        var pressed: List<PointerInputChange>
                        do {
                            val event = awaitPointerEvent()
                            pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                // تكبير بإصبعين مع تحريك مركز القرصة
                                pinched = true
                                val centroid = pressed
                                    .fold(Offset.Zero) { acc, c -> acc + c.position } *
                                    (1f / pressed.size)
                                var span = 0f
                                for (c in pressed) {
                                    val d = (c.position - centroid).getDistance()
                                    if (d > span) span = d
                                }
                                span *= 2f
                                if (lastSpan > 0f && span > 0f) {
                                    transform.zoomAbout(centroid, span / lastSpan)
                                }
                                transform.panBy(centroid - lastCentroid)
                                lastCentroid = centroid
                                lastSpan = span
                                pressed.forEach { it.consume() }
                            } else {
                                val change = pressed.first()
                                if (lastSpan > 0f) {
                                    // إصبع واحد بعد القرصة: تحديث المراجع بلا قفزة
                                    lastCentroid = change.position
                                    lastSpan = 0f
                                }
                                val delta = change.position - change.previousPosition
                                dragAmount += delta.getDistance()
                                if (dragAmount > touchSlop) {
                                    moved = true
                                    hintSeen = true
                                    if (pinched) {
                                        transform.panBy(delta)
                                    } else if (hitId != null) {
                                        // سحب عقدة: تحويل حركة الشاشة لوحدات العالم
                                        draggingId = hitId
                                        // مقترح 2: نقطة جديدة في ذيل المذنّب
                                        val world = transform.screenToWorld(change.position)
                                        trail.add(TrailPoint(world.x, world.y, System.currentTimeMillis()))
                                        while (trail.size > 44) trail.removeAt(0)
                                        viewModel.dragNodeBy(
                                            hitId,
                                            delta.x / transform.zoom,
                                            delta.y / transform.zoom
                                        )
                                    } else {
                                        transform.panBy(delta)
                                    }
                                    change.consume()
                                }
                            }
                        } while (pressed.isNotEmpty())

                        draggingId = null

                        // نقرة قصيرة على عقدة: فتح القائمة أو إتمام الربط
                        if (!moved && !pinched && hitId != null) {
                            hintSeen = true
                            // مقترح 4: موجة نقر تنطلق من العقدة
                            viewModel.state.value.positions[hitId]?.let { node ->
                                ripples.add(RipplePoint(node.x, node.y, System.currentTimeMillis()))
                                while (ripples.size > 8) ripples.removeAt(0)
                            }
                            if (viewModel.state.value.linkingFromId != null) {
                                viewModel.linkTarget(hitId)
                            } else {
                                menuSiteId = hitId
                            }
                        }
                    }
                }
        ) {
            val nowMs = System.currentTimeMillis()

            /* ═══ السماء أولًا — خلف كل شيء وبإحداثيات الشاشة ═══ */

            // مقترح 17: سديمان خافتان ينجرفان ببطء شديد
            val minDim = min(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.06f), primaryColor.copy(alpha = 0f)),
                    center = Offset(
                        size.width * 0.26f + 26f * sin(tSlow),
                        size.height * 0.24f + 18f * cos(tSlow)
                    ),
                    radius = minDim * 0.55f
                ),
                radius = minDim * 0.55f,
                center = Offset(
                    size.width * 0.26f + 26f * sin(tSlow),
                    size.height * 0.24f + 18f * cos(tSlow)
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryColor.copy(alpha = 0.05f), secondaryColor.copy(alpha = 0f)),
                    center = Offset(
                        size.width * 0.74f + 22f * cos(tSlow + 2f),
                        size.height * 0.70f + 20f * sin(tSlow + 2f)
                    ),
                    radius = minDim * 0.5f
                ),
                radius = minDim * 0.5f,
                center = Offset(
                    size.width * 0.74f + 22f * cos(tSlow + 2f),
                    size.height * 0.70f + 20f * sin(tSlow + 2f)
                )
            )

            // مقترحات 15+16+18: نجوم ثابتة تومض بهدوء وتنزاح مع التحريك (عمق)
            val w = size.width
            val h = size.height
            for ((i, star) in stars.withIndex()) {
                var sx = (star.nx * w + renderPan.x * 0.3f) % w
                if (sx < 0f) sx += w
                var sy = (star.ny * h + renderPan.y * 0.3f) % h
                if (sy < 0f) sy += h
                val twinkle = if (i % 3 == 0) {
                    0.45f + 0.55f * abs(sin(tRad * 2f + star.phase))
                } else 1f
                drawCircle(
                    color = Color(0xFFCFE9FF).copy(alpha = star.alpha * twinkle),
                    radius = star.radius,
                    center = Offset(sx, sy)
                )
            }

            // مركز ثقل الشبكة — قلب المجرة للتوهج وحلقات الرادار
            val worldCenter = Offset(NetworkLayout.WORLD_WIDTH / 2f, NetworkLayout.WORLD_HEIGHT / 2f)
            var centroidX = 0f
            var centroidY = 0f
            if (ui.positions.isNotEmpty()) {
                for (n in ui.positions.values) {
                    centroidX += n.x
                    centroidY += n.y
                }
                centroidX /= ui.positions.size
                centroidY /= ui.positions.size
            }
            val heart = Offset(centroidX * renderZoom + renderPan.x, centroidY * renderZoom + renderPan.y)

            if (ui.positions.isNotEmpty()) {
                // مقترح 19: توهج مركزي يتنفس خلف قلب الشبكة
                val glowAlpha = 0.07f + 0.05f * sin(tSlow + 1f)
                val glowRadius = (300f * renderZoom).coerceIn(90f, 640f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = glowAlpha), primaryColor.copy(alpha = 0f)),
                        center = heart,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = heart
                )
                // مقترح 20: حلقات رادار خافتة تنطلق من القلب
                for (ring in 0 until 3) {
                    val frac = (tRad / TWO_PI + ring / 3f) % 1f
                    drawCircle(
                        color = primaryColor.copy(alpha = (1f - frac) * 0.08f),
                        radius = frac * 430f * renderZoom,
                        center = heart,
                        style = Stroke(1.5f)
                    )
                }
            }

            if (ui.positions.isEmpty()) return@Canvas

            /* ═══ عالم المجرة: تحويل واحد يحمل الخطوط والعقد والأسماء ═══ */
            val pulse = linkPulse.value
            val invE = 1f - entryValue
            val rotDeg = -8f * invE * invE * invE // مقترح 21: دوران الدخول
            withTransform({
                translate(renderPan.x, renderPan.y)
                scale(renderZoom, renderZoom, Offset.Zero)
                if (rotDeg != 0f) {
                    translate(NetworkLayout.WORLD_WIDTH / 2f, NetworkLayout.WORLD_HEIGHT / 2f)
                    rotate(rotDeg, Offset.Zero)
                    translate(-NetworkLayout.WORLD_WIDTH / 2f, -NetworkLayout.WORLD_HEIGHT / 2f)
                }
            }) {
                // أقصى بعد عن المركز — لتدريج الظهور المتدرج (مقترح 5)
                var maxDist = 1f
                for (n in ui.positions.values) {
                    val dx = n.x - worldCenter.x
                    val dy = n.y - worldCenter.y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d > maxDist) maxDist = d
                }

                // مقترح 9: طور الشرطات المتدفقة — 3.2×20000 دورة متوافقة مع النمط
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f), -tLin * 3.2f)

                /* ── 1) الخطوط أولًا حتى تمر تحت العقد (مقترحات 9-14 و29) ── */
                for ((index, link) in ui.links.withIndex()) {
                    val fromNode = ui.positions[link.fromSiteId]
                        ?: ui.departing[link.fromSiteId]?.node ?: continue
                    val toNode = ui.positions[link.toSiteId]
                        ?: ui.departing[link.toSiteId]?.node ?: continue
                    val a = Offset(fromNode.x, fromNode.y)
                    val b = Offset(toNode.x, toNode.y)

                    // تلاشي الأطراف المغادرة (مقترح 8)
                    val fadeFrom = ui.departing[link.fromSiteId]
                        ?.let { (1f - (nowMs - it.removedAt).toFloat() / 400f).coerceIn(0f, 1f) } ?: 1f
                    val fadeTo = ui.departing[link.toSiteId]
                        ?.let { (1f - (nowMs - it.removedAt).toFloat() / 400f).coerceIn(0f, 1f) } ?: 1f

                    // كسر الدخول حسب بعد منتصف الخط عن القلب (مقترح 12)
                    val midX = (a.x + b.x) / 2f
                    val midY = (a.y + b.y) / 2f
                    val orderL = (sqrt(
                        (midX - worldCenter.x) * (midX - worldCenter.x) +
                            (midY - worldCenter.y) * (midY - worldCenter.y)
                    ) / maxDist).coerceIn(0f, 1f)
                    var frac = ((entryValue - orderL * 0.4f) / 0.6f).coerceIn(0f, 1f)
                    if (frac <= 0f) continue

                    // مقترح 14: الرابط الجديد يمتد من جهة مصدره
                    var reversed = false
                    val ev = lastLinkEvent
                    if (ev != null) {
                        val matches = (ev.first == link.fromSiteId && ev.second == link.toSiteId) ||
                            (ev.first == link.toSiteId && ev.second == link.fromSiteId)
                        val age = nowMs - ev.third
                        if (matches && age in 0..500L) {
                            frac = age.toFloat() / 500f
                            reversed = ev.first != link.fromSiteId
                        }
                    }

                    // مقترح 29: قوس منحني بدل الوتر المستقيم
                    val start = if (reversed) b else a
                    val end = if (reversed) a else b
                    val sdx = end.x - start.x
                    val sdy = end.y - start.y
                    val ctrl = Offset(
                        (start.x + end.x) / 2f + sdy * 0.16f,
                        (start.y + end.y) / 2f - sdx * 0.16f
                    )
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        val steps = 16
                        val upto = (steps * frac).toInt().coerceAtLeast(1)
                        for (s in 1..upto) {
                            val t = if (s == upto) frac else s.toFloat() / steps.toFloat() * frac
                            val pt = quadPoint(start.x, start.y, ctrl.x, ctrl.y, end.x, end.y, t)
                            lineTo(pt.x, pt.y)
                        }
                    }

                    // مقترح 13: سماكة وتوهج الخطوط الموصولة بالعقدة المسحوبة
                    val touchesDrag = draggingId != null &&
                        (link.fromSiteId == draggingId || link.toSiteId == draggingId)
                    val dragBoost = if (touchesDrag) dragGlow.value else 0f
                    val alpha = (0.45f + 0.25f * dragBoost) * min(fadeFrom, fadeTo)

                    val colA = nodeColors[link.fromSiteId] ?: nodeFallback
                    val colB = nodeColors[link.toSiteId] ?: nodeFallback
                    drawPath(
                        path = path,
                        // مقترح 10: تدرج بين لوني تصنيفي الطرفين
                        brush = Brush.linearGradient(
                            colors = listOf(colA.copy(alpha = alpha), colB.copy(alpha = alpha)),
                            start = start,
                            end = end
                        ),
                        style = Stroke(
                            width = 1.8f + 2.6f * dragBoost,
                            cap = StrokeCap.Round,
                            pathEffect = dashEffect
                        )
                    )

                    // مقترح 11: إشارة ضوئية تعبر الرابط بعد اكتمال دخوله
                    if (frac >= 1f) {
                        val sig = (tLin * 0.04f + index * 0.37f) % 1f
                        val sp = quadPoint(start.x, start.y, ctrl.x, ctrl.y, end.x, end.y, sig)
                        drawCircle(color = colB.copy(alpha = 0.16f), radius = 7f, center = sp)
                        drawCircle(color = Color(0xFFE8F6FF).copy(alpha = 0.8f), radius = 2.6f, center = sp)
                    }
                }

                /* ── مقترح 2: ذيل المذنّب تحت العقد ── */
                if (trail.isNotEmpty()) {
                    for (p in trail) {
                        val age = nowMs - p.at
                        if (age >= 450L) continue
                        val k = 1f - age.toFloat() / 450f
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.4f * k),
                            radius = 3f + NetworkLayout.NODE_RADIUS * 0.45f * k,
                            center = Offset(p.x, p.y)
                        )
                    }
                }

                /* ── 2) العقد وأسمائها (مقترحات 1 و3 و5-7 و27-28 و30) ── */
                val linkingFrom = ui.linkingFromId
                val sourceNode = linkingFrom?.let { ui.positions[it] }
                for (site in ui.sites) {
                    val node = ui.positions[site.id] ?: continue
                    val isSource = linkingFrom == site.id
                    val isCandidate = linkingFrom != null && !isSource

                    // الظهور المتدرج من مركز المجرة للخارج (مقترح 5)
                    val dx = node.x - worldCenter.x
                    val dy = node.y - worldCenter.y
                    val order = sqrt(dx * dx + dy * dy) / maxDist
                    val sEntry = ((entryValue - order * 0.45f) / 0.55f).coerceIn(0f, 1f)
                    if (sEntry <= 0f) continue
                    var radiusScale = easeOutBack(sEntry)

                    // مقترح 1: تنفس ذاتي بطور متفرد لكل موقع
                    val phase = (site.id % 628L).toFloat() / 100f
                    val breath01 = 0.5f + 0.5f * sin(tRad + phase)
                    radiusScale *= 1f + 0.045f * sin(tRad + phase)

                    // مقترح 6: المرشحون للربط يتمايلون نحو المصدر
                    var drawCenter = Offset(node.x, node.y)
                    if (isCandidate && sourceNode != null) {
                        val vx = sourceNode.x - node.x
                        val vy = sourceNode.y - node.y
                        val len = sqrt(vx * vx + vy * vy).coerceAtLeast(1f)
                        drawCenter = Offset(
                            node.x + vx / len * 4f * pulse,
                            node.y + vy / len * 4f * pulse
                        )
                        radiusScale *= 1f + 0.06f * pulse
                    }

                    // مقترح 7: نبضة قلب مزدوجة لطرفي آخر ربط ناجح
                    val ev = lastLinkEvent
                    if (ev != null && (ev.first == site.id || ev.second == site.id)) {
                        val age = nowMs - ev.third
                        if (age in 0..900L) {
                            val x = age.toFloat() / 900f
                            radiusScale *= 1f + abs(sin(TWO_PI * x)) * (1f - 0.5f * x) * 0.28f
                        }
                    }

                    val baseR = radiusOf(site.id)
                    val r = baseR * radiusScale
                    val catColor = nodeColors[site.id] ?: nodeFallback

                    // مقترح 3: هالة متنفسة بلون التصنيف خلف العقدة
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                catColor.copy(alpha = 0.10f + 0.12f * breath01),
                                catColor.copy(alpha = 0f)
                            ),
                            center = drawCenter,
                            radius = r * 2.1f
                        ),
                        radius = r * 2.1f,
                        center = drawCenter
                    )

                    // هالة نابضة حول مصدر الربط الجاري
                    if (isSource) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.35f * pulse),
                            radius = r + 10f + 8f * pulse,
                            center = drawCenter
                        )
                    }

                    // الدمج 27+30: عقدة بيضاء + حلقة خارجية بلون التصنيف
                    drawCircle(color = onSurfaceColor, radius = r, center = drawCenter)
                    drawCircle(
                        color = catColor.copy(alpha = 0.9f),
                        radius = r + 7f,
                        center = drawCenter,
                        style = Stroke(2.5f)
                    )

                    // حدود وضع الربط فوق الحلقة
                    if (isSource) {
                        drawCircle(
                            color = primaryColor,
                            radius = r,
                            center = drawCenter,
                            style = Stroke(4f)
                        )
                    } else if (isCandidate) {
                        drawCircle(
                            color = candidateBorder,
                            radius = r,
                            center = drawCenter,
                            style = Stroke(3f)
                        )
                    }

                    // اسم الموقع تحت العقدة، مقصوصًا على عرض الصندوق
                    if (labelAlpha > 0.01f) {
                        val measured = textMeasurer.measure(
                            text = site.name,
                            style = labelStyle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = true,
                            constraints = Constraints(
                                maxWidth = (NetworkLayout.NODE_RADIUS * 6f).toInt()
                            ),
                            layoutDirection = layoutDirection,
                            // كثافة 1: وحدة القياس = وحدة العالم، فيبقى حجم الاسم
                            // متناسبًا مع العقد مهما اختلفت كثافة الجهاز
                            density = Density(1f)
                        )
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                drawCenter.x - measured.size.width / 2f,
                                drawCenter.y + baseR + 8f
                            )
                        )
                    }
                }

                /* ── مقترح 8: العقد المغادرة تذبل بدل الاختفاء المفاجئ ── */
                for ((_, dep) in ui.departing) {
                    val age = nowMs - dep.removedAt
                    if (age > 400L) continue
                    val k = 1f - age.toFloat() / 400f
                    val r = nodeRadiusFor(0) * k
                    val c = Offset(dep.node.x, dep.node.y)
                    drawCircle(color = onSurfaceColor.copy(alpha = 0.85f * k), radius = r, center = c)
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.5f * k),
                        radius = r + 7f,
                        center = c,
                        style = Stroke(2.5f)
                    )
                }

                /* ── مقترح 4: موجات النقر فوق العقد ── */
                if (ripples.isNotEmpty()) {
                    for (rp in ripples) {
                        val age = nowMs - rp.at
                        if (age >= 520L) continue
                        val frac = age.toFloat() / 520f
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.55f * (1f - frac)),
                            radius = NetworkLayout.NODE_RADIUS * (1f + 2.4f * frac),
                            center = Offset(rp.x, rp.y),
                            style = Stroke(2.5f)
                        )
                    }
                }

                /* ── مقترح 24: غبار نجوم ينجذب لمصدر الربط ── */
                if (sourceNode != null) {
                    for (i in 0 until 13) {
                        val frac = (tLin * 0.05f + i * 0.077f) % 1f
                        val ang = i * 2.3999632f + frac * 1.8f
                        val rad = (1f - frac) * NetworkLayout.NODE_RADIUS * 4.5f
                        drawCircle(
                            color = Color(0xFFCFE9FF).copy(alpha = (1f - frac) * 0.6f),
                            radius = 2.2f,
                            center = Offset(
                                sourceNode.x + cos(ang) * rad,
                                sourceNode.y + sin(ang) * rad
                            )
                        )
                    }
                }
            }

            /* ═══ مقترح 26: شهاب النجاح يعبر طرف الشاشة ═══ */
            val shoot = lastLinkEvent
            if (shoot != null) {
                val age = nowMs - shoot.third
                if (age in 0..650L) {
                    val p = age.toFloat() / 650f
                    val s = Offset(size.width * 0.14f, size.height * 0.12f)
                    val e2 = Offset(size.width * 0.86f, size.height * 0.07f)
                    val head = Offset(s.x + (e2.x - s.x) * p, s.y + (e2.y - s.y) * p)
                    val tail = Offset(head.x - (e2.x - s.x) * 0.08f, head.y - (e2.y - s.y) * 0.08f)
                    drawLine(
                        color = Color(0xFFE8F6FF).copy(alpha = 0.9f * (1f - p)),
                        start = tail,
                        end = head,
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = Color(0xFFE8F6FF).copy(alpha = 1f - p), radius = 3f, center = head)
                }
            }
        }

        // ── الشعار العلوي لوضع الربط — يظهر فور بدء عملية الربط ──
        val linkingSite = ui.linkingFromId?.let { id -> ui.sites.firstOrNull { it.id == id } }
        AnimatedVisibility(
            visible = linkingSite != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // مقترح 25: نقطة تدور في مدار حول أيقونة الرابط
                    Box(modifier = Modifier.size(22.dp)) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.Center)
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.Center)
                                .graphicsLayer { rotationZ = tRad * (180f / PI.toFloat()) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .align(Alignment.TopCenter)
                                    .background(secondaryColor, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "وضع الربط مفعّل — انقر موقعًا آخر لربطه بـ «${linkingSite?.name.orEmpty()}»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.cancelLinking() }) {
                        Text("إلغاء")
                    }
                }
            }
        }

        // ── تلميح الاستخدام الأول — يختفي بعد أول تفاعل ──
        if (!hintSeen && !ui.isLoading && ui.sites.isNotEmpty()) {
            Text(
                text = "اسحب للتحريك • قرّب بإصبعين • انقر موقعًا للخيارات",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }

        // ── حالة عدم وجود مواقع ──
        if (!ui.isLoading && ui.sites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Hub,
                    title = "لا توجد مواقع بعد",
                    subtitle = "أضف موقعًا من واجهة المواقع أولًا ثم عُد هنا لبناء شبكتك"
                )
            }
        }

        // ── زر ملاءمة العرض — يعود بالشبكة كاملة داخل الشاشة ──
        if (!ui.isLoading && ui.sites.isNotEmpty()) {
            FloatingActionButton(
                // مقترح 22: انزلاق نابض نحو الملاءمة بدل القفزة
                onClick = {
                    val (toPan, toZoom) = transform.fitTarget(viewport)
                    val anim = FitAnim(transform.pan, toPan, transform.zoom, toZoom, Animatable(0f))
                    fitAnim = anim
                    scope.launch {
                        anim.progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                        transform.apply(anim.toPan, anim.toZoom)
                        fitAnim = null
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(44.dp)
            ) {
                Icon(Icons.Filled.CenterFocusWeak, contentDescription = "ملاءمة العرض")
            }
        }
    }

    // ── قائمة خيارات الموقع — «الانتقال للموقع» و«ربط موقع» وفك الروابط ──
    val menuSite = menuSiteId?.let { id -> ui.sites.firstOrNull { it.id == id } }
    if (menuSite != null) {
        val partners = ui.links
            .filter { it.fromSiteId == menuSite.id || it.toSiteId == menuSite.id }
            .mapNotNull { link ->
                val otherId = if (link.fromSiteId == menuSite.id) link.toSiteId else link.fromSiteId
                ui.sites.firstOrNull { it.id == otherId }
            }

        ModalBottomSheet(
            onDismissRequest = { menuSiteId = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = menuSite.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                // الخيار الأول: اختصار مباشر لصفحة تفاصيل الموقع
                GalaxySheetItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = "الانتقال للموقع",
                    onClick = {
                        menuSiteId = null
                        onOpenSite(menuSite.id)
                    }
                )

                // الخيار الثاني: بدء وضع الربط بالشعار العلوي
                GalaxySheetItem(
                    icon = Icons.Filled.Link,
                    text = "ربط موقع",
                    onClick = {
                        menuSiteId = null
                        viewModel.startLinking(menuSite.id)
                    }
                )

                // متطلب ضمني: تصحيح الأخطاء بفك روابط موجودة
                if (partners.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "الروابط الحالية — انقر للفك",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                    for (partner in partners) {
                        GalaxySheetItem(
                            icon = Icons.Filled.LinkOff,
                            text = "فك الربط مع «${partner.name}»",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                menuSiteId = null
                                viewModel.unlink(menuSite.id, partner.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** إيجاد العقدة تحت نقطة شاشة، بهامش لمس أريح من حجم الدائرة */
private fun hitNode(
    screen: Offset,
    transform: GalaxyTransform,
    ui: GalaxyNetworkUiState,
    radiusOf: (Long) -> Float
): Long? {
    val world = transform.screenToWorld(screen)
    var bestId: Long? = null
    var bestDist = Float.MAX_VALUE
    for ((id, node) in ui.positions) {
        val dx = node.x - world.x
        val dy = node.y - world.y
        val dist = sqrt(dx * dx + dy * dy)
        val reach = radiusOf(id) * 1.7f
        if (dist <= reach && dist < bestDist) {
            bestDist = dist
            bestId = id
        }
    }
    return bestId
}

/** عنصر قائمة في ورقة خيارات الموقع */
@Composable
private fun GalaxySheetItem(
    icon: ImageVector,
    text: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val iconTint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Spacer(Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
