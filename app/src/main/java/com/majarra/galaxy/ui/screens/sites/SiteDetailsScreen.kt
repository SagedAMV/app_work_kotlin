package com.majarra.galaxy.ui.screens.sites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.*
import com.majarra.galaxy.domain.model.RequestStatus
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.*
import com.majarra.galaxy.domain.usecase.*
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.util.MaterialLines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class DetailsSection { MATERIALS, NEEDS, WITHDRAWALS, EMERGENCY }

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSite: ObserveSiteUseCase,
    private val detailRepo: SiteDetailRepository,
    materialRepo: MaterialRepository,
    withdrawalRepo: WithdrawalRepository,
    requestRepo: MaterialRequestRepository,
    emergencyRepo: EmergencyVisitRepository,
    private val removeMaterial: RemoveSiteMaterialUseCase,
    private val withdrawMaterial: WithdrawMaterialForMaintenanceUseCase,
    private val markFixed: MarkWithdrawalFixedUseCase,
    private val markNotFixed: MarkWithdrawalNotFixedUseCase,
    private val returnItem: ReturnWithdrawnItemUseCase,
    private val deleteWithdrawal: DeleteWithdrawalUseCase,
    private val submitRequests: SubmitMaterialRequestsUseCase,
    private val addMaterialRequest: AddMaterialAsRequestUseCase,
    private val approveRequest: ApproveMaterialRequestUseCase,
    private val rejectRequest: RejectMaterialRequestUseCase,
    private val restoreRequest: RestoreMaterialRequestUseCase,
    private val deleteRequest: DeleteMaterialRequestUseCase
) : ViewModel() {
    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L
    val site = observeSite(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val detail = detailRepo.observeBySite(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val catalog = materialRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val withdrawals = withdrawalRepo.observeBySite(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val requests = requestRepo.observeBySite(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val emergencies = emergencyRepo.observeBySite(siteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val available: StateFlow<List<String>> = combine(detail, withdrawals) { d, ws ->
        val open = ws.filter { it.status.isOpen }.map { it.itemName.lowercase() }.toSet()
        MaterialLines.parse(d?.availableMaterials.orEmpty()).map { it.text }.filterNot { it.lowercase() in open }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun remove(name: String, done: () -> Unit) = action(done) { removeMaterial(siteId, name) }
    fun withdraw(name: String, reason: String, notes: String, done: () -> Unit, error: (String) -> Unit) = safe(done, error) { withdrawMaterial(siteId, name, reason, notes) }
    fun fixed(w: Withdrawal, text: String, done: () -> Unit, error: (String) -> Unit) = safe(done, error) { markFixed(w, text) }
    fun notFixed(w: Withdrawal, text: String, done: () -> Unit, error: (String) -> Unit) = safe(done, error) { markNotFixed(w, text) }
    fun returnItem(w: Withdrawal, done: () -> Unit) = action(done) { returnItem(w) }
    fun deleteWithdrawal(w: Withdrawal, done: () -> Unit) = action(done) { deleteWithdrawal(w) }
    fun submit(names: List<String>, done: () -> Unit, error: (String) -> Unit) = safe(done, error) { submitRequests(siteId, names) }
    fun addRequest(name: String, done: () -> Unit, error: (String) -> Unit) = safe(done, error) { addMaterialRequest(siteId, name) }
    fun approve(r: MaterialRequest, done: () -> Unit) = action(done) { approveRequest(r) }
    fun reject(r: MaterialRequest, done: () -> Unit) = action(done) { rejectRequest(r) }
    fun restore(r: MaterialRequest, done: () -> Unit) = action(done) { restoreRequest(r) }
    fun deleteRequest(r: MaterialRequest, done: () -> Unit) = action(done) { deleteRequest(r) }

    private fun action(done: () -> Unit, block: suspend () -> Unit) = viewModelScope.launch { block(); done() }
    private fun safe(done: () -> Unit, error: (String) -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
        try { block(); done() } catch (e: IllegalArgumentException) { error(e.message ?: "مدخلات غير صالحة") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailsScreen(
    onBack: () -> Unit,
    onOpenEmergency: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SiteDetailsViewModel = hiltViewModel()
) {
    val site by viewModel.site.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val withdrawals by viewModel.withdrawals.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val emergencies by viewModel.emergencies.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(DetailsSection.MATERIALS) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<@Composable (() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    fun message(text: String) { scope.launch { snackbarHostState.showSnackbar(text) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(site?.name ?: "تفاصيل الموقع") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.Menu, "قائمة الموقع") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("المواد") }, leadingIcon = { Icon(Icons.Default.Inventory2, null) }, onClick = { section = DetailsSection.MATERIALS; menu = false })
                        DropdownMenuItem(text = { Text("احتياج موقع") }, leadingIcon = { Icon(Icons.Default.AddShoppingCart, null) }, onClick = { section = DetailsSection.NEEDS; menu = false })
                        DropdownMenuItem(text = { Text("الاحتياجات") }, leadingIcon = { Icon(Icons.Default.ListAlt, null) }, onClick = { section = DetailsSection.NEEDS; menu = false })
                        DropdownMenuItem(text = { Text("المسحوبات") }, leadingIcon = { Icon(Icons.Default.Build, null) }, onClick = { section = DetailsSection.WITHDRAWALS; menu = false })
                        DropdownMenuItem(text = { Text("النزول الطارئ") }, leadingIcon = { Icon(Icons.Default.WarningAmber, null) }, onClick = { menu = false; onOpenEmergency(viewModel.siteId) })
                    }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = section.ordinal) {
                DetailsSection.values().forEach { s -> Tab(selected = section == s, onClick = { section = s }, text = { Text(when(s) { DetailsSection.MATERIALS -> "المواد"; DetailsSection.NEEDS -> "الاحتياجات"; DetailsSection.WITHDRAWALS -> "المسحوبات"; DetailsSection.EMERGENCY -> "الطارئ" }) }) }
            }
            when (section) {
                DetailsSection.MATERIALS -> MaterialsSection(available, catalog, onRemove = { name -> viewModel.remove(name) { message("تم حذف المادة") } }, onWithdraw = { name -> dialog = { WithdrawDialog(name, { reason, notes, close -> viewModel.withdraw(name, reason, notes, close, { error = it }) }, { dialog = null }) } })
                DetailsSection.NEEDS -> NeedsSection(catalog, requests, onSubmit = { names -> viewModel.submit(names, { message("تم رفع الطلبات") }, { error = it }) }, onAdd = { name -> viewModel.addRequest(name, { message("تمت إضافة المادة ورفع الطلب") }, { error = it }) }, onApprove = { viewModel.approve(it) { message("أضيفت المادة إلى الموقع") } }, onReject = { viewModel.reject(it) { message("تم رفض الطلب") } }, onRestore = { viewModel.restore(it) { message("تم استرجاع الطلب") } }, onDelete = { viewModel.deleteRequest(it) { message("حُذف الطلب") } })
                DetailsSection.WITHDRAWALS -> WithdrawalsSection(withdrawals, onFixed = { w -> dialog = { DecisionDialog("تم الإصلاح", "كيف أصلحت المشكلة؟", { text, close -> viewModel.fixed(w, text, close, { error = it }) }, { dialog = null }) } }, onNotFixed = { w -> dialog = { DecisionDialog("لم يتم الإصلاح", "سبب عدم الإصلاح", { text, close -> viewModel.notFixed(w, text, close, { error = it }) }, { dialog = null }) } }, onReturn = { viewModel.returnItem(it) { message("أعيدت المادة إلى الموقع") } }, onDelete = { viewModel.deleteWithdrawal(it) { message("حُذف السجل") } })
                DetailsSection.EMERGENCY -> EmergencySummary(emergencies)
            }
        }
    }
    error?.let { msg -> AlertDialog(onDismissRequest = { error = null }, title = { Text("تعذر الحفظ") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { error = null }) { Text("حسنًا") } }) }
    dialog?.invoke()
}

@Composable private fun MaterialsSection(items: List<String>, catalog: List<Material>, onRemove: (String) -> Unit, onWithdraw: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("المواد") }
        if (items.isEmpty()) item { EmptyState(Icons.Default.Inventory2, "لا توجد مواد", "أضف المواد من واجهة الاحتياجات ثم وافق عليها") }
        items(items, key = { it }) { name -> GalaxyCard { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f)); OutlinedButton(onClick = { onWithdraw(name) }) { Text("سحب للصيانة") }; IconButton(onClick = { onRemove(name) }) { Icon(Icons.Default.Delete, "حذف") } } } }
    }
}

@Composable private fun NeedsSection(catalog: List<Material>, requests: List<MaterialRequest>, onSubmit: (List<String>) -> Unit, onAdd: (String) -> Unit, onApprove: (MaterialRequest) -> Unit, onReject: (MaterialRequest) -> Unit, onRestore: (MaterialRequest) -> Unit, onDelete: (MaterialRequest) -> Unit) {
    var selected by remember { mutableStateOf(setOf<String>()) }; var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onSubmit(selected.toList()); selected = emptySet() }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("رفع الاحتياج") }; OutlinedButton(onClick = { add = true }) { Text("إضافة مادة") } }
        SectionTitle("المواد المحفوظة")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(catalog, key = { it.id }) { m -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = m.name in selected, onCheckedChange = { selected = if (it) selected + m.name else selected - m.name }); Text(m.name) } } }
        SectionTitle("طلبات الاحتياج")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(requests, key = { it.id }) { r -> RequestCard(r, onApprove, onReject, onRestore, onDelete) } }
    }
    if (add) TextInputDialog("إضافة مادة ورفع طلب", "اسم المادة", { onAdd(it) }, { add = false })
}

@Composable
private fun RequestCard(r: MaterialRequest, approve: (MaterialRequest) -> Unit, reject: (MaterialRequest) -> Unit, restore: (MaterialRequest) -> Unit, delete: (MaterialRequest) -> Unit) {
    GalaxyCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text(r.materialName, Modifier.weight(1f)); Text(r.status.label, color = MaterialTheme.colorScheme.primary) }
            when (r.status) {
                RequestStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { approve(r) }) { Text("موافقة") }; OutlinedButton(onClick = { reject(r) }) { Text("رفض") }; TextButton(onClick = { delete(r) }) { Text("حذف") } }
                RequestStatus.REJECTED -> Row { Button(onClick = { restore(r) }) { Text("استرجاع") }; TextButton(onClick = { delete(r) }) { Text("حذف") } }
                RequestStatus.APPROVED -> Text("أضيفت إلى المواد")
            }
        }
    }
}

@Composable
private fun WithdrawalsSection(items: List<Withdrawal>, onFixed: (Withdrawal) -> Unit, onNotFixed: (Withdrawal) -> Unit, onReturn: (Withdrawal) -> Unit, onDelete: (Withdrawal) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("المسحوبات للصيانة") }
        if (items.isEmpty()) item { EmptyState(Icons.Default.Build, "لا توجد مواد مسحوبة", "اسحب مادة من قسم المواد") }
        items(items, key = { it.id }) { w ->
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text(w.itemName, Modifier.weight(1f)); Text(w.status.label, color = MaterialTheme.colorScheme.primary) }
                    if (w.withdrawReason.isNotBlank()) Text("سبب السحب: ${w.withdrawReason}")
                    if (w.fixedNote.isNotBlank()) Text("كيف أُصلحت: ${w.fixedNote}")
                    if (w.notFixedReason.isNotBlank()) Text("سبب عدم الإصلاح: ${w.notFixedReason}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (w.status) {
                            WithdrawalStatus.WITHDRAWN, WithdrawalStatus.IN_MAINTENANCE -> { Button(onClick = { onFixed(w) }) { Text("تم الإصلاح") }; OutlinedButton(onClick = { onNotFixed(w) }) { Text("لم يتم الإصلاح") } }
                            WithdrawalStatus.FIXED, WithdrawalStatus.NOT_FIXED -> Button(onClick = { onReturn(w) }) { Text("إرجاع للموقع") }
                            WithdrawalStatus.RETURNED -> Text("أُعيدت للموقع")
                        }
                        IconButton(onClick = { onDelete(w) }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencySummary(visits: List<EmergencyVisit>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("سجل النزول الطارئ") }
        if (visits.isEmpty()) item { EmptyState(Icons.Default.WarningAmber, "لا توجد نزولات", "استخدم خيار النزول الطارئ من القائمة") }
        items(visits, key = { it.id }) { v -> GalaxyCard { Column(Modifier.padding(14.dp)) { Row { Text(v.reason, Modifier.weight(1f)); Text(v.outcome.label, color = MaterialTheme.colorScheme.primary) }; Text(v.visitDate.formatDateTime()); if (v.notes.isNotBlank()) Text(v.notes); if (v.problemDescription.isNotBlank()) Text(v.problemDescription) } } }
    }
}

@Composable
private fun WithdrawDialog(name: String, onSave: (String, String, () -> Unit) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onCancel, title = { Text("سحب «$name» للصيانة") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(reason, { reason = it }, label = { Text("سبب السحب للصيانة (إجباري)") }); OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (اختياري)") }) } }, confirmButton = { Button(onClick = { onSave(reason, notes, onCancel) }, enabled = reason.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } })
}

@Composable
private fun DecisionDialog(title: String, label: String, onSave: (String, () -> Unit) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { OutlinedTextField(text, { text = it }, label = { Text("$label (إجباري)") }) }, confirmButton = { Button(onClick = { onSave(text, onCancel) }, enabled = text.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } })
}

@Composable
private fun TextInputDialog(title: String, label: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { OutlinedTextField(text, { text = it }, label = { Text(label) }) }, confirmButton = { Button(onClick = { onSave(text); onCancel() }, enabled = text.isNotBlank()) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } })
}
