package com.example.ui.components

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.Track
import com.example.news.NewsHeadline
import com.example.news.NewsUiState
import com.example.news.StageScreenMode
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class TickerEntry(
    val badge: String,
    val text: String,
    val badgeColor: Color,
    val link: String = ""
)

@Composable
fun StudioStageCard(
    stageMode: StageScreenMode,
    onStageModeChange: (StageScreenMode) -> Unit,
    figureStyle: MarchingFigureStyle,
    isTrackPlaying: Boolean,
    reducedMotion: Boolean,
    currentTrack: Track?,
    isBluetoothActive: Boolean,
    bluetoothOutputName: String,
    sheetsCount: Int,
    selectedVideoUri: Uri?,
    videoMuted: Boolean,
    onPickVideoUri: (Uri) -> Unit,
    onToggleVideoMuted: () -> Unit,
    onOpenFullScreenVideo: () -> Unit,
    newsState: NewsUiState,
    onRefreshNews: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBlueprintTokens.current
    val context = LocalContext.current

    // Zero-permission Photo Picker for gallery/camera videos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickVideoUri(uri)
    }

    // SAF OpenDocument fallback for Downloads / MP4 files on storage
    val mp4DocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPickVideoUri(uri)
        }
    }

    val tickerItems = remember(newsState.headlines, currentTrack?.title, isBluetoothActive, bluetoothOutputName, sheetsCount) {
        buildList {
            newsState.headlines.forEach { h ->
                add(
                    TickerEntry(
                        badge = h.source,
                        text = h.title,
                        badgeColor = tokens.coralAccent,
                        link = h.link
                    )
                )
            }
            add(
                TickerEntry(
                    badge = "STUDIO",
                    text = "Track: ${currentTrack?.title ?: "Demo"} · $sheetsCount sheet(s) in library",
                    badgeColor = tokens.tealAccent
                )
            )
            add(
                TickerEntry(
                    badge = if (isBluetoothActive) "BT ON" else "AUDIO",
                    text = "Output route: $bluetoothOutputName",
                    badgeColor = if (isBluetoothActive) tokens.emeraldAccent else tokens.plumAccent
                )
            )
        }
    }

    var tickerIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(tickerItems.size) {
        while (true) {
            delay(4500L)
            if (tickerItems.isNotEmpty()) {
                tickerIndex = (tickerIndex + 1) % tickerItems.size
            }
        }
    }
    val activeTicker = tickerItems.getOrNull(tickerIndex % tickerItems.size.coerceAtLeast(1))

    val headerAccent = when (stageMode) {
        StageScreenMode.ANIM -> tokens.tealAccent
        StageScreenMode.VIDEO -> tokens.plumAccent
        StageScreenMode.NEWS -> tokens.coralAccent
    }

    BlueprintCard(
        modifier = modifier,
        accentBarColor = headerAccent
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Top Bar: STUDIO STAGE + [ANIM | VIDEO | NEWS] + Expand MP4 button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(headerAccent, CircleShape)
                    )
                    Text(
                        text = "STAGE",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        color = tokens.inkPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(modifier = Modifier.border(1.dp, tokens.border)) {
                        StageScreenMode.entries.forEach { tab ->
                            val selected = tab == stageMode
                            val tabBg = when (tab) {
                                StageScreenMode.ANIM -> tokens.tealAccent
                                StageScreenMode.VIDEO -> tokens.plumAccent
                                StageScreenMode.NEWS -> tokens.coralAccent
                            }
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .background(if (selected) tabBg else Color.Transparent)
                                    .clickable { onStageModeChange(tab) }
                                    .padding(horizontal = 7.dp)
                                    .testTag("stage_mode_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = if (selected) Color.White else tokens.inkPrimary
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(tokens.gold)
                            .border(1.dp, tokens.border)
                            .clickable(onClick = onOpenFullScreenVideo)
                            .testTag("stage_fullscreen_button")
                            .semantics { contentDescription = "Open full-screen MP4 & news player" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen video player",
                            tint = tokens.goldText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.5.dp, color = tokens.border)

            // Main Stage Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (stageMode) {
                    StageScreenMode.ANIM -> {
                        FigureStageAnimationView(
                            figureStyle = figureStyle,
                            isPlaying = isTrackPlaying,
                            reducedMotion = reducedMotion,
                            trackTitle = currentTrack?.title ?: "Studio Track",
                            onOpenFullScreen = onOpenFullScreenVideo
                        )
                    }

                    StageScreenMode.VIDEO -> {
                        CompactStageVideoView(
                            videoUri = selectedVideoUri,
                            muted = videoMuted,
                            onPickFromGallery = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            onPickMp4File = { mp4DocLauncher.launch(arrayOf("video/mp4", "video/*")) },
                            onToggleMute = onToggleVideoMuted,
                            onExpandFullScreen = onOpenFullScreenVideo
                        )
                    }

                    StageScreenMode.NEWS -> {
                        StageNewsReaderView(
                            newsState = newsState,
                            activeHeadlineIndex = tickerIndex,
                            onNextHeadline = {
                                if (newsState.headlines.isNotEmpty()) {
                                    tickerIndex = (tickerIndex + 1) % newsState.headlines.size
                                }
                            },
                            onRefresh = onRefreshNews,
                            onOpenLink = { url ->
                                if (url.isNotBlank()) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = tokens.gridMajor)

            // Always-on Bottom Auto-Running Live News & Studio Notification Ticker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.transportBg.copy(alpha = 0.65f))
                    .clickable {
                        onStageModeChange(StageScreenMode.NEWS)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("stage_news_ticker"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (activeTicker != null) {
                    Box(
                        modifier = Modifier
                            .background(activeTicker.badgeColor)
                            .border(1.dp, tokens.border)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = activeTicker.badge,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    AnimatedContent(
                        targetState = activeTicker.text,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tickerText",
                        modifier = Modifier.weight(1f)
                    ) { line ->
                        Text(
                            text = line,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            color = tokens.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = if (newsState.isLoading) "Loading live news headlines..." else "Tap NEWS to refresh studio feed",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = tokens.inkMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FigureStageAnimationView(
    figureStyle: MarchingFigureStyle,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    trackTitle: String,
    onOpenFullScreen: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val infiniteTransition = rememberInfiniteTransition(label = "stageAnim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 2400 else 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val phase = if (reducedMotion) 0.8f else animPhase

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(tokens.paperBg.copy(alpha = 0.5f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f

            when (figureStyle) {
                MarchingFigureStyle.FLAG_BEARER -> {
                    // Alpine ridges & animated wind ribbons
                    val hill1 = Path().apply {
                        moveTo(0f, h)
                        lineTo(w * 0.25f, h * 0.48f)
                        lineTo(w * 0.55f, h * 0.68f)
                        lineTo(w * 0.82f, h * 0.42f)
                        lineTo(w, h * 0.6f)
                        lineTo(w, h)
                        close()
                    }
                    drawPath(hill1, tokens.emeraldAccent.copy(alpha = 0.22f))
                    // Animated pennant wind ripples
                    for (band in 0..2) {
                        val yBase = h * (0.25f + band * 0.14f)
                        val wavePath = Path()
                        var x = 0f
                        wavePath.moveTo(0f, yBase)
                        while (x <= w) {
                            val wy = yBase + sin((x / w) * 4f * PI.toFloat() + phase + band) * (5.dp.toPx() + if (isPlaying) 3.dp.toPx() else 0f)
                            wavePath.lineTo(x, wy)
                            x += 10f
                        }
                        drawPath(
                            wavePath,
                            color = if (band == 1) tokens.gold else tokens.tealAccent.copy(alpha = 0.7f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                MarchingFigureStyle.DANCER -> {
                    // Lantern Glade: hanging amber lantern, luminous cyan pond & pulsing rings
                    val pondPath = Path().apply {
                        moveTo(0f, h * 0.74f)
                        quadraticTo(cx, h * 0.66f + sin(phase) * 4.dp.toPx(), w, h * 0.75f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(pondPath, tokens.tealAccent.copy(alpha = 0.28f))

                    // Expanding pulse ripples
                    for (r in 0..2) {
                        val t = ((phase / (2f * PI.toFloat())) + r / 3f) % 1f
                        drawCircle(
                            color = tokens.plumAccent.copy(alpha = (1f - t) * 0.55f),
                            radius = 12.dp.toPx() + t * (minOf(w, h) * 0.36f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    }
                    // Swinging amber lantern + floating fireflies
                    val lx = cx + sin(phase) * 14.dp.toPx()
                    drawLine(tokens.inkMuted, Offset(cx, 0f), Offset(lx, h * 0.26f), 1.4.dp.toPx())
                    drawCircle(tokens.gold.copy(alpha = 0.28f), 16.dp.toPx(), Offset(lx, h * 0.30f))
                    drawCircle(tokens.gold, 6.dp.toPx(), Offset(lx, h * 0.30f))

                    for (i in 0..5) {
                        val fx = w * (0.14f + 0.15f * i) + cos(phase + i) * 7.dp.toPx()
                        val fy = h * (0.28f + 0.12f * (i % 3)) + sin(phase * 1.3f + i) * 6.dp.toPx()
                        drawCircle(tokens.gold.copy(alpha = 0.8f), 2.5.dp.toPx(), Offset(fx, fy))
                    }
                }

                MarchingFigureStyle.SOLDIER -> {
                    // Regimental cadence chevrons & radar sweep
                    val barCount = 12
                    val barW = w / barCount
                    for (i in 0 until barCount) {
                        val amp = 0.3f + 0.65f * abs(sin(phase + i * 0.45f))
                        val barH = (h * 0.55f) * amp * (if (isPlaying) 1f else 0.45f)
                        val color = if (i % 2 == 0) tokens.coralAccent else tokens.gold
                        drawRect(
                            color = color.copy(alpha = 0.55f),
                            topLeft = Offset(i * barW + 3.dp.toPx(), h - barH),
                            size = Size((barW - 6.dp.toPx()).coerceAtLeast(3f), barH)
                        )
                    }
                }

                MarchingFigureStyle.DRUMMER -> {
                    // Concentric snare waveform rings & alternating beats
                    val beat = abs(sin(phase * 2f))
                    val colorList = listOf(tokens.emeraldAccent, tokens.gold, tokens.tealAccent)
                    colorList.forEachIndexed { idx, col ->
                        val radius = (14.dp.toPx() + idx * 14.dp.toPx()) + beat * (if (isPlaying) 9.dp.toPx() else 2.dp.toPx())
                        drawCircle(
                            color = col.copy(alpha = 0.7f - idx * 0.15f),
                            radius = radius,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.4.dp.toPx())
                        )
                    }
                }

                MarchingFigureStyle.CONDUCTOR -> {
                    // 5-line musical staff & sweeping baton arc
                    val staffGap = 10.dp.toPx()
                    val topY = cy - staffGap * 2f
                    for (i in 0..4) {
                        val ly = topY + i * staffGap
                        drawLine(tokens.inkMuted.copy(alpha = 0.45f), Offset(8.dp.toPx(), ly), Offset(w - 8.dp.toPx(), ly), 1.dp.toPx())
                    }
                    // Moving golden noteheads
                    for (n in 0..4) {
                        val nx = ((w * 0.15f) + (n * w * 0.18f) + (phase / (2f * PI.toFloat())) * 30.dp.toPx()) % (w - 20.dp.toPx()) + 10.dp.toPx()
                        val ny = topY + ((n % 4) + sin(phase + n) * 0.4f) * staffGap
                        drawCircle(if (n % 2 == 0) tokens.gold else tokens.plumAccent, 4.5.dp.toPx(), Offset(nx, ny))
                    }
                    // Sweeping baton arc
                    drawArc(
                        color = tokens.tealAccent,
                        startAngle = -140f + sin(phase) * 35f,
                        sweepAngle = 95f,
                        useCenter = false,
                        topLeft = Offset(cx - 28.dp.toPx(), cy - 28.dp.toPx()),
                        size = Size(56.dp.toPx(), 56.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // Bottom-left track & figure status chip
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(tokens.cardBg.copy(alpha = 0.88f))
                .border(1.dp, tokens.border)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "${figureStyle.shortLabel} · ${if (isPlaying) "LIVE" else "IDLE"}",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = tokens.tealAccent
            )
            Text(
                text = trackTitle,
                fontFamily = GaeguFontFamily,
                fontSize = 14.sp,
                color = tokens.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(tokens.cardBg.copy(alpha = 0.9f))
                .border(1.dp, tokens.plumAccent)
                .clickable(onClick = onOpenFullScreen)
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = "MP4 PLAYER ->",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp,
                color = tokens.plumAccent
            )
        }
    }
}

@Composable
private fun CompactStageVideoView(
    videoUri: Uri?,
    muted: Boolean,
    onPickFromGallery: () -> Unit,
    onPickMp4File: () -> Unit,
    onToggleMute: () -> Unit,
    onExpandFullScreen: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    var isVideoPlaying by remember(videoUri) { mutableStateOf(true) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(muted, mediaPlayerRef) {
        val vol = if (muted) 0f else 1f
        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
    }

    if (videoUri == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.OndemandVideo,
                contentDescription = "Select local video",
                tint = tokens.plumAccent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick a video or MP4 from storage",
                fontFamily = GaeguFontFamily,
                fontSize = 17.sp,
                color = tokens.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onPickFromGallery,
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, tokens.border),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.plumAccent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("stage_pick_video_gallery")
                ) {
                    Text("GALLERY", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                OutlinedButton(
                    onClick = onPickMp4File,
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, tokens.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("stage_pick_video_file")
                ) {
                    Text("MP4 FILE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = tokens.inkPrimary)
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    val vv = videoViewRef ?: return@clickable
                    if (vv.isPlaying) {
                        vv.pause()
                        isVideoPlaying = false
                    } else {
                        vv.start()
                        isVideoPlaying = true
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            val vol = if (muted) 0f else 1f
                            mp.setVolume(vol, vol)
                            start()
                            isVideoPlaying = true
                        }
                        setVideoURI(videoUri)
                    }
                },
                update = { vv ->
                    if (vv.tag != videoUri.toString()) {
                        vv.tag = videoUri.toString()
                        vv.setVideoURI(videoUri)
                        vv.start()
                        isVideoPlaying = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Compact overlay controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, tokens.gold)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isVideoPlaying) "Pause mini video" else "Play mini video",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            val vv = videoViewRef ?: return@clickable
                            if (vv.isPlaying) {
                                vv.pause()
                                isVideoPlaying = false
                            } else {
                                vv.start()
                                isVideoPlaying = true
                            }
                        }
                )
                Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) "Unmute video" else "Mute video",
                    tint = tokens.gold,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onToggleMute)
                )
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Change video",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onPickMp4File)
                )
                Text(
                    text = "FULL",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    color = tokens.gold,
                    modifier = Modifier
                        .clickable(onClick = onExpandFullScreen)
                        .padding(horizontal = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun StageNewsReaderView(
    newsState: NewsUiState,
    activeHeadlineIndex: Int,
    onNextHeadline: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLink: (String) -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val headline: NewsHeadline? = newsState.headlines.getOrNull(
        activeHeadlineIndex % newsState.headlines.size.coerceAtLeast(1)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.Newspaper, contentDescription = null, tint = tokens.coralAccent, modifier = Modifier.size(14.dp))
                Text(
                    text = headline?.source ?: "LIVE BRIEFING",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = tokens.coralAccent
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .border(1.dp, tokens.border)
                        .background(tokens.paperBg)
                        .clickable(onClick = onNextHeadline)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("NEXT", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = tokens.inkPrimary)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, tokens.border)
                        .background(tokens.paperBg)
                        .clickable(onClick = onRefresh)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("SYNC", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = tokens.tealAccent)
                }
            }
        }

        if (newsState.isLoading && headline == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = tokens.coralAccent, strokeWidth = 2.dp)
                Text("Fetching world & music news...", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = tokens.inkMuted)
            }
        } else if (headline != null) {
            Text(
                text = headline.title,
                fontFamily = GaeguFontFamily,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                color = tokens.inkPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenLink(headline.link) }
            )
            Text(
                text = listOfNotNull(
                    headline.publishedAt.takeIf { it.isNotBlank() },
                    if (headline.link.isNotBlank()) "TAP HEADLINE TO READ ->" else null
                ).joinToString(" · "),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 8.sp,
                color = tokens.tagBlue
            )
        } else {
            Text(
                text = newsState.errorMessage ?: "Tap SYNC to fetch live headlines.",
                fontFamily = GaeguFontFamily,
                fontSize = 17.sp,
                color = tokens.inkMuted
            )
        }
    }
}

@Composable
fun FullScreenVideoPlayerDialog(
    initialVideoUri: Uri?,
    initialMuted: Boolean,
    newsState: NewsUiState,
    onPickVideoUri: (Uri) -> Unit,
    onToggleMuted: () -> Unit,
    onRefreshNews: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val context = LocalContext.current

    BackHandler { onDismiss() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickVideoUri(uri)
    }
    val mp4DocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onPickVideoUri(uri)
        }
    }

    var isVideoPlaying by remember(initialVideoUri) { mutableStateOf(true) }
    var isLooping by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var totalDurMs by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var newsIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialMuted, mediaPlayerRef) {
        val vol = if (initialMuted) 0f else 1f
        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
    }

    LaunchedEffect(isLooping, mediaPlayerRef) {
        runCatching { mediaPlayerRef?.isLooping = isLooping }
    }

    LaunchedEffect(initialVideoUri, isVideoPlaying) {
        while (initialVideoUri != null) {
            val vv = videoViewRef
            if (vv != null) {
                runCatching {
                    val d = vv.duration.toLong()
                    if (d > 0L) totalDurMs = d
                    currentPosMs = vv.currentPosition.toLong().coerceAtLeast(0L)
                }
            }
            delay(350L)
        }
    }

    LaunchedEffect(newsState.headlines.size) {
        while (true) {
            delay(5000L)
            if (newsState.headlines.isNotEmpty()) {
                newsIndex = (newsIndex + 1) % newsState.headlines.size
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

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
                            text = "DAUDIO MP4 & NEWS PLAYER",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.4.sp,
                            color = tokens.inkPrimary
                        )
                        Text(
                            text = if (initialVideoUri != null) "Local storage video ready" else "Pick any MP4 or video file",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            color = tokens.plumAccent
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.border),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("GALLERY", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = tokens.inkPrimary)
                        }
                        OutlinedButton(
                            onClick = { mp4DocLauncher.launch(arrayOf("video/mp4", "video/*")) },
                            shape = RectangleShape,
                            border = BorderStroke(1.dp, tokens.plumAccent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("fullscreen_pick_mp4_button")
                        ) {
                            Text("MP4 FILE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = tokens.plumAccent)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.5.dp, tokens.border)
                                .clickable(onClick = onDismiss)
                                .testTag("fullscreen_video_close"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close video player", tint = tokens.inkPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Video Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .border(2.dp, tokens.border),
                    contentAlignment = Alignment.Center
                ) {
                    if (initialVideoUri == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OndemandVideo,
                                contentDescription = "No MP4 loaded",
                                tint = tokens.gold,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Select a downloaded news clip or video from storage",
                                fontFamily = GaeguFontFamily,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                            Button(
                                onClick = { mp4DocLauncher.launch(arrayOf("video/mp4", "video/*")) },
                                shape = RectangleShape,
                                border = BorderStroke(1.5.dp, tokens.gold),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText)
                            ) {
                                Text("OPEN MP4 FROM STORAGE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    videoViewRef = this
                                    setOnPreparedListener { mp ->
                                        mediaPlayerRef = mp
                                        mp.isLooping = isLooping
                                        val vol = if (initialMuted) 0f else 1f
                                        mp.setVolume(vol, vol)
                                        totalDurMs = duration.toLong().coerceAtLeast(0L)
                                        start()
                                        isVideoPlaying = true
                                    }
                                    setOnCompletionListener {
                                        if (!isLooping) isVideoPlaying = false
                                    }
                                    setVideoURI(initialVideoUri)
                                }
                            },
                            update = { vv ->
                                if (vv.tag != initialVideoUri.toString()) {
                                    vv.tag = initialVideoUri.toString()
                                    vv.setVideoURI(initialVideoUri)
                                    vv.start()
                                    isVideoPlaying = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Scrubber + Transport Controls
                if (initialVideoUri != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.cardBg)
                            .border(2.dp, tokens.border)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val frac = if (totalDurMs > 0L) (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f
                        Slider(
                            value = frac,
                            onValueChange = { f ->
                                val target = (f * totalDurMs).toInt()
                                runCatching { videoViewRef?.seekTo(target) }
                                currentPosMs = target.toLong()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = tokens.gold,
                                activeTrackColor = tokens.plumAccent,
                                inactiveTrackColor = tokens.gridMajor
                            ),
                            modifier = Modifier.fillMaxWidth().height(26.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatTimeMs(currentPosMs)} / ${formatTimeMs(totalDurMs)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = tokens.inkPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        runCatching {
                                            videoViewRef?.seekTo(0)
                                            videoViewRef?.start()
                                            isVideoPlaying = true
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = "Restart video", tint = tokens.inkPrimary)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(tokens.gold)
                                        .border(1.5.dp, tokens.border)
                                        .clickable {
                                            val vv = videoViewRef ?: return@clickable
                                            if (vv.isPlaying) {
                                                vv.pause()
                                                isVideoPlaying = false
                                            } else {
                                                vv.start()
                                                isVideoPlaying = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isVideoPlaying) "Pause" else "Play",
                                        tint = tokens.goldText
                                    )
                                }
                                IconButton(onClick = onToggleMuted, modifier = Modifier.size(34.dp)) {
                                    Icon(
                                        imageVector = if (initialMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (initialMuted) "Unmute" else "Mute",
                                        tint = tokens.tealAccent
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, if (isLooping) tokens.emeraldAccent else tokens.border)
                                        .background(if (isLooping) tokens.emeraldAccent.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable { isLooping = !isLooping }
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Text("LOOP", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = tokens.inkPrimary)
                                }
                            }
                        }
                    }
                }

                // Live News Ticker Strip inside MP4 Player
                val headline = newsState.headlines.getOrNull(newsIndex % newsState.headlines.size.coerceAtLeast(1))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.transportBg)
                        .border(1.5.dp, tokens.border)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(tokens.coralAccent)
                            .border(1.dp, tokens.border)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = headline?.source ?: "NEWS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = headline?.title ?: newsState.errorMessage ?: "Tap refresh to sync live headlines",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = tokens.inkPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (newsState.headlines.isNotEmpty()) {
                                newsIndex = (newsIndex + 1) % newsState.headlines.size
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next headline", tint = tokens.inkPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onRefreshNews, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh news", tint = tokens.tealAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
