package com.majarra.galaxy.ui.screens.sites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.usecase.ArchiveSiteUseCase
import com.majarra.galaxy.domain.usecase.CheckMaintenanceDueUseCase
import com.majarra.galaxy.domain.usecase.DueSite
import com.majarra.galaxy.domain.usecase.ObserveSitesUseCase
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import com.majarra.galaxy.ui.components.ColorDot
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.formatDate
import com.majarra.galaxy.util.daysFromNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * مرشح القائمة: الكل / تصنيف محدد / المؤرشفة.
 * غير خاص لأن الـ ViewModel العام يعرّضه في توقيعه.
 */
sealed class ListFilter {
    object Active : ListFilter()
    data class ByCategory(val categoryId: Long) : ListFilter()
    object Archived : ListFilter()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SitesViewModel @Inject constructor(
    private val observeSites: ObserveSitesUseCase,
    private val saveSite: SaveSiteUseCase,
    private val archiveSite: ArchiveSiteUseCase,
    categoryRepo: CategoryRepository,
    private val checkMaintenanceDue: CheckMaintenanceDueUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** المرشح الحالي: نشطة/حسب تصنيف/مؤرشفة */
    private val _filter = MutableStateFlow<ListFilter>(ListFilter.Active)
    val filter: StateFlow<ListFilter> = _filter

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * القائمة المعروضة تتبدل حسب المرشح: البحث يعمل على المواقع النشطة
     * فقط (نطاق البحث حسب الاسئله.md: الاسم فقط).
     */
    val sites: StateFlow<List<Site>> = combine(_filter, _query) { f, q -> f to q }
        .flatMapLatest { (f, q) -> sourceFor(f, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sourceFor(filter: ListFilter, q: String): Flow<List<Site>> = when (filter) {
        is ListFilter.Active -> observeSites.search(q)
        is ListFilter.ByCategory -> observeSites.byCategory(filter.categoryId)
        is ListFilter.Archived -> observeSites.archived()
    }

    /** المواقع المستحقة للصيانة — للشريط العلوي في الشاشة الرئيسية */
    private val _dueSites = MutableStateFlow<List<DueSite>>(emptyList())
    val dueSites: StateFlow<List<DueSite>> = _dueSites

    init {
        viewModelScope.launch {
            runCatching { _dueSites.value = checkMaintenanceDue() }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setFilter(filter: ListFilter) {
        _filter.value = filter
        _query.value = "" // البحث لا يشمل المؤرشفة/التصنيفات: نبدأ نظيفًا
    }

    /**
     * إضافة موقع جديد — الاسم إلزامي والملاحظات والتصنيف اختيارية.
     * رسائل أخطاء التحقق تُعاد للواجهة لتظهر تحت الحقول.
     */
    fun addSite(name: String, notes: String, categoryId: Long?, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                saveSite(Site(name = name, notes = notes, categoryId = categoryId))
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** استعادة موقع من الأرشيف */
    fun restore(site: Site) {
        viewModelScope.launch { archiveSite(site, archived = false) }
    }
}

/** شاشة قائمة المواقع: شريط مستحق + بحث + فلاتر تصنيف/أرشيف + قائمة */
@Composable
fun SitesScreen(
    onOpenSite: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenMaterials: () -> Unit,
    viewModel: SitesViewModel = hiltViewModel()
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val dueSites by viewModel.dueSites.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    // نسخة محلية: الخصائص المفوّضة (by) لا تدعم الإسناد الذكي في
    // الوصول للحقول الفرعية، فننسخ إلى val محلي أولًا.
    val currentFilter = filter
    val showArchived = currentFilter is ListFilter.Archived

    Scaffold(
        floatingActionButton = {
            if (!showArchived) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("موقع جديد", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showArchived) "المواقع المؤرشفة" else "المواقع",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                // إدارة التصنيفات + كتالوج المواد الموحد (النسخة 2.2)
                IconButton(onClick = onOpenMaterials) {
                    Icon(Icons.Filled.Inventory2, contentDescription = "المواد الموحدة")
                }
                IconButton(onClick = onOpenCategories) {
                    Icon(Icons.Filled.Category, contentDescription = "إدارة التصنيفات")
                }
            }

            // الشريط العلوي للمواقع المستحقة (إجابة الاسئله.md)
            DueSitesBanner(
                dueSites = dueSites,
                visible = !showArchived,
                onOpenSite = onOpenSite
            )

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث بالاسم…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                enabled = !showArchived // البحث على النشطة فقط حسب الاسئله.md
            )

            // شرائح الفلترة: الكل + التصنيفات + المؤرشفة
            LazyRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = currentFilter is ListFilter.Active,
                        onClick = { viewModel.setFilter(ListFilter.Active) },
                        label = { Text("الكل") }
                    )
                }
                items(categories, key = { "cat-${it.id}" }) { category ->
                    FilterChip(
                        selected = currentFilter is ListFilter.ByCategory && currentFilter.categoryId == category.id,
                        onClick = { viewModel.setFilter(ListFilter.ByCategory(category.id)) },
                        label = { Text(category.name) },
                        leadingIcon = { ColorDot(category.colorHex) }
                    )
                }
                item {
                    FilterChip(
                        selected = showArchived,
                        onClick = { viewModel.setFilter(ListFilter.Archived) },
                        label = { Text("المؤرشفة") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            if (sites.isEmpty()) {
                EmptyState(
                    icon = if (showArchived) Icons.Filled.Archive else Icons.Filled.CellTower,
                    title = when {
                        showArchived -> "لا توجد مواقع مؤرشفة"
                        query.isBlank() -> "لا توجد مواقع هنا بعد"
                        else -> "لا توجد مواقع مطابقة"
                    },
                    subtitle = when {
                        showArchived -> "المواقع المؤرشفة تظهر هنا ويمكن استعادتها"
                        query.isBlank() -> "أضف موقعًا جديدًا بزر «موقع جديد»"
                        else -> "غيّر كلمة البحث"
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sites, key = { it.id }) { site ->
                        SiteRow(
                            site = site,
                            categories = categories,
                            archived = showArchived,
                            onClick = { onOpenSite(site.id) },
                            onRestore = { viewModel.restore(site) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSiteDialog(
            categories = categories,
            onDismiss = { showAdd = false },
            onSave = { name, notes, categoryId, reportError ->
                viewModel.addSite(
                    name = name,
                    notes = notes,
                    categoryId = categoryId,
                    onSaved = { showAdd = false },
                    onError = reportError
                )
            }
        )
    }
}

/* ═══════════════════ الشريط العلوي للصيانة المستحقة ═══════════════════ */

/**
 * شريط علوي يعرض المواقع المتأخرة والقريبة الصيانة (إجابة الاسئله.md).
 * الأحمر = متأخر، الكهرماني = قريب. النقر يفتح الموقع مباشرة.
 */
@Composable
private fun DueSitesBanner(
    dueSites: List<DueSite>,
    visible: Boolean,
    onOpenSite: (Long) -> Unit
) {
    AnimatedVisibility(
        visible = visible && dueSites.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        GalaxyCard(modifier = Modifier.padding(bottom = 10.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("صيانة مستحقة", style = MaterialTheme.typography.titleMedium)
                }
                dueSites.take(4).forEach { due ->
                    val days = due.dueDate.daysFromNow()
                    val overdue = days < 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            due.siteName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            when {
                                overdue -> "متأخر ${-days} يوم"
                                days == 0L -> "اليوم"
                                else -> "بعد $days يوم"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
                if (dueSites.size > 4) {
                    Text(
                        "و${dueSites.size - 4} مواقع أخرى…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ═══════════════════ صف موقع في القائمة ═══════════════════ */

@Composable
private fun SiteRow(
    site: Site,
    categories: List<Category>,
    archived: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit
) {
    val category = categories.firstOrNull { it.id == site.categoryId }
    GalaxyCard(onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (category != null) ColorDot(category.colorHex)
                Text(
                    site.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (archived) {
                    OutlinedButton(onClick = onRestore, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                        Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("استعادة", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (site.notes.isNotBlank()) {
                Text(
                    site.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "آخر تعديل: ${site.lastModified.formatDate()}" +
                    if (category != null) " — ${category.name}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/* ═══════════════════ حوار إضافة موقع ═══════════════════ */

/**
 * حوار إضافة موقع — الاسم إلزامي، والملاحظات والتصنيف اختياريان.
 * أخطاء التحقق تظهر داخل الحوار بدل إغلاقه.
 */
@Composable
private fun AddSiteDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, categoryId: Long?, reportError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedCategory = categories.firstOrNull { it.id == categoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("موقع جديد") },
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
                    label = { Text("ملاحظات (اختياري)") },
                    minLines = 2,
                    maxLines = 4
                )
                // اختيار التصنيف (اختياري) — القائمة تظهر فوق الزر
                OutlinedButton(onClick = { showCategoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (selectedCategory != null) "التصنيف: ${selectedCategory.name}"
                        else "التصنيف: بلا تصنيف"
                    )
                }
                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("بلا تصنيف") },
                        onClick = {
                            categoryId = null
                            showCategoryMenu = false
                        }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ColorDot(category.colorHex)
                                    Text(category.name)
                                }
                            },
                            onClick = {
                                categoryId = category.id
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, notes, categoryId) { message -> error = message } },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
