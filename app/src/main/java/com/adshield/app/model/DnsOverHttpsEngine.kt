package com.adshield.app.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Encrypted DNS-over-HTTPS (DoH) Resolver.
 * Bypasses local ISP domain blocking, censorship, and DNS tampering without a VPN.
 * Queries Cloudflare / Quad9 via encrypted HTTPS with in-memory TTL caching.
 */
class DnsOverHttpsEngine {

    companion object {
        private const val TAG = "FokkDoHEngine"
        const val PROVIDER_CLOUDFLARE = "Cloudflare (1.1.1.1)"
        const val PROVIDER_QUAD9 = "Quad9 (9.9.9.9)"
        const val PROVIDER_GOOGLE = "Google (8.8.8.8)"
    }

    enum class Provider(val endpoint: String) {
        CLOUDFLARE("https://cloudflare-dns.com/dns-query"),
        QUAD9("https://dns.quad9.net:5053/dns-query"),
        GOOGLE("https://dns.google/resolve")
    }

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    var currentProvider: Provider = Provider.CLOUDFLARE
    var isEnabled: Boolean = true

    suspend fun resolve(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        if (!isEnabled || host.isBlank()) {
            return@withContext runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        }

        val cached = cache[host]
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
            return@withContext cached.addresses
        }

        try {
            val queryUrl = "${currentProvider.endpoint}?name=${host}&type=A"
            val url = URL(queryUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("User-Agent", "FokkShield/1.0")
            }

            if (conn.responseCode == 200) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseStr)
                val status = json.optInt("Status", -1)

                if (status == 0 && json.has("Answer")) {
                    val answers = json.getJSONArray("Answer")
                    val result = mutableListOf<InetAddress>()
                    var minTtl = 300L

                    for (i in 0 until answers.length()) {
                        val record = answers.getJSONObject(i)
                        val type = record.optInt("type", 1)
                        if (type == 1) { // Type A (IPv4)
                            val ip = record.optString("data", "")
                            val ttl = record.optLong("TTL", 300L)
                            if (ttl < minTtl) minTtl = ttl
                            if (ip.isNotEmpty()) {
                                runCatching {
                                    result.add(InetAddress.getByName(ip))
                                }
                            }
                        }
                    }

                    if (result.isNotEmpty()) {
                        val ttlMs = (minTtl.coerceAtLeast(60L) * 1000L)
                        cache[host] = CacheEntry(result, System.currentTimeMillis() + ttlMs)
                        return@withContext result
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DoH lookup failed for $host, falling back to system DNS: ${e.message}")
        }

        // Fallback to system DNS
        val fallback = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        if (fallback.isNotEmpty()) {
            cache[host] = CacheEntry(fallback, System.currentTimeMillis() + 60000L)
        }
        fallback
    }

    fun clearCache() {
        cache.clear()
    }
}
