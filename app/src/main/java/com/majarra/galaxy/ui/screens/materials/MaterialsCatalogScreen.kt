package com.majarra.galaxy.ui.screens.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Material
import com.majarra.galaxy.domain.repository.MaterialRepository
import com.majarra.galaxy.domain.usecase.DeleteMaterialUseCase
import com.majarra.galaxy.domain.usecase.RenameMaterialUseCase
import com.majarra.galaxy.domain.usecase.SaveMaterialUseCase
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * شاشة إدارة كتالوج المواد الموحد (تعديل النسخة 2.2):
 * المواد تُعرَّف هنا مرة واحدة، ثم تختارها المواقع من واجهة
 * اختيار داخل تبويب المواد — بلا كتابة نصية في كل موقع.
 * إعادة التسمية تنعكس على كل المواقع، والحذف من الكتالوج فقط.
 */
@HiltViewModel
class MaterialsViewModel @Inject constructor(
    materialRepo: MaterialRepository,
    private val saveMaterial: SaveMaterialUseCase,
    private val renameMaterial: RenameMaterialUseCase,
    private val deleteMaterial: DeleteMaterialUseCase
) : ViewModel() {

    val materials: StateFlow<List<Material>> = materialRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** إضافة مادة جديدة للكتالوج */
    fun add(name: String, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                saveMaterial(Material(name = name))
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** إعادة تسمية مادة — تنعكس على كل المواقع المستخدمة لها */
    fun rename(material: Material, newName: String, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                renameMaterial(material, newName)
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** الحذف من الكتالوج فقط — عناصر المواقع تبقى محفوظة */
    fun delete(material: Material) {
        viewModelScope.launch { deleteMaterial(material) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsCatalogScreen(
    onBack: () -> Unit,
    viewModel: MaterialsViewModel = hiltViewModel()
) {
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var materialToRename by remember { mutableStateOf<Material?>(null) }
    var materialToDelete by remember { mutableStateOf<Material?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("المواد الموحدة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("مادة جديدة", modifier = Modifier.padding(start = 6.dp))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                // شرح مختصر لفكرة الكتالوج الموحد حتى لا يلتبس الغرض منه
                GalaxyCard {
                    Text(
                        "عرّف المواد هنا مرة واحدة، ثم اخترها من تبويب «المواد» " +
                            "داخل أي موقع — بلا كتابة متكررة. إعادة التسمية تنعكس على كل المواقع.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (materials.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Inventory2,
                        title = "لا توجد مواد بعد",
                        subtitle = "أضف مادة بزر «مادة جديدة» لتظهر في واجهات الاختيار داخل المواقع"
                    )
                }
            } else {
                items(materials, key = { it.id }) { material ->
                    GalaxyCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(material.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "أُضيفت: ${material.createdDate.formatDate()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { materialToRename = material }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "إعادة تسمية",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { materialToDelete = material }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "حذف من الكتالوج",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        MaterialEditDialog(
            title = "مادة جديدة",
            initialName = "",
            note = null,
            onDismiss = { showAdd = false },
            onSave = { name, reportError ->
                viewModel.add(
                    name,
                    onSaved = { showAdd = false },
                    onError = reportError
                )
            }
        )
    }

    materialToRename?.let { material ->
        MaterialEditDialog(
            title = "إعادة تسمية المادة",
            initialName = material.name,
            note = "ستنعكس التسمية الجديدة على كل المواقع التي تستخدم هذه المادة.",
            onDismiss = { materialToRename = null },
            onSave = { name, reportError ->
                viewModel.rename(
                    material,
                    name,
                    onSaved = { materialToRename = null },
                    onError = reportError
                )
            }
        )
    }

    materialToDelete?.let { material ->
        ConfirmDialog(
            title = "حذف المادة من الكتالوج",
            text = "ستُحذف «${material.name}» من الكتالوج الموحد فقط — " +
                "العناصر المضافة سابقًا في قوائم المواقع تبقى محفوظة.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.delete(material)
                materialToDelete = null
            },
            onDismiss = { materialToDelete = null }
        )
    }
}

/** حوار إضافة/إعادة تسمية مادة — أخطاء التحقق تظهر داخل الحوار */
@Composable
private fun MaterialEditDialog(
    title: String,
    initialName: String,
    note: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, reportError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("اسم المادة") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) { message -> error = message } },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
