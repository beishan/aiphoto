package com.aiphoto.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            model.shareReady.collect { file ->
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("分享照片", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "分享原文件"))
            }
        }
        setContent {
            val dark = when (model.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val palette = if (dark) darkColorScheme(primary = Color(0xFF98D5B8), background = Color(0xFF121713), surface = Color(0xFF171D19), secondaryContainer = Color(0xFF284838))
                else lightColorScheme(primary = Color(0xFF28634E), background = Color(0xFFFAFAF7), surface = Color(0xFFFAFAF7), secondaryContainer = Color(0xFFE1EDE5), onSecondaryContainer = Color(0xFF204A37))
            MaterialTheme(colorScheme = palette) { Surface { AiphotoApp(model) } }
        }
    }
}
