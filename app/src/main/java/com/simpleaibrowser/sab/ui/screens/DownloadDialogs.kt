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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.DetectedPageMedia
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadSourceType
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.viewmodel.DownloadAddDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrConfirmDownloadSheet(
    draft: DownloadAddDraft,
    onPickTorrentFile: () -> Unit,
    onConfirm: (DownloadAddDraft) -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var url by remember(draft) { mutableStateOf(draft.url) }
    var fileName by remember(draft) { mutableStateOf(draft.fileName) }
    var subfolder by remember(draft) { mutableStateOf(draft.subfolder) }
    var threads by remember(draft) { mutableIntStateOf(draft.connectionsCount.coerceIn(1, 16)) }
    var wifiOnly by remember(draft) { mutableStateOf(draft.wifiOnly) }
    var sequential by remember(draft) { mutableStateOf(draft.sequentialTorrent) }

    val inferredKind = remember(url, fileName, draft.mimeType) {
        DownloadKind.infer(fileName, draft.mimeType, url)
    }
    val isTorrent = inferredKind.isTorrent ||
        draft.sourceType == DownloadSourceType.MAGNET ||
        draft.sourceType == DownloadSourceType.TORRENT_FILE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sabColors.chromeBar,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (draft.url.isBlank()) "Add Download or Torrent" else "Confirm Download",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { next ->
                    url = next
                    if (fileName.isBlank() || fileName == draft.fileName) {
                        fileName = UrlResolver.guessFileName(next, draft.mimeType)
                    }
                },
                label = { Text("URL or Magnet Link") },
                placeholder = { Text("https://... or magnet:?xt=urn:btih:...") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_download_url_input")
            )

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onPickTorrentFile()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_torrent_file_btn")
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Select Local .torrent File")
            }

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Save File / Torrent As") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Subfolder inside SAB Downloads", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("", "Media", "Documents", "Archives", "Apps", "Torrents", "Files").forEach { folder ->
                        val active = subfolder == folder
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else sabColors.chromeSurfaceElevated
                                )
                                .border(
                                    1.dp,
                                    if (active) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { subfolder = folder }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = folder.ifBlank { "Root" },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!isTorrent && draft.sourceType != DownloadSourceType.OPEN_STREAM) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Parallel Connections", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${threads}x threads",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 4, 8, 16).forEach { count ->
                            val selected = threads == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
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
                                    .clickable { threads = count }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${count}x",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (isTorrent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(sabColors.chromeSurfaceElevated)
                        .clickable { sequential = !sequential }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sequential Piece Order", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Download media pieces in order so playback starts earlier",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                    }
                    Switch(checked = sequential, onCheckedChange = { sequential = it })
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .clickable { wifiOnly = !wifiOnly }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi Only", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hold on metered mobile data and auto-resume on Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            }

            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    if (cleanUrl.isNotBlank() || !draft.stagedCacheFilePath.isNullOrBlank()) {
                        onConfirm(
                            draft.copy(
                                url = cleanUrl,
                                fileName = fileName.trim(),
                                subfolder = subfolder.trim(),
                                connectionsCount = threads,
                                wifiOnly = wifiOnly,
                                sequentialTorrent = sequential
                            )
                        )
                    }
                },
                enabled = url.isNotBlank() || !draft.stagedCacheFilePath.isNullOrBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("confirm_start_download_btn")
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (isTorrent) "Start Torrent" else "Start Download")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageMediaGrabberSheet(
    pageTitle: String,
    items: List<DetectedPageMedia>,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onPlayOrViewItem: ((DetectedPageMedia) -> Unit)? = null,
    onDownloadItem: (DetectedPageMedia) -> Unit,
    onDownloadAll: (List<DetectedPageMedia>) -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kindFilter by remember { mutableStateOf<String>("all") }

    val filtered = remember(items, kindFilter) {
        when (kindFilter) {
            "video" -> items.filter { it.kind == DownloadKind.VIDEO }
            "audio" -> items.filter { it.kind == DownloadKind.AUDIO }
            "image" -> items.filter { it.kind == DownloadKind.IMAGE }
            "torrent" -> items.filter { it.kind.isTorrent }
            "files" -> items.filter { !it.kind.isMedia && !it.kind.isTorrent }
            else -> items
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sabColors.chromeBar,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Page Media & Links", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = pageTitle.ifBlank { "Active Tab" },
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRescan, enabled = !isScanning) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Rescan page")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "all" to "All (${items.size})",
                    "video" to "Video (${items.count { it.kind == DownloadKind.VIDEO }})",
                    "audio" to "Audio (${items.count { it.kind == DownloadKind.AUDIO }})",
                    "image" to "Images (${items.count { it.kind == DownloadKind.IMAGE }})",
                    "torrent" to "Torrents (${items.count { it.kind.isTorrent }})",
                    "files" to "Docs/Files (${items.count { !it.kind.isMedia && !it.kind.isTorrent }})"
                ).forEach { (key, label) ->
                    val selected = kindFilter == key
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
                            .clickable { kindFilter = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else sabColors.subtleText
                        )
                    }
                }
            }

            when {
                isScanning -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(12.dp))
                        Text("Scanning page for videos, audio, images, and torrents...")
                    }
                }
                filtered.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No downloadable media detected here", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Start playback on HTML5 videos or scroll to load embedded media, then tap Refresh.",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                    }
                }
                else -> {
                    if (filtered.size > 1) {
                        OutlinedButton(
                            onClick = { onDownloadAll(filtered) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Download All Filtered (${filtered.size})")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.url }) { media ->
                            val icon = when (media.kind) {
                                DownloadKind.VIDEO -> Icons.Filled.Movie
                                DownloadKind.AUDIO -> Icons.Filled.Audiotrack
                                DownloadKind.IMAGE -> Icons.Filled.Image
                                DownloadKind.TORRENT, DownloadKind.MAGNET -> Icons.Filled.Link
                                else -> Icons.Filled.InsertDriveFile
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(sabColors.chromeSurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = media.kind.label,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = media.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${media.kind.label} • ${media.url}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val isPdf = UrlResolver.isPdfTarget(media.url, media.mimeType)
                                val (isPlayable, _) = UrlResolver.classifyPlayableMedia(media.url, media.mimeType)
                                if (onPlayOrViewItem != null && (isPdf || isPlayable)) {
                                    IconButton(
                                        onClick = {
                                            onDismiss()
                                            onPlayOrViewItem(media)
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.PlayCircleOutline,
                                            contentDescription = if (isPdf) "Open ${media.title} in PDF viewer" else "Play ${media.title}",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDownloadItem(media) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Download ${media.title}",
                                        tint = sabColors.shieldGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
