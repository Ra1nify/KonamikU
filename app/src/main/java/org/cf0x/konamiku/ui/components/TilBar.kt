package org.cf0x.konamiku.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

data class TilSegment(
    val content: @Composable () -> Unit,
    val weight: Float = 1f,
    val onClick: (() -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilBar(
    segments: List<TilSegment>,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    expandedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val outerRadius = 28.dp
    val outerShape  = RoundedCornerShape(outerRadius)

    val expandedBottomShape = RoundedCornerShape(
        topStart    = 4.dp,
        topEnd      = 4.dp,
        bottomStart = outerRadius,
        bottomEnd   = outerRadius
    )

    Column(modifier = modifier.fillMaxWidth()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(outerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalSlots = segments.size + 1

                segments.forEachIndexed { index, seg ->
                    val segShape = when {
                        totalSlots == 1 -> outerShape
                        index == 0      -> RoundedCornerShape(
                            topStart    = outerRadius,
                            bottomStart = outerRadius,
                            topEnd      = 4.dp,
                            bottomEnd   = 4.dp
                        )
                        else            -> RoundedCornerShape(4.dp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(seg.weight)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .clip(segShape)
                            .then(
                                if (seg.onClick != null || seg.onLongClick != null)
                                    Modifier.combinedClickable(
                                        onClick     = { seg.onClick?.invoke() },
                                        onLongClick = { seg.onLongClick?.invoke() }
                                    )
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        seg.content()
                    }

                    if (index < segments.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                val arrowShape = RoundedCornerShape(
                    topStart    = 4.dp,
                    bottomStart = 4.dp,
                    topEnd      = outerRadius,
                    bottomEnd   = outerRadius
                )

                val rotation by animateFloatAsState(
                    targetValue   = if (isExpanded) 180f else 0f,
                    animationSpec = tween(300),
                    label         = "arrow"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(arrowShape)
                        .combinedClickable(onClick = onExpandToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier           = Modifier.graphicsLayer { rotationZ = rotation },
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(expandedBottomShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    expandedContent()
                }
            }
        }
    }
}