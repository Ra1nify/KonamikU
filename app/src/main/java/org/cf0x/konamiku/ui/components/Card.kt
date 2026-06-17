package org.cf0x.konamiku.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.R
import org.cf0x.konamiku.data.EmuMode
import org.cf0x.konamiku.data.NfcCard
import org.cf0x.konamiku.ui.theme.LocalExpressiveMode

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
    val isExpressive = LocalExpressiveMode.current

    val glowAlpha by animateFloatAsState(
        targetValue   = if (isExpanded) 1f else 0f,
        animationSpec = tween(300),
        label         = "glow"
    )
    
    val primary = MaterialTheme.colorScheme.primary
    
    val containerColor = if (isActive && isExpressive) 
        MaterialTheme.colorScheme.tertiaryContainer 
    else if (isActive)
        MaterialTheme.colorScheme.primaryContainer
    else 
        MaterialTheme.colorScheme.surfaceContainer

    val borderModifier = if (glowAlpha > 0f) {
        Modifier.border(
            width  = if (isExpressive) 2.dp else 1.dp,
            brush  = Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = glowAlpha),
                    primary.copy(alpha = glowAlpha * 0.4f),
                    primary.copy(alpha = glowAlpha)
                )
            ),
            shape  = MaterialTheme.shapes.extraLarge
        )
    } else Modifier

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(borderModifier)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            ),
        onClick = onExpandClick,
        shape   = MaterialTheme.shapes.extraLarge,
        colors  = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape    = MaterialTheme.shapes.large,
                    // Container color avoids theme inversion
                    color    = if (isActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = if (isActive) Icons.Filled.Nfc else Icons.Outlined.Nfc,
                            contentDescription = null,
                            tint               = if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text     = card.name,
                    style    = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (isActive) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.card_badge_on),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text  = "IDENTIFIER",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )

                    SelectionContainer {
                        Text(
                            text     = card.idm,
                            style    = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }

                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.card_action_delete))
                        }

                        Spacer(Modifier.weight(1f))

                        FilledTonalButton(
                            onClick  = onEmuModeClick,
                            modifier = Modifier.padding(end = 8.dp),
                            shape    = MaterialTheme.shapes.medium
                        ) {
                            Text(when (emuMode) {
                                EmuMode.NORMAL -> stringResource(R.string.mode_normal)
                                EmuMode.COMPAT -> stringResource(R.string.mode_compat)
                                EmuMode.NATIVE -> stringResource(R.string.mode_native)
                            })
                        }

                        Button(
                            onClick = onActivateClick,
                            shape   = MaterialTheme.shapes.medium,
                            colors  = if (isActive)
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor   = MaterialTheme.colorScheme.onError
                                )
                            else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isActive) stringResource(R.string.card_action_stop) else stringResource(R.string.card_action_activate))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text(stringResource(R.string.card_delete_title)) },
            text             = { Text(stringResource(R.string.card_delete_desc, card.name)) },
            confirmButton    = {
                Button(
                    onClick = { showDeleteDialog = false; onDeleteConfirmed() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.card_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.card_add_cancel)) }
            }
        )
    }
}
