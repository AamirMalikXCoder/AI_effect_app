package com.malik.aieffectapp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.malik.aieffectapp.data.AiRepository
import kotlinx.coroutines.launch

/**
 * Mandatory visible "AI-generated" label adjacent to every AI output.
 * (Play policy: a privacy-policy mention alone does not count.)
 */
@Composable
fun AiGeneratedLabel(modifier: Modifier = Modifier) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        label = { Text("AI-generated") },
        leadingIcon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
    )
}

/**
 * Mandatory report/flag button on every AI output (Play policy).
 * Writes to Firestore `reports` collection for owner review.
 */
@Composable
fun ReportButton(generationId: String, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    IconButton(modifier = modifier, onClick = { showDialog = true }) {
        Icon(Icons.Filled.Flag, contentDescription = "Report this content")
    }

    if (showDialog) {
        var reason by remember { mutableStateOf("Inappropriate content") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Report content") },
            text = {
                androidx.compose.foundation.layout.Column {
                    listOf("Inappropriate content", "Wrong person / face", "Copyright issue", "Other")
                        .forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.RadioButton(
                                    selected = reason == option,
                                    onClick = { reason = option },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(option)
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    scope.launch {
                        try {
                            AiRepository.reportGeneration(generationId, reason)
                            sent = true
                        } catch (_: Exception) { }
                    }
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
    if (sent) {
        Text(
            "Report sent ✓",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )
    }
}

/** First-run disclosure shown before the first upload (data-sharing consent). */
@Composable
fun DataSharingDisclosure(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("How your photos are used") },
        text = {
            Text(
                "Your photo is sent to our AI providers (Luma AI, OpenAI) to create the effect, " +
                    "then deleted from processing servers. We never sell your photos. " +
                    "See the privacy policy for details."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("I understand") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Cancel") } },
    )
}
