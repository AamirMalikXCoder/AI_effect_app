package com.malik.aieffectapp.ui.effect

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.malik.aieffectapp.data.AdsManager
import com.malik.aieffectapp.data.AiRepository
import com.malik.aieffectapp.data.EffectType
import com.malik.aieffectapp.data.GenerateResult
import com.malik.aieffectapp.data.PaywallRequired
import com.malik.aieffectapp.data.effectById
import com.malik.aieffectapp.ui.components.DataSharingDisclosure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface GenerateUiState {
    data object Idle : GenerateUiState
    data object Picked : GenerateUiState
    data object Uploading : GenerateUiState
    data object Generating : GenerateUiState
    data class DoneVideo(val url: String) : GenerateUiState
    data class DoneImage(val url: String) : GenerateUiState
    data class Paywalled(val reason: String) : GenerateUiState
    data class Error(val message: String) : GenerateUiState
}

class EffectViewModel : ViewModel() {
    private val _state = MutableStateFlow<GenerateUiState>(GenerateUiState.Idle)
    val state: StateFlow<GenerateUiState> = _state

    var photoUri: Uri? = null
        private set

    fun onPhotoPicked(uri: Uri) {
        photoUri = uri
        _state.value = GenerateUiState.Picked
    }

    fun generate(effectId: String) {
        val uri = photoUri ?: return
        val effect = effectById(effectId)
        viewModelScope.launch {
            try {
                _state.value = GenerateUiState.Uploading
                val path = AiRepository.uploadPhoto(uri)
                _state.value = GenerateUiState.Generating
                when (effect.type) {
                    EffectType.VIDEO -> {
                        val res = AiRepository.generateVideo(effectId, path) as GenerateResult.Video
                        _state.value = GenerateUiState.DoneVideo(res.videoUrl)
                    }
                    EffectType.IMAGE -> {
                        val res = AiRepository.generateImage(path) as GenerateResult.Image
                        _state.value = GenerateUiState.DoneImage(res.imageUrl)
                    }
                }
            } catch (e: PaywallRequired) {
                _state.value = GenerateUiState.Paywalled(e.message ?: "Subscribe to continue")
            } catch (e: Exception) {
                _state.value = GenerateUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun reset() {
        photoUri = null
        _state.value = GenerateUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectScreen(
    effectId: String,
    onBack: () -> Unit,
    onResult: (effectId: String, url: String, isVideo: Boolean) -> Unit,
    onPaywall: () -> Unit,
    vm: EffectViewModel = viewModel(),
) {
    val effect = remember(effectId) { effectById(effectId) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }
    var pendingGenerate by remember { mutableStateOf(false) }

    // Route terminal states (as side-effects, not during composition)
    androidx.compose.runtime.LaunchedEffect(state) {
        when (val s = state) {
            is GenerateUiState.DoneVideo -> { onResult(effectId, s.url, true); vm.reset() }
            is GenerateUiState.DoneImage -> { onResult(effectId, s.url, false); vm.reset() }
            is GenerateUiState.Paywalled -> { onPaywall(); vm.reset() }
            else -> {}
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) vm.onPhotoPicked(uri) }

    if (showDisclosure) {
        DataSharingDisclosure(
            onAccept = {
                showDisclosure = false
                // remember consent for this install
                context.getSharedPreferences("prefs", Activity.MODE_PRIVATE)
                    .edit().putBoolean("disclosure_ok", true).apply()
                if (pendingGenerate) { pendingGenerate = false; vm.generate(effectId) }
            },
            onDecline = { showDisclosure = false; pendingGenerate = false },
        )
    }

    fun startGenerate() {
        val ok = context.getSharedPreferences("prefs", Activity.MODE_PRIVATE)
            .getBoolean("disclosure_ok", false)
        if (!ok) { pendingGenerate = true; showDisclosure = true }
        else vm.generate(effectId)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when (val s = state) {
                    is GenerateUiState.Idle -> {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("📷 Choose a photo") }
                    }
                    is GenerateUiState.Picked -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = vm.photoUri,
                                contentDescription = null,
                                modifier = Modifier.size(240.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Change photo") }
                        }
                    }
                    is GenerateUiState.Uploading ->
                        LoadingBlock("Uploading photo…")
                    is GenerateUiState.Generating ->
                        LoadingBlock(
                            if (effect.type == EffectType.VIDEO)
                                "Dreaming your video… (30–90 sec)" else "Painting anime…"
                        )
                    is GenerateUiState.Error ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️ ${s.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { vm.reset() }) { Text("Try again") }
                        }
                    else -> {}
                }
            }

            if (state is GenerateUiState.Picked) {
                Button(
                    onClick = ::startGenerate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        if (effect.type == EffectType.VIDEO) " Generate video" else " Generate anime",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (effect.type == EffectType.IMAGE) {
                    Text(
                        "Free images left today shown after generation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Videos need Pro (1 free trial included)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Preload ads while the user picks a photo
    androidx.compose.runtime.LaunchedEffect(Unit) { AdsManager.preload(context) }
}

@Composable
private fun LoadingBlock(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
