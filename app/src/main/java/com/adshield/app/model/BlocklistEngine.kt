package com.adshield.app.model

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.IDN
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

class BlocklistEngine(private val context: Context) {

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val coreBlockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val whitelistDomains = ConcurrentHashMap.newKeySet<String>()
    private val customBlockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val customWhitelistDomains = ConcurrentHashMap.newKeySet<String>()
    private val isReady = AtomicBoolean(false)
    private val decisionCache = ConcurrentHashMap<String, Boolean>()

    @Synchronized
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
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val normalized = normalizeDomain(trimmed)
                            if (normalized.isNotEmpty()) {
                                blockedDomains.add(normalized)
                                coreBlockedDomains.add(normalized)
                                count++
                            }
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
        val cached = decisionCache[rawDomain]
        if (cached != null) return cached

        // Application startup warms the list on IO, but WebView can issue requests
        // before that task runs. Complete local initialization before failing open.
        if (!isReady.get()) initialize()
        if (!isReady.get()) return false

        val domain = normalizeDomain(rawDomain)
        if (domain.isEmpty()) {
            decisionCache[rawDomain] = false
            return false
        }

        // Evaluate complete hierarchies in precedence order so a parent whitelist
        // cannot be bypassed by an exact block entry for one of its subdomains.
        val blocked = if (matchesDomainOrParent(domain, customWhitelistDomains)) false
        else if (matchesDomainOrParent(domain, customBlockedDomains)) true
        else if (isWhitelisted(domain)) false
        else matchesDomainOrParent(domain, coreBlockedDomains)

        if (decisionCache.size < 50000) {
            decisionCache[rawDomain] = blocked
        }
        return blocked
    }

    private fun matchesDomainOrParent(domain: String, candidates: Set<String>): Boolean {
        var current = domain
        while (true) {
            if (candidates.contains(current)) return true
            val dotIndex = current.indexOf('.')
            if (dotIndex < 0 || dotIndex == current.lastIndex) return false
            current = current.substring(dotIndex + 1)
        }
    }

    private fun isWhitelisted(domain: String): Boolean =
        matchesDomainOrParent(domain, essentialWhitelist)

    fun addCustomBlockedDomain(domain: String) {
        val normalized = normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            blockedDomains.add(normalized)
            whitelistDomains.remove(normalized)
            customBlockedDomains.add(normalized)
            customWhitelistDomains.remove(normalized)
            decisionCache.clear()
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
            decisionCache.clear()
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
        decisionCache.clear()
        saveCustomRules()
    }

    private fun saveCustomRules() {
        val prefs = context.getSharedPreferences("adshield_custom_rules", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("custom_blacklist", customBlockedDomains.toSet())
            .putStringSet("custom_whitelist", customWhitelistDomains.toSet())
            .apply()
    }

    private fun normalizeDomain(value: String): String {
        val input = value.trim()
        if (input.isEmpty() || '*' in input) return ""
        val uri = runCatching {
            Uri.parse(if (input.contains("://")) input else "http://$input")
        }.getOrNull() ?: return ""
        val host = uri.host?.trim('[', ']')?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
        if (host.isEmpty()) return ""
        if (':' in host) return host // IPv6 literals are already ASCII and have no DNS suffix hierarchy.

        val asciiHost = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }
            .getOrNull()
            ?.lowercase(Locale.ROOT)
            ?: return ""
        if (asciiHost.length > 253 || asciiHost.split('.').any { label ->
                label.isEmpty() || label.length > 63 || label.startsWith('-') || label.endsWith('-')
            }) return ""
        return asciiHost
    }

    fun getLoadedCount(): Int = blockedDomains.size

    companion object {
        private const val TAG = "BlocklistEngine"
    }
}
