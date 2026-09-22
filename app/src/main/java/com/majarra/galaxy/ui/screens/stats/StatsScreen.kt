package com.majarra.galaxy.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Withdrawal
import com.majarra.galaxy.domain.model.RequestStatus
import com.majarra.galaxy.domain.model.WithdrawalStatus
import com.majarra.galaxy.domain.repository.EmergencyVisitRepository
import com.majarra.galaxy.domain.repository.MaterialDependencyRepository
import com.majarra.galaxy.domain.repository.MaterialRequestRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.SiteDetailRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import com.majarra.galaxy.ui.anim.OdometerNumber
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.util.MaterialLines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/* ============================================================
 * واجهة الإحصائيات — أُعيدت هيكلتها بالكامل (جلسة تعديلات منطق
 * المواد والإحصائيات) لتعرض حسب تعليمات الجلسة:
 *  1) عدد المواقع النشطة.
 *  2) عدد المواد حاليًا (مواد المواقع النشطة + تبعياتها بدون
 *     المسحوبات المفتوحة — أي ما هو فعليًا موجود الآن).
 *  3) سجل آخر العمليات: سحب/إرجاع مادة، نزول طارئ، إضافة مادة
 *     (موافقة احتياج)، تسجيل صيانة — كل بيان يعرض تفاصيل العملية
 *     والموقع المرتبط بها، وهو قابل للنقر فيوجّه المستخدم إلى
 *     واجهة الموقع الفرعية الخاصة بتلك العملية.
 * ============================================================ */

/** نوع العملية في السجل — يحدد الأيقونة وسلوك النقر (إلى أين يوجّه) */
enum class OperationType(val label: String, val icon: ImageVector) {
    WITHDRAWAL("سحب مادة", Icons.Filled.SwapVert),
    RETURN("إرجاع مادة", Icons.Filled.Inventory2),
    EMERGENCY("نزول طارئ", Icons.Filled.Warning),
    MATERIAL_ADDED("إضافة مادة", Icons.Filled.Inventory2),
    MAINTENANCE("صيانة", Icons.Filled.Build)
}

/** بيان واحد في سجل آخر العمليات */
data class OperationLogEntry(
    /** مفتاح فريد للعنصر في القائمة */
    val key: String,
    val type: OperationType,
    /** تفاصيل العملية (اسم المادة، السبب…) */
    val details: String,
    val siteId: Long,
    val siteName: String,
    val date: Long
)

/** بيانات شاشة الإحصائيات — تُحسب من القاعدة عند كل ظهور للشاشة */
data class StatsUi(
    val activeSites: Int = 0,
    /** عدد المواد حاليًا: مواد المواقع النشطة + تبعياتها ناقص المسحوبات المفتوحة */
    val materialsNow: Int = 0,
    val operations: List<OperationLogEntry> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val detailRepo: SiteDetailRepository,
    private val withdrawalRepo: WithdrawalRepository,
    private val emergencyRepo: EmergencyVisitRepository,
    private val requestRepo: MaterialRequestRepository,
    private val dependencyRepo: MaterialDependencyRepository,
    private val logRepo: MaintenanceLogRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsUi())
    val stats: StateFlow<StatsUi> = _stats.asStateFlow()

    init {
        load()
    }

    /**
     * الاستدعاء من الشاشة عند كل ظهور — الأرقام تعود إلى لحظتها
     * الحالية حتى لو تغيرت البيانات بعد الخروج من الشاشة (كما كان).
     */
    fun refresh() = load()

    /** كل القيم من استعلامات عدّ خفيفة تُنفَّذ عند الفتح */
    private fun load() {
        viewModelScope.launch {
            val sites = siteRepo.getAll()
            val siteNameById = sites.associate { it.id to it.name }
            val activeIds = sites.filter { !it.archived }.map { it.id }.toSet()

            val withdrawals = withdrawalRepo.getAll()
            val dependencies = dependencyRepo.getAll()
            val openBySite = withdrawals
                .filter { it.status.isOpen }
                .groupBy { it.siteId }

            // عدد المواد حاليًا: أسطر «الموجود» لكل موقع نشط (معقّمة)
            // ناقص سحوباتها المفتوحة، زائد تبعيات الموقع ناقص سحوباتها
            // المفتوحة (مطابقة الأم + الاسم كإخفاء الواجهة تمامًا).
            val details = detailRepo.getAll().filter { it.siteId in activeIds }
            val materialsNow = details.sumOf { d ->
                val openMain: Set<String> = openBySite[d.siteId]
                    ?.filter { it.parentName.isBlank() }
                    ?.map { it.itemName.trim().lowercase() }
                    ?.toSet()
                    ?: emptySet()
                val openDeps: Set<Pair<String, String>> = openBySite[d.siteId]
                    ?.filter { it.parentName.isNotBlank() }
                    ?.map { it.parentName.trim().lowercase() to it.itemName.trim().lowercase() }
                    ?.toSet()
                    ?: emptySet()
                val mainCount: Int = MaterialLines.parse(d.availableMaterials)
                    .map { it.text.trim().lowercase() }
                    .filter { it.isNotEmpty() && it !in openMain }
                    .distinct()
                    .size
                val depCount: Int = dependencies
                    .filter { it.siteId == d.siteId }
                    .count { dep ->
                        val pair = dep.parentName.trim().lowercase() to dep.name.trim().lowercase()
                        pair !in openDeps
                    }
                mainCount + depCount
            }

            val operations = buildOperations(siteNameById, withdrawals)

            _stats.value = StatsUi(
                activeSites = sites.count { !it.archived },
                materialsNow = materialsNow,
                operations = operations
            )
        }
    }

    /**
     * سجل آخر العمليات من مصادره الخمسة (الأحدث أولًا، آخر ٢٥ بيانًا):
     *  - سحب مادة: كل سجل سحوبات عند تاريخ سحبه (مع وسم تبعية الأم).
     *  - إرجاع مادة: السجلات المرجعة عند تاريخ الإرجاع.
     *  - نزول طارئ: كل نزول عند تاريخه.
     *  - إضافة مادة: طلبات الاحتياج الموافَق عليها عند تاريخ البت.
     *  - صيانة: سجلات الصيانة عند تاريخها.
     */
    private suspend fun buildOperations(
        siteNameById: Map<Long, String>,
        withdrawals: List<Withdrawal>
    ): List<OperationLogEntry> {
        val entries = mutableListOf<OperationLogEntry>()

        withdrawals.forEach { w ->
            val siteName = siteNameById[w.siteId] ?: "موقع"
            val depPrefix = if (w.parentName.isNotBlank()) "تبعية «${w.parentName}» — " else ""
            entries += OperationLogEntry(
                key = "w-${w.id}",
                type = OperationType.WITHDRAWAL,
                details = depPrefix + w.itemName +
                    if (w.withdrawReason.isNotBlank()) " — ${w.withdrawReason}" else "",
                siteId = w.siteId,
                siteName = siteName,
                date = w.withdrawnDate
            )
            if (w.status == WithdrawalStatus.RETURNED && w.returnedDate != null) {
                entries += OperationLogEntry(
                    key = "wr-${w.id}",
                    type = OperationType.RETURN,
                    details = depPrefix + w.itemName,
                    siteId = w.siteId,
                    siteName = siteName,
                    date = w.returnedDate
                )
            }
        }

        emergencyRepo.getAll().forEach { v ->
            entries += OperationLogEntry(
                key = "e-${v.id}",
                type = OperationType.EMERGENCY,
                details = v.reason,
                siteId = v.siteId,
                siteName = siteNameById[v.siteId] ?: "موقع",
                date = v.visitDate
            )
        }

        requestRepo.getAll().forEach { r ->
            if (r.status == RequestStatus.APPROVED && r.resolvedDate != null) {
                entries += OperationLogEntry(
                    key = "r-${r.id}",
                    type = OperationType.MATERIAL_ADDED,
                    details = r.materialName,
                    siteId = r.siteId,
                    siteName = siteNameById[r.siteId] ?: "موقع",
                    date = r.resolvedDate
                )
            }
        }

        logRepo.getAll().forEach { log ->
            entries += OperationLogEntry(
                key = "m-${log.id}",
                type = OperationType.MAINTENANCE,
                details = log.notes.ifBlank { "تسجيل صيانة" },
                siteId = log.siteId,
                siteName = siteNameById[log.siteId] ?: "موقع",
                date = log.maintenanceDate
            )
        }

        return entries.sortedByDescending { it.date }.take(25)
    }
}

/**
 * صفحة الإحصائيات الجديدة: مؤشران رئيسيان + سجل عمليات تفاعلي.
 * النقر على بيان يفتح واجهة الموقع الفرعية الخاصة بالعملية:
 * سحب/إرجاع → المسحوبات، نزول طارئ → شاشة النزول الطارئ،
 * إضافة مادة/صيانة → قسم المواد في الموقع.
 */
@Composable
fun StatsScreen(
    onOpenSite: (Long) -> Unit = {},
    onOpenWithdrawals: (Long) -> Unit = {},
    onOpenEmergency: (Long) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // تحديث عند كل ظهور للشاشة (لا مرة واحدة عند أول فتح فقط)
    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "الإحصائيات",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item { SectionTitle("المؤشرات الأساسية") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    icon = Icons.Filled.CellTower,
                    value = stats.activeSites,
                    label = "المواقع النشطة",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.Inventory2,
                    value = stats.materialsNow,
                    label = "عدد المواد حاليًا",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { SectionTitle("سجل آخر العمليات") }
        if (stats.operations.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.Build,
                    "لا توجد عمليات بعد",
                    "ستظهر هنا السحوبات والإرجاعات والنزولات الطارئة والإضافات والصيانة"
                )
            }
        } else {
            items(stats.operations, key = { it.key }) { op ->
                OperationCard(
                    entry = op,
                    onClick = {
                        when (op.type) {
                            OperationType.WITHDRAWAL, OperationType.RETURN -> onOpenWithdrawals(op.siteId)
                            OperationType.EMERGENCY -> onOpenEmergency(op.siteId)
                            OperationType.MATERIAL_ADDED, OperationType.MAINTENANCE -> onOpenSite(op.siteId)
                        }
                    }
                )
            }
        }
    }
}

/** بطاقة بيان عملية: أيقونة النوع + العنوان والتفاصيل + الموقع والتاريخ */
@Composable
private fun OperationCard(entry: OperationLogEntry, onClick: () -> Unit) {
    GalaxyCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                entry.type.icon,
                contentDescription = entry.type.label,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.type.label, style = MaterialTheme.typography.titleSmall)
                if (entry.details.isNotBlank()) {
                    Text(
                        entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    "الموقع: ${entry.siteName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    entry.date.formatDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "فتح الموقع",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** بطاقة رقم واحدة: أيقونة + قيمة كبيرة + تسمية */
@Composable
private fun StatCard(
    icon: ImageVector,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    GalaxyCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                // عجلة أرقام تدور عموديًا حتى تستقر على القيمة
                // (اختيارات 2.3 — مقترح 15)
                OdometerNumber(
                    value = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
