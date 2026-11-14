package com.example.data.repository

import android.content.Context
import com.example.data.model.AudioQuality
import com.example.data.model.NotepadSheet
import com.example.data.model.ShareFormat
import com.example.data.model.Track
import com.example.data.model.UserSettings
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID

/**
 * Local-only data layer for daudio.
 *
 * Everything (settings, tracks, notes, drawings, recordings, audio files) is stored in the
 * app's private storage on this phone. No Firebase, no cloud, no billing.
 *
 * The public functions keep the same names and signatures as the old Firestore version,
 * so the ViewModel and the audio player keep working.
 *
 * Layout inside filesDir:
 *   studio/settings.json
 *   studio/tracks/<id>.json
 *   studio/sheets/<id>.json
 *   studio/media/<sheetId>.bin      (voice recordings)
 *   audio_cache/<trackId>.mp3       (imported audio, same path the player already checks)
 */
class StudioRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "studio").apply { mkdirs() }
    private val tracksDir = File(root, "tracks").apply { mkdirs() }
    private val sheetsDir = File(root, "sheets").apply { mkdirs() }
    private val mediaDir = File(root, "media").apply { mkdirs() }
    private val audioDir = File(appContext.filesDir, "audio_cache").apply { mkdirs() }
    private val settingsFile = File(root, "settings.json")

    private val lock = Mutex()
    private val tracksFlow = MutableStateFlow<List<Track>>(emptyList())
    private val sheetsFlow = MutableStateFlow<List<NotepadSheet>>(emptyList())
    private val settingsFlow = MutableStateFlow<UserSettings?>(null)

    @Volatile
    private var loaded = false

    // ---------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------

    private suspend fun ensureLoaded() {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) { reloadAll() }
            loaded = true
        }
    }

    private fun reloadAll() {
        tracksFlow.value = (tracksDir.listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { trackFromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedBy { it.orderIndex }

        sheetsFlow.value = (sheetsDir.listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .mapNotNull { f -> runCatching { sheetFromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }

        settingsFlow.value = if (settingsFile.exists()) {
            runCatching { settingsFromJson(JSONObject(settingsFile.readText())) }.getOrNull()
        } else {
            null
        }
    }

    private suspend fun <T> mutate(block: () -> T): Result<T> {
        return try {
            ensureLoaded()
            val value = lock.withLock { withContext(Dispatchers.IO) { block() } }
            Result.success(value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------------------------------

    @Suppress("UNUSED_PARAMETER")
    fun observeSettings(userId: String): Flow<UserSettings?> = flow {
        ensureLoaded()
        emitAll(settingsFlow)
    }

    @Suppress("UNUSED_PARAMETER")
    fun observeTracks(userId: String): Flow<List<Track>> = flow {
        ensureLoaded()
        emitAll(tracksFlow)
    }

    @Suppress("UNUSED_PARAMETER")
    fun observeSheets(userId: String): Flow<List<NotepadSheet>> = flow {
        ensureLoaded()
        emitAll(sheetsFlow)
    }

    // ---------------------------------------------------------------------------------------
    // Settings and demo content
    // ---------------------------------------------------------------------------------------

    suspend fun ensureDefaultSettingsAndDemos(displayName: String): Result<Unit> = mutate {
        if (settingsFlow.value == null) {
            writeSettings(
                UserSettings(
                    userId = LOCAL_USER_ID,
                    displayName = displayName.take(100).ifBlank { "Songwriter" },
                    darkTheme = false,
                    defaultShareFormat = ShareFormat.STORY_9_16.name,
                    autosaveEnabled = true,
                    hapticsEnabled = true,
                    reducedMotion = false,
                    autoScrollLyrics = true,
                    onboardingCompleted = false,
                    audioQuality = AudioQuality.HIGH.name
                )
            )
        }
        if (tracksFlow.value.isEmpty()) {
            seedDemosLocked()
        }
        Unit
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun seedDemoTracks(uid: String = LOCAL_USER_ID) {
        mutate { seedDemosLocked() }
    }

    suspend fun updateSettings(settings: UserSettings): Result<Unit> = mutate {
        val old = settingsFlow.value
        writeSettings(
            settings.copy(
                userId = old?.userId ?: settings.userId.ifBlank { LOCAL_USER_ID },
                displayName = settings.displayName.take(100)
            )
        )
        Unit
    }

    private fun seedDemosLocked() {
        val demos = listOf(
            DemoSeed(
                id = "demo_ensis_vi",
                title = "daudio",
                artist = "Ensis",
                album = "Blueprint One",
                tags = listOf("anthem", "march"),
                lyrics = "daudio by Ensis\nThe road is clear\nMarching on\nWith the blueprint near\n\nNorth by the rule\nGold on the line\nEvery step measured\nIn four-four time",
                durationMs = 26000L,
                orderIndex = 0L,
                demoKey = "ensis_vi"
            ),
            DemoSeed(
                id = "demo_drafting_room",
                title = "Drafting Room",
                artist = "Royal Studies",
                album = "Blueprint One",
                tags = listOf("sketch", "acoustic"),
                lyrics = "Paper grid at dawn\nCompass and the pen\nTracing out a chorus\nTill it breathes again\n\nLines in Prussian blue\nDrafting in the dark\nWaiting for the melody\nTo leave a golden mark",
                durationMs = 32000L,
                orderIndex = 1L,
                demoKey = "drafting_room"
            ),
            DemoSeed(
                id = "demo_prussian_gold",
                title = "Prussian Gold",
                artist = "Ensis Vi. Dauid",
                album = "Night Blueprint",
                tags = listOf("ambient", "studio"),
                lyrics = "Midnight on the desk\nInk upon my hands\nHumming low beneath the lamp\nDrawing up the plans\n\nHold the steady beat\nKeep the chorus clean\nEverything we need to say\nLives between the lines",
                durationMs = 28000L,
                orderIndex = 2L,
                demoKey = "prussian_gold"
            )
        )
        val now = Timestamp.now()
        for (d in demos) {
            putTrack(
                Track(
                    id = d.id,
                    userId = LOCAL_USER_ID,
                    title = d.title,
                    artist = d.artist,
                    album = d.album,
                    tags = d.tags,
                    lyrics = d.lyrics,
                    durationMs = d.durationMs,
                    orderIndex = d.orderIndex,
                    isDemo = true,
                    demoKey = d.demoKey,
                    mimeType = "audio/wav",
                    chunkCount = 0L,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private data class DemoSeed(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val tags: List<String>,
        val lyrics: String,
        val durationMs: Long,
        val orderIndex: Long,
        val demoKey: String
    )

    // ---------------------------------------------------------------------------------------
    // Tracks
    // ---------------------------------------------------------------------------------------

    suspend fun addTrack(
        title: String,
        artist: String,
        album: String,
        tags: List<String>,
        lyrics: String,
        durationMs: Long,
        orderIndex: Long,
        mimeType: String,
        audioBytes: ByteArray?,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = mutate {
        val trackId = newId("trk")
        var hasAudio = false
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            onProgress(0.3f)
            // Same path the audio player already looks at, so imported tracks play immediately.
            writeAtomic(audioFileFor(trackId, mimeType), audioBytes)
            hasAudio = true
        }
        val now = Timestamp.now()
        putTrack(
            Track(
                id = trackId,
                userId = LOCAL_USER_ID,
                title = title.trim().ifBlank { "Untitled Track" }.take(140),
                artist = artist.trim().take(140),
                album = album.trim().take(140),
                tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
                lyrics = lyrics.take(20000),
                durationMs = durationMs.coerceIn(0L, 36000000L),
                orderIndex = orderIndex.coerceIn(0L, 10000L),
                isDemo = false,
                demoKey = "",
                mimeType = mimeType.take(64),
                chunkCount = if (hasAudio) 1L else 0L,
                createdAt = now,
                updatedAt = now
            )
        )
        onProgress(1f)
        trackId
    }

    suspend fun updateTrackInfo(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        tags: List<String>,
        lyrics: String
    ): Result<Unit> = mutate {
        val old = tracksFlow.value.firstOrNull { it.id == trackId }
            ?: throw IllegalStateException("Track not found.")
        putTrack(
            old.copy(
                title = title.trim().ifBlank { "Untitled" }.take(140),
                artist = artist.trim().take(140),
                album = album.trim().take(140),
                tags = tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
                lyrics = lyrics.take(20000),
                updatedAt = Timestamp.now()
            )
        )
        Unit
    }

    suspend fun reorderTracks(orderedTrackIds: List<String>): Result<Unit> = mutate {
        val byId = tracksFlow.value.associateBy { it.id }
        orderedTrackIds.forEachIndexed { index, id ->
            val t = byId[id] ?: return@forEachIndexed
            putTrack(t.copy(orderIndex = index.toLong(), updatedAt = Timestamp.now()))
        }
        Unit
    }

    suspend fun deleteTrack(trackId: String): Result<Unit> = mutate {
        File(tracksDir, "$trackId.json").delete()
        audioCandidates(trackId).forEach { it.delete() }
        tracksFlow.value = tracksFlow.value.filterNot { it.id == trackId }
        Unit
    }

    // ---------------------------------------------------------------------------------------
    // Sheets (notes, drawings, recordings)
    // ---------------------------------------------------------------------------------------

    suspend fun saveSheet(
        type: String,
        title: String,
        content: String = "",
        imageBase64: String = "",
        durationMs: Long = 0L,
        mimeType: String = "text/plain",
        trackId: String = "",
        audioBytes: ByteArray? = null
    ): Result<String> = mutate {
        val sheetId = newId("sht")
        var hasAudio = false
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            writeAtomic(File(mediaDir, "$sheetId.bin"), audioBytes)
            hasAudio = true
        }
        val now = Timestamp.now()
        putSheet(
            NotepadSheet(
                id = sheetId,
                userId = LOCAL_USER_ID,
                type = type,
                title = title.trim().ifBlank { "Untitled Sheet" }.take(140),
                content = content.take(20000),
                imageBase64 = imageBase64,
                durationMs = durationMs.coerceIn(0L, 36000000L),
                mimeType = mimeType.take(64),
                chunkCount = if (hasAudio) 1L else 0L,
                trackId = trackId.take(128),
                createdAt = now,
                updatedAt = now
            )
        )
        sheetId
    }

    suspend fun renameSheet(sheetId: String, newTitle: String): Result<Unit> = mutate {
        val old = sheetsFlow.value.firstOrNull { it.id == sheetId }
            ?: throw IllegalStateException("Sheet not found.")
        putSheet(
            old.copy(
                title = newTitle.trim().ifBlank { "Untitled Sheet" }.take(140),
                updatedAt = Timestamp.now()
            )
        )
        Unit
    }

    suspend fun deleteSheet(sheetId: String): Result<Unit> = mutate {
        File(sheetsDir, "$sheetId.json").delete()
        File(mediaDir, "$sheetId.bin").delete()
        sheetsFlow.value = sheetsFlow.value.filterNot { it.id == sheetId }
        Unit
    }

    // ---------------------------------------------------------------------------------------
    // Media access (kept under the old name so existing callers still work)
    // ---------------------------------------------------------------------------------------

    suspend fun downloadMediaChunks(parentId: String): Result<ByteArray> {
        return try {
            val bytes = withContext(Dispatchers.IO) {
                val audio = audioCandidates(parentId).firstOrNull { it.exists() && it.length() > 0 }
                val media = File(mediaDir, "$parentId.bin")
                when {
                    audio != null -> audio.readBytes()
                    media.exists() -> media.readBytes()
                    else -> throw IllegalStateException("Audio file was not found on this phone.")
                }
            }
            Result.success(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reset / delete
    // ---------------------------------------------------------------------------------------

    suspend fun clearAllUserData(): Result<Unit> = mutate {
        wipeContent()
        seedDemosLocked()
        Unit
    }

    suspend fun deleteAccountData(): Result<Unit> = mutate {
        wipeContent()
        settingsFile.delete()
        settingsFlow.value = null
        Unit
    }

    private fun wipeContent() {
        tracksDir.listFiles()?.forEach { it.delete() }
        sheetsDir.listFiles()?.forEach { it.delete() }
        mediaDir.listFiles()?.forEach { it.delete() }
        audioDir.listFiles()?.forEach { it.delete() }
        tracksFlow.value = emptyList()
        sheetsFlow.value = emptyList()
    }

    // ---------------------------------------------------------------------------------------
    // Disk helpers
    // ---------------------------------------------------------------------------------------

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    /** Same extension rule as StudioAudioPlayer.resolveAudioFile, so the player finds the file. */
    private fun audioExtFor(mimeType: String): String = when {
        mimeType.contains("wav") -> "wav"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        else -> "mp3"
    }

    private fun audioFileFor(trackId: String, mimeType: String): File =
        File(audioDir, "$trackId.${audioExtFor(mimeType)}")

    private fun audioCandidates(trackId: String): List<File> =
        listOf("mp3", "wav", "ogg", "m4a").map { File(audioDir, "$trackId.$it") }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun writeSettings(s: UserSettings) {
        writeAtomic(settingsFile, settingsToJson(s).toString().toByteArray())
        settingsFlow.value = s
    }

    private fun putTrack(t: Track) {
        writeAtomic(File(tracksDir, "${t.id}.json"), trackToJson(t).toString().toByteArray())
        tracksFlow.value = (tracksFlow.value.filterNot { it.id == t.id } + t)
            .sortedBy { it.orderIndex }
    }

    private fun putSheet(s: NotepadSheet) {
        writeAtomic(File(sheetsDir, "${s.id}.json"), sheetToJson(s).toString().toByteArray())
        sheetsFlow.value = (sheetsFlow.value.filterNot { it.id == s.id } + s)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    // ---------------------------------------------------------------------------------------
    // JSON mapping
    // ---------------------------------------------------------------------------------------

    private fun tsOf(ms: Long): Timestamp? = if (ms > 0L) Timestamp(Date(ms)) else null

    private fun trackToJson(t: Track): JSONObject {
        val o = JSONObject()
        o.put("id", t.id)
        o.put("userId", t.userId)
        o.put("title", t.title)
        o.put("artist", t.artist)
        o.put("album", t.album)
        o.put("tags", JSONArray(t.tags))
        o.put("lyrics", t.lyrics)
        o.put("durationMs", t.durationMs)
        o.put("orderIndex", t.orderIndex)
        o.put("isDemo", t.isDemo)
        o.put("demoKey", t.demoKey)
        o.put("mimeType", t.mimeType)
        o.put("chunkCount", t.chunkCount)
        o.put("createdAt", t.createdAt?.toDate()?.time ?: 0L)
        o.put("updatedAt", t.updatedAt?.toDate()?.time ?: 0L)
        return o
    }

    private fun trackFromJson(o: JSONObject): Track {
        val tagsArr = o.optJSONArray("tags")
        val tags = ArrayList<String>()
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
        }
        return Track(
            id = o.getString("id"),
            userId = o.optString("userId", LOCAL_USER_ID),
            title = o.optString("title"),
            artist = o.optString("artist"),
            album = o.optString("album"),
            tags = tags,
            lyrics = o.optString("lyrics"),
            durationMs = o.optLong("durationMs"),
            orderIndex = o.optLong("orderIndex"),
            isDemo = o.optBoolean("isDemo"),
            demoKey = o.optString("demoKey"),
            mimeType = o.optString("mimeType", "audio/wav"),
            chunkCount = o.optLong("chunkCount"),
            createdAt = tsOf(o.optLong("createdAt")),
            updatedAt = tsOf(o.optLong("updatedAt"))
        )
    }

    private fun sheetToJson(s: NotepadSheet): JSONObject {
        val o = JSONObject()
        o.put("id", s.id)
        o.put("userId", s.userId)
        o.put("type", s.type)
        o.put("title", s.title)
        o.put("content", s.content)
        o.put("imageBase64", s.imageBase64)
        o.put("durationMs", s.durationMs)
        o.put("mimeType", s.mimeType)
        o.put("chunkCount", s.chunkCount)
        o.put("trackId", s.trackId)
        o.put("createdAt", s.createdAt?.toDate()?.time ?: 0L)
        o.put("updatedAt", s.updatedAt?.toDate()?.time ?: 0L)
        return o
    }

    private fun sheetFromJson(o: JSONObject): NotepadSheet {
        return NotepadSheet(
            id = o.getString("id"),
            userId = o.optString("userId", LOCAL_USER_ID),
            type = o.optString("type", "note"),
            title = o.optString("title"),
            content = o.optString("content"),
            imageBase64 = o.optString("imageBase64"),
            durationMs = o.optLong("durationMs"),
            mimeType = o.optString("mimeType", "text/plain"),
            chunkCount = o.optLong("chunkCount"),
            trackId = o.optString("trackId"),
            createdAt = tsOf(o.optLong("createdAt")),
            updatedAt = tsOf(o.optLong("updatedAt"))
        )
    }

    private fun settingsToJson(s: UserSettings): JSONObject {
        val o = JSONObject()
        o.put("userId", s.userId)
        o.put("displayName", s.displayName)
        o.put("darkTheme", s.darkTheme)
        o.put("defaultShareFormat", s.defaultShareFormat)
        o.put("autosaveEnabled", s.autosaveEnabled)
        o.put("hapticsEnabled", s.hapticsEnabled)
        o.put("reducedMotion", s.reducedMotion)
        o.put("autoScrollLyrics", s.autoScrollLyrics)
        o.put("onboardingCompleted", s.onboardingCompleted)
        o.put("audioQuality", s.audioQuality)
        return o
    }

    private fun settingsFromJson(o: JSONObject): UserSettings {
        return UserSettings(
            userId = o.optString("userId", LOCAL_USER_ID),
            displayName = o.optString("displayName", "Songwriter"),
            darkTheme = o.optBoolean("darkTheme", false),
            defaultShareFormat = o.optString("defaultShareFormat", ShareFormat.STORY_9_16.name),
            autosaveEnabled = o.optBoolean("autosaveEnabled", true),
            hapticsEnabled = o.optBoolean("hapticsEnabled", true),
            reducedMotion = o.optBoolean("reducedMotion", false),
            autoScrollLyrics = o.optBoolean("autoScrollLyrics", true),
            onboardingCompleted = o.optBoolean("onboardingCompleted", false),
            audioQuality = o.optString("audioQuality", AudioQuality.HIGH.name)
        )
    }

    companion object {
        /** Fixed id used for the single local user on this phone. */
        const val LOCAL_USER_ID = "local_user"
    }
}
