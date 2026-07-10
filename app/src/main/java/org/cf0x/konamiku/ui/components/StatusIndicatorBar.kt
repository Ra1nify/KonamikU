package org.cf0x.konamiku.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cf0x.konamiku.R
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.system.StatusDetector.RootProvider
import org.cf0x.konamiku.ui.viewmodels.PendingAction
import org.cf0x.konamiku.ui.viewmodels.StatusViewModel
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

private enum class Panel { HCEF, ROOT, XPOSED }
private enum class IconTint { Active, Warning, Inactive }

@Composable
fun StatusIndicatorBar(
    viewModel: StatusViewModel,
    modifier: Modifier = Modifier
) {
    val context     = LocalContext.current
    val allStatus   by viewModel.status.collectAsState()
    val xposedState by XposedState.activationStateFlow.collectAsState()
    var expanded    by remember { mutableStateOf<Panel?>(null) }
    val scope       = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val hcefActive = allStatus?.nfc?.hcefSupported == true && allStatus?.nfc?.rfEnabled == true
    val rootActive = allStatus?.root?.available == true

    // Sequential expansion logic: collapse then expand
    val onPanelToggle: (Panel) -> Unit = { target ->
        scope.launch {
            if (expanded == target) {
                expanded = null
            } else if (expanded == null) {
                expanded = target
            } else {
                // Switch: collapse first
                expanded = null
                delay(180) // Wait for shrink animation
                expanded = target
            }
        }
    }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIconSlot(
                    panel       = Panel.HCEF,
                    expanded    = expanded,
                    onToggle    = onPanelToggle,
                    onLongClick = { viewModel.onNfcLongPress() },
                    activeIcon  = Icons.Filled.Memory,
                    idleIcon    = Icons.Outlined.Memory,
                    tintState   = if (hcefActive) IconTint.Active else IconTint.Inactive,
                    modifier    = Modifier.weight(1f)
                )

                SlotDivider()

                StatusIconSlot(
                    panel       = Panel.ROOT,
                    expanded    = expanded,
                    onToggle    = onPanelToggle,
                    onLongClick = { viewModel.onRootLongPress() },
                    activeIcon  = Icons.Filled.Tag,
                    idleIcon    = Icons.Outlined.Tag,
                    tintState   = if (rootActive) IconTint.Active else IconTint.Inactive,
                    modifier    = Modifier.weight(1f)
                )

                SlotDivider()

                StatusIconSlot(
                    panel       = Panel.XPOSED,
                    expanded    = expanded,
                    onToggle    = onPanelToggle,
                    onLongClick = { viewModel.onXposedLongPress() },
                    activeIcon  = Icons.Filled.Extension,
                    idleIcon    = Icons.Outlined.Extension,
                    tintState   = when (xposedState) {
                        XposedActivationState.INACTIVE      -> IconTint.Inactive
                        XposedActivationState.NEEDS_RESTART -> IconTint.Warning
                        XposedActivationState.ACTIVE        -> IconTint.Active
                    },
                    modifier    = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = expanded != null,
                enter   = expandVertically() + fadeIn(tween(160)),
                exit    = shrinkVertically() + fadeOut(tween(120))
            ) {
                Column {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        when (expanded) {
                            Panel.HCEF   -> PanelHcef(allStatus?.nfc)
                            Panel.ROOT   -> PanelRoot(allStatus?.root)
                            Panel.XPOSED -> PanelXposed(allStatus?.xposed)
                            null         -> {}
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for long-press actions
    val pendingAction by viewModel.pendingAction.collectAsState()
    val action = pendingAction
    if (action != null) {
        val dialogTitle = when (action) {
            PendingAction.NfcEnable     -> stringResource(R.string.dialog_nfc_enable_title)
            PendingAction.NfcRestart    -> stringResource(R.string.dialog_nfc_restart_title)
            PendingAction.XposedRefresh -> stringResource(R.string.dialog_xposed_refresh_title)
        }
        val dialogDesc = when (action) {
            PendingAction.NfcEnable     -> stringResource(R.string.dialog_nfc_enable_desc)
            PendingAction.NfcRestart    -> stringResource(R.string.dialog_nfc_restart_desc)
            PendingAction.XposedRefresh -> stringResource(R.string.dialog_xposed_refresh_desc)
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelAction() },
            title       = { Text(dialogTitle) },
            text        = { Text(dialogDesc) },
            confirmButton = {
                Button(onClick = { viewModel.confirmAction() }) {
                    Text(stringResource(R.string.card_add_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAction() }) {
                    Text(stringResource(R.string.card_add_cancel))
                }
            }
        )
    }

    // Restart-app dialog (shown after NFC restart completes)
    val showRestartDialog by viewModel.showRestartDialog.collectAsState()
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = {},
            title       = { Text(stringResource(R.string.restart_app_dialog_confirm)) },
            text        = { Text(stringResource(R.string.restart_app_dialog_msg)) },
            confirmButton = {
                Button(onClick = { viewModel.restartApp() }) {
                    Text(stringResource(R.string.restart_app_dialog_confirm))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusIconSlot(
    panel: Panel,
    expanded: Panel?,
    onToggle: (Panel) -> Unit,
    onLongClick: () -> Unit,
    activeIcon: ImageVector,
    idleIcon: ImageVector,
    tintState: IconTint,
    modifier: Modifier = Modifier
) {
    val isExpanded = expanded == panel
    val primary    = MaterialTheme.colorScheme.primary
    val tertiary   = MaterialTheme.colorScheme.tertiary
    val onSurface  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    val targetTint = when {
        isExpanded                    -> primary
        tintState == IconTint.Active  -> primary
        tintState == IconTint.Warning -> tertiary
        else                          -> onSurface
    }

    val tint by animateColorAsState(
        targetValue   = targetTint,
        animationSpec = tween(250),
        label         = "icon_tint_${panel.name}"
    )

    Box(
        modifier         = modifier
            .fillMaxHeight()
            .combinedClickable(
                onClick     = { onToggle(panel) },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = if (tintState != IconTint.Inactive) activeIcon else idleIcon,
            contentDescription = panel.name,
            tint               = tint,
            modifier           = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SlotDivider() {
    Box(modifier = Modifier.width(0.5.dp).height(18.dp)) {
        Surface(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)) {}
    }
}

@Composable
private fun PanelHcef(nfc: StatusDetector.NfcStatus?) {
    val supported = nfc?.hcefSupported == true
    val rfOn      = nfc?.rfEnabled == true
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow(
            active = supported,
            label  = stringResource(R.string.status_hcef),
            detail = stringResource(if (supported) R.string.status_available else R.string.status_unavailable)
        )
        if (supported) {
            DetailRow(
                active = rfOn,
                label  = stringResource(R.string.status_nfc_rf),
                detail = stringResource(if (rfOn) R.string.status_rf_on else R.string.status_rf_off)
            )
        }
    }
}

@Composable
private fun PanelRoot(root: StatusDetector.RootStatus?) {
    val available    = root?.available == true
    val providerName = when (root?.provider) {
        RootProvider.MAGISK   -> "Magisk"
        RootProvider.KERNELSU -> "KernelSU"
        RootProvider.APATCH   -> "APatch"
        RootProvider.UNKNOWN  -> stringResource(R.string.status_root_unknown_provider)
        null                  -> stringResource(R.string.status_unavailable)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DetailRow(
            active = available,
            label  = stringResource(R.string.status_root),
            detail = if (available) providerName else stringResource(R.string.status_unavailable)
        )
    }
}

@Composable
private fun PanelXposed(xposed: StatusDetector.XposedStatus?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            xposed == null || (!xposed.active && !xposed.needsRestart) -> {
                DetailRow(
                    active = false,
                    label  = stringResource(R.string.status_lsposed),
                    detail = stringResource(R.string.status_unavailable)
                )
            }
            xposed.needsRestart -> {
                DetailRow(
                    active = false,
                    label  = stringResource(R.string.status_lsposed),
                    detail = xposed.provider
                )
                Text(
                    text  = stringResource(R.string.status_xposed_needs_restart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            xposed.active -> {
                DetailRow(
                    active = true,
                    label  = stringResource(R.string.status_lsposed),
                    detail = xposed.provider
                )
                DetailRow(
                    active = xposed.pmmActive,
                    label  = stringResource(R.string.status_pmmtool),
                    detail = stringResource(
                        if (xposed.pmmActive) R.string.status_injected
                        else                  R.string.status_not_injected
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailRow(active: Boolean, label: String, detail: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text  = if (active) "✓" else "✗",
            color = if (active) MaterialTheme.colorScheme.primary
            else        MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text  = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}