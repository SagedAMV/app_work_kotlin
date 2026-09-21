package com.majarra.galaxy.ui.screens.emergency

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.domain.model.VisitOutcome
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.EmergencyVisitValidator
import com.majarra.galaxy.domain.usecase.SaveEmergencyVisitUseCase
import com.majarra.galaxy.ui.anim.DrawnCheck
import com.majarra.galaxy.ui.anim.GalaxyNumberMorph
import com.majarra.galaxy.ui.anim.GalaxySnackbarHost
import com.majarra.galaxy.ui.anim.GlowButton
import com.majarra.galaxy.ui.anim.StaggeredItem
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * شاشة «النزول الطارئ / الاستكشاف» (النسخة 2.4 — تعليمات هذه الجلسة):
 * تُفتح من زر «طارئ» داخل الموقع، وتسجل سبب النزول (إلزامي) مع نتيجة
 * من ثلاث نتائج:
 *  - لا توجد مشكلة: بلا حقول إضافية.
 *  - تم حل المشكلة: وصف المشكلة مع حلها + المواد المستخدمة (اختيارية
 *    من الكتالوج الموحد).
 *  - لم يتم حل المشكلة: وصف المشكلة + تحليلات وحلول متوقعة (اختيارية).
 * زر الحفظ ثابت أسفل الشاشة.
 */
@HiltViewModel
class EmergencyVisitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    siteRepo: SiteRepository,
    materialRepo: MaterialRepository,
    private val saveEmergencyVisit: SaveEmergencyVisitUseCase
) : ViewModel() {

    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L

    /** الموقع — لعرض اسمه في العنوان */
    val site = siteRepo.observeSite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** كتالوج المواد الموحد — تختار منه المواد المستخدمة في الحل */
    val materialsCatalog = materialRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        reason: String,
        notes: String,
        outcome: VisitOutcome,
        problemDescription: String,
        usedMaterials: List<String>,
        analysis: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                saveEmergencyVisit(
                    EmergencyVisit(
                        siteId = siteId,
                        reason = reason,
                        notes = notes,
                        outcome = outcome,
                        problemDescription = problemDescription,
                        usedMaterials = usedMaterials.joinToString("\n"),
                        analysis = analysis
                    )
                )
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyVisitScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: EmergencyVisitViewModel = hiltViewModel()
) {
    val site by viewModel.site.collectAsStateWithLifecycle()
    val catalog by viewModel.materialsCatalog.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var reason by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var reasonError by remember { mutableStateOf<String?>(null) }
    var outcome by rememberSaveable { mutableStateOf<VisitOutcome?>(null) }
    var outcomeError by remember { mutableStateOf<String?>(null) }
    var problemDescription by rememberSaveable { mutableStateOf("") }
    var problemError by remember { mutableStateOf<String?>(null) }
    var analysis by rememberSaveable { mutableStateOf("") }
    var analysisError by remember { mutableStateOf<String?>(null) }
    // المواد المستخدمة: قائمة أسماء تختار من الكتالوج الموحد (اختيارية)
    val materialsSaver = listSaver<List<String>, String>(
        save = { it.toList() },
        restore = { it.toList() }
    )
    var usedMaterials by rememberSaveable(stateSaver = materialsSaver) {
        mutableStateOf(emptyList<String>())
    }

    val cleanReason = remember(reason) { reason.trim().replace(Regex("\\s+"), " ") }
    val cleanProblem = remember(problemDescription) { problemDescription.trim().replace(Regex("\\s+"), " ") }
    val cleanAnalysis = remember(analysis) { analysis.trim().replace(Regex("\\s+"), " ") }
    val requiresProblem = outcome == VisitOutcome.RESOLVED || outcome == VisitOutcome.UNRESOLVED
    val canSave = cleanReason.isNotEmpty() &&
        cleanReason.length <= EmergencyVisitValidator.MAX_REASON &&
        outcome != null &&
        (!requiresProblem || cleanProblem.isNotEmpty()) &&
        cleanProblem.length <= EmergencyVisitValidator.MAX_PROBLEM &&
        cleanAnalysis.length <= EmergencyVisitValidator.MAX_ANALYSIS

    Scaffold(
        snackbarHost = { GalaxySnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(site?.name?.let { "نزول طارئ — $it" } ?: "نزول طارئ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        // زر الحفظ ثابت أسفل الشاشة حسب تعليمات هذه الجلسة
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                GlowButton(
                    onClick = {
                        // نسخة محلية: الخصائص المفوّضة (by) لا تدعم الإسناد
                        // الذكي، فننسخ القيمة قبل الفروع.
                        val selectedOutcome = outcome
                        when {
                            cleanReason.isEmpty() -> {
                                reasonError = "سبب النزول مطلوب"
                            }
                            cleanReason.length > EmergencyVisitValidator.MAX_REASON -> {
                                reasonError = "سبب النزول طويل جدًا (الحد ${EmergencyVisitValidator.MAX_REASON} حرفًا)"
                            }
                            selectedOutcome == null -> {
                                outcomeError = "اختر نتيجة النزول أولًا"
                            }
                            (selectedOutcome == VisitOutcome.RESOLVED || selectedOutcome == VisitOutcome.UNRESOLVED) && cleanProblem.isEmpty() -> {
                                problemError = "وصف المشكلة مطلوب لهذه النتيجة"
                            }
                            cleanProblem.length > EmergencyVisitValidator.MAX_PROBLEM -> {
                                problemError = "وصف المشكلة طويل جدًا (الحد ${EmergencyVisitValidator.MAX_PROBLEM} حرفًا)"
                            }
                            cleanAnalysis.length > EmergencyVisitValidator.MAX_ANALYSIS -> {
                                analysisError = "التحليل طويل جدًا (الحد ${EmergencyVisitValidator.MAX_ANALYSIS} حرفًا)"
                            }
                            else -> viewModel.save(
                                reason = cleanReason,
                                notes = notes,
                                outcome = selectedOutcome,
                                problemDescription = cleanProblem,
                                usedMaterials = usedMaterials,
                                analysis = cleanAnalysis,
                                onSaved = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("تم حفظ النزول الطارئ")
                                        onBack()
                                    }
                                },
                                onError = { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            )
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) { Text("حفظ") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StaggeredItem(index = 0, trigger = "emergency-form") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "تسجيل نزول طارئ للموقع",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // سبب النزول/الغرض — إلزامي حسب تعليمات هذه الجلسة
                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                            reasonError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب النزول / الغرض (إلزامي)") },
                        placeholder = { Text("مثال: فحص البلاغ، صيانة طارئة، استكشاف…") },
                        minLines = 2,
                        maxLines = 4,
                        isError = reasonError != null || cleanReason.length > EmergencyVisitValidator.MAX_REASON,
                        supportingText = {
                            when {
                                reasonError != null -> Text(reasonError!!)
                                cleanReason.length > EmergencyVisitValidator.MAX_REASON ->
                                    Text("الحد الأقصى ${EmergencyVisitValidator.MAX_REASON} حرفًا")
                                else -> Text("${cleanReason.length}/${EmergencyVisitValidator.MAX_REASON}")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ملاحظات (اختياري)") },
                        minLines = 2,
                        maxLines = 4
                    )

                    // نتيجة النزول بزر مقسّم بحبة منزلق (اختيار 38 من
                    // الجولة الثالثة) بدل ثلاثة أزرار منفصلة
                    SectionTitle("نتيجة النزول")
                    OutcomeSegmented(
                        selected = outcome,
                        onSelect = {
                            outcome = it
                            outcomeError = null
                            if (it == VisitOutcome.NO_PROBLEM) {
                                problemError = null
                                analysisError = null
                            }
                        }
                    )
                    outcomeError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // «تم حل المشكلة»: وصف المشكلة مع حلها + المواد المستخدمة
            // (اختيارية) — يظهر فقط عند اختيار هذه النتيجة.
            AnimatedVisibility(
                visible = outcome == VisitOutcome.RESOLVED,
                enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = problemDescription,
                        onValueChange = {
                            problemDescription = it
                            problemError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وصف المشكلة والحل") },
                        placeholder = { Text("اكتب المشكلة وكيف حُلّت…") },
                        minLines = 3,
                        maxLines = 6,
                        isError = problemError != null || cleanProblem.length > EmergencyVisitValidator.MAX_PROBLEM,
                        supportingText = {
                            when {
                                problemError != null -> Text(problemError!!)
                                cleanProblem.length > EmergencyVisitValidator.MAX_PROBLEM ->
                                    Text("الحد الأقصى ${EmergencyVisitValidator.MAX_PROBLEM} حرفًا")
                                else -> Text("${cleanProblem.length}/${EmergencyVisitValidator.MAX_PROBLEM}")
                            }
                        }
                    )
                    UsedMaterialsSection(
                        catalog = catalog,
                        selected = usedMaterials,
                        onChange = { usedMaterials = it }
                    )
                }
            }

            // «لم يتم حل المشكلة»: وصف المشكلة + تحليلات وحلول متوقعة
            // (اختيارية) — يظهر فقط عند اختيار هذه النتيجة.
            AnimatedVisibility(
                visible = outcome == VisitOutcome.UNRESOLVED,
                enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = problemDescription,
                        onValueChange = {
                            problemDescription = it
                            problemError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وصف المشكلة التي لم تُحل") },
                        placeholder = { Text("اكتب المشكلة التي لم تُحل…") },
                        minLines = 3,
                        maxLines = 6,
                        isError = problemError != null || cleanProblem.length > EmergencyVisitValidator.MAX_PROBLEM,
                        supportingText = {
                            when {
                                problemError != null -> Text(problemError!!)
                                cleanProblem.length > EmergencyVisitValidator.MAX_PROBLEM ->
                                    Text("الحد الأقصى ${EmergencyVisitValidator.MAX_PROBLEM} حرفًا")
                                else -> Text("${cleanProblem.length}/${EmergencyVisitValidator.MAX_PROBLEM}")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = analysis,
                        onValueChange = {
                            analysis = it
                            analysisError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تحليلات المشكلة والحلول المتوقعة (اختياري)") },
                        placeholder = { Text("ما التحليل؟ وما الحلول المتوقعة؟") },
                        minLines = 2,
                        maxLines = 5,
                        isError = analysisError != null || cleanAnalysis.length > EmergencyVisitValidator.MAX_ANALYSIS,
                        supportingText = {
                            when {
                                analysisError != null -> Text(analysisError!!)
                                cleanAnalysis.length > EmergencyVisitValidator.MAX_ANALYSIS ->
                                    Text("الحد الأقصى ${EmergencyVisitValidator.MAX_ANALYSIS} حرفًا")
                                else -> Text("${cleanAnalysis.length}/${EmergencyVisitValidator.MAX_ANALYSIS}")
                            }
                        }
                    )
                }
            }
            // «لا توجد مشكلة»: لا تظهر حقول إضافية — زر الحفظ أسفل فقط.
        }
    }
}

/* ═══════════════════ زر نتيجة النزول المقسّم ═══════════════════ */

/**
 * زر النتيجة المقسّم (اختيار 38 = خيار 12 من اختيارات الجولة
 * الثالثة): حبة بلون النتيجة الدلالي تنزلق بنابض خلف الخيار المختار
 * مع ظهور علامة صح داخله — نمط M3 SegmentedButton بمؤشر مخصص.
 * المواقع تُقاس فعليًا عند التخطيط فيصح الانزلاق بأي اتجاه عرض.
 */
@Composable
private fun OutcomeSegmented(
    selected: VisitOutcome?,
    onSelect: (VisitOutcome) -> Unit
) {
    val outcomes = VisitOutcome.entries
    val selectedIndex = selected?.let { s -> outcomes.indexOf(s) } ?: -1
    val density = LocalDensity.current

    val rowLeftPx = remember { mutableStateOf(0f) }
    val itemLayouts = remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) }
    val measured = if (selectedIndex >= 0) itemLayouts.value[selectedIndex] else null

    val pillLeft by animateDpAsState(
        targetValue = with(density) { ((measured?.first ?: 0f) - rowLeftPx.value).toDp() },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "outcome-pill-left"
    )
    val pillWidth by animateDpAsState(
        targetValue = with(density) { (measured?.second ?: 0f).toDp() },
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        label = "outcome-pill-width"
    )
    val pillColor by animateColorAsState(
        targetValue = when (selected) {
            VisitOutcome.NO_PROBLEM -> MaterialTheme.colorScheme.tertiary
            VisitOutcome.RESOLVED -> MaterialTheme.colorScheme.primary
            VisitOutcome.UNRESOLVED -> MaterialTheme.colorScheme.error
            null -> Color.Transparent
        },
        animationSpec = tween(280),
        label = "outcome-pill-color"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .onPlaced { rowLeftPx.value = it.positionInRoot().x }
        ) {
            if (measured != null) {
                // المرتكز TopEnd = الزاوية العليا اليسرى فعليًا في اتجاه
                // التطبيق العربي القسري — تصح معه الإزاحة من الحافة اليسرى
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .absoluteOffset(x = pillLeft, y = 3.dp)
                        .width(pillWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(pillColor)
                )
            }
            Row(Modifier.fillMaxWidth()) {
                outcomes.forEachIndexed { index, outcome ->
                    val isSelected = index == selectedIndex
                    val textColor = when {
                        !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
                        outcome == VisitOutcome.UNRESOLVED -> Color.White
                        else -> Color(0xFF00243A)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onPlaced { c ->
                                val m = c.positionInRoot().x to c.size.width.toFloat()
                                if (itemLayouts.value[index] != m) {
                                    itemLayouts.value = itemLayouts.value + (index to m)
                                }
                            }
                            .clickable { onSelect(outcome) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(180)) + scaleIn(tween(220)),
                                exit = fadeOut(tween(120))
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                outcome.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ═══════════════════ المواد المستخدمة (اختيارية) ═══════════════════ */

/**
 * قسم اختيار المواد التي استُبدلت أو صُرفت لحل المشكلة — اختيار متعدد
 * من الكتالوج الموحد مع بحث، بلا كتابة نصية (نفس فلسفة المواد الموحدة
 * في النسخة 2.2). القسم كله اختياري.
 */
@Composable
private fun UsedMaterialsSection(
    catalog: List<Material>,
    selected: List<String>,
    onChange: (List<String>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(catalog, query) {
        catalog.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    GalaxyCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text("المواد المستخدمة (اختياري)", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "اختر المواد التي استبدلتها أو صرفتها للموقع لحل المشكلة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (catalog.isEmpty()) {
                Text(
                    "الكتالوج الموحد فارغ — افتح شاشة «المواد الموحدة» من الشاشة الرئيسية وأضف موادك أولًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في المواد…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                // إصلاح UX/أداء: كان كتالوج المواد يُبنى كاملًا داخل النموذج
                // الطويل، فمع كتالوج كبير يصبح الوصول إلى الحقول وحفظ النزول
                // متعبًا (تمرير طويل جدًا بلا نهاية). الآن القائمة في نافذة
                // محدودة الارتفاع تُمرَّر وحدها والنموذج يبقى قابلًا للإدارة.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    visible.forEach { material ->
                        val isSelected = selected.contains(material.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DrawnCheck(
                                checked = isSelected,
                                onToggle = { checked ->
                                    onChange(
                                        if (checked) selected + material.name
                                        else selected - material.name
                                    )
                                }
                            )
                            Text(material.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (visible.isEmpty()) {
                        Text(
                            "لا توجد مواد مطابقة للبحث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (selected.isNotEmpty()) {
                    // مروف الأرقام عند تغيّر عدد المواد المحددة
                    // (اختيار 34 من الجولة الثالثة)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "المواد المختارة:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        GalaxyNumberMorph(
                            value = selected.size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
