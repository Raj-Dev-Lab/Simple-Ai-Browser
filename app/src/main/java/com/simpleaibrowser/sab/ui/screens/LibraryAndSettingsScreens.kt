package com.simpleaibrowser.sab.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.BuildConfig
import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.CookieIsolationMode
import com.simpleaibrowser.sab.core.browser.DnsOverHttpsMode
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.CrashGuard
import com.simpleaibrowser.sab.core.browser.IncognitoShield
import com.simpleaibrowser.sab.core.browser.SecureRouting
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.SitePermissionStore
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.TrackingProtectionLevel
import com.simpleaibrowser.sab.core.network.PublicProxyRelay
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BookmarkEntity
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.data.DownloadEntity
import com.simpleaibrowser.sab.data.HistoryEntity
import com.simpleaibrowser.sab.ui.components.SiteFavicon
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.viewmodel.LibraryTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(
    initialTab: LibraryTab,
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    downloads: List<DownloadEntity>,
    onOpenUrl: (String) -> Unit,
    onOpenDownload: (DownloadEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onClearDownloads: () -> Unit,
    onOpenFullDownloadManager: () -> Unit = {},
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val filteredBookmarks = remember(bookmarks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) bookmarks else bookmarks.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }
    val filteredHistory = remember(history, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) history else history.filter {
            it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }
    val filteredDownloads = remember(downloads, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) downloads else downloads.filter {
            it.fileName.lowercase().contains(q) || it.url.lowercase().contains(q)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = when (activeTab) {
                            LibraryTab.BOOKMARKS -> "Bookmarks"
                            LibraryTab.HISTORY -> "History"
                            LibraryTab.DOWNLOADS -> "Downloads"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                val showClearButton = (activeTab == LibraryTab.HISTORY && history.isNotEmpty()) ||
                    (activeTab == LibraryTab.DOWNLOADS && downloads.isNotEmpty())
                if (showClearButton) {
                    TextButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            // 3-Segment Library Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LibrarySegmentChip(
                    label = "Saved (${bookmarks.size})",
                    selected = activeTab == LibraryTab.BOOKMARKS,
                    onClick = { activeTab = LibraryTab.BOOKMARKS },
                    modifier = Modifier.weight(1f)
                )
                LibrarySegmentChip(
                    label = "History (${history.size})",
                    selected = activeTab == LibraryTab.HISTORY,
                    onClick = { activeTab = LibraryTab.HISTORY },
                    modifier = Modifier.weight(1f)
                )
                LibrarySegmentChip(
                    label = "Files (${downloads.size})",
                    selected = activeTab == LibraryTab.DOWNLOADS,
                    onClick = { activeTab = LibraryTab.DOWNLOADS },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter ${activeTab.name.lowercase()}...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (activeTab) {
                    LibraryTab.BOOKMARKS -> {
                        if (filteredBookmarks.isEmpty()) {
                            item {
                                EmptyLibraryCard(
                                    icon = Icons.Filled.Bookmark,
                                    title = "No bookmarks yet",
                                    subtitle = "Tap the Bookmark star in the Opera menu on any webpage to save it here."
                                )
                            }
                        } else {
                            items(filteredBookmarks, key = { it.id }) { bm ->
                                LibrarySiteRow(
                                    title = bm.title,
                                    subtitle = bm.url,
                                    url = bm.url,
                                    onClick = { onOpenUrl(bm.url) },
                                    onDelete = { onDeleteBookmark(bm.id) }
                                )
                            }
                        }
                    }

                    LibraryTab.HISTORY -> {
                        if (filteredHistory.isEmpty()) {
                            item {
                                EmptyLibraryCard(
                                    icon = Icons.Filled.History,
                                    title = "History is clean",
                                    subtitle = "Pages you visit in regular tabs will appear here."
                                )
                            }
                        } else {
                            items(filteredHistory, key = { it.id }) { item ->
                                val timeLabel = remember(item.visitedAt) {
                                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.visitedAt))
                                }
                                LibrarySiteRow(
                                    title = item.title,
                                    subtitle = "$timeLabel • ${UrlResolver.extractHost(item.url)}",
                                    url = item.url,
                                    onClick = { onOpenUrl(item.url) },
                                    onDelete = { onDeleteHistory(item.id) }
                                )
                            }
                        }
                    }

                    LibraryTab.DOWNLOADS -> {
                        item(key = "open_full_dm_banner") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(sabColors.shieldGreen.copy(alpha = 0.14f))
                                    .border(1.dp, sabColors.shieldGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .clickable(onClick = onOpenFullDownloadManager)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Full Download Manager", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Multi-thread HTTP, Torrents, Page Media & File Explorer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Text(
                                    "Open",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = sabColors.shieldGreen
                                )
                            }
                        }
                        if (filteredDownloads.isEmpty()) {
                            item {
                                EmptyLibraryCard(
                                    icon = Icons.Filled.Download,
                                    title = "No downloads yet",
                                    subtitle = "Files downloaded via SAB Download Manager appear here and export to Android Downloads."
                                )
                            }
                        } else {
                            items(filteredDownloads, key = { it.id }) { dl ->
                                val timeLabel = remember(dl.createdAt) {
                                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(dl.createdAt))
                                }
                                val sizeLabel = remember(dl.sizeBytes) { formatBytes(dl.sizeBytes) }
                                DownloadItemRow(
                                    fileName = dl.fileName,
                                    meta = buildString {
                                        append(timeLabel)
                                        if (sizeLabel.isNotEmpty()) append(" • $sizeLabel")
                                        append(" • ${UrlResolver.extractHost(dl.url)}")
                                    },
                                    onClick = { onOpenDownload(dl) },
                                    onDelete = { onDeleteDownload(dl.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        val isDownloads = activeTab == LibraryTab.DOWNLOADS
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(if (isDownloads) "Clear downloads list?" else "Clear browsing history?") },
            text = {
                Text(
                    if (isDownloads) "This removes entries from SAB's list. Saved files remain in your Android Downloads folder."
                    else "This clears your browsing history and temporary web caches. Bookmarks and site logins stay safe."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isDownloads) onClearDownloads() else onClearHistory()
                        confirmClear = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

/** UI bundle for the Incognito Auto-Shield + Secure Routing sections of Settings. */
data class SecureRoutingUiState(
    val shieldEnabled: Boolean = true,
    val shieldPhase: IncognitoShield.ShieldPhase = IncognitoShield.ShieldPhase.Idle,
    val routingMode: SecureRouting.RoutingMode = SecureRouting.RoutingMode.OFF,
    val socksHost: String = SecureRouting.DEFAULT_TOR_HOST,
    val socksPort: Int = SecureRouting.DEFAULT_TOR_PORT,
    val pendingRestart: Boolean = false,
    val relayPhase: PublicProxyRelay.RelayPhase = PublicProxyRelay.RelayPhase.Idle,
    val orbotInstalled: Boolean = false
)

@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    engineInfo: EngineInfo,
    installedExtensionsCount: Int,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectThemePreset: (ThemePreset) -> Unit,
    onSelectAddressBarPosition: (AddressBarPosition) -> Unit,
    onToggleRestoreTabs: (Boolean) -> Unit,
    onSelectDefaultTextZoom: (Int) -> Unit,
    onSelectTrackingLevel: (TrackingProtectionLevel) -> Unit,
    onToggleDoNotTrack: (Boolean) -> Unit,
    onToggleGpc: (Boolean) -> Unit,
    onSelectCookieMode: (CookieIsolationMode) -> Unit,
    onToggleCookiePurging: (Boolean) -> Unit,
    onToggleStripTrackingParams: (Boolean) -> Unit,
    onToggleBounceTracking: (Boolean) -> Unit,
    onToggleFingerprintingDefense: (Boolean) -> Unit,
    onToggleLocalNetworkProtection: (Boolean) -> Unit,
    onToggleSafeBrowsing: (Boolean) -> Unit,
    onUpdateDohConfig: (DnsOverHttpsMode, String, Boolean) -> Unit,
    onToggleClearOnExit: (Boolean) -> Unit,
    onPurgeCookiesOnly: () -> Unit,
    onToggleHttpsOnly: (Boolean) -> Unit,
    onToggleForceDarkDefault: (Boolean) -> Unit,
    onToggleWallpaper: (Boolean) -> Unit,
    onToggleAutoUpdateExtensions: (Boolean) -> Unit,
    onOpenExtensionsStore: () -> Unit,
    onOpenDownloadManager: () -> Unit = {},
    sitePermissions: List<SitePermissionStore.SitePermission> = emptyList(),
    onRevokeSitePermission: (String, String) -> Unit = { _, _ -> },
    onResetAllSitePermissions: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    secureRouting: SecureRoutingUiState = SecureRoutingUiState(),
    onToggleIncognitoShield: (Boolean) -> Unit = {},
    onSetRoutingMode: (SecureRouting.RoutingMode) -> Unit = {},
    onRouteCustomSocks: (String, Int) -> Unit = { _, _ -> },
    onTestRoute: (String, Int) -> Unit = { _, _ -> },
    onStartRelayScan: () -> Unit = {},
    onConfirmRelay: (PublicProxyRelay.RelayProbeResult) -> Unit = {},
    onDismissRelayResults: () -> Unit = {},
    onStopRouting: () -> Unit = {},
    onRestartForRouting: () -> Unit = {},
    diagnostics: CrashGuard.DiagnosticsSnapshot? = null,
    onLoadDiagnostics: () -> Unit = {},
    onShareCrashReport: () -> Unit = {},
    onClearDiagnostics: () -> Unit = {},
    onReEnableGeckoEngine: () -> Unit = {},
    onClearAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Browser Settings",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Navigation & Ergonomics
                item(key = "ergonomics_section") {
                    SettingsSectionCard(title = "NAVIGATION & ERGONOMICS") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Address Bar Position",
                                style = MaterialTheme.typography.titleSmall
                            )
                            AddressBarPosition.entries.forEach { pos ->
                                val selected = settings.addressBarPosition == pos
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else sabColors.chromeBar
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectAddressBarPosition(pos) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pos.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(pos.subtitle, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                    }
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Restore Tabs & Islands on Startup",
                                subtitle = "Reopen your regular tabs and Tab Islands next time SAB launches",
                                checked = settings.restoreTabsOnStart,
                                onCheckedChange = onToggleRestoreTabs
                            )

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Default Page Text Zoom", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${settings.defaultTextZoom}%",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(85, 100, 115, 130, 150).forEach { zoom ->
                                        val isCurrent = settings.defaultTextZoom == zoom
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                    else sabColors.chromeBar
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isCurrent) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable { onSelectDefaultTextZoom(zoom) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$zoom%",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontSize = 11.sp,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Theme Presets
                item(key = "theme_section") {
                    SettingsSectionCard(title = "APPEARANCE & THEME") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreset.entries.forEach { preset ->
                                val selected = settings.themePreset == preset
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else sabColors.chromeBar
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else sabColors.chromeBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onSelectThemePreset(preset) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val swatch = when (preset) {
                                            ThemePreset.OBSIDIAN_CRIMSON -> Color(0xFFFF2E54)
                                            ThemePreset.MIDNIGHT_CYAN -> Color(0xFF00D4FF)
                                            ThemePreset.AURORA_VELVET -> Color(0xFFE056FD)
                                            ThemePreset.DAYLIGHT_CLEAN -> Color(0xFFF6F7FB)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(1.dp, sabColors.chromeBorder, CircleShape)
                                        )
                                        Column {
                                            Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                        }
                                    }
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            SettingsSwitchRow(
                                title = "Start Page Atmospheric Wallpaper",
                                subtitle = "Show SAB ambient art behind Speed Dial in dark themes",
                                checked = settings.showWallpaperOnStart,
                                onCheckedChange = onToggleWallpaper
                            )
                        }
                    }
                }

                // Search Engine
                item(key = "search_engine_section") {
                    SettingsSectionCard(title = "DEFAULT SEARCH ENGINE") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SearchEngine.entries.forEach { se ->
                                val selected = settings.searchEngine == se
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                                        .clickable { onSelectSearchEngine(se) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(se.displayName, style = MaterialTheme.typography.bodyLarge)
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Privacy & Security
                item(key = "privacy_section") {
                    var customDohDraft by remember(settings.customDohUrl) { mutableStateOf(settings.customDohUrl) }
                    SettingsSectionCard(title = "PRIVACY & SHIELD") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // ETP Level Selector
                            Text(
                                text = "Enhanced Tracking Protection",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TrackingProtectionLevel.entries.forEach { level ->
                                    val selected = settings.trackingProtectionLevel == level
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) sabColors.shieldGreen.copy(alpha = 0.2f)
                                                else sabColors.chromeBar
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) sabColors.shieldGreen else sabColors.chromeBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable { onSelectTrackingLevel(level) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = level.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) sabColors.shieldGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Text(
                                text = settings.trackingProtectionLevel.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Do Not Track (DNT: 1)",
                                subtitle = "Send DNT request header and navigator.doNotTrack signal to websites",
                                checked = settings.doNotTrackEnabled,
                                onCheckedChange = onToggleDoNotTrack
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Global Privacy Control (Sec-GPC)",
                                subtitle = "Signal websites not to sell or share your personal data",
                                checked = settings.globalPrivacyControlEnabled,
                                onCheckedChange = onToggleGpc
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            // Cookie Isolation Mode
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Cookie Isolation Policy",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                CookieIsolationMode.entries.forEach { mode ->
                                    val selected = settings.cookieIsolationMode == mode
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
                                            .clickable { onSelectCookieMode(mode) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mode.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                        }
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Purge Unused Tracker Cookies",
                                subtitle = "Automatically evict state from unvisited third-party trackers",
                                checked = settings.cookiePurgingEnabled,
                                onCheckedChange = onToggleCookiePurging
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Bounce Tracking Protection",
                                subtitle = "Purge cookies & storage left by hidden redirect-bounce trackers",
                                checked = settings.bounceTrackingProtection,
                                onCheckedChange = onToggleBounceTracking
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Strip URL Tracking Parameters",
                                subtitle = "Remove utm_*, fbclid, gclid, igshid, and ref tags before loading",
                                checked = settings.stripTrackingParams,
                                onCheckedChange = onToggleStripTrackingParams
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Fingerprinting Resistance",
                                subtitle = "Mask canvas, hardware concurrency & device entropy against profiling",
                                checked = settings.fingerprintingDefense,
                                onCheckedChange = onToggleFingerprintingDefense
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Local Network Access Protection",
                                subtitle = "Prevent public websites & trackers from probing your LAN / localhost",
                                checked = settings.localNetworkProtection,
                                onCheckedChange = onToggleLocalNetworkProtection
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Safe Browsing Guard",
                                subtitle = "Block known phishing, malware, and harmful downloads",
                                checked = settings.safeBrowsingEnabled,
                                onCheckedChange = onToggleSafeBrowsing
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "HTTPS-Only Mode",
                                subtitle = "Upgrade insecure HTTP addresses automatically",
                                checked = settings.httpsOnlyEnabled,
                                onCheckedChange = onToggleHttpsOnly
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            // Encrypted DNS over HTTPS
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Encrypted DNS over HTTPS (DoH)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                DnsOverHttpsMode.entries.forEach { mode ->
                                    val selected = settings.dnsOverHttpsMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (selected) sabColors.shieldGreen.copy(alpha = 0.14f)
                                                else sabColors.chromeBar
                                            )
                                            .border(
                                                1.dp,
                                                if (selected) sabColors.shieldGreen else sabColors.chromeBorder,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                onUpdateDohConfig(mode, customDohDraft.trim(), settings.dohStrict)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(mode.displayName, style = MaterialTheme.typography.titleSmall)
                                            Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
                                        }
                                        if (selected) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = sabColors.shieldGreen)
                                        }
                                    }
                                }
                                if (settings.dnsOverHttpsMode == DnsOverHttpsMode.CUSTOM) {
                                    OutlinedTextField(
                                        value = customDohDraft,
                                        onValueChange = { customDohDraft = it },
                                        label = { Text("Custom DoH HTTPS Endpoint") },
                                        placeholder = { Text("https://dns.example.com/dns-query") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateDohConfig(
                                                DnsOverHttpsMode.CUSTOM,
                                                customDohDraft.trim(),
                                                settings.dohStrict
                                            )
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Apply Custom DoH")
                                    }
                                }
                                if (settings.dnsOverHttpsMode != DnsOverHttpsMode.OFF) {
                                    SettingsSwitchRow(
                                        title = "Strict Encrypted DNS Only",
                                        subtitle = "Do not fall back to unencrypted ISP DNS if DoH fails",
                                        checked = settings.dohStrict,
                                        onCheckedChange = { strict ->
                                            onUpdateDohConfig(settings.dnsOverHttpsMode, customDohDraft.trim(), strict)
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Clear Cookies & History on Exit",
                                subtitle = "Automatically wipe browsing history, cookies & cache when SAB closes",
                                checked = settings.clearPrivateDataOnExit,
                                onCheckedChange = onToggleClearOnExit
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            SettingsSwitchRow(
                                title = "Force Dark Pages by Default",
                                subtitle = "Automatically apply dark palette to bright websites",
                                checked = settings.forceDarkDefault,
                                onCheckedChange = onToggleForceDarkDefault
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPurgeCookiesOnly() }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Purge Site Cookies & DOM Storage",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Clear all site cookies & storage while keeping bookmarks and history",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }

                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showClearDialog = true }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Clear All Browsing Data",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Delete history, cookies, saved tabs, and web cache",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Incognito Auto-Shield
                item(key = "incognito_shield_section") {
                    SettingsSectionCard(title = "INCOGNITO AUTO-SHIELD") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsSwitchRow(
                                title = "Auto-harden Private Tabs",
                                subtitle = "When a private tab opens, SAB benchmarks trusted secure DNS (Quad9, AdGuard, Cloudflare) and switches to the fastest working one, while forcing Strict tracking protection, fingerprinting defense, total cookie isolation, tracker-parameter stripping, bounce-tracking protection and local-network protection. Your normal settings return on regular tabs.",
                                checked = secureRouting.shieldEnabled,
                                onCheckedChange = onToggleIncognitoShield
                            )
                            val shieldStatus = when (val phase = secureRouting.shieldPhase) {
                                is IncognitoShield.ShieldPhase.Active ->
                                    if (phase.winner != null) {
                                        "Shield active • fastest secure DNS: ${phase.winner.label} (${phase.winner.bestLatencyMs} ms)"
                                    } else {
                                        "Shield active • strict defenses enforced (no secure DNS reachable)"
                                    }
                                IncognitoShield.ShieldPhase.Benchmarking -> "Benchmarking secure DNS providers…"
                                IncognitoShield.ShieldPhase.Idle -> "Waiting for a private tab…"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = sabColors.shieldGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    shieldStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText
                                )
                            }
                        }
                    }
                }

                // Secure Routing (Experimental)
                item(key = "secure_routing_section") {
                    SettingsSectionCard(title = "SECURE ROUTING (EXPERIMENTAL)") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Route page traffic AND DNS through SOCKS5. Tor/Orbot is genuinely private but slower. Downloads route immediately; page traffic applies after a relaunch. There are no fake \"free VPN\" claims here: public relays are unverified servers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )

                            RoutingModeRow(
                                icon = Icons.Filled.Public,
                                title = "No Routing",
                                subtitle = "Direct connection using your normal network",
                                selected = secureRouting.routingMode == SecureRouting.RoutingMode.OFF,
                                onClick = { onSetRoutingMode(SecureRouting.RoutingMode.OFF) }
                            )
                            RoutingModeRow(
                                icon = Icons.Filled.Router,
                                title = "Tor / Orbot (SOCKS5 127.0.0.1:9050)",
                                subtitle = if (secureRouting.orbotInstalled) {
                                    "Orbot detected — start it in Orbot, then tap Test Route"
                                } else {
                                    "Install & start Orbot (org.torproject.android), then tap Test Route"
                                },
                                selected = secureRouting.routingMode == SecureRouting.RoutingMode.TOR_ORBOT,
                                onClick = { onSetRoutingMode(SecureRouting.RoutingMode.TOR_ORBOT) }
                            )
                            RoutingModeRow(
                                icon = Icons.Filled.Router,
                                title = "Custom Trusted SOCKS5",
                                subtitle = "Your own server (self-hosted or otherwise trusted)",
                                selected = secureRouting.routingMode == SecureRouting.RoutingMode.SOCKS5,
                                onClick = { onSetRoutingMode(SecureRouting.RoutingMode.SOCKS5) }
                            )
                            RoutingModeRow(
                                icon = Icons.Filled.WarningAmber,
                                title = "Public Proxy Relay ⚠ EXPERIMENTAL",
                                subtitle = "Fetch + probe public lists, then pick a working relay. Unverified servers — use at your own risk.",
                                selected = secureRouting.routingMode == SecureRouting.RoutingMode.PUBLIC_RELAY,
                                onClick = onStartRelayScan
                            )

                            var hostValue by remember(secureRouting.socksHost) {
                                mutableStateOf(secureRouting.socksHost)
                            }
                            var portValue by remember(secureRouting.socksPort) {
                                mutableStateOf(secureRouting.socksPort.toString())
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = hostValue,
                                    onValueChange = { hostValue = it },
                                    label = { Text("SOCKS5 host") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f)
                                )
                                OutlinedTextField(
                                    value = portValue,
                                    onValueChange = { input ->
                                        portValue = input.filter { it.isDigit() }.take(5)
                                    },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    onTestRoute(hostValue, portValue.toIntOrNull() ?: 0)
                                }) {
                                    Text("Test Route")
                                }
                                OutlinedButton(onClick = {
                                    onRouteCustomSocks(hostValue, portValue.toIntOrNull() ?: 0)
                                }) {
                                    Text("Route Traffic")
                                }
                            }

                            when (val relay = secureRouting.relayPhase) {
                                PublicProxyRelay.RelayPhase.Fetching -> {
                                    Text(
                                        "Fetching public SOCKS5 lists…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is PublicProxyRelay.RelayPhase.Probing -> {
                                    Text(
                                        "Probing public relays ${relay.done}/${relay.total}…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    LinearProgressIndicator(
                                        progress = {
                                            if (relay.total == 0) 0f else relay.done.toFloat() / relay.total
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                is PublicProxyRelay.RelayPhase.Ready -> {
                                    Text(
                                        "Working relays found (${relay.results.size}) — tap one to route:",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    relay.results.take(5).forEach { probe ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onConfirmRelay(probe) }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "${probe.candidate.host}:${probe.candidate.port}",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    "${probe.latencyMs} ms • exit ${probe.exitIp ?: "?"}" +
                                                        (probe.countryCode?.let { " ($it)" } ?: ""),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = sabColors.subtleText
                                                )
                                            }
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = sabColors.shieldGreen
                                            )
                                        }
                                        HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.4f))
                                    }
                                    TextButton(onClick = onDismissRelayResults) { Text("Dismiss") }
                                }
                                is PublicProxyRelay.RelayPhase.Routed -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Routing via ${relay.result.candidate.host}:${relay.result.candidate.port}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                "UNVERIFIED THIRD-PARTY SERVER — your traffic is visible to its operator. Not bank-grade security. Use at your own risk.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    TextButton(onClick = onStopRouting) {
                                        Text("Stop routing", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                is PublicProxyRelay.RelayPhase.Failed -> {
                                    Text(
                                        relay.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    TextButton(onClick = onStartRelayScan) { Text("Retry scan") }
                                }
                                PublicProxyRelay.RelayPhase.Idle -> Unit
                            }

                            if (secureRouting.routingMode == SecureRouting.RoutingMode.PUBLIC_RELAY) {
                                Text(
                                    text = "⚠ PUBLIC PROXY RELAY (EXPERIMENTAL): you are routing through an unverified third-party server. Assume the operator can observe all traffic. Use only for casual, non-sensitive browsing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (secureRouting.pendingRestart &&
                                secureRouting.routingMode != SecureRouting.RoutingMode.OFF
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Page-traffic routing activates after a relaunch (tabs restore automatically).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onRestartForRouting) {
                                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Restart now")
                                    }
                                }
                            }
                        }
                    }
                }

                // Diagnostics (crash & boot guard)
                item(key = "diagnostics_section") {
                    LaunchedEffect(Unit) { onLoadDiagnostics() }
                    SettingsSectionCard(title = "DIAGNOSTICS") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val snap = diagnostics
                            if (snap == null) {
                                Text(
                                    "Collecting diagnostics…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText
                                )
                            } else {
                                Text(
                                    "Engine: ${snap.engineSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText
                                )
                                Text(
                                    if (snap.bootFailures == 0) {
                                        "Failed boot attempts: none"
                                    } else {
                                        "Failed boot attempts: ${snap.bootFailures}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (snap.bootFailures == 0) sabColors.subtleText else MaterialTheme.colorScheme.error
                                )
                                if (snap.safeModeNotice) {
                                    Text(
                                        "⚠ Safe mode: SAB removed the Secure Routing profile after repeated startup crashes. Re-enable routing below if you need it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (snap.engineLockout) {
                                    Text(
                                        "⚠ Recovery mode: after repeated startup crashes SAB is running on the built-in fallback engine instead of GeckoView.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    OutlinedButton(onClick = onReEnableGeckoEngine) {
                                        Text("Re-enable GeckoView engine")
                                    }
                                }
                                Text(
                                    "Secure Routing: ${snap.routingModeLabel} • prefs file " +
                                        if (snap.routingPrefsFilePresent) "present" else "not present",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText
                                )
                                val crash = snap.lastCrash
                                if (crash == null) {
                                    Text(
                                        "No crash has been recorded on this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                } else {
                                    val whenText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .format(java.util.Date(crash.timestampMs))
                                    Text(
                                        "Last crash: $whenText (${crash.threadName})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = crash.stackTrace.lineSequence().take(8).joinToString("\n"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = onShareCrashReport) {
                                            Text("Copy crash report")
                                        }
                                        TextButton(onClick = onClearDiagnostics) {
                                            Text("Clear", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Data Backup
                item(key = "data_backup_section") {
                    SettingsSectionCard(title = "DATA BACKUP") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onExportData)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Export Bookmarks & Speed Dials", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Save a JSON backup file anywhere on your device",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Icon(Icons.Filled.IosShare, contentDescription = null)
                            }
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onImportData)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Import from Backup File", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Restore bookmarks and speed dials from a SAB backup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Icon(Icons.Filled.SaveAlt, contentDescription = null)
                            }
                        }
                    }
                }

                // Site Permissions Manager
                item(key = "site_permissions_section") {
                    SettingsSectionCard(title = "SITE PERMISSIONS") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Sites you told SAB to remember are listed here. \"Forget\" asks again next time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                            if (sitePermissions.isEmpty()) {
                                Text(
                                    text = "No remembered site permissions yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = sabColors.subtleText
                                )
                            } else {
                                sitePermissions.take(30).forEach { grant ->
                                    val decisionLabel = if (grant.allow) "Allowed" else "Blocked"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = grant.origin,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$decisionLabel • ${grant.permission}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (grant.allow) {
                                                    Color(0xFF10B981)
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                        TextButton(onClick = { onRevokeSitePermission(grant.origin, grant.permission) }) {
                                            Text("Forget")
                                        }
                                    }
                                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.4f))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = onResetAllSitePermissions) {
                                        Text("Reset all", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // Extensions & Auto-Updates
                item(key = "extensions_section") {
                    SettingsSectionCard(title = "EXTENSIONS & AUTO-UPDATES") {
                        Column {
                            SettingsSwitchRow(
                                title = "Auto-Update Extensions",
                                subtitle = "Automatically install compatible updates from Mozilla Add-ons & GitHub Releases every 12 hours",
                                checked = settings.autoUpdateExtensions,
                                onCheckedChange = onToggleAutoUpdateExtensions
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenExtensionsStore)
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Open Extension Store & Manager",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (installedExtensionsCount > 0) "$installedExtensionsCount extension(s) installed"
                                        else "Browse Mozilla Add-ons & GitHub WebExtensions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Text(
                                    text = "Manage",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Download Manager & File System
                item(key = "downloads_section") {
                    SettingsSectionCard(title = "DOWNLOAD MANAGER & STORAGE") {
                        val dl = settings.downloadSettings
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiagnosticLine("Storage Mode", dl.storageLocationMode.title)
                            DiagnosticLine(
                                "HTTP Threads & Queue",
                                "${dl.defaultConnectionsPerDownload}x threads • ${dl.maxConcurrentDownloads} parallel"
                            )
                            DiagnosticLine(
                                "Torrent Engine",
                                if (dl.torrentEnableDht) "libtorrent4j • DHT Active" else "libtorrent4j • DHT Off"
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenDownloadManager)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Open Download Manager & Preferences",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Configure speed caps, folders, Wi-Fi rules, User-Agent & BitTorrent options",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Text(
                                    text = "Configure",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = sabColors.shieldGreen
                                )
                            }
                        }
                    }
                }

                // Engine Diagnostics Card
                item(key = "engine_section") {
                    SettingsSectionCard(title = "ENGINE ARCHITECTURE & LICENSE") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(engineInfo.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = engineInfo.version,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = sabColors.subtleText
                                    )
                                }
                            }
                            DiagnosticLine("Application ID", BuildConfig.APPLICATION_ID)
                            DiagnosticLine("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                            DiagnosticLine("Device ABI", engineInfo.abi)
                            DiagnosticLine("Android API", "API ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
                            DiagnosticLine("sab-bridge Extension", if (engineInfo.builtInExtensionReady) "Loaded" else "Standby")
                            DiagnosticLine("Author & Owner", "Tamal Mitra (Raj-Dev-Lab)")
                            DiagnosticLine("License", "GNU GPL v3.0 (Strong Copyleft + Attribution)")
                            Text(
                                text = engineInfo.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLicenseDialog = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Gavel,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("View License & Author Attribution", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all browsing data?") },
            text = { Text("History, cookies, saved session tabs, and engine storage will be permanently cleared.") },
            confirmButton = {
                TextButton(onClick = { onClearAllData(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("License & Attribution") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Simple AI Browser (SAB)\nCopyright (c) 2025-2026 Tamal Mitra (Raj-Dev-Lab)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Distributed under GNU GPL v3.0 with Section 7(b) attribution preservation. Any redistribution or derivative work must preserve original author credit to Tamal Mitra (Raj-Dev-Lab), retain LICENSE & NOTICE files, clearly mark modifications, and remain open source under GPL-3.0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                    HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                    Text(
                        text = "Key Open-Source Components:\n• Mozilla GeckoView (MPL-2.0)\n• libtorrent4j (MIT)\n• OkHttp & Android Jetpack (Apache-2.0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

@Composable
private fun LibrarySegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
            color = if (selected) MaterialTheme.colorScheme.primary else LocalSabColors.current.subtleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibrarySiteRow(
    title: String,
    subtitle: String,
    url: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sabColors = LocalSabColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(sabColors.chromeSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SiteFavicon(
            url = url,
            title = title,
            accentColor = MaterialTheme.colorScheme.primary,
            size = 38.dp,
            iconSize = 22.dp,
            cornerRadius = 10.dp,
            monogramSize = 15.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete item", tint = sabColors.subtleText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DownloadItemRow(
    fileName: String,
    meta: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sabColors = LocalSabColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(sabColors.chromeSurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(sabColors.shieldGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = sabColors.shieldGreen,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove download record", tint = sabColors.subtleText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val sabColors = LocalSabColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = sabColors.subtleText, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = sabColors.subtleText)
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val sabColors = LocalSabColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.4.sp,
            color = sabColors.subtleText
        )
        Surface(
            color = sabColors.chromeSurfaceElevated,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(18.dp))
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun RoutingModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sabColors = LocalSabColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else sabColors.subtleText,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText
            )
            if (selected) {
                Text(
                    text = "ACTIVE • downloads route now • page traffic after relaunch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
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
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LocalSabColors.current.subtleText)
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
