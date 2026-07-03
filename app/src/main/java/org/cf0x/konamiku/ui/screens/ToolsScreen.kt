package org.cf0x.konamiku.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.R
import org.cf0x.konamiku.ui.components.ConverterField
import org.cf0x.konamiku.ui.components.ConvertResult
import org.cf0x.konamiku.ui.components.ReorderableConverter
import org.cf0x.konamiku.util.AimeAccessCodeConverter
import org.cf0x.konamiku.util.CardIdConverter

private val HEX_CHARS    = ('0'..'9') + ('A'..'F')
private val KONAMI_ALPHA = "0123456789ABCDEFGHJKLMNPRSTUWXYZ".toSet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen() {
    var expandedBar by remember { mutableStateOf<String?>(null) }
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val isExpressive = org.cf0x.konamiku.ui.theme.LocalExpressiveMode.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isExpressive) {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.nav_tools)) },
                    scrollBehavior = topBarScrollBehavior
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.nav_tools)) },
                    scrollBehavior = topBarScrollBehavior
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMedium
                        )
                    ),
                onClick = { expandedBar = if (expandedBar == "id_converter") null else "id_converter" },
                shape   = MaterialTheme.shapes.extraLarge,
                colors  = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape    = MaterialTheme.shapes.large,
                            // Color Correction: Use Container roles
                            color    = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.CompareArrows, null,
                                    tint     = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.tools_id_converter_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    if (expandedBar == "id_converter") {
                        Column {
                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(Modifier.height(16.dp))
                            IdConverterPanel()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdConverterPanel() {
    val fields = listOf(
        ConverterField(
            label          = stringResource(R.string.tools_label_idm),
            placeholder    = stringResource(R.string.card_add_idm_placeholder),
            keyboardType   = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            maxLength      = 16,
            filter         = { it.uppercase().filter { c -> c in HEX_CHARS }.take(16) },
            validate       = { v ->
                v.length == 16 && v.all { it in HEX_CHARS } &&
                        (v.startsWith("E004") || v.startsWith("0"))
            }
        ),
        ConverterField(
            label          = stringResource(R.string.tools_label_kid),
            placeholder    = "FW5331K31WT1ZY2U",
            keyboardType   = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters,
            maxLength      = 16,
            filter         = { it.uppercase().filter { c -> c in KONAMI_ALPHA }.take(16) },
            validate       = { v -> v.length == 16 && v.all { it in KONAMI_ALPHA } }
        ),
        ConverterField(
            label          = stringResource(R.string.tools_label_ac),
            placeholder    = "00081234123412341234",
            keyboardType   = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None,
            maxLength      = 20,
            filter         = { it.filter { c -> c.isDigit() }.take(20) },
            validate       = { v -> v.filter { it.isDigit() }.length == 20 }
        )
    )

    val context = LocalContext.current
    ReorderableConverter(
        fields    = fields,
        onConvert = { sourceIndex, value -> convertAll(sourceIndex, value, context) },
        modifier  = Modifier.padding(top = 8.dp)
    )
}

private fun convertAll(sourceIndex: Int, value: String, context: android.content.Context): List<ConvertResult> {
    return when (sourceIndex) {
        0 -> {
            val kid = when (val r = CardIdConverter.toKonamiId(value, context::getString)) {
                is CardIdConverter.Result.Success -> ConvertResult.Success(r.value)
                is CardIdConverter.Result.Failure -> ConvertResult.Failure(r.reason)
            }
            val ac = idmToAccessCodeResult(value, context)
            listOf(ConvertResult.Skip, kid, ac)
        }
        1 -> {
            val idmResult = CardIdConverter.toUid(value, context::getString)
            val idm = when (idmResult) {
                is CardIdConverter.Result.Success -> ConvertResult.Success(idmResult.value)
                is CardIdConverter.Result.Failure -> ConvertResult.Failure(idmResult.reason)
            }
            val ac = if (idmResult is CardIdConverter.Result.Success)
                idmToAccessCodeResult(idmResult.value, context)
            else
                ConvertResult.Failure(context.getString(R.string.tools_err_generic))
            listOf(idm, ConvertResult.Skip, ac)
        }
        2 -> {
            val digits = value.filter { it.isDigit() }
            val idmResult = AimeAccessCodeConverter.accessCodeToIdm(digits, context::getString)
            val (idmStr, idmConvertResult) = when (idmResult) {
                is AimeAccessCodeConverter.Result.Single -> {
                    idmResult.value to ConvertResult.Success(idmResult.value)
                }
                is AimeAccessCodeConverter.Result.Ambiguous -> {
                    idmResult.positive to ConvertResult.Warning(
                        value = idmResult.positive,
                        note  = context.getString(R.string.tools_warn_ambiguous)
                    )
                }
                is AimeAccessCodeConverter.Result.Failure -> {
                    null to ConvertResult.Failure(idmResult.reason)
                }
            }
            val kid = if (idmStr != null) {
                when (val r = CardIdConverter.toKonamiId(idmStr, context::getString)) {
                    is CardIdConverter.Result.Success -> ConvertResult.Success(r.value)
                    is CardIdConverter.Result.Failure -> ConvertResult.Failure(r.reason)
                }
            } else {
                ConvertResult.Failure(context.getString(R.string.tools_err_generic))
            }
            listOf(idmConvertResult, kid, ConvertResult.Skip)
        }
        else -> List(3) { ConvertResult.Skip }
    }
}

private fun idmToAccessCodeResult(idm: String, context: android.content.Context): ConvertResult {
    return when (val r = AimeAccessCodeConverter.idmToAccessCode(idm, context::getString)) {
        is AimeAccessCodeConverter.Result.Single ->
            ConvertResult.Success(AimeAccessCodeConverter.formatAccessCode(r.value))
        is AimeAccessCodeConverter.Result.Ambiguous ->
            ConvertResult.Warning(
                value = AimeAccessCodeConverter.formatAccessCode(r.positive),
                note  = context.getString(R.string.tools_warn_ambiguous)
            )
        is AimeAccessCodeConverter.Result.Failure ->
            ConvertResult.Failure(r.reason)
    }
}
