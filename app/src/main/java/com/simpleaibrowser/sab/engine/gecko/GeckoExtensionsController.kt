package com.simpleaibrowser.sab.engine.gecko

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.simpleaibrowser.sab.core.browser.BrowserExtensionsController
import com.simpleaibrowser.sab.core.browser.ExtensionActionState
import com.simpleaibrowser.sab.core.browser.ExtensionPopupSession
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.InstalledExtensionInfo
import com.simpleaibrowser.sab.core.browser.WebPromptRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.Image
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val EXT_TAG = "SabGeckoExtensions"
internal const val SAB_BRIDGE_EXTENSION_ID = "sab-bridge@simpleaibrowser.com"
internal const val SAB_BRIDGE_EXTENSION_URI = "resource://android/assets/extensions/sab-bridge/"

class GeckoExtensionsController(
    private val runtime: GeckoRuntime,
    private val onPromptRequested: (WebPromptRequest) -> Unit,
    private val onOpenNewTab: (url: String, isPrivate: Boolean) -> Unit,
    private val onBuiltInReady: (WebExtension) -> Unit
) : BrowserExtensionsController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val rawExtensions = ConcurrentHashMap<String, WebExtension>()
    private val defaultActions = ConcurrentHashMap<String, WebExtension.Action>()
    private val extensionIcons = ConcurrentHashMap<String, Bitmap>()
    private val actionIcons = ConcurrentHashMap<String, Bitmap>()
    private val readyListeners = CopyOnWriteArrayList<(WebExtension) -> Unit>()

    @Volatile
    var builtInExtension: WebExtension? = null
        private set

    private val _installedExtensions = MutableStateFlow<List<InstalledExtensionInfo>>(emptyList())
    override val installedExtensions: StateFlow<List<InstalledExtensionInfo>> = _installedExtensions.asStateFlow()

    private val _activePopup = MutableStateFlow<ExtensionPopupSession?>(null)
    override val activePopup: StateFlow<ExtensionPopupSession?> = _activePopup.asStateFlow()

    init {
        installControllerDelegates()
        ensureBuiltInBridge()
    }

    fun registerBuiltInListener(listener: (WebExtension) -> Unit) {
        readyListeners.add(listener)
        builtInExtension?.let(listener)
    }

    private fun ensureBuiltInBridge() {
        runOnMain {
            runtime.webExtensionController
                .ensureBuiltIn(SAB_BRIDGE_EXTENSION_URI, SAB_BRIDGE_EXTENSION_ID)
                .accept(
                    { ext ->
                        if (ext != null) {
                            configurePrivateBrowsingForBuiltIn(ext)
                        }
                    },
                    { err ->
                        Log.w(EXT_TAG, "sab-bridge ensureBuiltIn failed: ${err?.message}")
                    }
                )
        }
    }

    private fun configurePrivateBrowsingForBuiltIn(ext: WebExtension) {
        if (!ext.metaData.allowedInPrivateBrowsing) {
            runtime.webExtensionController
                .setAllowedInPrivateBrowsing(ext, true)
                .accept(
                    { updated ->
                        val resolved = updated ?: ext
                        publishBuiltInReady(resolved)
                    },
                    {
                        publishBuiltInReady(ext)
                    }
                )
        } else {
            publishBuiltInReady(ext)
        }
    }

    private fun publishBuiltInReady(ext: WebExtension) {
        builtInExtension = ext
        rawExtensions[ext.id] = ext
        onBuiltInReady(ext)
        readyListeners.forEach { listener -> runCatching { listener(ext) } }
        scope.launch { refreshInstalled() }
    }

    private fun installControllerDelegates() {
        val controller = runtime.webExtensionController

        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (extension.isBuiltIn || extension.id == SAB_BRIDGE_EXTENSION_ID) {
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(true, true, true)
                    )
                }
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                val completed = AtomicBoolean(false)
                val combinedPerms = (permissions.toList() + dataCollectionPermissions.toList()).distinct()
                onPromptRequested(
                    WebPromptRequest.ExtensionInstall(
                        id = UUID.randomUUID().toString(),
                        originHost = extension.metaData.homepageUrl
                            ?: extension.metaData.amoListingUrl
                            ?: "Extension Package",
                        extensionName = extension.metaData.name.orEmpty().ifBlank { extension.id },
                        version = extension.metaData.version.orEmpty(),
                        permissions = combinedPerms,
                        origins = origins.toList(),
                        onResult = { granted, allowPrivate ->
                            if (completed.compareAndSet(false, true)) {
                                result.complete(
                                    WebExtension.PermissionPromptResponse(
                                        granted,
                                        granted && allowPrivate,
                                        false
                                    )
                                )
                            }
                        }
                    )
                )
                return result
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                val existingPerms = extension.metaData.requiredPermissions.toSet()
                val addedPerms = newPermissions.filterNot { it in existingPerms }
                // Auto-updates with identical or reduced permissions apply silently
                if (addedPerms.isEmpty() && newOrigins.isEmpty() && newDataCollectionPermissions.isEmpty()) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
                val result = GeckoResult<AllowOrDeny>()
                val completed = AtomicBoolean(false)
                val summary = buildString {
                    append("${extension.metaData.name} wants to update to a newer version and needs:\n")
                    (addedPerms + newOrigins + newDataCollectionPermissions).take(8).forEach {
                        append("• ").append(it).append('\n')
                    }
                }.trim()
                onPromptRequested(
                    WebPromptRequest.Confirm(
                        id = UUID.randomUUID().toString(),
                        originHost = "Extension Update",
                        title = "Approve Update Permissions",
                        message = summary,
                        confirmLabel = "Allow & Update",
                        onResult = { ok ->
                            if (completed.compareAndSet(false, true)) {
                                result.complete(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                            }
                        }
                    )
                )
                return result
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>
            ): GeckoResult<AllowOrDeny> {
                val result = GeckoResult<AllowOrDeny>()
                val completed = AtomicBoolean(false)
                val details = (permissions.toList() + origins.toList() + dataCollectionPermissions.toList())
                    .take(8)
                    .joinToString("\n• ", prefix = "• ")
                val extName = extension.metaData.name.orEmpty().ifBlank { extension.id }
                onPromptRequested(
                    WebPromptRequest.Confirm(
                        id = UUID.randomUUID().toString(),
                        originHost = extName,
                        title = "Additional Extension Permission",
                        message = "$extName requests optional access:\n$details",
                        confirmLabel = "Allow",
                        onResult = { ok ->
                            if (completed.compareAndSet(false, true)) {
                                result.complete(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                            }
                        }
                    )
                )
                return result
            }
        })

        controller.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) {
                registerExtensionDelegates(extension)
                scope.launch { refreshInstalled() }
            }

            override fun onEnabled(extension: WebExtension) {
                registerExtensionDelegates(extension)
                scope.launch { refreshInstalled() }
            }

            override fun onDisabled(extension: WebExtension) {
                rawExtensions[extension.id] = extension
                scope.launch { refreshInstalled() }
            }

            override fun onUninstalled(extension: WebExtension) {
                rawExtensions.remove(extension.id)
                defaultActions.remove(extension.id)
                extensionIcons.remove(extension.id)
                actionIcons.remove(extension.id)
                if (_activePopup.value?.extensionId == extension.id) {
                    dismissPopup()
                }
                scope.launch { refreshInstalled() }
            }

            override fun onReady(extension: WebExtension) {
                registerExtensionDelegates(extension)
                if (extension.id == SAB_BRIDGE_EXTENSION_ID) {
                    builtInExtension = extension
                    readyListeners.forEach { listener -> runCatching { listener(extension) } }
                }
                scope.launch { refreshInstalled() }
            }

            override fun onInstallationFailed(
                extension: WebExtension?,
                exception: WebExtension.InstallException
            ) {
                Log.w(EXT_TAG, "Extension install failed (${exception.code}): ${exception.message}")
            }
        })
    }

    private fun registerExtensionDelegates(ext: WebExtension) {
        rawExtensions[ext.id] = ext
        if (ext.isBuiltIn || ext.id == SAB_BRIDGE_EXTENSION_ID) return

        val icon = ext.metaData.icon
        if (icon != null && !extensionIcons.containsKey(ext.id)) {
            icon.getBitmap(96).accept { bmp ->
                if (bmp != null) {
                    extensionIcons[ext.id] = bmp
                    rebuildInstalledList()
                }
            }
        }

        ext.tabDelegate = object : WebExtension.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession> {
                val targetUrl = createDetails.url ?: "about:blank"
                onOpenNewTab(targetUrl, false)
                // We open inside SAB's managed tab model so Compose Tab Gallery stays in sync
                return GeckoResult.fromValue(null)
            }

            override fun onOpenOptionsPage(source: WebExtension) {
                val optionsUrl = source.metaData.optionsPageUrl
                if (!optionsUrl.isNullOrBlank()) {
                    onOpenNewTab(optionsUrl, false)
                }
            }
        }

        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                if (session == null) {
                    defaultActions[extension.id] = action
                } else {
                    val base = defaultActions[extension.id]
                    defaultActions[extension.id] = if (base != null) action.withDefault(base) else action
                }
                action.icon?.let { img ->
                    img.getBitmap(64).accept { bmp ->
                        if (bmp != null) {
                            actionIcons[extension.id] = bmp
                            rebuildInstalledList()
                        }
                    }
                }
                rebuildInstalledList()
            }

            override fun onPageAction(
                extension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                if (session == null) {
                    defaultActions.putIfAbsent(extension.id, action)
                }
                rebuildInstalledList()
            }

            override fun onTogglePopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession> {
                if (_activePopup.value?.extensionId == extension.id) {
                    dismissPopup()
                    return GeckoResult.fromValue(null)
                }
                return onOpenPopup(extension, action)
            }

            override fun onOpenPopup(
                extension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession> {
                val popupGeckoSession = GeckoSession()
                popupGeckoSession.open(runtime)
                _activePopup.value?.release()
                _activePopup.value = GeckoExtensionPopupSession(
                    extensionId = extension.id,
                    title = action.title ?: extension.metaData.name.orEmpty().ifBlank { extension.id },
                    geckoSession = popupGeckoSession
                )
                return GeckoResult.fromValue(popupGeckoSession)
            }
        })
    }

    override suspend fun refreshInstalled(): List<InstalledExtensionInfo> {
        val listed = awaitNullableOnMain { runtime.webExtensionController.list() }.getOrNull().orEmpty()
        listed.forEach { ext ->
            registerExtensionDelegates(ext)
            val icon = ext.metaData.icon
            if (icon != null && !extensionIcons.containsKey(ext.id)) {
                icon.getBitmap(96).accept { bmp ->
                    if (bmp != null) {
                        extensionIcons[ext.id] = bmp
                        rebuildInstalledList()
                    }
                }
            }
        }
        val mapped = rebuildInstalledList()
        return mapped
    }

    private fun rebuildInstalledList(): List<InstalledExtensionInfo> {
        val list = rawExtensions.values
            .sortedWith(compareBy<WebExtension> { it.isBuiltIn }.thenBy { it.metaData.name.orEmpty().lowercase() })
            .map { toInstalledInfo(it) }
        _installedExtensions.value = list
        return list
    }

    private fun toInstalledInfo(ext: WebExtension): InstalledExtensionInfo {
        val meta = ext.metaData
        val extName = meta.name.orEmpty().ifBlank { ext.id }
        val action = defaultActions[ext.id]
        val disabledReason = when {
            (meta.disabledFlags and WebExtension.DisabledFlags.SIGNATURE) != 0 ->
                "Disabled: missing Mozilla signature"
            (meta.disabledFlags and WebExtension.DisabledFlags.BLOCKLIST) != 0 ->
                "Blocked by Mozilla safety list"
            (meta.disabledFlags and WebExtension.DisabledFlags.APP_VERSION) != 0 ->
                "Incompatible with GeckoView version"
            else -> null
        }

        return InstalledExtensionInfo(
            id = ext.id,
            name = extName,
            description = meta.description.orEmpty(),
            fullDescription = meta.fullDescription.orEmpty(),
            version = meta.version.orEmpty().ifBlank { "1.0" },
            author = meta.creatorName.orEmpty().ifBlank { "Extension Author" },
            iconBitmap = extensionIcons[ext.id],
            iconUrl = null,
            enabled = meta.enabled,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            optionsPageUrl = meta.optionsPageUrl?.takeIf { it.isNotBlank() },
            openOptionsInTab = meta.openOptionsPageInTab,
            homepageUrl = meta.homepageUrl?.takeIf { it.isNotBlank() } ?: meta.amoListingUrl?.takeIf { it.isNotBlank() },
            isBuiltIn = ext.isBuiltIn || ext.id == SAB_BRIDGE_EXTENSION_ID,
            disabledReason = disabledReason,
            permissions = (meta.requiredPermissions.toList() + meta.requiredOrigins.toList()).distinct(),
            source = when {
                ext.isBuiltIn -> ExtensionSource.BUILTIN
                !meta.amoListingUrl.isNullOrBlank() -> ExtensionSource.AMO
                else -> ExtensionSource.GITHUB
            },
            sourceRef = ext.id,
            actionState = action?.let { act ->
                ExtensionActionState(
                    extensionId = ext.id,
                    extensionName = extName,
                    title = act.title ?: extName,
                    badgeText = act.badgeText?.takeIf { it.isNotBlank() },
                    badgeBackgroundColor = act.badgeBackgroundColor,
                    badgeTextColor = act.badgeTextColor,
                    enabled = act.enabled ?: true,
                    iconBitmap = actionIcons[ext.id] ?: extensionIcons[ext.id]
                )
            }
        )
    }

    override suspend fun installFromUrl(
        url: String,
        installMethod: String?
    ): Result<InstalledExtensionInfo> {
        val result = awaitOnMain {
            if (installMethod != null) {
                runtime.webExtensionController.install(url, installMethod)
            } else {
                runtime.webExtensionController.install(url)
            }
        }
        return result.fold(
            onSuccess = { ext ->
                registerExtensionDelegates(ext)
                refreshInstalled()
                Result.success(toInstalledInfo(ext))
            },
            onFailure = { err ->
                Result.failure(Exception(formatInstallError(err), err))
            }
        )
    }

    override suspend fun enableExtension(extensionId: String): Result<InstalledExtensionInfo> {
        val ext = rawExtensions[extensionId]
            ?: return Result.failure(IllegalStateException("Extension not found"))
        val res = awaitOnMain {
            runtime.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
        }
        return res.map { updated ->
            registerExtensionDelegates(updated)
            refreshInstalled()
            toInstalledInfo(updated)
        }
    }

    override suspend fun disableExtension(extensionId: String): Result<InstalledExtensionInfo> {
        val ext = rawExtensions[extensionId]
            ?: return Result.failure(IllegalStateException("Extension not found"))
        val res = awaitOnMain {
            runtime.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
        }
        return res.map { updated ->
            rawExtensions[updated.id] = updated
            refreshInstalled()
            toInstalledInfo(updated)
        }
    }

    override suspend fun setAllowedInPrivateBrowsing(
        extensionId: String,
        allowed: Boolean
    ): Result<InstalledExtensionInfo> {
        val ext = rawExtensions[extensionId]
            ?: return Result.failure(IllegalStateException("Extension not found"))
        val res = awaitOnMain {
            runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, allowed)
        }
        return res.map { updated ->
            registerExtensionDelegates(updated)
            refreshInstalled()
            toInstalledInfo(updated)
        }
    }

    override suspend fun uninstallExtension(extensionId: String): Result<Unit> {
        val ext = rawExtensions[extensionId]
            ?: return Result.failure(IllegalStateException("Extension not found"))
        if (ext.isBuiltIn || ext.id == SAB_BRIDGE_EXTENSION_ID) {
            return Result.failure(IllegalStateException("Built-in SAB Page Bridge cannot be uninstalled"))
        }
        val res = awaitOnMain { runtime.webExtensionController.uninstall(ext) }
        return res.map {
            rawExtensions.remove(extensionId)
            defaultActions.remove(extensionId)
            refreshInstalled()
        }
    }

    override suspend fun updateViaNativeManager(extensionId: String): Result<InstalledExtensionInfo?> {
        val ext = rawExtensions[extensionId]
            ?: return Result.failure(IllegalStateException("Extension not found"))
        val res = awaitNullableOnMain { runtime.webExtensionController.update(ext) }
        return res.map { updated ->
            if (updated != null) {
                registerExtensionDelegates(updated)
                refreshInstalled()
                toInstalledInfo(updated)
            } else {
                null
            }
        }
    }

    override fun triggerBrowserAction(extensionId: String) {
        runOnMain {
            val action = defaultActions[extensionId]
            if (action != null) {
                action.click()
            } else {
                openOptionsPage(extensionId)
            }
        }
    }

    override fun openOptionsPage(extensionId: String) {
        val ext = rawExtensions[extensionId] ?: return
        val optionsUrl = ext.metaData.optionsPageUrl
        if (!optionsUrl.isNullOrBlank()) {
            onOpenNewTab(optionsUrl, false)
        }
    }

    override fun dismissPopup() {
        _activePopup.update { current ->
            current?.release()
            null
        }
    }

    private fun formatInstallError(err: Throwable): String {
        val installEx = err as? WebExtension.InstallException
        if (installEx != null) {
            return when (installEx.code) {
                WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                    "This package isn't Mozilla-signed. Standard GeckoView 161 requires Mozilla-signed .xpi extensions."
                WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                    "Network error while downloading the extension package."
                WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                    "Extension file hash verification failed."
                WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE ->
                    "The downloaded .xpi package is corrupted or not a valid WebExtension."
                WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                    "This extension is marked incompatible with Android GeckoView."
                WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED ->
                    "Blocked by Mozilla Add-on security blocklist."
                WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED ->
                    "Extension installation was cancelled."
                else -> installEx.message ?: "Installation failed (code ${installEx.code})."
            }
        }
        return err.message ?: "Extension installation failed."
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private suspend fun <T : Any> awaitOnMain(call: () -> GeckoResult<T>): Result<T> =
        awaitNullableOnMain(call).mapCatching {
            it ?: throw IllegalStateException("Gecko operation returned null")
        }

    private suspend fun <T> awaitNullableOnMain(call: () -> GeckoResult<T>): Result<T?> =
        suspendCancellableCoroutine { cont ->
            runOnMain {
                try {
                    call().accept(
                        { value ->
                            if (cont.isActive) cont.resume(Result.success(value))
                        },
                        { err ->
                            if (cont.isActive) {
                                cont.resume(Result.failure(err ?: IllegalStateException("Gecko operation failed")))
                            }
                        }
                    )
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }
        }

    private class GeckoExtensionPopupSession(
        override val extensionId: String,
        override val title: String,
        private val geckoSession: GeckoSession
    ) : ExtensionPopupSession {
        @Volatile
        private var released = false

        override fun createPopupView(context: Context): View {
            return GeckoView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setSession(geckoSession)
            }
        }

        override fun release() {
            if (!released) {
                released = true
                runCatching { geckoSession.close() }
            }
        }
    }
}
