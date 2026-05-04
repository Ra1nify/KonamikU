package org.cf0x.konamiku.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            text  = "Tools",
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
                            modifier = Modifier.size(20.dp))
                        Text("Konami ID  ↔  IDm",
                            style = MaterialTheme.typography.labelLarge)
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
                        Icon(Icons.Outlined.Build, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                        Text("IDm  ↔  Access Code",
                            style = MaterialTheme.typography.labelLarge)
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
    val clipboard = LocalClipboardManager.current
    val KONAMI_ALPHABET = "0123456789ABCDEFGHJKLMNPRSTUWXYZ"

    var idmInput  by remember { mutableStateOf("") }
    var idmResult by remember { mutableStateOf<String?>(null) }
    var idmError  by remember { mutableStateOf<String?>(null) }

    var kidInput  by remember { mutableStateOf("") }
    var kidResult by remember { mutableStateOf<String?>(null) }
    var kidError  by remember { mutableStateOf<String?>(null) }

    val idmValidation: String? = when {
        idmInput.isEmpty()   -> null
        idmInput.length < 16 -> "需要16位，当前 ${idmInput.length} 位"
        idmInput.any { it !in '0'..'9' && it !in 'A'..'F' } -> "包含无效十六进制字符"
        !idmInput.startsWith("E004") && !idmInput.startsWith("0") ->
            "IDm 须以 E004（磁卡）或 0（FeliCa）开头"
        else -> null
    }
    val idmReady = idmInput.length == 16 && idmValidation == null

    val kidValidation: String? = when {
        kidInput.isEmpty()   -> null
        kidInput.length < 16 -> "需要16位，当前 ${kidInput.length} 位"
        kidInput.any { it !in KONAMI_ALPHABET } -> "包含无效字符"
        else -> null
    }
    val kidReady = kidInput.length == 16 && kidValidation == null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("IDm → Konami ID")
        ConverterTextField(
            value          = idmInput,
            onValueChange  = {
                idmInput  = it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(16)
                idmResult = null; idmError = null
            },
            label          = "IDm（16位十六进制）",
            placeholder    = "012E456789ABCDEF",
            maxLength      = 16,
            isError        = idmValidation != null,
            supportText    = idmValidation ?: "${idmInput.length} / 16",
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
        ) { Text("转换") }
        AnimatedVisibility(visible = idmResult != null || idmError != null) {
            if (idmResult != null)
                ResultCard("Konami ID", idmResult!!) { clipboard.setText(AnnotatedString(idmResult!!)) }
            else if (idmError != null)
                ErrorCard(idmError!!)
        }

        HorizontalDivider(thickness = 0.5.dp)

        SectionLabel("Konami ID → IDm")
        ConverterTextField(
            value          = kidInput,
            onValueChange  = {
                kidInput  = it.uppercase().filter { c -> c in KONAMI_ALPHABET }.take(16)
                kidResult = null; kidError = null
            },
            label          = "Konami ID（16位）",
            placeholder    = "FW5331K31WT1ZY2U",
            maxLength      = 16,
            isError        = kidValidation != null,
            supportText    = kidValidation ?: "${kidInput.length} / 16",
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
        ) { Text("转换") }
        AnimatedVisibility(visible = kidResult != null || kidError != null) {
            if (kidResult != null)
                ResultCard("IDm", kidResult!!) { clipboard.setText(AnnotatedString(kidResult!!)) }
            else if (kidError != null)
                ErrorCard(kidError!!)
        }
    }
}

@Composable
private fun AimeConverterPanel() {
    val clipboard = LocalClipboardManager.current

    var idmInput  by remember { mutableStateOf("") }
    var acResult  by remember { mutableStateOf<String?>(null) }
    var idmError  by remember { mutableStateOf<String?>(null) }

    var acInput   by remember { mutableStateOf("") }
    var idmResult by remember { mutableStateOf<AimeAccessCodeConverter.Result?>(null) }
    var acError   by remember { mutableStateOf<String?>(null) }

    val acDigits = acInput.filter { it.isDigit() }

    val idmValidation: String? = when {
        idmInput.isEmpty() -> null
        idmInput.any { it !in '0'..'9' && it !in 'A'..'F' } -> "包含无效十六进制字符"
        idmInput.length > 16 -> "最多16位"
        else -> null
    }
    val idmReady = idmInput.isNotEmpty() && idmValidation == null

    val acValidation: String? = when {
        acInput.isEmpty() -> null
        acInput.any { !it.isDigit() && it != '-' } -> "只能包含数字和连字符"
        acDigits.length > 20 -> "最多20位数字"
        else -> null
    }
    val acReady = acDigits.isNotEmpty() && acValidation == null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("IDm → Access Code")
        ConverterTextField(
            value          = idmInput,
            onValueChange  = {
                idmInput = it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(16)
                acResult = null; idmError = null
            },
            label          = "IDm（最多16位十六进制）",
            placeholder    = "012E456789ABCDEF",
            maxLength      = 16,
            isError        = idmValidation != null,
            supportText    = idmValidation ?: "${idmInput.length} / 16",
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
        ) { Text("转换") }
        AnimatedVisibility(visible = acResult != null || idmError != null) {
            if (acResult != null)
                ResultCard("Access Code", acResult!!) {
                    clipboard.setText(AnnotatedString(acResult!!))
                }
            else if (idmError != null)
                ErrorCard(idmError!!)
        }

        HorizontalDivider(thickness = 0.5.dp)

        SectionLabel("Access Code → IDm")
        ConverterTextField(
            value          = acInput,
            onValueChange  = {
                acInput   = it.filter { c -> c.isDigit() }.take(20)
                idmResult = null; acError = null
            },
            label          = "Access Code（20位数字）",
            placeholder    = "00081234123412341234",
            maxLength      = 20,
            isError        = acValidation != null,
            supportText    = acValidation ?: "${acDigits.length} / 20",
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
        ) { Text("转换") }
        AnimatedVisibility(visible = idmResult != null || acError != null) {
            when (val r = idmResult) {
                is AimeAccessCodeConverter.Result.Single -> {
                    ResultCard("IDm", r.value) { clipboard.setText(AnnotatedString(r.value)) }
                }
                is AimeAccessCodeConverter.Result.Ambiguous -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "⚠ 存在两种可能结果（转换过程负号丢失）。\n" +
                                "AiMe FeliCa 卡通常为正数范围，优先使用第一个结果。",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        ResultCard("IDm（正数，更常见）", r.positive) {
                            clipboard.setText(AnnotatedString(r.positive))
                        }
                        ResultCard("IDm（负数补码）", r.negative) {
                            clipboard.setText(AnnotatedString(r.negative))
                        }
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
    maxLength: Int,
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
            Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ))
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
private fun ResultCard(label: String, value: String, onCopy: () -> Unit) {
    val scope   = rememberCoroutineScope()
    var copied  by remember { mutableStateOf(false) }

    Surface(
        color  = MaterialTheme.colorScheme.primaryContainer,
        shape  = MaterialTheme.shapes.medium
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
                onCopy()
                copied = true
                scope.launch { delay(2000); copied = false }
            }) {
                Text(
                    text  = if (copied) "已复制" else "复制",
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