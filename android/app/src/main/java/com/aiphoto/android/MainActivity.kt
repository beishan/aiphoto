package com.aiphoto.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        lifecycleScope.launch {
            model.shareReady.collect { asset ->
                val file = asset.file
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("分享照片", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, asset.title))
            }
        }
        setContent {
            val dark = when (model.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val palette = if (dark) darkColorScheme(
                primary = Color(0xFFBDC7FF), primaryContainer = Color(0xFF303B63),
                onPrimaryContainer = Color(0xFFE6EAFF), background = Color(0xFF151823),
                surface = Color(0xFF222637), secondaryContainer = Color(0xFF303B63),
                onSecondaryContainer = Color(0xFFE6EAFF),
            ) else lightColorScheme(
                primary = Color(0xFF3448A4), primaryContainer = Color(0xFFE6EAFF),
                onPrimaryContainer = Color(0xFF253780), background = Color(0xFFF7F8FC),
                surface = Color.White, secondaryContainer = Color(0xFFE6EAFF),
                onSecondaryContainer = Color(0xFF253780),
            )
            MaterialTheme(colorScheme = palette) { Surface { AiphotoApp(model) } }
        }
    }
}

@Composable
internal fun DarkMediaSystemBars() {
    val view = LocalView.current
    val activity = LocalActivity.current ?: return
    DisposableEffect(view, activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}
