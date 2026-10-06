package com.malik.aieffectapp.data

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.malik.aieffectapp.Config

/**
 * AdMob helper. Rewarded ads gate "one more free anime image";
 * interstitials show after a completed generation (capped by caller).
 * Uses Google test units until the owner replaces them in Config.
 */
object AdsManager {

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    fun preload(context: Context) {
        loadRewarded(context)
        loadInterstitial(context)
    }

    private fun loadRewarded(context: Context) {
        RewardedAd.load(
            context, Config.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
            },
        )
    }

    private fun loadInterstitial(context: Context) {
        InterstitialAd.load(
            context, Config.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitialAd = null }
            },
        )
    }

    /** Shows rewarded ad; onReward is called only if the user earned it. */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) { loadRewarded(activity); return }
        ad.show(activity) { onReward() }
        rewardedAd = null
        loadRewarded(activity)
    }

    fun showInterstitial(activity: Activity) {
        interstitialAd?.show(activity)
        interstitialAd = null
        loadInterstitial(activity)
    }
}
