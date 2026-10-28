package com.adshield.app.model

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BlocklistEngine(private val context: Context) {

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val coreBlockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val whitelistDomains = ConcurrentHashMap.newKeySet<String>()
    private val customBlockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val customWhitelistDomains = ConcurrentHashMap.newKeySet<String>()
    private val isReady = AtomicBoolean(false)

    fun initialize() {
        if (isReady.get()) return

        try {
            val startTime = System.currentTimeMillis()
            var count = 0

            // Load core blocklist from assets
            context.assets.open("blocklist_core.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim().lowercase()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            blockedDomains.add(trimmed)
                            coreBlockedDomains.add(trimmed)
                            count++
                        }
                        line = reader.readLine()
                    }
                }
            }

            // Load user custom whitelist / blacklist
            loadCustomRules()

            isReady.set(true)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Blocklist initialized with $count domains in ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist", e)
        }
    }

    private fun loadCustomRules() {
        val prefs = context.getSharedPreferences("adshield_custom_rules", Context.MODE_PRIVATE)
        val customBlacklist = prefs.getStringSet("custom_blacklist", emptySet()) ?: emptySet()
        val customWhitelist = prefs.getStringSet("custom_whitelist", emptySet()) ?: emptySet()

        customBlockedDomains.addAll(customBlacklist.map(::normalizeDomain).filter { it.isNotEmpty() })
        customWhitelistDomains.addAll(customWhitelist.map(::normalizeDomain).filter { it.isNotEmpty() })
        blockedDomains.addAll(customBlockedDomains)
        whitelistDomains.addAll(customWhitelistDomains)
    }

    private val essentialWhitelist = setOf(
        "youtube.com",
        "m.youtube.com",
        "googlevideo.com",
        "ytimg.com",
        "i.ytimg.com",
        "ggpht.com",
        "gstatic.com",
        "googleapis.com",
        "accounts.google.com",
        "play.google.com",
        "gvt1.com",
        "googleusercontent.com",
        "1e100.net",
        "android.com"
    )

    fun isDomainBlocked(rawDomain: String): Boolean {
        if (!isReady.get()) return false

        val domain = normalizeDomain(rawDomain)
        if (domain.isEmpty()) return false

        // 1. User custom whitelist takes absolute priority
        if (whitelistDomains.contains(domain)) return false

        // 2. Direct exact block match
        if (blockedDomains.contains(domain)) return true

        // 3. Essential streaming/infrastructure whitelist
        if (isWhitelisted(domain)) return false

        // 4. Hierarchical subdomain match (e.g. "video-ads.doubleclick.net" -> "doubleclick.net")
        var dotIndex = domain.indexOf('.')
        while (dotIndex != -1 && dotIndex < domain.length - 1) {
            val parentDomain = domain.substring(dotIndex + 1)
            if (whitelistDomains.contains(parentDomain)) return false
            if (blockedDomains.contains(parentDomain)) return true
            if (isWhitelisted(parentDomain)) return false
            dotIndex = domain.indexOf('.', dotIndex + 1)
        }

        return false
    }

    private fun isWhitelisted(domain: String): Boolean {
        if (essentialWhitelist.contains(domain)) return true
        for (w in essentialWhitelist) {
            if (domain.endsWith(".$w")) return true
        }
        return false
    }

    fun addCustomBlockedDomain(domain: String) {
        val normalized = normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            blockedDomains.add(normalized)
            whitelistDomains.remove(normalized)
            customBlockedDomains.add(normalized)
            customWhitelistDomains.remove(normalized)
            saveCustomRules()
        }
    }

    fun addCustomWhitelistedDomain(domain: String) {
        val normalized = normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            whitelistDomains.add(normalized)
            // Keep the core rule in memory. The whitelist takes precedence while it is
            // enabled and removing it must reliably restore the bundled protection.
            if (!coreBlockedDomains.contains(normalized)) blockedDomains.remove(normalized)
            customWhitelistDomains.add(normalized)
            customBlockedDomains.remove(normalized)
            saveCustomRules()
        }
    }

    fun removeCustomRule(domain: String) {
        val normalized = normalizeDomain(domain)
        if (coreBlockedDomains.contains(normalized)) {
            blockedDomains.add(normalized)
        } else {
            blockedDomains.remove(normalized)
        }
        whitelistDomains.remove(normalized)
        customBlockedDomains.remove(normalized)
        customWhitelistDomains.remove(normalized)
        saveCustomRules()
    }

    private fun saveCustomRules() {
        val prefs = context.getSharedPreferences("adshield_custom_rules", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("custom_blacklist", customBlockedDomains)
            .putStringSet("custom_whitelist", customWhitelistDomains)
            .apply()
    }

    private fun normalizeDomain(value: String): String = value.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
        .trimEnd('.')

    fun getLoadedCount(): Int = blockedDomains.size

    companion object {
        private const val TAG = "BlocklistEngine"
    }
}
