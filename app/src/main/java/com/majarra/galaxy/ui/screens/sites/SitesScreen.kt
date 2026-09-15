package com.majarra.galaxy.ui.screens.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.usecase.ObserveSitesUseCase
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SitesViewModel @Inject constructor(
    private val observeSites: ObserveSitesUseCase,
    private val saveSite: SaveSiteUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** البحث في قاعدة البيانات مباشرة عبر Room — فوري وبدون إنترنت */
    val sites: StateFlow<List<Site>> = _query
        .flatMapLatest { observeSites.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    /**
     * إضافة موقع جديد — الاسم والملاحظات اختيارية.
     * رسائل أخطاء التحقق تُعاد للواجهة لتظهر تحت الحقول.
     */
    fun addSite(name: String, notes: String, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                saveSite(Site(name = name, notes = notes))
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }
}

/** شاشة قائمة المواقع: بحث + قائمة بسيطة بالأسماء + إضافة عبر حوار */
@Composable
fun SitesScreen(
    onOpenSite: (Long) -> Unit,
    viewModel: SitesViewModel = hiltViewModel()
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("موقع جديد", modifier = Modifier.padding(start = 6.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "المواقع",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث بالاسم…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            if (sites.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.CellTower,
                    title = if (query.isBlank()) "لا توجد مواقع بعد" else "لا توجد مواقع مطابقة",
                    subtitle = if (query.isBlank()) "أضف موقعك الأول بزر «موقع جديد»" else "غيّر كلمة البحث"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sites, key = { it.id }) { site ->
                        GalaxyCard(onClick = { onOpenSite(site.id) }) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(site.name, style = MaterialTheme.typography.titleMedium)
                                if (site.notes.isNotBlank()) {
                                    Text(
                                        site.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    "أُنشئ: ${site.createdDate.formatDate()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSiteDialog(
            onDismiss = { showAdd = false },
            onSave = { name, notes, reportError ->
                viewModel.addSite(
                    name = name,
                    notes = notes,
                    onSaved = { showAdd = false },
                    onError = reportError
                )
            }
        )
    }
}

/**
 * حوار إضافة موقع — الاسم فقط إلزامي، والملاحظات اختيارية.
 * أخطاء التحقق تظهر داخل الحوار بدل إغلاقه.
 */
@Composable
private fun AddSiteDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, reportError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, notes) { message -> error = message } },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
