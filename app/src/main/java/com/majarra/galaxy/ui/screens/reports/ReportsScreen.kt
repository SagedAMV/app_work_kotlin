package com.majarra.galaxy.ui.screens.reports

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.majarra.galaxy.domain.model.EquipmentCategory
import com.majarra.galaxy.domain.repository.EquipmentRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.MaintenanceRepository
import com.majarra.galaxy.domain.repository.RequirementRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.export.CsvExporter
import com.majarra.galaxy.export.ExcelXmlExporter
import com.majarra.galaxy.export.PdfReportExporter
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.majarra.galaxy.util.formatDecimals

/** أنواع التقارير الستة المطلوبة */
enum class ReportType(val title: String, val description: String) {
    EQUIPMENT("جرد المعدات", "كل المعدات موزعة على المواقع"),
    NETWORK("حالة الشبكة", "الروابط وحالاتها وتردداتها"),
    FAULTS("الأعطال", "التذاكر وزمن المعالجة"),
    REQUIREMENTS("الاحتياج", "الاحتياجات وحالاتها"),
    MAINTENANCE("الصيانة", "جداول الصيانة الوقائية واستحقاقاتها"),
    POWER("الطاقة", "الألواح الشمسية والبطاريات والمنظمات")
}

enum class ExportFormat(val label: String, val mime: String, val ext: String) {
    CSV("CSV", "text/csv", "csv"),
    EXCEL("Excel", "application/vnd.ms-excel", "xls"),
    PDF("PDF", "application/pdf", "pdf")
}

data class ReportData(val headers: List<String>, val rows: List<List<String>>)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val siteRepo: SiteRepository,
    private val equipmentRepo: EquipmentRepository,
    private val linkRepo: LinkRepository,
    private val ticketRepo: TicketRepository,
    private val requirementRepo: RequirementRepository,
    private val maintenanceRepo: MaintenanceRepository
) : ViewModel() {

    private suspend fun siteName(id: Long): String =
        siteRepo.getSite(id)?.name ?: "موقع $id"

    /** تجميع بيانات التقرير لحظيًا من قاعدة البيانات المحلية */
    suspend fun buildReport(type: ReportType): ReportData = withContext(Dispatchers.Default) {
        when (type) {
            ReportType.EQUIPMENT -> {
                val rows = equipmentRepo.getAll().map { e ->
                    listOf(
                        siteName(e.siteId), e.category.label, e.company, e.model,
                        e.serialNumber, e.status.label
                    )
                }
                ReportData(listOf("الموقع", "التصنيف", "الشركة", "الطراز", "الرقم التسلسلي", "الحالة"), rows)
            }
            ReportType.NETWORK -> {
                val rows = linkRepo.getAll().map { l ->
                    listOf(
                        siteName(l.sourceSiteId), siteName(l.targetSiteId), l.type.label,
                        l.networkClass.label, l.status.label,
                        l.frequencyMHz.toString(), l.distanceKm.formatDecimals(2)
                    )
                }
                ReportData(
                    listOf("المصدر", "الهدف", "النوع", "التصنيف", "الحالة", "التردد م.هـ", "المسافة كم"),
                    rows
                )
            }
            ReportType.FAULTS -> {
                val rows = ticketRepo.getAll().map { t ->
                    listOf(
                        siteName(t.siteId), t.title, t.severity.label, t.status.label,
                        t.openedAt.formatDate(),
                        (t.resolutionTimeMinutes ?: 0).toString()
                    )
                }
                ReportData(listOf("الموقع", "العنوان", "الخطورة", "الحالة", "تاريخ الفتح", "زمن المعالجة (د)"), rows)
            }
            ReportType.REQUIREMENTS -> {
                val requirements = requirementRepo.observeAll().first()
                val rows = requirements.map { r ->
                    listOf(siteName(r.siteId), r.type.label, r.status.label, r.createdAt.formatDate(), r.notes)
                }
                ReportData(listOf("الموقع", "النوع", "الحالة", "التاريخ", "ملاحظات"), rows)
            }
            ReportType.MAINTENANCE -> {
                val rows = maintenanceRepo.getAll().map { m ->
                    listOf(siteName(m.siteId), m.type.label, m.nextDue.formatDate(), m.intervalDays.toString())
                }
                ReportData(listOf("الموقع", "النوع", "الاستحقاق", "الفاصل (يوم)"), rows)
            }
            ReportType.POWER -> {
                val powerCategories = setOf(
                    EquipmentCategory.SOLAR,
                    EquipmentCategory.BATTERY,
                    EquipmentCategory.REGULATOR,
                    EquipmentCategory.CHARGER
                )
                val rows = equipmentRepo.getAll()
                    .filter { it.category in powerCategories }
                    .map { listOf(siteName(it.siteId), it.category.label, it.model, it.status.label) }
                ReportData(listOf("الموقع", "التصنيف", "الطراز", "الحالة"), rows)
            }
        }
    }

    /**
     * كتابة التقرير إلى ملف اختاره المستخدم عبر SAF.
     * تُعيد null عند النجاح أو نص سبب الفشل — سابقًا كان أي استثناء يُبتلع
     * ويعود `false` فقط فيرى المستخدم «فشل التصدير» بلا أي تفسير.
     */
    suspend fun exportTo(
        context: Context,
        type: ReportType,
        format: ExportFormat,
        uri: Uri
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val data = buildReport(type)
                if (data.rows.isEmpty()) return@withContext "لا توجد بيانات في هذا التقرير"

                val bytes: ByteArray = when (format) {
                    ExportFormat.CSV ->
                        CsvExporter.build(data.headers, data.rows).toByteArray(Charsets.UTF_8)
                    ExportFormat.EXCEL ->
                        ExcelXmlExporter.build(type.title, data.headers, data.rows).toByteArray(Charsets.UTF_8)
                    ExportFormat.PDF ->
                        PdfReportExporter.build("${type.title} — مجرة", data.headers, data.rows)
                }

                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: return@withContext "تعذّر فتح الملف المحدد للكتابة"
                stream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                null
            } catch (e: Exception) {
                Log.e("ReportsVM", "export failed: $type/$format", e)
                "فشل التصدير: ${e.localizedMessage ?: e.javaClass.simpleName}"
            }
        }

    fun fileName(type: ReportType, format: ExportFormat): String =
        "مجرة-${type.name.lowercase()}.${format.ext}"
}

/** شاشة التقارير: بناء لحظي + تصدير PDF / Excel / CSV */
@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var pending by remember { mutableStateOf<Pair<ReportType, ExportFormat>?>(null) }

    fun doExport(type: ReportType, format: ExportFormat, uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val failure = viewModel.exportTo(context, type, format, uri)
            snackbar.showSnackbar(failure ?: "تم تصدير ${type.title} بصيغة ${format.label}")
        }
    }

    // مشغلات اختيار ملف لكل صيغة
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> pending?.let { doExport(it.first, it.second, uri) }; pending = null }
    val xlsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri -> pending?.let { doExport(it.first, it.second, uri) }; pending = null }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> pending?.let { doExport(it.first, it.second, uri) }; pending = null }

    fun startExport(type: ReportType, format: ExportFormat) {
        pending = type to format
        val name = viewModel.fileName(type, format)
        when (format) {
            ExportFormat.CSV -> csvLauncher.launch(name)
            ExportFormat.EXCEL -> xlsLauncher.launch(name)
            ExportFormat.PDF -> pdfLauncher.launch(name)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        "التقارير",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SectionTitle("تصدير لحظي من قاعدة البيانات — أوفلاين بالكامل")
                }
            }
            items(ReportType.values().toList(), key = { it.name }) { type ->
                GalaxyCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(type.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            type.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { startExport(type, ExportFormat.PDF) }, modifier = Modifier.weight(1f)) {
                                Text("PDF")
                            }
                            OutlinedButton(onClick = { startExport(type, ExportFormat.EXCEL) }, modifier = Modifier.weight(1f)) {
                                Text("Excel")
                            }
                            OutlinedButton(onClick = { startExport(type, ExportFormat.CSV) }, modifier = Modifier.weight(1f)) {
                                Text("CSV")
                            }
                        }
                    }
                }
            }
        }
    }
}
