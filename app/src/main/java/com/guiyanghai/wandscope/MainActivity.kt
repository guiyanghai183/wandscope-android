package com.guiyanghai.wandscope

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContent {
            WandScopeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                val requested = remember { getSharedPreferences("wandscope_permissions", MODE_PRIVATE) }
                LaunchedEffect(state.loggedIn) {
                    if (
                        state.loggedIn && Build.VERSION.SDK_INT >= 33 &&
                        !requested.getBoolean("notification_requested", false)
                    ) {
                        requested.edit().putBoolean("notification_requested", true).apply()
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                BackHandler(enabled = state.loggedIn && state.screen != Screen.Projects) { viewModel.back() }
                WandScopeRoot(state, viewModel)
            }
        }
    }
}
