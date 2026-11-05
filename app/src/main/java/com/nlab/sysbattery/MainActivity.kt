package com.nlab.sysbattery

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nlab.sysbattery.data.AdvancedBatteryStatsReader
import com.nlab.sysbattery.data.RootAccessReader
import com.nlab.sysbattery.data.RootBatteryStatsParser
import com.nlab.sysbattery.data.ShizukuBatteryStatsReader
import com.nlab.sysbattery.data.safeBoolean
import com.nlab.sysbattery.data.safeInt
import com.nlab.sysbattery.data.safeString
import com.nlab.sysbattery.data.shizuku.BatteryStatsUserService
import com.nlab.sysbattery.data.shizuku.IBatteryStatsUserService
import com.nlab.sysbattery.diagnostics.DiagnosticLogger
import com.nlab.sysbattery.service.BatteryMonitorService
import com.nlab.sysbattery.ui.components.SysBatteryIcons
import com.nlab.sysbattery.ui.components.SysBatteryTopBar
import com.nlab.sysbattery.ui.screens.AppUsageTab
import com.nlab.sysbattery.ui.screens.SettingsTab
import com.nlab.sysbattery.ui.screens.StatusTab
import com.nlab.sysbattery.ui.theme.SysBatteryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var shizukuStatus by mutableStateOf("Checking Shizuku…")
    private var shizukuFindings by mutableStateOf<List<RootBatteryStatsParser.Finding>>(emptyList())
    private var shizukuLoading by mutableStateOf(false)
    private var showDiagnostics by mutableStateOf(false)
    private var usageAccessGranted by mutableStateOf(false)
    private var batteryStatsPermissionGranted by mutableStateOf(false)

    private val exportDiagnostics = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            val outputStream = contentResolver.openOutputStream(uri)
                ?: error("Could not open the selected export destination")
            outputStream.bufferedWriter().use { it.write(DiagnosticLogger.readLogs()) }
            Toast.makeText(this, "Diagnostics exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            DiagnosticLogger.error("Diagnostics export failed", it)
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private var shizukuAuthorized by mutableStateOf(false)
    private var shizukuServiceBinding = false
    private var shizukuServiceBound = false
    private var shizukuService: IBatteryStatsUserService? = null

    private val shizukuUserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(this, BatteryStatsUserService::class.java))
            .daemon(false)
            .tag("sysbattery-battery-stats")
            .version(1)
    }

    private val shizukuServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shizukuService = service?.let { IBatteryStatsUserService.Stub.asInterface(it) }
            shizukuServiceBinding = false
            shizukuServiceBound = true
            runOnUiThread {
                shizukuStatus = if (shizukuService != null) {
                    "Authorized · BatteryStats service connected"
                } else {
                    "Authorized · could not connect to BatteryStats service"
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuService = null
            // A disconnected Android service remains bound and may reconnect automatically.
            shizukuServiceBinding = false
            runOnUiThread {
                shizukuStatus = if (shizukuAuthorized) "Authorized · reconnecting to BatteryStats service" else "Shizuku disconnected"
            }
        }
    }

    private val shizukuRequestCode = 0x5B24
    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode != shizukuRequestCode) return
            runCatching { Shizuku.removeRequestPermissionResultListener(this) }
            runOnUiThread {
                shizukuAuthorized = grantResult == PackageManager.PERMISSION_GRANTED
                shizukuStatus = if (shizukuAuthorized) "Authorized · connecting to BatteryStats service"
                else "Permission denied · standard monitoring remains available"
                if (shizukuAuthorized) bindShizukuService()
            }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun hasBatteryStatsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DiagnosticLogger.initialize(applicationContext)
        DiagnosticLogger.info("MainActivity created")
        usageAccessGranted = hasUsageAccess()
        batteryStatsPermissionGranted = hasBatteryStatsPermission()

        val initialPrefs = getSharedPreferences("sysbattery", MODE_PRIVATE)
        if (initialPrefs.safeBoolean("monitor_enabled", true)) {
            BatteryMonitorService.start(this)
        }
        refreshShizukuStatus()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val state by BatteryMonitorService.state.collectAsState()
            val prefs = remember { getSharedPreferences("sysbattery", MODE_PRIVATE) }
            val coroutineScope = rememberCoroutineScope()
            var running by remember { mutableStateOf(prefs.safeBoolean("monitor_enabled", true)) }
            var themeMode by remember { mutableStateOf(prefs.safeString("theme_mode", "System default")) }
            var drainDisplayMode by remember {
                mutableStateOf(
                    prefs.safeString(
                        BatteryMonitorService.PREF_DRAIN_DISPLAY_MODE,
                        BatteryMonitorService.METHOD_BOTH
                    )
                )
            }
            var preferencesRevision by remember { mutableIntStateOf(0) }
            var advancedReport by remember { mutableStateOf<AdvancedBatteryStatsReader.Report?>(null) }
            var advancedError by remember { mutableStateOf<String?>(null) }
            var rootReport by remember { mutableStateOf<RootAccessReader.RootReport?>(null) }
            var rootLoading by remember { mutableStateOf(false) }

            val batteryHistory = remember { mutableStateListOf<Pair<Long, Int>>() }
            val level = state.sample?.levelPercent
            LaunchedEffect(state.sample?.timestampMillis, level) {
                val currentLevel = level
                if (currentLevel != null) {
                    val timestamp = state.sample?.timestampMillis ?: System.currentTimeMillis()
                    val previous = batteryHistory.lastOrNull()
                    if (previous == null || previous.second != currentLevel || timestamp - previous.first >= 30_000L) {
                        batteryHistory.add(timestamp to currentLevel.coerceIn(0, 100))
                        while (batteryHistory.size > 40) batteryHistory.removeAt(0)
                    }
                }
            }

            val temperatureUnit = remember(preferencesRevision) {
                prefs.safeString("temperature_unit", "Celsius (°C)")
            }
            val configuredDesignCapacityMah = remember(preferencesRevision) {
                prefs.safeInt("design_capacity_mah", 0).takeIf { it in 500..20000 }?.toDouble()
            }
            val reportedDesignCapacityMah = state.sample?.designCapacityMicroAmpHours?.takeIf { it > 0L }?.div(1000.0)
            val designCapacityMah = reportedDesignCapacityMah ?: configuredDesignCapacityMah

            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            val refreshAdvanced: () -> Unit = {
                if (!shizukuLoading) {
                    shizukuLoading = true
                    coroutineScope.launch {
                        val (report, findings, errorMsg) = withContext(Dispatchers.IO) {
                            var shizukuItems = emptyList<RootBatteryStatsParser.Finding>()
                            var shizukuErr: String? = null
                            val canUseShizuku = runCatching {
                                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                            }.getOrDefault(false)
                            if (canUseShizuku) {
                                try {
                                    val service = shizukuService
                                    if (service != null) shizukuItems = ShizukuBatteryStatsReader(service).read().findings
                                    else shizukuErr = "Shizuku service is not connected yet. Tap Enable Shizuku access again."
                                } catch (e: Exception) {
                                    shizukuErr = e.message ?: e.javaClass.simpleName
                                }
                            }
                            var advReport: AdvancedBatteryStatsReader.Report? = null
                            var reportErr: String? = null
                            try {
                                advReport = AdvancedBatteryStatsReader(applicationContext).read()
                            } catch (e: Exception) {
                                reportErr = "Could not read standard statistics: ${e.javaClass.simpleName}"
                            }
                            val combinedError = if (shizukuErr != null && shizukuItems.isEmpty()) {
                                listOfNotNull(reportErr, "Shizuku: $shizukuErr").joinToString("\n")
                            } else {
                                reportErr
                            }
                            Triple(advReport, shizukuItems, combinedError)
                        }
                        advancedReport = report
                        shizukuFindings = findings
                        advancedError = errorMsg
                        shizukuLoading = false
                    }
                }
            }

            LaunchedEffect(selectedTab, usageAccessGranted) {
                if (selectedTab == 1) refreshAdvanced()
            }

            SysBatteryTheme(themeMode = themeMode) {
                Scaffold(
                    topBar = {
                        SysBatteryTopBar(
                            running = running,
                            onToggleMonitor = {
                                val next = !running
                                if (next) BatteryMonitorService.start(this)
                                else BatteryMonitorService.stop(this)
                                running = next
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 4.dp
                        ) {
                            val tabs = listOf(
                                "Status" to SysBatteryIcons.BatteryChargingFull,
                                "App Usage" to SysBatteryIcons.Apps,
                                "Settings" to SysBatteryIcons.Settings
                            )
                            tabs.forEachIndexed { index, (label, icon) ->
                                val selected = selectedTab == index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            )
                            .padding(innerPadding)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Crossfade(targetState = selectedTab, label = "tab_crossfade") { tabIndex ->
                                when (tabIndex) {
                                    0 -> StatusTab(
                                        sample = state.sample,
                                        rates = state.rates,
                                        session = state.session,
                                        running = running,
                                        onStart = { BatteryMonitorService.start(this@MainActivity); running = true },
                                        onStop = { BatteryMonitorService.stop(this@MainActivity); running = false },
                                        onBackground = { openBackgroundSettings() },
                                        batteryHistory = batteryHistory,
                                        temperatureUnit = temperatureUnit,
                                        designCapacityMah = designCapacityMah,
                                        readingMethod = drainDisplayMode,
                                        onReadingMethodChange = { nextMode ->
                                            drainDisplayMode = nextMode
                                            prefs.edit().putString(BatteryMonitorService.PREF_DRAIN_DISPLAY_MODE, nextMode).apply()
                                            BatteryMonitorService.refresh(this@MainActivity)
                                        },
                                        prefs = prefs,
                                        preferencesRevision = preferencesRevision,
                                        onPreferencesChanged = { preferencesRevision++ }
                                    )
                                    1 -> AppUsageTab(
                                        usageAccessGranted = usageAccessGranted,
                                        onUsageAccess = { openUsageAccessSettings() },
                                        onRefreshAdvanced = refreshAdvanced,
                                        shizukuLoading = shizukuLoading,
                                        advancedReport = advancedReport,
                                        advancedError = advancedError
                                    )
                                    2 -> SettingsTab(
                                        prefs = prefs,
                                        running = running,
                                        onMonitorEnabledChange = { enabled ->
                                            if (enabled) {
                                                BatteryMonitorService.start(this@MainActivity)
                                                running = true
                                            } else {
                                                BatteryMonitorService.stop(this@MainActivity)
                                                running = false
                                            }
                                        },
                                        onThemeModeChange = { themeMode = it },
                                        drainDisplayMode = drainDisplayMode,
                                        onDrainDisplayModeChange = { drainDisplayMode = it },
                                        onPreferencesChanged = { preferencesRevision++ },
                                        preferencesRevision = preferencesRevision,
                                        usageAccessGranted = usageAccessGranted,
                                        onUsageAccess = { openUsageAccessSettings() },
                                        batteryStatsPermissionGranted = batteryStatsPermissionGranted,
                                        shizukuStatus = shizukuStatus,
                                        shizukuAuthorized = shizukuAuthorized,
                                        shizukuFindings = shizukuFindings,
                                        shizukuLoading = shizukuLoading,
                                        onRequestShizuku = { requestShizukuAccess() },
                                        onRefreshAdvanced = refreshAdvanced,
                                        rootReport = rootReport,
                                        rootLoading = rootLoading,
                                        onRefreshRoot = {
                                            if (!rootLoading) {
                                                rootLoading = true
                                                coroutineScope.launch {
                                                    val report = withContext(Dispatchers.IO) {
                                                        RootAccessReader().read()
                                                    }
                                                    rootReport = report
                                                    rootLoading = false
                                                }
                                            }
                                        },
                                        onOpenDiagnostics = { showDiagnostics = true },
                                        onExportDiagnostics = { exportDiagnostics.launch("sysbattery-diagnostics.txt") },
                                        onClearDiagnostics = {
                                            DiagnosticLogger.clear()
                                            DiagnosticLogger.info("Logs cleared by user")
                                            preferencesRevision++
                                            Toast.makeText(this@MainActivity, "Diagnostic logs cleared", Toast.LENGTH_SHORT).show()
                                        },
                                        showDiagnostics = showDiagnostics,
                                        onDismissDiagnostics = { showDiagnostics = false },
                                        diagnosticText = remember(showDiagnostics, preferencesRevision) {
                                            DiagnosticLogger.readLogs()
                                        },
                                        sampleSource = state.sample?.source
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        usageAccessGranted = hasUsageAccess()
        batteryStatsPermissionGranted = hasBatteryStatsPermission()
        refreshShizukuStatus()
    }

    private fun openUsageAccessSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun openBackgroundSettings() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager?.isIgnoringBatteryOptimizations(packageName) != true) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
                return
            } catch (_: Exception) {
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        if (shizukuServiceBound || shizukuServiceBinding) {
            runCatching { Shizuku.unbindUserService(shizukuUserServiceArgs, shizukuServiceConnection, true) }
        }
        shizukuService = null
        shizukuServiceBinding = false
        shizukuServiceBound = false
        super.onDestroy()
    }

    private fun refreshShizukuStatus() {
        val available = runCatching {
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        }.getOrDefault(false)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        shizukuAuthorized = binderAlive && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        shizukuStatus = when {
            shizukuService != null -> "Authorized · BatteryStats service connected"
            shizukuServiceBinding -> "Authorized · connecting to BatteryStats service"
            shizukuServiceBound && shizukuAuthorized -> "Authorized · reconnecting to BatteryStats service"
            shizukuAuthorized -> "Authorized · connecting to BatteryStats service"
            binderAlive -> "Shizuku running · permission needed"
            available -> "Shizuku installed · service not running"
            else -> "Shizuku not installed · optional"
        }
        if (shizukuAuthorized && shizukuService == null && !shizukuServiceBound) bindShizukuService()
    }

    private fun bindShizukuService() {
        val canBind = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!canBind || shizukuService != null || shizukuServiceBinding || shizukuServiceBound) return
        shizukuServiceBinding = true
        try {
            Shizuku.bindUserService(shizukuUserServiceArgs, shizukuServiceConnection)
            shizukuServiceBound = true
        } catch (e: Exception) {
            DiagnosticLogger.error("Failed to bind Shizuku BatteryStats service", e)
            shizukuServiceBinding = false
            shizukuStatus = "Could not bind Shizuku service: ${e.javaClass.simpleName}"
        }
    }

    private fun requestShizukuAccess() {
        DiagnosticLogger.info("Shizuku authorization requested")
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                runCatching { startActivity(launchIntent) }
                shizukuStatus = "Open Shizuku and start its service, then return here"
            } else {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
                }
                shizukuStatus = "Install Shizuku, start its service, then return here"
            }
            return
        }
        val alreadyGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (alreadyGranted) {
            shizukuAuthorized = true
            shizukuStatus = "Authorized · connecting to BatteryStats service"
            bindShizukuService()
            return
        }
        runCatching {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.requestPermission(shizukuRequestCode)
        }.onFailure { e ->
            DiagnosticLogger.warn("Shizuku permission request failed", e)
        }
    }
}
