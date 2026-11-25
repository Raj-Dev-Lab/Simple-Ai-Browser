package com.rajdevlab.sab.core.browser

import kotlinx.coroutines.flow.StateFlow

data class TabUiState(
    val id: String,
    val islandId: String? = null,
    val title: String = "Speed Dial",
    val url: String = "sab://start",
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = true,
    val blockedTrackersCount: Int = 0
)

data class PageContextSnapshot(
    val url: String,
    val title: String,
    val textContent: String,
    val selectedText: String? = null
)

interface BrowserTabSession {
    val state: StateFlow<TabUiState>
    fun loadUrl(urlOrQuery: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()
    suspend fun extractPageText(maxChars: Int = 12_000): PageContextSnapshot
    fun setDesktopMode(enabled: Boolean)
    fun setReaderMode(enabled: Boolean)
    fun setForceDarkMode(enabled: Boolean)
    fun release()
}

interface BrowserEngine {
    val engineName: String
    fun createSession(tabId: String, isPrivate: Boolean): BrowserTabSession
    fun setAdBlockEnabled(enabled: Boolean)
    fun clearBrowsingData()
}
