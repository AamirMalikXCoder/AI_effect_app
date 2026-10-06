package com.malik.aieffectapp.data

import android.net.Uri
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import com.malik.aieffectapp.Config
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed class GenerateResult {
    data class Video(val videoUrl: String, val chargedAs: String) : GenerateResult()
    data class Image(val imageUrl: String, val freeUsed: Int, val freeLimit: Int) : GenerateResult()
}

/** Thrown when the backend says the user hit the paywall. */
class PaywallRequired(message: String) : Exception(message)

/**
 * Talks to our Cloud Functions (which hold the Luma/OpenAI keys).
 * Photos are uploaded to Firebase Storage first; only the storage path
 * is sent to the function — never raw bytes, never API keys.
 */
object AiRepository {

    private fun functions() = Firebase.functions(Config.FUNCTIONS_REGION)

    /**
     * Returns the uid, signing in anonymously first if needed.
     * Throws a human-readable error if Firebase isn't configured.
     */
    private suspend fun ensureUid(): String {
        val auth = Firebase.auth
        auth.currentUser?.uid?.let { return it }
        if (Config.FIREBASE_API_KEY.startsWith("REPLACE_WITH")) {
            throw IllegalStateException(
                "Firebase setup pending hai: Config.kt me apne Firebase project ki " +
                    "values bharo (HANDOVER.md step 1), phir app rebuild karo."
            )
        }
        try {
            auth.signInAnonymously().await()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Sign-in fail: Firebase console me Anonymous auth enabled hai? " +
                    "Internet on hai? (${e.message})"
            )
        }
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Sign-in fail — HANDOVER.md step 1 check karo.")
    }

    /** Uploads a photo, returns the storage path for the function call. */
    suspend fun uploadPhoto(uri: Uri): String {
        val userId = ensureUid()
        val path = "uploads/$userId/${UUID.randomUUID()}.jpg"
        Firebase.storage.reference.child(path).putFile(uri).await()
        return path
    }

    suspend fun generateVideo(effectId: String, storagePath: String): GenerateResult.Video {
        return try {
            val data = mapOf("effect" to effectId, "storagePath" to storagePath)
            val result = functions().getHttpsCallable("generateVideo").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as Map<String, Any>
            GenerateResult.Video(
                videoUrl = map["videoUrl"] as String,
                chargedAs = (map["chargedAs"] as? String) ?: "unknown",
            )
        } catch (e: Exception) {
            if (e.message?.contains("PAYWALL") == true) throw PaywallRequired(e.message!!)
            throw e
        }
    }

    suspend fun generateImage(storagePath: String): GenerateResult.Image {
        return try {
            val data = mapOf("storagePath" to storagePath)
            val result = functions().getHttpsCallable("generateImage").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.data as Map<String, Any>
            GenerateResult.Image(
                imageUrl = map["imageUrl"] as String,
                freeUsed = ((map["freeUsed"] as? Number)?.toInt()) ?: 0,
                freeLimit = ((map["freeLimit"] as? Number)?.toInt()) ?: Config.FREE_IMAGES_PER_DAY,
            )
        } catch (e: Exception) {
            if (e.message?.contains("PAYWALL") == true) throw PaywallRequired(e.message!!)
            throw e
        }
    }

    /** Writes an AI-content report (Play policy: every AI output needs a flag button). */
    suspend fun reportGeneration(generationId: String, reason: String) {
        // generationId is the videoUrl/imageUrl shown; backend logs the real id.
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("reports").add(
            mapOf(
                "uid" to ensureUid(),
                "generationId" to generationId,
                "reason" to reason,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }
}
