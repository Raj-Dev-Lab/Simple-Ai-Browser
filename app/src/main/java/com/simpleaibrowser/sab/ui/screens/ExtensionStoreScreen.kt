package com.simpleaibrowser.sab.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.simpleaibrowser.sab.core.browser.AmoCategory
import com.simpleaibrowser.sab.core.browser.ExtensionCatalogItem
import com.simpleaibrowser.sab.core.browser.ExtensionInstallState
import com.simpleaibrowser.sab.core.browser.ExtensionPopupSession
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.GitHubReleaseXpiAsset
import com.simpleaibrowser.sab.core.browser.InstalledExtensionInfo
import com.simpleaibrowser.sab.data.extensions.GitHubRepoDetail
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.viewmodel.ExtensionsTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExtensionStoreScreen(
    initialTab: ExtensionsTab,
    isGeckoActive: Boolean,
    autoUpdateEnabled: Boolean,
    lastUpdateCheckAt: Long,
    amoCategory: AmoCategory,
    amoQuery: String,
    amoResults: List<ExtensionCatalogItem>,
    amoLoading: Boolean,
    amoError: String?,
    githubQuery: String,
    githubResults: List<ExtensionCatalogItem>,
    githubLoading: Boolean,
    githubError: String?,
    selectedGitHubRepo: GitHubRepoDetail?,
    gitHubRepoLoading: Boolean,
    installedExtensions: List<InstalledExtensionInfo>,
    updatesCount: Int,
    installState: ExtensionInstallState,
    onSelectAmoCategory: (AmoCategory) -> Unit,
    onAmoQueryChange: (String) -> Unit,
    onSearchAmo: () -> Unit,
    onGitHubQueryChange: (String) -> Unit,
    onSearchGitHub: () -> Unit,
    onInspectGitHubRepo: (String) -> Unit,
    onDismissGitHubRepo: () -> Unit,
    onInstallCatalogItem: (ExtensionCatalogItem) -> Unit,
    onInstallGitHubAsset: (GitHubRepoDetail, GitHubReleaseXpiAsset) -> Unit,
    onInstallDirectInput: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onTogglePrivateBrowsing: (String, Boolean) -> Unit,
    onToggleAutoUpdate: (String, Boolean) -> Unit,
    onToggleGlobalAutoUpdate: (Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onUpdateAll: () -> Unit,
    onUpdateSingle: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenOptions: (String) -> Unit,
    onOpenUrlInTab: (String) -> Unit,
    onClearInstallState: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sabColors = LocalSabColors.current
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var directInput by remember { mutableStateOf("") }
    var pendingUninstall by remember { mutableStateOf<InstalledExtensionInfo?>(null) }

    val installedById = remember(installedExtensions) {
        installedExtensions.associateBy { it.id }
    }
    val userInstalledCount = remember(installedExtensions) {
        installedExtensions.count { !it.isBuiltIn }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("extensions_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Extension Store", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (isGeckoActive) "Mozilla AMO & GitHub WebExtensions"
                            else "Requires Mozilla GeckoView engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                    }
                }
                TextButton(
                    onClick = if (updatesCount > 0) onUpdateAll else onCheckUpdatesNow,
                    enabled = isGeckoActive && userInstalledCount > 0,
                    modifier = Modifier.testTag("extensions_check_updates_btn")
                ) {
                    Icon(
                        imageVector = if (updatesCount > 0) Icons.Filled.SystemUpdateAlt else Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (updatesCount > 0) "Update ($updatesCount)" else "Check Updates")
                }
            }

            // 3-Segment Store Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExtensionsTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val label = when (tab) {
                        ExtensionsTab.INSTALLED -> if (updatesCount > 0) "Installed ($userInstalledCount • $updatesCount↑)"
                        else "Installed ($userInstalledCount)"
                        else -> tab.label
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .clickable { activeTab = tab }
                            .testTag("ext_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else sabColors.subtleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Install / Update Status Banner
            when (installState) {
                ExtensionInstallState.Idle -> Unit
                is ExtensionInstallState.Installing -> {
                    StatusProgressBanner(text = "Installing ${installState.name}...")
                }
                is ExtensionInstallState.CheckingUpdates -> {
                    StatusProgressBanner(text = installState.progressLabel)
                }
                is ExtensionInstallState.Success -> {
                    StatusMessageBanner(
                        text = installState.message,
                        accent = sabColors.shieldGreen,
                        onDismiss = onClearInstallState
                    )
                }
                is ExtensionInstallState.Error -> {
                    StatusMessageBanner(
                        text = installState.message,
                        accent = MaterialTheme.colorScheme.error,
                        onDismiss = onClearInstallState
                    )
                }
            }

            if (gitHubRepoLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (activeTab) {
                ExtensionsTab.AMO -> {
                    AmoStoreTab(
                        category = amoCategory,
                        query = amoQuery,
                        results = amoResults,
                        loading = amoLoading,
                        error = amoError,
                        installedById = installedById,
                        isGeckoActive = isGeckoActive,
                        onSelectCategory = onSelectAmoCategory,
                        onQueryChange = onAmoQueryChange,
                        onSearch = onSearchAmo,
                        onInstall = onInstallCatalogItem,
                        onOpenDetailsUrl = onOpenUrlInTab,
                        modifier = Modifier.weight(1f)
                    )
                }

                ExtensionsTab.GITHUB -> {
                    GitHubStoreTab(
                        directInput = directInput,
                        onDirectInputChange = { directInput = it },
                        onInstallDirect = {
                            onInstallDirectInput(directInput)
                        },
                        query = githubQuery,
                        onQueryChange = onGitHubQueryChange,
                        onSearch = onSearchGitHub,
                        results = githubResults,
                        loading = githubLoading,
                        error = githubError,
                        isGeckoActive = isGeckoActive,
                        onInspectRepo = onInspectGitHubRepo,
                        onInstallCatalogItem = onInstallCatalogItem,
                        modifier = Modifier.weight(1f)
                    )
                }

                ExtensionsTab.INSTALLED -> {
                    InstalledExtensionsTab(
                        installed = installedExtensions,
                        autoUpdateEnabled = autoUpdateEnabled,
                        lastCheckAt = lastUpdateCheckAt,
                        updatesCount = updatesCount,
                        onToggleGlobalAutoUpdate = onToggleGlobalAutoUpdate,
                        onCheckUpdatesNow = onCheckUpdatesNow,
                        onUpdateAll = onUpdateAll,
                        onUpdateSingle = onUpdateSingle,
                        onToggleEnabled = onToggleEnabled,
                        onTogglePrivateBrowsing = onTogglePrivateBrowsing,
                        onToggleAutoUpdate = onToggleAutoUpdate,
                        onOpenOptions = onOpenOptions,
                        onRequestUninstall = { pendingUninstall = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    selectedGitHubRepo?.let { detail ->
        GitHubReleasesDialog(
            detail = detail,
            isGeckoActive = isGeckoActive,
            onInstallAsset = { asset -> onInstallGitHubAsset(detail, asset) },
            onOpenRepoPage = { onOpenUrlInTab("https://github.com/${detail.ownerRepo}") },
            onDismiss = onDismissGitHubRepo
        )
    }

    pendingUninstall?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("Uninstall ${target.name}?") },
            text = { Text("Remove this extension and its stored configuration from SAB?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUninstall(target.id)
                        pendingUninstall = null
                    }
                ) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AmoStoreTab(
    category: AmoCategory,
    query: String,
    results: List<ExtensionCatalogItem>,
    loading: Boolean,
    error: String?,
    installedById: Map<String, InstalledExtensionInfo>,
    isGeckoActive: Boolean,
    onSelectCategory: (AmoCategory) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onInstall: (ExtensionCatalogItem) -> Unit,
    onOpenDetailsUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search Mozilla Add-ons (uBlock, Dark Reader...)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        onSearch()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("amo_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmoCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat && query.isBlank(),
                    onClick = { onSelectCategory(cat) },
                    label = { Text(cat.label) }
                )
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            ErrorRetryCard(message = error, onRetry = onSearch)
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Android-compatible extensions found.", color = sabColors.subtleText)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    val existing = installedById[item.id]
                    CatalogExtensionCard(
                        item = item,
                        installedInfo = existing,
                        isGeckoActive = isGeckoActive,
                        actionLabel = if (existing != null) "Installed v${existing.version}" else "Install",
                        onPrimaryClick = { onInstall(item) },
                        onOpenHomepage = {
                            item.homepageUrl?.let(onOpenDetailsUrl)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubStoreTab(
    directInput: String,
    onDirectInputChange: (String) -> Unit,
    onInstallDirect: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<ExtensionCatalogItem>,
    loading: Boolean,
    error: String?,
    isGeckoActive: Boolean,
    onInspectRepo: (String) -> Unit,
    onInstallCatalogItem: (ExtensionCatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Direct GitHub Repo / .xpi Package Installer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(sabColors.chromeSurfaceElevated)
                .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Direct GitHub Repo or .xpi URL", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "Install Mozilla-signed extensions directly from GitHub Releases (not listed on AMO), e.g. owner/repo or a direct .xpi link.",
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = directInput,
                    onValueChange = onDirectInputChange,
                    placeholder = { Text("owner/repo or https://.../ext.xpi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onInstallDirect() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("github_direct_input")
                )
                Button(
                    onClick = onInstallDirect,
                    enabled = isGeckoActive && directInput.isNotBlank(),
                    modifier = Modifier.testTag("github_direct_install_btn")
                ) {
                    Text("Get")
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search GitHub Firefox WebExtensions...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("github_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            ErrorRetryCard(message = error, onRetry = onSearch)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    CatalogExtensionCard(
                        item = item,
                        installedInfo = null,
                        isGeckoActive = isGeckoActive,
                        actionLabel = if (item.hasInstallableXpi) "Install" else "Releases",
                        onPrimaryClick = {
                            if (item.hasInstallableXpi) onInstallCatalogItem(item)
                            else item.sourceRepo?.let(onInspectRepo)
                        },
                        onOpenHomepage = { item.sourceRepo?.let(onInspectRepo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionsTab(
    installed: List<InstalledExtensionInfo>,
    autoUpdateEnabled: Boolean,
    lastCheckAt: Long,
    updatesCount: Int,
    onToggleGlobalAutoUpdate: (Boolean) -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onUpdateAll: () -> Unit,
    onUpdateSingle: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onTogglePrivateBrowsing: (String, Boolean) -> Unit,
    onToggleAutoUpdate: (String, Boolean) -> Unit,
    onOpenOptions: (String) -> Unit,
    onRequestUninstall: (InstalledExtensionInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val sabColors = LocalSabColors.current
    val lastCheckText = remember(lastCheckAt) {
        if (lastCheckAt <= 0L) "Never checked"
        else "Last checked ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(lastCheckAt))}"
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item(key = "auto_update_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(sabColors.chromeSurfaceElevated)
                    .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic Extension Updates", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Checks Mozilla AMO & GitHub releases automatically every 12 hours • $lastCheckText",
                            style = MaterialTheme.typography.bodySmall,
                            color = sabColors.subtleText
                        )
                    }
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = onToggleGlobalAutoUpdate
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCheckUpdatesNow) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Now")
                    }
                    if (updatesCount > 0) {
                        Button(onClick = onUpdateAll) {
                            Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update All ($updatesCount)")
                        }
                    }
                }
            }
        }

        if (installed.isEmpty()) {
            item(key = "empty_installed") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Extension, contentDescription = null, tint = sabColors.subtleText, modifier = Modifier.size(40.dp))
                    Text("No add-ons installed yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Browse the Mozilla Store or GitHub tabs to install extensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                }
            }
        } else {
            items(installed, key = { it.id }) { ext ->
                InstalledExtensionCard(
                    info = ext,
                    onToggleEnabled = { onToggleEnabled(ext.id, it) },
                    onTogglePrivateBrowsing = { onTogglePrivateBrowsing(ext.id, it) },
                    onToggleAutoUpdate = { onToggleAutoUpdate(ext.id, it) },
                    onUpdateNow = { onUpdateSingle(ext.id) },
                    onOpenOptions = { onOpenOptions(ext.id) },
                    onUninstall = { onRequestUninstall(ext) }
                )
            }
        }
    }
}

@Composable
private fun CatalogExtensionCard(
    item: ExtensionCatalogItem,
    installedInfo: InstalledExtensionInfo?,
    isGeckoActive: Boolean,
    actionLabel: String,
    onPrimaryClick: () -> Unit,
    onOpenHomepage: () -> Unit
) {
    val sabColors = LocalSabColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sabColors.chromeSurfaceElevated)
            .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenHomepage)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtensionIconBox(iconUrl = item.iconUrl, title = item.name)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isRecommended) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Recommended",
                            tint = sabColors.shieldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(item.author)
                        if (item.source == ExtensionSource.AMO && item.rating > 0.0) {
                            append(" • ★ ${String.format(Locale.US, "%.1f", item.rating)}")
                        } else if (item.source == ExtensionSource.GITHUB && item.dailyUsersOrStars > 0L) {
                            append(" • ★ ${item.dailyUsersOrStars}")
                        }
                        if (item.version.isNotBlank()) {
                            append(" • ${item.version}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = sabColors.subtleText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (installedInfo != null) {
                OutlinedButton(
                    onClick = onPrimaryClick,
                    enabled = false
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Installed", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onPrimaryClick,
                    enabled = isGeckoActive
                ) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
        }
        if (item.summary.isNotBlank()) {
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    info: InstalledExtensionInfo,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePrivateBrowsing: (Boolean) -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    onOpenOptions: () -> Unit,
    onUninstall: () -> Unit
) {
    val sabColors = LocalSabColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(sabColors.chromeSurfaceElevated)
            .border(0.8.dp, sabColors.chromeBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtensionIconBox(
                iconBitmap = info.iconBitmap,
                iconUrl = info.iconUrl,
                title = info.name
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${info.version} • ${info.source.displayName}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = sabColors.subtleText
                )
            }
            if (!info.isBuiltIn) {
                Switch(checked = info.enabled, onCheckedChange = onToggleEnabled)
            }
        }

        if (info.disabledReason != null) {
            Text(
                text = info.disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (info.description.isNotBlank()) {
            Text(
                text = info.description,
                style = MaterialTheme.typography.bodySmall,
                color = sabColors.subtleText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (info.availableUpdateVersion != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(sabColors.shieldGreen.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Update ready: ${info.availableUpdateVersion}",
                    style = MaterialTheme.typography.labelMedium,
                    color = sabColors.shieldGreen
                )
                Button(onClick = onUpdateNow) {
                    Text("Update")
                }
            }
        }

        if (!info.isBuiltIn) {
            HorizontalDivider(color = sabColors.chromeBorder.copy(alpha = 0.45f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = info.allowedInPrivateBrowsing,
                        onClick = { onTogglePrivateBrowsing(!info.allowedInPrivateBrowsing) },
                        label = { Text("Private", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = info.autoUpdateEnabled,
                        onClick = { onToggleAutoUpdate(!info.autoUpdateEnabled) },
                        label = { Text("Auto-update", fontSize = 11.sp) }
                    )
                }
                Row {
                    if (!info.optionsPageUrl.isNullOrBlank()) {
                        IconButton(onClick = onOpenOptions) {
                            Icon(Icons.Filled.Settings, contentDescription = "Extension options")
                        }
                    }
                    IconButton(onClick = onUninstall) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Uninstall extension",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubReleasesDialog(
    detail: GitHubRepoDetail,
    isGeckoActive: Boolean,
    onInstallAsset: (GitHubReleaseXpiAsset) -> Unit,
    onOpenRepoPage: () -> Unit,
    onDismiss: () -> Unit
) {
    val sabColors = LocalSabColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(detail.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${detail.ownerRepo} • ★ ${detail.stars}",
                    style = MaterialTheme.typography.bodySmall,
                    color = sabColors.subtleText
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (detail.description.isNotBlank()) {
                    item {
                        Text(detail.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (detail.xpiAssets.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(sabColors.chromeSurfaceElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No .xpi release asset found",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "This repository has not attached a Mozilla .xpi package in its GitHub Releases yet. You can open its GitHub page to check installation instructions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = sabColors.subtleText
                            )
                        }
                    }
                } else {
                    items(detail.xpiAssets, key = { it.downloadUrl }) { asset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(sabColors.chromeSurfaceElevated)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(asset.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Release ${asset.releaseTag}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = sabColors.subtleText
                                    )
                                }
                                Button(
                                    onClick = { onInstallAsset(asset) },
                                    enabled = isGeckoActive
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Install")
                                }
                            }
                            if (asset.releaseNotes.isNotBlank()) {
                                Text(
                                    text = asset.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = sabColors.subtleText,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenRepoPage) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPopupSheet(
    popup: ExtensionPopupSession,
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
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = popup.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close extension popup")
                }
            }
            AndroidView(
                factory = { ctx -> popup.createPopupView(ctx) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
    }
}

@Composable
private fun ExtensionIconBox(
    iconBitmap: android.graphics.Bitmap? = null,
    iconUrl: String? = null,
    title: String
) {
    val sabColors = LocalSabColors.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(sabColors.chromeBar),
        contentAlignment = Alignment.Center
    ) {
        when {
            iconBitmap != null -> {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !iconUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusProgressBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusMessageBanner(
    text: String,
    accent: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ErrorRetryCard(message: String, onRetry: () -> Unit) {
    val sabColors = LocalSabColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = sabColors.subtleText)
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
