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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BrowserSettings
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
    onPinToSpeedDial: () -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleForceDark: () -> Unit,
    onToggleReader: () -> Unit,
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
