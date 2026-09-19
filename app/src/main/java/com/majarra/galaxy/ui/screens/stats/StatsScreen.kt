package com.majarra.galaxy.ui.screens.stats

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.majarra.galaxy.ui.anim.GalaxyNumberMorph
import com.majarra.galaxy.ui.anim.OdometerNumber
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** شهر واحد في أعمدة نشاط الصيانة (اختيار 35) */
data class MonthStat(val label: String, val count: Int)

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
    val catalogMaterials: Int = 0,
    /** نشاط الصيانة لآخر ستة أشهر (اختيار 35 من الجولة الثالثة) */
    val monthlyMaintenance: List<MonthStat> = emptyList()
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
                catalogMaterials = materialRepo.getAll().size,
                monthlyMaintenance = buildMonthlyMaintenance()
            )
        }
    }

    /**
     * توزيع سجلات الصيانة على آخر ستة أشهر (اختيار 35). قراءة واحدة
     * لكل السجلات ثم عدّ محلي — بلا استعلام لكل شهر.
     */
    private suspend fun buildMonthlyMaintenance(): List<MonthStat> {
        val logs = logRepo.getAll()
        val thisMonth = YearMonth.now()
        val arabic = Locale.forLanguageTag("ar")
        return (5 downTo 0).map { back ->
            val ym = thisMonth.minusMonths(back.toLong())
            val count = logs.count { log ->
                val z = Instant.ofEpochMilli(log.maintenanceDate)
                    .atZone(ZoneId.systemDefault())
                z.year == ym.year && z.monthValue == ym.monthValue
            }
            MonthStat(
                label = ym.month.getDisplayName(TextStyle.SHORT, arabic),
                count = count
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

        // أعمدة نشاط الصيانة النابضة (اختيار 35 من الجولة الثالثة)
        MaintenanceBarsCard(monthly = stats.monthlyMaintenance)

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

/**
 * أعمدة نشاط الصيانة الشهري (اختيار 35 = خيار 8 من اختيارات الجولة
 * الثالثة): أعمدة تنمو بنابض مبالغ قليلًا (overshoot) عند الظهور
 * بتأخير متتابع، مع القيمة الرقمية فوق كل عمود بمروف الأرقام.
 */
@Composable
private fun MaintenanceBarsCard(monthly: List<MonthStat>) {
    if (monthly.isEmpty()) return
    GalaxyCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("نشاط الصيانة — آخر ٦ أشهر", style = MaterialTheme.typography.titleSmall)
            if (monthly.all { it.count == 0 }) {
                Text(
                    "لا توجد سجلات صيانة في الأشهر الستة الأخيرة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxCount = monthly.maxOf { it.count }.coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    monthly.forEachIndexed { index, month ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            GalaxyNumberMorph(
                                value = month.count,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // النمو بنابض مبالغ قليلًا وتأخير متتابع
                            var grown by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(120L + index * 90L)
                                grown = true
                            }
                            val fullHeight = 8 + 84 * month.count / maxCount
                            val height by animateDpAsState(
                                targetValue = if (grown) fullHeight.dp else 8.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "bar-$index"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(height)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (index == monthly.lastIndex) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        }
                                    )
                            )
                            Text(
                                month.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
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
