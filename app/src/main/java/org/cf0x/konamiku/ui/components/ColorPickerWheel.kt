package org.cf0x.konamiku.ui.components

import android.graphics.SweepGradient as AndroidSweepGradient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ColorPickerWheel(
    initialColor: Color = Color(0xFF6750A4),
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initHsv = remember(initialColor) {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initialColor.toArgb(), it)
        }
    }

    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var bri by remember { mutableStateOf(initHsv[2]) }

    val currentColor by remember(hue, sat, bri) {
        derivedStateOf { Color.hsv(hue, sat, bri) }
    }

    val hueColors = remember {
        IntArray(361) { i ->
            android.graphics.Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
    }

    var hexInput     by remember { mutableStateOf("") }
    var isEditingHex by remember { mutableStateOf(false) }
    var hexError     by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor)
        if (!isEditingHex) {
            hexInput = "#%06X".format(currentColor.toArgb() and 0xFFFFFF)
        }
    }

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val density    = LocalDensity.current
            val sizePx     = with(density) { maxWidth.toPx() }
            val ringWidth  = sizePx * 0.11f
            val outerR     = sizePx / 2f
            val innerR     = outerR - ringWidth
            val squareSide = innerR * sqrt(2f) * 0.9f
            val sqLeft     = sizePx / 2f - squareSide / 2f
            val sqTop      = sizePx / 2f - squareSide / 2f

            fun inRing(p: Offset): Boolean {
                val d = sqrt((p.x - sizePx / 2f).pow(2) + (p.y - sizePx / 2f).pow(2))
                return d in (innerR * 0.75f)..outerR
            }
            fun inSquare(p: Offset) =
                p.x in sqLeft..(sqLeft + squareSide) &&
                p.y in sqTop..(sqTop + squareSide)

            fun handleRing(p: Offset) {
                hue = (atan2(p.y - sizePx / 2f, p.x - sizePx / 2f)
                    * (180f / PI.toFloat()) + 360f) % 360f
            }
            fun handleSq(p: Offset) {
                sat = ((p.x - sqLeft) / squareSide).coerceIn(0f, 1f)
                bri = (1f - (p.y - sqTop) / squareSide).coerceIn(0f, 1f)
            }

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(squareSide, sqLeft, sqTop) {
                        detectDragGestures { change, _ ->
                            val p = change.position
                            when { inRing(p) -> handleRing(p); inSquare(p) -> handleSq(p) }
                        }
                    }
                    .pointerInput(squareSide, sqLeft, sqTop) {
                        detectTapGestures { p ->
                            when { inRing(p) -> handleRing(p); inSquare(p) -> handleSq(p) }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val midR   = (outerR + innerR) / 2f

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        shader      = AndroidSweepGradient(center.x, center.y, hueColors, null)
                        style       = android.graphics.Paint.Style.STROKE
                        strokeWidth = ringWidth
                        isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawCircle(center.x, center.y, midR, paint)
                }

                val hRad = Math.toRadians(hue.toDouble())
                val ix   = center.x + midR * cos(hRad).toFloat()
                val iy   = center.y + midR * sin(hRad).toFloat()
                drawCircle(Color.White, ringWidth * 0.48f, Offset(ix, iy), style = Stroke(2.5.dp.toPx()))
                drawCircle(Color.hsv(hue, 1f, 1f), ringWidth * 0.38f, Offset(ix, iy))

                drawRect(
                    brush   = Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.hsv(hue, 1f, 1f)),
                        startX = sqLeft, endX = sqLeft + squareSide
                    ),
                    topLeft = Offset(sqLeft, sqTop),
                    size    = Size(squareSide, squareSide)
                )
                drawRect(
                    brush   = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = sqTop, endY = sqTop + squareSide
                    ),
                    topLeft = Offset(sqLeft, sqTop),
                    size    = Size(squareSide, squareSide)
                )

                val svX = sqLeft + sat * squareSide
                val svY = sqTop  + (1f - bri) * squareSide
                drawCircle(Color.White,   9.dp.toPx(), Offset(svX, svY), style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black,   9.dp.toPx(), Offset(svX, svY), style = Stroke(1.dp.toPx()))
                drawCircle(currentColor,  6.dp.toPx(), Offset(svX, svY))
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Surface(
                modifier        = Modifier.size(48.dp),
                shape           = MaterialTheme.shapes.medium,
                color           = currentColor,
                shadowElevation = 2.dp,
                border          = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {}

            OutlinedTextField(
                value         = hexInput,
                onValueChange = { input ->
                    isEditingHex = true
                    hexError     = false
                    val clean    = input.filter {
                        it.isDigit() || it in 'A'..'F' || it in 'a'..'f' || it == '#'
                    }
                    hexInput = if (clean.startsWith("#")) clean.take(7)
                               else "#${clean.take(6)}"
                },
                label         = { Text("Hex") },
                placeholder   = { Text("#6750A4") },
                singleLine    = true,
                isError       = hexError,
                supportingText = if (hexError) {{ Text("Invalid hex, format: #RRGGBB") }} else null,
                textStyle      = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType   = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction      = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = parseHexColor(hexInput)
                        if (parsed != null) {
                            val arr = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toArgb(), arr)
                            hue      = arr[0]; sat = arr[1]; bri = arr[2]
                            hexError = false
                        } else {
                            hexError = true
                        }
                        isEditingHex = false
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun parseHexColor(input: String): Color? {
    val clean = input.removePrefix("#").trim().uppercase()
    if (clean.length != 6) return null
    if (clean.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
    return runCatching {
        Color(android.graphics.Color.parseColor("#$clean"))
    }.getOrNull()
}