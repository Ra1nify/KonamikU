package org.cf0x.konamiku.ui.components

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ConverterField(
    val label: String,
    val placeholder: String,
    val keyboardType: KeyboardType        = KeyboardType.Ascii,
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    val maxLength: Int                    = Int.MAX_VALUE,
    val filter: (String) -> String        = { it },
    val validate: (String) -> Boolean,
)

sealed class ConvertResult {
    data class Success(val value: String)                  : ConvertResult()
    data class Warning(val value: String, val note: String): ConvertResult()
    data class Failure(val reason: String)                 : ConvertResult()
    object Skip                                            : ConvertResult()
}

@Composable
fun ReorderableConverter(
    fields: List<ConverterField>,
    onConvert: (sourceIndex: Int, value: String) -> List<ConvertResult>,
    modifier: Modifier = Modifier
) {
    require(fields.isNotEmpty())

    var displayOrder  by remember { mutableStateOf(fields.indices.toList()) }
    val values         = remember { mutableStateListOf(*Array(fields.size) { "" }) }
    val errors         = remember { mutableStateListOf<String?>(*arrayOfNulls(fields.size)) }
    val resultNotes    = remember { mutableStateListOf<String?>(*arrayOfNulls(fields.size)) }
    val isResult       = remember { mutableStateListOf(*Array(fields.size) { false }) }
    val focusRequester = remember { FocusRequester() }
    var focusPending   by remember { mutableStateOf(false) }

    val sourceIdx = displayOrder[0]
    val canConvert = fields[sourceIdx].validate(values[sourceIdx])

    fun clearResults() {
        for (i in isResult.indices)    isResult[i]    = false
        for (i in errors.indices)      errors[i]      = null
        for (i in resultNotes.indices) resultNotes[i] = null
    }

    fun activateField(fieldIndex: Int) {
        if (fieldIndex == displayOrder[0]) return
        displayOrder  = listOf(fieldIndex) + displayOrder.filter { it != fieldIndex }
        focusPending  = true
        clearResults()
    }

    fun doConvert() {
        val src     = displayOrder[0]
        val results = onConvert(src, values[src])
        results.forEachIndexed { idx, result ->
            when (result) {
                is ConvertResult.Success -> {
                    values[idx]      = result.value
                    errors[idx]      = null
                    resultNotes[idx] = null
                    isResult[idx]    = true
                }
                is ConvertResult.Warning -> {
                    values[idx]      = result.value
                    errors[idx]      = null
                    resultNotes[idx] = result.note
                    isResult[idx]    = true
                }
                is ConvertResult.Failure -> {
                    values[idx]      = ""
                    errors[idx]      = result.reason
                    resultNotes[idx] = null
                    isResult[idx]    = false
                }
                ConvertResult.Skip -> Unit
            }
        }
    }

    LaunchedEffect(displayOrder) {
        if (focusPending) {
            delay(220)
            runCatching { focusRequester.requestFocus() }
            focusPending = false
        }
    }

    Column(modifier = modifier) {
        AnimatedContent(
            targetState  = displayOrder,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.97f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.97f))
            },
            label = "converter_reorder"
        ) { order ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val src = order[0]

                FieldItem(
                    field         = fields[src],
                    value         = values[src],
                    onValueChange = { v ->
                        values[src] = fields[src].filter(v)
                        clearResults()
                    },
                    error         = errors[src],
                    resultNote    = null,
                    isResult      = false,
                    isSource      = true,
                    onActivate    = null,
                    focusRequester = focusRequester
                )

                Button(
                    onClick  = ::doConvert,
                    enabled  = canConvert,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector        = Icons.Filled.SwapVert,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Convert")
                }

                order.drop(1).forEach { idx ->
                    FieldItem(
                        field          = fields[idx],
                        value          = values[idx],
                        onValueChange  = { v ->
                            values[idx]   = fields[idx].filter(v)
                            isResult[idx] = false
                            errors[idx]   = null
                            resultNotes[idx] = null
                        },
                        error          = errors[idx],
                        resultNote     = resultNotes[idx],
                        isResult       = isResult[idx],
                        isSource       = false,
                        onActivate     = { activateField(idx) },
                        focusRequester = null
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldItem(
    field: ConverterField,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    resultNote: String?,
    isResult: Boolean,
    isSource: Boolean,
    onActivate: (() -> Unit)?,
    focusRequester: FocusRequester?,
) {
    val clipboard = LocalClipboard.current
    val scope     = rememberCoroutineScope()
    var copied    by remember { mutableStateOf(false) }

    val containerColor = when {
        isResult -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else     -> Color.Unspecified
    }

    val supportingText: (@Composable () -> Unit)? = when {
        error      != null -> {{ Text(error, color = MaterialTheme.colorScheme.error) }}
        resultNote != null -> {{ Text(resultNote, color = MaterialTheme.colorScheme.tertiary) }}
        isSource           -> {{ Text("${value.length}${if (field.maxLength != Int.MAX_VALUE) " / ${field.maxLength}" else ""}",
            color = LocalContentColor.current.copy(alpha = 0.6f)) }}
        else               -> null
    }

    Box {
        OutlinedTextField(
            value          = value,
            onValueChange  = onValueChange,
            readOnly       = !isSource,
            label          = { Text(field.label) },
            placeholder    = {
                Text(
                    field.placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            },
            singleLine     = true,
            isError        = error != null,
            supportingText = supportingText,
            trailingIcon   = if (isResult && value.isNotEmpty()) {{
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", value)))
                            copied = true
                            delay(1800)
                            copied = false
                        }
                    },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text  = if (copied) "✓" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }} else null,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = containerColor,
                focusedContainerColor   = containerColor,
                disabledContainerColor  = containerColor,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                keyboardType   = field.keyboardType,
                capitalization = field.capitalization
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { fs ->
                    if (fs.isFocused && onActivate != null) onActivate()
                }
        )
    }
}