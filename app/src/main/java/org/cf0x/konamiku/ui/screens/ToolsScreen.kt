package org.cf0x.konamiku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.cf0x.konamiku.R
import org.cf0x.konamiku.ui.components.ConverterField
import org.cf0x.konamiku.ui.components.ConvertResult
import org.cf0x.konamiku.ui.components.ReorderableConverter
import org.cf0x.konamiku.ui.components.TilBar
import org.cf0x.konamiku.ui.components.TilSegment
import org.cf0x.konamiku.util.AimeAccessCodeConverter
import org.cf0x.konamiku.util.CardIdConverter

private val HEX_CHARS    = ('0'..'9') + ('A'..'F')
private val KONAMI_ALPHA = "0123456789ABCDEFGHJKLMNPRSTUWXYZ".toSet()

@Composable
fun ToolsScreen() {
    var expandedBar by remember { mutableStateOf<String?>(null) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text  = stringResource(R.string.nav_tools),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        TilBar(
            segments = listOf(
                TilSegment(content = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier              = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.CompareArrows, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.tools_id_converter_title),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                })
            ),
            isExpanded      = expandedBar == "id_converter",
            onExpandToggle  = { expandedBar = if (expandedBar == "id_converter") null else "id_converter" },
            expandedContent = { IdConverterPanel() }
        )
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

    ReorderableConverter(
        fields    = fields,
        onConvert = { sourceIndex, value -> convertAll(sourceIndex, value) },
        modifier  = Modifier.padding(top = 4.dp)
    )
}

private fun convertAll(sourceIndex: Int, value: String): List<ConvertResult> {
    return when (sourceIndex) {
        0 -> {
            // IDm → KonamiID + AccessCode
            val kid = when (val r = CardIdConverter.toKonamiId(value)) {
                is CardIdConverter.Result.Success -> ConvertResult.Success(r.value)
                is CardIdConverter.Result.Failure -> ConvertResult.Failure(r.reason)
            }
            val ac = idmToAccessCodeResult(value)
            listOf(ConvertResult.Skip, kid, ac)
        }
        1 -> {
            // KonamiID → IDm → AccessCode
            val idmResult = CardIdConverter.toUid(value)
            val idm = when (idmResult) {
                is CardIdConverter.Result.Success -> ConvertResult.Success(idmResult.value)
                is CardIdConverter.Result.Failure -> ConvertResult.Failure(idmResult.reason)
            }
            val ac = if (idmResult is CardIdConverter.Result.Success)
                idmToAccessCodeResult(idmResult.value)
            else
                ConvertResult.Failure("IDm conversion failed")
            listOf(idm, ConvertResult.Skip, ac)
        }
        2 -> {
            // AccessCode → IDm → KonamiID
            val digits = value.filter { it.isDigit() }
            val idmResult = AimeAccessCodeConverter.accessCodeToIdm(digits)
            val (idmStr, idmConvertResult) = when (idmResult) {
                is AimeAccessCodeConverter.Result.Single -> {
                    idmResult.value to ConvertResult.Success(idmResult.value)
                }
                is AimeAccessCodeConverter.Result.Ambiguous -> {
                    idmResult.positive to ConvertResult.Warning(
                        value = idmResult.positive,
                        note  = "Two possible results; showing positive (more common)"
                    )
                }
                is AimeAccessCodeConverter.Result.Failure -> {
                    null to ConvertResult.Failure(idmResult.reason)
                }
            }
            val kid = if (idmStr != null) {
                when (val r = CardIdConverter.toKonamiId(idmStr)) {
                    is CardIdConverter.Result.Success -> ConvertResult.Success(r.value)
                    is CardIdConverter.Result.Failure -> ConvertResult.Failure(r.reason)
                }
            } else {
                ConvertResult.Failure("IDm conversion failed")
            }
            listOf(idmConvertResult, kid, ConvertResult.Skip)
        }
        else -> List(3) { ConvertResult.Skip }
    }
}

private fun idmToAccessCodeResult(idm: String): ConvertResult {
    return when (val r = AimeAccessCodeConverter.idmToAccessCode(idm)) {
        is AimeAccessCodeConverter.Result.Single ->
            ConvertResult.Success(AimeAccessCodeConverter.formatAccessCode(r.value))
        is AimeAccessCodeConverter.Result.Ambiguous ->
            ConvertResult.Warning(
                value = AimeAccessCodeConverter.formatAccessCode(r.positive),
                note  = "Two possible results; showing positive (more common)"
            )
        is AimeAccessCodeConverter.Result.Failure ->
            ConvertResult.Failure(r.reason)
    }
}