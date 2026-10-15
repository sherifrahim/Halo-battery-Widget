package com.halo.btbattery.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.btbattery.R
import com.halo.btbattery.ui.components.AmbientBackdrop
import com.halo.btbattery.ui.components.bouncy
import com.halo.btbattery.ui.theme.LocalAccent
import com.halo.btbattery.ui.theme.LocalTones
import kotlinx.coroutines.launch

data class PhoneBattery(val level: Int, val charging: Boolean)

@Composable
fun rememberPhoneBattery(): PhoneBattery? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PhoneBattery?>(null) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0) state = PhoneBattery(
                    level * 100 / scale.coerceAtLeast(1),
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private data class Tab(val label: String, @DrawableRes val icon: Int)

private val tabs = listOf(Tab("Devices", R.drawable.ic_earbuds), Tab("Style", R.drawable.ic_palette))

@Composable
fun HaloScreen(vm: MainViewModel) {
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        AmbientBackdrop()
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> DevicesScreen(vm)
                else -> StyleScreen(vm)
            }
        }
        FloatingTabBar(
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
        )
    }
}

/** Floating, translucent capsule tab bar; the selection lozenge glides between tabs on a spring. */
@Composable
private fun FloatingTabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val accent = LocalAccent.current
    val itemW = 92.dp
    val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "tab")
    Box(
        modifier
            .shadow(18.dp, CircleShape, ambientColor = Color(0x22000000), spotColor = Color(0x2E000000))
            .clip(CircleShape)
            .background(t.bar)
            .border(1.dp, Brush.verticalGradient(listOf(t.edgeTop, t.edgeBottom)), CircleShape)
            .padding(4.dp)
            .height(56.dp)
            .width(itemW * tabs.size),
    ) {
        Box(Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(CircleShape).background(accent.copy(alpha = if (t.dark) 0.18f else 0.12f)))
        Row {
            tabs.forEachIndexed { i, tab ->
                val color by animateColorAsState(if (i == selected) accent else t.inkSoft, label = "tint")
                Column(
                    Modifier.width(itemW).fillMaxHeight().bouncy(pressedScale = 0.88f) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(painterResource(tab.icon), null, Modifier.size(22.dp), tint = color)
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 11.sp, fontWeight = if (i == selected) FontWeight.Medium else FontWeight.Normal, color = color)
                }
            }
        }
    }
}
