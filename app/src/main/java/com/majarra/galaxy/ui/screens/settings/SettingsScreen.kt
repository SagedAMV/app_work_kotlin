package com.majarra.galaxy.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.domain.repository.SettingsRepository
import com.majarra.galaxy.security.AppRestarter
import com.majarra.galaxy.security.BackupManager
import com.majarra.galaxy.security.BackupResult
import com.majarra.galaxy.security.SafeWipe
import com.majarra.galaxy.ui.anim.GalaxyBubbleSlider
import com.majarra.galaxy.ui.anim.GalaxyStarBurst
import com.majarra.galaxy.ui.anim.GalaxySunMoonSwitch
import com.majarra.galaxy.ui.components.ConfirmDialog
import com.majarra.galaxy.ui.components.GalaxyCard
import com.majarra.galaxy.ui.components.SectionTitle
import com.majarra.galaxy.ui.theme.DangerRed
import com.majarra.galaxy.util.DateFormats
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
    private val wipe: SafeWipe
) : ViewModel() {

    val prefs = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.Prefs())

    val message = MutableStateFlow<String?>(null)

    /** يُرفع بعد نجاح الاستيراد أو المسح: Room لا يعيد فتح قاعدة أُغلق كائنها */
    private val _restartRequired = MutableStateFlow(false)
    val restartRequired = _restartRequired

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settings.setDarkMode(enabled) }

    /** عدد أيام التذكير قبل موعد الصيانة (اختيار 39) */
    fun setReminderDays(days: Int) = viewModelScope.launch { settings.setReminderDays(days) }

    /** حفظ رمز سري جديد وتفعيل القفل معه */
    fun setPin(pin: String) {
        viewModelScope.launch {
            try {
                settings.setPin(pin)
                message.value = "تم حفظ الرمز السري وتفعيل القفل"
            } catch (e: IllegalArgumentException) {
                message.value = e.message ?: "رمز غير صالح"
            }
        }
    }

    /** إبطال القفل مع إبقاء الرمز محفوظًا لإعادة التفعيل لاحقًا */
    fun disableLock() = viewModelScope.launch { settings.setLockEnabled(false) }

    fun exportBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            message.value = when (val result = backup.exportBackup(uri)) {
                is BackupResult.Exported -> "تم تصدير النسخة الاحتياطية بنجاح"
                is BackupResult.Failure -> result.reason
                is BackupResult.Imported -> "تم التصدير"
            }
        }
    }

    fun importBackup(uri: android.net.Uri) {
        viewModelScope.launch {
            when (val result = backup.importBackup(uri)) {
                is BackupResult.Imported -> {
                    message.value = "تم استيراد النسخة الاحتياطية. أعد تشغيل التطبيق لتطبيق البيانات."
                    _restartRequired.value = true
                }
                is BackupResult.Failure -> message.value = result.reason
                is BackupResult.Exported -> message.value = "تم الاستيراد"
            }
        }
    }

    fun wipeAll() {
        viewModelScope.launch {
            when (val result = wipe.wipeAll()) {
                is BackupResult.Failure -> message.value = result.reason
                else -> {
                    message.value = "تم مسح كل البيانات. أعد تشغيل التطبيق."
                    _restartRequired.value = true
                }
            }
        }
    }

    fun restartApp(context: android.content.Context) = AppRestarter.restart(context)

    fun dismissMessage() {
        message.value = null
    }
}

/**
 * شاشة الإعدادات — النسخة المبسطة:
 * الوضع الليلي + قفل بسيط برمز سري + نسخة احتياطية محلية + مسح البيانات.
 * (بلا سجل تدقيق ولا قفل بيومتري حسب تعليمات التبسيط)
 */
@Composable
fun SettingsScreen(
    /**
     * مضيف السنابار العام من جذر التطبيق. إن غاب (استخدام مستقل) تسقط
     * الشاشة إلى حوار الرسالة القديم بلا أي فقدان وظيفي.
     */
    snackbarHostState: SnackbarHostState? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val restartRequired by viewModel.restartRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPinDialog by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    // انفجار نجوم عند نجاح التصدير/الاستيراد (اختيار 44 من الجولة
    // الثالثة): يُشغَّل عندما تحمل رسالة الحالة خبر نجاح
    var backupBurst by remember { mutableStateOf(0) }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        if (current.contains("بنجاح") || current.startsWith("تم استيراد")) {
            backupBurst++
        }
        // إصلاح UX: كانت كل رسالة نجاح بسيطة (حفظ رمز، تصدير نسخة…)
        // تُعرض في حوار يوقف المستخدم ويطلب «حسنًا». الآن تُعرض سنابار
        // خفيفة عبر مضيف الجذر، ويبقى الحوار للحالة الوحيدة التي تحتاج
        // قرارًا فعليًا: إعادة التشغيل بعد الاستيراد أو المسح.
        if (!restartRequired && snackbarHostState != null) {
            snackbarHostState.showSnackbar(current)
            viewModel.dismissMessage()
        }
    }

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

        // المظهر — مفتاح بشمس وقمر (اختيار 40 من الجولة الثالثة)
        GalaxyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                // إصلاح محاذاة: كان المفتاح يلتصق بأعلى الخلية بعيدًا عن
                // مركز النص ذي السطرين
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("الوضع الليلي", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "أسود عميق مريح للعين — الأساسي في التطبيق",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GalaxySunMoonSwitch(checked = prefs.darkMode, onCheckedChange = viewModel::setDarkMode)
            }
        }

        // تنبيه الصيانة — منزلق بفقاعة طافية (اختيار 39)
        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("التذكير بالصيانة", style = MaterialTheme.typography.titleSmall)
                Text(
                    "قبل كم يومًا من الموعد تريد التنبيه؟ (الافتراضي 30 يومًا)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GalaxyBubbleSlider(
                    value = prefs.reminderDays,
                    onValueChange = viewModel::setReminderDays,
                    valueRange = 5..90,
                    format = { "$it يومًا" }
                )
            }
        }

        // القفل البسيط
        GalaxyCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("قفل التطبيق", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "رمز سري بسيط (٤-٨ أرقام) عند فتح التطبيق",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.lockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // التفعيل يمر عبر حفظ رمز جديد أولًا
                            showPinDialog = true
                        } else {
                            viewModel.disableLock()
                        }
                    }
                )
            }
        }

        // النسخ الاحتياطي — فوقه طبقة انفجار النجوم عند النجاح (اختيار 44)
        SectionTitle("النسخ الاحتياطي المحلي")
        Box {
            GalaxyCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "تصدير/استيراد ملف قاعدة البيانات بالكامل — بدون إنترنت وبدون تشفير معقد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                exportLauncher.launch("majarra-backup-${DateFormats.backupStamp()}.db")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
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
            GalaxyStarBurst(trigger = backupBurst, modifier = Modifier.matchParentSize())
        }

        // مسح البيانات
        SectionTitle("مسح البيانات")
        GalaxyCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "مسح كل المواقع والتفاصيل والمرفقات والإعدادات نهائيًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("مسح كل البيانات")
                }
            }
        }
    }

    // حوار الحالة — لا يظهر إلا عند طلب إعادة التشغيل (أو بلا مضيف سنابار)
    message?.takeIf { restartRequired || snackbarHostState == null }?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            text = { Text(msg) },
            confirmButton = {
                if (restartRequired) {
                    Button(onClick = { viewModel.restartApp(context) }) { Text("إعادة التشغيل الآن") }
                } else {
                    TextButton(onClick = { viewModel.dismissMessage() }) { Text("حسنًا") }
                }
            },
            dismissButton = {
                if (restartRequired) {
                    TextButton(onClick = { viewModel.dismissMessage() }) { Text("لاحقًا") }
                }
            }
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            }
        )
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "مسح كل البيانات",
            text = "سيُحذف كل شيء نهائيًا ولا يمكن التراجع. هل أنت متأكد؟",
            confirmText = "امسح الآن",
            onConfirm = {
                confirmWipe = false
                viewModel.wipeAll()
            },
            onDismiss = { confirmWipe = false }
        )
    }
}

/** حوار ضبط الرمز السري — إدخال واحد مع تحقق من الصيغة */
@Composable
private fun SetPinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinValid = pin.length in 4..8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط الرمز السري") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "أدخل رمزًا من ٤ إلى ٨ أرقام. سيُطلب هذا الرمز عند كل فتح للتطبيق.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("الرمز السري") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null || (pin.isNotEmpty() && !pinValid),
                    supportingText = {
                        when {
                            error != null -> Text(error!!)
                            pin.isEmpty() -> Text("أدخل من ٤ إلى ٨ أرقام")
                            !pinValid -> Text("الطول الحالي ${pin.length}/٤ على الأقل")
                            else -> Text("طول صالح: ${pin.length}/٨")
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinValid) {
                        onSave(pin)
                    } else {
                        error = "الرمز يجب أن يكون بين ٤ و٨ أرقام"
                    }
                },
                enabled = pinValid
            ) { Text("حفظ وتفعيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
