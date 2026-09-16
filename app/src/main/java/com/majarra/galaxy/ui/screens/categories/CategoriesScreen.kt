package com.majarra.galaxy.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Category
import com.majarra.galaxy.domain.repository.CategoryRepository
import com.majarra.galaxy.domain.usecase.DeleteCategoryUseCase
import com.majarra.galaxy.domain.usecase.SaveCategoryUseCase
import com.majarra.galaxy.ui.components.ColorDot
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.EmptyState
import com.majarra.galaxy.ui.components.GalaxyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** لوحة ألوان جاهزة لاختيار لون التصنيف — قيم واضحة على الوضعين */
private val PALETTE = listOf(
    "#38BDF8", // سماوي
    "#3DFB7F", // أخضر نيوني
    "#FFC857", // كهرماني
    "#FF5A5A", // أحمر
    "#A78BFA", // بنفسجي
    "#F472B6", // وردي
    "#34D399", // فيروزي
    "#FB923C"  // برتقالي
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    categoryRepo: CategoryRepository,
    private val saveCategory: SaveCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** حفظ تصنيف جديد أو تعديل قائم — أخطاء التحقق تُعاد للواجهة */
    fun save(name: String, colorHex: String, existing: Category?, onSaved: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                saveCategory(
                    Category(
                        id = existing?.id ?: 0L,
                        name = name,
                        colorHex = colorHex,
                        createdDate = existing?.createdDate ?: System.currentTimeMillis()
                    )
                )
                onSaved()
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "مدخلات غير صالحة")
            }
        }
    }

    /** حذف تصنيف — المواقع المرتبطة تتحول إلى «بلا تصنيف» ولا تُحذف */
    fun remove(category: Category) {
        viewModelScope.launch { deleteCategory(category) }
    }
}

/** شاشة إدارة التصنيفات: إضافة/تعديل/حذف مع لوحة ألوان جاهزة */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var dialogCategory by remember { mutableStateOf<Category?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التصنيفات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("تصنيف جديد", modifier = Modifier.padding(start = 6.dp))
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Category,
                    title = "لا توجد تصنيفات بعد",
                    subtitle = "أنشئ تصنيفًا لونيًا لتنظيم مواقعك"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    GalaxyCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ColorDot(category.colorHex, sizeDp = 18)
                            Text(
                                category.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { dialogCategory = category }) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل التصنيف")
                            }
                            IconButton(onClick = { toDelete = category }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف التصنيف")
                            }
                        }
                    }
                }
                item {
                    Text(
                        "حذف التصنيف لا يحذف مواقعه — تعود إلى «بلا تصنيف».",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showCreate || dialogCategory != null) {
        CategoryDialog(
            existing = dialogCategory,
            onDismiss = {
                showCreate = false
                dialogCategory = null
            },
            onSave = { name, colorHex, reportError ->
                viewModel.save(
                    name = name,
                    colorHex = colorHex,
                    existing = dialogCategory,
                    onSaved = {
                        showCreate = false
                        dialogCategory = null
                    },
                    onError = reportError
                )
            }
        )
    }

    toDelete?.let { category ->
        ConfirmDialog(
            title = "حذف التصنيف",
            text = "سيُحذف التصنيف «${category.name}» وتعود مواقعه إلى «بلا تصنيف». المواقع نفسها لا تُحذف.",
            confirmText = "حذف",
            onConfirm = {
                viewModel.remove(category)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }
}

/** حوار إنشاء/تعديل تصنيف: الاسم + لوحة ألوان */
@Composable
private fun CategoryDialog(
    existing: Category?,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String, reportError: (String) -> Unit) -> Unit
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var colorHex by rememberSaveable(existing?.id) { mutableStateOf(existing?.colorHex ?: PALETTE.first()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تصنيف جديد" else "تعديل التصنيف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("اسم التصنيف") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                Text("اللون", style = MaterialTheme.typography.labelMedium)
                // لوحة الألوان — صفان من 4
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PALETTE.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowColors.forEach { hex ->
                                val selected = hex == colorHex
                                val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                    .getOrDefault(MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(parsed)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            colorHex = hex
                                            error = null
                                        }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, colorHex) { message -> error = message } },
                enabled = name.isNotBlank()
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
