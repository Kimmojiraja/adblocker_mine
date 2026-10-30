package com.adshield.app.model

import android.net.Uri
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

/** Stores text-only article snapshots in app-private storage for dependable offline reading. */
class OfflinePageStore(
    private val filesDir: File,
    private val preferences: SharedPreferences
) {
    data class SavedPage(
        val id: String?,
        val title: String,
        val url: String,
        val savedAt: Long
    )

    @Synchronized
    fun save(title: String, url: String, text: String): SavedPage {
        val safeUrl = url.trim()
        require(safeUrl.length <= MAX_URL_LENGTH) { "The saved page URL is too long" }
        require(isSafeWebUrl(safeUrl)) { "Only valid HTTP or HTTPS pages can be saved offline" }
        val safeTitle = title.filterNot(Char::isISOControl).take(MAX_TITLE_LENGTH).ifBlank { safeUrl }
        val directory = File(filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val target = File(directory, "$id.txt")
        val temporary = File(directory, "$id.txt.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.take(MAX_TEXT_LENGTH).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(target)) { "Could not commit offline page snapshot" }
        } finally {
            temporary.delete()
        }

        val previous = readMetadata()
        val savedAt = System.currentTimeMillis()
        val next = JSONArray()
        val keptIds = mutableSetOf(id)
        next.put(toJson(SavedPage(id, safeTitle, safeUrl, savedAt)))
        previous.forEach { page ->
            if (page.url != safeUrl && next.length() < MAX_SAVED_PAGES) {
                next.put(toJson(page))
                page.id?.let(keptIds::add)
            }
        }
        val metadataSaved = preferences.edit().putString(PREF_SAVED_PAGES, next.toString()).commit()
        if (!metadataSaved) {
            target.delete()
            error("Could not save offline page index")
        }

        directory.listFiles()?.forEach { file ->
            val fileId = file.name.removeSuffix(".txt")
            if (file.extension == "tmp" || (file.extension == "txt" && fileId !in keptIds)) file.delete()
        }
        return SavedPage(id, safeTitle, safeUrl, savedAt)
    }

    @Synchronized
    fun delete(page: SavedPage): Boolean {
        val previous = readMetadata()
        val remaining = previous.filterNot { saved ->
            if (page.id != null) saved.id == page.id else saved.id == null && saved.url == page.url
        }
        val metadataSaved = preferences.edit()
            .putString(PREF_SAVED_PAGES, JSONArray().apply {
                remaining.forEach { put(toJson(it)) }
            }.toString())
            .commit()
        if (metadataSaved) {
            page.id?.takeIf(ID_PATTERN::matches)?.let { id ->
                File(File(filesDir, DIRECTORY_NAME), "$id.txt").delete()
            }
        }
        return metadataSaved
    }

    @Synchronized
    fun list(): List<SavedPage> = readMetadata()

    @Synchronized
    fun search(query: String): List<SavedPage> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val pages = readMetadata()
        if (normalizedQuery.isEmpty()) return pages
        return pages.filter { page ->
            page.title.contains(normalizedQuery, ignoreCase = true) ||
                page.url.contains(normalizedQuery, ignoreCase = true) ||
                (page.id?.let(::readText)?.contains(normalizedQuery, ignoreCase = true) == true)
        }
    }

    @Synchronized
    fun hasOfflineCopy(id: String): Boolean =
        ID_PATTERN.matches(id) && File(File(filesDir, DIRECTORY_NAME), "$id.txt").isFile

    @Synchronized
    fun readText(id: String): String? {
        if (!ID_PATTERN.matches(id)) return null
        val file = File(File(filesDir, DIRECTORY_NAME), "$id.txt")
        if (!file.isFile || file.length() > MAX_TEXT_LENGTH * 4L) return null
        return runCatching { file.readText(Charsets.UTF_8).take(MAX_TEXT_LENGTH) }.getOrNull()
    }

    private fun readMetadata(): List<SavedPage> {
        val saved = preferences.getString(PREF_SAVED_PAGES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf(::isSafeWebUrl) ?: return@mapNotNull null
                SavedPage(
                    id = item.optString("id").takeIf(ID_PATTERN::matches),
                    title = item.optString("title").filterNot(Char::isISOControl).take(MAX_TITLE_LENGTH).ifBlank { url },
                    url = url,
                    savedAt = item.optLong("time")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun isSafeWebUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.length > MAX_URL_LENGTH || rawUrl.any(Char::isISOControl)) return false
        return runCatching {
            val uri = Uri.parse(rawUrl)
            uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    private fun toJson(page: SavedPage) = JSONObject().apply {
        page.id?.let { put("id", it) }
        put("title", page.title)
        put("url", page.url)
        put("time", page.savedAt)
    }

    companion object {
        private const val DIRECTORY_NAME = "offline_pages"
        const val PREF_SAVED_PAGES = "key_saved_offline_pages_v35"
        private const val MAX_SAVED_PAGES = 40
        private const val MAX_TEXT_LENGTH = 200_000
        private const val MAX_TITLE_LENGTH = 500
        private const val MAX_URL_LENGTH = 4_096
        private val ID_PATTERN = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}
