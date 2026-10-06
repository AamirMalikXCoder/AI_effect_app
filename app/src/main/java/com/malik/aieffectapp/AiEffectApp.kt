package com.malik.aieffectapp

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiEffectApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Manual Firebase init (used until google-services.json is added).
        // TODO(owner): add google-services.json to app/ and remove this block.
        if (FirebaseApp.getApps(this).isEmpty()) {
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val auth = Firebase.auth
                if (auth.currentUser == null) auth.signInAnonymously()
            } catch (_: Exception) { /* offline — retry on next launch */ }
        }
    }
}
