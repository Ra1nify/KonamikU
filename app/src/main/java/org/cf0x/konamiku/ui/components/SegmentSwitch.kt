package org.cf0x.konamiku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.ui.theme.LocalExpressiveMode

@Composable
fun SegmentSwitch(
    label: String = "",
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpressive = LocalExpressiveMode.current
    val containerShape = if (isExpressive) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium
    
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(containerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                    label = "scale"
                )

                val targetColor = if (isSelected) 
                    MaterialTheme.colorScheme.secondaryContainer 
                else 
                    Color.Transparent
                
                val contentColor = if (isSelected) 
                    MaterialTheme.colorScheme.onSecondaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
                
                val bgColor by animateColorAsState(targetColor, tween(250), label = "bg")
                val textColor by animateColorAsState(contentColor, tween(250), label = "text")

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(scale)
                        .clip(containerShape)
                        .background(bgColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = { onSelect(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text  = option,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}
