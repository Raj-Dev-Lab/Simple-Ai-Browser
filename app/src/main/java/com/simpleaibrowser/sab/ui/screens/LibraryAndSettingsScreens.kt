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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.SearchEngine
import com.simpleaibrowser.sab.core.browser.ThemePreset
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BookmarkEntity
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.data.HistoryEntity
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
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
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
                        text = if (activeTab == LibraryTab.BOOKMARKS) "Bookmarks" else "History",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (activeTab == LibraryTab.HISTORY && history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }

            // Segmented Toggle
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
                    label = "Bookmarks (${bookmarks.size})",
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
                if (activeTab == LibraryTab.BOOKMARKS) {
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
                            LibraryItemRow(
                                title = bm.title,
                                subtitle = bm.url,
                                badge = UrlResolver.extractMonogram(bm.title),
                                onClick = { onOpenUrl(bm.url) },
                                onDelete = { onDeleteBookmark(bm.id) }
                            )
                        }
                    }
                } else {
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
                            LibraryItemRow(
                                title = item.title,
                                subtitle = "$timeLabel • ${UrlResolver.extractHost(item.url)}",
                                badge = UrlResolver.extractMonogram(item.title),
                                onClick = { onOpenUrl(item.url) },
                                onDelete = { onDeleteHistory(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing history?") },
            text = { Text("This clears visited URLs, cookies, and web storage across SAB.") },
            confirmButton = {
                TextButton(onClick = { onClearHistory(); confirmClear = false }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    engineInfo: EngineInfo,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectThemePreset: (ThemePreset) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleHttpsOnly: (Boolean) -> Unit,
    onToggleForceDarkDefault: (Boolean) -> Unit,
    onToggleWallpaper: (Boolean) -> Unit,
    onClearAllData: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var showClearDialog by remember { mutableStateOf(false) }

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
                    SettingsSectionCard(title = "PRIVACY & SHIELD") {
                        Column {
                            SettingsSwitchRow(
                                title = "Ad & Tracker Blocker",
                                subtitle = "GeckoView Enhanced Tracking Protection + Safe Browsing",
                                checked = settings.adBlockEnabled,
                                onCheckedChange = onToggleAdBlock
                            )
                            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.5f))
                            SettingsSwitchRow(
                                title = "HTTPS-Only Mode",
                                subtitle = "Upgrade insecure HTTP addresses automatically",
                                checked = settings.httpsOnlyEnabled,
                                onCheckedChange = onToggleHttpsOnly
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
                                    .clickable { showClearDialog = true }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Clear Browsing Data",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Delete history, cookies, and web cache",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = sabColors.subtleText
                                    )
                                }
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Engine Diagnostics Card
                item(key = "engine_section") {
                    SettingsSectionCard(title = "ENGINE ARCHITECTURE & BUILD") {
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
                            DiagnosticLine("License", "MIT (Browser Shell) • MPL-2.0 (GeckoView)")
                            Text(
                                text = engineInfo.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
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
            text = { Text("History, cookies, and engine storage will be permanently cleared.") },
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
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else LocalSabColors.current.subtleText
        )
    }
}

@Composable
private fun LibraryItemRow(
    title: String,
    subtitle: String,
    badge: String,
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
