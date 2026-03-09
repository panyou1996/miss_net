package com.panyou.missnet.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.panyou.missnet.nativeapp.BuildConfig
import com.panyou.missnet.nativeapp.core.util.toStorageLabel
import com.panyou.missnet.nativeapp.ui.components.GlassCard
import com.panyou.missnet.nativeapp.ui.components.HeaderRow
import com.panyou.missnet.nativeapp.ui.components.OverlineLabel
import com.panyou.missnet.nativeapp.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeaderRow(
                title = "Settings",
                subtitle = "Meaningful toggles and storage controls for the native app.",
            )
        }
        item {
            GlassCard {
                OverlineLabel("playback")
                SettingToggle("Dynamic color", state.settings.useDynamicColor, viewModel::setDynamicColor)
                SettingToggle("Autoplay related video", state.settings.autoplayRelated, viewModel::setAutoplayRelated)
                SettingToggle("Keep screen awake in player", state.settings.keepScreenAwakeInPlayer, viewModel::setKeepScreenAwake)
            }
        }
        item {
            GlassCard {
                OverlineLabel("privacy")
                SettingToggle("Incognito mode", state.settings.incognitoMode, viewModel::setIncognito)
                SettingToggle("Wi-Fi only downloads", state.settings.preferWifiDownloads, viewModel::setWifiOnlyDownloads)
            }
        }
        item {
            GlassCard {
                OverlineLabel("storage")
                StorageRow("Favorites stored", state.favoriteCount.toString())
                StorageRow("History entries", state.historyCount.toString())
                StorageRow("Downloads", state.downloadCount.toString())
                StorageRow("Offline storage", state.offlineStorageBytes.toStorageLabel())
                androidx.compose.material3.TextButton(onClick = viewModel::clearHistory) { Text("Clear history") }
                androidx.compose.material3.TextButton(onClick = viewModel::clearSearchHistory) { Text("Clear search history") }
                androidx.compose.material3.TextButton(onClick = viewModel::clearDownloads) { Text("Remove all downloads") }
                androidx.compose.material3.TextButton(
                    onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            context.imageLoader.diskCache?.clear()
                            context.imageLoader.memoryCache?.clear()
                        }
                    },
                ) { Text("Clear image cache") }
            }
        }
        item {
            GlassCard {
                OverlineLabel("about")
                StorageRow("Build", "0.1.0")
                StorageRow("Repo", "panyou1996/miss_net")
                androidx.compose.material3.TextButton(onClick = { uriHandler.openUri(BuildConfig.GITHUB_REPO_URL) }) {
                    Text("Open repository")
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StorageRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
        )
    }
}
