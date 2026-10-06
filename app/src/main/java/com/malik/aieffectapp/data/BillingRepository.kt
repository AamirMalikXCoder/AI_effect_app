package com.malik.aieffectapp.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.malik.aieffectapp.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class OfferUi(
    val productId: String,
    val title: String,
    val price: String,
    val details: ProductDetails,
)

/**
 * Play Billing wrapper. Subscriptions unlock unlimited generations;
 * the one-time "credits_10" pack adds 10 video credits to Firestore.
 * Server-side verification (Play Developer API) is the production
 * upgrade — v1 trusts the client acknowledgement + Firestore write.
 */
class BillingRepository(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val appContext = context.applicationContext

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro

    private val _offers = MutableStateFlow<List<OfferUi>>(emptyList())
    val offers: StateFlow<List<OfferUi>> = _offers

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) scope.launch { handlePurchase(p) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases()
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        queryOffers()
                        refreshProStatus()
                    }
                }
            }
            override fun onBillingServiceDisconnected() { /* retry on next start() */ }
        })
    }

    private suspend fun queryOffers() {
        val subs = queryProducts(
            listOf(Config.SUB_WEEKLY_ID, Config.SUB_YEARLY_ID),
            BillingClient.ProductType.SUBS,
        )
        val inapps = queryProducts(
            listOf(Config.CREDITS_10_ID),
            BillingClient.ProductType.INAPP,
        )
        _offers.value = subs + inapps
    }

    private suspend fun queryProducts(ids: List<String>, type: String): List<OfferUi> {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it).setProductType(type).build()
            }
        ).build()
        val result = client.queryProductDetails(params)
        return (result.productDetailsList ?: emptyList()).map { d ->
            val price = d.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: d.oneTimePurchaseOfferDetails?.formattedPrice
                ?: ""
            OfferUi(d.productId, d.title.substringBefore(" ("), price, d)
        }
    }

    fun launchPurchase(activity: Activity, offer: OfferUi) {
        val params = if (offer.details.productType == BillingClient.ProductType.SUBS) {
            val token = offer.details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: return
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(offer.details).setOfferToken(token).build()
        } else {
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(offer.details).build()
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params)).build()
        client.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val uid = com.google.firebase.auth.ktx.auth.currentUser?.uid ?: return
        val userRef = Firebase.firestore.collection("users").document(uid)

        when {
            // Subscription -> pro flag
            purchase.products.any { it == Config.SUB_WEEKLY_ID || it == Config.SUB_YEARLY_ID } -> {
                if (!purchase.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                    )
                }
                userRef.update("isPro", true)
                _isPro.value = true
            }
            // Credit pack -> +10 video credits, then consume
            purchase.products.contains(Config.CREDITS_10_ID) -> {
                userRef.update("videoCredits", com.google.firebase.firestore.FieldValue.increment(10))
                client.consumePurchase(
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                )
            }
        }
    }

    /** Re-checks entitlement on every app start (restores, expiry). */
    suspend fun refreshProStatus() {
        val uid = com.google.firebase.auth.ktx.auth.currentUser?.uid ?: return
        return try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS).build()
            val res = client.queryPurchases(params)
            val active = (res.purchasesList ?: emptyList()).any {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            Firebase.firestore.collection("users").document(uid)
                .update("isPro", active).await()
            _isPro.value = active
        } catch (_: Exception) { /* keep last known */ }
    }
}
