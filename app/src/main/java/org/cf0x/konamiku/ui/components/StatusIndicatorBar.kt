package org.cf0x.konamiku.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.R
import org.cf0x.konamiku.system.StatusDetector
import org.cf0x.konamiku.ui.viewmodels.StatusViewModel
import org.cf0x.konamiku.xposed.XposedActivationState
import org.cf0x.konamiku.xposed.XposedState

private enum class Panel { HCEF, ROOT, XPOSED }

@Composable
fun StatusIndicatorBar(
    viewModel: StatusViewModel,
    modifier: Modifier = Modifier
) {
    val allStatus by viewModel.status.collectAsState()
    var expanded  by remember { mutableStateOf<Panel?>(null) }

    val hcefActive  = allStatus?.nfc?.hcefSupported == true && allStatus?.nfc?.rfEnabled == true
    val rootActive  = allStatus?.root?.available == true
    val xposedState = XposedState.activationState

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
                    panel      = Panel.HCEF,
                    expanded   = expanded,
                    onToggle   = { expanded = if (expanded == it) null else it },
                    activeIcon = Icons.Filled.Memory,
                    idleIcon   = Icons.Outlined.Memory,
                    tintState  = if (hcefActive) IconTint.Active else IconTint.Inactive,
                    modifier   = Modifier.weight(1f)
                )

                SlotDivider()

                StatusIconSlot(
                    panel      = Panel.ROOT,
                    expanded   = expanded,
                    onToggle   = { expanded = if (expanded == it) null else it },
                    activeIcon = Icons.Filled.Tag,
                    idleIcon   = Icons.Outlined.Tag,
                    tintState  = if (rootActive) IconTint.Active else IconTint.Inactive,
                    modifier   = Modifier.weight(1f)
                )

                SlotDivider()

                StatusIconSlot(
                    panel      = Panel.XPOSED,
                    expanded   = expanded,
                    onToggle   = { expanded = if (expanded == it) null else it },
                    activeIcon = Icons.Filled.Extension,
                    idleIcon   = Icons.Outlined.Extension,
                    tintState  = when (xposedState) {
                        XposedActivationState.INACTIVE      -> IconTint.Inactive
                        XposedActivationState.NEEDS_RESTART -> IconTint.Warning
                        XposedActivationState.ACTIVE        -> IconTint.Active
                    },
                    modifier   = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = expanded != null,
                enter   = expandVertically() + fadeIn(tween(160)),
                exit    = shrinkVertically() + fadeOut(tween(120))
            ) {
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
                        Panel.ROOT   -> PanelRoot(allStatus?.root, viewModel)
                        Panel.XPOSED -> PanelXposed(allStatus?.xposed)
                        null         -> {}
                    }
                }
            }
        }
    }
}


private enum class IconTint { Active, Warning, Inactive }

@Composable
private fun StatusIconSlot(
    panel: Panel,
    expanded: Panel?,
    onToggle: (Panel) -> Unit,
    activeIcon: ImageVector,
    idleIcon: ImageVector,
    tintState: IconTint,
    modifier: Modifier = Modifier
) {
    val isExpanded  = expanded == panel
    val primary     = MaterialTheme.colorScheme.primary
    val tertiary    = MaterialTheme.colorScheme.tertiary
    val onSurface   = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    val targetTint = when {
        isExpanded   -> primary
        tintState == IconTint.Active   -> primary
        tintState == IconTint.Warning  -> tertiary
        else         -> onSurface
    }

    val tint by animateColorAsState(
        targetValue   = targetTint,
        animationSpec = tween(250),
        label         = "icon_tint_${panel.name}"
    )

    Box(
        modifier         = modifier
            .fillMaxHeight()
            .clickable { onToggle(panel) },
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
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(18.dp)
    ) {
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
private fun PanelRoot(root: StatusDetector.RootStatus?, viewModel: StatusViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(
            active = root?.available == true,
            label  = "Root",
            detail = root?.provider?.ifEmpty {
                stringResource(R.string.status_unavailable)
            } ?: stringResource(R.string.status_unavailable)
        )
        if (root?.available != true) {
            OutlinedButton(
                onClick  = { viewModel.requestRootPermission() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Request Root Access")
            }
        }
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
                        else R.string.status_not_injected
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
            style = MaterialTheme.typography.labelLarge
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