package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.RepeatMode
import com.example.data.model.ShareFormat
import com.example.data.model.SheetType
import com.example.data.model.Track
import com.example.ui.auth.signOutUser
import com.example.ui.components.BlueprintCard
import com.example.ui.components.BlueprintGridBackground
import com.example.ui.components.FullScreenVideoPlayerDialog
import com.example.ui.components.LibraryVerticalStrip
import com.example.ui.components.MarchingFigureProgressBar
import com.example.ui.components.NotepadCard
import com.example.ui.components.ShareVerticalStrip
import com.example.ui.components.StudioStageCard
import com.example.ui.components.ThemeVerticalStrip
import com.example.ui.components.formatTimeMs
import com.example.ui.games.StudioGamesScreen
import com.example.ui.sheets.BluetoothSheet
import com.example.ui.sheets.EditSongInfoSheet
import com.example.ui.sheets.LoadAudioSheet
import com.example.ui.sheets.MarchingFigureSheet
import com.example.ui.sheets.NotepadLibrarySheet
import com.example.ui.sheets.PlaylistSheet
import com.example.ui.sheets.SettingsSheet
import com.example.ui.sheets.ShareToSocialsSheet
import com.example.ui.sheets.ThemeStyleSheet
import com.example.ui.theme.BlueprintThemeStyle
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import kotlinx.coroutines.delay

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    userEmail: String?,
    onSignedOut: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val sheets by viewModel.sheets.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val notepadMode by viewModel.notepadMode.collectAsStateWithLifecycle()
    val noteText by viewModel.noteText.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val redoCount by viewModel.redoCount.collectAsStateWithLifecycle()
    val recordOverTrack by viewModel.recordOverTrack.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val inlinePlayingSheetId by viewModel.inlinePlayingSheetId.collectAsStateWithLifecycle()
    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val marchingFigure by viewModel.marchingFigure.collectAsStateWithLifecycle()
    val notepadBackground by viewModel.notepadBackground.collectAsStateWithLifecycle()
    val stageMode by viewModel.stageMode.collectAsStateWithLifecycle()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsStateWithLifecycle()
    val videoMuted by viewModel.videoMuted.collectAsStateWithLifecycle()
    val showFullScreenVideo by viewModel.showFullScreenVideo.collectAsStateWithLifecycle()
    val showGamesPage by viewModel.showGamesPage.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val recorderState by viewModel.voiceRecorder.state.collectAsStateWithLifecycle()

    if (showGamesPage) {
        StudioGamesScreen(onBack = viewModel::closeGamesPage)
        return
    }

    val currentTrack = remember(tracks, playerState.currentTrackId) {
        tracks.firstOrNull { it.id == playerState.currentTrackId } ?: tracks.firstOrNull()
    }
    val currentIndex = remember(tracks, currentTrack) {
        tracks.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
    }
    var playlistExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = activeSheet != ActiveSheet.None) {
        viewModel.closeSheet()
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2600)
            viewModel.clearToast()
        }
    }

    BlueprintGridBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compact Blueprint Studio Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isBootstrapping) tokens.gold else tokens.emeraldAccent, CircleShape)
                                .border(1.dp, tokens.border, CircleShape)
                        )
                        Text(
                            text = "DAUDIO BY ENSIS",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                            color = tokens.inkPrimary,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Games subpage button
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .background(tokens.plumAccent.copy(alpha = 0.16f))
                                .border(1.5.dp, tokens.plumAccent)
                                .clickable { viewModel.openGamesPage() }
                                .padding(horizontal = 7.dp)
                                .testTag("open_games_button")
                                .semantics { contentDescription = "Open Studio Games" },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Casino,
                                    contentDescription = "Games",
                                    tint = tokens.plumAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "GAMES",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = tokens.inkPrimary
                                )
                            }
                        }

                        // Bluetooth audio button
                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .background(
                                    if (bluetoothState.isBluetoothAudioActive) tokens.emeraldAccent.copy(alpha = 0.2f)
                                    else tokens.tealAccent.copy(alpha = 0.14f)
                                )
                                .border(
                                    1.5.dp,
                                    if (bluetoothState.isBluetoothAudioActive) tokens.emeraldAccent else tokens.tealAccent
                                )
                                .clickable {
                                    viewModel.bluetoothController.refreshState()
                                    viewModel.openSheet(ActiveSheet.Bluetooth)
                                }
                                .padding(horizontal = 7.dp)
                                .testTag("open_bluetooth_button")
                                .semantics { contentDescription = "Bluetooth Audio Connection" },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (bluetoothState.isBluetoothAudioActive) {
                                        Icons.Default.BluetoothConnected
                                    } else {
                                        Icons.Default.Bluetooth
                                    },
                                    contentDescription = "Bluetooth",
                                    tint = if (bluetoothState.isBluetoothAudioActive) tokens.emeraldAccent else tokens.tealAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "BT",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = tokens.inkPrimary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(30.dp)
                                .background(tokens.cardBg)
                                .border(1.5.dp, tokens.border)
                                .clickable {
                                    viewModel.toggleDayNightTheme()
                                }
                                .padding(horizontal = 7.dp)
                                .testTag("theme_toggle_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (themeStyle.isDarkSurface) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle light or night blueprint theme",
                                    tint = tokens.gold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = themeStyle.shortLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    color = tokens.inkPrimary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(tokens.cardBg)
                                .border(1.5.dp, tokens.border)
                                .clickable { viewModel.openSheet(ActiveSheet.Settings) }
                                .testTag("settings_button")
                                .semantics { contentDescription = "Open Studio Settings" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = tokens.inkPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // ROW 1: THREE COLUMNS (REF 01 Hero Card | NOTEPAD LIBRARY Strip | NOTEPAD Card)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(228.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Column 1: REF 01 Hero Card
                    BlueprintCard(
                        modifier = Modifier
                            .weight(0.92f)
                            .fillMaxHeight(),
                        showDiagonalHatch = true,
                        showCornerBracket = true,
                        showCornerCircleArc = true,
                        accentBarColor = tokens.gold
                    ) {
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
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(tokens.tealAccent, CircleShape)
                                    )
                                    Text(
                                        text = "REF %02d".format(currentIndex + 1),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.6.sp,
                                        color = tokens.tealAccent
                                    )
                                }
                                if (currentTrack != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(tokens.cardBg)
                                            .border(1.dp, tokens.border)
                                            .clickable { viewModel.openSheet(ActiveSheet.EditSongInfo) }
                                            .testTag("hero_edit_info_button")
                                            .semantics { contentDescription = "Edit song info" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit song info",
                                            tint = tokens.inkPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(tokens.gold, CircleShape)
                                        .border(1.dp, tokens.border, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentTrack?.title ?: "daudio",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 21.sp,
                                    lineHeight = 24.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(tokens.emeraldAccent.copy(alpha = 0.14f))
                                    .border(1.dp, tokens.emeraldAccent)
                                    .clickable { viewModel.openSheet(ActiveSheet.LoadAudio) }
                                    .padding(horizontal = 6.dp)
                                    .testTag("hero_load_audio_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = tokens.emeraldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LOAD AUDIO",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Column 2: Deep Navy NOTEPAD LIBRARY vertical strip
                    LibraryVerticalStrip(
                        sheetCount = sheets.size,
                        onClick = { viewModel.openSheet(ActiveSheet.Library) },
                        modifier = Modifier
                            .width(48.dp)
                            .fillMaxHeight()
                    )

                    // Column 3: NOTEPAD Card with WRITE | DRAW | REC
                    NotepadCard(
                        mode = notepadMode,
                        onModeChange = viewModel::setNotepadMode,
                        noteText = noteText,
                        onNoteTextChange = viewModel::onNoteTextChanged,
                        onSaveNote = viewModel::saveNoteToLibrary,
                        strokes = strokes,
                        redoCount = redoCount,
                        onAddStroke = viewModel::addDrawingStroke,
                        onUndoStroke = viewModel::undoDrawingStroke,
                        onRedoStroke = viewModel::redoDrawingStroke,
                        onClearStrokes = viewModel::clearDrawing,
                        onSaveDrawing = { size ->
                            viewModel.saveDrawingToLibrary(size, tokens.paperBg, tokens.gridMinor)
                        },
                        recorderState = recorderState,
                        recordOverTrack = recordOverTrack,
                        onToggleRecordOverTrack = viewModel::toggleRecordOverTrack,
                        onStartRecording = viewModel::startRecording,
                        onPauseRecording = viewModel.voiceRecorder::pauseRecording,
                        onResumeRecording = viewModel.voiceRecorder::resumeRecording,
                        onStopRecording = viewModel::stopRecording,
                        onTogglePreviewRecording = viewModel.voiceRecorder::togglePreviewPlayback,
                        onDiscardRecording = viewModel.voiceRecorder::discard,
                        onSaveRecording = viewModel::saveRecordingToLibrary,
                        onDownloadRecording = viewModel::downloadLocalFile,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        isPlaying = playerState.isPlaying,
                        hasFinishedTrack = playerState.hasFinishedTrack,
                        reducedMotion = settings.reducedMotion,
                        hapticsEnabled = settings.hapticsEnabled,
                        figureStyle = marchingFigure,
                        onSelectFigure = viewModel::setMarchingFigure,
                        onTogglePlayPause = viewModel.audioPlayer::togglePlayPause,
                        onSeekFraction = { frac -> viewModel.audioPlayer.seekByProgress(frac) },
                        themeStyle = themeStyle,
                        notepadBackground = notepadBackground,
                        onCycleTheme = {
                            val entries = BlueprintThemeStyle.entries
                            val next = entries[(entries.indexOf(themeStyle) + 1) % entries.size]
                            viewModel.setThemeStyle(next)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    )
                }

                // ROW 2+: LEFT STACK (TRANSPORT + LYRICS + PLAYLIST) + RIGHT STRIPS (MARCHING PROGRESS + SHARE TO SOCIALS)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Main Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // TRANSPORT CARD
                        TransportCard(
                            trackCounter = "%02d/%02d".format(
                                if (tracks.isEmpty()) 0 else currentIndex + 1,
                                tracks.size
                            ),
                            audioOutputName = bluetoothState.activeAudioOutputName,
                            isBluetoothActive = bluetoothState.isBluetoothAudioActive,
                            isPlaying = playerState.isPlaying,
                            isLoading = playerState.isLoadingAudio,
                            positionMs = playerState.positionMs,
                            durationMs = playerState.durationMs,
                            shuffleEnabled = playerState.shuffleEnabled,
                            repeatMode = playerState.repeatMode,
                            speed = playerState.speed,
                            volume = playerState.volume,
                            onPrev = {
                                if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.previousTrack()
                            },
                            onPlayPause = {
                                if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.audioPlayer.togglePlayPause()
                            },
                            onNext = {
                                if (settings.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.nextTrack()
                            },
                            onToggleShuffle = viewModel.audioPlayer::toggleShuffle,
                            onCycleRepeat = viewModel.audioPlayer::cycleRepeatMode,
                            onCycleSpeed = viewModel.audioPlayer::cycleSpeed,
                            onVolumeChange = viewModel.audioPlayer::setVolume,
                            onOpenBluetooth = {
                                viewModel.bluetoothController.refreshState()
                                viewModel.openSheet(ActiveSheet.Bluetooth)
                            }
                        )

                        // ANIMATED STAGE / MINI VIDEO / LIVE NEWS SCREEN
                        StudioStageCard(
                            stageMode = stageMode,
                            onStageModeChange = viewModel::setStageMode,
                            figureStyle = marchingFigure,
                            isTrackPlaying = playerState.isPlaying,
                            reducedMotion = settings.reducedMotion,
                            currentTrack = currentTrack,
                            isBluetoothActive = bluetoothState.isBluetoothAudioActive,
                            bluetoothOutputName = bluetoothState.activeAudioOutputName,
                            sheetsCount = sheets.size,
                            selectedVideoUri = selectedVideoUri,
                            videoMuted = videoMuted,
                            onPickVideoUri = viewModel::setSelectedVideoUri,
                            onToggleVideoMuted = viewModel::toggleVideoMuted,
                            onOpenFullScreenVideo = viewModel::openFullScreenVideo,
                            newsState = newsState,
                            onRefreshNews = viewModel::refreshNews,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        // PLAYLIST CARD (collapsible + opens full reorderable sheet)
                        PlaylistSummaryCard(
                            tracks = tracks,
                            currentTrack = currentTrack,
                            currentIndex = currentIndex,
                            expanded = playlistExpanded,
                            onToggleExpand = { playlistExpanded = !playlistExpanded },
                            onSelectTrack = { viewModel.playTrack(it) },
                            onOpenFullPlaylist = { viewModel.openSheet(ActiveSheet.Playlist) }
                        )
                    }

                    // Tall Strip 1: SIGNATURE MARCHING FIGURE PROGRESS BAR
                    MarchingFigureProgressBar(
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        isPlaying = playerState.isPlaying,
                        hasFinishedTrack = playerState.hasFinishedTrack,
                        reducedMotion = settings.reducedMotion,
                        hapticsEnabled = settings.hapticsEnabled,
                        figureStyle = marchingFigure,
                        onOpenFigurePicker = { viewModel.openSheet(ActiveSheet.MarchingFigure) },
                        onSeekFraction = { frac -> viewModel.audioPlayer.seekByProgress(frac) },
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                    )

                    // Tall Strip 2 (Divided): THEMES (top) + SHARE SOCIALS (bottom)
                    Column(
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeVerticalStrip(
                            themeLabel = themeStyle.shortLabel,
                            onClick = { viewModel.openSheet(ActiveSheet.ThemeStyle) },
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxWidth()
                        )
                        ShareVerticalStrip(
                            onClick = { viewModel.openSheet(ActiveSheet.ShareToSocials) },
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            // Bottom Toast Confirmation Banner
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(tokens.inkPrimary)
                        .border(2.dp, tokens.gold)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = toastMessage ?: "",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tokens.cardBg
                    )
                }
            }

            // Active Bottom Sheets
            when (activeSheet) {
                ActiveSheet.None -> Unit
                ActiveSheet.Library -> {
                    NotepadLibrarySheet(
                        sheets = sheets,
                        inlinePlayingSheetId = inlinePlayingSheetId,
                        onSaveCurrentNotepad = {
                            viewModel.saveCurrentNotepad(tokens.paperBg, tokens.gridMinor)
                        },
                        onOpenNote = viewModel::openNoteInEditor,
                        onRenameSheet = viewModel::renameSheet,
                        onDeleteSheet = viewModel::deleteSheet,
                        onShareSheet = viewModel::shareSheet,
                        onDownloadSheet = viewModel::downloadSheet,
                        onToggleRecordingPlay = viewModel::toggleInlineSheetRecording,
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.LoadAudio -> {
                    LoadAudioSheet(
                        uploadProgress = uploadProgress,
                        onFilesPicked = viewModel::uploadAudioUris,
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.EditSongInfo -> {
                    currentTrack?.let { tr ->
                        EditSongInfoSheet(
                            track = tr,
                            onSave = { title, artist, album, tagsRaw, lyrics ->
                                viewModel.updateTrackInfo(tr.id, title, artist, album, tagsRaw, lyrics)
                            },
                            onDismiss = viewModel::closeSheet
                        )
                    }
                }
                ActiveSheet.Playlist -> {
                    PlaylistSheet(
                        tracks = tracks,
                        currentTrackId = currentTrack?.id,
                        onSelectTrack = {
                            viewModel.playTrack(it)
                            viewModel.closeSheet()
                        },
                        onMoveTrack = viewModel::moveTrack,
                        onDeleteTrack = viewModel::deleteTrack,
                        onAddAudioClick = { viewModel.openSheet(ActiveSheet.LoadAudio) },
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.ShareToSocials -> {
                    currentTrack?.let { tr ->
                        val fmt = runCatching { ShareFormat.valueOf(settings.defaultShareFormat) }
                            .getOrDefault(ShareFormat.STORY_9_16)
                        ShareToSocialsSheet(
                            track = tr,
                            defaultFormat = fmt,
                            savedDrawings = sheets.filter { it.type == SheetType.DRAWING },
                            noteExcerpt = noteText,
                            onCopyCaption = { caption -> viewModel.copyToClipboard("daudio Caption", caption) },
                            onDownloadPng = { bmp -> viewModel.saveBitmapToGallery(bmp, "daudio_${tr.title}") },
                            onSharePng = viewModel::shareBitmap,
                            onExportAudioClip = viewModel::shareTrackAudioClip,
                            onDismiss = viewModel::closeSheet
                        )
                    }
                }
                ActiveSheet.Settings -> {
                    SettingsSheet(
                        settings = settings,
                        themeStyle = themeStyle,
                        marchingFigure = marchingFigure,
                        notepadBackground = notepadBackground,
                        userEmail = userEmail,
                        onSelectThemeStyle = viewModel::setThemeStyle,
                        onSelectMarchingFigure = viewModel::setMarchingFigure,
                        onSelectNotepadBackground = viewModel::setNotepadBackground,
                        onUpdateSettings = viewModel::updateSettings,
                        onExportZip = viewModel::exportAllAsZip,
                        onReplayOnboarding = viewModel::replayOnboarding,
                        onClearData = viewModel::clearAllData,
                        onSignOut = {
                            viewModel.closeSheet()
                            signOutUser(credentialManager, scope, onSignedOut)
                        },
                        onDeleteAccount = {
                            viewModel.closeSheet()
                            viewModel.deleteAccountData {
                                signOutUser(credentialManager, scope, onSignedOut)
                            }
                        },
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.ThemeStyle -> {
                    ThemeStyleSheet(
                        currentStyle = themeStyle,
                        currentBackground = notepadBackground,
                        onSelectStyle = viewModel::setThemeStyle,
                        onSelectBackground = viewModel::setNotepadBackground,
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.MarchingFigure -> {
                    MarchingFigureSheet(
                        currentFigure = marchingFigure,
                        onSelectFigure = viewModel::setMarchingFigure,
                        onDismiss = viewModel::closeSheet
                    )
                }
                ActiveSheet.Bluetooth -> {
                    BluetoothSheet(
                        btState = bluetoothState,
                        onRefresh = viewModel.bluetoothController::refreshState,
                        onStartScan = viewModel.bluetoothController::startScan,
                        onStopScan = viewModel.bluetoothController::stopScan,
                        onPairDevice = viewModel.bluetoothController::pairDevice,
                        onRouteAudio = viewModel.bluetoothController::routeAudioToBluetoothOrSpeaker,
                        onOpenSystemSettings = viewModel.bluetoothController::openSystemBluetoothSettings,
                        onDismiss = viewModel::closeSheet
                    )
                }
            }

            if (showFullScreenVideo) {
                FullScreenVideoPlayerDialog(
                    initialVideoUri = selectedVideoUri,
                    initialMuted = videoMuted,
                    newsState = newsState,
                    onPickVideoUri = viewModel::setSelectedVideoUri,
                    onToggleMuted = viewModel::toggleVideoMuted,
                    onRefreshNews = viewModel::refreshNews,
                    onDismiss = viewModel::closeFullScreenVideo
                )
            }

            // First-run 3-screen Onboarding Overlay
            if (!isBootstrapping && !settings.onboardingCompleted) {
                OnboardingOverlay(
                    onFinish = viewModel::completeOnboarding
                )
            }
        }
    }
}

@Composable
private fun TransportCard(
    trackCounter: String,
    audioOutputName: String,
    isBluetoothActive: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    speed: Float,
    volume: Float,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenBluetooth: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = tokens.transportBg,
        accentBarColor = tokens.gold
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(tokens.gold, CircleShape)
                            .border(1.dp, tokens.border, CircleShape)
                    )
                    Text(
                        text = "TRANSPORT",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = tokens.inkPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                if (isBluetoothActive) tokens.emeraldAccent.copy(alpha = 0.2f)
                                else tokens.paperBg.copy(alpha = 0.7f)
                            )
                            .border(
                                1.dp,
                                if (isBluetoothActive) tokens.emeraldAccent else tokens.gridMajor
                            )
                            .clickable(onClick = onOpenBluetooth)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isBluetoothActive) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = "Audio output route",
                            tint = if (isBluetoothActive) tokens.emeraldAccent else tokens.inkMuted,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = audioOutputName.take(12),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBluetoothActive) tokens.emeraldAccent else tokens.inkMuted,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(tokens.cardBg)
                            .border(1.dp, tokens.border)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = trackCounter,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = tokens.inkPrimary
                        )
                    }
                }
            }

            // Main Transport Buttons: Prev, Big Gold Play/Pause, Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(tokens.paperBg)
                        .border(1.5.dp, tokens.border)
                        .clickable(onClick = onPrev)
                        .testTag("transport_prev_button")
                        .semantics { contentDescription = "Previous track" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = tokens.inkPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(tokens.shadow, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(tokens.gold)
                            .border(2.dp, tokens.border, CircleShape)
                            .clickable(enabled = !isLoading, onClick = onPlayPause)
                            .testTag("transport_play_pause_button")
                            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = tokens.goldText,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = tokens.goldText,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(tokens.paperBg)
                        .border(1.5.dp, tokens.border)
                        .clickable(onClick = onNext)
                        .testTag("transport_next_button")
                        .semantics { contentDescription = "Next track" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = tokens.inkPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Time display
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${formatTimeMs(positionMs)} / ${formatTimeMs(durationMs)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
                    color = tokens.inkPrimary
                )
                Text(
                    text = "TIME",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp,
                    color = tokens.inkMuted
                )
            }

            // Compact Shuffle, Repeat, Speed & Volume row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(if (shuffleEnabled) tokens.tealAccent else tokens.paperBg)
                        .border(1.dp, if (shuffleEnabled) tokens.tealAccent else tokens.border)
                        .clickable(onClick = onToggleShuffle)
                        .testTag("transport_shuffle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) Color.White else tokens.inkPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(if (repeatMode != RepeatMode.OFF) tokens.plumAccent else tokens.paperBg)
                        .border(1.dp, if (repeatMode != RepeatMode.OFF) tokens.plumAccent else tokens.border)
                        .clickable(onClick = onCycleRepeat)
                        .testTag("transport_repeat_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat mode",
                        tint = if (repeatMode != RepeatMode.OFF) Color.White else tokens.inkPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .background(tokens.paperBg)
                        .border(1.dp, tokens.border)
                        .clickable(onClick = onCycleSpeed)
                        .padding(horizontal = 6.dp)
                        .testTag("transport_speed_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%.2gx".format(speed),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = tokens.inkPrimary
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = tokens.inkMuted,
                    modifier = Modifier.size(14.dp)
                )
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = tokens.gold,
                        activeTrackColor = tokens.inkPrimary,
                        inactiveTrackColor = tokens.gridMajor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistSummaryCard(
    tracks: List<Track>,
    currentTrack: Track?,
    currentIndex: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectTrack: (Track) -> Unit,
    onOpenFullPlaylist: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        accentBarColor = tokens.plumAccent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFullPlaylist)
                    .testTag("playlist_card_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(tokens.plumAccent, CircleShape)
                        )
                        Text(
                            text = "PLAYLIST",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.8.sp,
                            color = tokens.inkPrimary
                        )
                    }
                    Text(
                        text = currentTrack?.title ?: "No tracks",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "%02d/%02d".format(
                            if (tracks.isEmpty()) 0 else currentIndex + 1,
                            tracks.size
                        ),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = tokens.inkMuted
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onToggleExpand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand playlist",
                            tint = tokens.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(color = tokens.gridMajor)
                tracks.take(4).forEachIndexed { idx, tr ->
                    val active = tr.id == currentTrack?.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (active) tokens.gold.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectTrack(tr) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%02d  %s".format(idx + 1, tr.title),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = tokens.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatTimeMs(tr.durationMs),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = tokens.inkMuted
                        )
                    }
                }
                Text(
                    text = "OPEN FULL PLAYLIST & REORDER ->",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = tokens.tagBlue,
                    modifier = Modifier
                        .clickable(onClick = onOpenFullPlaylist)
                        .padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingOverlay(
    onFinish: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    var step by remember { mutableIntStateOf(0) }

    val titles = listOf(
        "01 // MARCHING AUTOMATION & THEMES",
        "02 // CUSTOM PAPER & 3-MODE NOTEPAD",
        "03 // STAGE SCREEN, MP4 & LIVE NEWS"
    )
    val bodies = listOf(
        "Pick Flag Bearer, Pulse Dancer, Soldier, Drummer, or Maestro on the progress strip—themes and stage animations automatically pair with each figure.",
        "Switch between WRITE, DRAW, and REC in vibrant modes, and open THEMES to customize paper quality from Squares to Gutenberg Press 1455.",
        "Watch live figure animations, queue any local MP4 video inside the Studio Stage or Fullscreen Player, and follow auto-scrolling live news & studio alerts."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        BlueprintCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp),
            showDiagonalHatch = true,
            showCornerBracket = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "STUDIO GUIDE · ${step + 1}/3",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.8.sp,
                    color = tokens.gold
                )
                Text(
                    text = titles[step],
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = tokens.inkPrimary
                )
                Text(
                    text = bodies[step],
                    fontFamily = GaeguFontFamily,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    color = tokens.inkPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onFinish,
                        shape = RectangleShape,
                        border = BorderStroke(1.5.dp, tokens.border),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("SKIP", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                    }
                    Button(
                        onClick = {
                            if (step < 2) step++ else onFinish()
                        },
                        shape = RectangleShape,
                        border = BorderStroke(1.5.dp, tokens.border),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = if (step < 2) "NEXT" else "ENTER STUDIO",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
