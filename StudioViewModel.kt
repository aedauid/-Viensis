package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DemoAudioSynthesizer
import com.example.audio.PlayerUiState
import com.example.audio.StudioAudioPlayer
import com.example.audio.VoiceRecorderManager
import com.example.bluetooth.BluetoothAudioController
import com.example.bluetooth.BluetoothAudioState
import com.example.data.local.DraftPreferences
import com.example.data.model.AudioQuality
import com.example.data.model.MarchingFigureStyle
import com.example.data.model.NotepadBackgroundStyle
import com.example.data.model.NotepadSheet
import com.example.data.model.ShareFormat
import com.example.data.model.SheetType
import com.example.data.model.Track
import com.example.data.model.UserSettings
import com.example.data.repository.StudioRepository
import com.example.news.NewsUiState
import com.example.news.StageScreenMode
import com.example.news.StudioNewsService
import com.example.ui.components.DrawingStroke
import com.example.ui.components.NotepadMode
import com.example.ui.components.renderStrokesToBase64Png
import com.example.ui.theme.BlueprintThemeStyle
import com.example.ui.theme.pairedThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface ActiveSheet {
    data object None : ActiveSheet
    data object Library : ActiveSheet
    data object LoadAudio : ActiveSheet
    data object EditSongInfo : ActiveSheet
    data object Playlist : ActiveSheet
    data object ShareToSocials : ActiveSheet
    data object Settings : ActiveSheet
    data object ThemeStyle : ActiveSheet
    data object MarchingFigure : ActiveSheet
    data object Bluetooth : ActiveSheet
}

class StudioViewModel(
    private val appContext: Context,
    private val repository: StudioRepository,
    private val draftPreferences: DraftPreferences,
    private val currentUserId: String,
    private val initialDisplayName: String
) : ViewModel() {

    private val _settings = MutableStateFlow(UserSettings(userId = currentUserId, displayName = initialDisplayName))
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _sheets = MutableStateFlow<List<NotepadSheet>>(emptyList())
    val sheets: StateFlow<List<NotepadSheet>> = _sheets.asStateFlow()

    private val _activeSheet = MutableStateFlow<ActiveSheet>(ActiveSheet.None)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    private val _notepadMode = MutableStateFlow(NotepadMode.WRITE)
    val notepadMode: StateFlow<NotepadMode> = _notepadMode.asStateFlow()

    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText.asStateFlow()

    private val _strokes = MutableStateFlow<List<DrawingStroke>>(emptyList())
    val strokes: StateFlow<List<DrawingStroke>> = _strokes.asStateFlow()

    private val _redoStrokes = MutableStateFlow<List<DrawingStroke>>(emptyList())
    val redoCount: StateFlow<Int> = _redoStrokes
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _recordOverTrack = MutableStateFlow(false)
    val recordOverTrack: StateFlow<Boolean> = _recordOverTrack.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _inlinePlayingSheetId = MutableStateFlow<String?>(null)
    val inlinePlayingSheetId: StateFlow<String?> = _inlinePlayingSheetId.asStateFlow()

    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    private val _themeStyle = MutableStateFlow(BlueprintThemeStyle.CREAM_DRAFT)
    val themeStyle: StateFlow<BlueprintThemeStyle> = _themeStyle.asStateFlow()

    private val _marchingFigure = MutableStateFlow(MarchingFigureStyle.FLAG_BEARER)
    val marchingFigure: StateFlow<MarchingFigureStyle> = _marchingFigure.asStateFlow()

    private val _notepadBackground = MutableStateFlow(NotepadBackgroundStyle.SQUARES)
    val notepadBackground: StateFlow<NotepadBackgroundStyle> = _notepadBackground.asStateFlow()

    private val _stageMode = MutableStateFlow(StageScreenMode.ANIM)
    val stageMode: StateFlow<StageScreenMode> = _stageMode.asStateFlow()

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _videoMuted = MutableStateFlow(true)
    val videoMuted: StateFlow<Boolean> = _videoMuted.asStateFlow()

    private val _showFullScreenVideo = MutableStateFlow(false)
    val showFullScreenVideo: StateFlow<Boolean> = _showFullScreenVideo.asStateFlow()

    private val _showGamesPage = MutableStateFlow(false)
    val showGamesPage: StateFlow<Boolean> = _showGamesPage.asStateFlow()

    private var inlineSheetPlayer: MediaPlayer? = null

    val bluetoothController = BluetoothAudioController(appContext)
    val bluetoothState: StateFlow<BluetoothAudioState> = bluetoothController.state

    val newsService = StudioNewsService()
    val newsState: StateFlow<NewsUiState> = newsService.state

    val audioPlayer = StudioAudioPlayer(
        context = appContext,
        repository = repository,
        scope = viewModelScope,
        onNextRequested = { nextTrack(autoPlay = true) },
        onPrevRequested = { previousTrack(autoPlay = true) }
    )
    val playerState: StateFlow<PlayerUiState> = audioPlayer.state

    val voiceRecorder = VoiceRecorderManager(appContext, viewModelScope)

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val savedDraft = withContext(Dispatchers.IO) { draftPreferences.noteDraft.first() }
            val savedTheme = withContext(Dispatchers.IO) { draftPreferences.themeStyle.first() }
            val savedFigure = withContext(Dispatchers.IO) { draftPreferences.marchingFigure.first() }
            val savedBg = withContext(Dispatchers.IO) { draftPreferences.notepadBackground.first() }
            if (savedDraft.isNotBlank()) _noteText.value = savedDraft
            _themeStyle.value = savedTheme
            _marchingFigure.value = savedFigure
            _notepadBackground.value = savedBg

            withContext(Dispatchers.IO) {
                repository.ensureDefaultSettingsAndDemos(initialDisplayName)
            }
            _isBootstrapping.value = false
        }

        viewModelScope.launch {
            newsService.refreshHeadlines()
        }

        viewModelScope.launch {
            repository.observeSettings(currentUserId)
                .catch { e -> Log.w("StudioVM", "Settings stream error: ${e.message}") }
                .collect { s ->
                    if (s != null) {
                        _settings.value = s
                    }
                }
        }

        viewModelScope.launch {
            repository.observeTracks(currentUserId)
                .catch { e -> Log.w("StudioVM", "Tracks stream error: ${e.message}") }
                .collect { list ->
                    _tracks.value = list
                    val curId = playerState.value.currentTrackId
                    val active = list.firstOrNull { it.id == curId } ?: list.firstOrNull()
                    if (active != null && curId != active.id) {
                        audioPlayer.selectTrack(active, autoPlay = false)
                    }
                }
        }

        viewModelScope.launch {
            repository.observeSheets(currentUserId)
                .catch { e -> Log.w("StudioVM", "Sheets stream error: ${e.message}") }
                .collect { list ->
                    _sheets.value = list
                }
        }
    }

    fun currentTrack(): Track? {
        val list = _tracks.value
        if (list.isEmpty()) return null
        return list.firstOrNull { it.id == playerState.value.currentTrackId } ?: list.first()
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.None
    }

    fun setNotepadMode(mode: NotepadMode) {
        _notepadMode.value = mode
    }

    fun onNoteTextChanged(text: String) {
        _noteText.value = text
        if (_settings.value.autosaveEnabled) {
            viewModelScope.launch {
                draftPreferences.saveNoteDraft(text)
            }
        }
    }

    fun saveCurrentNotepad(paperBg: Color, gridColor: Color) {
        when (_notepadMode.value) {
            NotepadMode.WRITE -> saveNoteToLibrary()
            NotepadMode.DRAW -> saveDrawingToLibrary(IntSize(400, 400), paperBg, gridColor)
            NotepadMode.REC -> showToast("Use SAVE inside REC mode after recording a take.")
        }
    }

    fun saveNoteToLibrary() {
        val text = _noteText.value.trim()
        if (text.isEmpty()) {
            showToast("Write something in the notepad first.")
            return
        }
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.take(36) ?: "Studio Note"
        val trackId = currentTrack()?.id ?: ""
        viewModelScope.launch {
            val res = repository.saveSheet(
                type = SheetType.NOTE,
                title = firstLine,
                content = text,
                mimeType = "text/plain",
                trackId = trackId
            )
            if (res.isSuccess) {
                _noteText.value = ""
                draftPreferences.clearDrafts()
                showToast("Saved note to library.")
            } else {
                showToast("Failed to save note: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun addDrawingStroke(stroke: DrawingStroke) {
        _strokes.value = _strokes.value + stroke
        _redoStrokes.value = emptyList()
    }

    fun undoDrawingStroke() {
        val cur = _strokes.value
        if (cur.isEmpty()) return
        val last = cur.last()
        _strokes.value = cur.dropLast(1)
        _redoStrokes.value = _redoStrokes.value + last
    }

    fun redoDrawingStroke() {
        val redo = _redoStrokes.value
        if (redo.isEmpty()) return
        val item = redo.last()
        _redoStrokes.value = redo.dropLast(1)
        _strokes.value = _strokes.value + item
    }

    fun clearDrawing() {
        _strokes.value = emptyList()
        _redoStrokes.value = emptyList()
    }

    fun saveDrawingToLibrary(canvasSize: IntSize, paperBg: Color, gridColor: Color) {
        val cur = _strokes.value
        if (cur.isEmpty()) {
            showToast("Draw a sketch before saving.")
            return
        }
        viewModelScope.launch {
            val base64 = withContext(Dispatchers.Default) {
                renderStrokesToBase64Png(
                    strokes = cur,
                    sourceWidth = canvasSize.width,
                    sourceHeight = canvasSize.height,
                    paperBgColor = paperBg,
                    gridColor = gridColor,
                    backgroundStyle = _notepadBackground.value
                )
            }
            val title = "Sketch ${currentTrack()?.title ?: "Studio"}"
            val res = repository.saveSheet(
                type = SheetType.DRAWING,
                title = title,
                imageBase64 = base64,
                mimeType = "image/png",
                trackId = currentTrack()?.id ?: ""
            )
            if (res.isSuccess) {
                _strokes.value = emptyList()
                _redoStrokes.value = emptyList()
                showToast("Drawing saved as PNG to library.")
            } else {
                showToast("Failed to save drawing: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun toggleRecordOverTrack() {
        _recordOverTrack.value = !_recordOverTrack.value
    }

    fun startRecording() {
        val quality = runCatching { AudioQuality.valueOf(_settings.value.audioQuality) }.getOrDefault(AudioQuality.HIGH)
        if (_recordOverTrack.value && !playerState.value.isPlaying) {
            audioPlayer.togglePlayPause()
        } else if (!_recordOverTrack.value && playerState.value.isPlaying) {
            audioPlayer.pause()
        }
        voiceRecorder.startRecording(quality)
    }

    fun stopRecording() {
        voiceRecorder.stopRecording()
        if (_recordOverTrack.value && playerState.value.isPlaying) {
            audioPlayer.pause()
        }
    }

    fun saveRecordingToLibrary(title: String) {
        val recState = voiceRecorder.state.value
        val file = recState.recordedFile ?: return
        if (!file.exists()) return
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            val res = repository.saveSheet(
                type = SheetType.RECORDING,
                title = title.ifBlank { "Vocal Take" },
                durationMs = recState.elapsedMs,
                mimeType = "audio/mp4",
                trackId = currentTrack()?.id ?: "",
                audioBytes = bytes
            )
            if (res.isSuccess) {
                voiceRecorder.discard()
                showToast("Recording saved to cloud library.")
            } else {
                showToast("Failed to save take: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun playTrack(track: Track) {
        audioPlayer.selectTrack(track, autoPlay = true)
    }

    fun nextTrack(autoPlay: Boolean = playerState.value.isPlaying) {
        val list = _tracks.value
        if (list.isEmpty()) return
        val curIdx = list.indexOfFirst { it.id == playerState.value.currentTrackId }.coerceAtLeast(0)
        val nextIdx = if (playerState.value.shuffleEnabled && list.size > 1) {
            list.indices.filter { it != curIdx }.random()
        } else {
            (curIdx + 1) % list.size
        }
        audioPlayer.selectTrack(list[nextIdx], autoPlay = autoPlay)
    }

    fun previousTrack(autoPlay: Boolean = playerState.value.isPlaying) {
        val list = _tracks.value
        if (list.isEmpty()) return
        if (playerState.value.positionMs > 3000L) {
            audioPlayer.seekTo(0L)
            return
        }
        val curIdx = list.indexOfFirst { it.id == playerState.value.currentTrackId }.coerceAtLeast(0)
        val prevIdx = if (curIdx - 1 < 0) list.lastIndex else curIdx - 1
        audioPlayer.selectTrack(list[prevIdx], autoPlay = autoPlay)
    }

    fun uploadAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uploadProgress.value = 0.02f
            var successCount = 0
            for ((index, uri) in uris.withIndex()) {
                try {
                    val (mimeType, fileName, bytes, durationMs) = withContext(Dispatchers.IO) {
                        val mime = appContext.contentResolver.getType(uri) ?: "audio/mpeg"
                        val name = resolveDisplayName(uri)?.substringBeforeLast(".") ?: "Studio Audio"
                        val data = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val dur = extractDurationMs(uri).coerceAtLeast(1000L)
                        UploadPayload(mime, name, data, dur)
                    }
                    if (bytes == null) continue

                    val orderIdx = (_tracks.value.maxOfOrNull { it.orderIndex } ?: -1L) + 1L + index

                    val res = repository.addTrack(
                        title = fileName,
                        artist = _settings.value.displayName,
                        album = "Studio Uploads",
                        tags = listOf("upload"),
                        lyrics = "",
                        durationMs = durationMs,
                        orderIndex = orderIdx,
                        mimeType = mimeType,
                        audioBytes = bytes,
                        onProgress = { p ->
                            val overall = (index + p) / uris.size.toFloat()
                            _uploadProgress.value = overall.coerceIn(0.05f, 0.98f)
                        }
                    )
                    if (res.isSuccess) {
                        successCount++
                    } else {
                        showToast(res.exceptionOrNull()?.localizedMessage ?: "Upload failed")
                    }
                } catch (e: Exception) {
                    showToast("Could not read file: ${e.localizedMessage}")
                }
            }
            _uploadProgress.value = null
            if (successCount > 0) {
                showToast("Synced $successCount audio file(s) to your studio cloud.")
                closeSheet()
            }
        }
    }

    fun updateTrackInfo(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        tagsRaw: String,
        lyrics: String
    ) {
        val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(10)
        viewModelScope.launch {
            val res = repository.updateTrackInfo(trackId, title, artist, album, tags, lyrics)
            if (res.isSuccess) {
                showToast("Song info updated.")
                closeSheet()
            } else {
                showToast("Failed to save info: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val list = _tracks.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _tracks.value = list
        viewModelScope.launch {
            repository.reorderTracks(list.map { it.id })
        }
    }

    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            val res = repository.deleteTrack(track.id)
            if (res.isSuccess) {
                showToast("Removed ${track.title}")
            } else {
                showToast("Delete failed: ${res.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    fun renameSheet(sheetId: String, newTitle: String) {
        viewModelScope.launch {
            val res = repository.renameSheet(sheetId, newTitle)
            if (res.isSuccess) showToast("Renamed sheet.")
        }
    }

    fun deleteSheet(sheet: NotepadSheet) {
        if (_inlinePlayingSheetId.value == sheet.id) stopInlineSheetPlayback()
        viewModelScope.launch {
            val res = repository.deleteSheet(sheet.id)
            if (res.isSuccess) showToast("Deleted ${sheet.title}.")
        }
    }

    fun openNoteInEditor(sheet: NotepadSheet) {
        _notepadMode.value = NotepadMode.WRITE
        _noteText.value = sheet.content
        closeSheet()
        showToast("Loaded \"${sheet.title}\" into notepad.")
    }

    fun toggleInlineSheetRecording(sheet: NotepadSheet) {
        if (_inlinePlayingSheetId.value == sheet.id) {
            stopInlineSheetPlayback()
            return
        }
        stopInlineSheetPlayback()
        _inlinePlayingSheetId.value = sheet.id
        viewModelScope.launch {
            try {
                val cacheFile = File(appContext.cacheDir, "${sheet.id}.m4a")
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val bytes = repository.downloadMediaChunks(sheet.id).getOrThrow()
                    withContext(Dispatchers.IO) {
                        FileOutputStream(cacheFile).use { it.write(bytes) }
                    }
                }
                val mp = withContext(Dispatchers.IO) {
                    MediaPlayer().apply {
                        setDataSource(cacheFile.absolutePath)
                        prepare()
                    }
                }
                if (_inlinePlayingSheetId.value != sheet.id) {
                    withContext(Dispatchers.IO) {
                        try { mp.release() } catch (_: Exception) {}
                    }
                    return@launch
                }
                mp.setOnCompletionListener {
                    stopInlineSheetPlayback()
                }
                mp.start()
                inlineSheetPlayer = mp
            } catch (e: Exception) {
                _inlinePlayingSheetId.value = null
                showToast("Playback error: ${e.localizedMessage}")
            }
        }
    }

    fun stopInlineSheetPlayback() {
        try { inlineSheetPlayer?.stop() } catch (_: Exception) {}
        try { inlineSheetPlayer?.release() } catch (_: Exception) {}
        inlineSheetPlayer = null
        _inlinePlayingSheetId.value = null
    }

    fun shareSheet(sheet: NotepadSheet) {
        viewModelScope.launch {
            when (sheet.type) {
                SheetType.NOTE -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, sheet.title)
                        putExtra(Intent.EXTRA_TEXT, "${sheet.title}\n\n${sheet.content}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(Intent.createChooser(intent, "Share Note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                SheetType.DRAWING -> {
                    val bytes = Base64.decode(sheet.imageBase64, Base64.NO_WRAP)
                    val file = File(appContext.cacheDir, "${sheet.id}.png")
                    withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                    shareFile(file, "image/png", "Share Sketch")
                }
                SheetType.RECORDING -> {
                    val bytes = repository.downloadMediaChunks(sheet.id).getOrNull() ?: return@launch
                    val file = File(appContext.cacheDir, "${sheet.id}.m4a")
                    withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                    shareFile(file, "audio/mp4", "Share Vocal Take")
                }
            }
        }
    }

    fun downloadSheet(sheet: NotepadSheet) {
        viewModelScope.launch {
            when (sheet.type) {
                SheetType.NOTE -> {
                    val file = File(appContext.cacheDir, "${sheet.title.replace(" ", "_")}.txt")
                    withContext(Dispatchers.IO) { file.writeText("${sheet.title}\n\n${sheet.content}") }
                    saveToDownloads(file, "${sheet.title}.txt", "text/plain")
                }
                SheetType.DRAWING -> {
                    val bytes = Base64.decode(sheet.imageBase64, Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                    saveBitmapToGallery(bmp, "daudio_${sheet.title}")
                }
                SheetType.RECORDING -> {
                    val bytes = repository.downloadMediaChunks(sheet.id).getOrNull() ?: return@launch
                    val file = File(appContext.cacheDir, "${sheet.id}.m4a")
                    withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }
                    saveToDownloads(file, "${sheet.title}.m4a", "audio/mp4")
                }
            }
        }
    }

    fun downloadLocalFile(file: File) {
        viewModelScope.launch {
            saveToDownloads(file, file.name, "audio/mp4")
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        showToast("Copied caption to clipboard.")
    }

    fun saveBitmapToGallery(bitmap: Bitmap, namePrefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val safeName = namePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40) + "_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/daudio")
                    }
                    val uri = appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        appContext.contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    }
                } else {
                    val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "daudio").apply { mkdirs() }
                    FileOutputStream(File(dir, safeName)).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                showToast("Saved image to Pictures/daudio.")
            } catch (e: Exception) {
                showToast("Save failed: ${e.localizedMessage}")
            }
        }
    }

    fun shareBitmap(bitmap: Bitmap, caption: String) {
        viewModelScope.launch {
            val file = File(appContext.cacheDir, "share_card_${System.currentTimeMillis()}.png")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(Intent.createChooser(intent, "Share to Socials").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareTrackAudioClip(track: Track) {
        viewModelScope.launch {
            val file = if (track.isDemo && track.demoKey.isNotBlank()) {
                DemoAudioSynthesizer.getOrCreateDemoFile(appContext, track.demoKey)
            } else {
                val cached = File(appContext.filesDir, "audio_cache/${track.id}.mp3")
                if (cached.exists()) cached else {
                    val bytes = repository.downloadMediaChunks(track.id).getOrNull() ?: return@launch
                    val tmp = File(appContext.cacheDir, "${track.title.replace(" ", "_")}.audio")
                    withContext(Dispatchers.IO) { FileOutputStream(tmp).use { it.write(bytes) } }
                    tmp
                }
            }
            shareFile(file, track.mimeType.ifBlank { "audio/*" }, "Share Audio Clip")
        }
    }

    fun exportAllAsZip() {
        viewModelScope.launch {
            showToast("Preparing studio archive...")
            val zipFile = withContext(Dispatchers.IO) {
                val out = File(appContext.cacheDir, "daudio_by_ensis_export_${System.currentTimeMillis()}.zip")
                ZipOutputStream(FileOutputStream(out)).use { zos ->
                    // 1. Tracks metadata & lyrics
                    _tracks.value.forEach { t ->
                        val entryName = "tracks/${t.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")}.txt"
                        zos.putNextEntry(ZipEntry(entryName))
                        val body = "Title: ${t.title}\nArtist: ${t.artist}\nAlbum: ${t.album}\nTags: ${t.tags.joinToString(", ")}\n\n${t.lyrics}"
                        zos.write(body.toByteArray())
                        zos.closeEntry()
                    }
                    // 2. Sheets
                    _sheets.value.forEach { s ->
                        when (s.type) {
                            SheetType.NOTE -> {
                                zos.putNextEntry(ZipEntry("notes/${s.id}_${s.title.take(20)}.txt"))
                                zos.write("${s.title}\n\n${s.content}".toByteArray())
                                zos.closeEntry()
                            }
                            SheetType.DRAWING -> {
                                if (s.imageBase64.isNotBlank()) {
                                    zos.putNextEntry(ZipEntry("drawings/${s.id}.png"))
                                    zos.write(Base64.decode(s.imageBase64, Base64.NO_WRAP))
                                    zos.closeEntry()
                                }
                            }
                            SheetType.RECORDING -> {
                                val bytes = repository.downloadMediaChunks(s.id).getOrNull()
                                if (bytes != null) {
                                    zos.putNextEntry(ZipEntry("recordings/${s.id}.m4a"))
                                    zos.write(bytes)
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
                out
            }
            shareFile(zipFile, "application/zip", "Export Studio ZIP")
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        viewModelScope.launch {
            repository.updateSettings(updated)
        }
    }

    fun setThemeStyle(style: BlueprintThemeStyle) {
        _themeStyle.value = style
        viewModelScope.launch(Dispatchers.IO) {
            draftPreferences.saveThemeStyle(style)
        }
        updateSettings { it.copy(darkTheme = style.isDarkSurface) }
    }

    fun toggleDayNightTheme() {
        val next = if (_themeStyle.value.isDarkSurface) {
            BlueprintThemeStyle.CREAM_DRAFT
        } else {
            BlueprintThemeStyle.NIGHT_BLUEPRINT
        }
        setThemeStyle(next)
    }

    fun setMarchingFigure(figure: MarchingFigureStyle) {
        _marchingFigure.value = figure
        viewModelScope.launch(Dispatchers.IO) {
            draftPreferences.saveMarchingFigure(figure)
        }
        // Automatically apply matching Blueprint Theme & synced stage animation
        setThemeStyle(figure.pairedThemeStyle())
    }

    fun setNotepadBackground(style: NotepadBackgroundStyle) {
        _notepadBackground.value = style
        viewModelScope.launch(Dispatchers.IO) {
            draftPreferences.saveNotepadBackground(style)
        }
    }

    fun setStageMode(mode: StageScreenMode) {
        _stageMode.value = mode
        if (mode == StageScreenMode.NEWS && newsState.value.headlines.isEmpty() && !newsState.value.isLoading) {
            refreshNews()
        }
    }

    fun setSelectedVideoUri(uri: Uri?) {
        _selectedVideoUri.value = uri
        if (uri != null) {
            _stageMode.value = StageScreenMode.VIDEO
        }
    }

    fun toggleVideoMuted() {
        _videoMuted.value = !_videoMuted.value
    }

    fun openFullScreenVideo() {
        _showFullScreenVideo.value = true
    }

    fun closeFullScreenVideo() {
        _showFullScreenVideo.value = false
    }

    fun refreshNews() {
        viewModelScope.launch {
            newsService.refreshHeadlines()
        }
    }

    fun openGamesPage() {
        _showGamesPage.value = true
    }

    fun closeGamesPage() {
        _showGamesPage.value = false
    }

    fun completeOnboarding() {
        updateSettings { it.copy(onboardingCompleted = true) }
    }

    fun replayOnboarding() {
        closeSheet()
        updateSettings { it.copy(onboardingCompleted = false) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            draftPreferences.clearDrafts()
            _noteText.value = ""
            _strokes.value = emptyList()
            val res = repository.clearAllUserData()
            if (res.isSuccess) {
                showToast("Studio reset to factory demo tracks.")
                closeSheet()
            }
        }
    }

    fun deleteAccountData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            draftPreferences.clearDrafts()
            repository.deleteAccountData()
            onDeleted()
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun shareFile(file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun saveToDownloads(file: File, displayName: String, mime: String) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/daudio")
                    }
                    val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        appContext.contentResolver.openOutputStream(uri)?.use { out ->
                            file.inputStream().use { it.copyTo(out) }
                        }
                        showToast("Downloaded to Downloads/daudio/$displayName")
                        return@withContext
                    }
                }
                shareFile(file, mime, "Save $displayName")
            } catch (e: Exception) {
                showToast("Download failed: ${e.localizedMessage}")
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun extractDurationMs(uri: Uri): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(appContext, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15000L
        } catch (_: Exception) {
            15000L
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        voiceRecorder.release()
        bluetoothController.release()
        stopInlineSheetPlayback()
    }

    private data class UploadPayload(
        val mimeType: String,
        val fileName: String,
        val bytes: ByteArray?,
        val durationMs: Long
    )
}
