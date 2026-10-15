package com.halo.btbattery.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.btbattery.ui.theme.HaloTheme
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
            HaloTheme(settings.theme) {
                val dark = LocalTones.current.dark
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
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
