package org.cf0x.konamiku.ui.screens

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.cf0x.konamiku.R
import org.cf0x.konamiku.ui.components.TilBar
import org.cf0x.konamiku.ui.components.TilSegment
import org.cf0x.konamiku.util.AimeAccessCodeConverter
import org.cf0x.konamiku.util.CardIdConverter

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
                            stringResource(R.string.tools_konami_panel_title),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                })
            ),
            isExpanded     = expandedBar == "konami",
            onExpandToggle = { expandedBar = if (expandedBar == "konami") null else "konami" },
            expandedContent = { KonamiConverterPanel() }
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
                            Icons.Outlined.Build, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.tools_aime_panel_title),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                })
            ),
            isExpanded     = expandedBar == "aime",
            onExpandToggle = { expandedBar = if (expandedBar == "aime") null else "aime" },
            expandedContent = { AimeConverterPanel() }
        )
    }
}

@Composable
private fun KonamiConverterPanel() {
    val KONAMI_ALPHABET = "0123456789ABCDEFGHJKLMNPRSTUWXYZ"

    var idmInput  by remember { mutableStateOf("") }
    var idmResult by remember { mutableStateOf<String?>(null) }
    var idmError  by remember { mutableStateOf<String?>(null) }

    var kidInput  by remember { mutableStateOf("") }
    var kidResult by remember { mutableStateOf<String?>(null) }
    var kidError  by remember { mutableStateOf<String?>(null) }

    val needLengthIdm = stringResource(R.string.tools_validation_need_length, idmInput.length, 16)
    val needLengthKid = stringResource(R.string.tools_validation_need_length, kidInput.length, 16)
    val hexInvalid    = stringResource(R.string.tools_validation_hex_invalid)
    val idmPrefix     = stringResource(R.string.tools_validation_idm_prefix)
    val kidInvalid    = stringResource(R.string.tools_validation_kid_invalid)

    val idmValidation: String? = when {
        idmInput.isEmpty()   -> null
        idmInput.length < 16 -> needLengthIdm
        idmInput.any { it !in '0'..'9' && it !in 'A'..'F' } -> hexInvalid
        !idmInput.startsWith("E004") && !idmInput.startsWith("0") -> idmPrefix
        else -> null
    }
    val idmReady = idmInput.length == 16 && idmValidation == null

    val kidValidation: String? = when {
        kidInput.isEmpty()   -> null
        kidInput.length < 16 -> needLengthKid
        kidInput.any { it !in KONAMI_ALPHABET } -> kidInvalid
        else -> null
    }
    val kidReady = kidInput.length == 16 && kidValidation == null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(stringResource(R.string.tools_section_idm_to_kid))

        ConverterTextField(
            value          = idmInput,
            onValueChange  = {
                idmInput  = it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(16)
                idmResult = null; idmError = null
            },
            label          = stringResource(R.string.tools_label_idm),
            placeholder    = stringResource(R.string.card_add_idm_placeholder),
            isError        = idmValidation != null,
            supportText    = idmValidation ?: stringResource(R.string.tools_char_count, idmInput.length, 16),
            keyboardType   = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters
        )
        Button(
            onClick  = {
                when (val r = CardIdConverter.toKonamiId(idmInput)) {
                    is CardIdConverter.Result.Success -> { idmResult = r.value; idmError = null }
                    is CardIdConverter.Result.Failure -> { idmError = r.reason; idmResult = null }
                }
            },
            enabled  = idmReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.tools_convert)) }

        AnimatedVisibility(visible = idmResult != null || idmError != null) {
            if (idmResult != null)
                ResultCard(stringResource(R.string.tools_label_kid_result), idmResult!!)
            else if (idmError != null)
                ErrorCard(idmError!!)
        }

        HorizontalDivider(thickness = 0.5.dp)

        SectionLabel(stringResource(R.string.tools_section_kid_to_idm))

        ConverterTextField(
            value          = kidInput,
            onValueChange  = {
                kidInput  = it.uppercase().filter { c -> c in KONAMI_ALPHABET }.take(16)
                kidResult = null; kidError = null
            },
            label          = stringResource(R.string.tools_label_kid),
            placeholder    = "FW5331K31WT1ZY2U",
            isError        = kidValidation != null,
            supportText    = kidValidation ?: stringResource(R.string.tools_char_count, kidInput.length, 16),
            keyboardType   = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters
        )
        Button(
            onClick  = {
                when (val r = CardIdConverter.toUid(kidInput)) {
                    is CardIdConverter.Result.Success -> { kidResult = r.value; kidError = null }
                    is CardIdConverter.Result.Failure -> { kidError = r.reason; kidResult = null }
                }
            },
            enabled  = kidReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.tools_convert)) }

        AnimatedVisibility(visible = kidResult != null || kidError != null) {
            if (kidResult != null)
                ResultCard(stringResource(R.string.tools_label_idm_result), kidResult!!)
            else if (kidError != null)
                ErrorCard(kidError!!)
        }
    }
}

@Composable
private fun AimeConverterPanel() {
    var idmInput  by remember { mutableStateOf("") }
    var acResult  by remember { mutableStateOf<String?>(null) }
    var idmError  by remember { mutableStateOf<String?>(null) }

    var acInput   by remember { mutableStateOf("") }
    var idmResult by remember { mutableStateOf<AimeAccessCodeConverter.Result?>(null) }
    var acError   by remember { mutableStateOf<String?>(null) }

    val acDigits = acInput.filter { it.isDigit() }

    val hexInvalid   = stringResource(R.string.tools_validation_hex_invalid)
    val max16        = stringResource(R.string.tools_validation_max_16)
    val digitsOnly   = stringResource(R.string.tools_validation_digits_only)
    val max20        = stringResource(R.string.tools_validation_max_20)

    val idmValidation: String? = when {
        idmInput.isEmpty() -> null
        idmInput.any { it !in '0'..'9' && it !in 'A'..'F' } -> hexInvalid
        idmInput.length > 16 -> max16
        else -> null
    }
    val idmReady = idmInput.isNotEmpty() && idmValidation == null

    val acValidation: String? = when {
        acInput.isEmpty() -> null
        acInput.any { !it.isDigit() && it != '-' } -> digitsOnly
        acDigits.length > 20 -> max20
        else -> null
    }
    val acReady = acDigits.isNotEmpty() && acValidation == null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(stringResource(R.string.tools_section_idm_to_ac))

        ConverterTextField(
            value          = idmInput,
            onValueChange  = {
                idmInput = it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(16)
                acResult = null; idmError = null
            },
            label          = stringResource(R.string.tools_label_idm),
            placeholder    = stringResource(R.string.card_add_idm_placeholder),
            isError        = idmValidation != null,
            supportText    = idmValidation ?: stringResource(R.string.tools_char_count, idmInput.length, 16),
            keyboardType   = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.Characters
        )
        Button(
            onClick  = {
                when (val r = AimeAccessCodeConverter.idmToAccessCode(idmInput)) {
                    is AimeAccessCodeConverter.Result.Single -> {
                        acResult = AimeAccessCodeConverter.formatAccessCode(r.value)
                        idmError = null
                    }
                    is AimeAccessCodeConverter.Result.Failure -> {
                        idmError = r.reason; acResult = null
                    }
                    else -> {}
                }
            },
            enabled  = idmReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.tools_convert)) }

        AnimatedVisibility(visible = acResult != null || idmError != null) {
            if (acResult != null)
                ResultCard(stringResource(R.string.tools_label_ac_result), acResult!!)
            else if (idmError != null)
                ErrorCard(idmError!!)
        }

        HorizontalDivider(thickness = 0.5.dp)

        SectionLabel(stringResource(R.string.tools_section_ac_to_idm))

        ConverterTextField(
            value          = acInput,
            onValueChange  = {
                acInput   = it.filter { c -> c.isDigit() }.take(20)
                idmResult = null; acError = null
            },
            label          = stringResource(R.string.tools_label_ac),
            placeholder    = "00081234123412341234",
            isError        = acValidation != null,
            supportText    = acValidation ?: stringResource(R.string.tools_char_count, acDigits.length, 20),
            keyboardType   = KeyboardType.Number,
            capitalization = KeyboardCapitalization.None
        )
        AnimatedVisibility(visible = acDigits.length == 20) {
            Text(
                text     = AimeAccessCodeConverter.formatAccessCode(acDigits),
                style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color    = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Button(
            onClick  = {
                when (val r = AimeAccessCodeConverter.accessCodeToIdm(acDigits)) {
                    is AimeAccessCodeConverter.Result.Single    -> { idmResult = r; acError = null }
                    is AimeAccessCodeConverter.Result.Ambiguous -> { idmResult = r; acError = null }
                    is AimeAccessCodeConverter.Result.Failure   -> { acError = r.reason; idmResult = null }
                }
            },
            enabled  = acReady,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.tools_convert)) }

        AnimatedVisibility(visible = idmResult != null || acError != null) {
            when (val r = idmResult) {
                is AimeAccessCodeConverter.Result.Single -> {
                    ResultCard(stringResource(R.string.tools_label_idm_result), r.value)
                }
                is AimeAccessCodeConverter.Result.Ambiguous -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text     = stringResource(R.string.tools_aime_ambiguous_warning),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        ResultCard(stringResource(R.string.tools_label_idm_positive), r.positive)
                        ResultCard(stringResource(R.string.tools_label_idm_negative), r.negative)
                    }
                }
                else -> if (acError != null) ErrorCard(acError!!)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun ConverterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    supportText: String,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value          = value,
        onValueChange  = onValueChange,
        label          = { Text(label) },
        placeholder    = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        },
        singleLine     = true,
        isError        = isError,
        supportingText = {
            Text(
                text  = supportText,
                color = if (isError) MaterialTheme.colorScheme.error
                else         LocalContentColor.current
            )
        },
        textStyle       = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
            keyboardType   = keyboardType,
            capitalization = capitalization
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultCard(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope     = rememberCoroutineScope()
    var copied    by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text  = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("", value))
                    )
                    copied = true
                    delay(2000)
                    copied = false
                }
            }) {
                Text(
                    text  = if (copied) stringResource(R.string.tools_copied)
                    else        stringResource(R.string.tools_copy),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text     = "✗ $message",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}