package com.simpleaibrowser.sab.core.browser

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide memory of per-site permission decisions ("remember my choice").
 *
 * The engine layer consults this store BEFORE showing a site permission prompt, so a granted
 * site is not asked again on reload or navigation (this was the "always asks permission" bug).
 * The data layer (Room `site_permissions` table) persists decisions across app restarts via
 * the [setPersistenceHooks] callbacks, which are registered by the ViewModel at startup.
 *
 * Pure Kotlin, no Android dependencies: safe to call from the engine layer and from unit tests.
 */
object SitePermissionStore {

    data class SitePermission(
        val origin: String,
        val permission: String,
        val allow: Boolean,
        val updatedAt: Long = System.currentTimeMillis()
    )

    private val memory = ConcurrentHashMap<String, SitePermission>()

    @Volatile
    private var saver: ((SitePermission) -> Unit)? = null

    @Volatile
    private var revoker: ((origin: String, permission: String) -> Unit)? = null

    /** Registers the asynchronous persistence hooks (no-ops until the ViewModel wires them). */
    fun setPersistenceHooks(
        saver: ((SitePermission) -> Unit)?,
        revoker: ((origin: String, permission: String) -> Unit)?
    ) {
        this.saver = saver
        this.revoker = revoker
    }

    private fun key(origin: String, permission: String): String =
        "${origin.trim().lowercase()}|$permission"

    /** The remembered decision for an origin/permission pair, or null when the site must be asked. */
    fun decisionFor(origin: String, permission: String): Boolean? {
        if (origin.isBlank() || permission.isBlank()) return null
        return memory[key(origin, permission)]?.allow
    }

    /** Stores a decision, replaces any previous one, and persists it asynchronously. */
    fun record(origin: String, permission: String, allow: Boolean) {
        if (origin.isBlank() || permission.isBlank()) return
        val item = SitePermission(
            origin = origin.trim().lowercase(),
            permission = permission.trim(),
            allow = allow
        )
        memory[key(item.origin, item.permission)] = item
        runCatching { saver?.invoke(item) }
    }

    /** Removes a single remembered decision (the site is asked again next time). */
    fun revoke(origin: String, permission: String) {
        memory.remove(key(origin, permission))
        runCatching { revoker?.invoke(origin, permission) }
    }

    /** Removes every remembered decision for one site (the "forget this site" action). */
    fun revokeAllForOrigin(origin: String) {
        val prefix = "${origin.trim().lowercase()}|"
        memory.keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) }
        runCatching { revoker?.invoke(origin, "*") }
    }

    /** Clears everything (the "reset all site permissions" action). */
    fun clearAll() {
        memory.clear()
        runCatching { revoker?.invoke("*", "*") }
    }

    /** Replaces the in-memory snapshot, e.g. when the persisted list is loaded at startup. */
    fun replaceAll(items: List<SitePermission>) {
        memory.clear()
        items.forEach { memory[key(it.origin, it.permission)] = it }
    }

    fun all(): List<SitePermission> =
        memory.values.sortedWith(compareByDescending<SitePermission> { it.updatedAt }.thenBy { it.origin })
}
