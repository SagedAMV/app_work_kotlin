package com.majarra.galaxy.ui.screens.emergency

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
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
import com.majarra.galaxy.domain.usecase.SaveEmergencyVisitUseCase
import com.majarra.galaxy.ui.anim.DrawnCheck
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
    var reasonError by remember { mutableStateOf<String?>(null) }
    var outcome by rememberSaveable { mutableStateOf<VisitOutcome?>(null) }
    var outcomeError by remember { mutableStateOf<String?>(null) }
    var problemDescription by rememberSaveable { mutableStateOf("") }
    var analysis by rememberSaveable { mutableStateOf("") }
    // المواد المستخدمة: قائمة أسماء تختار من الكتالوج الموحد (اختيارية)
    val materialsSaver = listSaver<List<String>, String>(
        save = { it.toList() },
        restore = { it.toList() }
    )
    var usedMaterials by rememberSaveable(stateSaver = materialsSaver) {
        mutableStateOf(emptyList<String>())
    }

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
                        val cleanReason = reason.trim()
                        // نسخة محلية: الخصائص المفوّضة (by) لا تدعم الإسناد
                        // الذكي، فننسخ القيمة قبل الفروع.
                        val selectedOutcome = outcome
                        when {
                            cleanReason.isEmpty() -> reasonError = "سبب النزول مطلوب"
                            selectedOutcome == null -> outcomeError = "اختر نتيجة النزول أولًا"
                            else -> viewModel.save(
                                reason = cleanReason,
                                outcome = selectedOutcome,
                                problemDescription = problemDescription,
                                usedMaterials = usedMaterials,
                                analysis = analysis,
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
                        isError = reasonError != null,
                        supportingText = reasonError?.let { { Text(it) } }
                    )

                    // أزرار النتيجة الثلاثة بجانب بعض حسب تعليمات هذه الجلسة
                    SectionTitle("نتيجة النزول")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutcomeChoice(
                            outcome = VisitOutcome.NO_PROBLEM,
                            selected = outcome == VisitOutcome.NO_PROBLEM,
                            onSelect = {
                                outcome = it
                                outcomeError = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutcomeChoice(
                            outcome = VisitOutcome.RESOLVED,
                            selected = outcome == VisitOutcome.RESOLVED,
                            onSelect = {
                                outcome = it
                                outcomeError = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutcomeChoice(
                            outcome = VisitOutcome.UNRESOLVED,
                            selected = outcome == VisitOutcome.UNRESOLVED,
                            onSelect = {
                                outcome = it
                                outcomeError = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                        onValueChange = { problemDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وصف المشكلة والحل") },
                        placeholder = { Text("اكتب المشكلة وكيف حُلّت…") },
                        minLines = 3,
                        maxLines = 6
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
                        onValueChange = { problemDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("وصف المشكلة التي لم تُحل") },
                        placeholder = { Text("اكتب المشكلة التي لم تُحل…") },
                        minLines = 3,
                        maxLines = 6
                    )
                    OutlinedTextField(
                        value = analysis,
                        onValueChange = { analysis = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("تحليلات المشكلة والحلول المتوقعة (اختياري)") },
                        placeholder = { Text("ما التحليل؟ وما الحلول المتوقعة؟") },
                        minLines = 2,
                        maxLines = 5
                    )
                }
            }
            // «لا توجد مشكلة»: لا تظهر حقول إضافية — زر الحفظ أسفل فقط.
        }
    }
}

/* ═══════════════════ زر نتيجة النزول ═══════════════════ */

/**
 * زر نتيجة واحد من الثلاثة: غير محدد = مخطط، محدد = معبأ بلونه الدلالي
 * (أخضر/أساسي/أحمر). الألوان من نسق التطبيق لا قيم ثابتة.
 */
@Composable
private fun OutcomeChoice(
    outcome: VisitOutcome,
    selected: Boolean,
    onSelect: (VisitOutcome) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = when (outcome) {
        VisitOutcome.NO_PROBLEM -> MaterialTheme.colorScheme.tertiary
        VisitOutcome.RESOLVED -> MaterialTheme.colorScheme.primary
        VisitOutcome.UNRESOLVED -> MaterialTheme.colorScheme.error
    }
    val label: @Composable () -> Unit = {
        Text(
            outcome.label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
    if (selected) {
        Button(
            onClick = { onSelect(outcome) },
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = selectedColor,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
        ) { label() }
    } else {
        OutlinedButton(
            onClick = { onSelect(outcome) },
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
        ) { label() }
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    Text(
                        "المواد المختارة: ${selected.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
