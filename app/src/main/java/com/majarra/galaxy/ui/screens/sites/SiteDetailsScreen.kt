package com.majarra.galaxy.ui.screens.sites

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.ItemType
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import com.majarra.galaxy.domain.usecase.ArchiveSiteUseCase
import com.majarra.galaxy.domain.usecase.DeleteMaintenanceLogUseCase
import com.majarra.galaxy.domain.usecase.DeleteSiteUseCase
import com.majarra.galaxy.domain.usecase.DeleteWithdrawalUseCase
import com.majarra.galaxy.domain.usecase.NEXT_DUE_SUGGESTION_DAYS
import com.majarra.galaxy.domain.usecase.ObserveSiteUseCase
import com.majarra.galaxy.domain.usecase.ReturnWithdrawnItemUseCase
import com.majarra.galaxy.domain.usecase.SaveMaintenanceLogUseCase
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import com.majarra.galaxy.domain.usecase.SetNextMaintenanceUseCase
import com.majarra.galaxy.domain.usecase.StartWithdrawalMaintenanceUseCase
import com.majarra.galaxy.domain.usecase.WithdrawItemUseCase
import com.majarra.galaxy.ui.components.ColorDot
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.FullscreenImageViewer
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDate
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.util.MaterialItem
import com.majarra.galaxy.util.MaterialLines
import com.majarra.galaxy.util.daysFromNow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** تبويبات شاشة التفاصيل — النسخة 2.2: أُضيف تبويب المسحوبات */
private enum class DetailsTab(val label: String) {
    INFO("بيانات"),
    MATERIALS("المواد"),
    WITHDRAWALS("المسحوبات"),
    MAINTENANCE("الصيانة"),
    ATTACHMENTS("المرفقات")
}

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSite: ObserveSiteUseCase,
    detailRepo: SiteDetailRepository,
    logRepo: MaintenanceLogRepository,
    categoryRepo: CategoryRepository,
    materialRepo: MaterialRepository,
    withdrawalRepo: WithdrawalRepository,
    private val siteRepo: SiteRepository,
    private val detailRepository: SiteDetailRepository,
    private val attachmentRepo: AttachmentRepository,
    private val saveSite: SaveSiteUseCase,
    private val deleteSite: DeleteSiteUseCase,
    private val archiveSite: ArchiveSiteUseCase,
    private val saveLog: SaveMaintenanceLogUseCase,
    private val deleteLog: DeleteMaintenanceLogUseCase,
    private val setNextMaintenance: SetNextMaintenanceUseCase,
    private val withdrawItem: WithdrawItemUseCase,
    private val startWithdrawalMaintenance: StartWithdrawalMaintenanceUseCase,
    private val returnWithdrawnItem: ReturnWithdrawnItemUseCase,
    private val deleteWithdrawal: DeleteWithdrawalUseCase,
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
    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** كتالوج المواد الموحد — تختار منه قوائم تبويب المواد */
    val materialsCatalog = materialRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** سجل سحب المواد وصيانتها وإرجاعها لهذا الموقع */
    val withdrawals = withdrawalRepo.observeBySite(siteId)
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

    /** أرشفة الموقع أو استعادته (إجابة الاسئله.md) */
    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        val current = site.value ?: return
        viewModelScope.launch {
            archiveSite(current, archived)
            _message.value = if (archived) "تم نقل الموقع إلى الأرشيف" else "تمت استعادة الموقع"
            onDone()
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

    /**
     * سحب مادة جديدة من الموقع — يُسجَّل السحب ويتزامن تلقائيًا مع
     * قائمة «مواد تم سحبها» داخل حالة الاستخدام.
     */
    fun withdrawNewItem(
        name: String,
        type: ItemType,
        dateMillis: Long,
        notes: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // حالة الاستخدام تحدّث آخر تعديل للموقع بنفسها (لا تكرار هنا)
                withdrawItem(
                    Withdrawal(
                        siteId = siteId,
                        itemName = name,
                        itemType = type,
                        withdrawnDate = dateMillis,
                        notes = notes
                    )
                )
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** نقل المادة المسحوبة إلى حالة «قيد الصيانة» */
    fun startItemMaintenance(w: Withdrawal) {
        viewModelScope.launch { startWithdrawalMaintenance(w) }
    }

    /** إرجاع المادة إلى الموقع وختم الدورة */
    fun returnItem(w: Withdrawal) {
        viewModelScope.launch {
            returnWithdrawnItem(w)
            _message.value = "تم إرجاع «${w.itemName}» إلى الموقع"
        }
    }

    fun removeWithdrawal(w: Withdrawal) {
        viewModelScope.launch { deleteWithdrawal(w) }
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
                Attachment(siteId = siteId, filePath = uri.toString(), fileType = type)
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val materialsCatalog by viewModel.materialsCatalog.collectAsStateWithLifecycle()
    val withdrawals by viewModel.withdrawals.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(DetailsTab.INFO) }
    var showEditInfo by remember { mutableStateOf(false) }
    var confirmDeleteSite by remember { mutableStateOf(false) }
    // مرفق معروض ملء الشاشة / مرفق بانتظار تأكيد الحذف (مشترك بين
    // الشبكة والعارض حتى يعمل الحذف من داخل العارض أيضًا)
    var viewerAttachment by remember { mutableStateOf<Attachment?>(null) }
    var attachmentToDelete by remember { mutableStateOf<Attachment?>(null) }
    // سجل سحب بانتظار تأكيد الحذف
    var withdrawalToDelete by remember { mutableStateOf<Withdrawal?>(null) }

    val scope = rememberCoroutineScope()
    /** رسائل أخطاء الأفعال المحلية (فتح ملف بلا تطبيق مناسب…) */
    val reportLocal: (String) -> Unit = { text ->
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

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
                    // أرشفة/استعادة (إجابة الاسئله.md)
                    val currentSite = site
                    if (currentSite != null) {
                        IconButton(onClick = {
                            if (currentSite.archived) {
                                viewModel.setArchived(false) { }
                            } else {
                                viewModel.setArchived(true, onDone = onBack)
                            }
                        }) {
                            Icon(
                                if (currentSite.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = if (currentSite.archived) "استعادة من الأرشيف" else "أرشفة الموقع"
                            )
                        }
                    }
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
                DetailsTab.entries.forEach { t ->
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
                // انتقالات ناعمة بين التبويبات (إجابة الاسئله.md)
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(240)) { it / 4 } + fadeIn(tween(240)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(240)) { -it / 4 } + fadeOut(tween(240)))
                    },
                    label = "details-tabs"
                ) { target ->
                    when (target) {
                        DetailsTab.INFO -> InfoTab(s, categories)
                        DetailsTab.MATERIALS -> MaterialsTab(
                            detail = detail,
                            catalog = materialsCatalog,
                            onSave = viewModel::saveMaterials
                        )
                        DetailsTab.WITHDRAWALS -> WithdrawalsTab(
                            withdrawals = withdrawals,
                            onWithdraw = viewModel::withdrawNewItem,
                            onStartMaintenance = viewModel::startItemMaintenance,
                            onReturn = viewModel::returnItem,
                            onDelete = { withdrawalToDelete = it }
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
                            onOpenImage = { viewerAttachment = it },
                            onDelete = { attachmentToDelete = it },
                            reportError = reportLocal
                        )
                    }
                }
            }
        }
    }

    // عارض الصور ملء الشاشة (إجابة الاسئله.md: تكبير/تصغير بالسحب)
    viewerAttachment?.let { attachment ->
        FullscreenImageViewer(
            uri = attachment.filePath,
            onDismiss = { viewerAttachment = null },
            onDelete = {
                viewerAttachment = null
                attachmentToDelete = attachment
            }
        )
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

    // تأكيد حذف مرفق (من الشبكة أو من داخل العارض)
    attachmentToDelete?.let { attachment ->
        ConfirmDialog(
            title = "حذف المرفق",
            text = "سيُحذف هذا المرفق نهائيًا من الموقع.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.deleteAttachment(attachment)
                attachmentToDelete = null
            },
            onDismiss = { attachmentToDelete = null }
        )
    }

    // تأكيد حذف سجل سحب — قائمة «مواد تم سحبها» لا تتأثر (تبقى للتوثيق)
    withdrawalToDelete?.let { w ->
        ConfirmDialog(
            title = "حذف سجل السحب",
            text = "سيُحذف سجل سحب «${w.itemName}» نهائيًا من سجل المسحوبات.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.removeWithdrawal(w)
                withdrawalToDelete = null
            },
            onDismiss = { withdrawalToDelete = null }
        )
    }
}

/* ═══════════════════ تبويب البيانات ═══════════════════ */

@Composable
private fun InfoTab(s: Site, categories: List<Category>) {
    val category = categories.firstOrNull { it.id == s.categoryId }
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
                    // التصنيف والحالة: مؤرشف/نشط + اسم التصنيف بلونه
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (s.archived) "مؤرشف" else "نشط",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (s.archived) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        if (category != null) {
                            ColorDot(category.colorHex)
                            Text(category.name, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text("بلا تصنيف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
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

/* ═══════════════ تبويب المواد (اختيار من الكتالوج الموحد) ═══════════════ */

/**
 * المواد في النسخة 2.2: لا كتابة نصية في كل موقع — العناصر تُختار
 * من كتالوج المواد الموحد عبر واجهة اختيار (بحث + علامات ✔)،
 * فيلغى الإدخال المكرر وتصبح المواد موحدة ومرتبة وسهلة الاختيار.
 *
 * التخزين ما زال نصيًا في نفس الأعمدة (تنسيق «[ ] / [x]» في
 * MaterialLines) — البيانات القديمة النصية تبقى محفوظة ومقروءة،
 * والعناصر غير الموجودة في الكتالوج تُعرض كما هي وتُحذف فرديًا.
 */
@Composable
private fun MaterialsTab(
    detail: SiteDetail?,
    catalog: List<Material>,
    onSave: (available: String, needed: String, maintenance: String, withdrawn: String) -> Unit
) {
    // كل حقل قائمة عناصر تُحرَّر محليًا وتُحفظ دفعة واحدة
    var available by remember(detail?.id) { mutableStateOf(MaterialLines.parse(detail?.availableMaterials.orEmpty())) }
    var needed by remember(detail?.id) { mutableStateOf(MaterialLines.parse(detail?.neededMaterials.orEmpty())) }
    var maintenance by remember(detail?.id) { mutableStateOf(MaterialLines.parse(detail?.maintenanceMaterials.orEmpty())) }
    var withdrawn by remember(detail?.id) { mutableStateOf(MaterialLines.parse(detail?.withdrawnMaterials.orEmpty())) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("المواد الموجودة حاليًا")
            MaterialSelectionList(items = available, catalog = catalog) { available = it }
            SectionTitle("احتياج الموقع (ما ينقص)")
            MaterialSelectionList(items = needed, catalog = catalog) { needed = it }
            SectionTitle("مواد تحتاج صيانة")
            MaterialSelectionList(items = maintenance, catalog = catalog) { maintenance = it }
            SectionTitle("مواد تم سحبها")
            MaterialSelectionList(items = withdrawn, catalog = catalog) { withdrawn = it }
        }
        Button(
            onClick = {
                onSave(
                    MaterialLines.serialize(available),
                    MaterialLines.serialize(needed),
                    MaterialLines.serialize(maintenance),
                    MaterialLines.serialize(withdrawn)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ بيانات المواد")
        }
    }
}

/**
 * قائمة مواد واحدة: تحديد/إلغاء + حذف فردي + اختيار عناصر جديدة من
 * الكتالوج الموحد بدل الكتابة الحرة (تعديل النسخة 2.2).
 */
@Composable
private fun MaterialSelectionList(
    items: List<MaterialItem>,
    catalog: List<Material>,
    onChange: (List<MaterialItem>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    GalaxyCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (items.isEmpty()) {
                Text(
                    "لا عناصر بعد — اختر من المواد الموحدة بالزر أدناه",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { checked ->
                            onChange(items.toMutableList().also { it[index] = item.copy(checked = checked) })
                        }
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.checked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        onChange(items.toMutableList().also { it.removeAt(index) })
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف العنصر",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            TextButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("إضافة من المواد الموحدة", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (showPicker) {
        MaterialPickerDialog(
            catalog = catalog,
            currentItems = items,
            onDismiss = { showPicker = false },
            onApply = { newItems ->
                onChange(newItems)
                showPicker = false
            }
        )
    }
}

/**
 * واجهة الاختيار من الكتالوج الموحد: مواد الكتالوج تُعرض بعلامات ✔،
 * والتعليم هنا يعني وجود المادة في قائمة الموقع. إلغاء تعليم مادة
 * موجودة يزيلها من القائمة. العناصر القديمة غير الموجودة في الكتالوج
 * لا تتأثر (تُحفظ كما هي)، وحالات ✔ السابقة للمواد المختارة تبقى.
 */
@Composable
private fun MaterialPickerDialog(
    catalog: List<Material>,
    currentItems: List<MaterialItem>,
    onDismiss: () -> Unit,
    onApply: (List<MaterialItem>) -> Unit
) {
    val catalogNames = remember(catalog) { catalog.map { it.name } }
    // التحديد الابتدائي: أسماء الكتالوج الموجودة حاليًا في القائمة
    var selected by remember(catalogNames) {
        mutableStateOf(
            currentItems.map { it.text }
                .filter { text -> catalogNames.any { it.equals(text, ignoreCase = true) } }
                .toSet()
        )
    }
    var query by remember { mutableStateOf("") }
    val visible = catalog.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار من المواد الموحدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث عن مادة…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
                when {
                    catalog.isEmpty() -> Text(
                        "الكتالوج الموحد فارغ — افتح شاشة «المواد الموحدة» من الشاشة الرئيسية وأضف موادك مرة واحدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    visible.isEmpty() -> Text(
                        "لا مواد مطابقة للبحث",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    else -> LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visible, key = { it.id }) { material ->
                            val isSelected = selected.contains(material.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isSelected) {
                                            selected - material.name
                                        } else {
                                            selected + material.name
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) {
                                            selected + material.name
                                        } else {
                                            selected - material.name
                                        }
                                    }
                                )
                                Text(material.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // العناصر القديمة (نص حر خارج الكتالوج) تبقى كما هي
                    val legacy = currentItems.filterNot { item ->
                        catalogNames.any { it.equals(item.text, ignoreCase = true) }
                    }
                    // المواد المختارة: إن كانت موجودة سابقًا نحتفظ بحالتها
                    // (✔ أو بدونها) بدل إعادة إنشائها من الصفر
                    val picked = catalog.filter { selected.contains(it.name) }.map { material ->
                        currentItems.firstOrNull { it.text.equals(material.name, ignoreCase = true) }
                            ?: MaterialItem(material.name, checked = false)
                    }
                    onApply(picked + legacy)
                },
                enabled = catalog.isNotEmpty()
            ) { Text("تطبيق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/* ═══════════ تبويب المسحوبات (سحب ← صيانة ← إرجاع) ═══════════ */

/**
 * الإضافة الجديدة في النسخة 2.2: المواد التي يسحبها المستخدم من
 * الموقع (جهاز/مايك/لوح شمسي/بطارية/جهاز يدوي/أي شيء)، يصونها،
 * ثم يرجعها للموقع. كل سجل يحمل نوعه وتاريخه وحالته، والأفعال
 * بضغطات: «بدء الصيانة» ثم «إرجاع للموقع». السحب يتزامن تلقائيًا
 * مع قائمة «مواد تم سحبها» في تبويب المواد.
 */
@Composable
private fun WithdrawalsTab(
    withdrawals: List<Withdrawal>,
    onWithdraw: (
        name: String,
        type: ItemType,
        dateMillis: Long,
        notes: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit,
    onStartMaintenance: (Withdrawal) -> Unit,
    onReturn: (Withdrawal) -> Unit,
    onDelete: (Withdrawal) -> Unit
) {
    var showWithdrawDialog by remember { mutableStateOf(false) }
    val openCount = withdrawals.count { it.status != WithdrawalStatus.RETURNED }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { showWithdrawDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("سحب مادة جديدة", modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (openCount > 0) {
            item {
                GalaxyCard {
                    Text(
                        "مسحوبة الآن ولم تُرجع: $openCount",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (withdrawals.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.SwapVert,
                    title = "لا سجلات سحب",
                    subtitle = "عندما تسحب مادة من الموقع لصيانتها سجّلها هنا لتتبع دورتها حتى الإرجاع"
                )
            }
        } else {
            items(withdrawals, key = { it.id }) { w ->
                WithdrawalRow(
                    w = w,
                    onStartMaintenance = onStartMaintenance,
                    onReturn = onReturn,
                    onDelete = onDelete
                )
            }
        }
    }

    if (showWithdrawDialog) {
        WithdrawDialog(
            onDismiss = { showWithdrawDialog = false },
            onWithdraw = onWithdraw
        )
    }
}

/** صف سجل سحب واحد: الاسم والنوع والحالة والتواريخ + أفعال التقدم */
@Composable
private fun WithdrawalRow(
    w: Withdrawal,
    onStartMaintenance: (Withdrawal) -> Unit,
    onReturn: (Withdrawal) -> Unit,
    onDelete: (Withdrawal) -> Unit
) {
    val statusColor = when (w.status) {
        WithdrawalStatus.WITHDRAWN -> MaterialTheme.colorScheme.tertiary
        WithdrawalStatus.IN_MAINTENANCE -> MaterialTheme.colorScheme.secondary
        WithdrawalStatus.RETURNED -> MaterialTheme.colorScheme.outline
    }

    GalaxyCard {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    w.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(w.status.label, style = MaterialTheme.typography.labelSmall, color = statusColor)
                IconButton(onClick = { onDelete(w) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف السجل",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                "${w.itemType.label} — سُحبت: ${w.withdrawnDate.formatDate()}" +
                    (w.returnedDate?.let { " · أُرجعت: ${it.formatDate()}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (w.notes.isNotBlank()) {
                Text(w.notes, style = MaterialTheme.typography.bodySmall)
            }
            if (w.status != WithdrawalStatus.RETURNED) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w.status == WithdrawalStatus.WITHDRAWN) {
                        OutlinedButton(onClick = { onStartMaintenance(w) }) {
                            Text("بدء الصيانة")
                        }
                    }
                    Button(onClick = { onReturn(w) }) {
                        Text("إرجاع للموقع")
                    }
                }
            }
        }
    }
}

/**
 * حوار سحب مادة جديدة: الاسم إلزامي + النوع من قائمة الأنواع الشائعة
 * + تاريخ السحب (افتراضيًا اليوم) + ملاحظات اختيارية. أخطاء التحقق
 * تظهر داخل الحوار بدل إغلاقه.
 */
@Composable
private fun WithdrawDialog(
    onDismiss: () -> Unit,
    onWithdraw: (
        name: String,
        type: ItemType,
        dateMillis: Long,
        notes: String,
        onSaved: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ItemType.DEVICE) }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سحب مادة من الموقع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("اسم المادة") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                OutlinedButton(
                    onClick = { showTypeMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("النوع: ${type.label}")
                }
                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = { showTypeMenu = false }
                ) {
                    ItemType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.label) },
                            onClick = {
                                type = t
                                showTypeMenu = false
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تاريخ السحب: ${dateMillis.formatDate()}")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // عند نجاح الحفظ يُغلق الحوار عبر onSaved،
                    // وعند خطأ التحقق يبقى مفتوحًا لعرض الرسالة.
                    onWithdraw(name, type, dateMillis, notes, onDismiss) { message ->
                        error = message
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("سحب") }
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
    // اقتراح الموعد القادم بعد تسجيل صيانة (إجابة الاسئله.md)
    var suggestedDue by remember { mutableStateOf<Long?>(null) }

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
            },
            onSavedExtra = { dateMillis ->
                // بعد نجاح الحفظ: اقتراح الموعد القادم تلقائيًا
                suggestedDue = dateMillis + NEXT_DUE_SUGGESTION_DAYS * 24L * 3600 * 1000
            }
        )
    }

    // حوار اقتراح الموعد القادم: اعتماد / تعديل يدوي / تجاهل
    suggestedDue?.let { suggested ->
        AlertDialog(
            onDismissRequest = { suggestedDue = null },
            title = { Text("اقتراح موعد الصيانة القادم") },
            text = {
                Text(
                    "سُجلت الصيانة بنجاح. هل تريد تحديد الموعد القادم مقترحًا بعد ٩٠ يومًا؟\n" +
                        "الموعد المقترح: ${suggested.formatDate()}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    onSetDue(suggested)
                    suggestedDue = null
                }) { Text("اعتماد المقترح") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        suggestedDue = null
                        showDuePicker = true
                    }) { Text("تعديل يدوي") }
                    TextButton(onClick = { suggestedDue = null }) { Text("تجاهل") }
                }
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
    ) -> Unit,
    /** يُستدعى بعد نجاح الحفظ لتمرير تاريخ الصيانة لاقتراح الموعد القادم */
    onSavedExtra: (Long) -> Unit = {}
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
                    // عند نجاح الحفظ يُغلق الحوار عبر onSaved ويُمرَّر التاريخ
                    // لاقتراح الموعد القادم، وعند خطأ التحقق يبقى مفتوحًا.
                    val selectedDate = dateMillis
                    onSave(selectedDate, notes, performedBy, {
                        onSavedExtra(selectedDate)
                        onDismiss()
                    }) { message ->
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

/**
 * فتح ملف (عادة PDF) في عارض خارجي مع منح إذن القراءة المؤقت.
 * @return هل وُجد تطبيق مناسب وفتح فعلًا.
 */
private fun Context.openFileExternally(uriString: String): Boolean = runCatching {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
    true
}.getOrDefault(false)

/** مشاركة ملف مع التطبيقات الأخرى (إجابة الاسئله.md) */
private fun Context.shareFile(uriString: String): Boolean = runCatching {
    val uri = Uri.parse(uriString)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, "مشاركة الملف"))
    true
}.getOrDefault(false)

@Composable
private fun AttachmentsTab(
    attachments: List<Attachment>,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onOpenImage: (Attachment) -> Unit,
    onDelete: (Attachment) -> Unit,
    reportError: (String) -> Unit
) {
    val context = LocalContext.current
    val images = attachments.filter { it.fileType == AttachmentType.IMAGE }
    val files = attachments.filter { it.fileType != AttachmentType.IMAGE }

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
                // شبكة صور بارتفاع محسوب؛ نقرة = عارض ملء الشاشة،
                // ضغطة طويلة = حذف.
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
                        AsyncImage(
                            model = image.filePath,
                            contentDescription = "صورة مرفقة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                                .pointerInput(image.id) {
                                    detectTapGestures(
                                        onTap = { onOpenImage(image) },
                                        onLongPress = { onDelete(image) }
                                    )
                                }
                        )
                    }
                }
            }
            item {
                Text(
                    "نقرة لعرض الصورة ملء الشاشة — ضغطة طويلة للحذف",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (files.isNotEmpty()) {
            item { SectionTitle("الملفات (${files.size})") }
            items(files, key = { it.id }) { file ->
                GalaxyCard(onClick = {
                    // فتح الملف في عارض خارجي (إجابة الاسئله.md)
                    if (!context.openFileExternally(file.filePath)) {
                        reportError("لا يوجد تطبيق مناسب لفتح هذا الملف")
                    }
                }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text(file.fileType.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                file.uploadedDate.formatDateTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // مشاركة مع التطبيقات الأخرى (إجابة الاسئله.md)
                        IconButton(onClick = {
                            if (!context.shareFile(file.filePath)) {
                                reportError("تعذّرت مشاركة الملف")
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "مشاركة الملف")
                        }
                        IconButton(onClick = { onDelete(file) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف المرفق")
                        }
                    }
                }
            }
        }
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
