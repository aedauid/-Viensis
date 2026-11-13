package com.example.ui.sheets

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.bluetooth.BluetoothAudioState
import com.example.data.model.AudioQuality
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.data.model.NotepadSheet
import com.example.data.model.ShareFormat
import com.example.data.model.SheetType
import com.example.data.model.Track
import com.example.data.model.UserSettings
import com.example.ui.components.BlueprintBottomSheet
import com.example.ui.components.drawMarchingFigureArchetype
import com.example.ui.components.drawNotepadPaperPattern
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.BlueprintThemeStyle
import com.example.ui.theme.GaeguFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalBlueprintTokens
import com.example.ui.theme.pairedThemeStyle
import com.example.ui.theme.tokensForStyle
import java.text.SimpleDateFormat
import java.util.Locale

private enum class LibraryFilter(val label: String, val type: String?) {
    ALL("ALL", null),
    NOTES("NOTES", SheetType.NOTE),
    DRAWINGS("DRAWINGS", SheetType.DRAWING),
    RECORDINGS("RECORDINGS", SheetType.RECORDING)
}

private enum class LibrarySort(val label: String) {
    DATE("DATE"),
    NAME("NAME")
}

@Composable
fun NotepadLibrarySheet(
    sheets: List<NotepadSheet>,
    inlinePlayingSheetId: String?,
    onSaveCurrentNotepad: () -> Unit,
    onOpenNote: (NotepadSheet) -> Unit,
    onRenameSheet: (String, String) -> Unit,
    onDeleteSheet: (NotepadSheet) -> Unit,
    onShareSheet: (NotepadSheet) -> Unit,
    onDownloadSheet: (NotepadSheet) -> Unit,
    onToggleRecordingPlay: (NotepadSheet) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var sort by remember { mutableStateOf(LibrarySort.DATE) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingSheet by remember { mutableStateOf<NotepadSheet?>(null) }
    var renameText by remember { mutableStateOf("") }
    var viewingDrawingSheet by remember { mutableStateOf<NotepadSheet?>(null) }

    val filteredSheets = remember(sheets, filter, sort, searchQuery) {
        sheets.filter { s ->
            (filter.type == null || s.type == filter.type) &&
                (searchQuery.isBlank() ||
                    s.title.contains(searchQuery, ignoreCase = true) ||
                    s.content.contains(searchQuery, ignoreCase = true))
        }.let { list ->
            when (sort) {
                LibrarySort.DATE -> list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                LibrarySort.NAME -> list.sortedBy { it.title.lowercase() }
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    BlueprintBottomSheet(
        title = "NOTEPAD LIBRARY",
        subtitle = "%d sheet%s synced to your studio".format(sheets.size, if (sheets.size == 1) "" else "s"),
        onDismiss = onDismiss,
        primaryButtonLabel = "SAVE CURRENT NOTEPAD",
        onPrimaryClick = onSaveCurrentNotepad,
        cancelButtonLabel = "CLOSE"
    ) {
        // Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, tokens.border),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LibraryFilter.entries.forEach { tab ->
                val selected = tab == filter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(if (selected) tokens.inkPrimary else tokens.cardBg)
                        .clickable { filter = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        color = if (selected) tokens.cardBg else tokens.inkPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search + Sort row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(tokens.cardBg)
                    .border(1.dp, tokens.border)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = tokens.inkMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = tokens.inkPrimary),
                    cursorBrush = SolidColor(tokens.gold),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search sheets...", fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = tokens.inkMuted)
                        }
                        inner()
                    }
                )
            }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .border(1.dp, tokens.border)
                    .background(tokens.cardBg)
                    .clickable {
                        sort = if (sort == LibrarySort.DATE) LibrarySort.NAME else LibrarySort.DATE
                    }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SORT: ${sort.label}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = tokens.inkPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredSheets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(vertical = 34.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (sheets.isEmpty()) {
                        "No sheets yet. Write something in the notepad and hit save."
                    } else {
                        "No matching sheets in this filter."
                    },
                    fontFamily = GaeguFontFamily,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    color = tokens.inkMuted
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredSheets.forEach { sheet ->
                    val dateStr = sheet.createdAt?.toDate()?.let { dateFormatter.format(it) } ?: "Just now"
                    val isPlaying = inlinePlayingSheetId == sheet.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.cardBg)
                            .border(1.5.dp, tokens.border)
                            .clickable {
                                when (sheet.type) {
                                    SheetType.NOTE -> onOpenNote(sheet)
                                    SheetType.DRAWING -> viewingDrawingSheet = sheet
                                    SheetType.RECORDING -> onToggleRecordingPlay(sheet)
                                }
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Thumbnail or type icon
                            when (sheet.type) {
                                SheetType.DRAWING -> {
                                    val bmp = remember(sheet.imageBase64) {
                                        runCatching {
                                            val bytes = Base64.decode(sheet.imageBase64, Base64.NO_WRAP)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .border(1.dp, tokens.border)
                                            .background(tokens.paperBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp,
                                                contentDescription = sheet.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        } else {
                                            Icon(Icons.Default.Brush, contentDescription = null, tint = tokens.inkPrimary)
                                        }
                                    }
                                }
                                SheetType.RECORDING -> {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(tokens.gold)
                                            .border(1.5.dp, tokens.border)
                                            .clickable { onToggleRecordingPlay(sheet) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause take" else "Play take",
                                            tint = tokens.goldText
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(tokens.paperBg)
                                            .border(1.dp, tokens.border),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, tint = tokens.inkPrimary)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sheet.title,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sheet.type.uppercase()} · $dateStr" +
                                        if (sheet.type == SheetType.RECORDING) " · ${formatTimeMs(sheet.durationMs)}" else "",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = tokens.inkMuted
                                )
                                if (sheet.type == SheetType.NOTE && sheet.content.isNotBlank()) {
                                    Text(
                                        text = sheet.content,
                                        fontFamily = GaeguFontFamily,
                                        fontSize = 16.sp,
                                        color = tokens.inkPrimary.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Action row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (sheet.type) {
                                    SheetType.NOTE -> "LOAD"
                                    SheetType.DRAWING -> "VIEW"
                                    SheetType.RECORDING -> if (isPlaying) "STOP" else "PLAY"
                                    else -> "OPEN"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = tokens.tagBlue,
                                modifier = Modifier
                                    .clickable {
                                        when (sheet.type) {
                                            SheetType.NOTE -> onOpenNote(sheet)
                                            SheetType.DRAWING -> viewingDrawingSheet = sheet
                                            SheetType.RECORDING -> onToggleRecordingPlay(sheet)
                                            else -> onOpenNote(sheet)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            IconButton(
                                onClick = {
                                    renamingSheet = sheet
                                    renameText = sheet.title
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = tokens.inkPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onDownloadSheet(sheet) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = tokens.inkPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onShareSheet(sheet) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share", tint = tokens.inkPrimary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onDeleteSheet(sheet) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = tokens.danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename dialog
    renamingSheet?.let { sheet ->
        AlertDialog(
            onDismissRequest = { renamingSheet = null },
            containerColor = tokens.paperBg,
            title = {
                Text("RENAME SHEET", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.inkPrimary)
            },
            text = {
                BasicTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 14.sp, color = tokens.inkPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, tokens.border)
                        .background(tokens.cardBg)
                        .padding(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRenameSheet(sheet.id, renameText)
                    renamingSheet = null
                }) {
                    Text("SAVE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = tokens.gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSheet = null }) {
                    Text("CANCEL", fontFamily = JetBrainsMonoFontFamily, color = tokens.inkMuted)
                }
            }
        )
    }

    // Full drawing viewer
    viewingDrawingSheet?.let { sheet ->
        val bmp = remember(sheet.imageBase64) {
            runCatching {
                val bytes = Base64.decode(sheet.imageBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        Dialog(onDismissRequest = { viewingDrawingSheet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.paperBg)
                    .border(2.dp, tokens.border)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(sheet.title.uppercase(), fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.inkPrimary)
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = sheet.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .border(1.dp, tokens.border)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDownloadSheet(sheet) }) {
                        Text("DOWNLOAD", fontFamily = JetBrainsMonoFontFamily, color = tokens.tagBlue)
                    }
                    TextButton(onClick = { viewingDrawingSheet = null }) {
                        Text("CLOSE", fontFamily = JetBrainsMonoFontFamily, color = tokens.inkPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadAudioSheet(
    uploadProgress: Float?,
    onFilesPicked: (List<Uri>) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesPicked(uris)
        }
    }

    BlueprintBottomSheet(
        title = "LOAD AUDIO",
        subtitle = "mp3 · wav · ogg · m4a — synced to your cloud",
        onDismiss = onDismiss
    ) {
        // Dashed CHOOSE FILES zone
        val borderColor = tokens.border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .background(tokens.cardBg)
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = CornerRadius(0f, 0f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )
                }
                .clickable(enabled = uploadProgress == null) {
                    picker.launch(arrayOf("audio/*"))
                }
                .testTag("choose_audio_files_zone"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = "Choose audio files",
                    tint = tokens.inkPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = if (uploadProgress == null) "CHOOSE FILES" else "SYNCING TO CLOUD...",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                    color = tokens.inkPrimary
                )
                Text(
                    text = "multiple files supported",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = tokens.inkMuted
                )
                if (uploadProgress != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        color = tokens.gold,
                        trackColor = tokens.gridMajor,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                    )
                    Text(
                        text = "${(uploadProgress * 100).toInt()}%",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = tokens.inkPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.cardBg)
                .border(1.dp, tokens.gridMajor)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = tokens.gold, modifier = Modifier.size(20.dp))
            Text(
                text = "Three studio demo pieces are pre-loaded. Audio files you upload are chunked securely to your cloud studio so they persist across sessions and devices.",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = tokens.inkMuted
            )
        }
    }
}

@Composable
fun EditSongInfoSheet(
    track: Track,
    onSave: (title: String, artist: String, album: String, tagsRaw: String, lyrics: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var tagsRaw by remember(track.id) { mutableStateOf(track.tags.joinToString(", ")) }

    BlueprintBottomSheet(
        title = "EDIT SONG INFO",
        subtitle = "Synced per track in your studio",
        onDismiss = onDismiss,
        primaryButtonLabel = "SAVE INFO",
        onPrimaryClick = { onSave(title, artist, album, tagsRaw, track.lyrics) },
        primaryEnabled = title.isNotBlank()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BlueprintInputField(label = "SONG", value = title, onValueChange = { title = it }, placeholder = "Track title")
            BlueprintInputField(label = "ARTIST", value = artist, onValueChange = { artist = it }, placeholder = "Artist or band")
            BlueprintInputField(label = "ALBUM", value = album, onValueChange = { album = it }, placeholder = "Project or album")
            BlueprintInputField(label = "TAGS", value = tagsRaw, onValueChange = { tagsRaw = it }, placeholder = "anthem, march, acoustic")
        }
    }
}

@Composable
private fun BlueprintInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    useHandwritingFont: Boolean = false,
    minHeightDp: Int = 46
) {
    val tokens = LocalBlueprintTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            color = tokens.inkMuted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (singleLine) 46.dp else minHeightDp.dp)
                .background(tokens.cardBg)
                .border(1.5.dp, tokens.border)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    fontFamily = if (useHandwritingFont) GaeguFontFamily else JetBrainsMonoFontFamily,
                    fontSize = if (useHandwritingFont) 19.sp else 14.sp,
                    lineHeight = if (useHandwritingFont) 24.sp else 19.sp,
                    color = tokens.inkPrimary
                ),
                cursorBrush = SolidColor(tokens.gold),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = if (useHandwritingFont) GaeguFontFamily else JetBrainsMonoFontFamily,
                            fontSize = if (useHandwritingFont) 19.sp else 13.sp,
                            color = tokens.inkMuted.copy(alpha = 0.65f)
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
fun PlaylistSheet(
    tracks: List<Track>,
    currentTrackId: String?,
    onSelectTrack: (Track) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onAddAudioClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintBottomSheet(
        title = "PLAYLIST",
        subtitle = "%02d tracks · hold or use arrows to reorder".format(tracks.size),
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.forEachIndexed { index, track ->
                val isCurrent = track.id == currentTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) tokens.gold.copy(alpha = 0.18f) else tokens.cardBg)
                        .border(if (isCurrent) 2.dp else 1.5.dp, if (isCurrent) tokens.gold else tokens.border)
                        .pointerInput(index, tracks.size) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y > 28f && index < tracks.lastIndex) {
                                        onMoveTrack(index, index + 1)
                                    } else if (dragAmount.y < -28f && index > 0) {
                                        onMoveTrack(index, index - 1)
                                    }
                                }
                            )
                        }
                        .clickable { onSelectTrack(track) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Reorder track",
                        tint = tokens.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "%02d".format(index + 1),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isCurrent) tokens.gold else tokens.inkMuted
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = tokens.inkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artist.ifBlank { "Studio" }} · ${formatTimeMs(track.durationMs)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = tokens.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index > 0) {
                        IconButton(onClick = { onMoveTrack(index, index - 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = tokens.inkPrimary, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (index < tracks.lastIndex) {
                        IconButton(onClick = { onMoveTrack(index, index + 1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = tokens.inkPrimary, modifier = Modifier.size(15.dp))
                        }
                    }
                    if (tracks.size > 1) {
                        IconButton(onClick = { onDeleteTrack(track) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete track", tint = tokens.danger, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onAddAudioClick,
                shape = RectangleShape,
                border = BorderStroke(2.dp, tokens.border),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = tokens.goldText),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("playlist_add_audio_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADD AUDIO",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun SettingsSheet(
    settings: UserSettings,
    themeStyle: BlueprintThemeStyle,
    marchingFigure: MarchingFigureStyle,
    notepadBackground: NotepadBackgroundStyle,
    userEmail: String?,
    onSelectThemeStyle: (BlueprintThemeStyle) -> Unit,
    onSelectMarchingFigure: (MarchingFigureStyle) -> Unit,
    onSelectNotepadBackground: (NotepadBackgroundStyle) -> Unit,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    onExportZip: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onClearData: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BlueprintBottomSheet(
        title = "STUDIO SETTINGS",
        subtitle = userEmail ?: "Signed in songwriter",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Blueprint Theme Style selector inside Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BLUEPRINT THEME STYLE",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tokens.inkPrimary
                    )
                    Text(
                        text = themeStyle.shortLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.gold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlueprintThemeStyle.entries.forEach { style ->
                        val previewTokens = tokensForStyle(style)
                        val selected = style == themeStyle
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) tokens.inkPrimary else tokens.paperBg)
                                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.gold else tokens.border)
                                .clickable { onSelectThemeStyle(style) }
                                .padding(vertical = 6.dp, horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(previewTokens.paperBg)
                                        .border(0.8.dp, previewTokens.border)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(previewTokens.transportBg)
                                        .border(0.8.dp, previewTokens.border)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(previewTokens.gold)
                                )
                            }
                            Text(
                                text = style.shortLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Marching Automation Figure selector inside Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MARCHING AUTOMATION",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tokens.inkPrimary
                    )
                    Text(
                        text = marchingFigure.shortLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.gold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MarchingFigureStyle.entries.forEach { fig ->
                        val selected = fig == marchingFigure
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) tokens.inkPrimary else tokens.paperBg)
                                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.gold else tokens.border)
                                .clickable { onSelectMarchingFigure(fig) }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Canvas(modifier = Modifier.size(28.dp)) {
                                drawMarchingFigureArchetype(
                                    figureStyle = fig,
                                    centerX = size.width * 0.45f,
                                    figureY = size.height * 0.62f,
                                    walkPhase = 0.9f,
                                    stride = 0.55f,
                                    isPlaying = true,
                                    reducedMotion = false,
                                    hasFinished = false,
                                    celebrationPulse = 0f,
                                    inkPrimary = if (selected) tokens.cardBg else tokens.inkPrimary,
                                    border = if (selected) tokens.cardBg else tokens.border,
                                    gold = tokens.gold,
                                    tagBlue = tokens.tagBlue
                                )
                            }
                            Text(
                                text = fig.shortLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            SettingToggleRow(
                label = "AUTOSAVE DRAFTS",
                subtitle = "Keep unfinished notepad lines across restarts",
                checked = settings.autosaveEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(autosaveEnabled = v) } }
            )
            SettingToggleRow(
                label = "HAPTIC FEEDBACK",
                subtitle = "Tactile ticks on seek & transport controls",
                checked = settings.hapticsEnabled,
                onCheckedChange = { v -> onUpdateSettings { it.copy(hapticsEnabled = v) } }
            )
            SettingToggleRow(
                label = "REDUCED MOTION",
                subtitle = "Static marching figure & calmer transitions",
                checked = settings.reducedMotion,
                onCheckedChange = { v -> onUpdateSettings { it.copy(reducedMotion = v) } }
            )

            // Default Share Format
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("DEFAULT SHARE FORMAT", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShareFormat.entries.forEach { fmt ->
                        val selected = settings.defaultShareFormat == fmt.name
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(if (selected) tokens.inkPrimary else tokens.paperBg)
                                .border(1.dp, tokens.border)
                                .clickable { onUpdateSettings { it.copy(defaultShareFormat = fmt.name) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${fmt.label} ${fmt.ratioLabel}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary
                            )
                        }
                    }
                }
            }

            // Notepad Paper Quality Selector inside Settings
            NotepadPaperQualitySelector(
                currentBackground = notepadBackground,
                onSelectBackground = onSelectNotepadBackground
            )

            // Audio Recording Quality
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("VOICE RECORDER QUALITY", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudioQuality.entries.forEach { q ->
                        val selected = settings.audioQuality == q.name
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .background(if (selected) tokens.inkPrimary else tokens.paperBg)
                                .border(1.dp, tokens.border)
                                .clickable { onUpdateSettings { it.copy(audioQuality = q.name) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = q.label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) tokens.cardBg else tokens.inkPrimary
                            )
                        }
                    }
                }
            }

            // Utilities
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExportZip,
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("EXPORT ZIP", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.inkPrimary)
                }
                OutlinedButton(
                    onClick = onReplayOnboarding,
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("STUDIO GUIDE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.inkPrimary)
                }
            }

            HorizontalDivider(color = tokens.gridMajor)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.danger),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("RESET DATA", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.danger)
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("SIGN OUT", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.inkPrimary)
                }
            }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("DELETE STUDIO ACCOUNT & CLOUD DATA", fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = tokens.danger)
            }

            Text(
                text = "PRIVACY NOTE: Your tracks, lyrics, drawings, and recordings are private to your signed-in Google account and encrypted in transit via Firebase.",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = tokens.inkMuted
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.cardBg)
                    .border(1.5.dp, tokens.border)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "daudio by Ensis",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = tokens.inkPrimary
                )
                Text(
                    text = "LEGAL NAME: Ensis Vi. Dauid",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = tokens.tealAccent
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = tokens.paperBg,
            title = { Text("RESET STUDIO DATA?", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = tokens.inkPrimary) },
            text = { Text("This deletes all uploaded tracks, notes, drawings, and recordings and restores the 3 demo tracks.", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = tokens.inkPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearData()
                }) {
                    Text("RESET", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = tokens.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("CANCEL", fontFamily = JetBrainsMonoFontFamily, color = tokens.inkMuted)
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = tokens.paperBg,
            title = { Text("DELETE ACCOUNT DATA?", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = tokens.danger) },
            text = { Text("Permanently erases your Firestore cloud data and signs you out.", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = tokens.inkPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteAccount()
                }) {
                    Text("DELETE", fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, color = tokens.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("CANCEL", fontFamily = JetBrainsMonoFontFamily, color = tokens.inkMuted)
                }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.cardBg)
            .border(1.5.dp, tokens.border)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(label, fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.inkPrimary)
            Text(subtitle, fontFamily = JetBrainsMonoFontFamily, fontSize = 10.sp, color = tokens.inkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.goldText,
                checkedTrackColor = tokens.gold,
                uncheckedThumbColor = tokens.inkPrimary,
                uncheckedTrackColor = tokens.paperBg
            )
        )
    }
}

@Composable
private fun NotepadPaperQualitySelector(
    currentBackground: NotepadBackgroundStyle,
    onSelectBackground: (NotepadBackgroundStyle) -> Unit
) {
    val tokens = LocalBlueprintTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.cardBg)
            .border(1.5.dp, tokens.border)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NOTEPAD PAPER QUALITY",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tokens.inkPrimary
            )
            Text(
                text = currentBackground.title,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = tokens.tealAccent
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NotepadBackgroundStyle.entries.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { bgStyle ->
                        val selected = bgStyle == currentBackground
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) tokens.transportBg else tokens.paperBg)
                                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.gold else tokens.border)
                                .clickable { onSelectBackground(bgStyle) }
                                .padding(6.dp)
                                .testTag("notepad_bg_option_${bgStyle.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(tokens.cardBg)
                                    .border(1.dp, tokens.border)
                            ) {
                                drawNotepadPaperPattern(bgStyle, tokens)
                            }
                            Text(
                                text = bgStyle.shortLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = tokens.inkPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = currentBackground.subtitle,
            fontFamily = GaeguFontFamily,
            fontSize = 15.sp,
            color = tokens.inkMuted
        )
    }
}

@Composable
fun ThemeStyleSheet(
    currentStyle: BlueprintThemeStyle,
    currentBackground: NotepadBackgroundStyle,
    onSelectStyle: (BlueprintThemeStyle) -> Unit,
    onSelectBackground: (NotepadBackgroundStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintBottomSheet(
        title = "BLUEPRINT THEMES",
        subtitle = "Switch paper inks & notepad surface quality",
        onDismiss = onDismiss,
        primaryButtonLabel = "DONE",
        onPrimaryClick = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BlueprintThemeStyle.entries.forEach { style ->
                val palette = tokensForStyle(style)
                val selected = style == currentStyle

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) tokens.transportBg else tokens.cardBg)
                        .border(
                            width = if (selected) 2.5.dp else 1.5.dp,
                            color = if (selected) tokens.gold else tokens.border
                        )
                        .clickable { onSelectStyle(style) }
                        .padding(12.dp)
                        .testTag("theme_style_option_${style.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Palette swatch specimen box
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(palette.paperBg)
                            .border(1.5.dp, palette.border)
                            .padding(5.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .background(palette.cardBg)
                                        .border(1.dp, palette.border)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .background(palette.transportBg)
                                        .border(1.dp, palette.border)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .background(palette.libraryBg)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(palette.gold, CircleShape)
                                        .border(1.dp, palette.border, CircleShape)
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = null,
                                tint = palette.gold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = style.title,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.1.sp,
                                color = tokens.inkPrimary
                            )
                        }
                        Text(
                            text = style.subtitle,
                            fontFamily = GaeguFontFamily,
                            fontSize = 16.sp,
                            color = tokens.inkMuted,
                            maxLines = 2
                        )
                    }

                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(tokens.gold)
                                .border(1.5.dp, tokens.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = tokens.goldText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            NotepadPaperQualitySelector(
                currentBackground = currentBackground,
                onSelectBackground = onSelectBackground
            )
        }
    }
}

@Composable
fun MarchingFigureSheet(
    currentFigure: MarchingFigureStyle,
    onSelectFigure: (MarchingFigureStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    BlueprintBottomSheet(
        title = "MARCHING AUTOMATION",
        subtitle = "Auto-applies paired theme & stage animation",
        onDismiss = onDismiss,
        primaryButtonLabel = "DONE",
        onPrimaryClick = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MarchingFigureStyle.entries.forEach { figure ->
                val selected = figure == currentFigure
                val pairedTheme = figure.pairedThemeStyle()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) tokens.transportBg else tokens.cardBg)
                        .border(
                            width = if (selected) 2.5.dp else 1.5.dp,
                            color = if (selected) tokens.gold else tokens.border
                        )
                        .clickable { onSelectFigure(figure) }
                        .padding(12.dp)
                        .testTag("marching_figure_option_${figure.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(tokens.paperBg)
                            .border(1.5.dp, tokens.border),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(44.dp)) {
                            drawMarchingFigureArchetype(
                                figureStyle = figure,
                                centerX = size.width * 0.45f,
                                figureY = size.height * 0.62f,
                                walkPhase = 1.0f,
                                stride = 0.65f,
                                isPlaying = true,
                                reducedMotion = false,
                                hasFinished = false,
                                celebrationPulse = 0f,
                                inkPrimary = tokens.inkPrimary,
                                border = tokens.border,
                                gold = tokens.gold,
                                tagBlue = tokens.tagBlue
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = figure.title,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 1.1.sp,
                                color = tokens.inkPrimary
                            )
                            Text(
                                text = pairedTheme.shortLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = tokens.tealAccent,
                                modifier = Modifier
                                    .border(1.dp, tokens.tealAccent)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = figure.description,
                            fontFamily = GaeguFontFamily,
                            fontSize = 16.sp,
                            color = tokens.inkMuted,
                            maxLines = 2
                        )
                    }

                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(tokens.gold)
                                .border(1.5.dp, tokens.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = tokens.goldText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothSheet(
    btState: BluetoothAudioState,
    onRefresh: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPairDevice: (String) -> Unit,
    onRouteAudio: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalBlueprintTokens.current

    val btPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefresh()
    }

    BlueprintBottomSheet(
        title = "BLUETOOTH AUDIO",
        subtitle = "Wireless speakers, headphones & studio monitors",
        onDismiss = onDismiss,
        primaryButtonLabel = "BT SETTINGS",
        onPrimaryClick = onOpenSystemSettings,
        cancelButtonLabel = "CLOSE"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Active output status card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (btState.isBluetoothAudioActive) tokens.emeraldAccent.copy(alpha = 0.14f)
                        else tokens.cardBg
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (btState.isBluetoothAudioActive) tokens.emeraldAccent else tokens.border
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(if (btState.isBluetoothAudioActive) tokens.emeraldAccent else tokens.paperBg)
                        .border(1.5.dp, tokens.border),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (btState.isBluetoothAudioActive) Icons.Default.BluetoothConnected else Icons.Default.Speaker,
                        contentDescription = "Active audio output",
                        tint = if (btState.isBluetoothAudioActive) Color.White else tokens.inkPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE OUTPUT",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = tokens.inkMuted
                    )
                    Text(
                        text = btState.activeAudioOutputName,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = tokens.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp).testTag("bt_refresh_button")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Bluetooth devices", tint = tokens.inkPrimary, modifier = Modifier.size(18.dp))
                }
            }

            // Permission / Adapter prompt if needed
            if (!btState.isHardwareSupported) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.cardBg)
                        .border(1.5.dp, tokens.coralAccent)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Bluetooth hardware is unavailable on this emulator/device, though audio routing status remains live.",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = tokens.inkPrimary
                    )
                }
            } else if (!btState.hasConnectPermission || !btState.hasScanPermission) {
                Button(
                    onClick = { permissionLauncher.launch(btPermissions) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.tealAccent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("bt_grant_permission_button")
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALLOW BLUETOOTH ACCESS",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp
                    )
                }
            } else if (!btState.isAdapterEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.cardBg)
                        .border(1.5.dp, tokens.gold)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Bluetooth is turned off. Tap BT SETTINGS below to enable Bluetooth and pair studio headphones.",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = tokens.inkPrimary
                    )
                }
            }

            // Route + Scan Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onRouteAudio(!btState.isBluetoothAudioActive) },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("bt_route_toggle_button")
                ) {
                    Text(
                        text = if (btState.isBluetoothAudioActive) "USE SPEAKER" else "ROUTE BT AUDIO",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = tokens.inkPrimary,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = {
                        if (!btState.hasScanPermission || !btState.hasConnectPermission) {
                            permissionLauncher.launch(btPermissions)
                        } else if (btState.isScanning) {
                            onStopScan()
                        } else {
                            onStartScan()
                        }
                    },
                    shape = RectangleShape,
                    border = BorderStroke(1.5.dp, tokens.border),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (btState.isScanning) tokens.coralAccent else tokens.gold,
                        contentColor = if (btState.isScanning) Color.White else tokens.goldText
                    ),
                    modifier = Modifier.weight(1f).height(44.dp).testTag("bt_scan_button")
                ) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (btState.isScanning) "STOP SCAN" else "SCAN NEARBY",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            if (!btState.statusMessage.isNullOrBlank()) {
                Text(
                    text = btState.statusMessage,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = tokens.tealAccent
                )
            }

            // Paired devices
            Text(
                text = "PAIRED DEVICES (${btState.pairedDevices.size})",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = tokens.inkMuted
            )
            if (btState.pairedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.cardBg)
                        .border(1.dp, tokens.gridMajor)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "No paired Bluetooth devices found yet.",
                        fontFamily = GaeguFontFamily,
                        fontSize = 17.sp,
                        color = tokens.inkMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    btState.pairedDevices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (dev.isActiveAudioRoute) tokens.emeraldAccent.copy(alpha = 0.14f) else tokens.cardBg)
                                .border(1.5.dp, if (dev.isActiveAudioRoute) tokens.emeraldAccent else tokens.border)
                                .clickable {
                                    if (dev.isConnectedProfile || dev.isActiveAudioRoute) {
                                        onRouteAudio(true)
                                    } else {
                                        onOpenSystemSettings()
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (dev.isActiveAudioRoute) Icons.Default.BluetoothConnected else Icons.Default.Headphones,
                                contentDescription = dev.name,
                                tint = if (dev.isActiveAudioRoute) tokens.emeraldAccent else tokens.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (dev.isActiveAudioRoute) "ACTIVE AUDIO ROUTE" else if (dev.isConnectedProfile) "CONNECTED · TAP TO ROUTE" else "PAIRED · TAP SETTINGS TO CONNECT",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = if (dev.isActiveAudioRoute) tokens.emeraldAccent else tokens.inkMuted
                                )
                            }
                        }
                    }
                }
            }

            if (btState.discoveredDevices.isNotEmpty()) {
                Text(
                    text = "NEARBY DISCOVERED (${btState.discoveredDevices.size})",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                    color = tokens.tealAccent
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    btState.discoveredDevices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.cardBg)
                                .border(1.5.dp, tokens.tealAccent)
                                .clickable { onPairDevice(dev.address) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = tokens.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dev.address,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 9.sp,
                                    color = tokens.inkMuted
                                )
                            }
                            Text(
                                text = "PAIR",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = tokens.tealAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

