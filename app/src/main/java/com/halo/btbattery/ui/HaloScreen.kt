package com.halo.btbattery.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.halo.btbattery.R
import com.halo.btbattery.ui.components.FluidBackdrop
import com.halo.btbattery.ui.components.SmoothShape
import com.halo.btbattery.ui.components.bouncy
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

private val tabs = listOf(Tab("Devices", R.drawable.ic_earbuds), Tab("Style", R.drawable.ic_tune))

@Composable
fun HaloScreen(vm: MainViewModel) {
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        FluidBackdrop()
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> DevicesScreen(vm)
                else -> StyleScreen(vm)
            }
        }
        FloatingIsland(
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp),
        )
    }
}

/** ColorOS-style floating island: a glass capsule whose highlight glides to the chosen tab. */
@Composable
private fun FloatingIsland(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val itemW = 116.dp
    val shape = SmoothShape(30.dp)
    val x by animateDpAsState(
        targetValue = itemW * selected,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "island",
    )
    Box(
        modifier
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(if (t.dark) Color(0xCC1A1D24) else Color(0xE6FFFFFF))
            .border(1.dp, Brush.verticalGradient(listOf(t.edgeTop, t.edgeBottom)), shape)
            .padding(6.dp)
            .height(48.dp)
            .width(itemW * tabs.size),
    ) {
        Box(Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(SmoothShape(24.dp)).background(t.glassStrong))
        Row {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Row(
                    Modifier.width(itemW).fillMaxHeight().bouncy { onSelect(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(painterResource(tab.icon), null, Modifier.size(20.dp), tint = if (active) t.ink else t.inkSoft)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) t.ink else t.inkSoft,
                    )
                }
            }
        }
    }
}
