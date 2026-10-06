package com.malik.aieffectapp

/**
 * ALL placeholders the owner must fill before release.
 * Nothing secret goes here except non-sensitive Firebase web config
 * (API keys for AI services live ONLY in Cloud Functions env, never here).
 */
object Config {

    // ---- Firebase (from console.firebase.google.com → Project settings) ----
    // TODO(owner): fill these from your Firebase project, then delete this TODO.
    const val FIREBASE_API_KEY = "REPLACE_WITH_FIREBASE_API_KEY"
    const val FIREBASE_APP_ID = "REPLACE_WITH_FIREBASE_APP_ID" // e.g. 1:123456:android:abcdef
    const val FIREBASE_PROJECT_ID = "REPLACE_WITH_PROJECT_ID"
    const val FIREBASE_STORAGE_BUCKET = "REPLACE_WITH_PROJECT_ID.appspot.com"
    const val FUNCTIONS_REGION = "asia-south1"

    // ---- AdMob (from apps.admob.com) ----
    // Test IDs below are Google's official test units — replace with real ones.
    const val ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713" // TODO(owner): real app id
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // TODO(owner): real unit
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // TODO(owner): real unit

    // ---- Play Billing product IDs (create these in Play Console → Monetize) ----
    const val SUB_WEEKLY_ID = "pro_weekly"   // TODO(owner): create subscription
    const val SUB_YEARLY_ID = "pro_yearly"   // TODO(owner): create subscription
    const val CREDITS_10_ID = "credits_10"   // TODO(owner): create managed product (10 video credits)

    // ---- Free tier (must match Cloud Functions) ----
    const val FREE_IMAGES_PER_DAY = 3
}
