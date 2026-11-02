package com.adshield.app.model

import android.content.Context
import android.net.Uri
import android.util.Log
import org.mlm.adblock.AdblockEngine
import org.mlm.adblock.BlockDecision
import org.mlm.adblock.CosmeticResources
import org.mlm.adblock.RequestTypeMapper
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance Content Blocking Engine powered by Brave's native `adblock-rust`.
 *
 * Provides:
 * - Sub-millisecond C-FFI network request interception with trie matching
 * - uBlock Origin & EasyList ABP rule compatibility ($third-party, $redirect, $removeparam)
 * - Automatic scriptlet injection (+js) and cosmetic CSS element hiding per page
 * - Cold-start binary serialization caching (<20ms startup)
 * - Safe fallback to legacy domain blocklist if native libraries cannot be loaded
 */
class BraveAdblockEngine(private val context: Context) {

    private var engine: AdblockEngine? = null
    private val isReady = AtomicBoolean(false)
    private val isFailed = AtomicBoolean(false)

    fun initialize() {
        if (isReady.get() || isFailed.get()) return

        synchronized(this) {
            if (isReady.get() || isFailed.get()) return

            try {
                val startTime = System.currentTimeMillis()
                val instance = AdblockEngine.create()

                // Clean up legacy cache if present
                val oldCacheFile = File(context.cacheDir, "adblock_rust_v1.bin")
                if (oldCacheFile.exists()) oldCacheFile.delete()

                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)

                var loadedFromCache = false
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    try {
                        loadedFromCache = instance.deserializeFrom(cacheFile)
                        if (loadedFromCache) {
                            Log.d(TAG, "Successfully deserialized Brave engine cache (${cacheFile.length()} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Cache deserialization failed, recompiling from assets", e)
                        cacheFile.delete()
                        loadedFromCache = false
                    }
                }

                if (!loadedFromCache) {
                    val rulesBuilder = StringBuilder(3 * 1024 * 1024)

                    // 1. Load curated ABP / uBlock rules
                    try {
                        context.assets.open("adblock_rules_curated.txt").use { input ->
                            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                                reader.forEachLine { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.isNotEmpty() && !trimmed.startsWith("!")) {
                                        rulesBuilder.append(trimmed).append('\n')
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not load adblock_rules_curated.txt", e)
                    }

                    // 2. Load core blocklist domains and format as ABP network filters
                    try {
                        context.assets.open("blocklist_core.txt").use { input ->
                            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                                reader.forEachLine { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                                        // Convert domain to standard ABP network rule
                                        rulesBuilder.append("||").append(trimmed).append("^\n")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not load blocklist_core.txt for Brave engine", e)
                    }

                    val rulesText = rulesBuilder.toString()
                    instance.loadFilterList(rulesText)
                    Log.d(TAG, "Loaded rules into Brave adblock-rust engine (length: ${rulesText.length})")

                    // Serialize compiled engine for subsequent fast starts
                    try {
                        instance.serializeTo(cacheFile)
                        Log.d(TAG, "Serialized Brave engine to cache (${cacheFile.length()} bytes)")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to serialize Brave engine cache", e)
                    }
                }

                // Load scriptlet & redirect resources (e.g. assets/adblock/resources.json)
                try {
                    context.assets.open("adblock/resources.json").use { input ->
                        val resourcesJson = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        instance.loadResources(resourcesJson)
                        Log.d(TAG, "Loaded Brave scriptlet & redirect resources")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bundled adblock/resources.json not loaded: ${e.message}")
                }

                engine = instance
                isReady.set(true)
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "Brave adblock-rust engine successfully initialized in ${duration}ms")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize native Brave adblock-rust engine; falling back to legacy", t)
                isFailed.set(true)
                engine = null
            }
        }
    }

    /**
     * Checks if a network request should be blocked, rewritten, or served a replacement resource.
     */
    fun checkNetworkRequest(url: String, sourceUrl: String, mimeOrType: String?): BlockDecision {
        val currentEngine = engine
        if (currentEngine == null || !isReady.get()) {
            return FALLBACK_ALLOW_DECISION
        }

        return try {
            val parsedUri = Uri.parse(url)
            val requestType = RequestTypeMapper.from(parsedUri, mimeOrType)
            currentEngine.checkNetworkRequest(url, sourceUrl, requestType)
        } catch (t: Throwable) {
            Log.w(TAG, "Error checking network request: ${t.message}")
            FALLBACK_ALLOW_DECISION
        }
    }

    /**
     * Extracts cosmetic filtering CSS selectors and scriptlets for the given page URL.
     */
    fun getCosmeticResources(pageUrl: String): CosmeticResources? {
        val currentEngine = engine
        if (currentEngine == null || !isReady.get()) return null

        return try {
            currentEngine.cosmeticResources(pageUrl)
        } catch (t: Throwable) {
            Log.w(TAG, "Error getting cosmetic resources: ${t.message}")
            null
        }
    }

    fun isEngineReady(): Boolean = isReady.get() && engine != null

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {}
        engine = null
        isReady.set(false)
    }

    companion object {
        private const val TAG = "BraveAdblockEngine"
        private const val CACHE_FILE_NAME = "adblock_rust_v2_easylist.bin"

        private val FALLBACK_ALLOW_DECISION = BlockDecision(
            matched = false,
            important = false,
            redirect = null,
            rewrittenUrl = null,
            exception = false
        )
    }
}
