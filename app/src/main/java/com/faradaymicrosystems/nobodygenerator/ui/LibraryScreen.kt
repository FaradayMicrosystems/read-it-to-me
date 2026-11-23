package com.faradaymicrosystems.nobodygenerator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.faradaymicrosystems.nobodygenerator.data.Recording

@Composable
fun LibraryScreen(
    recordings: List<Recording>,
    player: PlayerState,
    onPlay: (Recording) -> Unit,
    onSeek: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onSave: (Recording) -> Unit,
    onShare: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf<Recording?>(null) }

    Column(modifier.fillMaxSize()) {
        Text("Your recordings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        if (recordings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing yet. Add a document on the Create tab and hit Go.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recordings, key = { it.file.absolutePath }) { rec ->
                    val isCurrent = player.file == rec.file
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = if (isCurrent) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                            Text(rec.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatTime(rec.durationMs.toInt())} · ${formatSize(rec.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onPlay(rec) }) {
                                    Icon(
                                        painterResource(if (isCurrent && player.playing) R.drawable.ic_pause else R.drawable.ic_play),
                                        contentDescription = if (isCurrent && player.playing) "Pause" else "Play",
                                    )
                                }
                                IconButton(onClick = { onSave(rec) }) {
                                    Icon(painterResource(R.drawable.ic_save), contentDescription = "Save MP3")
                                }
                                IconButton(onClick = { onShare(rec) }) {
                                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share")
                                }
                                IconButton(onClick = { confirmDelete = rec }) {
                                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (player.file != null) {
            HorizontalDivider()
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(player.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Slider(
                        value = player.positionMs.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..player.durationMs.coerceAtLeast(1).toFloat(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatTime(player.positionMs)} / ${formatTime(player.durationMs)}", style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.weight(1f))
                        TextButton(onClick = { onSkip(-15_000) }) { Text("−15s") }
                        IconButton(onClick = {
                            recordings.firstOrNull { it.file == player.file }?.let(onPlay)
                        }) {
                            Icon(
                                painterResource(if (player.playing) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (player.playing) "Pause" else "Play",
                            )
                        }
                        TextButton(onClick = { onSkip(30_000) }) { Text("+30s") }
                    }
                    SpeedControl(value = player.speed, onChange = onSpeed, range = 0.5f..3.0f)
                }
            }
        }
    }

    confirmDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${rec.title}\" will be removed from the app. Copies you've saved elsewhere aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(rec)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "${(bytes / 1000).coerceAtLeast(1)} KB"
}
