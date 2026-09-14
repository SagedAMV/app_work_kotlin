package com.majarra.galaxy.ui.screens.sites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.Equipment
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.EquipmentCategory
import com.majarra.galaxy.domain.model.EquipmentStatus
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.SiteHistoryRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.domain.usecase.DeleteSiteResult
import com.majarra.galaxy.domain.usecase.DeleteSiteUseCase
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.theme.GalaxyColors
import com.majarra.galaxy.ui.components.StatusChip
import com.majarra.galaxy.ui.components.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class DetailsTab(val label: String) {
    INFO("بيانات"),
    EQUIPMENT("المعدات"),
    ATTACHMENTS("المرفقات"),
    LINKS("الروابط"),
    HISTORY("السجل"),
    TICKETS("الأعطال")
}

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    siteRepo: com.majarra.galaxy.domain.repository.SiteRepository,
    equipmentRepo: EquipmentRepository,
    attachmentRepo: AttachmentRepository,
    historyRepo: SiteHistoryRepository,
    linkRepo: LinkRepository,
    ticketRepo: TicketRepository,
    private val auditRepo: AuditRepository,
    private val equipmentRepository: EquipmentRepository,
    private val attachmentRepository: AttachmentRepository,
    private val historyRepository: SiteHistoryRepository,
    private val deleteSite: DeleteSiteUseCase
) : ViewModel() {

    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L

    val site = siteRepo.observeSite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val equipment = equipmentRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attachments = attachmentRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = historyRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val links = linkRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tickets = ticketRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEquipment(category: EquipmentCategory, company: String, model: String, serial: String) {
        viewModelScope.launch {
            val id = equipmentRepository.insert(
                Equipment(siteId = siteId, category = category, company = company, model = model, serialNumber = serial)
            )
            historyRepository.record(siteId, "إضافة معدة", "", "${category.label} $model")
            auditRepo.log("CREATE", "Equipment", id, "${category.label} — $model")
        }
    }

    fun deleteEquipment(e: Equipment) {
        viewModelScope.launch {
            equipmentRepository.delete(e)
            historyRepository.record(siteId, "حذف معدة", "${e.category.label} ${e.model}", "")
            auditRepo.log("DELETE", "Equipment", e.id, e.model)
        }
    }

    fun addAttachment(uri: android.net.Uri, type: AttachmentType, caption: String) {
        viewModelScope.launch {
            val id = attachmentRepository.insert(
                Attachment(siteId = siteId, type = type, uri = uri.toString(), caption = caption)
            )
            historyRepository.record(siteId, "إضافة مرفق", "", caption.ifBlank { type.label })
            auditRepo.log("CREATE", "Attachment", id, caption)
        }
    }

    fun deleteAttachment(a: Attachment) {
        viewModelScope.launch {
            attachmentRepository.delete(a)
            auditRepo.log("DELETE", "Attachment", a.id, a.caption)
        }
    }

    /**
     * حذف الموقع — التأكيد المزدوج في الواجهة، وقاعدة المنع في حالة الاستخدام.
     * النتيجة: رسالة خطأ إن كان محظورًا، أو null عند نجاح الحذف.
     */
    fun deleteSite(onDone: (String?) -> Unit) {
        val current = site.value ?: return
        viewModelScope.launch {
            when (val result = deleteSite(current)) {
                is DeleteSiteResult.Blocked -> onDone(
                    "لا يمكن الحذف: الموقع يحتوي ${result.equipmentCount} معدة و${result.activeLinks} رابط نشط"
                )
                DeleteSiteResult.Deleted -> onDone(null)
            }
        }
    }
}

/** تفاصيل موقع: بيانات + معدات + مرفقات + سجل + روابط + أعطال */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailsScreen(
    siteId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: SiteDetailsViewModel = hiltViewModel()
) {
    val site by viewModel.site.collectAsState()
    val equipment by viewModel.equipment.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    val history by viewModel.history.collectAsState()
    val links by viewModel.links.collectAsState()
    val tickets by viewModel.tickets.collectAsState()

    var tab by remember { mutableStateOf(DetailsTab.INFO) }
    var showAddEquipment by remember { mutableStateOf(false) }
    var equipmentToDelete by remember { mutableStateOf<Equipment?>(null) }
    var confirmDeleteSiteStep by remember { mutableStateOf(0) } // 0=لا، 1=تحذير، 2=تأكيد نهائي
    var deleteBlockMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // منتقيا المرفقات: صورة أو PDF — أوفلاين بالكامل
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.addAttachment(uri, AttachmentType.IMAGE, "صورة")
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.addAttachment(uri, AttachmentType.PDF, "مخطط")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(site?.name ?: "تفاصيل الموقع") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(siteId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل")
                    }
                    IconButton(onClick = { confirmDeleteSiteStep = 1 }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailsTab.values().forEach { t ->
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val s = site ?: return@LazyColumn
                when (tab) {
                    DetailsTab.INFO -> {
                        item {
                            GalaxyCard {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(s.code, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        StatusChip(s.status.label, GalaxyColors.siteStatusColor(s.status))
                                    }
                                    Text("الإحداثيات: ${"%.5f".format(s.latitude)} , ${"%.5f".format(s.longitude)}")
                                    Text("أُنشئ: ${s.createdAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                                    Text("آخر تحديث: ${s.updatedAt.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                                    if (s.notes.isNotBlank()) Text("ملاحظات: ${s.notes}")
                                }
                            }
                        }
                    }

                    DetailsTab.EQUIPMENT -> {
                        item {
                            Button(onClick = { showAddEquipment = true }) {
                                Text("إضافة معدة")
                            }
                        }
                        items(equipment, key = { it.id }) { e ->
                            GalaxyCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${e.category.label} — ${e.model}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${e.company}  •  ${e.serialNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (e.lifespanMonths > 0) {
                                            Text(
                                                "العمر الافتراضي: ${e.lifespanMonths} شهرًا",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                        StatusChip(e.status.label, GalaxyColors.equipmentStatusColor(e.status))
                                        IconButton(onClick = { equipmentToDelete = e }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "حذف المعدة",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DetailsTab.ATTACHMENTS -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { imagePicker.launch("image/*") }) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                                    Text("صورة", modifier = Modifier.padding(start = 6.dp))
                                }
                                Button(onClick = { pdfPicker.launch("application/pdf") }) {
                                    Icon(Icons.Filled.Description, contentDescription = null)
                                    Text("PDF", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        items(attachments, key = { it.id }) { a ->
                            GalaxyCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (a.type == AttachmentType.IMAGE) {
                                        AsyncImage(
                                            model = a.uri,
                                            contentDescription = a.caption,
                                            modifier = Modifier.height(64.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(Icons.Filled.Description, contentDescription = null)
                                    }
                                    Text(a.type.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { viewModel.deleteAttachment(a) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    DetailsTab.LINKS -> {
                        if (links.isEmpty()) {
                            item { Text("لا روابط لهذا الموقع — أضفها من شاشة المجرة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(links, key = { it.id }) { l ->
                            GalaxyCard {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${l.type.label} • ${l.networkClass.label}", style = MaterialTheme.typography.titleSmall)
                                        StatusChip(l.status.label, GalaxyColors.linkStatusColor(l.status))
                                    }
                                    Text(
                                        "التردد: ${l.frequencyMHz} م.هـ • المسافة: ${"%.2f".format(l.distanceKm)} كم",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    DetailsTab.HISTORY -> {
                        if (history.isEmpty()) {
                            item { Text("لا سجل بعد", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(history, key = { it.id }) { h ->
                            GalaxyCard {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(h.action, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            h.timestamp.formatDateTime(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (h.oldValue.isNotBlank() || h.newValue.isNotBlank()) {
                                        Text("${h.oldValue} ← ${h.newValue}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    DetailsTab.TICKETS -> {
                        if (tickets.isEmpty()) {
                            item { Text("لا أعطال مسجلة لهذا الموقع", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(tickets, key = { it.id }) { t ->
                            GalaxyCard {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                                        StatusChip(t.severity.label, GalaxyColors.severityColor(t.severity))
                                    }
                                    Text(t.status.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // حوار إضافة معدة
    if (showAddEquipment) {
        AddEquipmentDialog(
            onDismiss = { showAddEquipment = false },
            onConfirm = { category, company, model, serial ->
                viewModel.addEquipment(category, company, model, serial)
                showAddEquipment = false
            }
        )
    }

    // حذف معدة — تأكيد مزدوج بسيط
    equipmentToDelete?.let { e ->
        ConfirmDialog(
            title = "حذف معدة",
            text = "هل تريد حذف ${e.category.label} ${e.model}؟ سيُسجل الإجراء في سجل التدقيق.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.deleteEquipment(e)
                equipmentToDelete = null
            },
            onDismiss = { equipmentToDelete = null }
        )
    }

    // حذف الموقع — تأكيد مزدوج + قاعدة منع الحذف إن وُجدت معدات/روابط نشطة
    if (confirmDeleteSiteStep == 1) {
        ConfirmDialog(
            title = "حذف الموقع",
            text = "سيتم حذف الموقع نهائيًا مع كل بياناته. لا يمكن التراجع. هل أنت متأكد؟",
            confirmText = "متابعة",
            onConfirm = { confirmDeleteSiteStep = 2 },
            onDismiss = { confirmDeleteSiteStep = 0 }
        )
    } else if (confirmDeleteSiteStep == 2) {
        ConfirmDialog(
            title = "التأكيد الأخير",
            text = "اضغط «حذف نهائي» لتأكيد العملية. إن كان الموقع يحتوي معدات أو روابط نشطة فسيُرفض الحذف.",
            confirmText = "حذف نهائي",
            onConfirm = {
                confirmDeleteSiteStep = 0
                viewModel.deleteSite { error ->
                    if (error == null) onBack() else deleteBlockMessage = error
                }
            },
            onDismiss = { confirmDeleteSiteStep = 0 }
        )
    }

    deleteBlockMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteBlockMessage = null },
            title = { Text("الحذف محظور") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { deleteBlockMessage = null }) { Text("حسنًا") }
            }
        )
    }
}

/** حوار إضافة معدة جديدة */
@Composable
private fun AddEquipmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (EquipmentCategory, String, String, String) -> Unit
) {
    var category by remember { mutableStateOf(EquipmentCategory.AERIAL) }
    var company by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة معدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EquipmentCategory.values().forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label) }
                        )
                    }
                }
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("الشركة") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("الطراز") }, singleLine = true)
                OutlinedTextField(value = serial, onValueChange = { serial = it }, label = { Text("الرقم التسلسلي") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(category, company, model, serial) }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
