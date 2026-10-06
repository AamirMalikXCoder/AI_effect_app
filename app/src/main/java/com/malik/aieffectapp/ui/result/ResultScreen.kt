package com.malik.aieffectapp.ui.result

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.malik.aieffectapp.data.AdsManager
import com.malik.aieffectapp.data.effectById
import com.malik.aieffectapp.ui.components.AiGeneratedLabel
import com.malik.aieffectapp.ui.components.ReportButton
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    effectId: String,
    url: String,
    isVideo: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val effect = remember(effectId) { effectById(effectId) }

    val player = remember {
        if (isVideo) ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        } else null
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Made with AI Effect Studio ✨ $url")
        }
        context.startActivity(Intent.createChooser(send, "Share your creation"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${effect.emoji} ${effect.name}", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isVideo && player != null) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentScale = ContentScale.Fit,
                )
            }

            // Play-policy: visible AI label + report button, adjacent to output.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AiGeneratedLabel()
                ReportButton(generationId = url)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = ::share,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                Button(
                    onClick = {
                        if (activity != null) AdsManager.showInterstitial(activity)
                        onHome()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Make another") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Route helper: result/{effectId}/{isVideo}/{encodedUrl} */
fun resultRoute(effectId: String, url: String, isVideo: Boolean): String =
    "result/$effectId/$isVideo/${URLEncoder.encode(url, "UTF-8")}"
