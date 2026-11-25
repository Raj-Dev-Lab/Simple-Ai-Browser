package com.simpleaibrowser.sab.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.simpleaibrowser.sab.core.browser.EngineInfo
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.data.BrowserSettings
import com.simpleaibrowser.sab.data.SpeedDialEntity
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.theme.SpaceGroteskFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialStartPage(
    isPrivate: Boolean,
    settings: BrowserSettings,
    speedDials: List<SpeedDialEntity>,
    engineInfo: EngineInfo,
    onSearchClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddSpeedDialClick: () -> Unit,
    onRemoveSpeedDial: (Long) -> Unit,
    onShieldClick: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    var pendingDeleteTile by remember { mutableStateOf<SpeedDialEntity?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (isPrivate) sabColors.privateSurface else MaterialTheme.colorScheme.background
            )
    ) {
        if (settings.showWallpaperOnStart && settings.themePreset.isDark && !isPrivate) {
            Image(
                painter = painterResource(id = R.drawable.img_sab_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.55f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x660B0D14),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Brand & Privacy Shield Header
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_sab_logo),
                                contentDescription = "SAB Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = if (isPrivate) "SAB Private" else "SAB",
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isPrivate) "Zero-trace incognito session" else "Simple AI Browser",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isPrivate) sabColors.privateAccent else sabColors.subtleText
                            )
                        }
                    }

                    // Live Shield Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(sabColors.chromeSurfaceElevated.copy(alpha = 0.92f))
                            .border(
                                1.dp,
                                if (settings.adBlockEnabled) sabColors.shieldGreen.copy(alpha = 0.45f)
                                else sabColors.chromeBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable(onClick = onShieldClick)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("start_shield_pill"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Privacy Shield",
                            tint = if (settings.adBlockEnabled) sabColors.shieldGreen else sabColors.subtleText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (settings.adBlockEnabled) {
                                "${settings.totalBlockedTrackers} blocked"
                            } else {
                                "Shield Off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Private Mode Notice Card
            if (isPrivate) {
                item(key = "private_card") {
                    Surface(
                        color = sabColors.privateAccent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                sabColors.privateAccent.copy(alpha = 0.4f),
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VisibilityOff,
                                contentDescription = null,
                                tint = sabColors.privateAccent,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "You're browsing privately",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "SAB won't save history, cookies, or site cache for this tab.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText
                                )
                            }
                        }
                    }
                }
            }

            // Hero Search Pill
            item(key = "search_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(sabColors.chromeSurfaceElevated.copy(alpha = 0.95f))
                        .border(
                            width = 1.2.dp,
                            color = if (isPrivate) sabColors.privateAccent.copy(alpha = 0.55f)
                            else sabColors.chromeBorder,
                            shape = RoundedCornerShape(28.dp)
                        )
                        .clickable(onClick = onSearchClick)
                        .padding(horizontal = 18.dp)
                        .testTag("start_page_search_bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary)
                                    .copy(alpha = 0.16f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = settings.searchEngine.badgeChar,
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Search ${settings.searchEngine.displayName} or enter URL",
                        style = MaterialTheme.typography.bodyLarge,
                        color = sabColors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Speed Dial Grid (4 columns)
            item(key = "speed_dial_section") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED DIAL",
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 1.6.sp,
                            color = sabColors.subtleText
                        )
                        Text(
                            text = "Long-press tile to remove",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText.copy(alpha = 0.7f)
                        )
                    }

                    val tilesWithAdd = speedDials.map<SpeedDialEntity, SpeedDialEntity?> { it } + null
                    val rows = tilesWithAdd.chunked(4)

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { item ->
                                    if (item != null) {
                                        SpeedDialTile(
                                            item = item,
                                            onClick = { onOpenUrl(item.url) },
                                            onLongPress = { pendingDeleteTile = item },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        AddSpeedDialTile(
                                            onClick = onAddSpeedDialClick,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                repeat(4 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Opera-style Quick Access Utility Row
            item(key = "quick_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StartQuickActionChip(
                        icon = Icons.Filled.VisibilityOff,
                        label = "Private",
                        accent = sabColors.privateAccent,
                        onClick = onNewPrivateTab,
                        modifier = Modifier.weight(1f).testTag("quick_private_btn")
                    )
                    StartQuickActionChip(
                        icon = Icons.Filled.Bookmark,
                        label = "Bookmarks",
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onOpenBookmarks,
                        modifier = Modifier.weight(1f).testTag("quick_bookmarks_btn")
                    )
                    StartQuickActionChip(
                        icon = Icons.Filled.History,
                        label = "History",
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f).testTag("quick_history_btn")
                    )
                    StartQuickActionChip(
                        icon = Icons.Filled.Settings,
                        label = "Settings",
                        accent = sabColors.subtleText,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f).testTag("quick_settings_btn")
                    )
                }
            }

            // Engine Architecture Pill Footer
            item(key = "engine_footer") {
                Surface(
                    color = sabColors.chromeSurfaceElevated.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.8.dp, sabColors.chromeBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenSettings)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = engineInfo.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${engineInfo.version} • ABI ${engineInfo.abi}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = sabColors.subtleText
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    (if (engineInfo.isGeckoViewActive) sabColors.shieldGreen else MaterialTheme.colorScheme.secondary)
                                        .copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (engineInfo.isGeckoViewActive) "GECKO" else "COMPAT",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (engineInfo.isGeckoViewActive) sabColors.shieldGreen
                                else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteTile?.let { tile ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTile = null },
            title = { Text("Remove Speed Dial?") },
            text = { Text("Remove \"${tile.title}\" from your Start Page?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSpeedDial(tile.id)
                        pendingDeleteTile = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
    item: SpeedDialEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    val accentColor = Color(item.accentHex)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.24f),
                            sabColors.chromeSurfaceElevated
                        )
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.42f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = UrlResolver.extractMonogram(item.title),
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddSpeedDialTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("add_speed_dial_tile")
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(sabColors.chromeSurfaceElevated.copy(alpha = 0.7f))
                .border(1.dp, sabColors.chromeBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Speed Dial",
                tint = sabColors.subtleText,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelMedium,
            color = sabColors.subtleText,
            maxLines = 1
        )
    }
}

@Composable
private fun StartQuickActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(sabColors.chromeSurfaceElevated.copy(alpha = 0.85f))
            .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
