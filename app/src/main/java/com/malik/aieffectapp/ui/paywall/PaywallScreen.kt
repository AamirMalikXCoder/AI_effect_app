package com.malik.aieffectapp.ui.paywall

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malik.aieffectapp.data.BillingRepository
import com.malik.aieffectapp.data.OfferUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    billing: BillingRepository,
    onBack: () -> Unit,
    onSubscribed: () -> Unit,
) {
    val offers by billing.offers.collectAsState()
    val isPro by billing.isPro.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    if (isPro) { onSubscribed(); return }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Go Pro ✨", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Unlock unlimited AI videos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            listOf(
                "🤗 AI Hug, 💋 AI Kiss, 💃 AI Dance videos",
                "🎨 Unlimited anime portraits",
                "⚡ Priority generation queue",
                "🚫 No ads",
            ).forEach { perk ->
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(4.dp))
                    Text(perk)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (offers.isEmpty()) {
                Card { Text("Loading plans…", modifier = Modifier.padding(16.dp)) }
            }
            offers.forEach { offer ->
                OfferCard(offer) {
                    if (activity != null) billing.launchPurchase(activity, offer)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Subscription billed via Google Play. Cancel anytime in Play Store settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfferCard(offer: OfferUi, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        ListItem(
            headlineContent = { Text(offer.title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(offer.price) },
            trailingContent = {
                if (offer.productId.contains("credit", ignoreCase = true)) {
                    OutlinedButton(onClick = onBuy) { Text("Buy") }
                } else {
                    Button(onClick = onBuy) { Text("Subscribe") }
                }
            },
        )
    }
}
