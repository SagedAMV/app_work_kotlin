package com.majarra.galaxy.ui.screens.galaxy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Link
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.model.LinkPriority
import com.majarra.galaxy.domain.model.LinkStatus
import com.majarra.galaxy.domain.model.LinkType
import com.majarra.galaxy.domain.model.NetworkClass
import com.majarra.galaxy.domain.repository.AuditRepository
import com.majarra.galaxy.domain.repository.LinkRepository
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.theme.GalaxyColors
import com.majarra.galaxy.ui.components.StatusChip
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.ui.theme.NeonGreen
import com.majarra.galaxy.ui.theme.SkyBlue
import com.majarra.galaxy.ui.theme.WarnAmber
import com.majarra.galaxy.util.RadioMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalaxyViewModel @Inject constructor(
    siteRepo: SiteRepository,
    linkRepo: LinkRepository,
    private val linkRepository: LinkRepository,
    private val auditRepo: AuditRepository
) : ViewModel() {

    val sites: StateFlow<List<Site>> = siteRepo.observeSites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allLinks: StateFlow<List<Link>> = linkRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // فلاتر العرض: نوع الرابط، تصنيف الشبكة، الحالة
    private val _typeFilter = MutableStateFlow<Set<LinkType>>(emptySet())
    private val _classFilter = MutableStateFlow<Set<NetworkClass>>(emptySet())
    private val _statusFilter = MutableStateFlow<LinkStatus?>(null)
    val typeFilter: StateFlow<Set<LinkType>> = _typeFilter
    val classFilter: StateFlow<Set<NetworkClass>> = _classFilter
    val statusFilter: StateFlow<LinkStatus?> = _statusFilter

    /** الروابط بعد الفلترة — قاعدة العمل رقم 8: الحالة اللحظية */
    val visibleLinks: StateFlow<List<Link>> =
        combine(allLinks, _typeFilter, _classFilter, _statusFilter) { links, t, c, s ->
            links.filter {
                (t.isEmpty() || it.type in t) &&
                    (c.isEmpty() || it.networkClass in c) &&
                    (s == null || it.status == s)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSiteId = MutableStateFlow<Long?>(null)
    private val _selectedLinkId = MutableStateFlow<Long?>(null)

    val selectedSite: StateFlow<Site?> = combine(_selectedSiteId, sites) { id, list ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedLink: StateFlow<Link?> = combine(_selectedLinkId, allLinks) { id, list ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var is3D by androidx.compose.runtime.mutableStateOf(false)

    fun selectSite(id: Long) {
        _selectedSiteId.value = id
        _selectedLinkId.value = null
    }

    fun selectLink(id: Long) {
        _selectedLinkId.value = id
        _selectedSiteId.value = null
    }

    fun clearSelection() {
        _selectedSiteId.value = null
        _selectedLinkId.value = null
    }

    fun toggleType(t: LinkType) {
        _typeFilter.value = if (t in _typeFilter.value) _typeFilter.value - t else _typeFilter.value + t
    }

    fun toggleClass(c: NetworkClass) {
        _classFilter.value = if (c in _classFilter.value) _classFilter.value - c else _classFilter.value + c
    }

    fun toggleStatus(s: LinkStatus) {
        _statusFilter.value = if (_statusFilter.value == s) null else s
    }

    /** إضافة رابط جديد — المسافة تُحسب تلقائيًا من إحداثيات الموقعين */
    fun addLink(
        sourceId: Long,
        targetId: Long,
        type: LinkType,
        networkClass: NetworkClass,
        priority: LinkPriority,
        frequencyMHz: Double
    ) {
        val src = sites.value.find { it.id == sourceId } ?: return
        val dst = sites.value.find { it.id == targetId } ?: return
        if (sourceId == targetId) return
        val distance = RadioMath.haversineKm(src.latitude, src.longitude, dst.latitude, dst.longitude)
        viewModelScope.launch {
            val id = linkRepository.insert(
                Link(
                    sourceSiteId = sourceId,
                    targetSiteId = targetId,
                    type = type,
                    networkClass = networkClass,
                    priority = priority,
                    frequencyMHz = frequencyMHz,
                    distanceKm = distance
                )
            )
            auditRepo.log("CREATE", "Link", id, "${src.name} ↔ ${dst.name}")
        }
    }

    /** تدوير حالة الرابط: نشط ← معطل ← متدهور ← نشط */
    fun cycleLinkStatus(link: Link) {
        val next = when (link.status) {
            LinkStatus.ACTIVE -> LinkStatus.DOWN
            LinkStatus.DOWN -> LinkStatus.DEGRADED
            LinkStatus.DEGRADED -> LinkStatus.ACTIVE
            LinkStatus.PLANNED -> LinkStatus.ACTIVE
        }
        viewModelScope.launch {
            linkRepository.update(link.copy(status = next))
            auditRepo.log("UPDATE", "Link", link.id, "الحالة: ${next.label}")
        }
    }

    fun deleteLink(link: Link) {
        viewModelScope.launch {
            linkRepository.delete(link)
            auditRepo.log("DELETE", "Link", link.id, link.type.label)
            _selectedLinkId.value = null
        }
    }
}

/** شاشة المجرة — قلب التطبيق البصري */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyScreen(
    onOpenSite: (Long) -> Unit,
    onOpenCalculator: (Long?) -> Unit,
    viewModel: GalaxyViewModel = hiltViewModel()
) {
    val sites by viewModel.sites.collectAsState()
    val links by viewModel.visibleLinks.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val classFilter by viewModel.classFilter.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val selectedSite by viewModel.selectedSite.collectAsState()
    val selectedLink by viewModel.selectedLink.collectAsState()

    var showAddLink by remember { mutableStateOf(false) }
    var linkToDelete by remember { mutableStateOf<Link?>(null) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddLink = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة رابط")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // الرأس: العنوان + وضع العرض + الحاسبة
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("المجرة", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !viewModel.is3D,
                        onClick = { viewModel.is3D = false },
                        label = { Text("2D") }
                    )
                    FilterChip(
                        selected = viewModel.is3D,
                        onClick = { viewModel.is3D = true },
                        label = { Text("3D") }
                    )
                    IconButton(onClick = { onOpenCalculator(null) }) {
                        Icon(Icons.Filled.Calculate, contentDescription = "حاسبة الرابط")
                    }
                }
            }

            // فلاتر: النوع
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinkType.values().forEach { t ->
                    FilterChip(
                        selected = t in typeFilter,
                        onClick = { viewModel.toggleType(t) },
                        label = { Text(t.label) }
                    )
                }
            }
            // فلاتر: التصنيف + الحالة
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NetworkClass.values().forEach { c ->
                    FilterChip(
                        selected = c in classFilter,
                        onClick = { viewModel.toggleClass(c) },
                        label = { Text(c.label) }
                    )
                }
                LinkStatus.values().forEach { s ->
                    FilterChip(
                        selected = statusFilter == s,
                        onClick = { viewModel.toggleStatus(s) },
                        label = { Text(s.label) }
                    )
                }
            }

            // اللوحة الرئيسية
            Box(modifier = Modifier.weight(1f)) {
                GalaxyCanvas(
                    sites = sites,
                    links = links,
                    is3D = viewModel.is3D,
                    onSiteClick = viewModel::selectSite,
                    onLinkClick = { viewModel.selectLink(it.id) },
                    modifier = Modifier.fillMaxSize()
                )

                // بطاقة الموقع المحدد
                selectedSite?.let { site ->
                    GalaxyCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        onClick = { viewModel.clearSelection() }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(site.name, style = MaterialTheme.typography.titleMedium)
                                StatusChip(site.status.label, GalaxyColors.siteStatusColor(site.status))
                            }
                            Text("${site.code} • ${links.count { it.sourceSiteId == site.id || it.targetSiteId == site.id }} روابط ظاهرة")
                            Button(onClick = { onOpenSite(site.id) }) { Text("فتح بطاقة الموقع") }
                        }
                    }
                }

                // بطاقة الرابط المحدد
                selectedLink?.let { link ->
                    GalaxyCard(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        onClick = { viewModel.clearSelection() }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${link.type.label} • ${link.networkClass.label}", style = MaterialTheme.typography.titleMedium)
                                StatusChip(link.status.label, GalaxyColors.linkStatusColor(link.status))
                            }
                            Text(
                                "الأولوية: ${link.priority.label} • التردد: ${link.frequencyMHz} م.هـ • المسافة: ${"%.2f".format(link.distanceKm)} كم",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onOpenCalculator(link.id) }) { Text("الحاسبة") }
                                TextButton(onClick = { viewModel.cycleLinkStatus(link) }) { Text("تبديل الحالة") }
                                TextButton(onClick = { linkToDelete = link }) {
                                    Text("حذف", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // دليل الألوان
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LegendItem("نشط", NeonGreen)
                LegendItem("متدهور", WarnAmber)
                LegendItem("معطل", DangerRed)
                LegendItem("مخطط", SkyBlue)
            }
        }
    }

    if (showAddLink) {
        AddLinkDialog(
            sites = sites,
            onDismiss = { showAddLink = false },
            onConfirm = { source, target, type, cls, priority, freq ->
                viewModel.addLink(source, target, type, cls, priority, freq)
                showAddLink = false
            }
        )
    }

    linkToDelete?.let { link ->
        ConfirmDialog(
            title = "حذف الرابط",
            text = "سيُحذف الرابط نهائيًا من المجرة. هل أنت متأكد؟",
            confirmText = "حذف",
            onConfirm = {
                viewModel.deleteLink(link)
                linkToDelete = null
            },
            onDismiss = { linkToDelete = null }
        )
    }
}

/** عنصر دليل الألوان */
@Composable
private fun LegendItem(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(50),
            color = color,
            modifier = Modifier.size(10.dp)
        ) {}
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** منتقي موقع داخل حوار إضافة رابط */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitePicker(
    label: String,
    sites: List<Site>,
    selected: Site?,
    onSelect: (Site) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
                        onSelect(s)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** حوار إضافة رابط بين موقعين */
@Composable
private fun AddLinkDialog(
    sites: List<Site>,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, LinkType, NetworkClass, LinkPriority, Double) -> Unit
) {
    var source by remember { mutableStateOf<Site?>(null) }
    var target by remember { mutableStateOf<Site?>(null) }
    var type by remember { mutableStateOf(LinkType.MICROWAVE) }
    var networkClass by remember { mutableStateOf(NetworkClass.OPERATIONS) }
    var priority by remember { mutableStateOf(LinkPriority.NORMAL) }
    var freqText by remember { mutableStateOf("5800") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة رابط جديد") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SitePicker("الموقع المصدر", sites, source) { source = it }
                SitePicker("الموقع الهدف", sites, target) { target = it }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinkType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NetworkClass.values().forEach { c ->
                        FilterChip(selected = networkClass == c, onClick = { networkClass = c }, label = { Text(c.label) })
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinkPriority.values().forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }

                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it },
                    label = { Text("التردد (ميجاهرتز)") },
                    singleLine = true
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = source
                val t = target
                val freq = freqText.toDoubleOrNull()
                when {
                    s == null || t == null -> error = "اختر الموقعين أولًا"
                    s.id == t.id -> error = "لا يمكن ربط الموقع بنفسه"
                    freq == null || freq <= 0 -> error = "تردد غير صالح"
                    else -> onConfirm(s.id, t.id, type, networkClass, priority, freq)
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
