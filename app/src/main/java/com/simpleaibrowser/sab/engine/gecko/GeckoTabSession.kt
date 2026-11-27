package com.simpleaibrowser.sab.engine.gecko

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.simpleaibrowser.sab.core.browser.BrowserTabSession
import com.simpleaibrowser.sab.core.browser.ContextMenuTarget
import com.simpleaibrowser.sab.core.browser.DetectedPageMedia
import com.simpleaibrowser.sab.core.browser.DownloadKind
import com.simpleaibrowser.sab.core.browser.DownloadRequest
import com.simpleaibrowser.sab.core.browser.DownloadSourceType
import com.simpleaibrowser.sab.core.browser.FindInPageResult
import com.simpleaibrowser.sab.core.browser.PageContextSnapshot
import com.simpleaibrowser.sab.core.browser.PromptChoiceOption
import com.simpleaibrowser.sab.core.browser.SAB_START_URL
import com.simpleaibrowser.sab.core.browser.SecurityState
import com.simpleaibrowser.sab.core.browser.SessionCallbacks
import com.simpleaibrowser.sab.core.browser.TabUiState
import com.simpleaibrowser.sab.core.browser.UrlResolver
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import com.simpleaibrowser.sab.downloads.DownloadFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PageExtractionController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val SESSION_TAG = "SabGeckoSession"

class GeckoTabSession(
    override val id: String,
    override val isPrivate: Boolean,
    private val appContext: Context,
    private val runtime: GeckoRuntime,
    extensionsController: GeckoExtensionsController,
    private val callbacks: SessionCallbacks
) : BrowserTabSession {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(
        TabUiState(
            id = id,
            title = if (isPrivate) "Private Tab" else "Speed Dial",
            url = SAB_START_URL,
            isPrivate = isPrivate,
            securityState = SecurityState.INTERNAL
        )
    )
    override val state: StateFlow<TabUiState> = _state.asStateFlow()

    private val geckoSession: GeckoSession = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
    )

    @Volatile
    private var nativePort: WebExtension.Port? = null

    @Volatile
    private var lastSessionState: GeckoSession.SessionState? = null

    @Volatile
    private var isOpened = false

    @Volatile
    private var isReleased = false

    @Volatile
    private var recoveryAttempts = 0

    @Volatile
    private var doNotTrackSignal: Boolean = true

    @Volatile
    private var gpcSignal: Boolean = true

    private val pendingSnapshotRequests = ConcurrentHashMap<String, CompletableDeferred<PageContextSnapshot>>()
    private val pendingMediaRequests = ConcurrentHashMap<String, CompletableDeferred<List<DetectedPageMedia>>>()

    init {
        installDelegates()
        ensureSessionOpened()
        extensionsController.registerBuiltInListener { ext ->
            if (!isReleased) {
                runOnMain { attachBridgeMessageDelegate(ext) }
            }
        }
    }

    internal fun geckoSessionRef(): GeckoSession = geckoSession

    internal fun applyPrivacySignals(
        useTrackingProtection: Boolean,
        doNotTrack: Boolean,
        globalPrivacyControl: Boolean
    ) {
        doNotTrackSignal = doNotTrack
        gpcSignal = globalPrivacyControl
        runCatching { geckoSession.settings.useTrackingProtection = useTrackingProtection }
        syncBridgeModes()
    }

    private fun ensureSessionOpened() {
        if (!isOpened && !isReleased) {
            geckoSession.open(runtime)
            isOpened = true
        }
    }

    private fun installDelegates() {
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (url.isNullOrBlank() || url == "about:blank") return
                _state.update { current ->
                    current.copy(
                        url = url,
                        securityState = UrlResolver.securityForUrl(url),
                        lastVisitedTimestamp = System.currentTimeMillis()
                    )
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                _state.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                _state.update { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                if (uri.startsWith("magnet:", ignoreCase = true)) {
                    callbacks.onDownloadRequested(
                        DownloadRequest(
                            url = uri,
                            fileName = UrlResolver.guessFileName(uri, "application/x-bittorrent"),
                            mimeType = "application/x-bittorrent",
                            sourceType = DownloadSourceType.MAGNET,
                            referrerUrl = _state.value.url
                        )
                    )
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW &&
                    (uri.startsWith("http://") || uri.startsWith("https://"))
                ) {
                    callbacks.onNewTabRequested(uri, id, isPrivate)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(
                session: GeckoSession,
                uri: String
            ): GeckoResult<GeckoSession>? {
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    callbacks.onNewTabRequested(uri, id, isPrivate)
                }
                return null
            }
        }

        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (url == "about:blank") return
                _state.update {
                    it.copy(
                        url = url,
                        isLoading = true,
                        progress = 12,
                        securityState = UrlResolver.securityForUrl(url)
                    )
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                _state.update { it.copy(progress = progress.coerceIn(0, 100)) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (success) recoveryAttempts = 0
                val snapshot = _state.value
                _state.update {
                    it.copy(
                        isLoading = false,
                        progress = 100,
                        lastVisitedTimestamp = System.currentTimeMillis()
                    )
                }
                if (success && !isPrivate && !snapshot.isStartPage) {
                    callbacks.onHistoryVisit(snapshot.url, snapshot.title)
                }
                syncBridgeModes()
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                val sec = when {
                    _state.value.isStartPage -> SecurityState.INTERNAL
                    securityInfo.isSecure -> SecurityState.SECURE
                    else -> SecurityState.INSECURE
                }
                _state.update { it.copy(securityState = sec) }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState
            ) {
                lastSessionState = sessionState
            }
        }

        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val cleanTitle = title?.trim().orEmpty()
                if (cleanTitle.isNotEmpty() && !_state.value.isStartPage) {
                    _state.update { it.copy(title = cleanTitle) }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                callbacks.onFullScreenChanged(fullScreen)
            }

            override fun onCrash(session: GeckoSession) {
                recoverCrashedSession("Tab recovered after content crash")
            }

            override fun onKill(session: GeckoSession) {
                recoverCrashedSession("Tab reloaded after background memory reclaim")
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                handleExternalResponse(response)
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                val link = element.linkUri?.takeIf { it.isNotBlank() }
                val media = element.srcUri?.takeIf { it.isNotBlank() }
                if (link == null && media == null) return
                callbacks.onContextMenu(
                    ContextMenuTarget(
                        tabId = id,
                        linkUrl = link,
                        mediaUrl = media,
                        title = element.title?.takeIf { it.isNotBlank() }
                            ?: element.altText?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        geckoSession.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(
                session: GeckoSession,
                event: ContentBlocking.BlockEvent
            ) {
                _state.update { it.copy(blockedTrackersCount = it.blockedTrackersCount + 1) }
                callbacks.onTrackerBlocked(id)
            }
        }

        geckoSession.promptDelegate = buildPromptDelegate()
        geckoSession.permissionDelegate = buildPermissionDelegate()
    }

    private fun handleExternalResponse(response: WebResponse) {
        val uri = response.uri
        if (uri.isBlank()) {
            runCatching { response.body?.close() }
            return
        }
        val headers = response.headers
        val mimeType = headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value?.substringBefore(";")?.trim()
        val contentLength = headers.entries.firstOrNull {
            it.key.equals("content-length", ignoreCase = true)
        }?.value?.trim()?.toLongOrNull() ?: -1L
        val disposition = headers.entries.firstOrNull {
            it.key.equals("content-disposition", ignoreCase = true)
        }?.value
        val fileName = disposition?.let { parseDispositionFileName(it) }
            ?: UrlResolver.guessFileName(uri, mimeType)
        val referrer = _state.value.url.takeIf { it.startsWith("http") }
        val isTorrent = mimeType.equals("application/x-bittorrent", ignoreCase = true) ||
            fileName.endsWith(".torrent", ignoreCase = true)

        val bodyStream = response.body
        if (bodyStream == null) {
            callbacks.onDownloadRequested(
                DownloadRequest(
                    url = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    contentLength = contentLength,
                    sourceType = DownloadSourceType.HTTP,
                    referrerUrl = referrer
                )
            )
            return
        }

        // Stream already-open WebResponse body into SAB staging vault (supports blob:/POST/auth/torrent responses)
        sessionScope.launch {
            val fileSystem = DownloadFileSystem(appContext)
            val safeName = DownloadFileSystem.sanitizeFileName(fileName)
            val stagedFile = File(fileSystem.metadataDir(), "stream_${System.currentTimeMillis()}_$safeName")
            val stagedResult = runCatching {
                bodyStream.use { input ->
                    var bytesWritten = 0L
                    FileOutputStream(stagedFile).use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            bytesWritten += read
                        }
                        out.flush()
                    }
                    bytesWritten
                }
            }
            stagedResult.onSuccess { bytesWritten ->
                callbacks.onDownloadRequested(
                    DownloadRequest(
                        url = uri,
                        fileName = safeName,
                        mimeType = mimeType,
                        contentLength = if (bytesWritten > 0L) bytesWritten else contentLength,
                        sourceType = if (isTorrent) DownloadSourceType.TORRENT_FILE else DownloadSourceType.OPEN_STREAM,
                        referrerUrl = referrer,
                        stagedCacheFilePath = stagedFile.absolutePath
                    )
                )
            }.onFailure { err ->
                runCatching { stagedFile.delete() }
                Log.w(SESSION_TAG, "Streaming response body failed (${err.message}); falling back to HTTP Download Manager")
                callbacks.onDownloadRequested(
                    DownloadRequest(
                        url = uri,
                        fileName = safeName,
                        mimeType = mimeType,
                        contentLength = contentLength,
                        sourceType = DownloadSourceType.HTTP,
                        referrerUrl = referrer
                    )
                )
            }
        }
    }

    private fun recoverCrashedSession(reason: String) {
        if (isReleased || recoveryAttempts >= 2) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        recoveryAttempts += 1
        mainHandler.post {
            if (isReleased) return@post
            runCatching {
                nativePort = null
                geckoSession.open(runtime)
                isOpened = true
                val stateToRestore = lastSessionState
                val currentUrl = _state.value.url
                if (stateToRestore != null) {
                    geckoSession.restoreState(stateToRestore)
                } else if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
                    geckoSession.loadUri(currentUrl)
                }
                callbacks.onSessionRecovered(id, reason)
            }.onFailure {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun buildPromptDelegate(): GeckoSession.PromptDelegate = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.Alert(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { host } ?: host,
                    message = prompt.message.orEmpty(),
                    onDismiss = {
                        if (completed.compareAndSet(false, true)) result.complete(prompt.dismiss())
                    }
                )
            )
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.Confirm(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { host } ?: host,
                    message = prompt.message.orEmpty(),
                    onResult = { confirmed ->
                        if (completed.compareAndSet(false, true)) {
                            val response = if (confirmed) {
                                prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)
                            } else {
                                prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                            }
                            result.complete(response)
                        }
                    }
                )
            )
            return result
        }

        override fun onTextPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.TextPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.TextInput(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { host } ?: host,
                    message = prompt.message.orEmpty(),
                    defaultValue = prompt.defaultValue.orEmpty(),
                    onResult = { value ->
                        if (completed.compareAndSet(false, true)) {
                            result.complete(if (value != null) prompt.confirm(value) else prompt.dismiss())
                        }
                    }
                )
            )
            return result
        }

        override fun onAuthPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AuthPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(prompt.authOptions.uri ?: _state.value.url)
            val onlyPassword =
                (prompt.authOptions.flags and GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            callbacks.onPromptRequested(
                WebPromptRequest.HttpAuth(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { "Sign In Required" } ?: "Sign In Required",
                    message = prompt.message.orEmpty(),
                    usernameOnly = onlyPassword,
                    onResult = { user, pass ->
                        if (completed.compareAndSet(false, true)) {
                            val response = when {
                                pass == null -> prompt.dismiss()
                                onlyPassword -> prompt.confirm(pass)
                                else -> prompt.confirm(user.orEmpty(), pass)
                            }
                            result.complete(response)
                        }
                    }
                )
            )
            return result
        }

        override fun onChoicePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ChoicePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            val flattened = mutableListOf<PromptChoiceOption>()
            flattenChoices(prompt.choices, indented = false, out = flattened)
            callbacks.onPromptRequested(
                WebPromptRequest.ChoiceSelect(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { "Select Option" } ?: "Select Option",
                    message = prompt.message.orEmpty(),
                    multiple = prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE,
                    options = flattened,
                    onResult = { selectedIds ->
                        if (completed.compareAndSet(false, true)) {
                            val response = if (selectedIds == null) {
                                prompt.dismiss()
                            } else {
                                prompt.confirm(selectedIds.toTypedArray())
                            }
                            result.complete(response)
                        }
                    }
                )
            )
            return result
        }

        override fun onColorPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ColorPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.ColorPick(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { "Pick Color" } ?: "Pick Color",
                    defaultValue = prompt.defaultValue ?: "#FF2E54",
                    predefinedColors = prompt.predefinedValues?.toList().orEmpty(),
                    onResult = { colorHex ->
                        if (completed.compareAndSet(false, true)) {
                            result.complete(if (colorHex != null) prompt.confirm(colorHex) else prompt.dismiss())
                        }
                    }
                )
            )
            return result
        }

        override fun onDateTimePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.DateTimePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            val modeHint = when (prompt.type) {
                GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME -> "time (HH:mm)"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH -> "month (YYYY-MM)"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK -> "week (YYYY-Www)"
                GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> "date & time (YYYY-MM-DDTHH:mm)"
                else -> "date (YYYY-MM-DD)"
            }
            callbacks.onPromptRequested(
                WebPromptRequest.DateTimePick(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { "Choose $modeHint" } ?: "Choose $modeHint",
                    modeHint = modeHint,
                    defaultValue = prompt.defaultValue.orEmpty(),
                    onResult = { value ->
                        if (completed.compareAndSet(false, true)) {
                            result.complete(if (!value.isNullOrBlank()) prompt.confirm(value) else prompt.dismiss())
                        }
                    }
                )
            )
            return result
        }

        override fun onFilePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.FilePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.FileUpload(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = prompt.title?.ifBlank { "Upload File" } ?: "Upload File",
                    mimeTypes = prompt.mimeTypes?.toList().orEmpty().ifEmpty { listOf("*/*") },
                    allowMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE,
                    onResult = { uris ->
                        if (completed.compareAndSet(false, true)) {
                            val response = if (uris.isNullOrEmpty()) {
                                prompt.dismiss()
                            } else {
                                prompt.confirm(appContext, uris.toTypedArray())
                            }
                            result.complete(response)
                        }
                    }
                )
            )
            return result
        }

        override fun onRepostConfirmPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.Confirm(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = "Resubmit Form?",
                    message = "Reloading this page will resend information you previously entered.",
                    confirmLabel = "Resend",
                    onResult = { ok ->
                        if (completed.compareAndSet(false, true)) {
                            result.complete(prompt.confirm(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                        }
                    }
                )
            )
            return result
        }

        override fun onBeforeUnloadPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val completed = AtomicBoolean(false)
            val host = UrlResolver.extractHost(_state.value.url)
            callbacks.onPromptRequested(
                WebPromptRequest.Confirm(
                    id = UUID.randomUUID().toString(),
                    originHost = host,
                    tabId = id,
                    title = "Leave Page?",
                    message = "Changes you made on $host may not be saved.",
                    confirmLabel = "Leave",
                    cancelLabel = "Stay",
                    onResult = { leave ->
                        if (completed.compareAndSet(false, true)) {
                            result.complete(prompt.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY))
                        }
                    }
                )
            )
            return result
        }

        override fun onSharePrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.SharePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val payload = prompt.uri?.takeIf { it.isNotBlank() } ?: prompt.text.orEmpty()
            val ok = runCatching {
                callbacks.onShareRequested(prompt.title.orEmpty(), payload)
            }.isSuccess
            return GeckoResult.fromValue(
                prompt.confirm(
                    if (ok) GeckoSession.PromptDelegate.SharePrompt.Result.SUCCESS
                    else GeckoSession.PromptDelegate.SharePrompt.Result.FAILURE
                )
            )
        }
    }

    private fun buildPermissionDelegate(): GeckoSession.PermissionDelegate =
        object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val list = permissions?.toList().orEmpty()
                if (list.isEmpty() || list.all {
                        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    callback.grant()
                    return
                }
                val completed = AtomicBoolean(false)
                val host = UrlResolver.extractHost(_state.value.url)
                callbacks.onPromptRequested(
                    WebPromptRequest.AndroidPermissions(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = id,
                        permissions = list,
                        onResult = { granted ->
                            if (completed.compareAndSet(false, true)) {
                                if (granted) callback.grant() else callback.reject()
                            }
                        }
                    )
                )
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int> {
                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) {
                    return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
                val result = GeckoResult<Int>()
                val completed = AtomicBoolean(false)
                val host = UrlResolver.extractHost(perm.uri)
                val label = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "access your location"
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "show notifications"
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store persistent offline data"
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "autoplay media with sound"
                    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected DRM media"
                    GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "access cross-site cookies"
                    else -> "access site capability"
                }
                callbacks.onPromptRequested(
                    WebPromptRequest.SitePermission(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = id,
                        permissionLabel = label,
                        onResult = { granted ->
                            if (completed.compareAndSet(false, true)) {
                                result.complete(
                                    if (granted) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                    else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                                )
                            }
                        }
                    )
                )
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val hasVideo = !video.isNullOrEmpty()
                val hasAudio = !audio.isNullOrEmpty()
                if (!hasVideo && !hasAudio) {
                    callback.reject()
                    return
                }
                val mediaLabel = when {
                    hasVideo && hasAudio -> "use your camera and microphone"
                    hasVideo -> "use your camera"
                    else -> "use your microphone"
                }
                val completed = AtomicBoolean(false)
                val host = UrlResolver.extractHost(uri)
                callbacks.onPromptRequested(
                    WebPromptRequest.SitePermission(
                        id = UUID.randomUUID().toString(),
                        originHost = host,
                        tabId = id,
                        permissionLabel = mediaLabel,
                        onResult = { granted ->
                            if (completed.compareAndSet(false, true)) {
                                if (granted) {
                                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                                } else {
                                    callback.reject()
                                }
                            }
                        }
                    )
                )
            }
        }

    private fun flattenChoices(
        choices: Array<out GeckoSession.PromptDelegate.ChoicePrompt.Choice>?,
        indented: Boolean,
        out: MutableList<PromptChoiceOption>
    ) {
        if (choices == null) return
        choices.forEach { ch ->
            val subItems = ch.items
            if (!subItems.isNullOrEmpty()) {
                out.add(
                    PromptChoiceOption(
                        id = ch.id,
                        label = ch.label,
                        selected = false,
                        disabled = true,
                        isGroupHeader = true,
                        isSeparator = false,
                        indented = indented
                    )
                )
                flattenChoices(subItems, indented = true, out = out)
            } else {
                out.add(
                    PromptChoiceOption(
                        id = ch.id,
                        label = ch.label,
                        selected = ch.selected,
                        disabled = ch.disabled,
                        isGroupHeader = false,
                        isSeparator = ch.separator,
                        indented = indented
                    )
                )
            }
        }
    }

    private fun parseDispositionFileName(disposition: String): String? {
        val match = Regex("filename\\*?=(?:UTF-8''|\"?)([^\";\\r\\n]+)", RegexOption.IGNORE_CASE)
            .find(disposition)
        return match?.groupValues?.getOrNull(1)?.trim('"')?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun attachBridgeMessageDelegate(ext: WebExtension) {
        if (isReleased) return
        geckoSession.webExtensionController.setMessageDelegate(
            ext,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    nativePort = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val obj = message as? JSONObject ?: return
                            when (obj.optString("type")) {
                                "SNAPSHOT_RESULT" -> {
                                    val reqId = obj.optString("requestId")
                                    val deferred = pendingSnapshotRequests.remove(reqId) ?: return
                                    val data = obj.optJSONObject("payload") ?: obj
                                    deferred.complete(
                                        PageContextSnapshot(
                                            url = data.optString("url", _state.value.url),
                                            title = data.optString("title", _state.value.title),
                                            textContent = data.optString("textContent", data.optString("text", "")),
                                            selectedText = data.optString("selectedText", data.optString("selection"))
                                                .takeIf { it.isNotBlank() }
                                        )
                                    )
                                }
                                "MEDIA_SCAN_RESULT" -> {
                                    val reqId = obj.optString("requestId")
                                    val deferred = pendingMediaRequests.remove(reqId) ?: return
                                    val arr = obj.optJSONArray("items")
                                    val list = buildList {
                                        if (arr != null) {
                                            for (i in 0 until arr.length()) {
                                                val item = arr.optJSONObject(i) ?: continue
                                                val mediaUrl = item.optString("url")
                                                if (mediaUrl.isBlank()) continue
                                                val rawMime = item.optString("mimeType").takeIf { it.isNotBlank() }
                                                val inferredKind = DownloadKind.infer(mediaUrl, rawMime, mediaUrl)
                                                val explicitKind = item.optString("kind").takeIf { it.isNotBlank() }
                                                    ?.let { DownloadKind.fromId(it) }
                                                    ?: inferredKind
                                                add(
                                                    DetectedPageMedia(
                                                        url = mediaUrl,
                                                        title = item.optString("title", mediaUrl),
                                                        kind = explicitKind,
                                                        mimeType = rawMime
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    deferred.complete(list)
                                }
                            }
                        }

                        override fun onDisconnect(port: WebExtension.Port) {
                            if (nativePort === port) {
                                nativePort = null
                            }
                        }
                    })
                    syncBridgeModes()
                }
            },
            SAB_NATIVE_APP
        )
    }

    private fun syncBridgeModes() {
        val port = nativePort ?: return
        val current = _state.value
        runOnMain {
            runCatching {
                port.postMessage(
                    JSONObject().apply {
                        put("type", "SYNC_MODES")
                        put("forceDark", current.forceDarkMode)
                        put("readerMode", current.readerMode)
                        put("textZoom", current.textZoomPercent)
                        put("doNotTrack", doNotTrackSignal)
                        put("globalPrivacyControl", gpcSignal)
                    }
                )
            }.onFailure { err ->
                Log.w(SESSION_TAG, "syncBridgeModes failed: ${err.message}")
            }
        }
    }

    override fun loadUrl(url: String) {
        if (url == SAB_START_URL || url.isBlank()) {
            showStartPage()
            return
        }
        ensureSessionOpened()
        _state.update {
            it.copy(
                url = url,
                title = UrlResolver.extractHost(url),
                isLoading = true,
                progress = 8,
                securityState = UrlResolver.securityForUrl(url)
            )
        }
        geckoSession.loadUri(url)
    }

    override fun showStartPage() {
        geckoSession.stop()
        _state.update {
            it.copy(
                url = SAB_START_URL,
                title = if (isPrivate) "Private Tab" else "Speed Dial",
                isLoading = false,
                progress = 0,
                securityState = SecurityState.INTERNAL
            )
        }
    }

    override fun goBack() {
        if (_state.value.canGoBack) {
            geckoSession.goBack()
        } else if (!_state.value.isStartPage) {
            showStartPage()
        }
    }

    override fun goForward() {
        if (_state.value.canGoForward) {
            geckoSession.goForward()
        }
    }

    override fun reload() {
        if (!_state.value.isStartPage) {
            geckoSession.reload()
        }
    }

    override fun stopLoading() {
        geckoSession.stop()
        _state.update { it.copy(isLoading = false) }
    }

    override fun setDesktopMode(enabled: Boolean) {
        _state.update { it.copy(desktopMode = enabled) }
        geckoSession.settings.userAgentMode = if (enabled) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        geckoSession.settings.viewportMode = if (enabled) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        if (!_state.value.isStartPage) {
            geckoSession.reload()
        }
    }

    override fun setForceDarkMode(enabled: Boolean) {
        _state.update { it.copy(forceDarkMode = enabled) }
        syncBridgeModes()
    }

    override fun setReaderMode(enabled: Boolean) {
        _state.update { it.copy(readerMode = enabled) }
        syncBridgeModes()
    }

    override fun setTextZoom(percent: Int) {
        val clamped = percent.coerceIn(75, 160)
        _state.update { it.copy(textZoomPercent = clamped) }
        syncBridgeModes()
    }

    override fun findInPage(query: String) {
        if (query.isBlank()) {
            clearFindInPage()
        } else {
            geckoSession.finder.find(query, 0).accept { res ->
                callbacks.onFindResult(
                    FindInPageResult(
                        currentMatch = res?.current ?: 0,
                        totalMatches = res?.total ?: 0
                    )
                )
            }
        }
    }

    override fun findNext(query: String) {
        if (query.isBlank()) return
        geckoSession.finder.find(query, 0).accept { res ->
            callbacks.onFindResult(
                FindInPageResult(
                    currentMatch = res?.current ?: 0,
                    totalMatches = res?.total ?: 0
                )
            )
        }
    }

    override fun findPrevious(query: String) {
        if (query.isBlank()) return
        geckoSession.finder.find(query, GeckoSession.FINDER_FIND_BACKWARDS).accept { res ->
            callbacks.onFindResult(
                FindInPageResult(
                    currentMatch = res?.current ?: 0,
                    totalMatches = res?.total ?: 0
                )
            )
        }
    }

    override fun clearFindInPage() {
        geckoSession.finder.clear()
        callbacks.onFindResult(FindInPageResult(0, 0))
    }

    override fun exitFullScreen() {
        geckoSession.exitFullScreen()
        callbacks.onFullScreenChanged(false)
    }

    override fun attachToView(view: View) {
        val geckoView = view as? GeckoView ?: return
        if (geckoView.session !== geckoSession) {
            geckoView.releaseSession()
            geckoView.setSession(geckoSession)
        }
    }

    override fun detachFromView(view: View) {
        val geckoView = view as? GeckoView ?: return
        if (geckoView.session === geckoSession) {
            geckoView.releaseSession()
        }
    }

    override suspend fun extractPageSnapshot(maxChars: Int): PageContextSnapshot {
        val current = _state.value
        val port = nativePort
        if (port != null) {
            val reqId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<PageContextSnapshot>()
            pendingSnapshotRequests[reqId] = deferred
            runOnMain {
                runCatching {
                    port.postMessage(
                        JSONObject().apply {
                            put("type", "EXTRACT_SNAPSHOT")
                            put("requestId", reqId)
                            put("maxChars", maxChars)
                        }
                    )
                }.onFailure {
                    pendingSnapshotRequests.remove(reqId)
                    deferred.completeExceptionally(it)
                }
            }
            val fromBridge = runCatching {
                withTimeoutOrNull(1_800L) { deferred.await() }
            }.getOrNull()
            pendingSnapshotRequests.remove(reqId)
            if (fromBridge != null && fromBridge.textContent.isNotBlank()) {
                return fromBridge
            }
        }

        // Fallback to GeckoView native SessionPageExtractor
        val extracted = withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { cont ->
                runOnMain {
                    runCatching {
                        PageExtractionController.SessionPageExtractor(geckoSession).pageContent.accept(
                            { content ->
                                if (cont.isActive) cont.resume(content.orEmpty())
                            },
                            {
                                if (cont.isActive) cont.resume("")
                            }
                        )
                    }.onFailure {
                        if (cont.isActive) cont.resume("")
                    }
                }
            }
        }.orEmpty()

        return PageContextSnapshot(
            url = current.url,
            title = current.title,
            textContent = extracted.take(maxChars)
        )
    }

    override suspend fun detectPageMedia(): List<DetectedPageMedia> {
        val port = nativePort ?: return emptyList()
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<List<DetectedPageMedia>>()
        pendingMediaRequests[reqId] = deferred
        runOnMain {
            runCatching {
                port.postMessage(
                    JSONObject().apply {
                        put("type", "SCAN_PAGE_MEDIA")
                        put("requestId", reqId)
                    }
                )
            }.onFailure {
                pendingMediaRequests.remove(reqId)
                deferred.complete(emptyList())
            }
        }
        val items = runCatching {
            withTimeoutOrNull(2_200L) { deferred.await() }
        }.getOrNull().orEmpty()
        pendingMediaRequests.remove(reqId)
        return items
    }

    override fun release() {
        isReleased = true
        nativePort = null
        pendingSnapshotRequests.values.forEach { it.cancel() }
        pendingSnapshotRequests.clear()
        pendingMediaRequests.values.forEach { it.cancel() }
        pendingMediaRequests.clear()
        sessionScope.cancel()
        if (isOpened) {
            runCatching { geckoSession.close() }
            isOpened = false
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
