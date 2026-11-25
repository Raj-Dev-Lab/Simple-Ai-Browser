package com.simpleaibrowser.sab.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simpleaibrowser.sab.ui.components.EngineViewport
import com.simpleaibrowser.sab.ui.components.FindInPageBar
import com.simpleaibrowser.sab.ui.components.OperaBottomBar
import com.simpleaibrowser.sab.ui.components.SabOmniboxEditorOverlay
import com.simpleaibrowser.sab.ui.components.SabSplashOverlay
import com.simpleaibrowser.sab.ui.components.SabTopAddressBar
import com.simpleaibrowser.sab.ui.screens.AddSpeedDialDialog
import com.simpleaibrowser.sab.ui.screens.LibraryScreen
import com.simpleaibrowser.sab.ui.screens.OperaMenuBottomSheet
import com.simpleaibrowser.sab.ui.screens.PrivacyShieldSheet
import com.simpleaibrowser.sab.ui.screens.SettingsScreen
import com.simpleaibrowser.sab.ui.screens.SpeedDialStartPage
import com.simpleaibrowser.sab.ui.screens.TabGalleryScreen
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import com.simpleaibrowser.sab.ui.theme.SabBrowserTheme
import com.simpleaibrowser.sab.ui.viewmodel.BrowserOverlay
import com.simpleaibrowser.sab.ui.viewmodel.BrowserViewModel
import com.simpleaibrowser.sab.ui.viewmodel.LibraryTab
import kotlinx.coroutines.delay

@Composable
fun SabBrowserApp(
    viewModel: BrowserViewModel,
    initialExternalUrl: String? = null
) {
    val context = LocalContext.current
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
    val isBookmarked by viewModel.isActivePageBookmarked.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val showSplash by viewModel.showSplash.collectAsStateWithLifecycle()
    val isOmniboxEditing by viewModel.isOmniboxEditing.collectAsStateWithLifecycle()
    val omniboxQuery by viewModel.omniboxQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.omniboxSuggestions.collectAsStateWithLifecycle()
    val isFindVisible by viewModel.isFindInPageVisible.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

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

    val shouldInterceptBack = isOmniboxEditing ||
        isFindVisible ||
        overlay != BrowserOverlay.None ||
        activeTab.canGoBack ||
        !activeTab.isStartPage

    BackHandler(enabled = shouldInterceptBack) {
        viewModel.goBack()
    }

    SabBrowserTheme(preset = settings.themePreset) {
        val sabColors = LocalSabColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status-bar safe top chrome
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (activeTab.isPrivate) sabColors.privateSurface else sabColors.chromeBar)
                        .statusBarsPadding()
                ) {
                    if (!activeTab.isStartPage) {
                        SabTopAddressBar(
                            tab = activeTab,
                            adBlockEnabled = settings.adBlockEnabled,
                            onAddressClick = { viewModel.beginOmniboxEdit() },
                            onShieldClick = { viewModel.openOverlay(BrowserOverlay.ShieldSheet) },
                            onReloadOrStop = { viewModel.reloadOrStop() }
                        )
                    }
                }

                FindInPageBar(
                    visible = isFindVisible && !activeTab.isStartPage,
                    query = findQuery,
                    onQueryChange = { viewModel.updateFindQuery(it) },
                    onClose = { viewModel.closeFindInPage() }
                )

                // Main Content Viewport: Speed Dial Start Page OR Live GeckoView Engine Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val session = activeSession
                    if (session != null) {
                        // Keep the native GeckoView attached when browsing web pages
                        if (!activeTab.isStartPage) {
                            EngineViewport(
                                engine = viewModel.engine,
                                session = session,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                            onOpenSettings = { viewModel.openOverlay(BrowserOverlay.Settings) }
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
                        searchEngine = settings.searchEngine,
                        isPrivate = activeTab.isPrivate,
                        suggestions = suggestions,
                        onQueryChange = { viewModel.updateOmniboxQuery(it) },
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
                        onToggleIslandCollapsed = { viewModel.toggleIslandCollapsed(it) },
                        onAddTabToIsland = { islandId ->
                            viewModel.createNewTab(isPrivate = false, islandId = islandId, select = true)
                        },
                        onCloseIsland = { viewModel.closeIsland(it) },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                BrowserOverlay.OperaMenu -> {
                    OperaMenuBottomSheet(
                        activeTab = activeTab,
                        isBookmarked = isBookmarked,
                        settings = settings,
                        onDismiss = { viewModel.closeOverlay() },
                        onForward = { viewModel.goForward() },
                        onToggleBookmark = { viewModel.toggleCurrentBookmark() },
                        onReload = { viewModel.reloadOrStop() },
                        onShare = {
                            if (!activeTab.isStartPage) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, activeTab.url)
                                    putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share page"))
                            }
                        },
                        onFindInPage = { viewModel.openFindInPage() },
                        onNewTab = { viewModel.createNewTab(isPrivate = false, select = true) },
                        onNewPrivateTab = { viewModel.createNewTab(isPrivate = true, select = true) },
                        onOpenBookmarks = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.BOOKMARKS)) },
                        onOpenHistory = { viewModel.openOverlay(BrowserOverlay.Library(LibraryTab.HISTORY)) },
                        onPinToSpeedDial = { viewModel.addCurrentPageToSpeedDial() },
                        onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                        onToggleDesktop = { viewModel.toggleDesktopMode() },
                        onToggleForceDark = { viewModel.toggleForceDarkMode() },
                        onToggleReader = { viewModel.toggleReaderMode() },
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
                        onOpenUrl = { viewModel.navigateToUrl(it) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onDeleteHistory = { viewModel.deleteHistoryItem(it) },
                        onClearHistory = { viewModel.clearAllBrowsingData() },
                        onDismiss = { viewModel.closeOverlay() }
                    )
                }

                BrowserOverlay.Settings -> {
                    SettingsScreen(
                        settings = settings,
                        engineInfo = viewModel.engineInfo,
                        onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                        onSelectThemePreset = { viewModel.setThemePreset(it) },
                        onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                        onToggleHttpsOnly = { viewModel.setHttpsOnlyEnabled(it) },
                        onToggleForceDarkDefault = { viewModel.setForceDarkDefault(it) },
                        onToggleWallpaper = { viewModel.setShowWallpaperOnStart(it) },
                        onClearAllData = { viewModel.clearAllBrowsingData() },
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

            // Floating Feedback Pill Toast
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp)
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
