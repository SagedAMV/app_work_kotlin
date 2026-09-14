package com.majarra.galaxy.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.domain.usecase.ClearAuditUseCase
import com.majarra.galaxy.domain.usecase.ObserveAuditUseCase
import com.majarra.galaxy.security.BackupManager
import com.majarra.galaxy.security.BiometricAuthHelper
import com.majarra.galaxy.security.SafeWipe
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.components.formatDateTime
import com.majarra.galaxy.ui.theme.DangerRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupManager,
    private val wipe: SafeWipe,
    private val clearAudit: ClearAuditUseCase,
    observeAudit: ObserveAuditUseCase
) : ViewModel() {

    val prefs = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.Prefs())

    val auditLogs = observeAudit()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val message = MutableStateFlow<String?>(null)

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settings.setDarkMode(enabled) }

    fun setBiometricLock(activity: FragmentActivity, enabled: Boolean) {
        if (enabled && !BiometricAuthHelper().canAuthenticate(activity)) {
            message.value = "لا يمكن تفعيل القفل: لا يتوفر مستشعر بيومتري مسجل"
            return
        }
        viewModelScope.launch { settings.setBiometricLock(enabled) }
    }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = backup.exportBackup(uri)
            message.value = if (ok) "تم تصدير النسخة الاحتياطية بنجاح" else "فشل تصدير النسخة الاحتياطية"
        }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = backup.importBackup(uri)
            message.value = if (ok) "تم الاستيراد — أعد تشغيل التطبيق لتطبيق البيانات" else "فشل الاستيراد"
        }
    }

    fun wipeAll(onDone: () -> Unit) {
        viewModelScope.launch {
            val ok = wipe.wipeAll()
            message.value = if (ok) "تم المسح الكامل — أعد تشغيل التطبيق" else "فشل المسح"
            if (ok) onDone()
        }
    }

    fun clearAuditLog() = viewModelScope.launch { clearAudit() }

    fun dismissMessage() {
        message.value = null
    }
}

/**
 * شاشة الإعدادات:
 * الوضع الليلي + القفل البيومتري + النسخ الاحتياطي + سجل التدقيق + الحذف الآمن الثلاثي.
 */
@Composable
fun SettingsScreen(
    activity: FragmentActivity,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val message by viewModel.message.collectAsState()
    val scope = rememberCoroutineScope()

    var wipeStep by remember { mutableStateOf(0) } // 0 لا، 1 تحذير، 2 كتابة كلمة حذف، 3 نهائي
    var wipeWord by remember { mutableStateOf("") }
    var confirmClearAudit by remember { mutableStateOf(false) }

    // مشغلات SAF للنسخ الاحتياطي
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "الإعدادات",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // المظهر
        GalaxyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("الوضع الليلي", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أسود عميق مريح للعين — الأساسي في التطبيق",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = prefs.darkMode, onCheckedChange = viewModel::setDarkMode)
            }
        }

        // القفل البيومتري
        GalaxyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("القفل البيومتري", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "طلب البصمة/الوجه عند فتح التطبيق",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.biometricLock,
                    onCheckedChange = { viewModel.setBiometricLock(activity, it) }
                )
            }
        }

        // النسخ الاحتياطي
        SectionTitle("النسخ الاحتياطي المحلي")
        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "تصدير/استيراد ملف قاعدة البيانات بالكامل — بدون إنترنت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("majarra-backup.db") }, modifier = Modifier.weight(1f)) {
                        Text("تصدير")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("استيراد")
                    }
                }
            }
        }

        // سجل التدقيق
        SectionTitle("سجل التدقيق — آخر العمليات")
        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (auditLogs.isEmpty()) {
                    Text("السجل فارغ", style = MaterialTheme.typography.bodySmall)
                }
                auditLogs.take(15).forEach { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${log.action} • ${log.entityType} ${log.details}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            log.timestamp.formatDateTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { confirmClearAudit = true }) {
                    Text("مسح السجل", color = DangerRed)
                }
            }
        }

        // الحذف الآمن — تأكيد ثلاثي
        SectionTitle("الحذف الآمن")
        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "مسح كل بيانات التطبيق نهائيًا. يتطلب ثلاثة تأكيدات متتالية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { wipeStep = 1 },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("مسح كل البيانات")
                }
            }
        }
    }

    // رسائل الحالة
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMessage() }) { Text("حسنًا") }
            }
        )
    }

    // خطوة 1: تحذير
    if (wipeStep == 1) {
        ConfirmDialog(
            title = "تحذير — الخطوة 1 من 3",
            text = "سيتم مسح كل المواقع والمعدات والروابط والتذاكر والتنبيهات نهائيًا. هل تريد المتابعة؟",
            confirmText = "متابعة",
            onConfirm = { wipeStep = 2 },
            onDismiss = { wipeStep = 0 }
        )
    }

    // خطوة 2: كتابة كلمة «حذف»
    if (wipeStep == 2) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text("الخطوة 2 من 3") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اكتب كلمة «حذف» للتأكيد:")
                    OutlinedTextField(value = wipeWord, onValueChange = { wipeWord = it }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (wipeWord.trim() == "حذف") wipeStep = 3 },
                    enabled = wipeWord.trim() == "حذف"
                ) { Text("متابعة") }
            },
            dismissButton = { TextButton(onClick = { wipeStep = 0 }) { Text("إلغاء") } }
        )
    }

    // خطوة 3: التأكيد النهائي
    if (wipeStep == 3) {
        ConfirmDialog(
            title = "الخطوة 3 من 3 — الأخيرة",
            text = "لا يمكن التراجع بعد هذه الخطوة. اضغط «امسح الآن» لتنفيذ المسح الكامل.",
            confirmText = "امسح الآن",
            onConfirm = {
                wipeStep = 0
                wipeWord = ""
                viewModel.wipeAll { /* المسح تم — الرسالة تظهر أعلاه */ }
            },
            onDismiss = { wipeStep = 0 }
        )
    }

    if (confirmClearAudit) {
        ConfirmDialog(
            title = "مسح سجل التدقيق",
            text = "سيتم حذف كل سجلات التدقيق نهائيًا.",
            confirmText = "مسح",
            onConfirm = {
                scope.launch { viewModel.clearAuditLog() }
                confirmClearAudit = false
            },
            onDismiss = { confirmClearAudit = false }
        )
    }
}
