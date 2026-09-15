package com.majarra.galaxy.ui.screens.sites

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.majarra.galaxy.data.local.Site
import com.majarra.galaxy.domain.model.SiteStatus
import com.majarra.galaxy.domain.repository.SiteRepository
import com.majarra.galaxy.domain.usecase.SaveSiteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.majarra.galaxy.util.formatDecimals

@HiltViewModel
class SiteFormViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val siteRepo: SiteRepository,
    private val saveSite: SaveSiteUseCase
) : ViewModel() {

    val siteId: Long = savedStateHandle.get<Long>("siteId") ?: -1L

    var name by mutableStateOf("")
    var code by mutableStateOf("")
    var latitude by mutableStateOf("33.3152")
    var longitude by mutableStateOf("44.3661")
    var status by mutableStateOf(SiteStatus.ACTIVE)
    var notes by mutableStateOf("")

    val saving = MutableStateFlow(false)

    init {
        if (siteId > 0) {
            viewModelScope.launch {
                siteRepo.getSite(siteId)?.let { s ->
                    name = s.name
                    code = s.code
                    latitude = s.latitude.toString()
                    longitude = s.longitude.toString()
                    status = s.status
                    notes = s.notes
                }
            }
        }
    }

    /** الحفظ مع التحقق من المدخلات — لا تثق بأي مدخل */
    fun save(onDone: (String?) -> Unit) {
        if (name.isBlank() || code.isBlank()) {
            onDone("اسم الموقع ورمزه مطلوبان")
            return
        }
        val lat = latitude.toDoubleOrNull()
        val lng = longitude.toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            onDone("الإحداثيات غير صالحة")
            return
        }
        viewModelScope.launch {
            saving.value = true
            try {
                saveSite(
                    Site(
                        id = if (siteId > 0) siteId else 0,
                        name = name.trim(),
                        code = code.trim(),
                        latitude = lat,
                        longitude = lng,
                        status = status,
                        notes = notes.trim()
                    )
                )
                onDone(null)
            } catch (e: IllegalArgumentException) {
                // رسائل التحقق الواضحة: اسم ناقص، إحداثيات خارج النطاق، رمز مكرر…
                onDone(e.message ?: "بيانات غير صالحة")
            } catch (t: Throwable) {
                android.util.Log.e("SiteForm", "save failed", t)
                onDone("تعذر الحفظ: ${t.localizedMessage ?: t.javaClass.simpleName}")
            } finally {
                saving.value = false
            }
        }
    }

    fun applyLocation(lat: Double, lng: Double) {
        latitude = lat.formatDecimals(6)
        longitude = lng.formatDecimals(6)
    }
}

/** شاشة إضافة/تعديل موقع — إحداثيات يدوية أو GPS تلقائي */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteFormScreen(
    siteId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SiteFormViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            fillFromGps()
        } else {
            scope.launch { snackbar.showSnackbar("تم رفض إذن الموقع — أدخل الإحداثيات يدويًا") }
        }
    }

    /**
     * قراءة آخر موقع معروف.
     * - لا نستدعي مزوّد الموقع قبل التحقق من الإذن (كان التحقق مُلتفًّا عليه بـ
     *   @SuppressLint فيرمي SecurityException عند رفض الإذن).
     * - لا نخترع إحداثيات افتراضية عند غياب قراءة، لأن ذلك يحفظ موقعًا خاطئًا
     *   بصمت. الآن نُبلغ المستخدم بوضوح.
     */
    fun fillFromGps() {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            scope.launch { snackbar.showSnackbar("خدمة الموقع غير متوفرة على هذا الجهاز") }
            return
        }
        val location = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: SecurityException) {
            null
        }
        if (location == null) {
            scope.launch {
                snackbar.showSnackbar("لا توجد قراءة موقع محفوظة — شغّل GPS ثم أعد المحاولة")
            }
            return
        }
        viewModel.applyLocation(location.latitude, location.longitude)
        scope.launch { snackbar.showSnackbar("تم تحديث الإحداثيات من الجهاز") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (siteId > 0) "تعديل موقع" else "موقع جديد") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("اسم الموقع") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.code,
                onValueChange = { viewModel.code = it },
                label = { Text("رمز الموقع (فريد)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.latitude,
                    onValueChange = { viewModel.latitude = it },
                    label = { Text("خط العرض") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.longitude,
                    onValueChange = { viewModel.longitude = it },
                    label = { Text("خط الطول") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(onClick = {
                val granted = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (granted) fillFromGps() else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Text("التقاط الإحداثيات تلقائيًا (GPS)", modifier = Modifier.padding(start = 8.dp))
            }

            // اختيار الحالة
            Text("حالة الموقع", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SiteStatus.values().forEach { s ->
                    androidx.compose.material3.FilterChip(
                        selected = viewModel.status == s,
                        onClick = { viewModel.status = s },
                        label = { Text(s.label) }
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text("ملاحظات") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Button(
                onClick = {
                    viewModel.save { error ->
                        if (error == null) onSaved() else {
                            viewModel.viewModelScope.launch { snackbar.showSnackbar(error) }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "جارٍ الحفظ…" else "حفظ الموقع")
            }
        }
    }
}
