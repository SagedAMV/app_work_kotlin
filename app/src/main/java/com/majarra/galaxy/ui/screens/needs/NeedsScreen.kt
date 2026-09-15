package com.majarra.galaxy.ui.screens.needs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.BoqDocument
import com.majarra.galaxy.data.local.BoqLine
import com.majarra.galaxy.data.local.InventoryItem
import com.majarra.galaxy.data.local.Requirement
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.model.InventoryUnit
import com.majarra.galaxy.domain.model.RequirementStatus
import com.majarra.galaxy.domain.model.RequirementType
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.BoqRepository
import com.majarra.galaxy.domain.repository.InventoryRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.AssessRequirementUseCase
import com.majarra.galaxy.domain.usecase.CancelRequirementUseCase
import com.majarra.galaxy.domain.usecase.CreateRequirementUseCase
import com.majarra.galaxy.domain.usecase.DraftItem
import com.majarra.galaxy.domain.usecase.FulfillRequirementUseCase
import com.majarra.galaxy.domain.usecase.FulfillResult
import com.majarra.galaxy.domain.usecase.DeleteRequirementUseCase
import com.majarra.galaxy.domain.usecase.RequirementDraft
import com.majarra.galaxy.domain.usecase.StockAssessment
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.StatusChip
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.SkyBlue
import com.majarra.galaxy.ui.theme.WarnAmber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class NeedsTab(val label: String) {
    REQUIREMENTS("الاحتياج"),
    INVENTORY("المخزون"),
    BOQ("جداول الكميات")
}

@HiltViewModel
class NeedsViewModel @Inject constructor(
    siteRepo: SiteRepository,
    private val requirementRepository: RequirementRepository,
    private val inventoryRepository: InventoryRepository,
    private val boqRepository: BoqRepository,
    private val createRequirement: CreateRequirementUseCase,
    private val fulfillRequirement: FulfillRequirementUseCase,
    private val assessRequirement: AssessRequirementUseCase,
    private val cancelRequirementUseCase: CancelRequirementUseCase,
    private val deleteRequirementUseCase: DeleteRequirementUseCase,
    private val auditRepo: AuditRepository
) : ViewModel() {

    val sites: StateFlow<List<Site>> = siteRepo.observeSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val requirements: StateFlow<List<Requirement>> = requirementRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val inventory: StateFlow<List<InventoryItem>> = inventoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val boqs: StateFlow<List<BoqDocument>> = boqRepository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedReqId = MutableStateFlow<Long?>(null)
    val expandedReqId: StateFlow<Long?> = _expandedReqId
    private val _assessment = MutableStateFlow<List<StockAssessment>>(emptyList())
    val assessment: StateFlow<List<StockAssessment>> = _assessment

    fun toggleRequirement(id: Long) {
        viewModelScope.launch {
            if (_expandedReqId.value == id) {
                _expandedReqId.value = null
                _assessment.value = emptyList()
            } else {
                _expandedReqId.value = id
                _assessment.value = assessRequirement(id)
            }
        }
    }

    fun approve(id: Long) {
        viewModelScope.launch {
            val r = requirementRepository.get(id) ?: return@launch
            if (r.status != RequirementStatus.PENDING) return@launch
            requirementRepository.update(r.copy(status = RequirementStatus.APPROVED))
            auditRepo.log("UPDATE", "Requirement", id, "اعتماد الاحتياج")
        }
    }

    /** إلغاء احتياج (مسودة/قيد الاعتماد/معتمد) */
    fun cancelRequirement(id: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val ok = cancelRequirementUseCase(id)
            onResult(if (ok) "تم إلغاء الاحتياج" else "لا يمكن إلغاء احتياج مصروف")
        }
    }

    /** حذف احتياج غير مصروف (بنوده ووثيقة BOQ تُحذف بالتسلسل) */
    fun deleteRequirement(id: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val ok = deleteRequirementUseCase(id)
            onResult(if (ok) "تم حذف الاحتياج" else "لا يمكن حذف احتياج مصروف")
        }
    }

    /** الصرف — يخصم المخزون ويسجل في Audit تلقائيًا داخل حالة الاستخدام */
    fun fulfill(id: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = fulfillRequirement(id)) {
                FulfillResult.Success -> {
                    _expandedReqId.value = null
                    _assessment.value = emptyList()
                    onResult("تم الصرف وخصم المخزون وتسجيل العملية")
                }
                is FulfillResult.Failure -> onResult(result.reason)
                FulfillResult.NotFound -> onResult("الاحتياج غير موجود")
            }
        }
    }

    fun create(draft: RequirementDraft, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                createRequirement(draft)
                onResult(null)
            } catch (e: IllegalArgumentException) {
                onResult(e.message ?: "بيانات غير صالحة")
            }
        }
    }

    fun addInventory(
        name: String,
        unit: InventoryUnit,
        quantity: Int,
        threshold: Int,
        location: String,
        onResult: (String?) -> Unit
    ) {
        val cleanName = name.trim().replace(Regex("\\s+"), " ")
        if (cleanName.isEmpty()) {
            onResult("اسم الصنف مطلوب")
            return
        }
        if (cleanName.length > MAX_ITEM_NAME) {
            onResult("اسم الصنف طويل جدًا (الحد $MAX_ITEM_NAME حرفًا)")
            return
        }
        if (quantity !in 0..MAX_QUANTITY || threshold !in 0..MAX_QUANTITY) {
            onResult("الكمية والحد الأدنى يجب أن يكونا بين 0 و $MAX_QUANTITY")
            return
        }
        viewModelScope.launch {
            val id = inventoryRepository.insert(
                InventoryItem(
                    name = cleanName,
                    unit = unit,
                    quantity = quantity,
                    minThreshold = threshold,
                    location = location.trim().take(60)
                )
            )
            auditRepo.log("CREATE", "InventoryItem", id, cleanName)
            onResult(null)
        }
    }

    /**
     * تعديل كمية صنف (استلام أو صرف يدوي) — يستخدم مسار التحديث في المستودع
     * الذي كان غير مستخدم إطلاقًا، ويُسجَّل في سجل التدقيق.
     */
    fun adjustStock(item: InventoryItem, delta: Int) {
        viewModelScope.launch {
            val newQty = (item.quantity + delta).coerceIn(0, MAX_QUANTITY)
            if (newQty == item.quantity) return@launch
            inventoryRepository.update(item.copy(quantity = newQty))
            auditRepo.log("UPDATE", "InventoryItem", item.id, "${item.name}: ${item.quantity} ← $newQty")
        }
    }

    /** حذف صنف من المخزون مع تسجيل العملية */
    fun deleteInventory(item: InventoryItem) {
        viewModelScope.launch {
            inventoryRepository.delete(item)
            auditRepo.log("DELETE", "InventoryItem", item.id, item.name)
        }
    }

    private val _expandedBoqId = MutableStateFlow<Long?>(null)
    val expandedBoqId: StateFlow<Long?> = _expandedBoqId
    private val _boqLines = MutableStateFlow<List<BoqLine>>(emptyList())
    val boqLines: StateFlow<List<BoqLine>> = _boqLines

    fun toggleBoq(boqId: Long) {
        viewModelScope.launch {
            if (_expandedBoqId.value == boqId) {
                _expandedBoqId.value = null
                _boqLines.value = emptyList()
            } else {
                _expandedBoqId.value = boqId
                _boqLines.value = boqRepository.observeLines(boqId).first()
            }
        }
    }

    fun siteName(id: Long): String = sites.value.find { it.id == id }?.name ?: "موقع $id"

    private companion object {
        const val MAX_ITEM_NAME = 80
        const val MAX_QUANTITY = 1_000_000
    }
}

/** شاشة الاحتياج والمخزون وجداول الكميات */
@Composable
fun NeedsScreen(viewModel: NeedsViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf(NeedsTab.REQUIREMENTS) }
    var showCreate by remember { mutableStateOf(false) }
    var showAddInventory by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (tab == NeedsTab.REQUIREMENTS) showCreate = true
                    else if (tab == NeedsTab.INVENTORY) showAddInventory = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "الاحتياج والمخزون",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeedsTab.values().forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                }
            }

            when (tab) {
                NeedsTab.REQUIREMENTS -> RequirementsTab(viewModel, snackbar)
                NeedsTab.INVENTORY -> InventoryTab(viewModel, snackbar)
                NeedsTab.BOQ -> BoqTab(viewModel)
            }
        }
    }

    if (showCreate) {
        CreateRequirementDialog(
            viewModel = viewModel,
            onDismiss = { showCreate = false }
        ) { showCreate = false }
    }
    if (showAddInventory) {
        AddInventoryDialog(
            viewModel = viewModel,
            snackbar = snackbar,
            onDismiss = { showAddInventory = false }
        )
    }
}

/** تبويب الاحتياجات: توسيع لعرض المقارنة مع المخزون، اعتماد، صرف */
@Composable
private fun RequirementsTab(
    viewModel: NeedsViewModel,
    snackbar: SnackbarHostState
) {
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedReqId.collectAsStateWithLifecycle()
    val assessment by viewModel.assessment.collectAsStateWithLifecycle()

    if (requirements.isEmpty()) {
        EmptyState(Icons.Filled.Add, "لا توجد احتياجات", "أنشئ احتياجًا جديدًا من الزر العائم")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(requirements, key = { it.id }) { req ->
            GalaxyCard(onClick = { viewModel.toggleRequirement(req.id) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(viewModel.siteName(req.siteId), style = MaterialTheme.typography.titleMedium)
                        StatusChip(req.status.label, statusColor(req.status))
                    }
                    Text(
                        "${req.type.label} • ${req.createdAt.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (req.notes.isNotBlank()) {
                        Text(req.notes, style = MaterialTheme.typography.bodySmall)
                    }

                    // التوسيع: المقارنة التلقائية مع المخزون
                    if (expandedId == req.id) {
                        assessment.forEach { a ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${a.item.description} × ${a.item.quantity}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (a.item.inventoryItemId == null) {
                                    Text("بند حر", color = SkyBlue, style = MaterialTheme.typography.labelSmall)
                                } else if (a.shortage > 0) {
                                    Text("ناقص ${a.shortage}", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Text("متوفر (${a.available})", color = NeonGreen, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (req.status == RequirementStatus.PENDING) {
                                Button(onClick = { viewModel.approve(req.id) }) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                    Text("اعتماد", modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                            if (req.status == RequirementStatus.APPROVED) {
                                Button(onClick = {
                                    viewModel.fulfill(req.id) { msg ->
                                        viewModel.viewModelScope.launch { snackbar.showSnackbar(msg) }
                                    }
                                }) {
                                    Text("صرف من المخزون")
                                }
                                // إلغاء/حذف: متاحان فقط للاحتياجات غير المصروفة
                                if (req.status != RequirementStatus.FULFILLED) {
                                    OutlinedButton(onClick = {
                                        viewModel.cancelRequirement(req.id) { msg ->
                                            viewModel.viewModelScope.launch { snackbar.showSnackbar(msg) }
                                        }
                                    }) {
                                        Text("إلغاء")
                                    }
                                    TextButton(onClick = {
                                        viewModel.deleteRequirement(req.id) { msg ->
                                            viewModel.viewModelScope.launch { snackbar.showSnackbar(msg) }
                                        }
                                    }) {
                                        Text("حذف", color = DangerRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusColor(status: RequirementStatus) = when (status) {
    RequirementStatus.DRAFT -> WarnAmber
    RequirementStatus.PENDING -> SkyBlue
    RequirementStatus.APPROVED -> NeonGreen
    RequirementStatus.FULFILLED -> NeonGreen
    RequirementStatus.CANCELLED -> DangerRed
}

/** تبويب المخزون: تمييز الأصناف تحت الحد الأدنى */
@Composable
private fun InventoryTab(
    viewModel: NeedsViewModel,
    snackbar: SnackbarHostState
) {
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()

    if (inventory.isEmpty()) {
        EmptyState(Icons.Filled.Warning, "المخزون فارغ", "أضف أصنافًا من الزر العائم")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(inventory, key = { it.id }) { item ->
            val low = item.quantity <= item.minThreshold
            var confirmDeleteItem by remember { mutableStateOf(false) }
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${item.location} • الحد الأدنى: ${item.minThreshold}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column {
                            Text(
                                "${item.quantity} ${item.unit.label}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (low) DangerRed else NeonGreen
                            )
                            if (low) {
                                Text("تحت الحد!", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.adjustStock(item, -1) }) { Text("−1") }
                        OutlinedButton(onClick = { viewModel.adjustStock(item, 1) }) { Text("+1") }
                        OutlinedButton(onClick = { viewModel.adjustStock(item, 10) }) { Text("+10") }
                        TextButton(onClick = { confirmDeleteItem = true }) {
                            Text("حذف", color = DangerRed)
                        }
                    }
                }
            }
            if (confirmDeleteItem) {
                AlertDialog(
                    onDismissRequest = { confirmDeleteItem = false },
                    title = { Text("حذف صنف المخزون") },
                    text = { Text("سيتم حذف «${item.name}» من المخزون. هل أنت متأكد؟") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDeleteItem = false
                            viewModel.deleteInventory(item)
                        }) { Text("حذف", color = DangerRed) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteItem = false }) { Text("إلغاء") }
                    }
                )
            }
        }
    }
}

/** تبويب جداول الكميات المولدة تلقائيًا */
@Composable
private fun BoqTab(viewModel: NeedsViewModel) {
    val boqs by viewModel.boqs.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedBoqId.collectAsStateWithLifecycle()
    val lines by viewModel.boqLines.collectAsStateWithLifecycle()

    if (boqs.isEmpty()) {
        EmptyState(Icons.Filled.CheckCircle, "لا توجد جداول كميات", "تُولَّد تلقائيًا عند إنشاء احتياج")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(boqs, key = { it.id }) { doc ->
            GalaxyCard(onClick = { viewModel.toggleBoq(doc.id) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(doc.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أُنشئ: ${doc.createdAt.formatDateTime()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expandedId == doc.id) {
                        lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(line.description, style = MaterialTheme.typography.bodySmall)
                                Text("${line.quantity} ${line.unit}", style = MaterialTheme.typography.bodySmall, color = SkyBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** حوار إنشاء احتياج: موقع + نوع + بنود مرتبطة بالمخزون أو حرة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRequirementDialog(
    viewModel: NeedsViewModel,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()

    var selectedSite by remember { mutableStateOf<Site?>(null) }
    var type by remember { mutableStateOf(RequirementType.MATERIALS) }
    var notes by remember { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<DraftItem>>(emptyList()) }

    // حقول البند الحالي
    var pickedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var freeDescription by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء احتياج") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // اختيار الموقع
                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                    OutlinedTextField(
                        value = selectedSite?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الموقع") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        sites.forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${s.name} (${s.code})") },
                                onClick = {
                                    selectedSite = s
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RequirementType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("إضافة بند:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = pickedItem == null,
                        onClick = { pickedItem = null },
                        label = { Text("بند حر") }
                    )
                    inventory.forEach { inv ->
                        FilterChip(
                            selected = pickedItem?.id == inv.id,
                            onClick = { pickedItem = inv },
                            label = { Text("${inv.name} (${inv.quantity})") }
                        )
                    }
                }
                if (pickedItem == null) {
                    OutlinedTextField(
                        value = freeDescription,
                        onValueChange = { freeDescription = it },
                        label = { Text("وصف البند الحر") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("الكمية") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val qty = qtyText.toIntOrNull()
                        val description = pickedItem?.name ?: freeDescription.trim()
                        when {
                            description.isBlank() -> error = "اختر صنفًا أو اكتب وصفًا"
                            qty == null || qty <= 0 -> error = "كمية غير صالحة"
                            else -> {
                                drafts = drafts + DraftItem(
                                    inventoryItemId = pickedItem?.id,
                                    description = description,
                                    quantity = qty
                                )
                                freeDescription = ""
                                pickedItem = null
                                qtyText = "1"
                                error = null
                            }
                        }
                    }) { Text("إضافة البند") }
                }

                drafts.forEach { d ->
                    Text("• ${d.description} × ${d.quantity}", style = MaterialTheme.typography.bodySmall)
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val site = selectedSite
                when {
                    site == null -> error = "اختر موقعًا"
                    drafts.isEmpty() -> error = "أضف بندًا واحدًا على الأقل"
                    else -> {
                        viewModel.create(RequirementDraft(site.id, type, notes, drafts)) { msg ->
                            if (msg == null) {
                                onCreated()
                            } else {
                                error = msg
                            }
                        }
                    }
                }
            }) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** حوار إضافة صنف مخزون */
@Composable
private fun AddInventoryDialog(
    viewModel: NeedsViewModel,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(InventoryUnit.PIECE) }
    var qtyText by remember { mutableStateOf("0") }
    var thresholdText by remember { mutableStateOf("1") }
    var location by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صنف جديد في المخزون") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الصنف") }, singleLine = true)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InventoryUnit.values().forEach { u ->
                        FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("الكمية") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("الحد الأدنى") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("الموقع في المستودع") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = qtyText.toIntOrNull()
                val th = thresholdText.toIntOrNull()
                when {
                    qty == null || th == null -> error = "أرقام غير صالحة"
                    else -> viewModel.addInventory(name, unit, qty, th, location) { msg ->
                        if (msg == null) onDismiss() else error = msg
                    }
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
