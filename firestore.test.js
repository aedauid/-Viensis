const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validSettingsPayload(uid) {
  return {
    userId: uid,
    displayName: "Songwriter",
    darkTheme: false,
    defaultShareFormat: "STORY_9_16",
    autosaveEnabled: true,
    hapticsEnabled: true,
    reducedMotion: false,
    autoScrollLyrics: true,
    onboardingCompleted: false,
    audioQuality: "HIGH",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validTrackPayload(uid) {
  return {
    userId: uid,
    title: "Ensis Vi",
    artist: "The Marching Line",
    album: "Blueprint One",
    tags: ["anthem", "march"],
    lyrics: "The road is clear\nMarching on",
    durationMs: 26000,
    orderIndex: 0,
    isDemo: true,
    demoKey: "ensis_vi",
    mimeType: "audio/wav",
    chunkCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validSheetPayload(uid) {
  return {
    userId: uid,
    type: "note",
    title: "Chorus Hook",
    content: "Gold on the line",
    imageBase64: "",
    durationMs: 0,
    mimeType: "text/plain",
    chunkCount: 0,
    trackId: "track_1",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validChunkPayload(uid) {
  return {
    userId: uid,
    parentId: "track_1",
    parentType: "track",
    chunkIndex: 0,
    totalChunks: 1,
    mimeType: "audio/mp3",
    dataBase64: "UklGRg==",
    createdAt: serverTimestamp(),
  };
}

test("Unauthenticated user cannot read or write user tracks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tracks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tracks").doc("t1").set(validTrackPayload(ALICE_UID))
  );
});

test("Alice can create, read, update, delete her own settings, tracks, sheets, chunks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validSettingsPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({ darkTheme: true, updatedAt: serverTimestamp() })
  );

  const trackRef = aliceDb.collection("users").doc(ALICE_UID).collection("tracks").doc("track_1");
  await assertSucceeds(trackRef.set(validTrackPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tracks").get());
  await assertSucceeds(trackRef.update({ title: "Ensis VI (Reprise)", updatedAt: serverTimestamp() }));

  const sheetRef = aliceDb.collection("users").doc(ALICE_UID).collection("sheets").doc("sheet_1");
  await assertSucceeds(sheetRef.set(validSheetPayload(ALICE_UID)));
  await assertSucceeds(sheetRef.update({ content: "Updated idea", updatedAt: serverTimestamp() }));

  const chunkRef = aliceDb.collection("users").doc(ALICE_UID).collection("mediaChunks").doc("chunk_0");
  await assertSucceeds(chunkRef.set(validChunkPayload(ALICE_UID)));
  await assertFails(chunkRef.update({ dataBase64: "AAAA" }));
  await assertSucceeds(chunkRef.delete());
  await assertSucceeds(sheetRef.delete());
  await assertSucceeds(trackRef.delete());
});

test("Bob cannot read or write Alice's tracks or sheets", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tracks").doc("track_1").set(validTrackPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tracks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tracks").doc("track_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sheets").doc("s1").set(validSheetPayload(BOB_UID))
  );
});

test("Shadow update with extra ghost field or userId spoofing fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tracks").doc("bad_track").set({
      ...validTrackPayload(ALICE_UID),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tracks").doc("spoof_track").set(validTrackPayload(BOB_UID))
  );
});
