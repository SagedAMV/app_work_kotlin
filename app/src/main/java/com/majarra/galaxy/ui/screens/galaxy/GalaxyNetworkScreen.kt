package com.majarra.galaxy.ui.screens.galaxy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import kotlin.math.min
import kotlin.math.sqrt

/* ============================================================
 * واجهة المجرة (النسخة 2.5 — تعليمات هذه الجلسة):
 * عرض المواقع نقاطًا مترابطة بخطوط كشبكة اتصالات، مع تحريك وتكبير،
 * ونقرة على الموقع تفتح قائمة: «الانتقال للموقع» أو «ربط موقع».
 *
 * الرسم كله داخل Canvas واحد بإحداثيات «العالم» الافتراضية، ثم
 * تحويل واحد (إزاحة + تكبير) يطبَّق على الخطوط والعقد والأسماء
 * معًا، فتبقى متلاصقة مهما تحرك المستخدم أو كبّر.
 * ============================================================ */

/** حالة واجهة المجرة — المواقع والروابط والمواضع ووضع الربط الجاري */
data class GalaxyNetworkUiState(
    val sites: List<Site> = emptyList(),
    val links: List<SiteLink> = emptyList(),
    val positions: Map<Long, NetworkLayout.Node> = emptyMap(),
    val linkingFromId: Long? = null,
    val isLoading: Boolean = true
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

    /** إزاحات المستخدم: مواضع العقد التي سحبها يدويًا فوق التوزيع المحسوب */
    private val _overrides = MutableStateFlow<Map<Long, NetworkLayout.Node>>(emptyMap())

    /** مصدر وضع الربط الجاري، أو خارج الوضع */
    private val _linkingFromId = MutableStateFlow<Long?>(null)

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
        GalaxyNetworkUiState(
            sites = sites,
            links = links,
            positions = baseLayout + overrides.filterKeys { it in liveIds },
            linkingFromId = linkingFrom,
            isLoading = false
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

    /** ملاءمة العالم كاملًا داخل الشاشة مع توسيطه */
    fun fit(viewport: IntSize) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val scale = min(
            viewport.width / NetworkLayout.WORLD_WIDTH,
            viewport.height / NetworkLayout.WORLD_HEIGHT
        ) * 0.92f
        zoom = scale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        pan = Offset(
            (viewport.width - NetworkLayout.WORLD_WIDTH * zoom) / 2f,
            (viewport.height - NetworkLayout.WORLD_HEIGHT * zoom) / 2f
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyNetworkScreen(
    onOpenSite: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: GalaxyNetworkViewModel = hiltViewModel()
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val transform = remember { GalaxyTransform() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var menuSiteId by remember { mutableStateOf<Long?>(null) }
    var hintSeen by rememberSaveable { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val layoutDirection = LocalLayoutDirection.current

    // ألوان الواجهة — تُقرأ في سياق التركيب لا داخل الرسم
    val primaryColor = MaterialTheme.colorScheme.primary
    val lineColor = primaryColor.copy(alpha = 0.6f)
    val nodeFallback = primaryColor
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val faintBorder = onSurfaceColor.copy(alpha = 0.3f)
    val candidateBorder = primaryColor.copy(alpha = 0.55f)
    val labelStyle = MaterialTheme.typography.titleLarge.copy(
        color = onSurfaceColor,
        textAlign = TextAlign.Center
    )
    // لون عقدة كل موقع من لون تصنيفه إن وُجد
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

    // رسائل العمليات على سنابار الشاشة الرئيسية (قادمة من الجذر)
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // ملاءمة العرض عند أول قياس وعند دوران الجهاز
    LaunchedEffect(viewport) {
        if (viewport != IntSize.Zero) transform.fit(viewport)
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { viewport = it }
    ) {
        // ── الشبكة كاملة: خطوط + عقد + أسماء في Canvas واحد ──
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
                        val hitId = hitNode(down.position, transform, viewModel.state.value)
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

                        // نقرة قصيرة على عقدة: فتح القائمة أو إتمام الربط
                        if (!moved && !pinched && hitId != null) {
                            hintSeen = true
                            if (viewModel.state.value.linkingFromId != null) {
                                viewModel.linkTarget(hitId)
                            } else {
                                menuSiteId = hitId
                            }
                        }
                    }
                }
        ) {
            if (ui.positions.isEmpty()) return@Canvas

            val pulse = linkPulse.value
            withTransform({
                translate(transform.pan.x, transform.pan.y)
                scale(transform.zoom, transform.zoom, Offset.Zero)
            }) {
                // 1) الخطوط أولًا حتى تمر تحت العقد
                for (link in ui.links) {
                    val from = ui.positions[link.fromSiteId] ?: continue
                    val to = ui.positions[link.toSiteId] ?: continue
                    drawLine(
                        color = lineColor,
                        start = Offset(from.x, from.y),
                        end = Offset(to.x, to.y),
                        strokeWidth = 3f
                    )
                }

                // 2) العقد وأسمائها
                val linkingFrom = ui.linkingFromId
                for (site in ui.sites) {
                    val node = ui.positions[site.id] ?: continue
                    val center = Offset(node.x, node.y)
                    val isSource = linkingFrom == site.id
                    val isCandidate = linkingFrom != null && !isSource

                    // هالة نابضة حول مصدر الربط الجاري
                    if (isSource) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.35f * pulse),
                            radius = NetworkLayout.NODE_RADIUS + 10f + 8f * pulse,
                            center = center
                        )
                    }

                    drawCircle(
                        color = nodeColors[site.id] ?: nodeFallback,
                        radius = NetworkLayout.NODE_RADIUS,
                        center = center
                    )

                    val (borderColor, borderWidth) = when {
                        isSource -> primaryColor to 4f
                        isCandidate -> candidateBorder to 3f
                        else -> faintBorder to 1.5f
                    }
                    drawCircle(
                        color = borderColor,
                        radius = NetworkLayout.NODE_RADIUS,
                        center = center,
                        style = Stroke(borderWidth)
                    )

                    // اسم الموقع تحت العقد، مقصوصًا على عرض الصندوق
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
                            node.x - measured.size.width / 2f,
                            node.y + NetworkLayout.NODE_RADIUS + 8f
                        )
                    )
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
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                onClick = { transform.fit(viewport) },
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
                    icon = Icons.Filled.OpenInNew,
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
    ui: GalaxyNetworkUiState
): Long? {
    val world = transform.screenToWorld(screen)
    val reach = NetworkLayout.NODE_RADIUS * 1.7f
    var bestId: Long? = null
    var bestDist = reach
    for ((id, node) in ui.positions) {
        val dx = node.x - world.x
        val dy = node.y - world.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= bestDist) {
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
