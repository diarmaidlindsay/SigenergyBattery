package com.github.diarmaidlindsay.sigenergybattery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.diarmaidlindsay.sigenergybattery.ui.MonitorScreen
import com.github.diarmaidlindsay.sigenergybattery.ui.MonitorViewModel
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.Background
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.SigenergyBatteryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Background.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Background.toArgb()),
        )
        setContent {
            SigenergyBatteryTheme {
                BatteryApp()
            }
        }
    }
}

@Composable
private fun BatteryApp(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as SigenergyBatteryApp).container
    val viewModel: MonitorViewModel = viewModel(factory = MonitorViewModel.Factory(container))

    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    fun requestBatteryOptimizationExemption() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            batteryOptimizationLauncher.launch(intent)
        } catch (_: Exception) {
            // Some OEMs block this intent; monitoring still works via the foreground service.
        }
    }

    MonitorScreen(
        viewModel = viewModel,
        notificationsGranted = notificationsGranted,
        onRequestNotifications = {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onEnsureBackgroundReliability = { requestBatteryOptimizationExemption() },
        modifier = modifier,
    )
}
