package com.majarra.galaxy.ui.screens.sites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.WarningAmber
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

/*
 * ============================================================
 * واجهة «تفاصيل الموقع» — النسخة 2.12 (تعليمات إعادة التصميم):
 *  - القسم الرئيسي «المواد» يعرض مواد هذا الموقع فقط، وبجانب كل مادة
 *    زر «سحب للصيانة» (سبب إلزامي + ملاحظات اختيارية).
 *  - قائمة (الثلاث خطوط) أعلى الواجهة بخيارات: احتياج موقع،
 *    الاحتياجات، المسحوبات، النزول الطارئ (+ اختصار المواد).
 *  - «احتياج موقع» = شاشة رفع الاحتياج (اختيار من المواد المحفوظة،
 *    وخيار «إضافة مادة» يظهر عند غياب المادة عن المحفوظ).
 *  - «الاحتياجات» = طلبات الاحتياج بحالتها: موافقة / رفض / حذف /
 *    استرجاع، والموافقة تضيف المادة إلى «المواد» فورًا.
 *  - «المسحوبات» = المواد المسحوبة للصيانة مع قرار (تم الإصلاح /
 *    لم يتم الإصلاح) ثم (إرجاع للموقع).
 *
 * ملاحظات جلسة التحقق العميق (2.12.0) — ما أُصلح في هذا الملف:
 *  1) كان خياري «احتياج موقع» و«الاحتياجات» يفتحان الشاشة نفسها،
 *     فأُفصل القسمان (رفع احتياج / متابعة الطلبات) وفق نص التعليمات.
 *  2) معامل `catalog` في قسم المواد كان غير مستعمل إطلاقًا (تحذير
 *     مترجم Kotlin: Parameter 'catalog' is never used) فحُذف.
 *  3) أيقونتا الرجوع والقائمة كانتا النسخة المهملة غير المرآتية
 *     (`Icons.Default.ArrowBack` / `ListAlt`) — صارتا AutoMirrored
 *     فتنقلبان تلقائيًا في واجهة عربية RTL.
 *  4) قائمة المواد كانت مفتاحة بـ`key = { it }` بلا تعقيم، وأي تكرار
 *     بالبيانات القديمة (سطران بنفس النص) كان يُسقط LazyColumn
 *     باستثناء «مفتاح مكرر» — صار التعقيم مزدوجًا (في الـViewModel
 *     وفي الشاشة) فلا يتكرر نص أبدًا.
 *  5) القوائم المتداخلة (LazyColumn داخل LazyColumn) في قسم الاحتياج
 *     أُبسّطت إلى قائمة واحدة أقسامًا لتجنب تمرير متعارض.
 * ============================================================
 */

private enum class DetailsSection(val label: String) {
    MATERIALS("المواد"),
    DEMAND("احتياج موقع"),
    REQUESTS("الاحتياجات"),
    WITHDRAWALS("المسحوبات")
}

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSite: ObserveSiteUseCase,
    private val detailRepo: SiteDetailRepository,
    materialRepo: MaterialRepository,
    withdrawalRepo: WithdrawalRepository,
    requestRepo: MaterialRequestRepository,
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

    /**
     * مواد الموقع المعروضة: كل ما في «الموجود» عدا ما هو خارج الموقع
     * الآن بسبب سحب مفتوح (لم يُرجع بعد)، ومعقّمة من التكرار حتى لا
     * تتكرر مفاتيح القائمة في الشاشة.
     */
    val available: StateFlow<List<String>> = combine(detail, withdrawals) { d, ws ->
        val open = ws.filter { it.status.isOpen }
            .map { it.itemName.trim().lowercase() }
            .toSet()
        MaterialLines.parse(d?.availableMaterials.orEmpty())
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in open }
            .distinctBy { it.lowercase() }
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
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val withdrawals by viewModel.withdrawals.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(DetailsSection.MATERIALS) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<@Composable (() -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    fun message(text: String) { scope.launch { snackbarHostState.showSnackbar(text) } }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                val base = site?.name ?: "تفاصيل الموقع"
                val extra = if (section == DetailsSection.MATERIALS) "" else " — ${section.label}"
                Text(base + extra)
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.Menu, "قائمة الموقع") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("المواد") },
                            leadingIcon = { Icon(Icons.Default.Inventory2, null) },
                            onClick = { section = DetailsSection.MATERIALS; menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("احتياج موقع") },
                            leadingIcon = { Icon(Icons.Default.AddShoppingCart, null) },
                            onClick = { section = DetailsSection.DEMAND; menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("الاحتياجات") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                            onClick = { section = DetailsSection.REQUESTS; menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("المسحوبات") },
                            leadingIcon = { Icon(Icons.Default.Build, null) },
                            onClick = { section = DetailsSection.WITHDRAWALS; menu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("النزول الطارئ") },
                            leadingIcon = { Icon(Icons.Default.WarningAmber, null) },
                            onClick = { menu = false; onOpenEmergency(viewModel.siteId) }
                        )
                    }
                }
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (section) {
                DetailsSection.MATERIALS -> MaterialsSection(
                    items = available,
                    onRemove = { name -> viewModel.remove(name) { message("تم حذف المادة") } },
                    onWithdraw = { name -> dialog = { WithdrawDialog(name, { reason, notes, close -> viewModel.withdraw(name, reason, notes, close, { error = it }) }, { dialog = null }) } }
                )
                DetailsSection.DEMAND -> DemandSection(
                    catalog = catalog,
                    pendingCount = requests.count { it.status == RequestStatus.PENDING },
                    onSubmit = { names -> viewModel.submit(names, { message("تم رفع الاحتياج") }, { error = it }) },
                    onAdd = { name -> viewModel.addRequest(name, { message("تمت إضافة المادة ورفع الطلب") }, { error = it }) },
                    onOpenRequests = { section = DetailsSection.REQUESTS }
                )
                DetailsSection.REQUESTS -> RequestsSection(
                    requests = requests,
                    onApprove = { viewModel.approve(it) { message("أضيفت المادة إلى المواد") } },
                    onReject = { viewModel.reject(it) { message("تم رفض الطلب") } },
                    onRestore = { viewModel.restore(it) { message("تم استرجاع الطلب") } },
                    onDelete = { viewModel.deleteRequest(it) { message("حُذف الطلب") } },
                    onOpenDemand = { section = DetailsSection.DEMAND }
                )
                DetailsSection.WITHDRAWALS -> WithdrawalsSection(
                    items = withdrawals,
                    onFixed = { w -> dialog = { DecisionDialog("تم الإصلاح", "كيف أصلحت المشكلة؟", { text, close -> viewModel.fixed(w, text, close, { error = it }) }, { dialog = null }) } },
                    onNotFixed = { w -> dialog = { DecisionDialog("لم يتم الإصلاح", "سبب عدم الإصلاح", { text, close -> viewModel.notFixed(w, text, close, { error = it }) }, { dialog = null }) } },
                    onReturn = { viewModel.returnItem(it) { message("أعيدت المادة إلى الموقع") } },
                    onDelete = { viewModel.deleteWithdrawal(it) { message("حُذف السجل") } }
                )
            }
        }
    }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("تعذر الحفظ") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("حسنًا") } }
        )
    }
    dialog?.invoke()
}

/** قسم «المواد»: مواد هذا الموقع فقط + سحب للصيانة + حذف */
@Composable
private fun MaterialsSection(items: List<String>, onRemove: (String) -> Unit, onWithdraw: (String) -> Unit) {
    val unique = remember(items) { items.distinctBy { it.lowercase() } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("المواد (${unique.size})") }
        if (unique.isEmpty()) {
            item { EmptyState(Icons.Default.Inventory2, "لا توجد مواد", "أضف المواد من «احتياج موقع» ثم وافق عليها في «الاحتياجات»") }
        }
        items(unique, key = { it.lowercase() }) { name ->
            GalaxyCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    OutlinedButton(onClick = { onWithdraw(name) }) { Text("سحب للصيانة") }
                    IconButton(onClick = { onRemove(name) }) { Icon(Icons.Default.Delete, "حذف") }
                }
            }
        }
    }
}

/**
 * قسم «احتياج موقع»: اختيار احتياج الموقع من المواد المحفوظة، وظهور
 * خيار «إضافة مادة» عند عدم وجود المادة في المحفوظ (أو من الزر دائمًا).
 */
@Composable
private fun DemandSection(
    catalog: List<Material>,
    pendingCount: Int,
    onSubmit: (List<String>) -> Unit,
    onAdd: (String) -> Unit,
    onOpenRequests: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var addDialog by remember { mutableStateOf(false) }
    val cleanQuery = query.trim()
    val filtered = remember(catalog, cleanQuery) {
        if (cleanQuery.isEmpty()) catalog else catalog.filter { it.name.contains(cleanQuery, ignoreCase = true) }
    }
    val exists = catalog.any { it.name.equals(cleanQuery, ignoreCase = true) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("احتياج الموقع") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ابحث في المواد المحفوظة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // «إضافة مادة» تظهر عند غياب المادة عن القائمة المحفوظة
        if (cleanQuery.isNotEmpty() && !exists) {
            item {
                GalaxyCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("«$cleanQuery» غير موجودة في المواد المحفوظة")
                        Button(onClick = { onAdd(cleanQuery); query = "" }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة مادة ورفع الطلب")
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSubmit(selected); selected = emptyList() },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("رفع الاحتياج (${selected.size})") }
                OutlinedButton(onClick = { addDialog = true }) { Text("إضافة مادة") }
            }
        }
        item { SectionTitle("المواد المحفوظة") }
        if (filtered.isEmpty()) {
            item { EmptyState(Icons.Default.AddShoppingCart, "لا نتائج", "اكتب اسم المادة ثم أضفها من الزر أعلاه") }
        }
        items(filtered, key = { it.id }) { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = m.name in selected,
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + m.name else selected - m.name
                    }
                )
                Text(m.name)
            }
        }
        item {
            TextButton(onClick = onOpenRequests) {
                Icon(Icons.AutoMirrored.Filled.ListAlt, null); Spacer(Modifier.width(6.dp))
                Text("عرض الاحتياجات المرفوعة ($pendingCount بانتظار الموافقة)")
            }
        }
    }
    if (addDialog) {
        TextInputDialog("إضافة مادة ورفع طلب", "اسم المادة", { onAdd(it) }, { addDialog = false })
    }
}

/** قسم «الاحتياجات»: طلبات الاحتياج ودورة الموافقة/الرفض/الحذف/الاسترجاع */
@Composable
private fun RequestsSection(
    requests: List<MaterialRequest>,
    onApprove: (MaterialRequest) -> Unit,
    onReject: (MaterialRequest) -> Unit,
    onRestore: (MaterialRequest) -> Unit,
    onDelete: (MaterialRequest) -> Unit,
    onOpenDemand: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("الاحتياجات (${requests.size})") }
        if (requests.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Filled.ListAlt, "لا توجد طلبات", "ارفع احتياج الموقع من «احتياج موقع»") }
        }
        items(requests, key = { it.id }) { r ->
            RequestCard(r, onApprove, onReject, onRestore, onDelete)
        }
        item {
            TextButton(onClick = onOpenDemand) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("احتياج موقع جديد")
            }
        }
    }
}

@Composable
private fun RequestCard(
    r: MaterialRequest,
    approve: (MaterialRequest) -> Unit,
    reject: (MaterialRequest) -> Unit,
    restore: (MaterialRequest) -> Unit,
    delete: (MaterialRequest) -> Unit
) {
    GalaxyCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.materialName, Modifier.weight(1f))
                Text(r.status.label, color = MaterialTheme.colorScheme.primary)
            }
            r.resolvedDate?.let { Text("آخر بت: ${it.formatDateTime()}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when (r.status) {
                    RequestStatus.PENDING -> {
                        Button(onClick = { approve(r) }) { Text("موافقة") }
                        OutlinedButton(onClick = { reject(r) }) { Text("رفض") }
                    }
                    RequestStatus.REJECTED -> {
                        Button(onClick = { restore(r) }) { Text("استرجاع") }
                    }
                    RequestStatus.APPROVED -> {
                        Text("أضيفت إلى قسم المواد", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = { delete(r) }) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("حذف")
                }
            }
        }
    }
}

/** قسم «المسحوبات»: المواد المسحوبة للصيانة + قرار الإصلاح + الإرجاع */
@Composable
private fun WithdrawalsSection(
    items: List<Withdrawal>,
    onFixed: (Withdrawal) -> Unit,
    onNotFixed: (Withdrawal) -> Unit,
    onReturn: (Withdrawal) -> Unit,
    onDelete: (Withdrawal) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("المسحوبات للصيانة (${items.size})") }
        if (items.isEmpty()) {
            item { EmptyState(Icons.Default.Build, "لا توجد مواد مسحوبة", "اسحب مادة من قسم «المواد» بسبب إلزامي") }
        }
        items(items, key = { it.id }) { w ->
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.itemName, Modifier.weight(1f))
                        Text(w.status.label, color = MaterialTheme.colorScheme.primary)
                    }
                    if (w.withdrawReason.isNotBlank()) Text("سبب السحب: ${w.withdrawReason}")
                    if (w.fixedNote.isNotBlank()) Text("كيف أُصلحت: ${w.fixedNote}")
                    if (w.notFixedReason.isNotBlank()) Text("سبب عدم الإصلاح: ${w.notFixedReason}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (w.status) {
                            WithdrawalStatus.WITHDRAWN, WithdrawalStatus.IN_MAINTENANCE -> {
                                Button(onClick = { onFixed(w) }) { Text("تم الإصلاح") }
                                OutlinedButton(onClick = { onNotFixed(w) }) { Text("لم يتم الإصلاح") }
                            }
                            WithdrawalStatus.FIXED, WithdrawalStatus.NOT_FIXED -> {
                                val label = if (w.status == WithdrawalStatus.FIXED) "إرجاع للموقع" else "إرجاع المادة"
                                Button(onClick = { onReturn(w) }) { Text(label) }
                            }
                            WithdrawalStatus.RETURNED -> {
                                Text("أُعيدت للموقع", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        IconButton(onClick = { onDelete(w) }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
        }
    }
}


/** حوار سحب مادة للصيانة: سبب إلزامي + ملاحظات اختيارية */
@Composable
private fun WithdrawDialog(name: String, onSave: (String, String, () -> Unit) -> Unit, onCancel: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("سحب «$name» للصيانة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب السحب للصيانة (إجباري)") })
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات (اختياري)") })
            }
        },
        confirmButton = { Button(onClick = { onSave(reason, notes, onCancel) }, enabled = reason.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

/** حوار قرار الإصلاح: كيف أُصلحت المشكلة (إلزامي) أو سبب عدم الإصلاح (إلزامي) */
@Composable
private fun DecisionDialog(title: String, label: String, onSave: (String, () -> Unit) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text("$label (إجباري)") }) },
        confirmButton = { Button(onClick = { onSave(text, onCancel) }, enabled = text.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

/** حوار إدخال نصي بسيط (إضافة مادة مباشرة من واجهة احتياج الموقع) */
@Composable
private fun TextInputDialog(title: String, label: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }) },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onSave(text); onCancel() }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

