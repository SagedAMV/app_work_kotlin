package com.majarra.galaxy.ui.screens.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Alert
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.domain.model.SiteStatus
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.repository.AlertRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import android.util.Log
import com.majarra.galaxy.domain.usecase.CheckAlertsUseCase
import com.majarra.galaxy.domain.usecase.ClearAlertsUseCase
import com.majarra.galaxy.domain.usecase.DismissAlertUseCase
import com.majarra.galaxy.domain.usecase.MarkAlertReadUseCase
import com.majarra.galaxy.domain.usecase.MarkAllAlertsReadUseCase
import com.majarra.galaxy.notify.AlertNotifier
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.theme.GalaxyColors
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.StatCard
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.SkyBlue
import com.majarra.galaxy.ui.theme.WarnAmber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** إحصائيات اللوحة الرئيسية */
data class DashStats(
    val siteCount: Int = 0,
    val activeSites: Int = 0,
    val downSites: Int = 0,
    val openTickets: Int = 0,
    val activeLinks: Int = 0,
    val downLinks: Int = 0,
    val totalLinks: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    siteRepo: SiteRepository,
    ticketRepo: TicketRepository,
    linkRepo: LinkRepository,
    alertRepo: AlertRepository,
    private val checkAlerts: CheckAlertsUseCase,
    private val markRead: MarkAlertReadUseCase,
    private val markAllRead: MarkAllAlertsReadUseCase,
    private val dismissAlert: DismissAlertUseCase,
    private val clearAlerts: ClearAlertsUseCase,
    private val notifier: AlertNotifier
) : ViewModel() {

    /** الإحصائيات تُحسب لحظيًا من المصادر الثلاثة — قاعدة العمل رقم 8 */
    val stats = combine(
        siteRepo.observeSites(),
        ticketRepo.observeAll(),
        linkRepo.observeAll()
    ) { sites, tickets, links ->
        DashStats(
            siteCount = sites.size,
            activeSites = sites.count { it.status == SiteStatus.ACTIVE },
            downSites = sites.count { it.status == SiteStatus.DOWN },
            openTickets = tickets.count { it.status != TicketStatus.CLOSED },
            activeLinks = links.count { it.status == LinkStatus.ACTIVE },
            downLinks = links.count { it.status == LinkStatus.DOWN },
            totalLinks = links.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashStats())

    val alerts = alertRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount = alertRepo.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _checking = MutableStateFlow(false)
    val checking = _checking

    /** رسالة الفحص اليدوي (العدد المُنشأ أو سبب الفشل الحقيقي) */
    private val _status = MutableStateFlow<String?>(null)
    val status = _status

    fun checkNow() {
        viewModelScope.launch {
            _checking.value = true
            _status.value = try {
                val created = checkAlerts()
                if (created > 0) {
                    notifier.notifyNewAlerts(created, unreadCount.value)
                    "تم إنشاء $created تنبيه جديد"
                } else {
                    "لا توجد تنبيهات جديدة"
                }
            } catch (t: Throwable) {
                // لا نُخفي السبب: runCatching سابقًا كان يبتلع الخطأ ويترك الزر بلا نتيجة
                Log.e("DashboardVM", "alert check failed", t)
                "فشل الفحص: ${t.localizedMessage ?: t.javaClass.simpleName}"
            }
            _checking.value = false
        }
    }

    fun dismissStatus() { _status.value = null }

    fun read(alert: Alert) = viewModelScope.launch { markRead(alert.id) }
    fun readAll() = viewModelScope.launch { markAllRead() }
    fun dismiss(alert: Alert) = viewModelScope.launch { dismissAlert(alert.id) }
    fun clearAll() = viewModelScope.launch { clearAlerts() }
}

/** لوحة المؤشرات — الشاشة الرئيسية */
@Composable
fun DashboardScreen(
    onOpenSites: () -> Unit,
    onOpenGalaxy: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var confirmClearAlerts by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("مجرة", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "نظام إدارة مواقع الاتصالات",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // شبكة المؤشرات — بطاقة المواقع تفتح شاشة المواقع
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "المواقع",
                    stats.siteCount.toString(),
                    SkyBlue,
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenSites)
                )
                StatCard("أعطال مفتوحة", stats.openTickets.toString(), DangerRed, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("تنبيهات غير مقروءة", unread.toString(), WarnAmber, Modifier.weight(1f))
                StatCard("روابط نشطة", "${stats.activeLinks}/${stats.totalLinks}", NeonGreen, Modifier.weight(1f))
            }
        }

        // حالة الشبكة (المجرة) — شريط نسب لحظي
        item {
            GalaxyCard(onClick = onOpenGalaxy) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("حالة الشبكة")
                    NetworkHealthBar(
                        active = stats.activeLinks,
                        degraded = stats.totalLinks - stats.activeLinks - stats.downLinks,
                        down = stats.downLinks,
                        total = stats.totalLinks
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("مواقع معطلة: ${stats.downSites}", style = MaterialTheme.typography.bodySmall, color = DangerRed)
                        Text("مواقع نشطة: ${stats.activeSites}", style = MaterialTheme.typography.bodySmall, color = NeonGreen)
                    }
                }
            }
        }

        // التنبيهات الأخيرة
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionTitle("أحدث التنبيهات")
                    Button(onClick = viewModel::checkNow, enabled = !checking) {
                        Text(if (checking) "جارٍ الفحص…" else "افحص الآن")
                    }
                }
                if (unread > 0 || alerts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (unread > 0) {
                            TextButton(onClick = viewModel::readAll) { Text("تحديد الكل كمقروء") }
                        }
                        TextButton(onClick = { confirmClearAlerts = true }) {
                            Text("مسح التنبيهات", color = DangerRed)
                        }
                    }
                }
            }
        }
        if (alerts.isEmpty()) {
            item {
                Text(
                    "لا توجد تنبيهات بعد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // مفتاح العنصر يمنع إعادة استخدام صف خاطئ عند تغيّر القائمة (قراءة/حذف)
        items(alerts.take(6), key = { it.id }) { alert ->
            GalaxyCard(onClick = { viewModel.read(alert) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(alert.type.label, style = MaterialTheme.typography.labelMedium, color = WarnAmber)
                        Text(
                            alert.createdAt.formatDateTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                    if (!alert.isRead) {
                        Text("● غير مقروء", color = SkyBlue, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { viewModel.dismiss(alert) }) { Text("تجاهل") }
                }
            }
        }
    }

    // نتيجة الفحص اليدوي (عدد التنبيهات المُنشأة أو سبب الفشل)
    status?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissStatus,
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissStatus) { Text("حسنًا") }
            }
        )
    }

    if (confirmClearAlerts) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClearAlerts = false },
            title = { Text("مسح كل التنبيهات") },
            text = { Text("سيتم حذف كل التنبيهات نهائيًا. هل تريد المتابعة؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAlerts = false
                    viewModel.clearAll()
                }) { Text("مسح", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAlerts = false }) { Text("إلغاء") }
            }
        )
    }
}

/** شريط صحة الشبكة — أنيميشن ناعم عند تغيّر النسب */
@Composable
private fun NetworkHealthBar(active: Int, degraded: Int, down: Int, total: Int) {
    val safeTotal = total.coerceAtLeast(1)
    val activeF by animateFloatAsState(active.toFloat() / safeTotal, label = "active")
    val degradedF by animateFloatAsState(degraded.toFloat() / safeTotal, label = "degraded")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(50))
    ) {
        Segment(weight = activeF, color = NeonGreen)
        Segment(weight = degradedF, color = WarnAmber)
        Segment(weight = (1f - activeF - degradedF).coerceAtLeast(0f), color = DangerRed)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(weight: Float, color: Color) {
    if (weight > 0f) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(weight)
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}
