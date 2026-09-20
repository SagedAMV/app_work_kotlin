package com.majarra.galaxy.ui.screens.sites

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.majarra.galaxy.data.local.Attachment
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.EmergencyVisit
import com.majarra.galaxy.data.local.MaintenanceLog
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.SiteDetail
import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.model.ItemType
import com.majarra.galaxy.domain.model.VisitOutcome
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
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
import com.majarra.galaxy.ui.anim.DrawnCheck
import com.majarra.galaxy.ui.anim.DueCountdownRing
import com.majarra.galaxy.ui.anim.GalaxyShimmerBox
import com.majarra.galaxy.ui.anim.GalaxySnackbarHost
import com.majarra.galaxy.ui.anim.GalaxyStarBurst
import com.majarra.galaxy.ui.anim.GlowButton
import com.majarra.galaxy.ui.anim.MorphingActionButton
import com.majarra.galaxy.ui.anim.StaggeredItem
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * تبويبات شاشة التفاصيل — النسخة 2.2: أُضيف تبويب المسحوبات.
 * النسخة 2.8 (اختيار 21 من جلسة المواد والسحوبات والطوارئ): أُضيف
 * تبويب الطوارئ لعرض سجل النزولات الذي كان يُحفظ بلا أي شاشة قراءة.
 */
private enum class DetailsTab(val label: String) {
    INFO("بيانات"),
    MATERIALS("المواد"),
    WITHDRAWALS("المسحوبات"),
    EMERGENCY("الطوارئ"),
    MAINTENANCE("الصيانة"),
    ATTACHMENTS("المرفقات")
}

/**
 * حفظ التبويب المختار عبر إعادة إنشاء النشاط (إصلاح UX): كان التبويب
 * يُحفظ بـ `remember` وحده فيعود المستخدم إلى «بيانات» بعد أي استدارة
 * أو رجوع من الخلفية — أي فقدان موضعه في شاشة طويلة بستة تبويبات.
 */
private val DetailsTabSaver: Saver<DetailsTab, String> = Saver(
    save = { it.name },
    restore = { name -> DetailsTab.entries.firstOrNull { it.name == name } ?: DetailsTab.INFO }
)

/** أرقام عربية-هندية لعرض قيم رقمية داخل نصوص عربية */
private fun Int.arabicDigits(): String =
    toString().map { if (it.isDigit()) '\u0660' + (it - '0') else it }.joinToString("")

@HiltViewModel
class SiteDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSite: ObserveSiteUseCase,
    private val detailRepo: SiteDetailRepository,
    logRepo: MaintenanceLogRepository,
    categoryRepo: CategoryRepository,
    materialRepo: MaterialRepository,
    withdrawalRepo: WithdrawalRepository,
    emergencyVisitRepo: EmergencyVisitRepository,
    private val siteRepo: SiteRepository,
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

    /** سجل النزول الطارئ/الاستكشاف لهذا الموقع (اختيار 21) — الأحدث أولًا */
    val emergencyVisits = emergencyVisitRepo.observeBySite(siteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * عناصر «المواد الموجودة حاليًا» كما تُعرض فعليًا (اختيار 12 — العرض
     * المحسوب): القائمة المخزنة ناقص المواد المسحوبة الآن (غير المُرجعة).
     * البيانات المخزنة لا تتغير إطلاقًا — الحساب عرض فقط: المادة المسحوبة
     * تختفي من قسم الموجود، وعند الإرجاع تعود للظهور تلقائيًا.
     */
    val displayAvailableItems: StateFlow<List<MaterialItem>> =
        combine(detail, withdrawals) { detail, withdrawals ->
            val openNames = withdrawals
                .filter { it.status != WithdrawalStatus.RETURNED }
                .map { it.itemName.lowercase() }
                .toSet()
            MaterialLines.parse(detail?.availableMaterials.orEmpty())
                .filterNot { it.text.lowercase() in openNames }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val existing = detailRepo.getBySite(siteId)
            detailRepo.upsert(
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SiteDetailsScreen(
    onBack: () -> Unit,
    onOpenEmergency: (Long) -> Unit,
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
    val emergencyVisits by viewModel.emergencyVisits.collectAsStateWithLifecycle()
    val displayAvailableItems by viewModel.displayAvailableItems.collectAsStateWithLifecycle()

    var tab by rememberSaveable(stateSaver = DetailsTabSaver) { mutableStateOf(DetailsTab.INFO) }
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

    // رسائل الحالة عبر الشريط السفلي العام + انفجار نجوم عند نجاح
    // إرجاع مادة مسحوبة (اختيار 44 من الجولة الثالثة)
    val message by viewModel.message.collectAsStateWithLifecycle()
    var successBurst by remember { mutableStateOf(0) }
    LaunchedEffect(message) {
        message?.let {
            if (it.startsWith("تم إرجاع")) successBurst++
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

    // لون تصنيف الموقع — يغذي تدرج الرأس (اختيار 46)
    val siteCategory = categories.firstOrNull { it.id == site?.categoryId }

    Scaffold(
        // سنابار بشريط مهلة متناقص (اختيارات 2.3 — مقترح 12)
        snackbarHost = { GalaxySnackbarHost(snackbarHostState) },
        topBar = {
            // رأس التفاصيل بتدرج يتبع لون التصنيف (اختيار 46 من
            // الجولة الثالثة): كل موقع له سماؤه — التدرج يتحول بمزج
            // بطيء عند تغيير التصنيف.
            SiteDetailsHeader(
                categoryColorHex = siteCategory?.colorHex
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Text(
                    site?.name ?: "تفاصيل الموقع",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // زر «طارئ» — يفتح شاشة النزول الطارئ/الاستكشاف لهذا الموقع
            // (النسخة 2.4 — تعليمات هذه الجلسة). يظهر فور فتح الموقع
            // فوق التبويبات بلون تنبيهي واضح.
            Button(
                onClick = { onOpenEmergency(viewModel.siteId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("طارئ")
            }

            // إصلاح UX (2.9.2 + تتمته في 2.9.3): صفوف التبويبات العلوية
            // كانت تُفتح بالنقر فقط، فيضطر المستخدم للوصول لشريط بعيد أعلى
            // الشاشة في كل تبديل. الحل الاحترافي القياسي في الأندرويد: ربط
            // شريط التبويبات بـ `HorizontalPager` فيصبح بالإمكان أيضًا التنقل
            // بالسحب يمينًا/يسارًا فوق محتوى أي تبويب — بلا التخلي عن النقر
            // المباشر على الشريحة لمن يفضّله. الشريط ما زال يُمرَّر تلقائيًا
            // ليبقى التبويب النشط ظاهرًا. وفي 2.9.3 أُضيف `weight(1f)`
            // للمُصفّح: بدونه كان يُقاس بارتفاع العمود كاملًا فيمتد خارج
            // أسفل الشاشة ويُقصّ آخر محتوى كل تبويب (لا يصل إليه التمرير).
            val tabsScrollState = rememberLazyListState()
            val pagerState = rememberPagerState(
                initialPage = DetailsTab.entries.indexOf(tab)
            ) { DetailsTab.entries.size }

            // إصلاح جذري (2.9.4) لمشكلة «الرجوع للخلف عند السحب السريع بين
            // التبويبات»: المزامنة السابقة كانت ثنائية الاتجاه — السحب يحدّث
            // `tab` عبر `settledPage`، و`LaunchedEffect(tab)` كان يستدعي
            // `animateScrollToPage` لكن بعد تعليق `animateScrollToItem`
            // (تمرير شريط الشرائح) الذي يستغرق مئات المللي ثواني، فيُقيَّم
            // شرط الحراسة ببيانات تقادمت أثناءها: إن كان المستخدم قد مرّر
            // مجددًا خلال ذلك التأخير سحبت الحركة البرمجية المُصفّح إلى
            // الصفحة السابقة — وهذا بالضبط سلوك «يتقدم ثم يرجع» المبلَّغ
            // عنه. الحل القياسي: تدفق أحادي الاتجاه — المُصفّح يقود التبويب
            // (عند الاستقرار) والنقرة على الشريحة تقود المُصفّح مباشرة،
            // ولا يوجد أي مسار يعيد تحريك المُصفّح استجابةً لتغيّر `tab`.
            // سحب يستقر على صفحة جديدة: يُحدَّث التبويب المحدد ليطابقها
            // ويُمرَّر الشريط ليبقى ظاهرًا (`settledPage` لا يتغير أثناء
            // السحب نفسه فلا تعارض، وتمرير الشريط لا يمس المُصفّح).
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    val newTab = DetailsTab.entries.getOrNull(page) ?: return@collect
                    if (newTab != tab) tab = newTab
                    tabsScrollState.animateScrollToItem(page)
                }
            }

            LazyRow(
                state = tabsScrollState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(DetailsTab.entries, key = { _, t -> t.name }) { index, t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = {
                            // نقرة على شريحة: تُحرّك المُصفّح صراحةً وبلا وسيط
                            // متأخر — المستخدم هو مصدر هذه الحركة وحده.
                            if (tab != t) {
                                tab = t
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        label = { Text(t.label) }
                    )
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
                // انتقال التبويبات: سحب أفقي حي بين الصفحات (بدل تلاشي
                // AnimatedContent السابق) — وعناصر كل تبويب لا تزال تدخل
                // متتابعة بفاصل 30 مللي ثانية عبر StaggeredItem بداخله.
                //
                // إصلاح (2.9.4) لمشكلة «البيانات تظهر في وسط التبويب بدل
                // أعلاه»: المُصفّح يقيس صفحاته بارتفاع أدنى صفر، و`LazyColumn`
                // الذي أقصر من الصفحة يلتفّ حول محتواه، والمُصفّح يضع الصفحة
                // الافتراضية عموديًا بمنتصف المساحة (`verticalAlignment`
                // الافتراضي `CenterVertically`) — فكانت بيانات تبويبات
                // المعلومات/المسحوبات/الطوارئ/الصيانة/المرفقات تظهر بالوسط
                // بينما تبويب المواد سليم لأن جذره `fillMaxSize`. الحل:
                // محاذاة الصفحات لأعلى دائمًا.
                HorizontalPager(
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (DetailsTab.entries[page]) {
                        DetailsTab.INFO -> InfoTab(s, categories)
                        DetailsTab.MATERIALS -> MaterialsTab(
                            detail = detail,
                            displayAvailable = displayAvailableItems,
                            openWithdrawnNames = withdrawals
                                .filter { it.status != WithdrawalStatus.RETURNED }
                                .map { it.itemName.lowercase() }
                                .toSet(),
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
                        DetailsTab.EMERGENCY -> EmergencyTab(visits = emergencyVisits)
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
        // انفجار نجوم نجاح الإرجاع (اختيار 44) — طبقة فوق المحتوى
        // كله؛ لا تعترض اللمس لأن الـ Canvas بلا أي معالج إشارات
        GalaxyStarBurst(
            trigger = successBurst,
            modifier = Modifier.matchParentSize()
        )
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

/* ═══════════════════ رأس التفاصيل المتدرج ═══════════════════ */

/**
 * رأس شاشة التفاصيل (اختيار 46 من الجولة الثالثة): تدرج لوني ناعم
 * مشتق من لون تصنيف الموقع — «كل موقع له سماؤه». عند تغيير
 * التصنيف تمتزج الألوان ببطء (600 مللي ثانية) بدل القفز.
 * المحتوى (رجوع + عنوان + أفعال) يأتي من المستدعي عبر `actions`.
 */
@Composable
private fun SiteDetailsHeader(
    categoryColorHex: String?,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val parsed = categoryColorHex?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
    }
    val headerColor by animateColorAsState(
        targetValue = parsed ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(600),
        label = "header-gradient-color"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        headerColor.copy(alpha = 0.30f),
                        headerColor.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                )
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            actions()
        }
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
            // ظهور متتابع عند دخول التبويب (مقترح 9)
            StaggeredItem(index = 0, trigger = "info-tab") {
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
        }
        item {
            StaggeredItem(index = 1, trigger = "info-tab") {
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
}

/* ═══════════════ تبويب المواد (اختيار من الكتالوج الموحد) ═══════════════ */

/**
 * أنواع أقسام تبويب المواد — تشتق لون الرقاقة الدلالي (اختيار 2 من
 * جلسة المواد والسحوبات والطوارئ): أخضر للموجود، كهرماني للاحتياج،
 * بنفسجي لتحتاج صيانة، أحمر للمسحوب. الرقاقة تقول «عنصر جرد» بصريًا
 * ولا يمكن الخلط بينها وبين المهام.
 */
private enum class MaterialSectionKind(val tint: Color) {
    AVAILABLE(Color(0xFF34D399)),
    NEEDED(Color(0xFFFFC857)),
    MAINTENANCE(Color(0xFFA78BFA)),
    WITHDRAWN(Color(0xFFF87171))
}

/**
 * المواد في النسخة 2.8 (الاختياران 2 و12 من جلسة المواد والسحوبات
 * والطوارئ):
 *  - عرض رقاقات حالة دلالية بدل مربعات علامة الصح: المواد بيانات جرد
 *    لا مهام (المربعات كانت توحي بمنطق مهام وتخزّن تحديدًا بلا أثر).
 *  - قسم «الموجودة حاليًا» يُعرض محسوبًا: القائمة المخزنة ناقص المواد
 *    المسحوبة الآن (غير المُرجعة) — التخزين لا يتغير، وعند الإرجاع
 *    تعود المادة للظهور تلقائيًا. عند الحفظ تُدمج العناصر المطروحة
 *    مجددًا في النص المخزن حتى لا يطمسها الحفظ (صفر تغيير للبيانات).
 *
 * التخزين ما زال نصيًا في نفس الأعمدة (تنسيق «[ ] / [x]» في
 * MaterialLines) — البيانات القديمة النصية تبقى محفوظة ومقروءة،
 * والعناصر غير الموجودة في الكتالوج تُعرض كما هي وتُحذف فرديًا.
 */
@Composable
private fun MaterialsTab(
    detail: SiteDetail?,
    displayAvailable: List<MaterialItem>,
    openWithdrawnNames: Set<String>,
    catalog: List<Material>,
    onSave: (available: String, needed: String, maintenance: String, withdrawn: String) -> Unit
) {
    // «الموجودة» تُحرَّر انطلاقًا من القائمة المعروضة المحسوبة (التي
    // أسقطت المسحوبات المفتوحة في الـ ViewModel). المفاتيح: معرف الصف
    // والقائمة المعروضة — عند إرجاع مادة تتحدث القائمة فيعاد التهيئة.
    // إصلاح UX: كان المفتاح قائمة العرض نفسها (كائن جديد مع كل إصدار من
    // الـ Flow) فأي إصدار — ولو بنفس المحتوى — يمحو تعديلات المستخدم قبل
    // الحفظ. المفتاح الآن مضمون: معرّف صف التفاصيل + بصمة نصية لأسماء
    // المسحوبات المفتوحة (تُقارن بالمحتوى لا بهوية الكائن).
    val openSignature = openWithdrawnNames.sorted().joinToString("|")
    var available by remember(detail?.id, openSignature) { mutableStateOf(displayAvailable) }
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
            // ظهور متتابع للأقسام الأربعة عند دخول التبويب (مقترح 9)
            StaggeredItem(index = 0, trigger = "materials-tab") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("المواد الموجودة حاليًا")
                    MaterialChipSection(
                        items = available,
                        kind = MaterialSectionKind.AVAILABLE,
                        catalog = catalog
                    ) { available = it }
                }
            }
            StaggeredItem(index = 1, trigger = "materials-tab") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("احتياج الموقع (ما ينقص)")
                    MaterialChipSection(
                        items = needed,
                        kind = MaterialSectionKind.NEEDED,
                        catalog = catalog
                    ) { needed = it }
                }
            }
            StaggeredItem(index = 2, trigger = "materials-tab") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("مواد تحتاج صيانة")
                    MaterialChipSection(
                        items = maintenance,
                        kind = MaterialSectionKind.MAINTENANCE,
                        catalog = catalog
                    ) { maintenance = it }
                }
            }
            StaggeredItem(index = 3, trigger = "materials-tab") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("مواد تم سحبها")
                    MaterialChipSection(
                        items = withdrawn,
                        kind = MaterialSectionKind.WITHDRAWN,
                        catalog = catalog
                    ) { withdrawn = it }
                }
            }
        }
        // زر الحفظ مع توهج الضغط (مقترح 1). الحفظ يدمج المواد المطروحة
        // من العرض (المسحوبة الآن) مع القائمة المحررة حتى يبقى التخزين
        // كاملًا — العرض محسوب فقط (اختيار 12).
        GlowButton(
            onClick = {
                val availableNames = available.map { it.text.lowercase() }.toSet()
                val stillWithdrawn = MaterialLines.parse(detail?.availableMaterials.orEmpty())
                    .filter {
                        it.text.lowercase() in openWithdrawnNames &&
                            it.text.lowercase() !in availableNames
                    }
                onSave(
                    MaterialLines.serialize(available + stillWithdrawn),
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
 * قسم مواد واحد برقاقات دلالية (اختيار 2): بلا مربعات علامة صح —
 * كل مادة رقاقة بلون قسمها تظهر بأنميشن نابض، وأيقونة × صغيرة تحذفها
 * من القائمة. زر «إضافة» يختار من الكتالوج الموحد كما في النسخة 2.2
 * (واجهة الاختيار نفسها تبقى بأدوات التحديد لأن التحديد فيها منطقي).
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow تجريبي في Compose 1.6
@Composable
private fun MaterialChipSection(
    items: List<MaterialItem>,
    kind: MaterialSectionKind,
    catalog: List<Material>,
    onChange: (List<MaterialItem>) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    GalaxyCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) {
                Text(
                    "لا عناصر بعد — اختر من المواد الموحدة بالزر أدناه",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // إصلاح (كود انميشن ميت): كان `AnimatedVisibility(visible = true)`
                // ثابتًا على «ظاهر»، فمهما أُضيف عنصر جديد يظهر فورًا بلا أي
                // حركة رغم أن التعليق كان يعد بنبضة دخول. الآن كل رقاقة
                // تبدأ مخفية ثم تُشغّل نبضتها مرة واحدة عند تركيبها فعليًا
                // (حالة انتقال محفوظة لكل عنصر بمفتاح نصّه).
                items.forEach { item ->
                    key(item.text) {
                        val appear = remember {
                            MutableTransitionState(false).apply { targetState = true }
                        }
                        AnimatedVisibility(
                            visibleState = appear,
                            enter = fadeIn(tween(200)) + scaleIn(tween(240), initialScale = 0.86f),
                            label = "material-chip-enter"
                        ) {
                            MaterialChip(
                                item = item,
                                kind = kind,
                                onRemove = {
                                    onChange(items.filterNot { it.text == item.text })
                                }
                            )
                        }
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
 * رقاقة مادة واحدة (اختيار 2): AssistChip بلون دلالي مشتق من نوع القسم
 * ونقطة لون في المقدمة، وبجانبها أيقونة × صغيرة للحذف من القائمة.
 * في قسم «المسحوبة»: العناصر الموسومة ✔ سابقًا (دورة إرجاع مكتملة)
 * تظهر باهتة حتى لا تضيع معلومة الإرجاع ولا تعود هيئة «مهام».
 */
@Composable
private fun MaterialChip(
    item: MaterialItem,
    kind: MaterialSectionKind,
    onRemove: () -> Unit
) {
    val tint = kind.tint
    val faded = kind == MaterialSectionKind.WITHDRAWN && item.checked
    val containerColor = tint.copy(alpha = if (faded) 0.08f else 0.16f)
    val labelColor = if (faded) tint.copy(alpha = 0.55f) else tint

    // إصلاح UX مهم: كانت الرقاقة `AssistChip(onClick = { })` — زر يلمع عند
    // الضغط ولا يفعل شيئًا (فخّ إيحاء كاذب، وهو نفس الخطأ الذي تجنّبه
    // `GalaxyCard`). صارت سطحًا غير قابل للنقر يحمل حالة العنصر بصريًا،
    // وزرّ الحذف × داخلها: لا إيحاء زائف وتوفير مساحة الرقاقة المضاعفة.
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = containerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = if (faded) 0.2f else 0.45f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (faded) tint.copy(alpha = 0.4f) else tint)
            )
            Text(
                item.text,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.padding(start = 8.dp)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "حذف «${item.text}» من القائمة",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
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
                                // نفس علامة الصح المرسومة (مقترح 5)
                                DrawnCheck(
                                    checked = isSelected,
                                    onToggle = { checked ->
                                        selected = if (checked) {
                                            selected + material.name
                                        } else {
                                            selected - material.name
                                        }
                                    }
                                )
                                Spacer(Modifier.size(10.dp))
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
    // إصلاح تعثّر التمرير (2.9.3): نفس حل شاشة المواقع — أثناء
    // التمرير النشط تظهر العناصر فورًا بلا تأخير ولا حركة.
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
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
            // ظهور متتابع للسجلات عند دخول التبويب (مقترح 9)
            itemsIndexed(withdrawals, key = { _, w -> w.id }) { index, w ->
                StaggeredItem(
                    index = index,
                    trigger = "withdrawals-tab",
                    immediate = listState.isScrollInProgress
                ) {
                    WithdrawalRow(
                        w = w,
                        onStartMaintenance = onStartMaintenance,
                        onReturn = onReturn,
                        onDelete = onDelete
                    )
                }
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
                IconButton(onClick = { onDelete(w) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف السجل",
                        modifier = Modifier.size(20.dp)
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
                    // أزرار الحالة المتدرجة: تنكمش لدائرة تحميل ثم نجاح
                    // أخضر قبل تحديث الحالة (اختيارات 2.3 — مقترح 3)
                    if (w.status == WithdrawalStatus.WITHDRAWN) {
                        MorphingActionButton(
                            label = "بدء الصيانة",
                            outlined = true,
                            onClick = { onStartMaintenance(w) }
                        )
                    }
                    MorphingActionButton(
                        label = "إرجاع للموقع",
                        onClick = { onReturn(w) }
                    )
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
@OptIn(ExperimentalMaterial3Api::class)
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
            // توهج الضغط (مقترح 1)
            GlowButton(
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

/* ═══════════ تبويب الطوارئ (اختيار 21: السجلات لم تعد مدفونة) ═══════════ */

/**
 * سجل النزول الطارئ/الاستكشاف لهذا الموقع (النسخة 2.8 — اختيار 21 من
 * جلسة المواد والسحوبات والطوارئ). السجلات كانت تُحفظ في جدول
 * `emergency_visits` بلا أي وسيلة قراءة (الـ DAO كان إدخالًا فقط)؛
 * الآن تبويب سادس يعرضها بترتيب الأحدث أولًا: بطاقة لكل زيارة بسببها
 * وشريحة نتيجة ملونة دلاليًا وتاريخها، وتوسعة تُظهر التفاصيل.
 */
@Composable
private fun EmergencyTab(visits: List<EmergencyVisit>) {
    // إصلاح تعثّر التمرير (2.9.3): نفس حل شاشة المواقع.
    // (يُعلَن قبل العودة المبكرة — الدوال المركّبة تُستدعى دائمًا وبلا شروط)
    val listState = rememberLazyListState()
    if (visits.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.WarningAmber,
            title = "لا نزولات طارئة",
            subtitle = "اضغط زر «طارئ» أعلى الشاشة لتسجيل أول نزول لهذا الموقع — سيظهر سجله هنا"
        )
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            GalaxyCard {
                Text(
                    "سجل النزولات الطارئة: ${visits.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        // ظهور متتابع للبطاقات عند دخول التبويب (مقترح 9)
        itemsIndexed(visits, key = { _, v -> v.id }) { index, visit ->
            StaggeredItem(
                index = index,
                trigger = "emergency-tab",
                immediate = listState.isScrollInProgress
            ) {
                EmergencyVisitCard(visit)
            }
        }
    }
}

/**
 * بطاقة زيارة طارئة واحدة: السبب + شريحة النتيجة بلونها الدلالي
 * (نفس ألوان زر النتيجة المقسّم في شاشة التسجيل) + التاريخ، ومع أي
 * تفاصيل إضافية زر توسعة يُظهر وصف المشكلة والمواد المستخدمة
 * والتحليلات حسب النتيجة.
 */
@Composable
private fun EmergencyVisitCard(visit: EmergencyVisit) {
    var expanded by remember { mutableStateOf(false) }
    val outcomeColor = when (visit.outcome) {
        VisitOutcome.NO_PROBLEM -> MaterialTheme.colorScheme.tertiary
        VisitOutcome.RESOLVED -> MaterialTheme.colorScheme.primary
        VisitOutcome.UNRESOLVED -> MaterialTheme.colorScheme.error
    }
    val hasDetails = visit.problemDescription.isNotBlank() ||
        visit.usedMaterials.isNotBlank() ||
        visit.analysis.isNotBlank()

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
                    visit.reason,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = outcomeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        visit.outcome.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = outcomeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                visit.visitDate.formatDateTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasDetails) {
                TextButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (expanded) "إخفاء التفاصيل" else "عرض التفاصيل",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(220)),
                    exit = fadeOut(tween(140)) + shrinkVertically(animationSpec = tween(140))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (visit.problemDescription.isNotBlank()) {
                            VisitDetailBlock("وصف المشكلة والحل", visit.problemDescription)
                        }
                        if (visit.usedMaterials.isNotBlank()) {
                            VisitDetailBlock("المواد المستخدمة", visit.usedMaterials)
                        }
                        if (visit.analysis.isNotBlank()) {
                            VisitDetailBlock("تحليلات وحلول متوقعة", visit.analysis)
                        }
                    }
                }
            }
        }
    }
}

/** كتلة تفصيل واحدة داخل بطاقة الزيارة: عنوان ملون + النص */
@Composable
private fun VisitDetailBlock(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
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

    // السجلات الجديدة (اختيار 36 من الجولة الثالثة): تُحفظ المعرفات
    // التي رُكبت أثناء فتح التبويب، وأي سجل يظهر بعدها تُنبض نقطته
    // بحلقة سماوية — الخط الزمني «يُكتب» أمام المستخدم.
    val seenLogIds = remember { mutableStateOf(logs.map { it.id }.toSet()) }
    var freshLogIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(logs) {
        val current = logs.map { it.id }.toSet()
        val fresh = current - seenLogIds.value
        if (fresh.isNotEmpty()) freshLogIds = fresh
        seenLogIds.value = current
    }

    // إصلاح تعثّر التمرير (2.9.3): نفس الحل — أثناء التمرير النشط
    // تظهر العناصر فورًا بلا تأخير.
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StaggeredItem(index = 0, trigger = "maintenance-tab") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("موعد الصيانة القادم")
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val due = detail?.nextMaintenanceDue
                    if (due == null) {
                        Text("لا يوجد موعد محدد", color = MaterialTheme.colorScheme.outline)
                    } else {
                        val days = due.daysFromNow()
                        // حلقة عد تنازلي تستنزف وتتدرج ألوانها حسب
                        // الخطورة (اختيار 37 من الجولة الثالثة)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DueCountdownRing(days = days, size = 48.dp)
                            Column {
                                Text("الموعد: ${due.formatDate()}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    when {
                                        days < 0 -> "متأخر ${-days} يوم"
                                        days == 0L -> "اليوم"
                                        else -> "بعد $days يوم"
                                    },
                                    // اتساق الحالات مع بقية الشاشات: أحمر
                                    // للتأخر الفعلي، كهرماني لمستحق اليوم
                                    color = when {
                                        days < 0 -> MaterialTheme.colorScheme.error
                                        days == 0L -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.secondary
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
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
            }
        }

        item {
            StaggeredItem(index = 1, trigger = "maintenance-tab") {
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
            // خط زمني: الخط يُرسم تدريجيًا والنقاط والبطاقات تظهر
            // بالترتيب خلفه (اختيارات 2.3 — مقترح 18)
            itemsIndexed(logs, key = { _, log -> log.id }) { index, log ->
                TimelineLogRow(
                    log = log,
                    index = index,
                    isLast = index == logs.lastIndex,
                    isFresh = log.id in freshLogIds,
                    onDelete = { logToDelete = log }
                )
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
                    "سُجلت الصيانة بنجاح. هل تريد تحديد الموعد القادم مقترحًا بعد " +
                        "${NEXT_DUE_SUGGESTION_DAYS.toInt().arabicDigits()} يومًا؟\n" +
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

/**
 * صف واحد في الخط الزمني لسجل الصيانة (مقترح 18): نقطة وخط عمودي
 * يظهران برسم تدريجي متتابع (تأخير 180 مللي لكل سجل)، ثم تظهر
 * البطاقة بتلاشي مرتبط بنفس التقدم.
 *
 * ترقية الجولة الثالثة (اختيار 36): السجل الجديد المضاف أثناء فتح
 * التبويب تُنبض نقطته بحلقتين سماويتين متعاقبتين — الخط «يمتد»
 * أمام المستخدم بدل الإدراج الصامت.
 */
@Composable
private fun TimelineLogRow(
    log: MaintenanceLog,
    index: Int,
    isLast: Boolean,
    isFresh: Boolean,
    onDelete: () -> Unit
) {
    // التقدم يبدأ صفرًا عند أول تركيب للسجل ويتحرك بتأخير متزايد
    // مقصور على أول 8 سجلات (إصلاح 2.9.3): بلا سقف كان السجل رقم 20
    // ينتظر ~4 ثوانٍ قبل أن يظهر.
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 420, delayMillis = 250 + minOf(index, 8) * 180),
        label = "timeline-$index"
    )
    // نبض الحلقة للسجل الجديد (اختيار 36): حلقتان متعاقبتان
    val ping = remember { Animatable(0f) }
    LaunchedEffect(isFresh) {
        if (isFresh) {
            repeat(2) {
                ping.snapTo(0f)
                ping.animateTo(1f, animationSpec = tween(750))
                delay(140)
            }
            ping.snapTo(0f)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outline

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // نقطة الدخول — تكبر مع التقدم، وتنبض حلقة حولها إن كان
            // السجل جديدًا (اختيار 36)
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(12.dp)
                    .graphicsLayer {
                        scaleX = progress
                        scaleY = progress
                    }
                    .drawBehind {
                        val p = ping.value
                        if (isFresh && p > 0f) {
                            drawCircle(
                                color = primary.copy(alpha = 0.65f * (1f - p)),
                                radius = 6.dp.toPx() + 11.dp.toPx() * p
                            )
                        }
                    }
                    .clip(CircleShape)
                    .background(primary)
            )
            // الخط الواصل للسجل التالي — يزداد وضوحه مع التقدم
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor.copy(alpha = progress))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).graphicsLayer { alpha = progress }) {
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
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف الإدخال")
                    }
                }
            }
        }
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
        // توهج الضغط لأزرار الحفظ مفعّل في حوارات الإضافة الأخرى (مقترح 1)
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
    // إصلاح تعثّر التمرير (2.9.3): نفس حل شاشة المواقع، لكل من شبكة
    // الصور وقائمة الملفات (كلٌ منهما قائمة كسولة منفصلة) — أثناء
    // أي تمرير نشط تظهر العناصر فورًا بلا تأخير ولا حركة.
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StaggeredItem(index = 0, trigger = "attachments-tab") {
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
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((rows * 110).dp.coerceAtMost(440.dp)),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // شبكة متتابعة: الصور تظهر واحدة تلو الأخرى (مقترح 20)
                    // وخلف كل صورة شيمر سديمي حتى يكتمل التحميل
                    // (اختيار 43 من الجولة الثالثة)
                    itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
                        StaggeredItem(
                            index = index,
                            trigger = images.size,
                            immediate = gridState.isScrollInProgress || listState.isScrollInProgress
                        ) {
                            var loaded by remember(image.id) { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (!loaded) {
                                    GalaxyShimmerBox(Modifier.matchParentSize())
                                }
                                AsyncImage(
                                    model = image.filePath,
                                    contentDescription = "صورة مرفقة",
                                    contentScale = ContentScale.Crop,
                                    onState = { state ->
                                        loaded = state is AsyncImagePainter.State.Success
                                    },
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
            itemsIndexed(files, key = { _, file -> file.id }) { index, file ->
                StaggeredItem(
                    index = index + 1,
                    trigger = "attachments-tab",
                    immediate = listState.isScrollInProgress
                ) {
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
            // توهج الضغط (مقترح 1)
            GlowButton(
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
