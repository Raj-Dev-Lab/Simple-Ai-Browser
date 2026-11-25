package com.simpleaibrowser.sab.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.TabIsland
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.theme.SpaceGroteskFamily
import com.simpleaibrowser.sab.ui.viewmodel.ClosedTabRecord

private enum class GallerySection {
    REGULAR,
    ISLANDS,
    PRIVATE
}

@Composable
fun TabGalleryScreen(
    tabs: List<TabUiState>,
    activeTabId: String,
    islands: List<TabIsland>,
    recentlyClosed: List<ClosedTabRecord>,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAll: (Boolean) -> Unit,
    onReopenClosed: () -> Unit,
    onCreateIsland: (String) -> Unit,
    onToggleIslandCollapsed: (String) -> Unit,
    onAddTabToIsland: (String) -> Unit,
    onCloseIsland: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var section by remember { mutableStateOf(GallerySection.REGULAR) }
    var isGridLayout by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    var showCreateIslandDialog by remember { mutableStateOf(false) }
    var newIslandName by remember { mutableStateOf("") }

    val regularTabs = remember(tabs, filterQuery) {
        tabs.filter { !it.isPrivate && matchesFilter(it, filterQuery) }
    }
    val privateTabs = remember(tabs, filterQuery) {
        tabs.filter { it.isPrivate && matchesFilter(it, filterQuery) }
    }

    Surface(
        color = if (section == GallerySection.PRIVATE) sabColors.privateSurface
        else MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar with Opera Gallery Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(sabColors.chromeSurfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GalleryTabSegment(
                        label = "Tabs (${tabs.count { !it.isPrivate }})",
                        selected = section == GallerySection.REGULAR,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { section = GallerySection.REGULAR },
                        modifier = Modifier.weight(1f)
                    )
                    GalleryTabSegment(
                        label = "Islands (${islands.size})",
                        selected = section == GallerySection.ISLANDS,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = { section = GallerySection.ISLANDS },
                        modifier = Modifier.weight(1f)
                    )
                    GalleryTabSegment(
                        label = "Private (${tabs.count { it.isPrivate }})",
                        selected = section == GallerySection.PRIVATE,
                        accent = sabColors.privateAccent,
                        onClick = { section = GallerySection.PRIVATE },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp).testTag("tab_gallery_close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close tab gallery",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Search + Layout Switcher Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Search open tabs...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (filterQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { filterQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )
                if (section != GallerySection.ISLANDS) {
                    IconButton(
                        onClick = { isGridLayout = !isGridLayout },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(sabColors.chromeSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = if (isGridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle grid or list view",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Recently Closed Undo Strip
            if (recentlyClosed.isNotEmpty() && section != GallerySection.PRIVATE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(sabColors.chromeSurfaceElevated)
                        .clickable(onClick = onReopenClosed)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Reopen: ${recentlyClosed.first().title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "UNDO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (section) {
                    GallerySection.REGULAR -> {
                        TabCollectionContent(
                            tabs = regularTabs,
                            activeTabId = activeTabId,
                            islands = islands,
                            isGrid = isGridLayout,
                            emptyTitle = "No matching tabs",
                            emptySubtitle = "Open a new tab to explore the web.",
                            onSelectTab = onSelectTab,
                            onCloseTab = onCloseTab
                        )
                    }
                    GallerySection.PRIVATE -> {
                        TabCollectionContent(
                            tabs = privateTabs,
                            activeTabId = activeTabId,
                            islands = emptyList(),
                            isGrid = isGridLayout,
                            emptyTitle = "No Private Tabs",
                            emptySubtitle = "Private tabs keep your browsing off local history and cookies.",
                            onSelectTab = onSelectTab,
                            onCloseTab = onCloseTab
                        )
                    }
                    GallerySection.ISLANDS -> {
                        TabIslandsContent(
                            islands = islands,
                            tabs = regularTabs,
                            activeTabId = activeTabId,
                            onSelectTab = onSelectTab,
                            onCloseTab = onCloseTab,
                            onToggleCollapsed = onToggleIslandCollapsed,
                            onAddTabToIsland = onAddTabToIsland,
                            onCloseIsland = onCloseIsland,
                            onCreateIslandClick = { showCreateIslandDialog = true }
                        )
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sabColors.chromeBar)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { onCloseAll(section == GallerySection.PRIVATE) },
                    modifier = Modifier.testTag("tab_gallery_close_all")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = sabColors.subtleText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Close All",
                        color = sabColors.subtleText
                    )
                }

                if (section == GallerySection.ISLANDS) {
                    Button(
                        onClick = { showCreateIslandDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.testTag("create_island_button")
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Island")
                    }
                } else {
                    val isPrivateMode = section == GallerySection.PRIVATE
                    Button(
                        onClick = { onNewTab(isPrivateMode) },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrivateMode) sabColors.privateAccent
                            else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("tab_gallery_new_tab")
                    ) {
                        Icon(
                            imageVector = if (isPrivateMode) Icons.Filled.VisibilityOff else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPrivateMode) "New Private Tab" else "New Tab")
                    }
                }
            }
        }
    }

    if (showCreateIslandDialog) {
        AlertDialog(
            onDismissRequest = { showCreateIslandDialog = false },
            title = { Text("Create Tab Island") },
            text = {
                OutlinedTextField(
                    value = newIslandName,
                    onValueChange = { newIslandName = it },
                    label = { Text("Island name (e.g., Research, Travel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateIsland(newIslandName)
                        newIslandName = ""
                        showCreateIslandDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateIslandDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun matchesFilter(tab: TabUiState, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return tab.title.lowercase().contains(q) || tab.url.lowercase().contains(q)
}

@Composable
private fun GalleryTabSegment(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else LocalSabColors.current.subtleText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TabCollectionContent(
    tabs: List<TabUiState>,
    activeTabId: String,
    islands: List<TabIsland>,
    isGrid: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit
) {
    if (tabs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text(emptyTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(emptySubtitle, style = MaterialTheme.typography.bodyMedium, color = LocalSabColors.current.subtleText)
            }
        }
        return
    }

    val islandById = remember(islands) { islands.associateBy { it.id } }

    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabPreviewCard(
                    tab = tab,
                    isActive = tab.id == activeTabId,
                    island = tab.islandId?.let { islandById[it] },
                    onSelect = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabRowItem(
                    tab = tab,
                    isActive = tab.id == activeTabId,
                    island = tab.islandId?.let { islandById[it] },
                    onSelect = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) }
                )
            }
        }
    }
}

@Composable
private fun TabIslandsContent(
    islands: List<TabIsland>,
    tabs: List<TabUiState>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onAddTabToIsland: (String) -> Unit,
    onCloseIsland: (String) -> Unit,
    onCreateIslandClick: () -> Unit
) {
    val sabColors = LocalSabColors.current
    if (islands.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(44.dp)
                )
                Text("Organize with Tab Islands", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Links opened in new tabs automatically group into color-coded Tab Islands, or tap below to create one manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sabColors.subtleText
                )
                TextButton(onClick = onCreateIslandClick) {
                    Text("Create your first Island")
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(islands, key = { it.id }) { island ->
            val islandColor = Color(island.color.hex)
            val memberTabs = tabs.filter { it.islandId == island.id }

            Surface(
                color = sabColors.chromeSurfaceElevated,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, islandColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCollapsed(island.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(islandColor)
                            )
                            Text(
                                text = island.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${memberTabs.size} tabs",
                                style = MaterialTheme.typography.labelMedium,
                                color = sabColors.subtleText
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onAddTabToIsland(island.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add tab to island", tint = islandColor)
                            }
                            IconButton(
                                onClick = { onCloseIsland(island.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close island", tint = sabColors.subtleText)
                            }
                            Icon(
                                imageVector = if (island.isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = "Toggle collapse",
                                tint = sabColors.subtleText
                            )
                        }
                    }

                    if (!island.isCollapsed) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            memberTabs.forEach { tab ->
                                TabRowItem(
                                    tab = tab,
                                    isActive = tab.id == activeTabId,
                                    island = null,
                                    onSelect = { onSelectTab(tab.id) },
                                    onClose = { onCloseTab(tab.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPreviewCard(
    tab: TabUiState,
    isActive: Boolean,
    island: TabIsland?,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val sabColors = LocalSabColors.current
    val borderColor = when {
        isActive && tab.isPrivate -> sabColors.privateAccent
        isActive -> MaterialTheme.colorScheme.primary
        island != null -> Color(island.color.hex).copy(alpha = 0.6f)
        else -> sabColors.chromeBorder
    }

    Surface(
        color = sabColors.chromeSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(sabColors.chromeBar)
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            tab.isPrivate -> Icons.Filled.VisibilityOff
                            tab.securityState == SecurityState.SECURE -> Icons.Filled.Lock
                            else -> Icons.Filled.Public
                        },
                        contentDescription = null,
                        tint = if (tab.isPrivate) sabColors.privateAccent else sabColors.subtleText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close tab",
                        tint = sabColors.subtleText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Card Body Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = UrlResolver.extractMonogram(tab.title),
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = if (tab.isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = UrlResolver.extractHost(tab.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (island != null) {
                        Text(
                            text = island.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(island.color.hex)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabRowItem(
    tab: TabUiState,
    isActive: Boolean,
    island: TabIsland?,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val sabColors = LocalSabColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else sabColors.chromeBar)
            .border(
                1.dp,
                if (isActive) MaterialTheme.colorScheme.primary else sabColors.chromeBorder.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(sabColors.chromeSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = UrlResolver.extractMonogram(tab.title),
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (tab.isPrivate) sabColors.privateAccent else MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tab.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = island?.let { "${it.name} • ${UrlResolver.extractHost(tab.url)}" }
                    ?: UrlResolver.extractHost(tab.url),
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close tab",
                tint = sabColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
