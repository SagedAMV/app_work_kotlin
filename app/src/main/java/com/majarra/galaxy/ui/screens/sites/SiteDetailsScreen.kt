package com.majarra.galaxy.ui.screens.sites

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.DeleteMaintenanceLogUseCase
import com.majarra.galaxy.domain.usecase.DeleteSiteUseCase
import com.majarra.galaxy.domain.usecase.ObserveSiteUseCase
import com.majarra.galaxy.domain.usecase.SaveMaintenanceLogUseCase
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import com.majarra.galaxy.domain.usecase.SetNextMaintenanceUseCase
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDate
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.util.daysFromNow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** تبويبات شاشة التفاصيل — حسب تعليمات التبسيط */
private enum class DetailsTab(val label: String) {
    INFO("بيانات"),
    MATERIALS("المواد"),
    MAINTENANCE("الصيانة"),
    ATTACHMENTS("المرفقات")
}

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSite: ObserveSiteUseCase,
    detailRepo: SiteDetailRepository,
    logRepo: MaintenanceLogRepository,
    private val siteRepo: SiteRepository,
    private val detailRepository: SiteDetailRepository,
    private val attachmentRepo: AttachmentRepository,
    private val saveSite: SaveSiteUseCase,
    private val deleteSite: DeleteSiteUseCase,
    private val saveLog: SaveMaintenanceLogUseCase,
    private val deleteLog: DeleteMaintenanceLogUseCase,
    private val setNextMaintenance: SetNextMaintenanceUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: 0L

    val site = observeSite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val detail = detailRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val logs = logRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attachments = attachmentRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** رسالة قصيرة للواجهة */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() {
        _message.value = null
    }

    /** تعديل اسم الموقع وملاحظاته العامة */
    fun updateSiteInfo(name: String, notes: String, onSaved: () -> Unit, onError: (String) -> Unit) {
        val current = site.value ?: return
        viewModelScope.launch {
            try {
                saveSite(current.copy(name = name, notes = notes))
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** حفظ الحقول الاختيارية الأربعة (المواد) في صف تفاصيل الموقع */
    fun saveMaterials(available: String, needed: String, maintenance: String, withdrawn: String) {
        viewModelScope.launch {
            val existing = detailRepository.getBySite(siteId)
            detailRepository.upsert(
                existing?.copy(
                    availableMaterials = available.trim(),
                    neededMaterials = needed.trim(),
                    maintenanceMaterials = maintenance.trim(),
                    withdrawnMaterials = withdrawn.trim()
                ) ?: SiteDetail(
                    siteId = siteId,
                    availableMaterials = available.trim(),
                    neededMaterials = needed.trim(),
                    maintenanceMaterials = maintenance.trim(),
                    withdrawnMaterials = withdrawn.trim()
                )
            )
            touchSite()
            _message.value = "تم حفظ بيانات المواد"
        }
    }

    /** تحديد/مسح موعد الصيانة القادمة */
    fun setNextMaintenanceDue(due: Long?) {
        viewModelScope.launch {
            setNextMaintenance(siteId, due)
            _message.value = if (due == null) "تم مسح موعد الصيانة القادم" else "تم تحديد موعد الصيانة القادم"
        }
    }

    /** إضافة إدخال لسجل الصيانة */
    fun addLog(
        dateMillis: Long,
        notes: String,
        performedBy: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                saveLog(
                    MaintenanceLog(
                        siteId = siteId,
                        maintenanceDate = dateMillis,
                        notes = notes,
                        performedBy = performedBy
                    )
                )
                touchSite()
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    fun removeLog(log: MaintenanceLog) {
        viewModelScope.launch { deleteLog(log) }
    }

    /** إضافة مرفق: تثبيت إذن القراءة الدائم ثم حفظ المسار */
    fun addAttachment(uri: Uri, type: AttachmentType) {
        viewModelScope.launch {
            val persisted = try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (e: SecurityException) {
                // بعض مزودي الملفات لا يمنحون إذنًا دائمًا — المرفق يعمل للجلسة الحالية
                false
            }
            attachmentRepo.insert(
                Attachment(siteId = siteId, filePath = uri.toString(), fileType = type.name)
            )
            touchSite()
            _message.value = if (persisted) {
                "تمت إضافة المرفق"
            } else {
                "تمت إضافة المرفق، لكن النظام لم يمنح إذنًا دائمًا — قد لا يظهر بعد إعادة التشغيل"
            }
        }
    }

    fun deleteAttachment(a: Attachment) {
        viewModelScope.launch {
            attachmentRepo.delete(a)
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(a.filePath), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // الفشل هنا غير ضار: يعني فقط أن الإذن لم يكن مثبّتًا أصلًا
        }
    }

    /** حذف الموقع — التأكيد في الواجهة، والحذف المتسلسل يمسح البقية */
    fun deleteSite(onDone: () -> Unit) {
        val current = site.value ?: return
        viewModelScope.launch {
            deleteSite(current)
            onDone()
        }
    }

    /** تحديث lastModified عند أي تغيير ذي معنى في بيانات الموقع */
    private suspend fun touchSite() {
        siteRepo.getSite(siteId)?.let {
            siteRepo.update(it.copy(lastModified = System.currentTimeMillis()))
        }
    }
}

/**
 * تفاصيل موقع: بيانات + مواد + سجل صيانة + مرفقات.
 * لا يُمرَّر معرف الموقع كمعامل: الـ ViewModel يقرأه من SavedStateHandle
 * مباشرة (مرتبط بمدخل التنقل الحالي عبر hiltViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailsScreen(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    viewModel: SiteDetailsViewModel = hiltViewModel()
) {
    val site by viewModel.site.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(DetailsTab.INFO) }
    var showEditInfo by remember { mutableStateOf(false) }
    var confirmDeleteSite by remember { mutableStateOf(false) }

    // رسائل الحالة عبر الشريط السفلي العام
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // منتقيا المرفقات: صورة أو PDF — أوفلاين بالكامل.
    // نستخدم OpenDocument (لا GetContent) لأنه يمنح إذنًا قابلًا للتثبيت.
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addAttachment(uri, AttachmentType.IMAGE)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addAttachment(uri, AttachmentType.PDF)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(site?.name ?: "تفاصيل الموقع") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditInfo = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "تعديل")
                    }
                    IconButton(onClick = { confirmDeleteSite = true }) {
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

            val s = site
            if (s == null) {
                EmptyState(
                    icon = Icons.Filled.Delete,
                    title = "الموقع غير موجود",
                    subtitle = "ربما حُذف للتو"
                )
            } else {
                when (tab) {
                    DetailsTab.INFO -> InfoTab(s)
                    DetailsTab.MATERIALS -> MaterialsTab(
                        detail = detail,
                        onSave = viewModel::saveMaterials
                    )
                    DetailsTab.MAINTENANCE -> MaintenanceTab(
                        detail = detail,
                        logs = logs,
                        onSetDue = viewModel::setNextMaintenanceDue,
                        onAddLog = viewModel::addLog,
                        onDeleteLog = viewModel::removeLog
                    )

                    DetailsTab.ATTACHMENTS -> AttachmentsTab(
                        attachments = attachments,
                        onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                        onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onDelete = viewModel::deleteAttachment
                    )
                }
            }
        }
    }

    // حوار تعديل الاسم والملاحظات
    val currentSite = site
    if (showEditInfo && currentSite != null) {
        EditSiteInfoDialog(
            initialName = currentSite.name,
            initialNotes = currentSite.notes,
            onDismiss = { showEditInfo = false },
            onSave = { name, notes, reportError ->
                viewModel.updateSiteInfo(
                    name = name,
                    notes = notes,
                    onSaved = { showEditInfo = false },
                    onError = reportError
                )
            }
        )
    }

    // تأكيد حذف الموقع — الحذف المتسلسل يمسح التفاصيل والمرفقات والسجل
    if (confirmDeleteSite) {
        ConfirmDialog(
            title = "حذف الموقع",
            text = "سيُحذف الموقع «${site?.name.orEmpty()}» مع كل تفاصيله ومرفقاته وسجل صيانته نهائيًا.",
            confirmText = "حذف",
            onConfirm = {
                confirmDeleteSite = false
                viewModel.deleteSite(onDone = onBack)
            },
            onDismiss = { confirmDeleteSite = false }
        )
    }
}

/* ═══════════════════ تبويب البيانات ═══════════════════ */

@Composable
private fun InfoTab(s: Site) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("أُنشئ: ${s.createdDate.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                    Text("آخر تحديث: ${s.lastModified.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionTitle("ملاحظات عامة")
            GalaxyCard {
                Text(
                    s.notes.ifBlank { "لا توجد ملاحظات — استخدم زر التعديل في الأعلى" },
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.notes.isBlank()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

/* ═══════════════════ تبويب المواد ═══════════════════ */

@Composable
private fun MaterialsTab(
    detail: SiteDetail?,
    onSave: (available: String, needed: String, maintenance: String, withdrawn: String) -> Unit
) {
    // الحقول تبدأ من القيم المحفوظة وتبقى قابلة للتحرير محليًا.
    // المفتاح على معرف صف التفاصيل يجعلها تتحدث عند تحميل قيم موقع آخر.
    var available by remember(detail?.id) { mutableStateOf(detail?.availableMaterials.orEmpty()) }
    var needed by remember(detail?.id) { mutableStateOf(detail?.neededMaterials.orEmpty()) }
    var maintenance by remember(detail?.id) { mutableStateOf(detail?.maintenanceMaterials.orEmpty()) }
    var withdrawn by remember(detail?.id) { mutableStateOf(detail?.withdrawnMaterials.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MaterialsField("المواد الموجودة حاليًا", available) { available = it }
            MaterialsField("احتياج الموقع (ما ينقص)", needed) { needed = it }
            MaterialsField("مواد تحتاج صيانة", maintenance) { maintenance = it }
            MaterialsField("مواد تم سحبها", withdrawn) { withdrawn = it }
        }
        Button(
            onClick = { onSave(available, needed, maintenance, withdrawn) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ بيانات المواد")
        }
    }
}

@Composable
private fun MaterialsField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 3,
        maxLines = 8,
        placeholder = { Text("اكتب سطرًا لكل مادة…") }
    )
}

/* ═══════════════════ تبويب الصيانة ═══════════════════ */

@Composable
private fun MaintenanceTab(
    detail: SiteDetail?,
    logs: List<MaintenanceLog>,
    onSetDue: (Long?) -> Unit,
    onAddLog: (
        dateMillis: Long,
        notes: String,
        performedBy: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onDeleteLog: (MaintenanceLog) -> Unit
) {
    var showAddLog by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<MaintenanceLog?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionTitle("موعد الصيانة القادم")
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val due = detail?.nextMaintenanceDue
                    if (due == null) {
                        Text("لا يوجد موعد محدد", color = MaterialTheme.colorScheme.outline)
                    } else {
                        val days = due.daysFromNow()
                        Text("الموعد: ${due.formatDate()}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                days < 0 -> "متأخر ${-days} يوم"
                                days == 0L -> "اليوم"
                                else -> "بعد $days يوم"
                            },
                            color = if (days <= 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showDuePicker = true }) { Text("تحديد الموعد") }
                        if (due != null) {
                            OutlinedButton(onClick = { onSetDue(null) }) { Text("مسح الموعد") }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("سجل الصيانة")
                OutlinedButton(onClick = { showAddLog = true }) {
                    Icon(Icons.Filled.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("إضافة", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Build,
                    title = "لا يوجد سجل صيانة",
                    subtitle = "أضف إدخالًا جديدًا لتوثيق أعمال الصيانة"
                )
            }
        } else {
            items(logs, key = { it.id }) { log ->
                GalaxyCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(log.maintenanceDate.formatDate(), style = MaterialTheme.typography.titleSmall)
                            Text(log.notes, style = MaterialTheme.typography.bodyMedium)
                            if (log.performedBy.isNotBlank()) {
                                Text(
                                    "نفّذها: ${log.performedBy}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { logToDelete = log }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف الإدخال")
                        }
                    }
                }
            }
        }
    }

    if (showDuePicker) {
        DueDatePicker(
            initial = detail?.nextMaintenanceDue,
            onDismiss = { showDuePicker = false },
            onConfirm = { millis ->
                onSetDue(millis)
                showDuePicker = false
            }
        )
    }

    if (showAddLog) {
        AddLogDialog(
            onDismiss = { showAddLog = false },
            onSave = { dateMillis, notes, performedBy, onSaved, reportError ->
                onAddLog(dateMillis, notes, performedBy, onSaved, reportError)
            }
        )
    }

    logToDelete?.let { log ->
        ConfirmDialog(
            title = "حذف إدخال الصيانة",
            text = "سيُحذف هذا الإدخال من سجل الصيانة نهائيًا.",
            confirmText = "حذف",
            onConfirm = {
                onDeleteLog(log)
                logToDelete = null
            },
            onDismiss = { logToDelete = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onConfirm) },
                enabled = state.selectedDateMillis != null
            ) { Text("تحديد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLogDialog(
    onDismiss: () -> Unit,
    onSave: (
        dateMillis: Long,
        notes: String,
        performedBy: String,
        onSaved: () -> Unit,
        reportError: (String) -> Unit
    ) -> Unit
) {
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var performedBy by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة سجل صيانة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("التاريخ: ${dateMillis.formatDate()}")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        error = null
                    },
                    label = { Text("ملاحظات الصيانة") },
                    minLines = 2,
                    maxLines = 5,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                OutlinedTextField(
                    value = performedBy,
                    onValueChange = { performedBy = it },
                    label = { Text("نفّذها (اختياري)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // عند نجاح الحفظ يُغلق الحوار عبر onSaved، وعند خطأ التحقق
                    // يبقى مفتوحًا وتظهر الرسالة تحت حقل الملاحظات.
                    onSave(dateMillis, notes, performedBy, onDismiss) { message ->
                        error = message
                    }
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("تحديد") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }
        ) {
            DatePicker(state = state)
        }
    }
}

/* ═══════════════════ تبويب المرفقات ═══════════════════ */

@Composable
private fun AttachmentsTab(
    attachments: List<Attachment>,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onDelete: (Attachment) -> Unit
) {
    val images = attachments.filter { it.fileType == AttachmentType.IMAGE.name }
    val files = attachments.filter { it.fileType != AttachmentType.IMAGE.name }
    var toDelete by remember { mutableStateOf<Attachment?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickImage, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("إضافة صورة", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onPickPdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("إضافة ملف", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        if (attachments.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.AddAPhoto,
                    title = "لا توجد مرفقات",
                    subtitle = "أضف صورًا أو ملفات لهذا الموقع"
                )
            }
        }

        if (images.isNotEmpty()) {
            item { SectionTitle("معرض الصور (${images.size})") }
            item {
                // شبكة صور بارتفاع محسوب — عرض بسيط بلا شاشة تكبير منفصلة
                val rows = (images.size + 2) / 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((rows * 110).dp.coerceAtMost(440.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        GalaxyCard(onClick = { toDelete = image }) {
                            AsyncImage(
                                model = image.filePath,
                                contentDescription = "صورة مرفقة",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                            )
                        }
                    }
                }
            }
        }

        if (files.isNotEmpty()) {
            item { SectionTitle("الملفات (${files.size})") }
            items(files, key = { it.id }) { file ->
                GalaxyCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("مستند PDF", style = MaterialTheme.typography.titleSmall)
                            Text(
                                file.uploadedDate.formatDateTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { toDelete = file }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف المرفق")
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { attachment ->
        ConfirmDialog(
            title = "حذف المرفق",
            text = "سيُحذف هذا المرفق نهائيًا من الموقع.",
            confirmText = "حذف",
            onConfirm = {
                onDelete(attachment)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/* ═══════════════════ حوار تعديل الاسم والملاحظات ═══════════════════ */

@Composable
private fun EditSiteInfoDialog(
    initialName: String,
    initialNotes: String,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, reportError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var notes by remember { mutableStateOf(initialNotes) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الموقع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("اسم الموقع") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    minLines = 2,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // عند النجاح يُغلق الحوار عبر مسار onSaved في الشاشة،
                    // وعند خطأ التحقق يبقى مفتوحًا لعرض الرسالة.
                    onSave(name, notes) { message -> error = message }
                },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
