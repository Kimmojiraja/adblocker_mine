package com.adshield.app.model

import android.content.Context
import android.util.Log
import com.adshield.app.AdShieldApp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object SelectorSyncManager {

    private const val TAG = "SelectorSyncManager"
    private const val PREF_KEY_SELECTORS_JSON = "key_remote_selectors_json"
    private const val SELECTORS_URL = "https://raw.githubusercontent.com/Kimmojiraja/adblocker_mine/main/selectors.json"

    private val defaultButtonIds = listOf(
        "skip_ad_button",
        "ad_skip_button",
        "skip_ad_button_native",
        "modern_skip_ad_button"
    )

    private val defaultSkipTexts = listOf(
        "Skip Ad",
        "Skip",
        "Saltar",
        "Passer",
        "Overskrid",
        "Pular",
        "Überspringen",
        "Пропустить",
        "Ignorar",
        "विज्ञापन छोड़ें"
    )

    private val defaultProtectedPanelIds = listOf(
        "comments",
        "playlist_panel",
        "playlist_header",
        "playlist_items",
        "queue_panel"
    )

    fun getSkipButtonIds(): List<String> {
        val cached = getCachedList("button_ids")
        return if (cached.isNotEmpty()) cached else defaultButtonIds
    }

    fun getSkipTexts(): List<String> {
        val cached = getCachedList("skip_texts")
        return if (cached.isNotEmpty()) cached else defaultSkipTexts
    }

    fun getProtectedPanelIds(): List<String> {
        val cached = getCachedList("protected_panel_ids")
        return if (cached.isNotEmpty()) cached else defaultProtectedPanelIds
    }

    private fun getCachedList(key: String): List<String> {
        return try {
            val jsonStr = AdShieldApp.instance.prefs.getString(PREF_KEY_SELECTORS_JSON, null) ?: return emptyList()
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray(key) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Asynchronously fetches updated selector rules from the GitHub repository without blocking the UI.
     */
    fun syncSelectorsAsync() {
        thread(name = "FokkShieldSelectorSync") {
            try {
                val url = URL(SELECTORS_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "FokkShield-Android")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = conn.inputStream.bufferedReader().use { it.readText() }
                    // Validate JSON
                    JSONObject(content)
                    AdShieldApp.instance.prefs.edit().putString(PREF_KEY_SELECTORS_JSON, content).apply()
                    Log.d(TAG, "Successfully synced remote selectors from GitHub")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "Selector sync skipped (offline or unreachable): ${e.message}")
            }
        }
    }
}
