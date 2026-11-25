package com.simpleaibrowser.sab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simpleaibrowser.sab.core.browser.ContextMenuTarget
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperaMenuBottomSheet(
    activeTab: TabUiState,
    isBookmarked: Boolean,
    settings: BrowserSettings,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onFindInPage: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onPinToSpeedDial: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleForceDark: () -> Unit,
    onToggleReader: () -> Unit,
    onAdjustTextZoom: (Int) -> Unit,
    onResetTextZoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onReplaySplash: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top 5-Icon Opera Quick Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MenuQuickIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "Forward",
                    enabled = activeTab.canGoForward,
                    onClick = { onForward(); onDismiss() }
                )
                MenuQuickIcon(
                    icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    label = if (isBookmarked) "Saved" else "Bookmark",
                    enabled = !activeTab.isStartPage,
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else null,
                    onClick = { onToggleBookmark(); onDismiss() }
                )
                MenuQuickIcon(
                    icon = Icons.Filled.Refresh,
                    label = "Reload",
                    enabled = !activeTab.isStartPage,
                    onClick = { onReload(); onDismiss() }
                )
                MenuQuickIcon(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    enabled = !activeTab.isStartPage,
                    onClick = { onShare(); onDismiss() }
                )
                MenuQuickIcon(
                    icon = Icons.Filled.FindInPage,
                    label = "Find",
                    enabled = !activeTab.isStartPage,
                    onClick = onFindInPage
                )
            }

            // Feature Switches Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(sabColors.chromeSurfaceElevated)
            ) {
                MenuToggleRow(
                    icon = Icons.Filled.Shield,
                    iconTint = if (settings.adBlockEnabled) sabColors.shieldGreen else sabColors.subtleText,
                    title = "Ad & Tracker Blocker",
                    subtitle = "${settings.totalBlockedTrackers} blocked total",
                    checked = settings.adBlockEnabled,
                    onCheckedChange = onToggleAdBlock
                )
                HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                MenuToggleRow(
                    icon = Icons.Filled.DesktopWindows,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "Desktop Site",
                    subtitle = "Request desktop viewport & user agent",
                    checked = activeTab.desktopMode,
                    onCheckedChange = { onToggleDesktop(); onDismiss() }
                )
                HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                MenuToggleRow(
                    icon = Icons.Filled.DarkMode,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    title = "Force Dark Pages",
                    subtitle = "Invert bright pages cleanly via sab-bridge",
                    checked = activeTab.forceDarkMode,
                    onCheckedChange = { onToggleForceDark(); onDismiss() }
                )
                HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                MenuToggleRow(
                    icon = Icons.AutoMirrored.Filled.ChromeReaderMode,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Reader Focus Mode",
                    subtitle = "Distraction-free typography layout",
                    checked = activeTab.readerMode,
                    onCheckedChange = { onToggleReader(); onDismiss() }
                )

                // Live Page Text Zoom Controls
                if (!activeTab.isStartPage) {
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FormatSize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Page Text Zoom", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Scale article readability live",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onAdjustTextZoom(-10) },
                                enabled = activeTab.textZoomPercent > 75,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(sabColors.chromeBar)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease text size", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "${activeTab.textZoomPercent}%",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab.textZoomPercent != 100) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onResetTextZoom)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            IconButton(
                                onClick = { onAdjustTextZoom(10) },
                                enabled = activeTab.textZoomPercent < 160,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(sabColors.chromeBar)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase text size", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Navigation Actions Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(sabColors.chromeSurfaceElevated)
            ) {
                MenuActionRow(icon = Icons.Filled.Add, title = "New Tab", onClick = onNewTab)
                MenuActionRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "New Private Tab",
                    tint = sabColors.privateAccent,
                    onClick = onNewPrivateTab
                )
                if (!activeTab.isStartPage) {
                    MenuActionRow(
                        icon = Icons.Filled.PushPin,
                        title = "Pin to Speed Dial",
                        onClick = { onPinToSpeedDial(); onDismiss() }
                    )
                }
                MenuActionRow(icon = Icons.Filled.Bookmark, title = "Bookmarks", onClick = onOpenBookmarks)
                MenuActionRow(icon = Icons.Filled.History, title = "History", onClick = onOpenHistory)
                MenuActionRow(
                    icon = Icons.Filled.Download,
                    title = "Downloads",
                    tint = sabColors.shieldGreen,
                    onClick = onOpenDownloads,
                    testTag = "menu_downloads_row"
                )
                MenuActionRow(
                    icon = Icons.Filled.Settings,
                    title = "Browser Settings & Engine Info",
                    onClick = onOpenSettings,
                    testTag = "menu_settings_row"
                )
                MenuActionRow(
                    icon = Icons.Filled.PlayCircleOutline,
                    title = "Replay Launch Animation",
                    onClick = onReplaySplash
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextBottomSheet(
    target: ContextMenuTarget,
    onOpenInNewTab: (String) -> Unit,
    onOpenInIsland: (String) -> Unit,
    onOpenInPrivateTab: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val primaryUrl = target.linkUrl ?: target.mediaUrl ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sabColors.chromeBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(14.dp)
            ) {
                if (!target.title.isNullOrBlank()) {
                    Text(
                        text = target.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = primaryUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = sabColors.subtleText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(sabColors.chromeSurfaceElevated)
            ) {
                MenuActionRow(
                    icon = Icons.Filled.OpenInNew,
                    title = "Open in New Tab",
                    onClick = { onOpenInNewTab(primaryUrl) }
                )
                MenuActionRow(
                    icon = Icons.Filled.Layers,
                    title = "Open in Tab Island",
                    tint = MaterialTheme.colorScheme.secondary,
                    onClick = { onOpenInIsland(primaryUrl) }
                )
                MenuActionRow(
                    icon = Icons.Filled.VisibilityOff,
                    title = "Open in Private Tab",
                    tint = sabColors.privateAccent,
                    onClick = { onOpenInPrivateTab(primaryUrl) }
                )
                HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                MenuActionRow(
                    icon = Icons.Filled.ContentCopy,
                    title = "Copy Link Address",
                    onClick = { onCopyUrl(primaryUrl); onDismiss() }
                )
                val downloadTarget = target.mediaUrl ?: primaryUrl
                MenuActionRow(
                    icon = Icons.Filled.Download,
                    title = if (target.mediaUrl != null) "Download Image / Media" else "Download Link",
                    tint = sabColors.shieldGreen,
                    onClick = { onDownloadUrl(downloadTarget) }
                )
                MenuActionRow(
                    icon = Icons.Filled.Share,
                    title = "Share Link",
                    onClick = { onShareUrl(primaryUrl); onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun WebPromptDialog(
    prompt: WebPromptRequest,
    onDismissComplete: () -> Unit
) {
    when (prompt) {
        is WebPromptRequest.Alert -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onDismiss()
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onDismiss()
                            onDismissComplete()
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        is WebPromptRequest.Confirm -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(false)
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = { Text(prompt.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(true)
                            onDismissComplete()
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(false)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.TextInput -> {
            var inputText by remember(prompt.id) { mutableStateOf(prompt.defaultValue) }
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(null)
                    onDismissComplete()
                },
                title = { Text(prompt.title.ifBlank { prompt.originHost }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (prompt.message.isNotBlank()) {
                            Text(prompt.message)
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(inputText)
                            onDismissComplete()
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(null)
                            onDismissComplete()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        is WebPromptRequest.SitePermission -> {
            AlertDialog(
                onDismissRequest = {
                    prompt.onResult(false)
                    onDismissComplete()
                },
                title = { Text("Site Permission") },
                text = { Text("${prompt.originHost} wants to ${prompt.permissionLabel}.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(true)
                            onDismissComplete()
                        }
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prompt.onResult(false)
                            onDismissComplete()
                        }
                    ) {
                        Text("Block")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyShieldSheet(
    activeTab: TabUiState,
    settings: BrowserSettings,
    engineInfo: EngineInfo,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleHttpsOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sabColors.chromeBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(sabColors.shieldGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = sabColors.shieldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "SAB Privacy Shield",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeTab.isStartPage) "Start Page • Protected"
                        else UrlResolver.extractHost(activeTab.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShieldMetricCard(
                    value = activeTab.blockedTrackersCount.toString(),
                    label = "Blocked on tab",
                    accent = sabColors.shieldGreen,
                    modifier = Modifier.weight(1f)
                )
                ShieldMetricCard(
                    value = settings.totalBlockedTrackers.toString(),
                    label = "Lifetime blocked",
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (activeTab.securityState == SecurityState.INSECURE) MaterialTheme.colorScheme.error
                    else sabColors.shieldGreen
                )
                Column {
                    Text(
                        text = when (activeTab.securityState) {
                            SecurityState.SECURE -> "Encrypted HTTPS Connection"
                            SecurityState.INSECURE -> "Unencrypted HTTP Connection"
                            else -> "Internal SAB Page"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Powered by ${engineInfo.name} ${engineInfo.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(sabColors.chromeSurfaceElevated)
            ) {
                MenuToggleRow(
                    icon = Icons.Filled.Shield,
                    iconTint = sabColors.shieldGreen,
                    title = "Enhanced Ad & Tracker Blocking",
                    subtitle = "Blocks ads, analytics, fingerprinting & cryptominers",
                    checked = settings.adBlockEnabled,
                    onCheckedChange = onToggleAdBlock
                )
                HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                MenuToggleRow(
                    icon = Icons.Filled.Lock,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    title = "HTTPS-Only Mode",
                    subtitle = "Automatically upgrade HTTP addresses to HTTPS",
                    checked = settings.httpsOnlyEnabled,
                    onCheckedChange = onToggleHttpsOnly
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun AddSpeedDialDialog(
    onConfirm: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Speed Dial") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Site name") },
                    placeholder = { Text("e.g., Mozilla Docs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web address") },
                    placeholder = { Text("https://developer.mozilla.org") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isNotBlank()) onConfirm(title, url)
                }
            ) {
                Text("Add Tile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ShieldMetricCard(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(sabColors.chromeSurfaceElevated)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = sabColors.subtleText
        )
    }
}

@Composable
private fun MenuQuickIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val resolvedTint = when {
        !enabled -> sabColors.subtleText.copy(alpha = 0.35f)
        tint != null -> tint
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = resolvedTint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = resolvedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalSabColors.current.subtleText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    title: String,
    tint: Color? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 13.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
