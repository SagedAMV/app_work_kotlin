package com.majarra.galaxy.ui.screens.emergency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.SaveEmergencyVisitUseCase
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmergencyVisitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    siteRepo: SiteRepository,
    materialRepo: MaterialRepository,
    emergencyRepo: EmergencyVisitRepository,
    private val saveEmergencyVisit: SaveEmergencyVisitUseCase
) : ViewModel() {

    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L

    val site = siteRepo.observeSite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val materialsCatalog = materialRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visits = emergencyRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(
        reason: String,
        notes: String,
        outcome: VisitOutcome,
        problemDescription: String,
        usedMaterial: String?,
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
                        usedMaterials = usedMaterial?.trim().orEmpty(),
                        analysis = ""
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
    val visits by viewModel.visits.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }

    // مدخلات النموذج (تظهر فقط عند الضغط على الزر العائم +)
    var reason by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf<VisitOutcome?>(null) }
    var usedMaterial by rememberSaveable { mutableStateOf<String?>(null) }
    var notSolvedReason by rememberSaveable { mutableStateOf("") }

    // رسالة خطأ عامة بسيطة
    var error by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        reason = ""
        notes = ""
        outcome = null
        usedMaterial = null
        notSolvedReason = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(site?.name?.let { "النزول الطارئ — $it" } ?: "النزول الطارئ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { sheetOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة نزول")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("سجل النزول الطارئ (${visits.size})") }
            if (visits.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.WarningAmber,
                        title = "لا توجد نزولات",
                        subtitle = "اضغط زر (+) لإضافة نزول طارئ"
                    )
                }
            }
            items(visits, key = { it.id }) { v ->
                EmergencyVisitCard(v)
            }
        }

        if (sheetOpen) {
            ModalBottomSheet(
                onDismissRequest = {
                    sheetOpen = false
                    resetForm()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("تسجيل نزول طارئ", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب النزول (إجباري)") },
                        minLines = 2,
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ملاحظات") },
                        minLines = 2,
                        maxLines = 4
                    )

                    SectionTitle("النتيجة")
                    OutcomeButtons(
                        selected = outcome,
                        onSelect = {
                            outcome = it
                            // تنظيف حقول مرتبطة بخيارات أخرى
                            if (it != VisitOutcome.UNRESOLVED) notSolvedReason = ""
                            if (it != VisitOutcome.RESOLVED) usedMaterial = null
                        }
                    )

                    // خيار: تم حل المشكلة => زر اختياري لاختيار مادة واحدة
                    if (outcome == VisitOutcome.RESOLVED) {
                        GalaxyCard {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("المادة التي ساعدت في الحل (اختياري)")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(onClick = { pickerOpen = true }) {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("اختيار مادة")
                                    }
                                    if (usedMaterial != null) {
                                        Surface(
                                            tonalElevation = 2.dp,
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Default.Inventory2, contentDescription = null)
                                                Text(
                                                    usedMaterial!!,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                IconButton(onClick = { usedMaterial = null }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                                }
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "يمكن تركها فارغة وشرح الحل داخل سبب النزول.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // خيار: لم يتم حل المشكلة => حقل إلزامي
                    if (outcome == VisitOutcome.UNRESOLVED) {
                        OutlinedTextField(
                            value = notSolvedReason,
                            onValueChange = { notSolvedReason = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("سبب عدم حل المشكلة (إجباري)") },
                            minLines = 2,
                            maxLines = 5
                        )
                    }

                    val canSave = reason.trim().isNotEmpty() && outcome != null &&
                        (outcome != VisitOutcome.UNRESOLVED || notSolvedReason.trim().isNotEmpty())

                    Button(
                        onClick = {
                            val selectedOutcome = outcome ?: return@Button
                            val problem = if (selectedOutcome == VisitOutcome.UNRESOLVED) notSolvedReason else ""

                            viewModel.save(
                                reason = reason,
                                notes = notes,
                                outcome = selectedOutcome,
                                problemDescription = problem,
                                usedMaterial = usedMaterial,
                                onSaved = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("تم حفظ النزول الطارئ")
                                    }
                                    sheetOpen = false
                                    resetForm()
                                },
                                onError = { error = it }
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ")
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (pickerOpen) {
            MaterialPickerDialog(
                catalog = catalog,
                onSelect = { selected -> usedMaterial = selected; pickerOpen = false },
                onDismiss = { pickerOpen = false }
            )
        }

        error?.let { msg ->
            AlertDialog(
                onDismissRequest = { error = null },
                title = { Text("تعذر الحفظ") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { error = null }) { Text("حسنًا") } }
            )
        }
    }
}

@Composable
private fun EmergencyVisitCard(v: EmergencyVisit) {
    GalaxyCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.reason, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.outcome.label, color = MaterialTheme.colorScheme.primary)
            }
            Text(v.visitDate.formatDateTime(), style = MaterialTheme.typography.bodySmall)
            if (v.notes.isNotBlank()) Text("ملاحظات: ${v.notes}")
            if (v.usedMaterials.isNotBlank()) Text("مادة مستخدمة: ${v.usedMaterials.lines().first()}")
            if (v.problemDescription.isNotBlank()) Text("سبب/وصف: ${v.problemDescription}")
        }
    }
}

@Composable
private fun OutcomeButtons(selected: VisitOutcome?, onSelect: (VisitOutcome) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutcomeButton(
            title = "لا توجد مشكلة",
            selected = selected == VisitOutcome.NO_PROBLEM,
            onClick = { onSelect(VisitOutcome.NO_PROBLEM) },
            modifier = Modifier.weight(1f)
        )
        OutcomeButton(
            title = "تم حل المشكلة",
            selected = selected == VisitOutcome.RESOLVED,
            onClick = { onSelect(VisitOutcome.RESOLVED) },
            modifier = Modifier.weight(1f)
        )
        OutcomeButton(
            title = "لم يتم حل المشكلة",
            selected = selected == VisitOutcome.UNRESOLVED,
            onClick = { onSelect(VisitOutcome.UNRESOLVED) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OutcomeButton(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors)
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MaterialPickerDialog(
    catalog: List<Material>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = remember(catalog, q) {
        if (q.isEmpty()) catalog else catalog.filter { it.name.contains(q, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار مادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    EmptyState(Icons.Default.Inventory2, "لا توجد نتائج", "جرّب كلمة أخرى")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { m ->
                            GalaxyCard(onClick = { onSelect(m.name) }) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null)
                                    Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
