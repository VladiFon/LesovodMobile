package com.lesovod.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lesovod.mobile.data.local.ThemeMode
import com.lesovod.mobile.data.local.ThemePrefs
import com.lesovod.mobile.ui.navigation.LesovodNavGraph
import com.lesovod.mobile.ui.theme.LesovodTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val themePrefs = ThemePrefs.getInstance(this)
        setContent {
            val mode by themePrefs.mode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            // По умолчанию тёмная тема независимо от системы; «Как в системе» — по выбору в Профиле.
            val darkTheme = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            // Значки строки состояния и навигации — светлые на тёмной теме и тёмные на светлой.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LesovodTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LesovodNavGraph()
                }
            }
        }
    }
}
