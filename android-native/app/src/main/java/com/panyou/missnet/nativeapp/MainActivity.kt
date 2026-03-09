package com.panyou.missnet.nativeapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.panyou.missnet.nativeapp.core.util.appGraph
import com.panyou.missnet.nativeapp.ui.navigation.MissNetApp
import com.panyou.missnet.nativeapp.ui.theme.MissNetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val appGraph = applicationContext.appGraph
            val settings by appGraph.settingsRepository.settings.collectAsState(initial = com.panyou.missnet.nativeapp.core.model.AppSettings())
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            MissNetTheme(settings = settings) {
                MissNetApp(appGraph = appGraph, activity = this)
            }
        }
    }
}
