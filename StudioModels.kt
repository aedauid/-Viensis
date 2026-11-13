package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ShareFormat(val label: String, val ratioLabel: String, val width: Int, val height: Int) {
    STORY_9_16("STORY", "9:16", 1080, 1920),
    SQUARE_1_1("SQUARE", "1:1", 1080, 1080),
    POST_4_5("POST", "4:5", 1080, 1350)
}

enum class AudioQuality(val label: String, val bitRate: Int, val sampleRate: Int) {
    STANDARD("STANDARD (64 KBPS)", 64000, 22050),
    HIGH("HIGH (128 KBPS)", 128000, 44100)
}

enum class MarchingFigureStyle(
    val title: String,
    val shortLabel: String,
    val description: String
) {
    FLAG_BEARER("FLAG BEARER", "FLAG", "Classic pioneer carrying the gold pennant"),
    DANCER("PULSE DANCER", "DANCE", "Grooving figure radiating beat pulse rings"),
    SOLDIER("PARADE SOLDIER", "SOLDIER", "Plumed shako cap marching in crisp cadence"),
    DRUMMER("BEAT DRUMMER", "DRUM", "Marching snare player tapping alternating sticks"),
    CONDUCTOR("MAESTRO", "MAESTRO", "Leading tempo arcs with a sweeping gold baton")
}

enum class NotepadBackgroundStyle(
    val title: String,
    val shortLabel: String,
    val subtitle: String
) {
    SQUARES("SQUARES", "GRID", "Classic square drafting grid"),
    LINES("LINED PAPER", "LINES", "Ruled writing lines & margin"),
    EMPTY("EMPTY VELLUM", "EMPTY", "Clean blank surface, zero lines"),
    GUTENBERG_PRESS("GUTENBERG PRESS", "1455", "Laid press parchment & rubric frame"),
    DOT_MATRIX("DOT MATRIX", "DOTS", "Architectural dot matrix"),
    MUSIC_STAFF("MUSIC STAFF", "STAFF", "5-line manuscript staves")
}

object SheetType {
    const val NOTE = "note"
    const val DRAWING = "drawing"
    const val RECORDING = "recording"
}

@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Songwriter",
    val darkTheme: Boolean = false,
    val defaultShareFormat: String = ShareFormat.STORY_9_16.name,
    val autosaveEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val autoScrollLyrics: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val audioQuality: String = AudioQuality.HIGH.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Track(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val tags: List<String> = emptyList(),
    val lyrics: String = "",
    val durationMs: Long = 0L,
    val orderIndex: Long = 0L,
    val isDemo: Boolean = false,
    val demoKey: String = "",
    val mimeType: String = "audio/wav",
    val chunkCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotepadSheet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = SheetType.NOTE,
    val title: String = "",
    val content: String = "",
    val imageBase64: String = "",
    val durationMs: Long = 0L,
    val mimeType: String = "text/plain",
    val chunkCount: Long = 0L,
    val trackId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MediaChunk(
    @DocumentId val id: String = "",
    val userId: String = "",
    val parentId: String = "",
    val parentType: String = "track",
    val chunkIndex: Long = 0L,
    val totalChunks: Long = 1L,
    val mimeType: String = "audio/mpeg",
    val dataBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
