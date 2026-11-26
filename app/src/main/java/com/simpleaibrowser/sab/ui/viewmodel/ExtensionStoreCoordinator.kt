package com.simpleaibrowser.sab.ui.viewmodel

import com.simpleaibrowser.sab.core.browser.AmoCategory
import com.simpleaibrowser.sab.core.browser.BrowserExtensionsController
import com.simpleaibrowser.sab.core.browser.ExtensionActionState
import com.simpleaibrowser.sab.core.browser.ExtensionCatalogItem
import com.simpleaibrowser.sab.core.browser.ExtensionInstallState
import com.simpleaibrowser.sab.core.browser.ExtensionPopupSession
import com.simpleaibrowser.sab.core.browser.ExtensionSource
import com.simpleaibrowser.sab.core.browser.ExtensionVersionComparator
import com.simpleaibrowser.sab.core.browser.GitHubReleaseXpiAsset
import com.simpleaibrowser.sab.core.browser.InstalledExtensionInfo
import com.simpleaibrowser.sab.data.BrowserRepository
import com.simpleaibrowser.sab.data.extensions.ExtensionStoreClient
import com.simpleaibrowser.sab.data.extensions.GitHubRepoDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExtensionsTab(val label: String) {
    AMO("Mozilla Store"),
    GITHUB("GitHub"),
    INSTALLED("Installed")
}

class ExtensionStoreCoordinator(
    private val scope: CoroutineScope,
    private val repository: BrowserRepository,
    private val controller: BrowserExtensionsController?,
    private val onToast: (String) -> Unit,
    private val storeClient: ExtensionStoreClient = ExtensionStoreClient()
) {
    private val _amoCategory = MutableStateFlow(AmoCategory.FEATURED)
    val amoCategory: StateFlow<AmoCategory> = _amoCategory.asStateFlow()

    private val _amoQuery = MutableStateFlow("")
    val amoQuery: StateFlow<String> = _amoQuery.asStateFlow()

    private val _amoResults = MutableStateFlow<List<ExtensionCatalogItem>>(emptyList())
    val amoResults: StateFlow<List<ExtensionCatalogItem>> = _amoResults.asStateFlow()

    private val _amoLoading = MutableStateFlow(false)
    val amoLoading: StateFlow<Boolean> = _amoLoading.asStateFlow()

    private val _amoError = MutableStateFlow<String?>(null)
    val amoError: StateFlow<String?> = _amoError.asStateFlow()

    private val _githubQuery = MutableStateFlow("")
    val githubQuery: StateFlow<String> = _githubQuery.asStateFlow()

    private val _githubResults = MutableStateFlow<List<ExtensionCatalogItem>>(emptyList())
    val githubResults: StateFlow<List<ExtensionCatalogItem>> = _githubResults.asStateFlow()

    private val _githubLoading = MutableStateFlow(false)
    val githubLoading: StateFlow<Boolean> = _githubLoading.asStateFlow()

    private val _githubError = MutableStateFlow<String?>(null)
    val githubError: StateFlow<String?> = _githubError.asStateFlow()

    private val _selectedGitHubRepo = MutableStateFlow<GitHubRepoDetail?>(null)
    val selectedGitHubRepo: StateFlow<GitHubRepoDetail?> = _selectedGitHubRepo.asStateFlow()

    private val _gitHubRepoLoading = MutableStateFlow(false)
    val gitHubRepoLoading: StateFlow<Boolean> = _gitHubRepoLoading.asStateFlow()

    private val _installState = MutableStateFlow<ExtensionInstallState>(ExtensionInstallState.Idle)
    val installState: StateFlow<ExtensionInstallState> = _installState.asStateFlow()

    val activePopup: StateFlow<ExtensionPopupSession?> = controller?.activePopup
        ?: MutableStateFlow<ExtensionPopupSession?>(null).asStateFlow()

    val installedExtensions: StateFlow<List<InstalledExtensionInfo>> = combine(
        controller?.installedExtensions ?: flowOf(emptyList()),
        repository.extensionRecords
    ) { liveList, dbRecords ->
        val recMap = dbRecords.associateBy { it.extensionId }
        liveList.map { ext ->
            val rec = recMap[ext.id]
            if (rec == null) {
                ext
            } else {
                ext.copy(
                    source = if (ext.isBuiltIn) ExtensionSource.BUILTIN else ExtensionSource.fromId(rec.sourceType),
                    sourceRef = rec.sourceRef.ifBlank { ext.sourceRef },
                    iconUrl = rec.iconUrl ?: ext.iconUrl,
                    autoUpdateEnabled = rec.autoUpdateEnabled,
                    availableUpdateVersion = rec.latestVersion
                        ?.takeIf { ExtensionVersionComparator.isNewer(it, ext.version) },
                    availableUpdateUrl = rec.latestDownloadUrl,
                    lastCheckedAt = rec.lastCheckedAt
                )
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val extensionActions: StateFlow<List<ExtensionActionState>> = installedExtensions
        .map { list ->
            list.mapNotNull { ext ->
                if (!ext.isBuiltIn && ext.enabled) ext.actionState else null
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val availableUpdatesCount: StateFlow<Int> = installedExtensions
        .map { list -> list.count { !it.isBuiltIn && it.availableUpdateVersion != null } }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    fun loadInitialCatalogIfNeeded() {
        scope.launch {
            controller?.refreshInstalled()
        }
        if (_amoResults.value.isEmpty() && !_amoLoading.value) {
            searchAmo(_amoQuery.value, _amoCategory.value)
        }
        if (_githubResults.value.isEmpty() && !_githubLoading.value) {
            searchGitHub(_githubQuery.value)
        }
    }

    fun selectAmoCategory(category: AmoCategory) {
        _amoCategory.value = category
        searchAmo(_amoQuery.value, category)
    }

    fun updateAmoQuery(query: String) {
        _amoQuery.value = query
    }

    fun searchAmo(query: String = _amoQuery.value, category: AmoCategory = _amoCategory.value) {
        _amoQuery.value = query
        _amoCategory.value = category
        scope.launch {
            _amoLoading.value = true
            _amoError.value = null
            storeClient.searchAmoExtensions(query, category).fold(
                onSuccess = { items -> _amoResults.value = items },
                onFailure = { err -> _amoError.value = err.message ?: "Unable to reach Mozilla Add-ons" }
            )
            _amoLoading.value = false
        }
    }

    fun updateGitHubQuery(query: String) {
        _githubQuery.value = query
    }

    fun searchGitHub(query: String = _githubQuery.value) {
        _githubQuery.value = query
        scope.launch {
            _githubLoading.value = true
            _githubError.value = null
            storeClient.searchGitHubExtensions(query).fold(
                onSuccess = { items -> _githubResults.value = items },
                onFailure = { err -> _githubError.value = err.message ?: "Unable to reach GitHub API" }
            )
            _githubLoading.value = false
        }
    }

    fun inspectGitHubRepo(ownerRepoOrUrl: String) {
        scope.launch {
            _gitHubRepoLoading.value = true
            storeClient.fetchGitHubRepoDetail(ownerRepoOrUrl).fold(
                onSuccess = { detail -> _selectedGitHubRepo.value = detail },
                onFailure = { err ->
                    onToast(err.message ?: "Could not load repository releases")
                }
            )
            _gitHubRepoLoading.value = false
        }
    }

    fun clearSelectedGitHubRepo() {
        _selectedGitHubRepo.value = null
    }

    fun clearInstallState() {
        _installState.value = ExtensionInstallState.Idle
    }

    fun installCatalogItem(item: ExtensionCatalogItem) {
        if (item.source == ExtensionSource.GITHUB && !item.hasInstallableXpi && !item.sourceRepo.isNullOrBlank()) {
            inspectGitHubRepo(item.sourceRepo)
            return
        }
        installPackage(
            displayName = item.name,
            downloadUrl = item.downloadUrl,
            source = item.source,
            sourceRef = item.sourceRepo ?: item.id,
            homepageUrl = item.homepageUrl,
            iconUrl = item.iconUrl
        )
    }

    fun installGitHubAsset(detail: GitHubRepoDetail, asset: GitHubReleaseXpiAsset) {
        _selectedGitHubRepo.value = null
        installPackage(
            displayName = "${detail.name} (${asset.releaseTag})",
            downloadUrl = asset.downloadUrl,
            source = ExtensionSource.GITHUB,
            sourceRef = detail.ownerRepo,
            homepageUrl = detail.homepageUrl ?: "https://github.com/${detail.ownerRepo}",
            iconUrl = detail.avatarUrl
        )
    }

    fun installFromDirectInput(rawInput: String) {
        val ctrl = controller
        if (ctrl == null) {
            onToast("Extensions require the active Mozilla GeckoView engine.")
            return
        }
        scope.launch {
            _installState.value = ExtensionInstallState.Installing("Resolving package...")
            storeClient.resolveDirectXpiOrGitHub(rawInput).fold(
                onSuccess = { item ->
                    installPackageInternal(
                        ctrl = ctrl,
                        displayName = item.name,
                        downloadUrl = item.downloadUrl,
                        source = item.source,
                        sourceRef = item.sourceRepo ?: item.id,
                        homepageUrl = item.homepageUrl,
                        iconUrl = item.iconUrl
                    )
                },
                onFailure = { err ->
                    val msg = err.message ?: "Could not find an installable .xpi package"
                    _installState.value = ExtensionInstallState.Error(msg)
                    onToast(msg)
                }
            )
        }
    }

    private fun installPackage(
        displayName: String,
        downloadUrl: String,
        source: ExtensionSource,
        sourceRef: String,
        homepageUrl: String?,
        iconUrl: String?
    ) {
        val ctrl = controller
        if (ctrl == null) {
            onToast("Extensions require the active Mozilla GeckoView engine.")
            return
        }
        if (downloadUrl.isBlank()) {
            onToast("No installable .xpi URL available for $displayName")
            return
        }
        scope.launch {
            installPackageInternal(
                ctrl = ctrl,
                displayName = displayName,
                downloadUrl = downloadUrl,
                source = source,
                sourceRef = sourceRef,
                homepageUrl = homepageUrl,
                iconUrl = iconUrl
            )
        }
    }

    private suspend fun installPackageInternal(
        ctrl: BrowserExtensionsController,
        displayName: String,
        downloadUrl: String,
        source: ExtensionSource,
        sourceRef: String,
        homepageUrl: String?,
        iconUrl: String?
    ) {
        _installState.value = ExtensionInstallState.Installing(displayName)
        ctrl.installFromUrl(downloadUrl).fold(
            onSuccess = { installed ->
                repository.upsertExtensionRecord(
                    extensionId = installed.id,
                    name = installed.name,
                    source = source,
                    sourceRef = sourceRef,
                    installedVersion = installed.version,
                    homepageUrl = homepageUrl ?: installed.homepageUrl,
                    iconUrl = iconUrl
                )
                repository.updateExtensionCheckMetadata(
                    extensionId = installed.id,
                    latestVersion = null,
                    downloadUrl = null,
                    error = null
                )
                val msg = "Installed ${installed.name} v${installed.version}"
                _installState.value = ExtensionInstallState.Success(msg)
                onToast(msg)
            },
            onFailure = { err ->
                val msg = err.message ?: "Failed to install $displayName"
                _installState.value = ExtensionInstallState.Error(msg)
                onToast(msg)
            }
        )
    }

    fun toggleEnabled(extensionId: String, enabled: Boolean) {
        val ctrl = controller ?: return
        scope.launch {
            val res = if (enabled) ctrl.enableExtension(extensionId) else ctrl.disableExtension(extensionId)
            res.onFailure { onToast(it.message ?: "Failed to change extension state") }
        }
    }

    fun togglePrivateBrowsing(extensionId: String, allowed: Boolean) {
        val ctrl = controller ?: return
        scope.launch {
            ctrl.setAllowedInPrivateBrowsing(extensionId, allowed)
                .onFailure { onToast(it.message ?: "Failed to update private mode access") }
        }
    }

    fun toggleAutoUpdate(extensionId: String, enabled: Boolean) {
        scope.launch {
            val current = installedExtensions.value.firstOrNull { it.id == extensionId }
            val existing = repository.findExtensionRecord(extensionId)
            if (existing == null && current != null) {
                repository.upsertExtensionRecord(
                    extensionId = current.id,
                    name = current.name,
                    source = current.source,
                    sourceRef = current.sourceRef,
                    installedVersion = current.version,
                    homepageUrl = current.homepageUrl,
                    iconUrl = current.iconUrl
                )
            }
            repository.setExtensionAutoUpdate(extensionId, enabled)
            onToast(if (enabled) "Auto-update enabled" else "Auto-update paused")
        }
    }

    fun uninstall(extensionId: String) {
        val ctrl = controller ?: return
        scope.launch {
            ctrl.uninstallExtension(extensionId).fold(
                onSuccess = {
                    repository.deleteExtensionRecord(extensionId)
                    onToast("Extension uninstalled")
                },
                onFailure = { err ->
                    onToast(err.message ?: "Could not uninstall extension")
                }
            )
        }
    }

    fun triggerBrowserAction(extensionId: String) {
        controller?.triggerBrowserAction(extensionId)
    }

    fun openOptionsPage(extensionId: String) {
        controller?.openOptionsPage(extensionId)
    }

    fun dismissPopup() {
        controller?.dismissPopup()
    }

    fun updateSingleExtension(extensionId: String) {
        val ctrl = controller ?: return
        scope.launch {
            val info = installedExtensions.value.firstOrNull { it.id == extensionId } ?: return@launch
            val rec = repository.findExtensionRecord(extensionId)
            val updateUrl = info.availableUpdateUrl ?: rec?.latestDownloadUrl
            _installState.value = ExtensionInstallState.Installing("Updating ${info.name}...")
            val result = if (!updateUrl.isNullOrBlank()) {
                ctrl.installFromUrl(updateUrl)
            } else {
                ctrl.updateViaNativeManager(extensionId).mapCatching {
                    it ?: throw IllegalStateException("No update package available")
                }
            }
            result.fold(
                onSuccess = { updated ->
                    repository.upsertExtensionRecord(
                        extensionId = updated.id,
                        name = updated.name,
                        source = info.source,
                        sourceRef = info.sourceRef,
                        installedVersion = updated.version,
                        homepageUrl = updated.homepageUrl ?: info.homepageUrl,
                        iconUrl = info.iconUrl
                    )
                    repository.updateExtensionCheckMetadata(updated.id, null, null, null)
                    val msg = "Updated ${updated.name} to v${updated.version}"
                    _installState.value = ExtensionInstallState.Success(msg)
                    onToast(msg)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Update failed for ${info.name}"
                    _installState.value = ExtensionInstallState.Error(msg)
                    onToast(msg)
                }
            )
        }
    }

    fun updateAllAvailable() {
        checkAndApplyUpdates(silent = false, applyAutomatically = true)
    }

    fun checkAndApplyUpdates(silent: Boolean, applyAutomatically: Boolean) {
        val ctrl = controller ?: return
        scope.launch {
            if (!silent) {
                _installState.value = ExtensionInstallState.CheckingUpdates("Checking AMO & GitHub releases...")
            }
            val currentInstalled = ctrl.refreshInstalled().filterNot { it.isBuiltIn }
            val recordsMap = currentInstalled.associate { it.id to repository.findExtensionRecord(it.id) }
            val now = System.currentTimeMillis()
            var updatedCount = 0
            var availableCount = 0

            for (ext in currentInstalled) {
                val rec = recordsMap[ext.id]
                val shouldCheck = !silent || (rec?.autoUpdateEnabled ?: true)
                if (!shouldCheck) continue

                val source = rec?.let { ExtensionSource.fromId(it.sourceType) } ?: ext.source
                val sourceRef = rec?.sourceRef?.takeIf { it.isNotBlank() } ?: ext.sourceRef
                var candidateVersion: String? = null
                var candidateUrl: String? = null

                when (source) {
                    ExtensionSource.GITHUB -> {
                        val repo = ExtensionStoreClient.extractOwnerRepoFromInput(sourceRef)
                        if (repo != null) {
                            val asset = storeClient.fetchGitHubRepoDetail(repo).getOrNull()?.xpiAssets?.firstOrNull()
                            if (asset != null && ExtensionVersionComparator.isNewer(asset.releaseTag, ext.version)) {
                                candidateVersion = asset.releaseTag
                                candidateUrl = asset.downloadUrl
                            }
                        }
                    }

                    ExtensionSource.AMO -> {
                        val amoItem = storeClient.fetchAmoAddon(ext.id).getOrNull()
                        if (amoItem != null && ExtensionVersionComparator.isNewer(amoItem.version, ext.version)) {
                            candidateVersion = amoItem.version
                            candidateUrl = amoItem.downloadUrl
                        }
                    }

                    ExtensionSource.DIRECT_URL, ExtensionSource.BUILTIN -> Unit
                }

                if (candidateVersion != null && !candidateUrl.isNullOrBlank()) {
                    availableCount += 1
                    ensureRecordExists(ext, source, sourceRef)
                    if (applyAutomatically && (rec?.autoUpdateEnabled ?: true)) {
                        val res = ctrl.installFromUrl(candidateUrl)
                        if (res.isSuccess) {
                            updatedCount += 1
                            val updatedInfo = res.getOrThrow()
                            repository.upsertExtensionRecord(
                                extensionId = ext.id,
                                name = updatedInfo.name,
                                source = source,
                                sourceRef = sourceRef,
                                installedVersion = updatedInfo.version,
                                homepageUrl = updatedInfo.homepageUrl,
                                iconUrl = ext.iconUrl
                            )
                            repository.updateExtensionCheckMetadata(ext.id, null, null, null)
                        } else {
                            repository.updateExtensionCheckMetadata(
                                extensionId = ext.id,
                                latestVersion = candidateVersion,
                                downloadUrl = candidateUrl,
                                error = res.exceptionOrNull()?.message
                            )
                        }
                    } else {
                        repository.updateExtensionCheckMetadata(
                            extensionId = ext.id,
                            latestVersion = candidateVersion,
                            downloadUrl = candidateUrl,
                            error = null
                        )
                    }
                } else {
                    if (applyAutomatically && (rec?.autoUpdateEnabled ?: true)) {
                        val nativeUpdated = ctrl.updateViaNativeManager(ext.id).getOrNull()
                        if (nativeUpdated != null && ExtensionVersionComparator.isNewer(nativeUpdated.version, ext.version)) {
                            updatedCount += 1
                            ensureRecordExists(ext, source, sourceRef)
                            repository.upsertExtensionRecord(
                                extensionId = ext.id,
                                name = nativeUpdated.name,
                                source = source,
                                sourceRef = sourceRef,
                                installedVersion = nativeUpdated.version,
                                homepageUrl = nativeUpdated.homepageUrl,
                                iconUrl = ext.iconUrl
                            )
                            repository.updateExtensionCheckMetadata(ext.id, null, null, null)
                            continue
                        }
                    }
                    if (rec != null) {
                        repository.updateExtensionCheckMetadata(ext.id, null, null, null)
                    }
                }
            }

            repository.setLastExtensionUpdateCheckAt(now)
            val summary = when {
                updatedCount > 0 -> "Auto-updated $updatedCount extension(s)"
                availableCount > 0 -> "$availableCount extension update(s) ready"
                else -> "All extensions are up to date"
            }
            if (!silent || updatedCount > 0) {
                _installState.value = ExtensionInstallState.Success(summary)
                onToast(summary)
            }
        }
    }

    private suspend fun ensureRecordExists(
        ext: InstalledExtensionInfo,
        source: ExtensionSource,
        sourceRef: String
    ) {
        if (repository.findExtensionRecord(ext.id) == null) {
            repository.upsertExtensionRecord(
                extensionId = ext.id,
                name = ext.name,
                source = source,
                sourceRef = sourceRef,
                installedVersion = ext.version,
                homepageUrl = ext.homepageUrl,
                iconUrl = ext.iconUrl
            )
        }
    }
}
