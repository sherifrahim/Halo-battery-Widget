package com.halo.btbattery.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.btbattery.ui.theme.LocalTones

/** Capsule segmented control whose thumb glides between options on a spring. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val shape = SmoothShape(18.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(shape).background(t.glass).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val x by animateDpAsState(
            targetValue = segment * index,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "thumb",
        )
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(SmoothShape(14.dp)).background(t.glassStrong))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (option == selected) t.ink else t.inkSoft,
                    )
                }
            }
        }
    }
}
