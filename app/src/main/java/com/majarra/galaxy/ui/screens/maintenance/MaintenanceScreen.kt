package com.majarra.galaxy.ui.screens.maintenance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.MaintenanceSchedule
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.WorkOrder
import com.majarra.galaxy.domain.model.TicketSeverity
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.model.WorkOrderStatus
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.MaintenanceRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.repository.TicketRepository
import com.majarra.galaxy.domain.repository.WorkOrderRepository
import com.majarra.galaxy.domain.usecase.CloseTicketUseCase
import com.majarra.galaxy.domain.usecase.OpenTicketUseCase
import com.majarra.galaxy.domain.usecase.SaveWorkOrderUseCase
import com.majarra.galaxy.domain.usecase.StartTicketUseCase
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.GalaxyColors
import com.majarra.galaxy.ui.components.StatusChip
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.WarnAmber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class MaintTab(val label: String) {
    TICKETS("التذاكر"),
    WORK_ORDERS("أوامر الشغل"),
    PREVENTIVE("الوقائية")
}

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    siteRepo: SiteRepository,
    ticketRepo: TicketRepository,
    workOrderRepo: WorkOrderRepository,
    maintenanceRepo: MaintenanceRepository,
    private val openTicket: OpenTicketUseCase,
    private val startTicket: StartTicketUseCase,
    private val closeTicket: CloseTicketUseCase,
    private val saveWorkOrder: SaveWorkOrderUseCase,
    private val maintenanceRepository: MaintenanceRepository,
    private val auditRepo: AuditRepository
) : ViewModel() {

    val sites: StateFlow<List<Site>> = siteRepo.observeSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tickets: StateFlow<List<Ticket>> = ticketRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val workOrders: StateFlow<List<WorkOrder>> = workOrderRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedules: StateFlow<List<MaintenanceSchedule>> = maintenanceRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun siteName(id: Long): String = sites.value.find { it.id == id }?.name ?: "موقع $id"

    fun openNewTicket(siteId: Long, title: String, description: String, severity: TicketSeverity, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                openTicket(
                    Ticket(siteId = siteId, title = title, description = description, severity = severity)
                )
                onDone(null)
            } catch (e: IllegalArgumentException) {
                onDone(e.message)
            }
        }
    }

    fun start(id: Long) = viewModelScope.launch { startTicket(id) }

    /** الإغلاق يسجل زمن المعالجة تلقائيًا — قاعدة العمل رقم 2 */
    fun close(id: Long, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val ok = closeTicket(id)
            onDone(if (ok) "أُغلقت التذكرة وسُجل زمن المعالجة" else "تعذر إغلاق التذكرة")
        }
    }

    fun addWorkOrder(siteId: Long, assignedTo: String, tasks: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                saveWorkOrder(WorkOrder(siteId = siteId, assignedTo = assignedTo, tasks = tasks))
                onDone(null)
            } catch (e: IllegalArgumentException) {
                onDone(e.message)
            }
        }
    }

    fun advanceWorkOrder(order: WorkOrder) {
        viewModelScope.launch {
            val next = when (order.status) {
                WorkOrderStatus.SCHEDULED -> order.copy(status = WorkOrderStatus.IN_PROGRESS)
                WorkOrderStatus.IN_PROGRESS -> order.copy(status = WorkOrderStatus.DONE)
                else -> return@launch
            }
            saveWorkOrder(next)
        }
    }

    /** تمت الصيانة اليوم: تحديث آخر تنفيذ والموعد القادم */
    fun markMaintenanceDone(schedule: MaintenanceSchedule) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextDue = now + schedule.intervalDays * 24L * 3600 * 1000
            maintenanceRepository.update(schedule.copy(lastDone = now, nextDue = nextDue))
            auditRepo.log("MAINTENANCE", "MaintenanceSchedule", schedule.id, "تمت ${schedule.type.label}")
        }
    }
}

/** شاشة الصيانة والأعطال: تذاكر + أوامر شغل + صيانة وقائية */
@Composable
fun MaintenanceScreen(viewModel: MaintenanceViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf(MaintTab.TICKETS) }
    var showNewTicket by remember { mutableStateOf(false) }
    var showNewOrder by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab != MaintTab.PREVENTIVE) {
                FloatingActionButton(
                    onClick = {
                        if (tab == MaintTab.TICKETS) showNewTicket = true else showNewOrder = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "جديد")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "الصيانة والأعطال",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaintTab.values().forEach { t ->
                    AssistChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
                }
            }

            when (tab) {
                MaintTab.TICKETS -> TicketsTab(viewModel, snackbar)
                MaintTab.WORK_ORDERS -> WorkOrdersTab(viewModel)
                MaintTab.PREVENTIVE -> PreventiveTab(viewModel)
            }
        }
    }

    if (showNewTicket) NewTicketDialog(viewModel) { showNewTicket = false }
    if (showNewOrder) NewWorkOrderDialog(viewModel) { showNewOrder = false }
}

@Composable
private fun TicketsTab(viewModel: MaintenanceViewModel, snackbar: SnackbarHostState) {
    val tickets by viewModel.tickets.collectAsState()
    if (tickets.isEmpty()) {
        EmptyState(Icons.Filled.Build, "لا توجد تذاكر", "افتح تذكرة عطل جديدة من الزر العائم")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tickets, key = { it.id }) { t ->
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusChip(t.severity.label, GalaxyColors.severityColor(t.severity))
                    }
                    Text(
                        "${viewModel.siteName(t.siteId)} • ${t.status.label} • فُتحت ${t.openedAt.formatDateTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (t.status == TicketStatus.CLOSED) {
                        Text(
                            "زمن المعالجة: ${t.resolutionTimeMinutes ?: 0} دقيقة",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (t.status == TicketStatus.OPEN) {
                                Button(onClick = { viewModel.start(t.id) }) { Text("بدء المعالجة") }
                            }
                            TextButton(onClick = {
                                viewModel.close(t.id) { msg ->
                                    viewModel.viewModelScope.launch { snackbar.showSnackbar(msg) }
                                }
                            }) { Text("إغلاق التذكرة") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkOrdersTab(viewModel: MaintenanceViewModel) {
    val orders by viewModel.workOrders.collectAsState()
    if (orders.isEmpty()) {
        EmptyState(Icons.Filled.Build, "لا توجد أوامر شغل", "أنشئ أمر شغل جديدًا")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(orders, key = { it.id }) { w ->
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(viewModel.siteName(w.siteId), style = MaterialTheme.typography.titleSmall)
                        StatusChip(
                            w.status.label,
                            when (w.status) {
                                WorkOrderStatus.DONE -> NeonGreen
                                WorkOrderStatus.IN_PROGRESS -> WarnAmber
                                WorkOrderStatus.SCHEDULED -> com.majarra.galaxy.ui.theme.SkyBlue
                                WorkOrderStatus.CANCELLED -> DangerRed
                            }
                        )
                    }
                    Text("المنفذ: ${w.assignedTo}", style = MaterialTheme.typography.bodySmall)
                    w.tasks.split("\n").forEach { task ->
                        if (task.isNotBlank()) Text("• $task", style = MaterialTheme.typography.bodySmall)
                    }
                    if (w.status == WorkOrderStatus.SCHEDULED || w.status == WorkOrderStatus.IN_PROGRESS) {
                        Button(onClick = { viewModel.advanceWorkOrder(w) }) {
                            Text(if (w.status == WorkOrderStatus.SCHEDULED) "بدء التنفيذ" else "إتمام")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreventiveTab(viewModel: MaintenanceViewModel) {
    val schedules by viewModel.schedules.collectAsState()
    val now = System.currentTimeMillis()
    val weekMs = 7L * 24 * 3600 * 1000

    if (schedules.isEmpty()) {
        EmptyState(Icons.Filled.Build, "لا جداول صيانة وقائية")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(schedules, key = { it.id }) { s ->
            val overdue = s.nextDue <= now
            val soon = !overdue && s.nextDue <= now + weekMs
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${s.type.label} — ${viewModel.siteName(s.siteId)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when {
                                overdue -> "متأخرة!"
                                soon -> "خلال 7 أيام"
                                else -> "مجدولة"
                            },
                            color = when {
                                overdue -> DangerRed
                                soon -> WarnAmber
                                else -> NeonGreen
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Text(
                        "الاستحقاق: ${s.nextDue.formatDateTime()} • كل ${s.intervalDays} يومًا",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { viewModel.markMaintenanceDone(s) }) {
                        Text("تمت الصيانة اليوم")
                    }
                }
            }
        }
    }
}

/** حوار تذكرة جديدة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTicketDialog(viewModel: MaintenanceViewModel, onDismiss: () -> Unit) {
    val sites by viewModel.sites.collectAsState()
    var site by remember { mutableStateOf<Site?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(TicketSeverity.MEDIUM) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تذكرة عطل جديدة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = site?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الموقع") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sites.forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${s.name} (${s.code})") },
                                onClick = {
                                    site = s
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("العنوان") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("الوصف") })
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TicketSeverity.values().forEach { s ->
                        AssistChip(selected = severity == s, onClick = { severity = s }, label = { Text(s.label) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = site
                when {
                    s == null -> error = "اختر موقعًا"
                    title.isBlank() -> error = "العنوان مطلوب"
                    else -> viewModel.openNewTicket(s.id, title.trim(), description.trim(), severity) { msg ->
                        if (msg == null) onDismiss() else error = msg
                    }
                }
            }) { Text("فتح التذكرة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** حوار أمر شغل جديد */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewWorkOrderDialog(viewModel: MaintenanceViewModel, onDismiss: () -> Unit) {
    val sites by viewModel.sites.collectAsState()
    var site by remember { mutableStateOf<Site?>(null) }
    var assignedTo by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أمر شغل جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = site?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الموقع") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sites.forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${s.name} (${s.code})") },
                                onClick = {
                                    site = s
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = assignedTo, onValueChange = { assignedTo = it }, label = { Text("المنفذ") }, singleLine = true)
                OutlinedTextField(
                    value = tasks,
                    onValueChange = { tasks = it },
                    label = { Text("المهام — سطر لكل مهمة") },
                    minLines = 3
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = site
                when {
                    s == null -> error = "اختر موقعًا"
                    assignedTo.isBlank() -> error = "اسم المنفذ مطلوب"
                    tasks.isBlank() -> error = "اكتب مهمة واحدة على الأقل"
                    else -> viewModel.addWorkOrder(s.id, assignedTo.trim(), tasks.trim()) { msg ->
                        if (msg == null) onDismiss() else error = msg
                    }
                }
            }) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    }
}
