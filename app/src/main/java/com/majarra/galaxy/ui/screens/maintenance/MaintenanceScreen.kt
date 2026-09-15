package com.majarra.galaxy.ui.screens.maintenance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Equipment
import com.majarra.galaxy.data.local.MaintenanceSchedule
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.data.local.Ticket
import com.majarra.galaxy.data.local.WorkOrder
import com.majarra.galaxy.domain.model.TicketSeverity
import com.majarra.galaxy.domain.model.TicketStatus
import com.majarra.galaxy.domain.model.MaintenanceType
import com.majarra.galaxy.domain.model.WorkOrderStatus
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.EquipmentRepository
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
import com.majarra.galaxy.ui.theme.GalaxyColors
import com.majarra.galaxy.ui.components.StatusChip
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.ui.components.formatDurationMinutes
import com.majarra.galaxy.util.daysFromNow
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.WarnAmber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val workOrderRepo: WorkOrderRepository,
    private val maintenanceRepo: MaintenanceRepository,
    private val equipmentRepo: EquipmentRepository,
    private val openTicket: OpenTicketUseCase,
    private val startTicket: StartTicketUseCase,
    private val closeTicket: CloseTicketUseCase,
    private val saveWorkOrder: SaveWorkOrderUseCase,
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

    /** معدات الموقع المختار لجدولة الصيانة الوقائية */
    private val _equipment = MutableStateFlow<List<Equipment>>(emptyList())
    val equipment: StateFlow<List<Equipment>> = _equipment

    fun loadEquipment(siteId: Long) {
        viewModelScope.launch {
            _equipment.value = if (siteId > 0) equipmentRepo.getBySite(siteId) else emptyList()
        }
    }

    /** جدولة صيانة وقائية — كان مسار الإضافة في المستودع غير مستخدم إطلاقًا */
    fun addSchedule(
        siteId: Long,
        equipmentId: Long,
        type: MaintenanceType,
        intervalDays: Int,
        onDone: (String?) -> Unit
    ) {
        if (siteId <= 0) {
            onDone("اختر موقعًا")
            return
        }
        if (equipmentId <= 0) {
            onDone("اختر معدة من الموقع")
            return
        }
        if (intervalDays !in 1..MAX_INTERVAL_DAYS) {
            onDone("الفاصل الزمني يجب أن يكون بين 1 و $MAX_INTERVAL_DAYS يومًا")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = maintenanceRepo.insert(
                MaintenanceSchedule(
                    siteId = siteId,
                    equipmentId = equipmentId,
                    type = type,
                    intervalDays = intervalDays,
                    lastDone = now,
                    nextDue = now + intervalDays * DAY_MS
                )
            )
            auditRepo.log("CREATE", "MaintenanceSchedule", id, "كل $intervalDays يومًا — ${type.label}")
            onDone(null)
        }
    }

    /** حذف أمر شغل (يُستخدم مسار الحذف في المستودع) */
    fun deleteWorkOrder(order: WorkOrder) {
        viewModelScope.launch {
            workOrderRepo.delete(order)
            auditRepo.log("DELETE", "WorkOrder", order.id, order.assignedTo)
        }
    }

    private companion object {
        const val DAY_MS = 24L * 3600 * 1000
        const val MAX_INTERVAL_DAYS = 3650
    }

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
            // الفاصل يجب ألا يكون صفريًا/سالبًا وإلا صار الجدول مستحقًا في نفس اللحظة
            val interval = schedule.intervalDays.coerceIn(1, MAX_INTERVAL_DAYS)
            val nextDue = now + interval * DAY_MS
            maintenanceRepo.update(
                schedule.copy(intervalDays = interval, lastDone = now, nextDue = nextDue)
            )
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
                    FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.label) })
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
    val tickets by viewModel.tickets.collectAsStateWithLifecycle()
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
                            "زمن المعالجة: ${(t.resolutionTimeMinutes ?: 0L).formatDurationMinutes()}",
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
    val orders by viewModel.workOrders.collectAsStateWithLifecycle()
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
                    // حذف أمر الشغل — يمنع تراكم صفوف معلّقة بلا إمكانية إدارة
                    var confirmDeleteOrder by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirmDeleteOrder = true }) {
                        Text("حذف الأمر", color = DangerRed)
                    }
                    if (confirmDeleteOrder) {
                        AlertDialog(
                            onDismissRequest = { confirmDeleteOrder = false },
                            title = { Text("حذف أمر الشغل") },
                            text = { Text("سيتم حذف أمر شغل «${w.assignedTo}». هل أنت متأكد؟") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDeleteOrder = false
                                    viewModel.deleteWorkOrder(w)
                                }) { Text("حذف", color = DangerRed) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDeleteOrder = false }) { Text("إلغاء") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreventiveTab(viewModel: MaintenanceViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val weekMs = 7L * 24 * 3600 * 1000

    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "جدولة صيانة")
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (schedules.isEmpty()) {
                EmptyState(
                    Icons.Filled.Build,
                    "لا جداول صيانة وقائية",
                    "أضف جدولًا من زر الجدولة أسفل الشاشة"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(schedules, key = { it.id }) { item ->
                        MaintenanceCard(viewModel, item, now, weekMs)
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewScheduleDialog(
            viewModel = viewModel,
            onDismiss = { showAdd = false },
            onDone = { error ->
                showAdd = false
                scope.launch { snackbar.showSnackbar(error ?: "تمت جدولة الصيانة الوقائية") }
            }
        )
    }
}

/** بطاقة جدول صيانة واحد — تُظهر المتأخر والمستحق والمدة المتبقية */
@Composable
private fun MaintenanceCard(
    viewModel: MaintenanceViewModel,
    schedule: MaintenanceSchedule,
    now: Long,
    weekMs: Long
) {
    val overdue = schedule.nextDue <= now
    val soon = !overdue && schedule.nextDue <= now + weekMs
    val days = schedule.nextDue.daysFromNow(now)
    GalaxyCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${schedule.type.label} — ${viewModel.siteName(schedule.siteId)}",
                    style = MaterialTheme.typography.titleSmall
                )
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
                "الاستحقاق: ${schedule.nextDue.formatDateTime()} • كل ${schedule.intervalDays} يومًا",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (overdue) "متأخرة ${-days} يومًا" else "متبقٍ $days يومًا",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { viewModel.markMaintenanceDone(schedule) }) {
                Text("تمت الصيانة اليوم")
            }
        }
    }
}

/** حوار جدولة صيانة وقائية لمعدة في موقع */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewScheduleDialog(
    viewModel: MaintenanceViewModel,
    onDismiss: () -> Unit,
    onDone: (String?) -> Unit
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val equipment by viewModel.equipment.collectAsStateWithLifecycle()
    var siteId by remember { mutableStateOf(0L) }
    var equipmentId by remember { mutableStateOf(0L) }
    var type by remember { mutableStateOf(MaintenanceType.PREVENTIVE) }
    var intervalText by remember { mutableStateOf("90") }
    var siteMenu by remember { mutableStateOf(false) }
    var equipmentMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جدولة صيانة وقائية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = siteMenu, onExpandedChange = { siteMenu = it }) {
                    OutlinedTextField(
                        value = sites.firstOrNull { it.id == siteId }?.name ?: "اختر موقعًا",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("الموقع") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = siteMenu, onDismissRequest = { siteMenu = false }) {
                        sites.forEach { site ->
                            DropdownMenuItem(
                                text = { Text(site.name) },
                                onClick = {
                                    siteId = site.id
                                    equipmentId = 0L
                                    viewModel.loadEquipment(site.id)
                                    siteMenu = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = equipmentMenu, onExpandedChange = { equipmentMenu = it }) {
                    OutlinedTextField(
                        value = equipment.firstOrNull { it.id == equipmentId }
                            ?.let { "${it.category.label} ${it.model}" } ?: "اختر معدة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المعدة") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = equipmentMenu, onDismissRequest = { equipmentMenu = false }) {
                        equipment.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.category.label} — ${item.model}") },
                                onClick = {
                                    equipmentId = item.id
                                    equipmentMenu = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaintenanceType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(4) },
                    label = { Text("كل كم يومًا؟") },
                    singleLine = true
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val interval = intervalText.toIntOrNull()
                if (interval == null) {
                    error = "أدخل فاصلًا رقميًا صحيحًا"
                } else {
                    viewModel.addSchedule(siteId, equipmentId, type, interval) { message ->
                        if (message == null) onDone(null) else error = message
                    }
                }
            }) { Text("جدولة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** حوار تذكرة جديدة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTicketDialog(viewModel: MaintenanceViewModel, onDismiss: () -> Unit) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
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
                        FilterChip(selected = severity == s, onClick = { severity = s }, label = { Text(s.label) })
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
    val sites by viewModel.sites.collectAsStateWithLifecycle()
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
)
}
