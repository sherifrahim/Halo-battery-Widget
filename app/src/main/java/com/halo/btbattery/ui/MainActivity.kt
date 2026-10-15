package com.halo.btbattery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.halo.btbattery.model.AccentStyle
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.HaloTheme
import com.halo.btbattery.widget.BatteryPalette
import com.halo.btbattery.ui.theme.LocalTones
import com.halo.btbattery.work.RefreshWorker

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshWorker.schedulePeriodic(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val accent = Color(BatteryPalette.healthy(this, settings.accent).second)
                .takeIf { settings.accent == AccentStyle.WALLPAPER } ?: Fluid.Green
            HaloTheme(settings.theme, accent) {
                val dark = LocalTones.current.dark
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                HaloScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
