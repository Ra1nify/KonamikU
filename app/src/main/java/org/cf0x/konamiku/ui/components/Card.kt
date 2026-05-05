package org.cf0x.konamiku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.NfcCard

@Composable
fun NfcCardItem(
    card: NfcCard,
    isExpanded: Boolean,
    isActive: Boolean,
    emuMode: EmuMode,
    onExpandClick: () -> Unit,
    onActivateClick: () -> Unit,
    onEmuModeClick: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val glowAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(300),
        label         = "glow"
    )
    val primary = MaterialTheme.colorScheme.primary

    val borderModifier = if (glowAlpha > 0f) {
        Modifier.border(
            width  = 1.5.dp,
            brush  = Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = glowAlpha),
                    primary.copy(alpha = glowAlpha * 0.3f),
                    primary.copy(alpha = glowAlpha)
                )
            ),
            shape  = MaterialTheme.shapes.medium
        )
    } else Modifier

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(borderModifier)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            ),
        onClick = onExpandClick,
        colors  = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = MaterialTheme.shapes.medium,
                    color    = if (isActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = if (isActive) Icons.Filled.Nfc
                            else          Icons.Outlined.Nfc,
                            contentDescription = null,
                            tint               = if (isActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text     = card.name,
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("ON", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text  = "IDm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    SelectionContainer {
                        Text(
                            text     = card.idm,
                            style    = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                    }

                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors  = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier           = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }

                        Spacer(Modifier.weight(1f))

                        OutlinedButton(
                            onClick  = onEmuModeClick,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(when (emuMode) {
                                EmuMode.NORMAL -> "Normal"
                                EmuMode.COMPAT -> "Compat"
                                EmuMode.NATIVE -> "Native"
                            })
                        }

                        Button(
                            onClick = onActivateClick,
                            colors  = if (isActive)
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor   = MaterialTheme.colorScheme.onErrorContainer
                                )
                            else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isActive) "Stop" else "Activate")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text("Delete Card") },
            text             = { Text("Delete \"${card.name}\"? This cannot be undone.") },
            confirmButton    = {
                Button(
                    onClick = { showDeleteDialog = false; onDeleteConfirmed() },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}