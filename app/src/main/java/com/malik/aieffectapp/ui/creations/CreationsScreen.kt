package com.malik.aieffectapp.ui.creations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

data class CreationItem(
    val id: String,
    val type: String,
    val effect: String?,
    val videoUrl: String?,
    val imageUrl: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationsScreen(
    onBack: () -> Unit,
    onOpen: (effectId: String, url: String, isVideo: Boolean) -> Unit,
) {
    var items by remember { mutableStateOf<List<CreationItem>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val uid = Firebase.auth.currentUser?.uid ?: return@LaunchedEffect
            val snap = Firebase.firestore.collection("generations")
                .whereEqualTo("uid", uid)
                .whereEqualTo("status", "completed")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60).get().await()
            items = snap.documents.mapNotNull { d ->
                val type = d.getString("type") ?: return@mapNotNull null
                CreationItem(
                    id = d.id,
                    type = type,
                    effect = d.getString("effect"),
                    videoUrl = d.getString("videoUrl"),
                    imageUrl = d.getString("imageUrl"),
                )
            }
        } catch (_: Exception) { items = emptyList() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My creations", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val list = items) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> if (list.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎬", style = MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing here yet — go create something viral!")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list) { item ->
                            val url = item.videoUrl ?: item.imageUrl
                            val isVideo = item.type == "video"
                            if (url != null) {
                                Card(modifier = Modifier.clickable {
                                    onOpen(item.effect ?: "hug", url, isVideo)
                                }) {
                                    Box {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.aspectRatio(9f / 16f),
                                            contentScale = ContentScale.Crop,
                                        )
                                        if (isVideo) {
                                            Text(
                                                "▶",
                                                modifier = Modifier.align(Alignment.Center),
                                                style = MaterialTheme.typography.headlineLarge,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
