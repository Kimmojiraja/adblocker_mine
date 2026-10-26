package com.adshield.app.model

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BlocklistEngine(private val context: Context) {

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val whitelistDomains = ConcurrentHashMap.newKeySet<String>()
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

        blockedDomains.addAll(customBlacklist.map { it.lowercase() })
        whitelistDomains.addAll(customWhitelist.map { it.lowercase() })
    }

    fun isDomainBlocked(rawDomain: String): Boolean {
        if (!isReady.get()) return false

        val domain = rawDomain.lowercase().trimEnd('.')
        if (domain.isEmpty()) return false

        // 1. Whitelist always takes precedence
        if (whitelistDomains.contains(domain)) return false

        // 2. Direct exact match
        if (blockedDomains.contains(domain)) return true

        // 3. Hierarchical subdomain match (e.g. "video-ads.doubleclick.net" -> "doubleclick.net")
        var dotIndex = domain.indexOf('.')
        while (dotIndex != -1 && dotIndex < domain.length - 1) {
            val parentDomain = domain.substring(dotIndex + 1)
            if (whitelistDomains.contains(parentDomain)) return false
            if (blockedDomains.contains(parentDomain)) return true
            dotIndex = domain.indexOf('.', dotIndex + 1)
        }

        return false
    }

    fun addCustomBlockedDomain(domain: String) {
        val normalized = domain.trim().lowercase()
        if (normalized.isNotEmpty()) {
            blockedDomains.add(normalized)
            whitelistDomains.remove(normalized)
            saveCustomRules()
        }
    }

    fun addCustomWhitelistedDomain(domain: String) {
        val normalized = domain.trim().lowercase()
        if (normalized.isNotEmpty()) {
            whitelistDomains.add(normalized)
            blockedDomains.remove(normalized)
            saveCustomRules()
        }
    }

    fun removeCustomRule(domain: String) {
        val normalized = domain.trim().lowercase()
        blockedDomains.remove(normalized)
        whitelistDomains.remove(normalized)
        saveCustomRules()
    }

    private fun saveCustomRules() {
        val prefs = context.getSharedPreferences("adshield_custom_rules", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("custom_blacklist", blockedDomains)
            .putStringSet("custom_whitelist", whitelistDomains)
            .apply()
    }

    fun getLoadedCount(): Int = blockedDomains.size

    companion object {
        private const val TAG = "BlocklistEngine"
    }
}
