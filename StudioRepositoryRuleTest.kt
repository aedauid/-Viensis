package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SheetType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StudioRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureDefaultsAndAddTrack_authenticatedOwner_succeedsAndEmitsUpdates() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = StudioRepository(firestore, auth)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureDefaultSettingsAndDemos("Alice")
        }
        assertTrue(initResult.isSuccess)

        val tracks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeTracks(uid).first { it.size >= 3 }
        }
        assertEquals("daudio", tracks.first().title)

        val customAudio = ByteArray(1024) { (it % 127).toByte() }
        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addTrack(
                title = "Studio Sketch",
                artist = "Alice",
                album = "Drafts",
                tags = listOf("demo", "guitar"),
                lyrics = "Line one\nLine two",
                durationMs = 15000L,
                orderIndex = 3L,
                mimeType = "audio/wav",
                audioBytes = customAudio
            )
        }
        assertTrue(addResult.isSuccess)
        val trackId = addResult.getOrThrow()

        val downloaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.downloadMediaChunks(trackId)
        }
        assertTrue(downloaded.isSuccess)
        assertEquals(1024, downloaded.getOrThrow().size)
    }

    @Test
    fun saveAndDeleteSheet_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("writer_${UUID.randomUUID()}@test.com")
        val repository = StudioRepository(firestore, auth)

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveSheet(
                type = SheetType.NOTE,
                title = "Bridge Idea",
                content = "Hold the chord in four-four time"
            )
        }
        assertTrue(saveResult.isSuccess)
        val sheetId = saveResult.getOrThrow()

        val sheets = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSheets(uid).first { list -> list.any { it.id == sheetId } }
        }
        assertEquals("Bridge Idea", sheets.first { it.id == sheetId }.title)

        val renameResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.renameSheet(sheetId, "Bridge V2")
        }
        assertTrue(renameResult.isSuccess)

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteSheet(sheetId)
        }
        assertTrue(deleteResult.isSuccess)
    }

    @Test
    fun observeTracks_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
        val aliceRepo = StudioRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureDefaultSettingsAndDemos("Alice").getOrThrow()
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
        val bobRepo = StudioRepository(firestore, auth)
        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeTracks(aliceUid).first()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeTracks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = StudioRepository(firestore, auth)

        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeTracks("alice_123").first()
            }
        }.exceptionOrNull()

        val firestoreEx = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
