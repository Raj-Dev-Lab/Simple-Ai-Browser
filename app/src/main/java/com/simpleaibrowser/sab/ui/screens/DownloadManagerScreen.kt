package com.simpleaibrowser.sab.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.DetectedPageMedia
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadManagerSettings
import com.simpleaibrowser.sab.core.browser.DownloadTaskStatus
import com.simpleaibrowser.sab.core.browser.SabFileEntry
import com.simpleaibrowser.sab.data.DownloadEntity
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.viewmodel.DownloadManagerTab

private enum class StatusFilterChip(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    PAUSED("Paused"),
    DONE("Completed"),
    FAILED("Failed")
}

@Composable
fun DownloadManagerScreen(
    initialTab: DownloadManagerTab,
    downloads: List<DownloadEntity>,
    downloadSettings: DownloadManagerSettings,
    currentExplorerPath: String,
    explorerEntries: List<SabFileEntry>,
    storageSummary: Pair<Long, Long>,
    verifyingShaTaskId: String?,
    canScanActivePage: Boolean,
    activePageTitle: String,
    detectedPageMedia: List<DetectedPageMedia>,
    isScanningMedia: Boolean,
    onAddClick: () -> Unit,
    onPickTorrentFile: () -> Unit,
    onScanPageMedia: () -> Unit,
    onPlayOrViewMedia: ((DetectedPageMedia) -> Unit)? = null,
    onDownloadDetectedMedia: (DetectedPageMedia) -> Unit,
    onDownloadAllMedia: (List<DetectedPageMedia>) -> Unit,
    onClearDetectedMedia: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearFinished: () -> Unit,
    onPauseTask: (DownloadEntity) -> Unit,
    onResumeTask: (DownloadEntity) -> Unit,
    onCancelTask: (DownloadEntity) -> Unit,
    onOpenTask: (DownloadEntity) -> Unit,
    onExportTask: (DownloadEntity) -> Unit,
    onShareTask: (DownloadEntity) -> Unit,
    onCopyTaskUrl: (DownloadEntity) -> Unit,
    onVerifySha: (DownloadEntity) -> Unit,
    onToggleTorrentSequential: (DownloadEntity) -> Unit,
    onToggleTorrentFile: (DownloadEntity, Int) -> Unit,
    onDeleteTask: (DownloadEntity, Boolean) -> Unit,
    onNavigateExplorerUp: () -> Unit,
    onCreateExplorerFolder: (String) -> Unit,
    onOpenExplorerEntry: (SabFileEntry) -> Unit,
    onExportExplorerEntry: (SabFileEntry) -> Unit,
    onShareExplorerEntry: (SabFileEntry) -> Unit,
    onRenameExplorerEntry: (SabFileEntry, String) -> Unit,
    onDeleteExplorerEntry: (SabFileEntry) -> Unit,
    onUpdateDownloadSettings: ((DownloadManagerSettings) -> DownloadManagerSettings) -> Unit,
    onPickCustomFolder: () -> Unit,
    onClearCustomFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(StatusFilterChip.ALL) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }

    val activeItems = remember(downloads) {
        downloads.filter { DownloadTaskStatus.fromId(it.status).isActive }
    }
    val totalSpeed = remember(activeItems) { activeItems.sumOf { it.speedBytesPerSec } }
    val torrentCount = remember(downloads) {
        downloads.count { DownloadKind.fromId(it.kind).isTorrent }
    }
    val mediaCount = remember(downloads) {
        downloads.count { DownloadKind.fromId(it.kind).isMedia }
    }

    val filteredDownloads = remember(downloads, activeTab, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        downloads.filter { item ->
            val kind = DownloadKind.fromId(item.kind)
            val status = DownloadTaskStatus.fromId(item.status)
            val matchesTab = when (activeTab) {
                DownloadManagerTab.ALL -> true
                DownloadManagerTab.TORRENTS -> kind.isTorrent
                DownloadManagerTab.MEDIA -> kind.isMedia
                DownloadManagerTab.FILES -> false
            }
            val matchesQuery = q.isEmpty() ||
                item.fileName.lowercase().contains(q) ||
                item.url.lowercase().contains(q)
            val matchesStatus = when (statusFilter) {
                StatusFilterChip.ALL -> true
                StatusFilterChip.ACTIVE -> status.isActive
                StatusFilterChip.PAUSED -> status == DownloadTaskStatus.PAUSED || status == DownloadTaskStatus.WAITING_WIFI
                StatusFilterChip.DONE -> status == DownloadTaskStatus.COMPLETED
                StatusFilterChip.FAILED -> status == DownloadTaskStatus.FAILED || status == DownloadTaskStatus.CANCELLED
            }
            matchesTab && matchesQuery && matchesStatus
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dl_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Download Manager",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusSub = if (activeItems.isNotEmpty()) {
                            "${activeItems.size} active • ${formatSpeed(totalSpeed)}"
                        } else {
                            "${downloads.size} item(s) • ${downloadSettings.defaultConnectionsPerDownload}x threads"
                        }
                        Text(
                            text = statusSub,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = if (activeItems.isNotEmpty()) sabColors.shieldGreen else sabColors.subtleText
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canScanActivePage) {
                        IconButton(
                            onClick = {
                                onScanPageMedia()
                                showMediaSheet = true
                            },
                            modifier = Modifier.testTag("dl_scan_media_btn")
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = "Grab page media")
                        }
                    }
                    if (activeItems.isNotEmpty()) {
                        IconButton(onClick = onPauseAll) {
                            Icon(Icons.Filled.PauseCircleOutline, contentDescription = "Pause all")
                        }
                    } else if (downloads.any { DownloadTaskStatus.fromId(it.status).canResume }) {
                        IconButton(onClick = onResumeAll) {
                            Icon(Icons.Filled.PlayCircleOutline, contentDescription = "Resume all")
                        }
                    }
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.testTag("dl_add_btn")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add download or magnet")
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("dl_settings_btn")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Download settings")
                    }
                }
            }

            // 4 Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DownloadTabButton("All (${downloads.size})", activeTab == DownloadManagerTab.ALL, Modifier.weight(1f)) {
                    activeTab = DownloadManagerTab.ALL
                }
                DownloadTabButton("Torrents ($torrentCount)", activeTab == DownloadManagerTab.TORRENTS, Modifier.weight(1f)) {
                    activeTab = DownloadManagerTab.TORRENTS
                }
                DownloadTabButton("Media ($mediaCount)", activeTab == DownloadManagerTab.MEDIA, Modifier.weight(1f)) {
                    activeTab = DownloadManagerTab.MEDIA
                }
                DownloadTabButton("Files (${explorerEntries.size})", activeTab == DownloadManagerTab.FILES, Modifier.weight(1f)) {
                    activeTab = DownloadManagerTab.FILES
                }
            }

            if (activeTab == DownloadManagerTab.FILES) {
                FileExplorerTab(
                    currentPath = currentExplorerPath,
                    entries = explorerEntries,
                    storageSummary = storageSummary,
                    onNavigateUp = onNavigateExplorerUp,
                    onCreateFolder = onCreateExplorerFolder,
                    onPickTorrent = onPickTorrentFile,
                    onOpenEntry = onOpenExplorerEntry,
                    onExportEntry = onExportExplorerEntry,
                    onShareEntry = onShareExplorerEntry,
                    onRenameEntry = onRenameExplorerEntry,
                    onDeleteEntry = onDeleteExplorerEntry
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search downloads, magnets, torrents...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusFilterChip.entries.forEach { chip ->
                        val selected = statusFilter == chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else sabColors.chromeSurfaceElevated
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { statusFilter = chip }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else sabColors.subtleText
                            )
                        }
                    }

                    if (downloads.any {
                            val st = DownloadTaskStatus.fromId(it.status)
                            st == DownloadTaskStatus.COMPLETED || st == DownloadTaskStatus.FAILED || st == DownloadTaskStatus.CANCELLED
                        }
                    ) {
                        TextButton(onClick = onClearFinished) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Clear Done", fontSize = 12.sp)
                        }
                    }
                }

                if (filteredDownloads.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = sabColors.subtleText,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = when (activeTab) {
                                    DownloadManagerTab.TORRENTS -> "No torrents or magnet links"
                                    DownloadManagerTab.MEDIA -> "No downloaded media yet"
                                    else -> "No downloads match filter"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap + to add any file URL, magnet:? link, or .torrent file, or scan embedded page media from any open website.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredDownloads, key = { it.id }) { item ->
                            DownloadTaskCard(
                                item = item,
                                isVerifyingSha = verifyingShaTaskId == item.taskId,
                                onPause = { onPauseTask(item) },
                                onResume = { onResumeTask(item) },
                                onCancel = { onCancelTask(item) },
                                onOpen = {
                                    val st = item.stagingPath
                                    if (st.isNotBlank() && java.io.File(st).isDirectory) {
                                        activeTab = DownloadManagerTab.FILES
                                    }
                                    onOpenTask(item)
                                },
                                onExportPublic = { onExportTask(item) },
                                onShare = { onShareTask(item) },
                                onCopyUrl = { onCopyTaskUrl(item) },
                                onVerifySha = { onVerifySha(item) },
                                onToggleTorrentSequential = { onToggleTorrentSequential(item) },
                                onToggleTorrentFile = { idx -> onToggleTorrentFile(item, idx) },
                                onDelete = { delFiles -> onDeleteTask(item, delFiles) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        DownloadSettingsSheet(
            settings = downloadSettings,
            onUpdateSettings = onUpdateDownloadSettings,
            onPickCustomFolder = onPickCustomFolder,
            onClearCustomFolder = onClearCustomFolder,
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showMediaSheet) {
        PageMediaGrabberSheet(
            pageTitle = activePageTitle,
            items = detectedPageMedia,
            isScanning = isScanningMedia,
            onRescan = onScanPageMedia,
            onPlayOrViewItem = { media ->
                showMediaSheet = false
                onPlayOrViewMedia?.invoke(media)
            },
            onDownloadItem = { media ->
                onDownloadDetectedMedia(media)
            },
            onDownloadAll = { list ->
                onDownloadAllMedia(list)
                showMediaSheet = false
            },
            onDismiss = {
                showMediaSheet = false
                onClearDetectedMedia()
            }
        )
    }
}

@Composable
private fun DownloadTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalSabColors.current.subtleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
