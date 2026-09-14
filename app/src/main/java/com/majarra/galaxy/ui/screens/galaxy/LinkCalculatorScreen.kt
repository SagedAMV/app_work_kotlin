package com.majarra.galaxy.ui.screens.galaxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.util.RadioMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** نتائج الحسابات اللحظية */
data class CalcResults(
    val fsplDb: Double,
    val receivedDbm: Double,
    val marginDb: Double,
    val viable: Boolean,
    val fresnelM: Double,
    val losClear: Boolean,
    val losMarginM: Double
)

@HiltViewModel
class LinkCalculatorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val linkRepo: LinkRepository
) : ViewModel() {

    val linkId: Long = savedStateHandle.get<Long>("linkId") ?: -1L

    // مدخلات الحاسبة — قيم افتراضية واقعية
    var txPower by mutableStateOf("23")
    var txGain by mutableStateOf("30")
    var rxGain by mutableStateOf("30")
    var cableLoss by mutableStateOf("2")
    var freqMHz by mutableStateOf("5800")
    var distanceKm by mutableStateOf("10")
    var txHeight by mutableStateOf("35")
    var rxHeight by mutableStateOf("35")
    var obstacleHeight by mutableStateOf("0")
    var obstaclePosKm by mutableStateOf("5")
    var rxSensitivity by mutableStateOf("-75")

    init {
        // تعبئة تلقائية من رابط قائم إن فُتحت الحاسبة منه
        if (linkId > 0) {
            viewModelScope.launch {
                linkRepo.getById(linkId)?.let { l ->
                    if (l.frequencyMHz > 0) freqMHz = l.frequencyMHz.toString()
                    if (l.distanceKm > 0) {
                        distanceKm = "%.2f".format(l.distanceKm)
                        obstaclePosKm = "%.2f".format(l.distanceKm / 2)
                    }
                }
            }
        }
    }

    /** الحساب اللحظي — يرجع null إن كانت أي قيمة غير صالحة */
    fun calculate(): CalcResults? {
        val tx = txPower.toDoubleOrNull() ?: return null
        val tg = txGain.toDoubleOrNull() ?: return null
        val rg = rxGain.toDoubleOrNull() ?: return null
        val cl = cableLoss.toDoubleOrNull() ?: return null
        val f = freqMHz.toDoubleOrNull() ?: return null
        val d = distanceKm.toDoubleOrNull() ?: return null
        val h1 = txHeight.toDoubleOrNull() ?: return null
        val h2 = rxHeight.toDoubleOrNull() ?: return null
        val oh = obstacleHeight.toDoubleOrNull() ?: return null
        val d1 = obstaclePosKm.toDoubleOrNull() ?: return null
        val sens = rxSensitivity.toDoubleOrNull() ?: return null
        if (f <= 0 || d <= 0 || d1 <= 0 || d1 >= d) return null

        val budget = RadioMath.linkBudget(tx, tg, rg, cl, d, f, sens)
        val d2 = d - d1
        val los = RadioMath.lineOfSight(h1, h2, oh, d1, d2)
        val fresnel = RadioMath.fresnelRadiusM(d1, d2, f)

        return CalcResults(
            fsplDb = budget.fsplDb,
            receivedDbm = budget.receivedPowerDbm,
            marginDb = budget.marginDb,
            viable = budget.viable,
            fresnelM = fresnel,
            losClear = los.clear,
            losMarginM = los.marginM
        )
    }
}

/** حاسبة موازنة الرابط + خط النظر + فرينل — القسم 6 من التعليمات */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCalculatorScreen(
    linkId: Long,
    onBack: () -> Unit,
    viewModel: LinkCalculatorViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (linkId > 0) "حاسبة الرابط" else "حاسبة جديدة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("معاملات الإرسال")
            PairRow("قدرة الإرسال dBm", viewModel.txPower) { viewModel.txPower = it }
            PairRow("كسب هوائي الإرسال dBi", viewModel.txGain) { viewModel.txGain = it }
            PairRow("كسب هوائي الاستقبال dBi", viewModel.rxGain) { viewModel.rxGain = it }
            PairRow("فقد الكابل dB", viewModel.cableLoss) { viewModel.cableLoss = it }

            SectionTitle("المسار")
            PairRow("التردد ميجاهرتز", viewModel.freqMHz) { viewModel.freqMHz = it }
            PairRow("المسافة كم", viewModel.distanceKm) { viewModel.distanceKm = it }
            PairRow("ارتفاع الإرسال م", viewModel.txHeight) { viewModel.txHeight = it }
            PairRow("ارتفاع الاستقبال م", viewModel.rxHeight) { viewModel.rxHeight = it }
            PairRow("ارتفاع العائق م", viewModel.obstacleHeight) { viewModel.obstacleHeight = it }
            PairRow("موقع العائق كم", viewModel.obstaclePosKm) { viewModel.obstaclePosKm = it }

            SectionTitle("المستقبل")
            OutlinedTextField(
                value = viewModel.rxSensitivity,
                onValueChange = { viewModel.rxSensitivity = it },
                label = { Text("حساسية المستقبل dBm") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // النتائج — تُعاد لحظيًا عند كل إعادة تركيب
            val results = viewModel.calculate()
            if (results == null) {
                GalaxyCard {
                    Text(
                        "أدخل قيمًا صالحة: التردد والمسافة موجبان، وموقع العائق بين الطرفين.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                SectionTitle("النتائج")
                GalaxyCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultRow("فقد المسار الحر (FSPL)", "%.2f dB".format(results.fsplDb), MaterialTheme.colorScheme.onSurface)
                        ResultRow("القدرة المستلمة", "%.2f dBm".format(results.receivedDbm), if (results.viable) NeonGreen else DangerRed)
                        ResultRow("الهامش فوق الحساسية", "%+.2f dB".format(results.marginDb), if (results.viable) NeonGreen else DangerRed)
                        ResultRow(
                            "حكم موازنة الرابط",
                            if (results.viable) "الرابط ممكن" else "الرابط غير ممكن",
                            if (results.viable) NeonGreen else DangerRed
                        )
                        ResultRow("نصف قطر فرينل (منتصف المسار)", "%.2f م".format(results.fresnelM), MaterialTheme.colorScheme.onSurface)
                        ResultRow(
                            "خط النظر LOS",
                            if (results.losClear) "صافٍ" else "محجوب",
                            if (results.losClear) NeonGreen else DangerRed
                        )
                        ResultRow("هامش خط النظر", "%+.2f م".format(results.losMarginM), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** صفّا حقول متجاوران لتقليل التمرير */
@Composable
private fun PairRow(
    label1: String,
    value1: String,
    pair: (@Composable () -> Unit)? = null,
    onValue1: (String) -> Unit = {}
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value1,
            onValueChange = onValue1,
            label = { Text(label1) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        if (pair != null) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { pair() }
        }
    }
}

/** صف نتيجة */
@Composable
private fun ResultRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}
