package com.simpleaibrowser.sab.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpleaibrowser.sab.core.browser.AddressBarPosition
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import com.simpleaibrowser.sab.ui.components.EngineViewport
import com.simpleaibrowser.sab.ui.components.FindInPageBar
import com.simpleaibrowser.sab.ui.components.OperaBottomBar
import com.simpleaibrowser.sab.ui.components.SabOmniboxEditorOverlay
import com.simpleaibrowser.sab.ui.components.SabSplashOverlay
import com.simpleaibrowser.sab.ui.components.SabTopAddressBar
import com.simpleaibrowser.sab.ui.screens.AddSpeedDialDialog
import com.simpleaibrowser.sab.ui.screens.ExtensionPopupSheet
import com.simpleaibrowser.sab.ui.screens.ExtensionStoreScreen
import com.simpleaibrowser.sab.ui.screens.LibraryScreen
import com.simpleaibrowser.sab.ui.screens.LinkContextBottomSheet
import com.simpleaibrowser.sab.ui.screens.OperaMenuBottomSheet
import com.simpleaibrowser.sab.ui.screens.PrivacyShieldSheet
import com.simpleaibrowser.sab.ui.screens.SettingsScreen
import com.simpleaibrowser.sab.ui.screens.SpeedDialStartPage
import com.simpleaibrowser.sab.ui.screens.TabGalleryScreen
import com.simpleaibrowser.sab.ui.screens.WebPromptDialog
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.theme.SabBrowserTheme
import com.simpleaibrowser.sab.ui.viewmodel.BrowserOverlay
import com.simpleaibrowser.sab.ui.viewmodel.BrowserViewModel
import com.simpleaibrowser.sab.ui.viewmodel.ExtensionsTab
import com.simpleaibrowser.sab.ui.viewmodel.LibraryTab
import kotlinx.coroutines.delay

@Composable
fun SabBrowserApp(
    viewModel: BrowserViewModel,
    initialExternalUrl: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val islands by viewModel.islands.collectAsStateWithLifecycle()
    val recentlyClosed by viewModel.recentlyClosed.collectAsStateWithLifecycle()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isActivePageBookmarked.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val showSplash by viewModel.showSplash.collectAsStateWithLifecycle()
    val isOmniboxEditing by viewModel.isOmniboxEditing.collectAsStateWithLifecycle()
    val omniboxQuery by viewModel.omniboxQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.omniboxSuggestions.collectAsStateWithLifecycle()
    val isFindVisible by viewModel.isFindInPageVisible.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val findResult by viewModel.findResult.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val contextMenuTarget by viewModel.contextMenuTarget.collectAsStateWithLifecycle()
    val activePrompt by viewModel.activePrompt.collectAsStateWithLifecycle()
    val pendingShareRequest by viewModel.pendingShareRequest.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val installedExtensions by viewModel.extensions.installedExtensions.collectAsStateWithLifecycle()
    val extensionActions by viewModel.extensions.extensionActions.collectAsStateWithLifecycle()
    val extensionUpdatesCount by viewModel.extensions.availableUpdatesCount.collectAsStateWithLifecycle()
    val activeExtensionPopup by viewModel.extensions.activePopup.collectAsStateWithLifecycle()

    var pendingFilePrompt by remember { mutableStateOf<WebPromptRequest.FileUpload?>(null) }
    var pendingPermissionPrompt by remember { mutableStateOf<WebPromptRequest.AndroidPermissions?>(null) }

    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val p = pendingFilePrompt
        pendingFilePrompt = null
        p?.onResult(if (uri != null) listOf(uri) else null)
        viewModel.dismissPrompt(p?.id)
    }

    val multiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val p = pendingFilePrompt
        pendingFilePrompt = null
        p?.onResult(uris.ifEmpty { null })
        viewModel.dismissPrompt(p?.id)
    }

    val androidPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.isNotEmpty() && grants.values.all { it }
        val p = pendingPermissionPrompt
        pendingPermissionPrompt = null
        p?.onResult(allGranted)
        viewModel.dismissPrompt(p?.id)
    }

    LaunchedEffect(activePrompt?.id) {
        when (val p = activePrompt) {
            is WebPromptRequest.FileUpload -> {
                pendingFilePrompt = p
                val mimes = p.mimeTypes.toTypedArray()
                if (p.allowMultiple) {
                    multiFileLauncher.launch(mimes)
                } else {
                    singleFileLauncher.launch(mimes)
                }
            }
            is WebPromptRequest.AndroidPermissions -> {
                pendingPermissionPrompt = p
                androidPermissionsLauncher.launch(p.permissions.toTypedArray())
            }
            else -> Unit
        }
    }

    LaunchedEffect(pendingShareRequest) {
        pendingShareRequest?.let { (subject, payload) ->
            shareText(context, subject, payload)
            viewModel.consumeShareRequest()
        }
    }

    LaunchedEffect(initialExternalUrl) {
        if (!initialExternalUrl.isNullOrBlank()) {
            viewModel.handleIncomingExternalUri(initialExternalUrl)
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2100)
            viewModel.clearToast()
        }
    }

    // Immersive Fullscreen Video / Web Fullscreen system bar control
    DisposableEffect(isFullScreen) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullScreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val shouldInterceptBack = isFullScreen ||
        contextMenuTarget != null ||
        isOmniboxEditing ||
        isFindVisible ||
        overlay != BrowserOverlay.None ||
        activeTab.canGoBack ||
        !activeTab.isStartPage

    BackHandler(enabled = shouldInterceptBack) {
        viewModel.goBack()
    }

    val isBottomAddressBar = settings.addressBarPosition == AddressBarPosition.BOTTOM

    SabBrowserTheme(preset = settings.themePreset) {
        val sabColors = LocalSabColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Chrome (hidden in fullscreen video mode)
                if (!isFullScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (activeTab.isPrivate) sabColors.privateSurface else sabColors.chromeBar)
                            .statusBarsPadding()
                    ) {
                        if (!activeTab.isStartPage && !isBottomAddressBar) {
                            SabTopAddressBar(
                                tab = activeTab,
                                adBlockEnabled = settings.adBlockEnabled,
                                onAddressClick = { viewModel.beginOmniboxEdit() },
                                onShieldClick = { viewModel.openOverlay(BrowserOverlay.ShieldSheet) },
                                onReloadOrStop = { viewModel.reloadOrStop() },
                                onSwipeTab = { step -> viewModel.switchToAdjacentTab(step) },
                                showTopDivider = false
                            )
                        }
                    }

                    if (!isBottomAddressBar) {
                        FindInPageBar(
                            visible = isFindVisible && !activeTab.isStartPage,
                            query = findQuery,
                            findResult = findResult,
                            onQueryChange = { viewModel.updateFindQuery(it) },
                            onFindPrevious = { viewModel.findPreviousMatch() },
                            onFindNext = { viewModel.findNextMatch() },
                            onClose = { viewModel.closeFindInPage() }
                        )
                    }
                }

                // Main Content Viewport: Speed Dial Start Page OR Live GeckoView Engine Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val session = activeSession
                    if (session != null && !activeTab.isStartPage) {
                        EngineViewport(
                            engine = viewModel.engine,
                            session = session,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (activeTab.isStartPage) {
                        SpeedDialStartPage(
                            isPrivate = activeTab.isPrivate,
                            settings = settings,
                            speedDials = speedDials,
                            engineInfo = viewModel.engineInfo,
                            onSearchClick = { viewModel.beginOmniboxEdit() },
                            onOpenUrl = { viewModel.navigateToUrl(it) },
                            onAddSpeedDialClick = { viewModel.openOverlay(BrowserOverlay.AddSpeedDial) },
                            onRemoveSpeedDial = { viewModel.removeSpeedDial(it) },
                            onShieldClick = { viewModel.openOverlay(BrowserOverlay.ShieldSheet) },
                            onNewPrivateTab = { viewModel.createNewTab(isPrivate = true, select = true) },
                            onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.BOOKMARKS)) },
                            onOpenHistory = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.HISTORY)) },
                            onOpenDownloads = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.DOWNLOADS)) },
                            onOpenExtensions = { viewModel.openOverlay(BrowserOverlay.Extensions(ExtensionsTab.AMO)) },
                            onOpenSettings = { viewModel.openOverlay(BrowserOverlay.Settings) }
                        )
                    }
                }

                // Bottom Chrome (hidden in fullscreen video mode)
                if (!isFullScreen) {
                    if (isBottomAddressBar) {
                        FindInPageBar(
                            visible = isFindVisible && !activeTab.isStartPage,
                            query = findQuery,
                            findResult = findResult,
                            onQueryChange = { viewModel.updateFindQuery(it) },
                            onFindPrevious = { viewModel.findPreviousMatch() },
                            onFindNext = { viewModel.findNextMatch() },
                            onClose = { viewModel.closeFindInPage() }
                        )

                        if (!activeTab.isStartPage) {
                            SabTopAddressBar(
                                tab = activeTab,
                                adBlockEnabled = settings.adBlockEnabled,
                                onAddressClick = { viewModel.beginOmniboxEdit() },
                                onShieldClick = { viewModel.openOverlay(BrowserOverlay.ShieldSheet) },
                                onReloadOrStop = { viewModel.reloadOrStop() },
                                onSwipeTab = { step -> viewModel.switchToAdjacentTab(step) },
                                showTopDivider = true
                            )
                        }
                    }

                    // Opera Mobile 5-Slot Bottom Navigation Bar
                    OperaBottomBar(
                        activeTab = activeTab,
                        totalTabCount = tabs.count { !it.isPrivate },
                        onBack = { viewModel.goBack() },
                        onHome = { viewModel.navigateHome() },
                        onFastActionClick = { viewModel.beginOmniboxEdit() },
                        onFastActionLongPress = { viewModel.createNewTab(isPrivate = activeTab.isPrivate, select = true) },
                        onOpenTabGallery = { viewModel.openOverlay(BrowserOverlay.TabGallery) },
                        onOpenMenu = { viewModel.openOverlay(BrowserOverlay.OperaMenu) }
                    )
                }
            }

            // Full-width Omnibox Editor & Live Suggestions Overlay
            AnimatedVisibility(
                visible = isOmniboxEditing,
                enter = fadeIn() + slideInVertically { -it / 4 },
                exit = fadeOut() + slideOutVertically { -it / 4 }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                        .statusBarsPadding()
                ) {
                    SabOmniboxEditorOverlay(
                        query = omniboxQuery,
                        activePageUrl = activeTab.url.takeIf { !activeTab.isStartPage },
                        searchEngine = settings.searchEngine,
                        isPrivate = activeTab.isPrivate,
                        suggestions = suggestions,
                        onQueryChange = { viewModel.updateOmniboxQuery(it) },
                        onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                        onCopyUrl = { url -> viewModel.copyTextToClipboard("URL", url) },
                        onSubmit = { viewModel.submitOmnibox(it) },
                        onDismiss = { viewModel.cancelOmniboxEdit() }
                    )
                }
            }

            // Full-Screen Overlays & Bottom Sheets
            when (val currentOverlay = overlay) {
                BrowserOverlay.None -> Unit

                BrowserOverlay.TabGallery -> {
                    TabGalleryScreen(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        islands = islands,
                        recentlyClosed = recentlyClosed,
                        onSelectTab = { viewModel.selectTab(it) },
                        onCloseTab = { viewModel.closeTab(it) },
                        onNewTab = { privateFlag -> viewModel.createNewTab(isPrivate = privateFlag, select = true) },
                        onCloseAll = { privateFlag -> viewModel.closeAllTabs(privateOnly = privateFlag) },
                        onReopenClosed = { viewModel.reopenLastClosedTab() },
                        onCreateIsland = { name -> viewModel.createTabIsland(name) },
                        onRenameIsland = { id, newName -> viewModel.renameIsland(id, newName) },
                        onAssignTabToIsland = { tabId, islandId -> viewModel.assignTabToIsland(tabId, islandId) },
                        onToggleIslandCollapsed = { viewModel.toggleIslandCollapsed(it) },
                        onAddTabToIsland = { islandId ->
                            viewModel.createNewTab(isPrivate = false, islandId = islandId, select = true)
                        },
                        onCloseIsland = { viewModel.closeIsland(it) },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                BrowserOverlay.OperaMenu -> {
                    val userExtCount = installedExtensions.count { !it.isBuiltIn }
                    OperaMenuBottomSheet(
                        activeTab = activeTab,
                        isBookmarked = isBookmarked,
                        settings = settings,
                        extensionActions = extensionActions,
                        installedExtensionsCount = userExtCount,
                        extensionUpdatesCount = extensionUpdatesCount,
                        onDismiss = { viewModel.closeOverlay() },
                        onForward = { viewModel.goForward() },
                        onToggleBookmark = { viewModel.toggleCurrentBookmark() },
                        onReload = { viewModel.reloadOrStop() },
                        onShare = {
                            if (!activeTab.isStartPage) {
                                shareText(context, activeTab.title, activeTab.url)
                            }
                        },
                        onFindInPage = { viewModel.openFindInPage() },
                        onNewTab = { viewModel.createNewTab(isPrivate = false, select = true) },
                        onNewPrivateTab = { viewModel.createNewTab(isPrivate = true, select = true) },
                        onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.BOOKMARKS)) },
                        onOpenHistory = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.HISTORY)) },
                        onOpenDownloads = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.DOWNLOADS)) },
                        onOpenExtensions = { viewModel.openOverlay(BrowserOverlay.Extensions(ExtensionsTab.AMO)) },
                        onTriggerExtensionAction = { extId -> viewModel.extensions.triggerBrowserAction(extId) },
                        onPinToSpeedDial = { viewModel.addCurrentPageToSpeedDial() },
                        onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                        onToggleDesktop = { viewModel.toggleDesktopMode() },
                        onToggleForceDark = { viewModel.toggleForceDarkMode() },
                        onToggleReader = { viewModel.toggleReaderMode() },
                        onAdjustTextZoom = { delta -> viewModel.adjustActiveTabTextZoom(delta) },
                        onResetTextZoom = { viewModel.resetActiveTabTextZoom() },
                        onOpenSettings = { viewModel.openOverlay(BrowserOverlay.Settings) },
                        onReplaySplash = { viewModel.replaySplash() }
                    )
                }

                BrowserOverlay.ShieldSheet -> {
                    PrivacyShieldSheet(
                        activeTab = activeTab,
                        settings = settings,
                        engineInfo = viewModel.engineInfo,
                        onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                        onToggleHttpsOnly = { viewModel.setHttpsOnlyEnabled(it) },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                is BrowserOverlay.Library -> {
                    LibraryScreen(
                        initialTab = currentOverlay.initialTab,
                        bookmarks = bookmarks,
                        history = history,
                        downloads = downloads,
                        onOpenUrl = { viewModel.navigateToUrl(it) },
                        onOpenDownload = { item -> viewModel.openDownloadedFile(context, item) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onDeleteHistory = { viewModel.deleteHistoryItem(it) },
                        onDeleteDownload = { viewModel.deleteDownloadRecord(it) },
                        onClearHistory = { viewModel.clearHistoryOnly() },
                        onClearDownloads = { viewModel.clearDownloads() },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                BrowserOverlay.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        engineInfo = viewModel.engineInfo,
                        installedExtensionsCount = installedExtensions.count { !it.isBuiltIn },
                        onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                        onSelectThemePreset = { viewModel.setThemePreset(it) },
                        onSelectAddressBarPosition = { viewModel.setAddressBarPosition(it) },
                        onToggleRestoreTabs = { viewModel.setRestoreTabsOnStart(it) },
                        onSelectDefaultTextZoom = { viewModel.setDefaultTextZoom(it) },
                        onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                        onToggleHttpsOnly = { viewModel.setHttpsOnlyEnabled(it) },
                        onToggleForceDarkDefault = { viewModel.setForceDarkDefault(it) },
                        onToggleWallpaper = { viewModel.setShowWallpaperOnStart(it) },
                        onToggleAutoUpdateExtensions = { viewModel.setAutoUpdateExtensions(it) },
                        onOpenExtensionsStore = { viewModel.openOverlay(BrowserOverlay.Extensions(ExtensionsTab.INSTALLED)) },
                        onClearAllData = { viewModel.clearAllBrowsingData() },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                is BrowserOverlay.Extensions -> {
                    val amoCategory by viewModel.extensions.amoCategory.collectAsStateWithLifecycle()
                    val amoQuery by viewModel.extensions.amoQuery.collectAsStateWithLifecycle()
                    val amoResults by viewModel.extensions.amoResults.collectAsStateWithLifecycle()
                    val amoLoading by viewModel.extensions.amoLoading.collectAsStateWithLifecycle()
                    val amoError by viewModel.extensions.amoError.collectAsStateWithLifecycle()
                    val ghQuery by viewModel.extensions.githubQuery.collectAsStateWithLifecycle()
                    val ghResults by viewModel.extensions.githubResults.collectAsStateWithLifecycle()
                    val ghLoading by viewModel.extensions.githubLoading.collectAsStateWithLifecycle()
                    val ghError by viewModel.extensions.githubError.collectAsStateWithLifecycle()
                    val selectedGhRepo by viewModel.extensions.selectedGitHubRepo.collectAsStateWithLifecycle()
                    val ghRepoLoading by viewModel.extensions.gitHubRepoLoading.collectAsStateWithLifecycle()
                    val extInstallState by viewModel.extensions.installState.collectAsStateWithLifecycle()

                    ExtensionStoreScreen(
                        initialTab = currentOverlay.initialTab,
                        isGeckoActive = viewModel.engineInfo.isGeckoViewActive,
                        autoUpdateEnabled = settings.autoUpdateExtensions,
                        lastUpdateCheckAt = settings.lastExtensionUpdateCheckAt,
                        amoCategory = amoCategory,
                        amoQuery = amoQuery,
                        amoResults = amoResults,
                        amoLoading = amoLoading,
                        amoError = amoError,
                        githubQuery = ghQuery,
                        githubResults = ghResults,
                        githubLoading = ghLoading,
                        githubError = ghError,
                        selectedGitHubRepo = selectedGhRepo,
                        gitHubRepoLoading = ghRepoLoading,
                        installedExtensions = installedExtensions,
                        updatesCount = extensionUpdatesCount,
                        installState = extInstallState,
                        onSelectAmoCategory = { viewModel.extensions.selectAmoCategory(it) },
                        onAmoQueryChange = { viewModel.extensions.updateAmoQuery(it) },
                        onSearchAmo = { viewModel.extensions.searchAmo() },
                        onGitHubQueryChange = { viewModel.extensions.updateGitHubQuery(it) },
                        onSearchGitHub = { viewModel.extensions.searchGitHub() },
                        onInspectGitHubRepo = { viewModel.extensions.inspectGitHubRepo(it) },
                        onDismissGitHubRepo = { viewModel.extensions.clearSelectedGitHubRepo() },
                        onInstallCatalogItem = { viewModel.extensions.installCatalogItem(it) },
                        onInstallGitHubAsset = { detail, asset -> viewModel.extensions.installGitHubAsset(detail, asset) },
                        onInstallDirectInput = { viewModel.extensions.installFromDirectInput(it) },
                        onToggleEnabled = { id, enabled -> viewModel.extensions.toggleEnabled(id, enabled) },
                        onTogglePrivateBrowsing = { id, allowed -> viewModel.extensions.togglePrivateBrowsing(id, allowed) },
                        onToggleAutoUpdate = { id, auto -> viewModel.extensions.toggleAutoUpdate(id, auto) },
                        onToggleGlobalAutoUpdate = { viewModel.setAutoUpdateExtensions(it) },
                        onCheckUpdatesNow = { viewModel.extensions.checkAndApplyUpdates(silent = false, applyAutomatically = false) },
                        onUpdateAll = { viewModel.extensions.updateAllAvailable() },
                        onUpdateSingle = { viewModel.extensions.updateSingleExtension(it) },
                        onUninstall = { viewModel.extensions.uninstall(it) },
                        onOpenOptions = {
                            viewModel.closeOverlay()
                            viewModel.extensions.openOptionsPage(it)
                        },
                        onOpenUrlInTab = { viewModel.navigateToUrl(it) },
                        onClearInstallState = { viewModel.extensions.clearInstallState() },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                BrowserOverlay.AddSpeedDial -> {
                    AddSpeedDialDialog(
                        onConfirm = { title, url -> viewModel.addSpeedDial(title, url) },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }
            }

            activeExtensionPopup?.let { popup ->
                ExtensionPopupSheet(
                    popup = popup,
                    onDismiss = { viewModel.extensions.dismissPopup() }
                )
            }

            // Long-Press Link / Image Context Sheet
            contextMenuTarget?.let { target ->
                LinkContextBottomSheet(
                    target = target,
                    onOpenInNewTab = { url -> viewModel.openLinkInNewTab(url, inIsland = false, isPrivate = false) },
                    onOpenInIsland = { url -> viewModel.openLinkInNewTab(url, inIsland = true, isPrivate = false) },
                    onOpenInPrivateTab = { url -> viewModel.openLinkInNewTab(url, inIsland = false, isPrivate = true) },
                    onCopyUrl = { url -> viewModel.copyTextToClipboard("link", url) },
                    onDownloadUrl = { url -> viewModel.downloadUrl(url) },
                    onShareUrl = { url -> shareText(context, target.title ?: url, url) },
                    onDismiss = { viewModel.dismissContextMenu() }
                )
            }

            // Native JS Alert / Confirm / Prompt & Site Permission Dialog
            activePrompt?.let { prompt ->
                WebPromptDialog(
                    prompt = prompt,
                    onDismissComplete = { viewModel.dismissPrompt(prompt.id) }
                )
            }

            // Floating Feedback Pill Toast
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isBottomAddressBar) 144.dp else 86.dp)
            ) {
                Surface(
                    color = sabColors.chromeSurfaceElevated,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            // Signature SAB Launch Animation Overlay
            SabSplashOverlay(
                visible = showSplash,
                engineInfo = viewModel.engineInfo,
                onFinished = { viewModel.dismissSplash() }
            )
        }
    }
}

private fun shareText(context: android.content.Context, subject: String, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share"))
}
