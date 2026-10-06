package com.malik.aieffectapp

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AiEffectApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val configOk = !Config.FIREBASE_API_KEY.startsWith("REPLACE_WITH")
        if (!configOk) {
            Log.w("AiEffectApp", "Firebase NOT configured — fill Config.kt (see HANDOVER.md step 1)")
        }

        // Manual Firebase init (used until google-services.json is added).
        // TODO(owner): add google-services.json to app/ and remove this block.
        if (FirebaseApp.getApps(this).isEmpty() && configOk) {
            val options = FirebaseOptions.Builder()
                .setApiKey(Config.FIREBASE_API_KEY)
                .setApplicationId(Config.FIREBASE_APP_ID)
                .setProjectId(Config.FIREBASE_PROJECT_ID)
                .setStorageBucket(Config.FIREBASE_STORAGE_BUCKET)
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        MobileAds.initialize(this) {}

        // Anonymous auth so every install gets a credit ledger immediately.
        // Retried with backoff — AiRepository.ensureUid() also signs in on demand.
        CoroutineScope(Dispatchers.IO).launch {
            repeat(3) { attempt ->
                try {
                    val auth = Firebase.auth
                    if (auth.currentUser == null) auth.signInAnonymously().await()
                    return@launch
                } catch (e: Exception) {
                    Log.w("AiEffectApp", "Anonymous sign-in attempt ${attempt + 1} failed: ${e.message}")
                    kotlinx.coroutines.delay(2000L * (attempt + 1))
                }
            }
        }
    }
}
