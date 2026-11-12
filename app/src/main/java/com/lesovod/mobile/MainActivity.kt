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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.lesovod.mobile.data.local.ThemeMode
import com.lesovod.mobile.data.local.ThemePrefs
import com.lesovod.mobile.ui.navigation.LesovodNavGraph
import com.lesovod.mobile.ui.theme.LesovodTheme

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        com.lesovod.mobile.data.update.AppUpdateManager.foreground = true
    }

    override fun onStop() {
        com.lesovod.mobile.data.update.AppUpdateManager.foreground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.lesovod.mobile.data.network.ServerConfig.init(applicationContext)
        com.lesovod.mobile.data.izdanie.Izdanie.init(applicationContext)
        // Кто этот сервер — лесничество или лесопункт (от этого зависят экраны).
        lifecycleScope.launch {
            com.lesovod.mobile.data.izdanie.Izdanie.obnovit()?.let {
                com.lesovod.mobile.data.izdanie.Organizacii.nazvatTekushchuyu(applicationContext, it.podpis)
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val themePrefs = ThemePrefs.getInstance(this)
        setContent {
            val mode by themePrefs.mode.collectAsState()
            val large by themePrefs.large.collectAsState()
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
                // Режим «крупно»: увеличиваем и dp, и sp — весь экран как в прототипе (zoom 1.2).
                val base = androidx.compose.ui.platform.LocalDensity.current
                val density = if (large) androidx.compose.ui.unit.Density(base.density * 1.2f, base.fontScale) else base
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LesovodNavGraph()
                    }
                }
            }
        }
    }
}
