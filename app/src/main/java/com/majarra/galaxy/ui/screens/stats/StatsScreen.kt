package com.majarra.galaxy.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.domain.model.AttachmentType
import com.majarra.galaxy.domain.repository.AttachmentRepository
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.repository.MaintenanceLogRepository
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.WithdrawalRepository
import com.majarra.galaxy.domain.usecase.CheckMaintenanceDueUseCase
import com.majarra.galaxy.ui.anim.OdometerNumber
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** أرقام شاشة الإحصائيات — تُحمَّل دفعة واحدة عند فتح الشاشة */
data class StatsUi(
    val activeSites: Int = 0,
    val archivedSites: Int = 0,
    val categories: Int = 0,
    val maintenanceLogs: Int = 0,
    val attachmentsTotal: Int = 0,
    val attachmentsImages: Int = 0,
    val dueSites: Int = 0,
    val openWithdrawals: Int = 0,
    val catalogMaterials: Int = 0
) {
    val attachmentsFiles: Int get() = attachmentsTotal - attachmentsImages
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val categoryRepo: CategoryRepository,
    private val logRepo: MaintenanceLogRepository,
    private val attachmentRepo: AttachmentRepository,
    private val withdrawalRepo: WithdrawalRepository,
    private val materialRepo: MaterialRepository,
    private val checkMaintenanceDue: CheckMaintenanceDueUseCase
) : ViewModel() {

    private val _stats = MutableStateFlow(StatsUi())
    val stats: StateFlow<StatsUi> = _stats.asStateFlow()

    init {
        load()
    }

    /** كل الأرقام استعلامات عدّ خفيفة تُنفَّذ مرة واحدة عند الفتح */
    private fun load() {
        viewModelScope.launch {
            val images = attachmentRepo.countByType(AttachmentType.IMAGE.name)
            _stats.value = StatsUi(
                activeSites = siteRepo.countActive(),
                archivedSites = siteRepo.countArchived(),
                categories = categoryRepo.getAll().size,
                maintenanceLogs = logRepo.countAll(),
                attachmentsTotal = attachmentRepo.countAll(),
                attachmentsImages = images,
                dueSites = runCatching { checkMaintenanceDue().size }.getOrDefault(0),
                openWithdrawals = withdrawalRepo.countOpen(),
                catalogMaterials = materialRepo.getAll().size
            )
        }
    }
}

/**
 * صفحة إحصائيات كاملة (إجابة الاسئله.md): نظرة رقمية واحدة على
 * كل ما في التطبيق — المواقع والتصنيفات والسجلات والمرفقات والمستحق.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "الإحصائيات",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        SectionTitle("المواقع")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.CellTower,
                value = stats.activeSites,
                label = "مواقع نشطة",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Filled.Archive,
                value = stats.archivedSites,
                label = "مواقع مؤرشفة",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.Category,
                value = stats.categories,
                label = "تصنيفات",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Filled.Warning,
                value = stats.dueSites,
                label = "صيانة مستحقة",
                modifier = Modifier.weight(1f),
                emphasize = stats.dueSites > 0
            )
        }

        SectionTitle("الصيانة والمرفقات")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.Build,
                value = stats.maintenanceLogs,
                label = "سجلات صيانة",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Filled.AttachFile,
                value = stats.attachmentsTotal,
                label = "مرفقات",
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitle("السحوبات والمواد الموحدة")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Filled.SwapVert,
                value = stats.openWithdrawals,
                label = "مسحوبات لم تُرجع",
                modifier = Modifier.weight(1f),
                emphasize = stats.openWithdrawals > 0
            )
            StatCard(
                icon = Icons.Filled.Inventory2,
                value = stats.catalogMaterials,
                label = "مواد موحدة",
                modifier = Modifier.weight(1f)
            )
        }

        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("تفصيل المرفقات", style = MaterialTheme.typography.titleSmall)
                Text(
                    "صور: ${stats.attachmentsImages} — ملفات: ${stats.attachmentsFiles}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** بطاقة رقم واحدة: أيقونة + قيمة كبيرة + تسمية */
@Composable
private fun StatCard(
    icon: ImageVector,
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    GalaxyCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (emphasize) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column {
                // عجلة أرقام تدور عموديًا حتى تستقر على القيمة
                // (اختيارات 2.3 — مقترح 15)
                OdometerNumber(
                    value = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (emphasize) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
