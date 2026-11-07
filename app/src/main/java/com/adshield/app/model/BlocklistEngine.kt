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
    // @@ exception rules from the bundled blocklist (path-aware where specified)
    private val exceptionDomains = ConcurrentHashMap.newKeySet<String>()
    // Path-scoped network rules: ||host^path*  -> host => path rules to block
    private val blockedPathRules = ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<PathRule>>()
    // Path-scoped exceptions: @@||host^path* -> host => path rules to allow
    private val exceptionPathRules = ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<PathRule>>()
    private val isReady = AtomicBoolean(false)
    private val decisionCache = ConcurrentHashMap<String, Boolean>()

    @Synchronized
    fun initialize() {
        if (isReady.get()) return

        try {
            val startTime = System.currentTimeMillis()
            var count = 0

            // Load core blocklist from assets (plain domains ||host^ path rules and @@ exceptions supported)
            context.assets.open("blocklist_core.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val rule = parseRule(trimmed)
                            if (rule != null) {
                                applyRule(rule)
                                count++
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }

            // Load user custom whitelist / blacklist (plain domains only)
            loadCustomRules()

            isReady.set(true)
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Blocklist initialized with $count rules in ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load blocklist", e)
        }
    }

    /**
     * Path-scoped rule entry.
     * [isWildcard] indicates prefix matching (e.g. ||host^path*); otherwise ABP separator
     * semantics are applied so /tr only matches /tr, /tr/, /tr?... and not /translate.
     */
    private data class PathRule(
        val prefix: String,
        val isWildcard: Boolean
    )

    private data class Rule(
        val exception: Boolean,
        val host: String,
        val pathRule: PathRule?
    )

    private fun parseRule(line: String): Rule? {
        var body = line.trim()
        var exception = false
        if (body.startsWith("@@")) {
            exception = true
            body = body.substring(2).trim()
        }
        if (body.isEmpty()) return null

        if (body.startsWith("||")) {
            body = body.substring(2).trim()
        }
        if (body.isEmpty()) return null

        var host: String
        var pathPrefix: String? = null
        var isWildcard = false

        val sep = body.indexOf('^')
        val slash = body.indexOf('/')
        if (sep >= 0 && (slash < 0 || sep < slash)) {
            // ^ anchor present: ||host^path*  (path starts immediately after ^, no leading slash)
            host = body.substring(0, sep)
            val rest = body.substring(sep + 1).trim()
            if (rest.isNotEmpty()) {
                if (rest.endsWith("*")) {
                    isWildcard = true
                }
                var clean = rest.trimEnd('*').trimEnd('^').trim()
                if (clean.isNotEmpty() && clean != "*") {
                    if (!clean.startsWith("/")) clean = "/$clean"
                    pathPrefix = clean
                }
            }
        } else if (slash >= 0) {
            host = body.substring(0, slash)
            val rest = body.substring(slash).trim()
            if (rest.endsWith("*")) {
                isWildcard = true
            }
            var clean = rest.trimEnd('*').trimEnd('^').trim()
            if (clean.isNotEmpty() && clean != "/" && clean != "*") {
                pathPrefix = clean
            }
        } else {
            host = body
        }

        val normalized = normalizeDomain(host)
        if (normalized.isEmpty()) return null

        val pathRule = if (!pathPrefix.isNullOrEmpty()) {
            PathRule(pathPrefix, isWildcard)
        } else {
            null
        }

        return Rule(exception, normalized, pathRule)
    }

    private fun applyRule(rule: Rule) {
        val pathRule = rule.pathRule
        if (pathRule == null) {
            if (rule.exception) {
                exceptionDomains.add(rule.host)
                whitelistDomains.add(rule.host)
            } else {
                blockedDomains.add(rule.host)
                coreBlockedDomains.add(rule.host)
            }
            return
        }

        val ruleMap = if (rule.exception) exceptionPathRules else blockedPathRules
        val list = ruleMap.getOrPut(rule.host) { java.util.concurrent.CopyOnWriteArrayList() }
        list.add(pathRule)
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
        "android.com",
        // Financial & Secure Payment Gateways (Guaranteed never blocked)
        "paypal.com",
        "paypalobjects.com",
        "stripe.com",
        "stripe.network",
        "visa.com",
        "mastercard.com",
        "americanexpress.com",
        "razorpay.com",
        "plaid.com",
        "pay.google.com",
        "npci.org.in",
        // Anti-Bot & Captcha Verification Providers (Prevents login breakage)
        "challenges.cloudflare.com",
        "cloudflare.com",
        "hcaptcha.com",
        "recaptcha.net"
    )

    /**
     * Domain-level blocking decision (cached by raw domain). Path is NOT considered here;
     * use [isUrlBlocked] for path-aware decisions.
     */
    fun isDomainBlocked(rawDomain: String): Boolean {
        val cached = decisionCache[rawDomain]
        if (cached != null) return cached

        if (!isReady.get()) initialize()
        if (!isReady.get()) return false

        val domain = normalizeDomain(rawDomain)
        if (domain.isEmpty()) {
            decisionCache[rawDomain] = false
            return false
        }

        // Precedence: explicit user/custom whitelist > user/custom block >
        // bundled exceptions > essential services > bundled blocklist.
        val blocked = if (matchesDomainOrParent(domain, customWhitelistDomains)) false
        else if (matchesDomainOrParent(domain, customBlockedDomains)) true
        else if (matchesDomainOrParent(domain, exceptionDomains)) false
        else if (isWhitelisted(domain)) false
        else matchesDomainOrParent(domain, coreBlockedDomains)

        if (decisionCache.size < 50000) {
            decisionCache[rawDomain] = blocked
        }
        return blocked
    }

    /**
     * Evaluates path-scoped rules against a request path & full path.
     * When [rule.isWildcard] is true, matches if path or fullPath starts with prefix.
     * When false (default for ABP separator rules like ||facebook.com^tr), matches exact
     * path, or separator boundary ('/', '?', '#'), preventing /tr from blocking /translate.
     */
    private fun matchesPathRule(path: String, fullPath: String, rule: PathRule): Boolean {
        val p = rule.prefix
        if (rule.isWildcard) {
            return path.startsWith(p) || fullPath.startsWith(p)
        }
        return path == p ||
            path.startsWith("$p/") ||
            path.startsWith("$p?") ||
            path.startsWith("$p#") ||
            fullPath.startsWith("$p?") ||
            fullPath.startsWith("$p/")
    }

    /**
     * Full URL-aware blocking decision. Path-scoped rules (`||host^path*`, `@@||host^path*`)
     * are evaluated first; otherwise falls back to the domain-level decision.
     * Exceptions take precedence over blocks at every level.
     */
    fun isUrlBlocked(rawUrl: String): Boolean {
        if (!isReady.get()) initialize()
        if (!isReady.get()) return false

        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        val domain = normalizeDomain(host)
        if (domain.isEmpty()) return false

        // User explicit custom rules always take highest precedence
        if (matchesDomainOrParent(domain, customWhitelistDomains)) return false
        if (matchesDomainOrParent(domain, customBlockedDomains)) return true

        val rawPath = uri.path
        val path = if (!rawPath.isNullOrEmpty()) rawPath else "/"
        val query = uri.query
        val fullPath = if (!query.isNullOrEmpty()) "$path?$query" else path

        // Walk up the host hierarchy so rules on a parent domain apply to subdomains too.
        var current = domain
        while (true) {
            // Path-scoped exceptions win first
            exceptionPathRules[current]?.let { rules ->
                if (rules.any { r -> matchesPathRule(path, fullPath, r) }) return false
            }
            // Path-scoped blocks
            blockedPathRules[current]?.let { rules ->
                if (rules.any { r -> matchesPathRule(path, fullPath, r) }) return true
            }
            val dotIndex = current.indexOf('.')
            if (dotIndex < 0 || dotIndex == current.lastIndex) break
            current = current.substring(dotIndex + 1)
        }

        return isDomainBlocked(domain)
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
