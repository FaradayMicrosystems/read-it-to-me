package com.faradaymicrosystems.nobodygenerator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faradaymicrosystems.nobodygenerator.R
import com.faradaymicrosystems.nobodygenerator.UiState
import com.faradaymicrosystems.nobodygenerator.service.JobState
import com.faradaymicrosystems.nobodygenerator.tts.KokoroModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    ui: UiState,
    job: JobState,
    onPickFile: () -> Unit,
    onClearFile: () -> Unit,
    onInput: (String) -> Unit,
    onVoice: (String) -> Unit,
    onRate: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPreview: () -> Unit,
    onGo: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpenLibrary: () -> Unit,
    onGetVoices: () -> Unit,
    onDownloadNatural: () -> Unit,
    onCancelNatural: () -> Unit,
    onRemoveNatural: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = job is JobState.Running
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Read It To Me", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Add a document, hit Go, and get an MP3 you can play here or save.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ui.engineError?.let { err ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(err)
                    TextButton(onClick = onGetVoices) { Text("Open speech settings") }
                }
            }
        }
        if (ui.engineReady && ui.voices.isNotEmpty() && !ui.hasAustralian && !ui.naturalSelected) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("No Australian voice installed yet.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "In speech settings, choose \"Speech Services by Google\", tap the settings cog, then " +
                            "Install voice data → English (Australia). Come back here and it'll be picked automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onGetVoices) { Text("Open speech settings") }
                }
            }
        }

        // 1. What to read
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What should I read?", style = MaterialTheme.typography.titleMedium)
                if (ui.picked == null) {
                    FilledTonalButton(onClick = onPickFile, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.ic_document), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add a document (PDF, Markdown, text)")
                    }
                    OutlinedTextField(
                        value = ui.input,
                        onValueChange = onInput,
                        enabled = !running,
                        label = { Text("…or paste an article link or some text") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_document), contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ui.picked.name,
                            Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(onClick = onClearFile, enabled = !running) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove document")
                        }
                    }
                }
            }
        }

        // 2. Voice
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Voice", style = MaterialTheme.typography.titleMedium)
                var open by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (!running) open = it }) {
                    OutlinedTextField(
                        value = ui.selectedVoice?.label ?: if (ui.engineReady) "No voice" else "Loading voices…",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        ui.voices.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.label) },
                                onClick = {
                                    onVoice(v.name)
                                    open = false
                                },
                            )
                        }
                    }
                }
                NaturalVoicesSection(ui.natural, running, onDownloadNatural, onCancelNatural, onRemoveNatural)
                SpeedControl(value = ui.rate, onChange = onRate, range = 0.5f..2.0f, enabled = !running)
                Text(
                    if (ui.naturalSelected) "Pitch (phone voices only)" else "Pitch  ${"%.2f".format(ui.pitch)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = ui.pitch,
                    onValueChange = onPitch,
                    valueRange = 0.6f..1.6f,
                    steps = 9,
                    enabled = !running && !ui.naturalSelected,
                )
                OutlinedButton(onClick = onPreview, enabled = ui.engineReady && !running && !ui.previewing) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (ui.previewing) "Preparing sample…" else "Hear a sample")
                }
            }
        }

        // 3. Progress / result
        when (job) {
            is JobState.Running -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(job.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(job.stage, style = MaterialTheme.typography.bodyMedium)
                    if (job.total > 0) {
                        LinearProgressIndicator(
                            progress = { job.done.toFloat() / job.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        "You can leave the app. I'll notify you when it's done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            is JobState.Finished -> Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Done: ${job.title}", style = MaterialTheme.typography.titleMedium)
                    if (job.skipped > 0) {
                        Text("${job.skipped} small part(s) couldn't be spoken and were skipped.", style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton(onClick = onOpenLibrary) { Text("Listen / save") }
                        TextButton(onClick = onDismiss) { Text("OK") }
                    }
                }
            }
            is JobState.Failed -> Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Couldn't make the MP3", style = MaterialTheme.typography.titleMedium)
                    Text(job.message, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
            JobState.Idle -> Unit
        }

        Button(
            onClick = onGo,
            enabled = ui.canGo && !running,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Go", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Download / progress / remove controls for the offline natural (Kokoro) voices. */
@Composable
private fun NaturalVoicesSection(
    state: KokoroModel.State,
    running: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val hint = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        KokoroModel.State.NotInstalled -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Want a more natural voice? Download the offline AI voices (about ${KokoroModel.DOWNLOAD_MB} MB, " +
                    "Wi-Fi recommended). British and American accents; there's no Australian one yet.",
                style = MaterialTheme.typography.bodySmall,
                color = hint,
            )
            FilledTonalButton(onClick = onDownload, enabled = !running) { Text("Download natural voices") }
        }
        is KokoroModel.State.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Downloading natural voices… ${state.done / 1_000_000} of ${state.total / 1_000_000} MB",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("You can leave the app; it keeps going.", style = MaterialTheme.typography.bodySmall, color = hint)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        is KokoroModel.State.Installing -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Setting up natural voices…", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
        }
        is KokoroModel.State.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(onClick = onDownload, enabled = !running) { Text("Try again") }
        }
        KokoroModel.State.Ready -> TextButton(onClick = onRemove, enabled = !running) {
            Text("Remove natural voices (frees about 370 MB)")
        }
    }
}
