package com.simpleaibrowser.sab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadSegmentState
import com.simpleaibrowser.sab.core.browser.DownloadTaskStatus
import com.simpleaibrowser.sab.core.browser.SabFileEntry
import com.simpleaibrowser.sab.core.browser.TorrentFileEntry
import com.simpleaibrowser.sab.data.DownloadEntity
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadTaskCard(
    item: DownloadEntity,
    isVerifyingSha: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onExportPublic: () -> Unit,
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onVerifySha: () -> Unit,
    onToggleTorrentSequential: () -> Unit,
    onToggleTorrentFile: (Int) -> Unit,
    onDelete: (deleteLocalFiles: Boolean) -> Unit
) {
    val sabColors = LocalSabColors.current
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val kind = remember(item.kind) { DownloadKind.fromId(item.kind) }
    val status = remember(item.status) { DownloadTaskStatus.fromId(item.status) }
    val torrentFiles = remember(item.torrentFilesJson) { TorrentFileEntry.fromJson(item.torrentFilesJson) }
    val segments = remember(item.segmentsJson) { DownloadSegmentState.fromJson(item.segmentsJson) }

    val statusColor = when (status) {
        DownloadTaskStatus.COMPLETED -> sabColors.shieldGreen
        DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.SEEDING -> MaterialTheme.colorScheme.primary
        DownloadTaskStatus.CONNECTING, DownloadTaskStatus.FETCHING_METADATA, DownloadTaskStatus.QUEUED ->
            MaterialTheme.colorScheme.secondary
        DownloadTaskStatus.WAITING_WIFI, DownloadTaskStatus.PAUSED -> Color(0xFFF59E0B)
        DownloadTaskStatus.FAILED, DownloadTaskStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }

    Surface(
        color = sabColors.chromeSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable {
                    if (status == DownloadTaskStatus.COMPLETED) onOpen() else expanded = !expanded
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(statusColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForKind(kind),
                        contentDescription = kind.label,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = status.label,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                        Text(
                            text = buildMetaLine(item, kind, status, segments.size),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = sabColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Primary control button
                when {
                    status.canPause -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    status.canResume -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = if (status == DownloadTaskStatus.FAILED) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    status == DownloadTaskStatus.COMPLETED -> {
                        IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open", tint = sabColors.shieldGreen)
                        }
                    }
                }

                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse details" else "Expand details",
                        tint = sabColors.subtleText
                    )
                }
            }

            if (status != DownloadTaskStatus.COMPLETED && status != DownloadTaskStatus.CANCELLED) {
                val fraction = if (item.sizeBytes > 0L) {
                    (item.downloadedBytes.toFloat() / item.sizeBytes.toFloat()).coerceIn(0f, 1f)
                } else -1f
                if (fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = statusColor,
                        trackColor = sabColors.chromeBar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else if (status.isActive) {
                    LinearProgressIndicator(
                        color = statusColor,
                        trackColor = sabColors.chromeBar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            if (!item.errorMessage.isNullOrBlank() && status == DownloadTaskStatus.FAILED) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                    Text(
                        text = item.url,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = sabColors.subtleText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (item.subfolder.isNotBlank() || item.publicUri != null) {
                        Text(
                            text = buildString {
                                append("Folder: ")
                                append(item.subfolder.ifBlank { "Root" })
                                if (item.publicUri != null) append(" • Exported to Public Downloads")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                    }

                    if (!item.sha256.isNullOrBlank()) {
                        Text(
                            text = "SHA-256: ${item.sha256}",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = sabColors.shieldGreen
                        )
                    }

                    if (kind.isTorrent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(sabColors.chromeBar)
                                .clickable(onClick = onToggleTorrentSequential)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sequential Piece Streaming", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = item.sequentialTorrent,
                                onCheckedChange = { onToggleTorrentSequential() }
                            )
                        }

                        if (torrentFiles.isNotEmpty()) {
                            Text(
                                text = "Torrent Files (${torrentFiles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = sabColors.subtleText
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(sabColors.chromeBar)
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                torrentFiles.take(25).forEach { tf ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggleTorrentFile(tf.index) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = tf.isSelected,
                                            onCheckedChange = { onToggleTorrentFile(tf.index) },
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tf.path.substringAfterLast('/'),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatTransferBytes(tf.downloadedBytes)} / ${formatTransferBytes(tf.sizeBytes)}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 9.sp,
                                                color = sabColors.subtleText
                                            )
                                        }
                                    }
                                }
                                if (torrentFiles.size > 25) {
                                    Text(
                                        text = "+ ${torrentFiles.size - 25} more files in torrent",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (status.canPause || status == DownloadTaskStatus.PAUSED || status == DownloadTaskStatus.WAITING_WIFI) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel", maxLines = 1)
                            }
                        }
                        if (item.stagingPath.isNotBlank() && status == DownloadTaskStatus.COMPLETED) {
                            OutlinedButton(onClick = onExportPublic, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export", maxLines = 1)
                            }
                        }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCopyUrl) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Link")
                        }
                        if (status == DownloadTaskStatus.COMPLETED && item.stagingPath.isNotBlank()) {
                            TextButton(onClick = onVerifySha, enabled = !isVerifyingSha) {
                                if (isVerifyingSha) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SHA-256")
                            }
                        }
                        TextButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        var deleteLocal by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove download?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove \"${item.fileName}\" from Download Manager?")
                    if (item.stagingPath.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deleteLocal = !deleteLocal }
                        ) {
                            Checkbox(checked = deleteLocal, onCheckedChange = { deleteLocal = it })
                            Text("Also delete local file(s) from SAB Vault", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(deleteLocal)
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun FileExplorerTab(
    currentPath: String,
    entries: List<SabFileEntry>,
    storageSummary: Pair<Long, Long>,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onPickTorrent: () -> Unit,
    onOpenEntry: (SabFileEntry) -> Unit,
    onExportEntry: (SabFileEntry) -> Unit,
    onShareEntry: (SabFileEntry) -> Unit,
    onRenameEntry: (SabFileEntry, String) -> Unit,
    onDeleteEntry: (SabFileEntry) -> Unit
) {
    val sabColors = LocalSabColors.current
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renamingEntry by remember { mutableStateOf<SabFileEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Storage bar & actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(sabColors.chromeSurfaceElevated)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentPath.isBlank()) "SAB Vault /" else "SAB Vault / $currentPath",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${formatTransferBytes(storageSummary.first)} used • ${formatTransferBytes(storageSummary.second)} free",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = sabColors.subtleText
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentPath.isNotBlank()) {
                    OutlinedButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Up")
                    }
                }
                OutlinedButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Folder", maxLines = 1)
                }
                OutlinedButton(onClick = onPickTorrent, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open .torrent", maxLines = 1)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = sabColors.subtleText, modifier = Modifier.size(42.dp))
                    Text("This folder is empty", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Files & folders managed by SAB Download Manager appear here and can be organized, renamed, shared, or exported to Android Downloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(entries, key = { it.absolutePath }) { entry ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val dateLabel = remember(entry.modifiedAt) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.modifiedAt))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(sabColors.chromeSurfaceElevated)
                            .clickable { onOpenEntry(entry) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    (if (entry.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                                        .copy(alpha = 0.16f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Filled.Folder else iconForKind(entry.kind),
                                contentDescription = null,
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (entry.isDirectory) {
                                    "${entry.childCount} item(s) • ${formatTransferBytes(entry.sizeBytes)} • $dateLabel"
                                } else {
                                    "${formatTransferBytes(entry.sizeBytes)} • $dateLabel"
                                },
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = sabColors.subtleText
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "File options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (entry.isDirectory) "Open Folder" else "Open") },
                                    leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                                    onClick = { menuOpen = false; onOpenEntry(entry) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export to Public Downloads") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = { menuOpen = false; onExportEntry(entry) }
                                )
                                if (!entry.isDirectory) {
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                        onClick = { menuOpen = false; onShareEntry(entry) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = { menuOpen = false; renamingEntry = entry }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; onDeleteEntry(entry) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateFolder(name)
                            showNewFolderDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    renamingEntry?.let { target ->
        var nextName by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingEntry = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = nextName,
                    onValueChange = { nextName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nextName.isNotBlank()) {
                            onRenameEntry(target, nextName)
                            renamingEntry = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingEntry = null }) { Text("Cancel") }
            }
        )
    }
}

private fun iconForKind(kind: DownloadKind): ImageVector = when (kind) {
    DownloadKind.VIDEO -> Icons.Filled.Movie
    DownloadKind.AUDIO -> Icons.Filled.Audiotrack
    DownloadKind.IMAGE -> Icons.Filled.Image
    DownloadKind.DOCUMENT -> Icons.Filled.Description
    DownloadKind.ARCHIVE -> Icons.Filled.FolderZip
    DownloadKind.APPLICATION -> Icons.Filled.Android
    DownloadKind.TORRENT, DownloadKind.MAGNET -> Icons.Filled.Link
    DownloadKind.FILE -> Icons.Filled.InsertDriveFile
}

private fun buildMetaLine(
    item: DownloadEntity,
    kind: DownloadKind,
    status: DownloadTaskStatus,
    segmentCount: Int
): String = buildString {
    if (status == DownloadTaskStatus.COMPLETED) {
        append(formatTransferBytes(item.sizeBytes.coerceAtLeast(item.downloadedBytes)))
        if (item.completedAt > 0L) {
            append(" • ")
            append(SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.completedAt)))
        }
        return@buildString
    }
    append(formatTransferBytes(item.downloadedBytes))
    if (item.sizeBytes > 0L) {
        append(" / ")
        append(formatTransferBytes(item.sizeBytes))
    }
    if (item.speedBytesPerSec > 0L) {
        append(" • ")
        append(formatSpeed(item.speedBytesPerSec))
    }
    if (item.etaSeconds > 0L && status == DownloadTaskStatus.DOWNLOADING) {
        append(" • ")
        append(formatEta(item.etaSeconds))
    }
    if (kind.isTorrent) {
        append(" • S:")
        append(item.torrentSeeds)
        append(" P:")
        append(item.torrentPeers)
    } else if (segmentCount > 1 && item.supportsRange) {
        append(" • ${segmentCount}x")
    }
}

fun formatTransferBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB/s", kb)
    return String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
}

private fun formatEta(seconds: Long): String {
    if (seconds < 60L) return "${seconds}s"
    val mins = seconds / 60L
    val secs = seconds % 60L
    if (mins < 60L) return String.format(Locale.US, "%dm %02ds", mins, secs)
    return String.format(Locale.US, "%dh %02dm", mins / 60L, mins % 60L)
}
