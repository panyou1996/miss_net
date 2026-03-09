package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panyou.missnet.nativeapp.core.model.DownloadRecord
import com.panyou.missnet.nativeapp.core.model.DownloadStatus
import com.panyou.missnet.nativeapp.core.model.Video
import com.panyou.missnet.nativeapp.core.util.toPercentLabel
import com.panyou.missnet.nativeapp.ui.components.EmptyState
import com.panyou.missnet.nativeapp.ui.components.GlassCard
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onOpenVideo: (Video) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.items.isEmpty()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            HeaderRow(title = "Downloads", subtitle = "Offline-ready Media3 downloads live here.")
            EmptyState(title = "No downloads yet", body = "Start a download from the player page.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderRow(title = "Downloads", subtitle = "Offline-ready Media3 downloads live here.")
        }
        items(state.items) { item ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(item.video.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.status.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                        )
                    }
                    IconButton(onClick = { viewModel.remove(item.video.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove download")
                    }
                }
                LinearProgressIndicator(
                    progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = progressLabel(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                )
                if (item.status == DownloadStatus.Completed) {
                    androidx.compose.material3.TextButton(
                        onClick = { onOpenVideo(item.video.copy(isOfflineReady = true)) },
                    ) {
                        Text("Play offline")
                    }
                }
            }
        }
    }
}

private fun progressLabel(item: DownloadRecord): String {
    return when (item.status) {
        DownloadStatus.Completed -> "Ready offline"
        DownloadStatus.Failed -> item.failureReason ?: "Failed"
        else -> item.progressPercent.toPercentLabel()
    }
}
