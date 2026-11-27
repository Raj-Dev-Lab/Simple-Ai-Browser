package com.simpleaibrowser.sab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.StorageLocationMode
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsSheet(
    settings: DownloadManagerSettings,
    onUpdateSettings: ((DownloadManagerSettings) -> DownloadManagerSettings) -> Unit,
    onPickCustomFolder: () -> Unit,
    onClearCustomFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var userAgentDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sabColors.chromeBar,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SettingsSuggest,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Download Manager Settings", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close settings")
                }
            }

            // Storage & Destination Section
            SettingsCardBlock(title = "STORAGE & FILE SYSTEM") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageLocationMode.entries.forEach { mode ->
                        val selected = settings.storageLocationMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else sabColors.chromeBar
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (mode == StorageLocationMode.CUSTOM_TREE && settings.customTreeUri.isNullOrBlank()) {
                                        onPickCustomFolder()
                                    } else {
                                        onUpdateSettings { it.copy(storageLocationMode = mode) }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.title, style = MaterialTheme.typography.titleSmall)
                                val sub = if (mode == StorageLocationMode.CUSTOM_TREE && !settings.customTreeLabel.isNullOrBlank()) {
                                    "Active folder: ${settings.customTreeLabel}"
                                } else {
                                    mode.subtitle
                                }
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                            }
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPickCustomFolder,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dl_pick_folder_btn")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Choose Folder")
                        }
                        if (!settings.customTreeUri.isNullOrBlank()) {
                            TextButton(onClick = onClearCustomFolder) {
                                Text("Reset Default")
                            }
                        }
                    }

                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                    DownloadSwitchRow(
                        title = "Auto-Organize into Type Folders",
                        subtitle = "Sort into Media, Documents, Archives, Apps, and Torrents subfolders",
                        checked = settings.autoOrganizeByType,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(autoOrganizeByType = enabled) }
                        }
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Ask Before Starting Downloads",
                        subtitle = "Confirm filename, folder, thread count, and Wi-Fi options per download",
                        checked = settings.askBeforeDownloading,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(askBeforeDownloading = enabled) }
                        }
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Completion Notifications",
                        subtitle = "Alert in notification shade when files or torrents finish",
                        checked = settings.notifyOnComplete,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(notifyOnComplete = enabled) }
                        }
                    )
                }
            }

            // Speed, Threads & Network
            SettingsCardBlock(title = "HTTP / MEDIA ENGINE & NETWORK") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipOptionGroup(
                        title = "Threads per Download (Multi-Part)",
                        valueLabel = "${settings.defaultConnectionsPerDownload}x",
                        options = listOf(1, 2, 4, 8, 16),
                        selected = settings.defaultConnectionsPerDownload,
                        labelFor = { "${it}x" },
                        onSelect = { count ->
                            onUpdateSettings { it.copy(defaultConnectionsPerDownload = count) }
                        }
                    )

                    ChipOptionGroup(
                        title = "Max Simultaneous Downloads",
                        valueLabel = "${settings.maxConcurrentDownloads}",
                        options = listOf(1, 2, 3, 4, 6),
                        selected = settings.maxConcurrentDownloads,
                        labelFor = { "$it" },
                        onSelect = { max ->
                            onUpdateSettings { it.copy(maxConcurrentDownloads = max) }
                        }
                    )

                    ChipOptionGroup(
                        title = "HTTP Bandwidth Speed Limit",
                        valueLabel = formatKbpsLimit(settings.speedLimitKbps),
                        options = listOf(0, 256, 512, 1024, 2048, 5120, 10240),
                        selected = settings.speedLimitKbps,
                        labelFor = ::formatKbpsLimit,
                        onSelect = { kbps ->
                            onUpdateSettings { it.copy(speedLimitKbps = kbps) }
                        }
                    )

                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Wi-Fi Only Downloads",
                        subtitle = "Pause downloads automatically on metered mobile data",
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(wifiOnlyDownloads = enabled) }
                        }
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Auto-Resume on Network Reconnect",
                        subtitle = "Resume paused Wi-Fi-waiting tasks when unmetered Wi-Fi returns",
                        checked = settings.autoResumeOnReconnect,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(autoResumeOnReconnect = enabled) }
                        }
                    )

                    OutlinedTextField(
                        value = userAgentDraft,
                        onValueChange = { userAgentDraft = it },
                        label = { Text("Custom Downloader User-Agent (Optional)") },
                        placeholder = { Text("Leave blank for default OkHttp/SAB agent") },
                        singleLine = true,
                        trailingIcon = {
                            if (userAgentDraft != settings.customUserAgent) {
                                TextButton(
                                    onClick = {
                                        onUpdateSettings { it.copy(customUserAgent = userAgentDraft.trim()) }
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // BitTorrent Engine Settings
            SettingsCardBlock(title = "BITTORRENT & MAGNET ENGINE") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DownloadSwitchRow(
                        title = "Enable DHT & Local Peer Discovery",
                        subtitle = "Discover magnet peers without relying solely on central trackers",
                        checked = settings.torrentEnableDht,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(torrentEnableDht = enabled) }
                        }
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Sequential Download by Default",
                        subtitle = "Prioritize first-to-last pieces for streaming media playback",
                        checked = settings.torrentSequentialDefault,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(torrentSequentialDefault = enabled) }
                        }
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    DownloadSwitchRow(
                        title = "Keep Seeding After Completion",
                        subtitle = "Share completed pieces with the swarm instead of auto-pausing",
                        checked = settings.torrentSeedAfterComplete,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(torrentSeedAfterComplete = enabled) }
                        }
                    )

                    ChipOptionGroup(
                        title = "Max Peer Connections",
                        valueLabel = "${settings.torrentMaxConnections}",
                        options = listOf(50, 100, 200, 350, 500),
                        selected = settings.torrentMaxConnections,
                        labelFor = { "$it" },
                        onSelect = { max ->
                            onUpdateSettings { it.copy(torrentMaxConnections = max) }
                        }
                    )

                    ChipOptionGroup(
                        title = "Torrent Download Limit",
                        valueLabel = formatKbpsLimit(settings.torrentDownloadLimitKbps),
                        options = listOf(0, 512, 1024, 3072, 5120, 10240),
                        selected = settings.torrentDownloadLimitKbps,
                        labelFor = ::formatKbpsLimit,
                        onSelect = { kbps ->
                            onUpdateSettings { it.copy(torrentDownloadLimitKbps = kbps) }
                        }
                    )

                    ChipOptionGroup(
                        title = "Torrent Upload Limit",
                        valueLabel = formatKbpsLimit(settings.torrentUploadLimitKbps),
                        options = listOf(64, 128, 256, 512, 1024, 0),
                        selected = settings.torrentUploadLimitKbps,
                        labelFor = ::formatKbpsLimit,
                        onSelect = { kbps ->
                            onUpdateSettings { it.copy(torrentUploadLimitKbps = kbps) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun formatKbpsLimit(kbps: Int): String {
    if (kbps <= 0) return "Unlimited"
    if (kbps < 1024) return "$kbps KB/s"
    return "${kbps / 1024} MB/s"
}

@Composable
private fun SettingsCardBlock(
    title: String,
    content: @Composable () -> Unit
) {
    val sabColors = LocalSabColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = sabColors.subtleText
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(sabColors.chromeSurfaceElevated)
                .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DownloadSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalSabColors.current.subtleText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChipOptionGroup(
    title: String,
    valueLabel: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    val sabColors = LocalSabColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = valueLabel,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else sabColors.chromeBar
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelFor(option),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
