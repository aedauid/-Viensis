package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.audio.RecorderState
import com.example.audio.RecorderStatus
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.ui.theme.BlueprintThemeStyle
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import com.example.ui.theme.pairedThemeStyle
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

enum class NotepadMode(val label: String) {
    WRITE("WRITE"),
    DRAW("DRAW"),
    REC("REC")
}

enum class DrawTool(val label: String, val alpha: Float, val widthMultiplier: Float) {
    PEN("PEN", 1.0f, 1.0f),
    PENCIL("PENCIL", 0.62f, 0.72f),
    MARKER("MARKER", 0.42f, 2.3f),
    ERASER("ERASER", 1.0f, 2.5f)
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

data class DrawingStroke(
    val points: List<StrokePoint>,
    val colorArgb: Int,
    val baseWidth: Float,
    val alpha: Float,
    val isEraser: Boolean
)

val DrawingSwatches = listOf(
    Color(0xFF14213D), // Navy
    Color(0xFFC9A227), // Gold
    Color(0xFF2B7A5B), // Forest Emerald
    Color(0xFF17859B), // Glade Teal
    Color(0xFF6D3B8E), // Storybook Plum
    Color(0xFFC1121F), // Crimson
    Color(0xFF111111), // Ink Black
    Color(0xFFFFFFFF)  // Paper White
)

fun renderStrokesToBase64Png(
    strokes: List<DrawingStroke>,
    sourceWidth: Int,
    sourceHeight: Int,
    paperBgColor: Color,
    gridColor: Color,
    backgroundStyle: NotepadBackgroundStyle = NotepadBackgroundStyle.SQUARES
): String {
    val outW = 720
    val outH = if (sourceWidth > 0 && sourceHeight > 0) {
        ((outW.toFloat() / sourceWidth.toFloat()) * sourceHeight.toFloat()).toInt().coerceIn(480, 1280)
    } else 720
    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(paperBgColor.toArgb())

    // Paper quality pattern matching selected NotepadBackgroundStyle
    val gridPaint = Paint().apply {
        color = gridColor.toArgb()
        strokeWidth = 1.5f
    }
    when (backgroundStyle) {
        NotepadBackgroundStyle.SQUARES -> {
            val step = 36f
            var gx = step
            while (gx < outW) {
                canvas.drawLine(gx, 0f, gx, outH.toFloat(), gridPaint)
                gx += step
            }
            var gy = step
            while (gy < outH) {
                canvas.drawLine(0f, gy, outW.toFloat(), gy, gridPaint)
                gy += step
            }
        }
        NotepadBackgroundStyle.LINES -> {
            val rowH = 44f
            var gy = rowH
            while (gy < outH) {
                canvas.drawLine(0f, gy, outW.toFloat(), gy, gridPaint)
                gy += rowH
            }
            val marginPaint = Paint().apply {
                color = android.graphics.Color.argb(140, 210, 60, 70)
                strokeWidth = 2.4f
            }
            canvas.drawLine(52f, 0f, 52f, outH.toFloat(), marginPaint)
        }
        NotepadBackgroundStyle.EMPTY -> Unit
        NotepadBackgroundStyle.GUTENBERG_PRESS -> {
            val laidPaint = Paint().apply {
                color = android.graphics.Color.argb(45, 195, 145, 45)
                strokeWidth = 1.1f
            }
            var gy = 16f
            while (gy < outH) {
                canvas.drawLine(0f, gy, outW.toFloat(), gy, laidPaint)
                gy += 16f
            }
            val rubricPaint = Paint().apply {
                color = android.graphics.Color.argb(170, 200, 50, 50)
                style = Paint.Style.STROKE
                strokeWidth = 2.6f
            }
            canvas.drawRect(14f, 14f, outW - 14f, outH - 14f, rubricPaint)
        }
        NotepadBackgroundStyle.DOT_MATRIX -> {
            val step = 30f
            var gy = step
            while (gy < outH) {
                var gx = step
                while (gx < outW) {
                    canvas.drawCircle(gx, gy, 2.2f, gridPaint)
                    gx += step
                }
                gy += step
            }
        }
        NotepadBackgroundStyle.MUSIC_STAFF -> {
            var topY = 28f
            while (topY + 48f < outH) {
                for (i in 0..4) {
                    val ly = topY + i * 12f
                    canvas.drawLine(14f, ly, outW - 14f, ly, gridPaint)
                }
                topY += 92f
            }
        }
    }

    val sx = if (sourceWidth > 0) outW.toFloat() / sourceWidth.toFloat() else 1f
    val sy = if (sourceHeight > 0) outH.toFloat() / sourceHeight.toFloat() else 1f
    val scale = minOf(sx, sy)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    for (stroke in strokes) {
        if (stroke.points.isEmpty()) continue
        paint.color = if (stroke.isEraser) paperBgColor.toArgb() else stroke.colorArgb
        paint.alpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
        val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.5f)
        paint.strokeWidth = (stroke.baseWidth * scale * avgPressure).coerceAtLeast(2f)

        val path = AndroidPath()
        val p0 = stroke.points.first()
        path.moveTo(p0.x * sx, p0.y * sy)
        if (stroke.points.size == 1) {
            path.lineTo(p0.x * sx + 0.5f, p0.y * sy + 0.5f)
        } else {
            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                val midX = ((prev.x + curr.x) * 0.5f) * sx
                val midY = ((prev.y + curr.y) * 0.5f) * sy
                path.quadTo(prev.x * sx, prev.y * sy, midX, midY)
            }
            val last = stroke.points.last()
            path.lineTo(last.x * sx, last.y * sy)
        }
        canvas.drawPath(path, paint)
    }

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 92, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}

@Composable
fun NotepadCard(
    mode: NotepadMode,
    onModeChange: (NotepadMode) -> Unit,
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    strokes: List<DrawingStroke>,
    redoCount: Int,
    onAddStroke: (DrawingStroke) -> Unit,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onSaveDrawing: (IntSize) -> Unit,
    recorderState: RecorderState,
    recordOverTrack: Boolean,
    onToggleRecordOverTrack: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePreviewRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onSaveRecording: (String) -> Unit,
    onDownloadRecording: (File) -> Unit,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasFinishedTrack: Boolean,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    figureStyle: MarchingFigureStyle,
    onSelectFigure: (MarchingFigureStyle) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    themeStyle: BlueprintThemeStyle,
    notepadBackground: NotepadBackgroundStyle = NotepadBackgroundStyle.SQUARES,
    onCycleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    var showFullScreenDraw by remember { mutableStateOf(false) }
    var showFullScreenWrite by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(DrawTool.PEN) }
    var activeColor by remember { mutableStateOf(DrawingSwatches[0]) }
    var strokeSize by remember { mutableFloatStateOf(4.5f) }
    var inlineCanvasSize by remember { mutableStateOf(IntSize(400, 400)) }

    BlueprintCard(
        modifier = modifier,
        accentBarColor = when (mode) {
            NotepadMode.WRITE -> tokens.gold
            NotepadMode.DRAW -> tokens.tealAccent
            NotepadMode.REC -> tokens.coralAccent
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header + Mode Switcher
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "NOTEPAD",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.6.sp,
                            color = tokens.inkPrimary
                        )
                        Text(
                            text = notepadBackground.shortLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.sp,
                            color = tokens.tealAccent,
                            modifier = Modifier
                                .border(1.dp, tokens.tealAccent.copy(alpha = 0.65f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (mode == NotepadMode.DRAW || mode == NotepadMode.WRITE) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(tokens.tealAccent.copy(alpha = 0.14f))
                                .border(1.dp, tokens.tealAccent)
                                .clickable {
                                    if (mode == NotepadMode.DRAW) showFullScreenDraw = true
                                    else showFullScreenWrite = true
                                }
                                .testTag("notepad_expand_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = if (mode == NotepadMode.DRAW) "Expand drawing canvas" else "Expand note editor",
                                tint = tokens.inkPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (recorderState.status == RecorderStatus.RECORDING) tokens.coralAccent
                                    else tokens.gridMajor,
                                    CircleShape
                                )
                        )
                    }
                }

                // Mode Tabs: WRITE | DRAW | REC
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, tokens.border),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NotepadMode.entries.forEach { tab ->
                        val selected = tab == mode
                        val tabColor = when (tab) {
                            NotepadMode.WRITE -> tokens.gold
                            NotepadMode.DRAW -> tokens.tealAccent
                            NotepadMode.REC -> tokens.coralAccent
                        }
                        val selectedTextColor = if (tab == NotepadMode.WRITE) tokens.goldText else Color.White
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .background(if (selected) tabColor else Color.Transparent)
                                .clickable { onModeChange(tab) }
                                .testTag("notepad_mode_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp,
                                color = if (selected) selectedTextColor else tokens.inkPrimary
                            )
                        }
                    }
                }
            }

            // Body by mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                when (mode) {
                    NotepadMode.WRITE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(tokens.paperBg.copy(alpha = 0.55f))
                                .drawBehind { drawNotepadPaperPattern(notepadBackground, tokens) }
                                .border(1.dp, tokens.gridMajor)
                                .padding(6.dp)
                        ) {
                            BasicTextField(
                                value = noteText,
                                onValueChange = onNoteTextChange,
                                textStyle = TextStyle(
                                    fontFamily = GaeguFontFamily,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    color = tokens.inkPrimary
                                ),
                                cursorBrush = SolidColor(tokens.gold),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("notepad_write_input"),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (noteText.isEmpty()) {
                                            Text(
                                                text = "hooks, half-lines,\nideas...",
                                                fontFamily = GaeguFontFamily,
                                                fontSize = 18.sp,
                                                lineHeight = 22.sp,
                                                color = tokens.inkMuted.copy(alpha = 0.7f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    NotepadMode.DRAW -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Mini tool strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    DrawingSwatches.take(4).forEach { swatch ->
                                        val isChosen = activeColor == swatch && activeTool != DrawTool.ERASER
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(swatch)
                                                .border(
                                                    if (isChosen) 2.dp else 1.dp,
                                                    if (isChosen) tokens.gold else tokens.border
                                                )
                                                .clickable {
                                                    activeColor = swatch
                                                    if (activeTool == DrawTool.ERASER) activeTool = DrawTool.PEN
                                                }
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "UNDO",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 8.sp,
                                        color = if (strokes.isNotEmpty()) tokens.inkPrimary else tokens.inkMuted,
                                        modifier = Modifier
                                            .clickable(enabled = strokes.isNotEmpty(), onClick = onUndoStroke)
                                            .padding(2.dp)
                                    )
                                    Text(
                                        text = "CLR",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 8.sp,
                                        color = if (strokes.isNotEmpty()) tokens.danger else tokens.inkMuted,
                                        modifier = Modifier
                                            .clickable(enabled = strokes.isNotEmpty(), onClick = onClearStrokes)
                                            .padding(2.dp)
                                    )
                                }
                            }

                            InlineDrawingCanvas(
                                strokes = strokes,
                                activeTool = activeTool,
                                activeColor = activeColor,
                                strokeSize = strokeSize,
                                notepadBackground = notepadBackground,
                                onAddStroke = onAddStroke,
                                onSizeUpdated = { inlineCanvasSize = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, tokens.gridMajor)
                            )
                        }
                    }

                    NotepadMode.REC -> {
                        RecorderInlineContent(
                            recorderState = recorderState,
                            recordOverTrack = recordOverTrack,
                            onToggleRecordOverTrack = onToggleRecordOverTrack,
                            onStartRecording = onStartRecording,
                            onPauseRecording = onPauseRecording,
                            onResumeRecording = onResumeRecording,
                            onStopRecording = onStopRecording,
                            onTogglePreview = onTogglePreviewRecording,
                            onDiscard = onDiscardRecording,
                            onSave = onSaveRecording,
                            onDownload = onDownloadRecording
                        )
                    }
                }
            }

            // Bottom Save CTA for WRITE and DRAW modes (REC has its own Save/Discard inside)
            if (mode == NotepadMode.WRITE || mode == NotepadMode.DRAW) {
                Button(
                    onClick = {
                        if (mode == NotepadMode.WRITE) onSaveNote() else onSaveDrawing(inlineCanvasSize)
                    },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.gold,
                        contentColor = tokens.goldText
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("notepad_save_button")
                ) {
                    Text(
                        text = "SAVE",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }

    if (showFullScreenWrite) {
        FullScreenWriteDialog(
            noteText = noteText,
            onNoteTextChange = onNoteTextChange,
            onSave = {
                onSaveNote()
                showFullScreenWrite = false
            },
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            hasFinishedTrack = hasFinishedTrack,
            reducedMotion = reducedMotion,
            hapticsEnabled = hapticsEnabled,
            figureStyle = figureStyle,
            onSelectFigure = onSelectFigure,
            onTogglePlayPause = onTogglePlayPause,
            onSeekFraction = onSeekFraction,
            themeStyle = themeStyle,
            notepadBackground = notepadBackground,
            onCycleTheme = onCycleTheme,
            onDismiss = { showFullScreenWrite = false }
        )
    }

    if (showFullScreenDraw) {
        FullScreenDrawingDialog(
            strokes = strokes,
            redoCount = redoCount,
            activeTool = activeTool,
            onToolSelected = { activeTool = it },
            activeColor = activeColor,
            onColorSelected = { activeColor = it },
            strokeSize = strokeSize,
            onStrokeSizeChange = { strokeSize = it },
            onAddStroke = onAddStroke,
            onUndo = onUndoStroke,
            onRedo = onRedoStroke,
            onClear = onClearStrokes,
            onSave = { size ->
                onSaveDrawing(size)
                showFullScreenDraw = false
            },
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            hasFinishedTrack = hasFinishedTrack,
            reducedMotion = reducedMotion,
            hapticsEnabled = hapticsEnabled,
            figureStyle = figureStyle,
            onSelectFigure = onSelectFigure,
            onTogglePlayPause = onTogglePlayPause,
            onSeekFraction = onSeekFraction,
            themeStyle = themeStyle,
            notepadBackground = notepadBackground,
            onCycleTheme = onCycleTheme,
            onDismiss = { showFullScreenDraw = false }
        )
    }
}

@Composable
private fun InlineDrawingCanvas(
    strokes: List<DrawingStroke>,
    activeTool: DrawTool,
    activeColor: Color,
    strokeSize: Float,
    notepadBackground: NotepadBackgroundStyle,
    onAddStroke: (DrawingStroke) -> Unit,
    onSizeUpdated: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    var currentPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(tokens.paperBg.copy(alpha = 0.6f))
            .onSizeChanged(onSizeUpdated)
            .pointerInput(activeTool, activeColor, strokeSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val pts = mutableListOf(
                        StrokePoint(down.position.x, down.position.y, down.pressure.coerceIn(0.4f, 1.8f))
                    )
                    currentPoints = pts.toList()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            change.consume()
                            pts.add(
                                StrokePoint(
                                    change.position.x,
                                    change.position.y,
                                    change.pressure.coerceIn(0.4f, 1.8f)
                                )
                            )
                            currentPoints = pts.toList()
                        } else {
                            break
                        }
                    } while (true)
                    if (pts.isNotEmpty()) {
                        onAddStroke(
                            DrawingStroke(
                                points = pts,
                                colorArgb = activeColor.toArgb(),
                                baseWidth = strokeSize * activeTool.widthMultiplier,
                                alpha = activeTool.alpha,
                                isEraser = activeTool == DrawTool.ERASER
                            )
                        )
                    }
                    currentPoints = emptyList()
                }
            }
            .testTag("drawing_canvas_inline")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawNotepadPaperPattern(notepadBackground, tokens)
            drawStrokesOnCanvas(strokes, tokens.paperBg)
            if (currentPoints.isNotEmpty()) {
                drawSingleStroke(
                    DrawingStroke(
                        points = currentPoints,
                        colorArgb = activeColor.toArgb(),
                        baseWidth = strokeSize * activeTool.widthMultiplier,
                        alpha = activeTool.alpha,
                        isEraser = activeTool == DrawTool.ERASER
                    ),
                    tokens.paperBg
                )
            }
        }
        if (strokes.isEmpty() && currentPoints.isEmpty()) {
            Text(
                text = "sketch here...",
                fontFamily = GaeguFontFamily,
                fontSize = 16.sp,
                color = tokens.inkMuted.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokesOnCanvas(
    strokes: List<DrawingStroke>,
    eraserColor: Color
) {
    for (stroke in strokes) {
        drawSingleStroke(stroke, eraserColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSingleStroke(
    stroke: DrawingStroke,
    eraserColor: Color
) {
    if (stroke.points.isEmpty()) return
    val color = if (stroke.isEraser) eraserColor else Color(stroke.colorArgb).copy(alpha = stroke.alpha)
    val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.6f, 1.5f)
    val strokeW = (stroke.baseWidth * avgPressure).coerceAtLeast(1.5f)

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x, first.y)
    if (stroke.points.size == 1) {
        path.lineTo(first.x + 0.5f, first.y + 0.5f)
    } else {
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            path.quadraticTo(prev.x, prev.y, (prev.x + curr.x) * 0.5f, (prev.y + curr.y) * 0.5f)
        }
        val last = stroke.points.last()
        path.lineTo(last.x, last.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

@Composable
private fun RecorderInlineContent(
    recorderState: RecorderState,
    recordOverTrack: Boolean,
    onToggleRecordOverTrack: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTogglePreview: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String) -> Unit,
    onDownload: (File) -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var takeTitle by remember { mutableStateOf("Vocal Take") }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            onStartRecording()
        } else {
            permissionDenied = true
        }
    }

    fun requestOrStart() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            onStartRecording()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (permissionDenied) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Mic permission needed to record vocal ideas.",
                    fontFamily = GaeguFontFamily,
                    fontSize = 15.sp,
                    color = tokens.inkPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { requestOrStart() },
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, tokens.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "ALLOW MIC",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = tokens.inkPrimary
                    )
                }
            }
            return
        }

        when (recorderState.status) {
            RecorderStatus.IDLE -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.gridMajor)
                            .clickable(onClick = onToggleRecordOverTrack)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (recordOverTrack) "OVER TRACK (MIC ON)" else "SOLO TAKE (MIC)",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp,
                            color = if (recordOverTrack) tokens.tagBlue else tokens.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (recordOverTrack) tokens.gold else Color.Transparent)
                                .border(1.dp, tokens.border)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(tokens.danger, CircleShape)
                            .border(2.dp, tokens.border, CircleShape)
                            .clickable { requestOrStart() }
                            .testTag("record_start_button")
                            .semantics { contentDescription = "Start voice recording" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = recorderState.errorMessage ?: "TAP TO RECORD",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = if (recorderState.errorMessage != null) tokens.danger else tokens.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            RecorderStatus.RECORDING, RecorderStatus.PAUSED -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatTimeMs(recorderState.elapsedMs),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (recorderState.status == RecorderStatus.RECORDING) tokens.danger else tokens.inkPrimary
                    )

                    // Live waveform
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .border(1.dp, tokens.gridMajor)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        val bars = recorderState.amplitudes
                        val barW = (size.width / bars.size.coerceAtLeast(1)) * 0.65f
                        val step = size.width / bars.size.coerceAtLeast(1)
                        bars.forEachIndexed { idx, amp ->
                            val h = (amp * size.height).coerceAtLeast(3f)
                            val x = idx * step + (step - barW) * 0.5f
                            val y = (size.height - h) * 0.5f
                            drawRect(
                                color = if (recorderState.status == RecorderStatus.RECORDING) tokens.gold else tokens.inkMuted,
                                topLeft = Offset(x, y),
                                size = androidx.compose.ui.geometry.Size(barW, h)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (recorderState.status == RecorderStatus.RECORDING) onPauseRecording() else onResumeRecording()
                            },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.border),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (recorderState.status == RecorderStatus.RECORDING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Pause or resume",
                                tint = tokens.inkPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Button(
                            onClick = onStopRecording,
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.border),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.danger, contentColor = Color.White),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .testTag("record_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop recording",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            RecorderStatus.REVIEW -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicTextField(
                        value = takeTitle,
                        onValueChange = { takeTitle = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = tokens.inkPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.border)
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(tokens.gold)
                                .border(1.dp, tokens.border)
                                .clickable(onClick = onTogglePreview),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (recorderState.isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play recorded take",
                                tint = tokens.goldText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = formatTimeMs(recorderState.elapsedMs),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = tokens.inkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        recorderState.recordedFile?.let { f ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(1.dp, tokens.border)
                                    .clickable { onDownload(f) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download take",
                                    tint = tokens.inkPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDiscard,
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.border),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        ) {
                            Text("DROP", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkPrimary)
                        }
                        Button(
                            onClick = { onSave(takeTitle.ifBlank { "Vocal Take" }) },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.border),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(30.dp)
                                .testTag("record_save_button")
                        ) {
                            Text("SAVE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenMarchingCompanionBar(
    figureStyle: MarchingFigureStyle,
    onSelectFigure: (MarchingFigureStyle) -> Unit,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    themeStyle: BlueprintThemeStyle,
    onCycleTheme: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.transportBg)
            .border(1.5.dp, tokens.border)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(tokens.gold)
                        .border(1.dp, tokens.border)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause track" else "Play track",
                        tint = tokens.goldText,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "MARCHING · AUTO ${figureStyle.pairedThemeStyle().shortLabel}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.0.sp,
                    color = tokens.inkPrimary
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, tokens.border)
                    .background(tokens.cardBg)
                    .clickable(onClick = onCycleTheme)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("fullscreen_cycle_theme_button")
            ) {
                Text(
                    text = "STYLE: ${themeStyle.shortLabel}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = tokens.inkPrimary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MarchingFigureStyle.entries.forEach { fig ->
                val selected = fig == figureStyle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .background(if (selected) tokens.inkPrimary else tokens.cardBg)
                        .border(1.dp, if (selected) tokens.gold else tokens.border)
                        .clickable { onSelectFigure(fig) }
                        .testTag("fullscreen_figure_${fig.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fig.shortLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = if (selected) tokens.cardBg else tokens.inkPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenWriteDialog(
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onSave: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasFinishedTrack: Boolean,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    figureStyle: MarchingFigureStyle,
    onSelectFigure: (MarchingFigureStyle) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    themeStyle: BlueprintThemeStyle,
    notepadBackground: NotepadBackgroundStyle = NotepadBackgroundStyle.SQUARES,
    onCycleTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val lineCount = remember(noteText) { noteText.lines().count { it.isNotBlank() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BlueprintGridBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.cardBg)
                        .border(2.dp, tokens.border)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NOTEPAD // FULL SCREEN",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.6.sp,
                            color = tokens.inkPrimary
                        )
                        Text(
                            text = "$lineCount lines · autosaved",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = tokens.inkMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onSave,
                            shape = RectangleShape,
                            border = BorderStroke(1.5.dp, tokens.border),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("SAVE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .border(1.5.dp, tokens.border)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit full screen notes", tint = tokens.inkPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Visible Marching Figure Selector + Play/Theme companion bar
                FullScreenMarchingCompanionBar(
                    figureStyle = figureStyle,
                    onSelectFigure = onSelectFigure,
                    isPlaying = isPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    themeStyle = themeStyle,
                    onCycleTheme = onCycleTheme
                )

                // Main Paper Editor + Live Marching Progress Rail side-by-side
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(tokens.cardBg)
                            .drawBehind { drawNotepadPaperPattern(notepadBackground, tokens) }
                            .border(2.dp, tokens.border)
                            .padding(16.dp)
                    ) {
                        BasicTextField(
                            value = noteText,
                            onValueChange = onNoteTextChange,
                            textStyle = TextStyle(
                                fontFamily = GaeguFontFamily,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                color = tokens.inkPrimary
                            ),
                            cursorBrush = SolidColor(tokens.gold),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fullscreen_write_input"),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (noteText.isEmpty()) {
                                        Text(
                                            text = "hooks, verses, bridges, studio thoughts...",
                                            fontFamily = GaeguFontFamily,
                                            fontSize = 22.sp,
                                            lineHeight = 29.sp,
                                            color = tokens.inkMuted.copy(alpha = 0.65f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    MarchingFigureProgressBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        hasFinishedTrack = hasFinishedTrack,
                        reducedMotion = reducedMotion,
                        hapticsEnabled = hapticsEnabled,
                        figureStyle = figureStyle,
                        onOpenFigurePicker = {
                            val entries = MarchingFigureStyle.entries
                            onSelectFigure(entries[(entries.indexOf(figureStyle) + 1) % entries.size])
                        },
                        onSeekFraction = onSeekFraction,
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenDrawingDialog(
    strokes: List<DrawingStroke>,
    redoCount: Int,
    activeTool: DrawTool,
    onToolSelected: (DrawTool) -> Unit,
    activeColor: Color,
    onColorSelected: (Color) -> Unit,
    strokeSize: Float,
    onStrokeSizeChange: (Float) -> Unit,
    onAddStroke: (DrawingStroke) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSave: (IntSize) -> Unit,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasFinishedTrack: Boolean,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    figureStyle: MarchingFigureStyle,
    onSelectFigure: (MarchingFigureStyle) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    themeStyle: BlueprintThemeStyle,
    notepadBackground: NotepadBackgroundStyle = NotepadBackgroundStyle.SQUARES,
    onCycleTheme: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    var relaxMode by remember { mutableStateOf(false) }
    var toolbarVisible by remember { mutableStateOf(true) }
    var lastInteractionTick by remember { mutableStateOf(0L) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize(720, 1080)) }
    var currentPoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    LaunchedEffect(relaxMode, lastInteractionTick) {
        if (relaxMode) {
            toolbarVisible = true
            delay(3200)
            toolbarVisible = false
        } else {
            toolbarVisible = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BlueprintGridBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Fullscreen Drawing Canvas with pinch-zoom & single-finger draw
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .border(2.dp, tokens.border)
                            .clipToBounds()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(activeTool, activeColor, strokeSize, relaxMode) {
                                awaitEachGesture {
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    lastInteractionTick = System.currentTimeMillis()
                                    val pts = mutableListOf(
                                        StrokePoint(
                                            (firstDown.position.x - panOffset.x) / zoomScale,
                                            (firstDown.position.y - panOffset.y) / zoomScale,
                                            firstDown.pressure.coerceIn(0.4f, 1.8f)
                                        )
                                    )
                                    var isPinching = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val activePointers = event.changes.count { it.pressed }
                                        if (activePointers >= 2) {
                                            isPinching = true
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            zoomScale = (zoomScale * zoomChange).coerceIn(1f, 4f)
                                            panOffset = if (zoomScale <= 1.02f) Offset.Zero else panOffset + panChange
                                            event.changes.forEach { it.consume() }
                                            currentPoints = emptyList()
                                        } else if (!isPinching) {
                                            val c = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                                            if (c.pressed) {
                                                c.consume()
                                                pts.add(
                                                    StrokePoint(
                                                        (c.position.x - panOffset.x) / zoomScale,
                                                        (c.position.y - panOffset.y) / zoomScale,
                                                        c.pressure.coerceIn(0.4f, 1.8f)
                                                    )
                                                )
                                                currentPoints = pts.toList()
                                            } else {
                                                break
                                            }
                                        } else if (activePointers == 0) {
                                            break
                                        }
                                    } while (true)

                                    if (!isPinching && pts.isNotEmpty()) {
                                        onAddStroke(
                                            DrawingStroke(
                                                points = pts,
                                                colorArgb = activeColor.toArgb(),
                                                baseWidth = strokeSize * activeTool.widthMultiplier,
                                                alpha = activeTool.alpha,
                                                isEraser = activeTool == DrawTool.ERASER
                                            )
                                        )
                                    }
                                    currentPoints = emptyList()
                                }
                            }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                }
                        ) {
                            drawRect(color = tokens.paperBg.copy(alpha = 0.72f))
                            drawNotepadPaperPattern(notepadBackground, tokens)
                            drawStrokesOnCanvas(strokes, tokens.paperBg)
                            if (currentPoints.isNotEmpty()) {
                                drawSingleStroke(
                                    DrawingStroke(
                                        points = currentPoints,
                                        colorArgb = activeColor.toArgb(),
                                        baseWidth = strokeSize * activeTool.widthMultiplier,
                                        alpha = activeTool.alpha,
                                        isEraser = activeTool == DrawTool.ERASER
                                    ),
                                    tokens.paperBg
                                )
                            }
                        }
                    }

                    // Live Marching Figure + Terrain strip ALWAYS visible beside canvas
                    MarchingFigureProgressBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        hasFinishedTrack = hasFinishedTrack,
                        reducedMotion = reducedMotion,
                        hapticsEnabled = hapticsEnabled,
                        figureStyle = figureStyle,
                        onOpenFigurePicker = {
                            val entries = MarchingFigureStyle.entries
                            onSelectFigure(entries[(entries.indexOf(figureStyle) + 1) % entries.size])
                        },
                        onSeekFraction = onSeekFraction,
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxSize()
                    )
                }

                // Compact live Marching Figure + Play pill kept visible even when Relax Mode hides toolbars
                if (relaxMode && !toolbarVisible) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 12.dp)
                            .background(tokens.cardBg.copy(alpha = 0.92f))
                            .border(1.5.dp, tokens.border)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = tokens.gold,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onTogglePlayPause() }
                        )
                        Text(
                            text = figureStyle.shortLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = tokens.inkPrimary,
                            modifier = Modifier.clickable {
                                val entries = MarchingFigureStyle.entries
                                onSelectFigure(entries[(entries.indexOf(figureStyle) + 1) % entries.size])
                            }
                        )
                        Text(
                            text = "SHOW UI",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.sp,
                            color = tokens.tealAccent,
                            modifier = Modifier.clickable {
                                toolbarVisible = true
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )
                    }
                }

                // Top Floating Toolbar + Visible Marching Figure Selector (fade in Relax Mode)
                AnimatedVisibility(
                    visible = toolbarVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 52.dp, top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.cardBg)
                                .border(2.dp, tokens.border)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = onUndo, enabled = strokes.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = tokens.inkPrimary)
                                }
                                IconButton(onClick = onRedo, enabled = redoCount > 0, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = tokens.inkPrimary)
                                }
                                IconButton(onClick = onClear, enabled = strokes.isNotEmpty(), modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = tokens.danger)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, if (relaxMode) tokens.gold else tokens.border)
                                        .background(if (relaxMode) tokens.gold.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            relaxMode = !relaxMode
                                            lastInteractionTick = System.currentTimeMillis()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Outlined.VisibilityOff, contentDescription = "Relax mode", tint = tokens.inkPrimary, modifier = Modifier.size(14.dp))
                                        Text("RELAX", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkPrimary)
                                    }
                                }
                                if (abs(zoomScale - 1f) > 0.05f) {
                                    Text(
                                        text = "%.1fx".format(zoomScale),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = tokens.tagBlue,
                                        modifier = Modifier
                                            .clickable {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            }
                                            .padding(4.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .border(1.5.dp, tokens.border)
                                        .clickable(onClick = onDismiss),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Exit full screen", tint = tokens.inkPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Visible Marching Figure Selector + Play/Theme companion bar
                        FullScreenMarchingCompanionBar(
                            figureStyle = figureStyle,
                            onSelectFigure = {
                                onSelectFigure(it)
                                lastInteractionTick = System.currentTimeMillis()
                            },
                            isPlaying = isPlaying,
                            onTogglePlayPause = {
                                onTogglePlayPause()
                                lastInteractionTick = System.currentTimeMillis()
                            },
                            themeStyle = themeStyle,
                            onCycleTheme = {
                                onCycleTheme()
                                lastInteractionTick = System.currentTimeMillis()
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = toolbarVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 52.dp, bottom = 12.dp)
                            .background(tokens.cardBg)
                            .border(2.dp, tokens.border)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DrawTool.entries.forEach { tool ->
                                val selected = tool == activeTool
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .background(if (selected) tokens.inkPrimary else tokens.paperBg)
                                        .border(1.dp, tokens.border)
                                        .clickable {
                                            onToolSelected(tool)
                                            lastInteractionTick = System.currentTimeMillis()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tool.label,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (selected) tokens.cardBg else tokens.inkPrimary
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DrawingSwatches.forEach { swatch ->
                                    val chosen = activeColor == swatch && activeTool != DrawTool.ERASER
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(swatch)
                                            .border(if (chosen) 2.5.dp else 1.dp, if (chosen) tokens.gold else tokens.border)
                                            .clickable {
                                                onColorSelected(swatch)
                                                if (activeTool == DrawTool.ERASER) onToolSelected(DrawTool.PEN)
                                                lastInteractionTick = System.currentTimeMillis()
                                            }
                                    )
                                }
                            }
                            Button(
                                onClick = { onSave(canvasSize) },
                                shape = RectangleShape,
                                border = BorderStroke(1.5.dp, tokens.border),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("SAVE PNG", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SIZE", fontFamily = JetBrainsMonoFontFamily, fontSize = 9.sp, color = tokens.inkMuted)
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = strokeSize,
                                onValueChange = {
                                    onStrokeSizeChange(it)
                                    lastInteractionTick = System.currentTimeMillis()
                                },
                                valueRange = 2f..18f,
                                colors = SliderDefaults.colors(thumbColor = tokens.gold, activeTrackColor = tokens.inkPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
