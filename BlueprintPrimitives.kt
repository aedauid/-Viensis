package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.ui.theme.BlueprintTokens
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d".format(mins, secs)
}

@Composable
fun BlueprintGridBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.paperBg)
            .drawBehind {
                val minorStep = 20.dp.toPx()
                val majorStep = minorStep * 4f
                var x = 0f
                var idx = 0
                while (x <= size.width) {
                    val isMajor = idx % 4 == 0
                    drawLine(
                        color = if (isMajor) tokens.gridMajor else tokens.gridMinor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = if (isMajor) 1.dp.toPx() else 0.7.dp.toPx()
                    )
                    x += minorStep
                    idx++
                }
                var y = 0f
                var idy = 0
                while (y <= size.height) {
                    val isMajor = idy % 4 == 0
                    drawLine(
                        color = if (isMajor) tokens.gridMajor else tokens.gridMinor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = if (isMajor) 1.dp.toPx() else 0.7.dp.toPx()
                    )
                    y += minorStep
                    idy++
                }
                // Keep unused majorStep referenced cleanly
                if (majorStep < 0f) Unit
            },
        content = content
    )
}

@Composable
fun BlueprintCard(
    modifier: Modifier = Modifier,
    showDiagonalHatch: Boolean = false,
    showCornerBracket: Boolean = false,
    showCornerCircleArc: Boolean = false,
    shadowOffset: Dp = 3.dp,
    containerColor: Color? = null,
    accentBarColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val bg = containerColor ?: tokens.cardBg

    Box(modifier = modifier, propagateMinConstraints = true) {
        // Hard offset shadow (no blur)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(tokens.shadow.copy(alpha = 0.92f))
        )
        Box(
            modifier = Modifier
                .background(bg)
                .border(2.dp, tokens.border)
                .clipToBounds()
                .drawBehind {
                    // Subtle internal drafting grid
                    val minor = 20.dp.toPx()
                    var gx = minor
                    while (gx < size.width) {
                        drawLine(tokens.gridMinor.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, size.height), 0.6.dp.toPx())
                        gx += minor
                    }
                    var gy = minor
                    while (gy < size.height) {
                        drawLine(tokens.gridMinor.copy(alpha = 0.55f), Offset(0f, gy), Offset(size.width, gy), 0.6.dp.toPx())
                        gy += minor
                    }

                    if (accentBarColor != null) {
                        drawRect(
                            color = accentBarColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 3.5.dp.toPx())
                        )
                    }

                    if (showDiagonalHatch) {
                        val step = 10.dp.toPx()
                        var d = -size.height
                        while (d < size.width + size.height) {
                            drawLine(
                                color = tokens.hatchLine,
                                start = Offset(d, 0f),
                                end = Offset(d + size.height, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            d += step
                        }
                    }

                    if (showCornerBracket) {
                        val pad = 7.dp.toPx()
                        val arm = 10.dp.toPx()
                        drawLine(tokens.inkMuted, Offset(size.width - pad - arm, pad), Offset(size.width - pad, pad), 1.dp.toPx())
                        drawLine(tokens.inkMuted, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), 1.dp.toPx())
                    }

                    if (showCornerCircleArc) {
                        val r = 46.dp.toPx()
                        drawArc(
                            color = tokens.gridMajor,
                            startAngle = 180f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(size.width - r, size.height - r),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    // Small corner tick marks on top-left & bottom-right
                    val tick = 4.dp.toPx()
                    drawLine(tokens.border, Offset(0f, tick), Offset(tick, 0f), 1.dp.toPx())
                },
            content = content
        )
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNotepadPaperPattern(
    style: NotepadBackgroundStyle,
    tokens: BlueprintTokens
) {
    when (style) {
        NotepadBackgroundStyle.SQUARES -> {
            val minor = 18.dp.toPx()
            val major = minor * 4f
            var x = minor
            while (x < size.width) {
                val isMajor = ((x / major) - (x / major).toInt()) < 0.08f
                drawLine(
                    color = if (isMajor) tokens.gridMajor.copy(alpha = 0.7f) else tokens.gridMinor.copy(alpha = 0.75f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.7.dp.toPx()
                )
                x += minor
            }
            var y = minor
            while (y < size.height) {
                val isMajor = ((y / major) - (y / major).toInt()) < 0.08f
                drawLine(
                    color = if (isMajor) tokens.gridMajor.copy(alpha = 0.7f) else tokens.gridMinor.copy(alpha = 0.75f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.7.dp.toPx()
                )
                y += minor
            }
        }

        NotepadBackgroundStyle.LINES -> {
            val rowH = 24.dp.toPx()
            var y = rowH
            while (y < size.height) {
                drawLine(
                    color = tokens.tealAccent.copy(alpha = 0.34f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                y += rowH
            }
            val marginX = 24.dp.toPx()
            if (size.width > marginX * 2f) {
                drawLine(
                    color = tokens.coralAccent.copy(alpha = 0.55f),
                    start = Offset(marginX, 0f),
                    end = Offset(marginX, size.height),
                    strokeWidth = 1.4.dp.toPx()
                )
            }
        }

        NotepadBackgroundStyle.EMPTY -> Unit

        NotepadBackgroundStyle.GUTENBERG_PRESS -> {
            // Laid paper wire lines & vertical chain lines
            val laidStep = 8.dp.toPx()
            var y = laidStep
            while (y < size.height) {
                drawLine(
                    color = tokens.gold.copy(alpha = 0.14f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.6.dp.toPx()
                )
                y += laidStep
            }
            val chainStep = 40.dp.toPx()
            var x = chainStep
            while (x < size.width) {
                drawLine(
                    color = tokens.gold.copy(alpha = 0.22f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 0.9.dp.toPx()
                )
                x += chainStep
            }
            // Rubricated double press border + corner diamonds
            val outer = 6.dp.toPx()
            val inner = 10.dp.toPx()
            if (size.width > inner * 3f && size.height > inner * 3f) {
                drawRect(
                    color = tokens.coralAccent.copy(alpha = 0.6f),
                    topLeft = Offset(outer, outer),
                    size = Size(size.width - outer * 2f, size.height - outer * 2f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawRect(
                    color = tokens.gold.copy(alpha = 0.55f),
                    topLeft = Offset(inner, inner),
                    size = Size(size.width - inner * 2f, size.height - inner * 2f),
                    style = Stroke(width = 0.8.dp.toPx())
                )
                val r = 2.6.dp.toPx()
                listOf(
                    Offset(inner, inner),
                    Offset(size.width - inner, inner),
                    Offset(inner, size.height - inner),
                    Offset(size.width - inner, size.height - inner)
                ).forEach { pt ->
                    drawCircle(tokens.coralAccent.copy(alpha = 0.75f), r, pt)
                }
            }
        }

        NotepadBackgroundStyle.DOT_MATRIX -> {
            val step = 15.dp.toPx()
            var y = step * 0.7f
            while (y < size.height) {
                var x = step * 0.7f
                while (x < size.width) {
                    drawCircle(
                        color = tokens.inkMuted.copy(alpha = 0.42f),
                        radius = 1.2.dp.toPx(),
                        center = Offset(x, y)
                    )
                    x += step
                }
                y += step
            }
        }

        NotepadBackgroundStyle.MUSIC_STAFF -> {
            val staffLineGap = 6.dp.toPx()
            val staffGroupGap = 22.dp.toPx()
            var topY = 14.dp.toPx()
            while (topY + staffLineGap * 4f < size.height) {
                for (i in 0..4) {
                    val ly = topY + i * staffLineGap
                    drawLine(
                        color = tokens.inkMuted.copy(alpha = 0.36f),
                        start = Offset(6.dp.toPx(), ly),
                        end = Offset(size.width - 6.dp.toPx(), ly),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }
                // Measure barlines across the 5 lines
                val staffBottom = topY + 4f * staffLineGap
                val barStep = 64.dp.toPx()
                var bx = 6.dp.toPx()
                while (bx < size.width - 6.dp.toPx()) {
                    drawLine(
                        color = tokens.plumAccent.copy(alpha = 0.35f),
                        start = Offset(bx, topY),
                        end = Offset(bx, staffBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                    bx += barStep
                }
                topY += (4f * staffLineGap) + staffGroupGap
            }
        }
    }
}

/**
 * Vertical rotated label modifier (-90 degrees) that lays out accurately inside narrow strips.
 */
fun Modifier.verticalText(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxHeight, minHeight = 0, maxHeight = constraints.maxWidth))
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2)
        )
    }
}

@Composable
fun LibraryVerticalStrip(
    sheetCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(tokens.shadow)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.libraryBg)
                .border(2.dp, tokens.border)
                .clickable(onClick = onClick)
                .testTag("notepad_library_strip")
                .semantics { contentDescription = "Open Notepad Library, $sheetCount sheets saved" }
                .drawBehind {
                    val step = 10.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(tokens.libraryLine, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
                        x += step
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NOTEPAD LIBRARY",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 2.4.sp,
                    color = Color(0xFFF4F1EA),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .verticalText()
                        .graphicsLayer { rotationZ = -90f }
                )
            }
            HorizontalDivider(thickness = 1.5.dp, color = tokens.tealAccent.copy(alpha = 0.7f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%02d".format(sheetCount.coerceAtLeast(0)),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    color = tokens.gold
                )
            }
        }
    }
}

@Composable
fun ThemeVerticalStrip(
    themeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(tokens.shadow)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.transportBg)
                .border(2.dp, tokens.border)
                .clickable(onClick = onClick)
                .testTag("theme_style_strip")
                .semantics { contentDescription = "Change blueprint theme style, current $themeLabel" }
                .drawBehind {
                    val step = 10.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(tokens.gridMinor.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
                        x += step
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = "Theme styles",
                    tint = tokens.gold,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "THEMES",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.2.sp,
                    color = tokens.inkPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .verticalText()
                        .graphicsLayer { rotationZ = -90f }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.border.copy(alpha = 0.35f))
                    .background(tokens.cardBg.copy(alpha = 0.7f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = themeLabel.take(5),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.sp,
                    letterSpacing = 0.8.sp,
                    color = tokens.inkPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ShareVerticalStrip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(tokens.shadow)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.cardBg)
                .border(2.dp, tokens.border)
                .clickable(onClick = onClick)
                .testTag("share_to_socials_strip")
                .semantics { contentDescription = "Share to socials" }
                .drawBehind {
                    val step = 10.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(tokens.gridMinor, Offset(x, 0f), Offset(x, size.height), 0.8.dp.toPx())
                        x += step
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "Share",
                    tint = tokens.coralAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SHARE SOCIALS",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    letterSpacing = 2.0.sp,
                    color = tokens.inkPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .verticalText()
                        .graphicsLayer { rotationZ = -90f }
                )
            }
        }
    }
}

/**
 * SIGNATURE FEATURE: THE MARCHING FIGURE PROGRESS BAR
 * Supports multiple metaphorical marching figures (Flag Bearer, Pulse Dancer,
 * Parade Soldier, Beat Drummer, Maestro Conductor) traveling vertically UPWARD
 * from 0 (bottom) to 100 (top), leaving a gold trail behind.
 */
@Composable
fun MarchingFigureProgressBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasFinishedTrack: Boolean,
    reducedMotion: Boolean,
    hapticsEnabled: Boolean,
    figureStyle: MarchingFigureStyle = MarchingFigureStyle.FLAG_BEARER,
    onOpenFigurePicker: (() -> Unit)? = null,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var stripHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val playbackFraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val activeFraction = if (isDragging) dragFraction else playbackFraction

    val shouldWalk = isPlaying && !isDragging && !reducedMotion
    val walkPhase = if (shouldWalk) {
        val infiniteTransition = rememberInfiniteTransition(label = "marchCycle")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = when (figureStyle) {
                        MarchingFigureStyle.DANCER -> 460
                        MarchingFigureStyle.DRUMMER -> 420
                        else -> 520
                    },
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "walkPhase"
        )
        phase
    } else 0f

    val shouldCelebrate = hasFinishedTrack && activeFraction >= 0.97f && !reducedMotion
    val celebrationPulse = if (shouldCelebrate) {
        val infiniteTransition = rememberInfiniteTransition(label = "celebrateCycle")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "celebrate"
        )
        pulse
    } else 0.9f

    val effectivePhase = if (shouldWalk) walkPhase else 0f
    val effectiveStride = if (shouldWalk) sin(walkPhase) else 0f

    fun yToFraction(yPx: Float): Float {
        val padPx = with(density) { 26.dp.toPx() }
        val usable = (stripHeightPx - padPx * 2f).coerceAtLeast(1f)
        return (1f - ((yPx - padPx) / usable)).coerceIn(0f, 1f)
    }

    Box(modifier = modifier) {
        // Hard offset shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(tokens.shadow)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.cardBg)
                .border(2.dp, tokens.border)
        ) {
            if (onOpenFigurePicker != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.transportBg)
                        .clickable(onClick = onOpenFigurePicker)
                        .padding(vertical = 4.dp)
                        .testTag("marching_figure_selector_button")
                        .semantics { contentDescription = "Select marching figure, current ${figureStyle.title}" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = tokens.gold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = figureStyle.shortLabel.take(5),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        letterSpacing = 0.6.sp,
                        color = tokens.inkPrimary,
                        maxLines = 1
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = tokens.border)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { stripHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val frac = yToFraction(offset.y)
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeekFraction(frac)
                        }
                    }
                    .pointerInput(durationMs) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragFraction = yToFraction(offset.y)
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                dragFraction = yToFraction(change.position.y)
                            },
                            onDragEnd = {
                                onSeekFraction(dragFraction)
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            }
                        )
                    }
                    .testTag("marching_figure_progress_bar")
                    .semantics {
                        contentDescription = "Song progress ${formatTimeMs(positionMs)} of ${formatTimeMs(durationMs)}, ${figureStyle.title}"
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val padPx = 26.dp.toPx()
                    val usableHeight = (size.height - padPx * 2f).coerceAtLeast(1f)
                    val centerX = size.width * 0.5f

                    // Figure-specific hand-drawn environment & terrain
                    drawMarchingEnvironmentTerrain(
                        figureStyle = figureStyle,
                        padPx = padPx,
                        usableHeight = usableHeight,
                        centerX = centerX,
                        activeFraction = activeFraction,
                        walkPhase = effectivePhase,
                        isPlaying = isPlaying && !isDragging,
                        reducedMotion = reducedMotion,
                        gridMajor = tokens.gridMajor,
                        inkMuted = tokens.inkMuted,
                        gold = tokens.gold,
                        emerald = tokens.emeraldAccent,
                        teal = tokens.tealAccent,
                        plum = tokens.plumAccent,
                        coral = tokens.coralAccent
                    )

                    // Faint ruler ticks along left/right & horizontal grid lines
                    val tickCount = 20
                    for (i in 0..tickCount) {
                        val y = padPx + (usableHeight * i / tickCount)
                        val isMajor = i % 5 == 0
                        if (isMajor) {
                            drawLine(
                                color = tokens.gridMajor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 0.9.dp.toPx()
                            )
                        }
                        val tickLen = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                        drawLine(
                            color = tokens.inkMuted.copy(alpha = if (isMajor) 0.7f else 0.35f),
                            start = Offset(2.dp.toPx(), y),
                            end = Offset(2.dp.toPx() + tickLen, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val bottomY = size.height - padPx
                    val figureY = bottomY - (usableHeight * activeFraction)

                    // Trail colored by figure environment & gold crest
                    val trailColor = when (figureStyle) {
                        MarchingFigureStyle.FLAG_BEARER -> tokens.gold
                        MarchingFigureStyle.DANCER -> tokens.tealAccent
                        MarchingFigureStyle.SOLDIER -> tokens.coralAccent
                        MarchingFigureStyle.DRUMMER -> tokens.emeraldAccent
                        MarchingFigureStyle.CONDUCTOR -> tokens.plumAccent
                    }
                    if (activeFraction > 0.005f) {
                        drawLine(
                            color = trailColor,
                            start = Offset(centerX, bottomY),
                            end = Offset(centerX, figureY + 8.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    drawMarchingFigureArchetype(
                        figureStyle = figureStyle,
                        centerX = centerX,
                        figureY = figureY,
                        walkPhase = effectivePhase,
                        stride = effectiveStride,
                        isPlaying = isPlaying && !isDragging,
                        reducedMotion = reducedMotion,
                        hasFinished = hasFinishedTrack && activeFraction >= 0.97f,
                        celebrationPulse = celebrationPulse,
                        inkPrimary = tokens.inkPrimary,
                        border = tokens.border,
                        gold = tokens.gold,
                        tagBlue = tokens.tealAccent
                    )
                }

                Text(
                    text = "100",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    color = tokens.inkMuted,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                )
                Text(
                    text = "0",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    color = tokens.inkMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                )

                if (isDragging) {
                    val previewMs = (dragFraction * durationMs).toLong()
                    val padPx = with(density) { 26.dp.toPx() }
                    val usableHeight = (stripHeightPx - padPx * 2f).coerceAtLeast(1f)
                    val yPx = (stripHeightPx - padPx) - (usableHeight * dragFraction) + with(density) { 14.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, yPx.roundToInt().coerceIn(8, (stripHeightPx - 48).roundToInt())) }
                            .background(tokens.border)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatTimeMs(previewMs),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = tokens.cardBg
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarchingEnvironmentTerrain(
    figureStyle: MarchingFigureStyle,
    padPx: Float,
    usableHeight: Float,
    centerX: Float,
    activeFraction: Float,
    walkPhase: Float,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    gridMajor: Color,
    inkMuted: Color,
    gold: Color,
    emerald: Color,
    teal: Color,
    plum: Color,
    coral: Color
) {
    val bottomY = size.height
    val sway = if (isPlaying && !reducedMotion) sin(walkPhase) else 0f

    when (figureStyle) {
        MarchingFigureStyle.FLAG_BEARER -> {
            // Alpine Ridge & Emerald Pine Trail
            val skyTint = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, padPx + 22.dp.toPx())
                lineTo(0f, padPx + 22.dp.toPx())
                close()
            }
            drawPath(skyTint, color = teal.copy(alpha = 0.08f))

            // Top mountain summit silhouette near 100
            val peak = Path().apply {
                moveTo(0f, padPx + 14.dp.toPx())
                lineTo(size.width * 0.35f, padPx - 4.dp.toPx())
                lineTo(size.width * 0.65f, padPx + 8.dp.toPx())
                lineTo(size.width, padPx - 2.dp.toPx())
                lineTo(size.width, padPx + 18.dp.toPx())
                lineTo(0f, padPx + 18.dp.toPx())
                close()
            }
            drawPath(peak, color = inkMuted.copy(alpha = 0.14f))

            // Foothills & pine silhouettes along the climb
            val baseHill = Path().apply {
                moveTo(0f, bottomY)
                lineTo(0f, bottomY - 26.dp.toPx())
                lineTo(size.width * 0.45f, bottomY - 38.dp.toPx())
                lineTo(size.width, bottomY - 20.dp.toPx())
                lineTo(size.width, bottomY)
                close()
            }
            drawPath(baseHill, color = emerald.copy(alpha = 0.20f))

            for (i in 1..4) {
                val py = padPx + usableHeight * (i / 5f)
                val tx = if (i % 2 == 0) size.width - 10.dp.toPx() else 10.dp.toPx()
                val pine = Path().apply {
                    moveTo(tx, py - 8.dp.toPx())
                    lineTo(tx - 5.dp.toPx(), py + 2.dp.toPx())
                    lineTo(tx + 5.dp.toPx(), py + 2.dp.toPx())
                    close()
                }
                drawPath(pine, color = emerald.copy(alpha = 0.45f))
            }
        }

        MarchingFigureStyle.DANCER -> {
            // Lantern Glade & Shimmering Cyan Pond (reference image inspired)
            val gladeTint = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, bottomY)
                lineTo(0f, bottomY)
                close()
            }
            drawPath(gladeTint, color = plum.copy(alpha = 0.06f))

            // Hanging amber lanterns near top canopy
            val canopy = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, padPx + 6.dp.toPx())
                quadraticTo(centerX, padPx + 18.dp.toPx(), 0f, padPx + 10.dp.toPx())
                close()
            }
            drawPath(canopy, color = emerald.copy(alpha = 0.22f))

            val lanternX = size.width * 0.72f + sway * 1.4.dp.toPx()
            drawLine(inkMuted.copy(alpha = 0.6f), Offset(size.width * 0.72f, 0f), Offset(lanternX, padPx + 6.dp.toPx()), 1.dp.toPx())
            drawCircle(gold.copy(alpha = 0.35f), 7.dp.toPx(), Offset(lanternX, padPx + 9.dp.toPx()))
            drawCircle(gold, 3.2.dp.toPx(), Offset(lanternX, padPx + 9.dp.toPx()))

            // Floating fireflies along the glade
            for (i in 1..6) {
                val fy = padPx + usableHeight * (i / 7f)
                val fx = if (i % 2 == 0) size.width * 0.22f else size.width * 0.78f
                drawCircle(gold.copy(alpha = 0.65f), 1.8.dp.toPx(), Offset(fx + sway * 1.2f, fy))
            }

            // Cyan pond water ripples at bottom
            val pond = Path().apply {
                moveTo(0f, bottomY - 22.dp.toPx())
                quadraticTo(centerX, bottomY - 28.dp.toPx(), size.width, bottomY - 20.dp.toPx())
                lineTo(size.width, bottomY)
                lineTo(0f, bottomY)
                close()
            }
            drawPath(pond, color = teal.copy(alpha = 0.26f))
            drawLine(teal.copy(alpha = 0.7f), Offset(4.dp.toPx(), bottomY - 12.dp.toPx()), Offset(size.width - 4.dp.toPx(), bottomY - 12.dp.toPx()), 1.2.dp.toPx())
        }

        MarchingFigureStyle.SOLDIER -> {
            // Citadel Parapet & Regimental Cobble Track
            val parapet = Path().apply {
                moveTo(0f, bottomY)
                lineTo(0f, bottomY - 26.dp.toPx())
                lineTo(size.width * 0.25f, bottomY - 26.dp.toPx())
                lineTo(size.width * 0.25f, bottomY - 18.dp.toPx())
                lineTo(size.width * 0.5f, bottomY - 18.dp.toPx())
                lineTo(size.width * 0.5f, bottomY - 26.dp.toPx())
                lineTo(size.width * 0.75f, bottomY - 26.dp.toPx())
                lineTo(size.width * 0.75f, bottomY - 18.dp.toPx())
                lineTo(size.width, bottomY - 18.dp.toPx())
                lineTo(size.width, bottomY)
                close()
            }
            drawPath(parapet, color = coral.copy(alpha = 0.18f))

            // Regimental chevron mileposts along side rails
            for (i in 1..4) {
                val cy = padPx + usableHeight * (i / 5f)
                drawLine(coral.copy(alpha = 0.55f), Offset(size.width - 8.dp.toPx(), cy + 3.dp.toPx()), Offset(size.width - 4.dp.toPx(), cy), 1.3.dp.toPx())
                drawLine(coral.copy(alpha = 0.55f), Offset(size.width - 4.dp.toPx(), cy), Offset(size.width, cy + 3.dp.toPx()), 1.3.dp.toPx())
            }
        }

        MarchingFigureStyle.DRUMMER -> {
            // Acoustic Meadow & Soundwave Terraces
            val meadow = Path().apply {
                moveTo(0f, bottomY)
                lineTo(0f, bottomY - 24.dp.toPx())
                quadraticTo(size.width * 0.3f, bottomY - 34.dp.toPx(), size.width * 0.65f, bottomY - 22.dp.toPx())
                quadraticTo(size.width * 0.85f, bottomY - 16.dp.toPx(), size.width, bottomY - 26.dp.toPx())
                lineTo(size.width, bottomY)
                close()
            }
            drawPath(meadow, color = emerald.copy(alpha = 0.24f))

            // Acoustic pulse arcs flanking the march track
            for (i in 1..5) {
                val wy = padPx + usableHeight * (i / 6f)
                val pulseW = (5.dp.toPx() + abs(sway) * 3.dp.toPx())
                drawLine(teal.copy(alpha = 0.45f), Offset(size.width - pulseW - 3.dp.toPx(), wy), Offset(size.width - 3.dp.toPx(), wy), 1.4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(gold.copy(alpha = 0.45f), Offset(3.dp.toPx(), wy + 3.dp.toPx()), Offset(3.dp.toPx() + pulseW * 0.7f, wy + 3.dp.toPx()), 1.4.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        MarchingFigureStyle.CONDUCTOR -> {
            // Grand Musical Staff & Velvet Curtain Crest
            for (s in -2..2) {
                val sx = centerX + s * 5.dp.toPx()
                drawLine(
                    color = gridMajor.copy(alpha = 0.85f),
                    start = Offset(sx, padPx),
                    end = Offset(sx, bottomY - padPx),
                    strokeWidth = 0.9.dp.toPx()
                )
            }
            // Velvet stage proscenium drape at top
            val curtainLeft = Path().apply {
                moveTo(0f, 0f)
                lineTo(centerX, 0f)
                quadraticTo(size.width * 0.22f, padPx + 8.dp.toPx(), 0f, padPx + 16.dp.toPx())
                close()
            }
            val curtainRight = Path().apply {
                moveTo(size.width, 0f)
                lineTo(centerX, 0f)
                quadraticTo(size.width * 0.78f, padPx + 8.dp.toPx(), size.width, padPx + 16.dp.toPx())
                close()
            }
            drawPath(curtainLeft, color = plum.copy(alpha = 0.24f))
            drawPath(curtainRight, color = plum.copy(alpha = 0.24f))

            // Warm spotlight beam cone along current figure position
            val figY = (bottomY - padPx) - (usableHeight * activeFraction)
            drawCircle(gold.copy(alpha = 0.14f), 14.dp.toPx(), Offset(centerX, figY))
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarchingFigureArchetype(
    figureStyle: MarchingFigureStyle,
    centerX: Float,
    figureY: Float,
    walkPhase: Float,
    stride: Float,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    hasFinished: Boolean,
    celebrationPulse: Float,
    inkPrimary: Color,
    border: Color,
    gold: Color,
    tagBlue: Color
) {
    val strokeW = 2.dp.toPx()
    val headR = 3.5.dp.toPx()
    val bob = if (isPlaying && !reducedMotion) abs(stride) * 1.8.dp.toPx() else 0f
    val baseY = figureY - bob

    when (figureStyle) {
        MarchingFigureStyle.FLAG_BEARER -> {
            val figX = centerX - 3.dp.toPx()
            val stridePx = stride * 5.dp.toPx()
            val headCenter = Offset(figX, baseY - 12.dp.toPx())
            val shoulder = Offset(figX, baseY - 7.5.dp.toPx())
            val hip = Offset(figX, baseY - 0.5.dp.toPx())

            drawCircle(inkPrimary, headR, headCenter, style = Stroke(width = strokeW))
            drawLine(inkPrimary, Offset(figX, headCenter.y + headR), hip, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX - 4.dp.toPx() - stridePx * 0.35f, baseY + 8.dp.toPx() - stridePx * 0.5f), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX + 4.dp.toPx() + stridePx * 0.35f, baseY + 8.dp.toPx() + stridePx * 0.5f), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, shoulder, Offset(figX - 5.dp.toPx(), baseY - 2.dp.toPx() - stridePx * 0.3f), strokeW, cap = StrokeCap.Round)

            val poleX = figX + 8.dp.toPx()
            drawLine(inkPrimary, shoulder, Offset(poleX, baseY - 5.dp.toPx()), strokeW, cap = StrokeCap.Round)
            val poleTopY = baseY - 17.dp.toPx()
            drawLine(gold, Offset(poleX, baseY + 8.dp.toPx()), Offset(poleX, poleTopY), strokeW, cap = StrokeCap.Round)

            val wave = if (isPlaying && !reducedMotion) stridePx * 0.28f else 0f
            val flagPath = Path().apply {
                moveTo(poleX, poleTopY)
                lineTo(poleX + 9.dp.toPx(), poleTopY + 3.5.dp.toPx() + wave)
                lineTo(poleX, poleTopY + 7.dp.toPx())
                close()
            }
            drawPath(flagPath, color = gold)
            drawPath(flagPath, color = border, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
            if (hasFinished) {
                drawCelebrationRays(Offset(poleX, poleTopY), gold, celebrationPulse, reducedMotion, strokeW)
            }
        }

        MarchingFigureStyle.DANCER -> {
            val groove = if (isPlaying && !reducedMotion) sin(walkPhase) else 0f
            val sway = if (isPlaying && !reducedMotion) cos(walkPhase) * 2.5.dp.toPx() else 0f
            val figX = centerX + sway * 0.5f
            val headCenter = Offset(figX, baseY - 12.dp.toPx())
            val shoulder = Offset(figX, baseY - 7.5.dp.toPx())
            val hip = Offset(figX - sway * 0.4f, baseY)

            // Animated pulse rings radiating outward when playing
            if (isPlaying && !reducedMotion) {
                val ringProgress = (walkPhase / (2f * PI.toFloat())).coerceIn(0f, 1f)
                val ringRadius = 7.dp.toPx() + ringProgress * 12.dp.toPx()
                drawCircle(
                    color = gold.copy(alpha = (1f - ringProgress) * 0.75f),
                    radius = ringRadius,
                    center = Offset(centerX, baseY - 4.dp.toPx()),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }

            drawCircle(inkPrimary, headR, headCenter, style = Stroke(width = strokeW))
            drawLine(inkPrimary, Offset(figX, headCenter.y + headR), hip, strokeW, cap = StrokeCap.Round)

            // Expressive dancing arms
            val leftElbow = Offset(figX - 6.dp.toPx(), shoulder.y - 4.dp.toPx() + groove * 4.dp.toPx())
            val rightElbow = Offset(figX + 6.dp.toPx(), shoulder.y - 4.dp.toPx() - groove * 4.dp.toPx())
            drawLine(inkPrimary, shoulder, leftElbow, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, leftElbow, Offset(leftElbow.x - 3.dp.toPx(), leftElbow.y - 4.dp.toPx() - groove * 2.dp.toPx()), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, shoulder, rightElbow, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, rightElbow, Offset(rightElbow.x + 3.dp.toPx(), rightElbow.y - 4.dp.toPx() + groove * 2.dp.toPx()), strokeW, cap = StrokeCap.Round)

            // Rhythmic dancing legs
            val legSwing = groove * 4.5.dp.toPx()
            drawLine(inkPrimary, hip, Offset(figX - 5.dp.toPx(), baseY + 7.5.dp.toPx() - legSwing * 0.5f), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX + 5.dp.toPx(), baseY + 7.5.dp.toPx() + legSwing * 0.5f), strokeW, cap = StrokeCap.Round)

            // Tiny gold eighth-note floating beside dancer
            val noteX = figX + 10.dp.toPx()
            val noteY = baseY - 14.dp.toPx() - abs(groove) * 3.dp.toPx()
            drawCircle(gold, 2.dp.toPx(), Offset(noteX, noteY))
            drawLine(gold, Offset(noteX + 1.5.dp.toPx(), noteY), Offset(noteX + 1.5.dp.toPx(), noteY - 6.dp.toPx()), 1.4.dp.toPx())
            drawLine(gold, Offset(noteX + 1.5.dp.toPx(), noteY - 6.dp.toPx()), Offset(noteX + 4.5.dp.toPx(), noteY - 4.5.dp.toPx()), 1.4.dp.toPx())

            if (hasFinished) {
                drawCelebrationRays(headCenter, gold, celebrationPulse, reducedMotion, strokeW)
            }
        }

        MarchingFigureStyle.SOLDIER -> {
            val figX = centerX - 1.dp.toPx()
            val march = stride * 5.dp.toPx()
            val headCenter = Offset(figX, baseY - 10.5.dp.toPx())
            val shoulder = Offset(figX, baseY - 6.5.dp.toPx())
            val hip = Offset(figX, baseY + 0.5.dp.toPx())

            // Tall shako parade hat + gold plume
            drawRect(
                color = inkPrimary,
                topLeft = Offset(figX - 4.dp.toPx(), headCenter.y - headR - 5.5.dp.toPx()),
                size = Size(8.dp.toPx(), 5.5.dp.toPx())
            )
            drawLine(
                color = gold,
                start = Offset(figX - 5.dp.toPx(), headCenter.y - headR),
                end = Offset(figX + 5.dp.toPx(), headCenter.y - headR),
                strokeWidth = 1.6.dp.toPx()
            )
            // Gold plume at top of shako
            drawLine(
                color = gold,
                start = Offset(figX, headCenter.y - headR - 5.5.dp.toPx()),
                end = Offset(figX + march * 0.2f, headCenter.y - headR - 9.5.dp.toPx()),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawCircle(inkPrimary, headR, headCenter, style = Stroke(width = strokeW))
            drawLine(inkPrimary, Offset(figX, headCenter.y + headR), hip, strokeW, cap = StrokeCap.Round)
            // Gold cross-belt on chest
            drawLine(gold, Offset(figX - 3.dp.toPx(), shoulder.y), Offset(figX + 3.dp.toPx(), hip.y), 1.6.dp.toPx())

            // High-knee crisp parade march legs
            val leftKnee = Offset(figX - 3.dp.toPx(), baseY + 4.dp.toPx() - abs(march) * 0.35f)
            val rightKnee = Offset(figX + 3.dp.toPx(), baseY + 4.dp.toPx() - abs(-march) * 0.25f)
            drawLine(inkPrimary, hip, leftKnee, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, leftKnee, Offset(figX - 3.5.dp.toPx(), baseY + 8.5.dp.toPx() - march * 0.45f), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, rightKnee, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, rightKnee, Offset(figX + 3.5.dp.toPx(), baseY + 8.5.dp.toPx() + march * 0.45f), strokeW, cap = StrokeCap.Round)

            // Left arm swing, right arm holding upright regimental standard or saluting at end
            drawLine(inkPrimary, shoulder, Offset(figX - 6.dp.toPx(), shoulder.y + 5.dp.toPx() - march * 0.5f), strokeW, cap = StrokeCap.Round)
            val staffX = figX + 7.dp.toPx()
            if (hasFinished) {
                // Crisp salute
                drawLine(inkPrimary, shoulder, Offset(figX + 6.dp.toPx(), shoulder.y - 3.dp.toPx()), strokeW, cap = StrokeCap.Round)
                drawLine(inkPrimary, Offset(figX + 6.dp.toPx(), shoulder.y - 3.dp.toPx()), Offset(figX + 3.5.dp.toPx(), headCenter.y - 1.dp.toPx()), strokeW, cap = StrokeCap.Round)
                drawCelebrationRays(Offset(figX, headCenter.y - 10.dp.toPx()), gold, celebrationPulse, reducedMotion, strokeW)
            } else {
                drawLine(inkPrimary, shoulder, Offset(staffX, shoulder.y + 1.5.dp.toPx()), strokeW, cap = StrokeCap.Round)
                drawLine(gold, Offset(staffX, baseY + 8.dp.toPx()), Offset(staffX, baseY - 17.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(gold, 2.5.dp.toPx(), Offset(staffX, baseY - 17.5.dp.toPx()))
            }
        }

        MarchingFigureStyle.DRUMMER -> {
            val figX = centerX - 2.dp.toPx()
            val beat = if (isPlaying && !reducedMotion) sin(walkPhase * 2f) else 0f
            val legStride = stride * 4.5.dp.toPx()
            val headCenter = Offset(figX, baseY - 12.dp.toPx())
            val shoulder = Offset(figX, baseY - 7.5.dp.toPx())
            val hip = Offset(figX, baseY)

            drawCircle(inkPrimary, headR, headCenter, style = Stroke(width = strokeW))
            drawLine(inkPrimary, Offset(figX, headCenter.y + headR), hip, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX - 4.dp.toPx(), baseY + 8.dp.toPx() - legStride * 0.45f), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX + 4.dp.toPx(), baseY + 8.dp.toPx() + legStride * 0.45f), strokeW, cap = StrokeCap.Round)

            // Gold snare drum held in front-right
            val drumTopLeft = Offset(figX + 2.dp.toPx(), baseY - 5.dp.toPx())
            val drumSize = Size(10.dp.toPx(), 6.5.dp.toPx())
            drawRect(color = gold, topLeft = drumTopLeft, size = drumSize)
            drawRect(color = border, topLeft = drumTopLeft, size = drumSize, style = Stroke(width = 1.2.dp.toPx()))
            // Diagonal drum X bracing
            drawLine(border, drumTopLeft, Offset(drumTopLeft.x + drumSize.width, drumTopLeft.y + drumSize.height), 1.dp.toPx())

            // Alternating drumsticks hitting drum top
            val leftTipY = drumTopLeft.y - 1.dp.toPx() - abs(beat.coerceAtLeast(0f)) * 4.dp.toPx()
            val rightTipY = drumTopLeft.y - 1.dp.toPx() - abs((-beat).coerceAtLeast(0f)) * 4.dp.toPx()
            drawLine(inkPrimary, shoulder, Offset(figX + 3.dp.toPx(), leftTipY), 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(inkPrimary, shoulder, Offset(figX + 9.dp.toPx(), rightTipY), 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(gold, 1.5.dp.toPx(), Offset(figX + 3.dp.toPx(), leftTipY))
            drawCircle(gold, 1.5.dp.toPx(), Offset(figX + 9.dp.toPx(), rightTipY))

            if (isPlaying && !reducedMotion && abs(beat) > 0.7f) {
                drawLine(tagBlue, Offset(drumTopLeft.x + 2.dp.toPx(), drumTopLeft.y - 4.dp.toPx()), Offset(drumTopLeft.x + 1.dp.toPx(), drumTopLeft.y - 8.dp.toPx()), 1.4.dp.toPx())
                drawLine(tagBlue, Offset(drumTopLeft.x + 8.dp.toPx(), drumTopLeft.y - 4.dp.toPx()), Offset(drumTopLeft.x + 10.dp.toPx(), drumTopLeft.y - 8.dp.toPx()), 1.4.dp.toPx())
            }
            if (hasFinished) {
                drawCelebrationRays(Offset(drumTopLeft.x + 5.dp.toPx(), drumTopLeft.y - 4.dp.toPx()), gold, celebrationPulse, reducedMotion, strokeW)
            }
        }

        MarchingFigureStyle.CONDUCTOR -> {
            val figX = centerX - 2.dp.toPx()
            val sweep = if (isPlaying && !reducedMotion) sin(walkPhase) else 0f
            val lift = if (isPlaying && !reducedMotion) cos(walkPhase) else 0f
            val headCenter = Offset(figX, baseY - 12.dp.toPx())
            val shoulder = Offset(figX, baseY - 7.5.dp.toPx())
            val hip = Offset(figX, baseY)

            drawCircle(inkPrimary, headR, headCenter, style = Stroke(width = strokeW))
            drawLine(inkPrimary, Offset(figX, headCenter.y + headR), hip, strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX - 4.dp.toPx(), baseY + 8.dp.toPx() - stride * 2.dp.toPx()), strokeW, cap = StrokeCap.Round)
            drawLine(inkPrimary, hip, Offset(figX + 4.dp.toPx(), baseY + 8.dp.toPx() + stride * 2.dp.toPx()), strokeW, cap = StrokeCap.Round)

            // Cueing left arm
            drawLine(inkPrimary, shoulder, Offset(figX - 6.dp.toPx(), shoulder.y + lift * 3.dp.toPx()), strokeW, cap = StrokeCap.Round)
            // Baton arm sweeping tempo arcs
            val hand = Offset(figX + 6.dp.toPx(), shoulder.y - 2.dp.toPx() + sweep * 3.dp.toPx())
            val batonTip = Offset(hand.x + 7.dp.toPx(), hand.y - 6.dp.toPx() + lift * 4.dp.toPx())
            drawLine(inkPrimary, shoulder, hand, strokeW, cap = StrokeCap.Round)
            drawLine(gold, hand, batonTip, 2.dp.toPx(), cap = StrokeCap.Round)

            if (isPlaying && !reducedMotion) {
                drawArc(
                    color = tagBlue.copy(alpha = 0.65f),
                    startAngle = -65f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(hand.x, batonTip.y - 4.dp.toPx()),
                    size = Size(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            if (hasFinished) {
                drawCelebrationRays(batonTip, gold, celebrationPulse, reducedMotion, strokeW)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCelebrationRays(
    origin: Offset,
    gold: Color,
    celebrationPulse: Float,
    reducedMotion: Boolean,
    strokeW: Float
) {
    val alpha = if (reducedMotion) 0.9f else celebrationPulse
    val rays = listOf(
        Offset(-10f, -14f), Offset(0f, -18f), Offset(12f, -14f),
        Offset(-13f, -4f), Offset(15f, -4f)
    )
    rays.forEach { dir ->
        val start = Offset(origin.x + dir.x * 0.8f, origin.y + dir.y * 0.8f)
        val end = Offset(origin.x + dir.x * 1.7f, origin.y + dir.y * 1.7f)
        drawLine(
            color = gold.copy(alpha = alpha),
            start = start,
            end = end,
            strokeWidth = (strokeW * 0.9f),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Standard Ensis Vi drafting bottom sheet modal with dimmed scrim, square X button,
 * mono uppercase title, subtitle, keyboard-safe insets, and optional CANCEL + gold CTA.
 */
@Composable
fun BlueprintBottomSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    primaryButtonLabel: String? = null,
    onPrimaryClick: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    cancelButtonLabel: String = "CANCEL",
    showBottomActions: Boolean = primaryButtonLabel != null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .background(tokens.paperBg)
                .border(2.dp, tokens.border)
                .clickable(enabled = false) {} // swallow backdrop clicks
                .navigationBarsPadding()
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.uppercase(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = tokens.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.5.dp, tokens.border)
                        .background(tokens.cardBg)
                        .clickable(onClick = onDismiss)
                        .testTag("sheet_close_button")
                        .semantics { contentDescription = "Close $title" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = tokens.inkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 1.5.dp, color = tokens.border)

            // Body
            val bodyModifier = if (scrollable) {
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            } else {
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            }

            Column(
                modifier = bodyModifier,
                content = content
            )

            if (showBottomActions && primaryButtonLabel != null && onPrimaryClick != null) {
                HorizontalDivider(thickness = 1.dp, color = tokens.gridMajor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RectangleShape,
                        border = BorderStroke(1.5.dp, tokens.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.inkPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_cancel_button")
                    ) {
                        Text(
                            text = cancelButtonLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.8.sp
                        )
                    }
                    Button(
                        onClick = onPrimaryClick,
                        enabled = primaryEnabled,
                        shape = RectangleShape,
                        border = BorderStroke(1.5.dp, tokens.border),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.gold,
                            contentColor = tokens.goldText,
                            disabledContainerColor = tokens.gold.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sheet_primary_button")
                    ) {
                        Text(
                            text = primaryButtonLabel.uppercase(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.8.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
