package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.data.model.AudioQuality
import com.example.data.model.MediaChunk
import com.example.data.model.NotepadSheet
import com.example.data.model.ShareFormat
import com.example.data.model.Track
import com.example.data.model.UserSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSettings(userId: String): Flow<UserSettings?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAllWithErrorLogging(
            docRef.snapshots().map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            },
            OperationType.GET,
            docRef.path,
            userId
        )
    }

    fun observeTracks(userId: String): Flow<List<Track>> = flow {
        val colRef = db.collection("users").document(userId).collection("tracks")
        emitAllWithErrorLogging(
            colRef.snapshots().map { snapshot ->
                snapshot.toObjects(Track::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.orderIndex }
            },
            OperationType.LIST,
            colRef.path,
            userId
        )
    }

    fun observeSheets(userId: String): Flow<List<NotepadSheet>> = flow {
        val colRef = db.collection("users").document(userId).collection("sheets")
        emitAllWithErrorLogging(
            colRef.snapshots().map { snapshot ->
                snapshot.toObjects(NotepadSheet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            },
            OperationType.LIST,
            colRef.path,
            userId
        )
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAllWithErrorLogging(
        upstream: Flow<T>,
        opType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val caught = upstream.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(exceptionToLog, opType, path, auth)
            }
            throw error
        }
        caught.collect { emit(it) }
    }

    suspend fun ensureDefaultSettingsAndDemos(displayName: String): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            val userRef = db.collection("users").document(uid)
            val userSnap = try {
                userRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, userRef.path, auth)
                throw e
            }

            if (!userSnap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName.take(100).ifBlank { "Songwriter" },
                    "darkTheme" to false,
                    "defaultShareFormat" to ShareFormat.STORY_9_16.name,
                    "autosaveEnabled" to true,
                    "hapticsEnabled" to true,
                    "reducedMotion" to false,
                    "autoScrollLyrics" to true,
                    "onboardingCompleted" to false,
                    "audioQuality" to AudioQuality.HIGH.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                try {
                    userRef.set(payload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, userRef.path, auth)
                    throw e
                }
            }

            val tracksRef = userRef.collection("tracks")
            val existingTracks = try {
                tracksRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, tracksRef.path, auth)
                throw e
            }

            if (existingTracks.isEmpty) {
                seedDemoTracks(uid)
            }
        }
    }

    suspend fun seedDemoTracks(uid: String = requireUserId()) {
        val demos = listOf(
            mapOf(
                "id" to "demo_ensis_vi",
                "title" to "daudio",
                "artist" to "Ensis",
                "album" to "Blueprint One",
                "tags" to listOf("anthem", "march"),
                "lyrics" to "daudio by Ensis\nThe road is clear\nMarching on\nWith the blueprint near\n\nNorth by the rule\nGold on the line\nEvery step measured\nIn four-four time",
                "durationMs" to 26000L,
                "orderIndex" to 0L,
                "demoKey" to "ensis_vi"
            ),
            mapOf(
                "id" to "demo_drafting_room",
                "title" to "Drafting Room",
                "artist" to "Royal Studies",
                "album" to "Blueprint One",
                "tags" to listOf("sketch", "acoustic"),
                "lyrics" to "Paper grid at dawn\nCompass and the pen\nTracing out a chorus\nTill it breathes again\n\nLines in Prussian blue\nDrafting in the dark\nWaiting for the melody\nTo leave a golden mark",
                "durationMs" to 32000L,
                "orderIndex" to 1L,
                "demoKey" to "drafting_room"
            ),
            mapOf(
                "id" to "demo_prussian_gold",
                "title" to "Prussian Gold",
                "artist" to "Ensis Vi. Dauid",
                "album" to "Night Blueprint",
                "tags" to listOf("ambient", "studio"),
                "lyrics" to "Midnight on the desk\nInk upon my hands\nHumming low beneath the lamp\nDrawing up the plans\n\nHold the steady beat\nKeep the chorus clean\nEverything we need to say\nLives between the lines",
                "durationMs" to 28000L,
                "orderIndex" to 2L,
                "demoKey" to "prussian_gold"
            )
        )
        for (demo in demos) {
            val id = demo["id"] as String
            val docRef = db.collection("users").document(uid).collection("tracks").document(id)
            val payload = mapOf(
                "userId" to uid,
                "title" to demo["title"]!!,
                "artist" to demo["artist"]!!,
                "album" to demo["album"]!!,
                "tags" to demo["tags"]!!,
                "lyrics" to demo["lyrics"]!!,
                "durationMs" to demo["durationMs"]!!,
                "orderIndex" to demo["orderIndex"]!!,
                "isDemo" to true,
                "demoKey" to demo["demoKey"]!!,
                "mimeType" to "audio/wav",
                "chunkCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
                throw e
            }
        }
    }

    suspend fun updateSettings(settings: UserSettings): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return runCatching {
            val updates = mapOf(
                "displayName" to settings.displayName.take(100),
                "darkTheme" to settings.darkTheme,
                "defaultShareFormat" to settings.defaultShareFormat,
                "autosaveEnabled" to settings.autosaveEnabled,
                "hapticsEnabled" to settings.hapticsEnabled,
                "reducedMotion" to settings.reducedMotion,
                "autoScrollLyrics" to settings.autoScrollLyrics,
                "onboardingCompleted" to settings.onboardingCompleted,
                "audioQuality" to settings.audioQuality,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                throw e
            }
        }
    }

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
    ): Result<String> {
        val uid = requireUserId()
        val trackId = "trk_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return runCatching {
            val chunks = if (audioBytes != null && audioBytes.isNotEmpty()) {
                splitIntoChunks(audioBytes)
            } else {
                emptyList()
            }

            if (chunks.size > 60) {
                throw IllegalArgumentException("Audio file exceeds the 27 MB cloud sync limit.")
            }

            chunks.forEachIndexed { idx, base64Chunk ->
                val chunkId = "${trackId}_c_$idx"
                val chunkRef = db.collection("users").document(uid).collection("mediaChunks").document(chunkId)
                val chunkPayload = mapOf(
                    "userId" to uid,
                    "parentId" to trackId,
                    "parentType" to "track",
                    "chunkIndex" to idx.toLong(),
                    "totalChunks" to chunks.size.toLong(),
                    "mimeType" to mimeType.take(64),
                    "dataBase64" to base64Chunk,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                try {
                    chunkRef.set(chunkPayload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, chunkRef.path, auth)
                    throw e
                }
                onProgress((idx + 1).toFloat() / (chunks.size + 1).toFloat())
            }

            val trackRef = db.collection("users").document(uid).collection("tracks").document(trackId)
            val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10)
            val trackPayload = mapOf(
                "userId" to uid,
                "title" to title.trim().ifBlank { "Untitled Track" }.take(140),
                "artist" to artist.trim().take(140),
                "album" to album.trim().take(140),
                "tags" to cleanTags,
                "lyrics" to lyrics.take(20000),
                "durationMs" to durationMs.coerceIn(0L, 36000000L),
                "orderIndex" to orderIndex.coerceIn(0L, 10000L),
                "isDemo" to false,
                "demoKey" to "",
                "mimeType" to mimeType.take(64),
                "chunkCount" to chunks.size.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                trackRef.set(trackPayload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, trackRef.path, auth)
                throw e
            }
            onProgress(1f)
            trackId
        }
    }

    suspend fun updateTrackInfo(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        tags: List<String>,
        lyrics: String
    ): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("tracks").document(trackId)
        return runCatching {
            val updates = mapOf(
                "title" to title.trim().ifBlank { "Untitled" }.take(140),
                "artist" to artist.trim().take(140),
                "album" to album.trim().take(140),
                "tags" to tags.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
                "lyrics" to lyrics.take(20000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                throw e
            }
        }
    }

    suspend fun reorderTracks(orderedTrackIds: List<String>): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            val batch = db.batch()
            orderedTrackIds.forEachIndexed { index, trackId ->
                val ref = db.collection("users").document(uid).collection("tracks").document(trackId)
                batch.update(ref, mapOf("orderIndex" to index.toLong(), "updatedAt" to FieldValue.serverTimestamp()))
            }
            try {
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "users/$uid/tracks", auth)
                throw e
            }
        }
    }

    suspend fun deleteTrack(trackId: String): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            deleteChunksForParent(uid, trackId)
            val docRef = db.collection("users").document(uid).collection("tracks").document(trackId)
            try {
                docRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
                throw e
            }
        }
    }

    suspend fun saveSheet(
        type: String,
        title: String,
        content: String = "",
        imageBase64: String = "",
        durationMs: Long = 0L,
        mimeType: String = "text/plain",
        trackId: String = "",
        audioBytes: ByteArray? = null
    ): Result<String> {
        val uid = requireUserId()
        val sheetId = "sht_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return runCatching {
            val chunks = if (audioBytes != null && audioBytes.isNotEmpty()) {
                splitIntoChunks(audioBytes)
            } else {
                emptyList()
            }
            if (chunks.size > 60) {
                throw IllegalArgumentException("Recording exceeds cloud chunk limit.")
            }

            chunks.forEachIndexed { idx, base64Chunk ->
                val chunkId = "${sheetId}_c_$idx"
                val chunkRef = db.collection("users").document(uid).collection("mediaChunks").document(chunkId)
                val chunkPayload = mapOf(
                    "userId" to uid,
                    "parentId" to sheetId,
                    "parentType" to "recording",
                    "chunkIndex" to idx.toLong(),
                    "totalChunks" to chunks.size.toLong(),
                    "mimeType" to mimeType.take(64),
                    "dataBase64" to base64Chunk,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                try {
                    chunkRef.set(chunkPayload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, chunkRef.path, auth)
                    throw e
                }
            }

            val sheetRef = db.collection("users").document(uid).collection("sheets").document(sheetId)
            val payload = mapOf(
                "userId" to uid,
                "type" to type,
                "title" to title.trim().ifBlank { "Untitled Sheet" }.take(140),
                "content" to content.take(20000),
                "imageBase64" to imageBase64.take(750000),
                "durationMs" to durationMs.coerceIn(0L, 36000000L),
                "mimeType" to mimeType.take(64),
                "chunkCount" to chunks.size.toLong(),
                "trackId" to trackId.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                sheetRef.set(payload).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, sheetRef.path, auth)
                throw e
            }
            sheetId
        }
    }

    suspend fun renameSheet(sheetId: String, newTitle: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sheets").document(sheetId)
        return runCatching {
            val updates = mapOf(
                "title" to newTitle.trim().ifBlank { "Untitled Sheet" }.take(140),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
                throw e
            }
        }
    }

    suspend fun deleteSheet(sheetId: String): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            deleteChunksForParent(uid, sheetId)
            val docRef = db.collection("users").document(uid).collection("sheets").document(sheetId)
            try {
                docRef.delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
                throw e
            }
        }
    }

    suspend fun downloadMediaChunks(parentId: String): Result<ByteArray> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("mediaChunks")
        return runCatching {
            val snapshot = try {
                colRef.whereEqualTo("parentId", parentId).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
                throw e
            }
            val chunks = snapshot.toObjects(MediaChunk::class.java).sortedBy { it.chunkIndex }
            if (chunks.isEmpty()) throw IllegalStateException("No audio data found in cloud.")
            val output = java.io.ByteArrayOutputStream()
            for (chunk in chunks) {
                val bytes = Base64.decode(chunk.dataBase64, Base64.NO_WRAP)
                output.write(bytes)
            }
            output.toByteArray()
        }
    }

    suspend fun clearAllUserData(): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            val userRef = db.collection("users").document(uid)
            val tracks = userRef.collection("tracks").get().await()
            val sheets = userRef.collection("sheets").get().await()
            val chunks = userRef.collection("mediaChunks").get().await()

            val refs = tracks.documents.map { it.reference } +
                sheets.documents.map { it.reference } +
                chunks.documents.map { it.reference }

            refs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            seedDemoTracks(uid)
        }
    }

    suspend fun deleteAccountData(): Result<Unit> {
        val uid = requireUserId()
        return runCatching {
            val userRef = db.collection("users").document(uid)
            val tracks = userRef.collection("tracks").get().await()
            val sheets = userRef.collection("sheets").get().await()
            val chunks = userRef.collection("mediaChunks").get().await()
            val refs = tracks.documents.map { it.reference } +
                sheets.documents.map { it.reference } +
                chunks.documents.map { it.reference } +
                listOf(userRef)

            refs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        }
    }

    private suspend fun deleteChunksForParent(uid: String, parentId: String) {
        val chunksRef = db.collection("users").document(uid).collection("mediaChunks")
        val snap = try {
            chunksRef.whereEqualTo("parentId", parentId).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, chunksRef.path, auth)
            throw e
        }
        if (!snap.isEmpty) {
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun splitIntoChunks(bytes: ByteArray): List<String> {
        val maxRawChunkSize = 450_000 // ~600 KB base64 (< 750 KB rule limit)
        val result = mutableListOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + maxRawChunkSize, bytes.size)
            val slice = bytes.copyOfRange(offset, end)
            result.add(Base64.encodeToString(slice, Base64.NO_WRAP))
            offset = end
        }
        return result
    }
}
