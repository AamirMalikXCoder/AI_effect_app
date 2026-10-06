/**
 * AI Effect App — Cloud Functions (Node 20, firebase-functions v2)
 *
 * Two callable functions that proxy AI APIs so secret keys NEVER live in the app:
 *   generateVideo({ effect, storagePath })  -> Luma Dream Machine (hug/kiss/dance)
 *   generateImage({ storagePath, style })   -> OpenAI gpt-image-1-mini (photo-to-anime)
 *
 * Credit model (the economics guardrail — do not loosen without redoing the math):
 *   - Video: paid-only. 1 free trial per user, then subscription or credit packs.
 *   - Image: 3 free / day (ad-supported), unlimited for subscribers.
 *
 * Required env (firebase functions:config / .env):
 *   LUMA_API_KEY, LUMA_MODEL (default "ray-3.2" — confirm exact model string in Luma docs),
 *   OPENAI_API_KEY
 */

const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getStorage } = require("firebase-admin/storage");

initializeApp();
const db = getFirestore();
const bucket = getStorage().bucket();

const LUMA_API_KEY = process.env.LUMA_API_KEY;
const LUMA_MODEL = process.env.LUMA_MODEL || "ray-3.2"; // TODO: confirm exact model id in Luma dashboard
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;

const FREE_IMAGES_PER_DAY = 3;
const VIDEO_POLL_MS = 8000;
const VIDEO_POLL_MAX = 34; // ~4.5 min max

// ---------------------------------------------------------------------------
// Effect prompt templates (9:16 vertical, photorealistic motion)
// ---------------------------------------------------------------------------
const VIDEO_PROMPTS = {
  hug: "The two people in this photo warmly embrace in a heartfelt hug, natural gentle motion, soft cinematic lighting, photorealistic, vertical video",
  kiss: "The couple in this photo leans in and shares a tender romantic kiss, soft natural motion, cinematic close-up, photorealistic, vertical video",
  dance: "The person in this photo starts dancing energetically with rhythmic body movement, joyful expression, dynamic motion, photorealistic, vertical video",
};

const ANIME_STYLE_PROMPT =
  "Redraw this photo in beautiful anime style, clean line art, vibrant colors, studio-quality illustration. Keep the same person, pose and composition. No text, no watermark.";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function todayKey() {
  return new Date().toISOString().slice(0, 10); // UTC day
}

async function getUserDoc(uid) {
  const ref = db.collection("users").doc(uid);
  const snap = await ref.get();
  if (!snap.exists) {
    const fresh = {
      videoCredits: 0,
      trialVideoUsed: false,
      imageFreeDate: todayKey(),
      imageFreeUsed: 0,
      isPro: false,
      createdAt: FieldValue.serverTimestamp(),
    };
    await ref.set(fresh);
    return { ref, data: fresh };
  }
  return { ref, data: snap.data() };
}

/** Signed public URL (15 min) for the uploaded photo, so Luma can fetch it. */
async function signedPhotoUrl(storagePath, uid) {
  // Basic ownership check: path must live under this user's folder.
  if (!storagePath.startsWith(`uploads/${uid}/`)) {
    throw new HttpsError("permission-denied", "Invalid photo path.");
  }
  const file = bucket.file(storagePath);
  const [exists] = await file.exists();
  if (!exists) throw new HttpsError("not-found", "Photo not found. Upload again.");
  const [url] = await file.getSignedUrl({ action: "read", expires: Date.now() + 15 * 60 * 1000 });
  return url;
}

async function logGeneration(uid, data) {
  await db.collection("generations").add({
    uid,
    createdAt: FieldValue.serverTimestamp(),
    ...data,
  });
}

// ---------------------------------------------------------------------------
// Luma adapter
// ---------------------------------------------------------------------------
async function lumaCreateImageToVideo(photoUrl, prompt) {
  const res = await fetch("https://api.lumalabs.ai/dream-machine/v1/generations", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${LUMA_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      prompt,
      model: LUMA_MODEL,
      resolution: "720p",
      duration: "5s",
      aspect_ratio: "9:16",
      keyframes: { frame0: { type: "image", url: photoUrl } },
    }),
  });
  if (!res.ok) {
    const t = await res.text().catch(() => "");
    throw new HttpsError("internal", `Luma create failed (${res.status}): ${t.slice(0, 200)}`);
  }
  const json = await res.json();
  if (!json.id) throw new HttpsError("internal", "Luma returned no generation id.");
  return json.id;
}

async function lumaPollUntilDone(generationId) {
  for (let i = 0; i < VIDEO_POLL_MAX; i++) {
    await new Promise((r) => setTimeout(r, VIDEO_POLL_MS));
    const res = await fetch(
      `https://api.lumalabs.ai/dream-machine/v1/generations/${generationId}`,
      { headers: { Authorization: `Bearer ${LUMA_API_KEY}` } }
    );
    if (!res.ok) continue;
    const g = await res.json();
    if (g.state === "completed" && g.assets && g.assets.video) return g.assets.video;
    if (g.state === "failed") {
      throw new HttpsError("internal", `Video generation failed: ${(g.failure_reason || "unknown").slice(0, 200)}`);
    }
  }
  throw new HttpsError("deadline-exceeded", "Video is taking too long. Try again in a minute.");
}

// ---------------------------------------------------------------------------
// generateVideo — callable
// ---------------------------------------------------------------------------
exports.generateVideo = onCall(
  { timeoutSeconds: 300, memory: "512MiB", region: "asia-south1" },
  async (request) => {
    if (!LUMA_API_KEY) throw new HttpsError("failed-precondition", "LUMA_API_KEY not configured.");
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

    const { effect, storagePath } = request.data || {};
    if (!VIDEO_PROMPTS[effect]) throw new HttpsError("invalid-argument", "Unknown effect.");
    if (!storagePath) throw new HttpsError("invalid-argument", "Photo missing.");

    const { ref, data } = await getUserDoc(uid);

    // --- entitlement check (deduct ONLY after success) ---
    let charge = null; // 'trial' | 'credit' | 'pro'
    if (data.isPro) charge = "pro";
    else if (!data.trialVideoUsed) charge = "trial";
    else if ((data.videoCredits || 0) > 0) charge = "credit";
    else throw new HttpsError("resource-exhausted", "PAYWALL: no video credits. Subscribe or buy a pack.");

    const photoUrl = await signedPhotoUrl(storagePath, uid);
    let videoUrl;
    try {
      const genId = await lumaCreateImageToVideo(photoUrl, VIDEO_PROMPTS[effect]);
      videoUrl = await lumaPollUntilDone(genId);
      await logGeneration(uid, { type: "video", effect, status: "completed", lumaId: genId, videoUrl });
    } catch (e) {
      await logGeneration(uid, { type: "video", effect, status: "failed", error: String(e.message || e).slice(0, 300) });
      throw e; // no charge on failure
    }

    // --- apply charge after success ---
    const update = {};
    if (charge === "trial") update.trialVideoUsed = true;
    if (charge === "credit") update.videoCredits = FieldValue.increment(-1);
    await ref.update(update);

    return { videoUrl, chargedAs: charge };
  }
);

// ---------------------------------------------------------------------------
// generateImage — callable (photo-to-anime via OpenAI image edits)
// ---------------------------------------------------------------------------
exports.generateImage = onCall(
  { timeoutSeconds: 120, memory: "512MiB", region: "asia-south1" },
  async (request) => {
    if (!OPENAI_API_KEY) throw new HttpsError("failed-precondition", "OPENAI_API_KEY not configured.");
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

    const { storagePath } = request.data || {};
    if (!storagePath) throw new HttpsError("invalid-argument", "Photo missing.");

    const { ref, data } = await getUserDoc(uid);

    // --- free-tier check: 3/day, resets on UTC day rollover ---
    let freeUsed = data.imageFreeUsed || 0;
    if (data.imageFreeDate !== todayKey()) freeUsed = 0;
    const isPro = !!data.isPro;
    if (!isPro && freeUsed >= FREE_IMAGES_PER_DAY) {
      throw new HttpsError("resource-exhausted", "PAYWALL: daily free limit reached. Watch an ad or subscribe.");
    }

    const photoUrl = await signedPhotoUrl(storagePath, uid);
    const photoRes = await fetch(photoUrl);
    const photoBuf = Buffer.from(await photoRes.arrayBuffer());

    // OpenAI images/edits (multipart). Model id per research: gpt-image-1-mini.
    const form = new FormData();
    form.append("model", "gpt-image-1-mini");
    form.append("prompt", ANIME_STYLE_PROMPT);
    form.append("image", new Blob([photoBuf], { type: "image/jpeg" }), "photo.jpg");
    form.append("size", "1024x1024");

    const res = await fetch("https://api.openai.com/v1/images/edits", {
      method: "POST",
      headers: { Authorization: `Bearer ${OPENAI_API_KEY}` },
      body: form,
    });
    if (!res.ok) {
      const t = await res.text().catch(() => "");
      await logGeneration(uid, { type: "image", status: "failed", error: t.slice(0, 300) });
      throw new HttpsError("internal", `Image generation failed (${res.status}).`);
    }
    const json = await res.json();
    const b64 = json.data && json.data[0] && json.data[0].b64_json;
    if (!b64) throw new HttpsError("internal", "Image API returned no image.");

    // Persist to our Storage for a stable URL (OpenAI URLs expire).
    const outPath = `results/${uid}/${Date.now()}.png`;
    const outFile = bucket.file(outPath);
    await outFile.save(Buffer.from(b64, "base64"), { contentType: "image/png" });
    await outFile.makePublic().catch(() => {});
    const publicUrl = `https://storage.googleapis.com/${bucket.name}/${outPath}`;

    await ref.update({ imageFreeDate: todayKey(), imageFreeUsed: freeUsed + 1 });
    await logGeneration(uid, { type: "image", status: "completed", imageUrl: publicUrl });
    return { imageUrl: publicUrl, freeUsed: freeUsed + 1, freeLimit: FREE_IMAGES_PER_DAY };
  }
);
