package com.adshield.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import android.os.Environment
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adshield.app.AdShieldApp
import com.adshield.app.MainActivity
import com.adshield.app.R
import com.adshield.app.model.OfflinePageStore
import com.adshield.app.util.AppUpdateManager
import com.adshield.app.databinding.ActivityMediaHubBinding
import com.adshield.app.databinding.ItemTabCardBinding
import com.adshield.app.databinding.SheetBrowserMenuBinding
import com.adshield.app.databinding.SheetExtensionsManagerBinding
import com.adshield.app.databinding.SheetHistoryBinding
import com.adshield.app.databinding.SheetTabSwitcherBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

open class MediaHubActivity : AppCompatActivity() {

    protected open val isIsolatedPrivateProfile: Boolean = false
    protected open val privateProfileConfigurationReady: Boolean = true

    enum class SearchEngine(val displayName: String, val searchUrl: String, val iconRes: Int) {
        GOOGLE("Google", "https://www.google.com/search?q=", R.drawable.ic_engine_google),
        BRAVE("Brave Search", "https://search.brave.com/search?q=", R.drawable.ic_engine_brave),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", R.drawable.ic_engine_duckduckgo),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=", R.drawable.ic_engine_startpage)
    }

    data class BrowserTab(
        val id: String = UUID.randomUUID().toString(),
        var title: String = "Fokk Browser",
        var url: String = "file:///android_asset/fokk_start.html",
        var filteredRequests: Int = 0,
        val filteredRequestHosts: MutableMap<String, Int> = mutableMapOf(),
        var thirdPartyRequests: Int = 0,
        val thirdPartyRequestHosts: MutableMap<String, Int> = mutableMapOf(),
        var isDesktop: Boolean = false,
        var shouldLoadOnSelect: Boolean = false,
        var isOfflineReader: Boolean = false,
        var isSuspended: Boolean = false,
        var suspendedForMemory: Boolean = false,
        val isPrivate: Boolean = false,
        var webView: WebView?
    )

    private data class PendingWebPermission(
        val request: PermissionRequest,
        val webView: WebView,
        val resources: Array<String>,
        val androidPermissions: List<String>,
        val origin: String,
        val topLevelOrigin: String?
    )

    private data class PendingLocationPermission(
        val callback: GeolocationPermissions.Callback,
        val origin: String,
        val webView: WebView,
        val topLevelOrigin: String?
    )

    private data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String?,
        val mimeType: String?,
        val sourceUrl: String,
        val allowThirdPartyCookies: Boolean,
        val wifiOnly: Boolean
    )

    private data class PendingRequestSnapshot(
        val filteredCount: Int,
        val filteredHosts: Map<String, Int>,
        val thirdPartyCount: Int,
        val thirdPartyHosts: Map<String, Int>
    )

    private lateinit var binding: ActivityMediaHubBinding
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int? = null
    private var previousRequestedOrientation: Int? = null
    private var previousWindowWasFullscreen: Boolean? = null
    private var browserChromeHidden = false
    private var injectionScript = ""
    private val tabs = mutableListOf<BrowserTab>()
    private val pageUrlsByWebView = Collections.synchronizedMap(WeakHashMap<WebView, String>())
    private val pendingFilteredCounts = WeakHashMap<WebView, Int>()
    private val pendingFilteredHosts = WeakHashMap<WebView, MutableMap<String, Int>>()
    private val pendingThirdPartyCounts = WeakHashMap<WebView, Int>()
    private val pendingThirdPartyHosts = WeakHashMap<WebView, MutableMap<String, Int>>()
    private val approvedWebMediaOrigins = mutableSetOf<String>()
    private val approvedLocationOrigins = mutableSetOf<String>()
    private val filteredCountLock = Any()
    private val pendingFilteredCountFlush = AtomicBoolean(false)
    private val privateFilteredRequestTotal = AtomicLong(0L)
    private var pendingFilteredCountTotal = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val filteredCountFlushRunnable = Runnable { flushFilteredRequestCounts() }
    private var activeTabIndex = 0
    private var savedPagesSearchJob: Job? = null
    private var privateProfileReady = false
    private var privateProfileCloseCleanupStarted = false
    private var privateProfileDataClearedOnClose = false
    private val pendingPrivateUrls = mutableListOf<String>()
    private var isShieldEnabled = true
    private var isDarkMode = false
    private var showingPrivateTabs = false
    private var isMediaPlaying = false
    private var isBiometricAuthenticatedForSession = false

    private var lastMediaPlayingTime = 0L

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PendingWebPermission? = null
    private var pendingLocationPermission: PendingLocationPermission? = null
    private var pendingDownload: PendingDownload? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val download = pendingDownload
        pendingDownload = null
        if (granted && download != null) {
            enqueueDownload(
                download.url,
                download.userAgent,
                download.contentDisposition,
                download.mimeType,
                download.sourceUrl,
                download.allowThirdPartyCookies,
                download.wifiOnly
            )
        } else if (download != null) {
            Toast.makeText(this, "Storage access is needed to save downloads on this Android version", Toast.LENGTH_LONG).show()
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val dataUri = result.data?.data
            val clipData = result.data?.clipData
            if (clipData != null && clipData.itemCount > 0) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else if (dataUri != null) {
                arrayOf(dataUri)
            } else {
                null
            }
        } else {
            null
        }
        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
    }

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenMatches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrEmpty()) {
                binding.urlInput.setText(spokenText)
                binding.cardSuggestions.visibility = View.GONE
                val rawTarget = if (spokenText.contains(".") && !spokenText.contains(" ")) {
                    "https://$spokenText"
                } else {
                    currentSearchEngine.searchUrl + Uri.encode(spokenText)
                }
                loadUrlInActiveWebView(rawTarget)
                recordHistory(spokenText, rawTarget)
            }
        }
    }

    private val webPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingWebPermission
        pendingWebPermission = null
        if (pending == null) return@registerForActivityResult

        val stillOpen = tabs.any { it.webView === pending.webView }
        val currentOrigin = pending.webView.url?.let(::getWebOrigin)
        val permissionsGranted = pending.androidPermissions.isNotEmpty() &&
            pending.androidPermissions.all {
                val wasGranted = grants[it] ?: (ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                wasGranted && ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        if (stillOpen && currentOrigin == pending.topLevelOrigin && permissionsGranted) {
            approvedWebMediaOrigins.add(sitePermissionKey(pending.origin, pending.topLevelOrigin))
            pending.request.grant(pending.resources)
        } else {
            pending.request.deny()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingLocationPermission
        pendingLocationPermission = null
        if (pending == null) return@registerForActivityResult

        val stillOpen = tabs.any { it.webView === pending.webView }
        val currentOrigin = pending.webView.url?.let(::getWebOrigin)
        val hasLocation = listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ).any { permission ->
            (grants[permission] == true || ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
                ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val allowed = stillOpen && pending.webView === activeWebView &&
            currentOrigin == pending.topLevelOrigin && hasLocation
        if (allowed) approvedLocationOrigins.add(sitePermissionKey(pending.origin, pending.topLevelOrigin))
        pending.callback.invoke(pending.origin, allowed, false)
    }

    private fun startVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
            }
            speechRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    data class HistorySuggestion(val title: String, val url: String, val isHistory: Boolean = true)

    private fun getRecentHistory(): List<HistorySuggestion> {
        val list = mutableListOf<HistorySuggestion>()
        try {
            val historyJson = localPrefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
            val jsonArr = JSONArray(historyJson)
            for (i in 0 until minOf(jsonArr.length(), 10)) {
                val item = jsonArr.getJSONObject(i)
                val title = item.optString("title")
                val url = item.optString("url")
                if (url.isNotBlank()) {
                    list.add(HistorySuggestion(title = title.ifBlank { url }, url = url, isHistory = true))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getRecentHistory error", e)
        }
        return list
    }

    private val wallpaperPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val dataUri = "data:$mime;base64,$base64"
                    activeWebView?.post {
                        activeWebView?.evaluateJavascript("if (typeof applyCustomWallpaper === 'function') applyCustomWallpaper('$dataUri');", null)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openWallpaperGalleryPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        wallpaperPickerLauncher.launch(Intent.createChooser(intent, "Choose Wallpaper Image"))
    }

    fun openWallpapersCustomizer() {
        val currentUrl = activeTab?.url ?: ""
        if (currentUrl.contains("fokk_start.html")) {
            activeWebView?.evaluateJavascript("if (typeof openThemeModal === 'function') openThemeModal();", null)
        } else {
            addNewTab("file:///android_asset/fokk_start.html?action=wallpapers", isPrivate = activeTab?.isPrivate == true)
        }
    }

    fun openShortcutsCustomizer() {
        val currentUrl = activeTab?.url ?: ""
        if (currentUrl.contains("fokk_start.html")) {
            activeWebView?.evaluateJavascript("if (typeof openShortcutsManager === 'function') openShortcutsManager();", null)
        } else {
            addNewTab("file:///android_asset/fokk_start.html?action=shortcuts", isPrivate = activeTab?.isPrivate == true)
        }
    }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val localPrefs: SharedPreferences by lazy {
        getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val offlinePageStore: OfflinePageStore by lazy {
        OfflinePageStore(filesDir, localPrefs)
    }

    private fun migrateLegacyBrowserPreferences() {
        val legacy = getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val oldValues = legacy.all
        if (oldValues.isEmpty()) return

        val settingsTarget = prefs
        val localTarget = localPrefs
        val settingsEditor = settingsTarget.edit()
        val localEditor = localTarget.edit()
        oldValues.forEach { (key, value) ->
            val isLocalBrowserData = key in LOCAL_BROWSER_DATA_KEYS
            val target = if (isLocalBrowserData) localTarget else settingsTarget
            if (target.contains(key)) return@forEach
            val editor = if (isLocalBrowserData) localEditor else settingsEditor
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }

        if (!settingsEditor.commit() || !localEditor.commit()) {
            Log.e(TAG, "Could not migrate all legacy browser preferences; keeping the old file for retry")
            return
        }
        if (!legacy.edit().clear().commit()) {
            Log.e(TAG, "Migrated browser preferences but could not remove the legacy preference file")
        }
    }

    private var currentSearchEngine: SearchEngine = SearchEngine.GOOGLE

    private fun applySearchEngine(engine: SearchEngine) {
        currentSearchEngine = engine
        prefs.edit().putString(PREF_SEARCH_ENGINE, engine.name).apply()
        binding.imgEngineIcon.setImageResource(engine.iconRes)
        if (binding.urlInput.text.isEmpty() || activeWebView?.url?.contains("fokk_start.html") == true) {
            binding.urlInput.hint = "Search with ${engine.displayName} or enter address"
        }
    }
    private var suggestionJob: Job? = null

    private val activeTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex)

    private val activeWebView: WebView?
        get() = activeTab?.webView

    private val cleanMobileUa: String by lazy {
        val defaultUa = WebSettings.getDefaultUserAgent(this)
        defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
    }

    private val desktopUa: String by lazy {
        cleanMobileUa
            .replace(Regex("\\(Linux; Android [^)]*\\)"), "(Windows NT 10.0; Win64; x64)")
            .replace("Mobile Safari/", "Safari/")
    }

    private fun clearPrivateProfileData(onComplete: (() -> Unit)? = null) {
        val clearRemainingData = {
            runCatching { WebStorage.getInstance().deleteAllData() }
            runCatching { WebView(this).apply { clearCache(true); clearSslPreferences(); destroy() } }
            runCatching {
                WebViewDatabase.getInstance(this).apply {
                    clearFormData()
                    clearHttpAuthUsernamePassword()
                    clearUsernamePassword()
                }
            }
            runCatching { CookieManager.getInstance().flush() }
            onComplete?.invoke()
        }
        runCatching {
            CookieManager.getInstance().removeAllCookies {
                runOnUiThread { clearRemainingData() }
            }
        }.onFailure {
            runOnUiThread { clearRemainingData() }
        }
    }

    private fun closePrivateBrowser() {
        if (privateProfileCloseCleanupStarted || isFinishing) return
        privateProfileCloseCleanupStarted = true
        binding.root.isEnabled = false

        pendingWebPermission?.let { pending ->
            pendingWebPermission = null
            runCatching { pending.request.deny() }
        }
        pendingLocationPermission?.let { pending ->
            pendingLocationPermission = null
            runCatching { pending.callback.invoke(pending.origin, false, false) }
        }
        tabs.forEach { tab ->
            tab.webView?.let { webView ->
                cancelPendingWebPermission(webView)
                cancelPendingLocationPermission(webView)
                binding.tabContainer.removeView(webView)
                pageUrlsByWebView.remove(webView)
                runCatching { webView.stopLoading() }
                runCatching { webView.destroy() }
            }
        }
        tabs.clear()
        mainHandler.removeCallbacks(filteredCountFlushRunnable)

        clearPrivateProfileData {
            privateProfileDataClearedOnClose = true
            if (!isDestroyed) finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isIsolatedPrivateProfile && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Private tabs with isolated site data require Android 9 or newer", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (isIsolatedPrivateProfile && !privateProfileConfigurationReady) {
            Toast.makeText(this, "Could not create an isolated private browser profile", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!isIsolatedPrivateProfile) migrateLegacyBrowserPreferences()
        binding = ActivityMediaHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedEngineName = prefs.getString(PREF_SEARCH_ENGINE, SearchEngine.GOOGLE.name)
        currentSearchEngine = try {
            SearchEngine.valueOf(savedEngineName ?: SearchEngine.GOOGLE.name)
        } catch (e: Exception) {
            SearchEngine.GOOGLE
        }
        applySearchEngine(currentSearchEngine)

        loadInjectionScript()
        setupControls()
        setupRefreshControl()

        val initialUrl = incomingWebUrl(intent)
        if (isIsolatedPrivateProfile) {
            showingPrivateTabs = true
            clearPrivateProfileData {
                if (!isFinishing) {
                    privateProfileReady = true
                    if (tabs.isEmpty()) {
                        addNewTab(initialUrl?.takeIf(::isWebUrl) ?: "file:///android_asset/fokk_start.html", isPrivate = true)
                    }
                    pendingPrivateUrls.toList().forEach { addNewTab(it, isPrivate = true) }
                    pendingPrivateUrls.clear()
                }
            }
        } else if (initialUrl.isNullOrBlank()) {
            restoreLastSession()
        } else {
            addNewTab(initialUrl, isPrivate = false)
        }
        handleOpenAction(intent)

        if (!isIsolatedPrivateProfile) AppUpdateManager.checkForUpdates(this, manualCheck = false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenAction(intent)
        val incomingUrl = incomingWebUrl(intent)
        if (isIsolatedPrivateProfile) {
            if (incomingUrl != null) addNewTab(incomingUrl, isPrivate = true)
        } else if (incomingUrl != null) {
            addNewTab(incomingUrl, isPrivate = false)
        }
    }

    private fun incomingWebUrl(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra(EXTRA_URL)?.takeIf(::isWebUrl)?.let { return it }
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf(::isWebUrl)
            Intent.ACTION_SEND -> {
                if (intent.type != "text/plain") return null
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().trim()
                sharedText.takeIf(::isWebUrl) ?: run {
                    val matcher = Patterns.WEB_URL.matcher(sharedText)
                    if (!matcher.find()) return null
                    val candidate = matcher.group().trimEnd('.', ',', ';', ')', ']', '}')
                    val webUrl = if (Uri.parse(candidate).scheme.isNullOrBlank()) "https://$candidate" else candidate
                    webUrl.takeIf(::isWebUrl)
                }
            }
            else -> null
        }
    }

    private fun handleOpenAction(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN_ACTION)) {
            "wallpapers" -> openWallpapersCustomizer()
            "shortcuts" -> openShortcutsCustomizer()
        }
    }

    private fun setupRefreshControl() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.fokk_cyan)
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            activeWebView?.canScrollVertically(-1) == true
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            val webView = activeWebView
            if (webView == null) {
                binding.swipeRefreshLayout.isRefreshing = false
            } else {
                webView.reload()
            }
        }
    }

    private fun loadInjectionScript() {
        try {
            assets.open("inject_adshield.js").use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    injectionScript = reader.readText()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load injection script", e)
        }
    }

    private fun recordFilteredRequest(webView: WebView?, host: String) {
        if (isIsolatedPrivateProfile) privateFilteredRequestTotal.incrementAndGet()
        synchronized(filteredCountLock) {
            pendingFilteredCountTotal += 1
            if (webView != null) {
                pendingFilteredCounts[webView] = (pendingFilteredCounts[webView] ?: 0) + 1
                val hostCounts = pendingFilteredHosts.getOrPut(webView) { mutableMapOf() }
                val normalizedHost = host.lowercase(Locale.ROOT)
                if (normalizedHost in hostCounts || hostCounts.size < MAX_REPORTED_HOSTS) {
                    hostCounts[normalizedHost] = (hostCounts[normalizedHost] ?: 0) + 1
                }
            }
        }
        scheduleFilteredCountFlush()
    }

    private fun recordThirdPartyRequest(webView: WebView?, host: String) {
        if (webView == null) return
        synchronized(filteredCountLock) {
            pendingThirdPartyCounts[webView] = (pendingThirdPartyCounts[webView] ?: 0) + 1
            val hostCounts = pendingThirdPartyHosts.getOrPut(webView) { mutableMapOf() }
            val normalizedHost = host.lowercase(Locale.ROOT)
            if (normalizedHost in hostCounts || hostCounts.size < MAX_REPORTED_HOSTS) {
                hostCounts[normalizedHost] = (hostCounts[normalizedHost] ?: 0) + 1
            }
        }
        scheduleFilteredCountFlush()
    }

    private fun scheduleFilteredCountFlush() {
        if (pendingFilteredCountFlush.compareAndSet(false, true)) {
            mainHandler.postDelayed(filteredCountFlushRunnable, 200L)
        }
    }

    private fun flushFilteredRequestCounts() {
        val snapshot: Map<WebView, PendingRequestSnapshot>
        val total: Int
        synchronized(filteredCountLock) {
            val webViews = (pendingFilteredCounts.keys + pendingThirdPartyCounts.keys).toSet()
            snapshot = webViews.associateWith { webView ->
                PendingRequestSnapshot(
                    filteredCount = pendingFilteredCounts[webView] ?: 0,
                    filteredHosts = pendingFilteredHosts[webView]?.toMap().orEmpty(),
                    thirdPartyCount = pendingThirdPartyCounts[webView] ?: 0,
                    thirdPartyHosts = pendingThirdPartyHosts[webView]?.toMap().orEmpty()
                )
            }
            total = pendingFilteredCountTotal
            pendingFilteredCounts.clear()
            pendingFilteredHosts.clear()
            pendingThirdPartyCounts.clear()
            pendingThirdPartyHosts.clear()
            pendingFilteredCountTotal = 0
        }

        snapshot.forEach { (webView, pending) ->
            tabs.firstOrNull { it.webView === webView }?.let { tab ->
                tab.filteredRequests += pending.filteredCount
                tab.thirdPartyRequests += pending.thirdPartyCount
                pending.filteredHosts.forEach { (host, count) ->
                    if (host in tab.filteredRequestHosts || tab.filteredRequestHosts.size < MAX_REPORTED_HOSTS) {
                        tab.filteredRequestHosts[host] = (tab.filteredRequestHosts[host] ?: 0) + count
                    }
                }
                pending.thirdPartyHosts.forEach { (host, count) ->
                    if (host in tab.thirdPartyRequestHosts || tab.thirdPartyRequestHosts.size < MAX_REPORTED_HOSTS) {
                        tab.thirdPartyRequestHosts[host] = (tab.thirdPartyRequestHosts[host] ?: 0) + count
                    }
                }
            }
        }
        if (total > 0 && !isIsolatedPrivateProfile) {
            AdShieldApp.instance.incrementBlockedAds(total)
        }

        pendingFilteredCountFlush.set(false)
        val hasMorePending = synchronized(filteredCountLock) {
            pendingFilteredCountTotal > 0 || pendingThirdPartyCounts.isNotEmpty()
        }
        if (hasMorePending) scheduleFilteredCountFlush()
    }

    class WebAppInterface(private val activity: MediaHubActivity) {
        @JavascriptInterface
        fun getStats(): String {
            val total = if (activity.isIsolatedPrivateProfile) {
                activity.privateFilteredRequestTotal.get()
            } else {
                AdShieldApp.instance.getBlockedAdsCount()
            }
            return """{"blocked":$total}"""
        }

        @JavascriptInterface
        fun setSearchEngine(engineName: String) {
            activity.runOnUiThread {
                val found = SearchEngine.values().find {
                    it.name.equals(engineName, ignoreCase = true) || it.displayName.equals(engineName, ignoreCase = true)
                }
                if (found != null) {
                    activity.currentSearchEngine = found
                    activity.prefs.edit().putString(PREF_SEARCH_ENGINE, found.name).apply()
                    activity.binding.urlInput.hint = "Search with ${found.displayName} or enter URL..."
                }
            }
        }

        @JavascriptInterface
        fun pickWallpaperImage() {
            activity.runOnUiThread {
                activity.openWallpaperGalleryPicker()
            }
        }

        @JavascriptInterface
        fun onMediaPlaybackChanged(isPlaying: Boolean) {
            onMediaPlaybackChanged(isPlaying, isPlaying)
        }

        @JavascriptInterface
        fun onMediaPlaybackChanged(isPlaying: Boolean, hasActiveVideo: Boolean) {
            activity.runOnUiThread {
                activity.isMediaPlaying = isPlaying
                if (isPlaying || hasActiveVideo) {
                    activity.lastMediaPlayingTime = System.currentTimeMillis()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val isStartPage = activity.activeTab?.url?.contains("fokk_start.html") == true
                    val canAutoPip = (isPlaying || hasActiveVideo) && !isStartPage
                    try {
                        val builder = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .setAutoEnterEnabled(canAutoPip)
                            .setSeamlessResizeEnabled(true)
                        activity.setPictureInPictureParams(builder.build())
                    } catch (e: Exception) {}
                }
            }
        }

        @JavascriptInterface
        fun getSearchEngine(): String {
            return activity.currentSearchEngine.name
        }
    }

    /** Minimal, read-only playback bridge exposed to pages so native PiP starts only for media. */
    class MediaPlaybackBridge(private val activity: MediaHubActivity) {
        @JavascriptInterface
        fun onMediaPlaybackChanged(isPlaying: Boolean, hasActiveVideo: Boolean) {
            activity.runOnUiThread {
                activity.isMediaPlaying = isPlaying
                if (isPlaying || hasActiveVideo) {
                    activity.lastMediaPlayingTime = System.currentTimeMillis()
                }
            }
        }
    }

    private fun canUseBiometric(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticateUser(title: String, subtitle: String, onSuccess: () -> Unit, onFail: (() -> Unit)? = null) {
        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onFail?.invoke()
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this@MediaHubActivity, "Biometric: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFail?.invoke()
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onSuccess()
        }
    }

    private fun authenticateForPrivateTabs(onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Private tabs with isolated site data require Android 9 or newer", Toast.LENGTH_LONG).show()
            return
        }
        val isLockEnabled = prefs.getBoolean(PREF_BIOMETRIC_PRIVATE_TABS, false)
        if (!isLockEnabled || isBiometricAuthenticatedForSession) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Private Browsing")
            .setSubtitle("Confirm your fingerprint or device PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isBiometricAuthenticatedForSession = true
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this@MediaHubActivity, "Biometric: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onSuccess()
        }
    }

    private fun recordHistory(title: String, url: String, isPrivate: Boolean = activeTab?.isPrivate == true) {
        if (isPrivate) return
        if (url.startsWith("file://") || url.isBlank()) return

        try {
            val historyJson = localPrefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
            val jsonArr = JSONArray(historyJson)
            val newArr = JSONArray()
            val newItem = JSONObject().apply {
                put("title", title.ifEmpty { url })
                put("url", url)
                put("time", System.currentTimeMillis())
            }
            newArr.put(newItem)
            for (i in 0 until minOf(jsonArr.length(), 80)) {
                val prev = jsonArr.getJSONObject(i)
                if (prev.optString("url") != url) {
                    newArr.put(prev)
                }
            }
            localPrefs.edit().putString(PREF_BROWSING_HISTORY, newArr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving history", e)
        }
    }

    private fun applyDesktopMode(webView: WebView, isDesktop: Boolean) {
        webView.settings.apply {
            userAgentString = if (isDesktop) desktopUa else cleanMobileUa
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
    }

    private fun handleBrowserNavigation(webView: WebView?, rawUrl: String, userGesture: Boolean): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> {
                val cleanedUrl = stripTrackingParameters(rawUrl)
                if (cleanedUrl != rawUrl && webView != null) {
                    loadUrlInWebView(webView, cleanedUrl)
                    return true
                }
                webView?.let {
                    pageUrlsByWebView[it] = rawUrl
                    setBridgeEnabled(it, rawUrl)
                }
                return false
            }
            "intent" -> {
                val parsedIntent = runCatching { Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME) }.getOrNull()
                if (parsedIntent == null) {
                    Toast.makeText(this, "Blocked an invalid app link", Toast.LENGTH_SHORT).show()
                    return true
                }
                val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
                if (fallbackUrl != null && isWebUrl(fallbackUrl)) {
                    loadUrlInWebView(webView, fallbackUrl)
                    return true
                }
                if (!userGesture) {
                    Toast.makeText(this, "Blocked a page that tried to open another app automatically", Toast.LENGTH_LONG).show()
                    return true
                }
                confirmExternalAppLaunch(uri, parsedIntent)
                return true
            }
            else -> {
                if (!userGesture) {
                    Toast.makeText(this, "Blocked a page that tried to open another app automatically", Toast.LENGTH_LONG).show()
                    return true
                }
                confirmExternalAppLaunch(uri, null)
                return true
            }
        }
    }

    private fun getWebOrigin(rawUrl: String?): String? = runCatching {
        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank() || (scheme != "https" && !(scheme == "http" && host in setOf("localhost", "127.0.0.1", "::1")))) {
            null
        } else {
            val includePort = uri.port != -1 &&
                !((scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80))
            val authorityHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
            val port = if (includePort) ":${uri.port}" else ""
            "$scheme://$authorityHost$port"
        }
    }.getOrNull()

    private fun getHttpStorageOrigins(rawUrl: String): Set<String> = runCatching {
        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
        if (scheme !in setOf("http", "https") || host == null) return@runCatching emptySet()

        val sourceDefaultPort = if (scheme == "https") 443 else 80
        val explicitNonDefaultPort = uri.port.takeIf { it != -1 && it != sourceDefaultPort }
        val authorityHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        setOf("http", "https").mapTo(linkedSetOf()) { targetScheme ->
            val targetDefaultPort = if (targetScheme == "https") 443 else 80
            val port = explicitNonDefaultPort?.takeUnless { it == targetDefaultPort }?.let { ":$it" }.orEmpty()
            "$targetScheme://$authorityHost$port"
        }
    }.getOrDefault(emptySet())

    private fun handleWebPermissionRequest(webView: WebView, request: PermissionRequest) {
        val requested = request.resources.distinct().toTypedArray()
        val permissions = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requested) add(android.Manifest.permission.CAMERA)
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requested) add(android.Manifest.permission.RECORD_AUDIO)
        }
        val origin = request.origin.toString().trimEnd('/')
        val currentOrigin = getWebOrigin(webView.url)
        val supportedResources = requested.all {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }

        if (requested.isEmpty() || permissions.isEmpty() || !supportedResources ||
            getWebOrigin(origin) != origin || currentOrigin == null || webView !== activeWebView ||
            pendingWebPermission != null || pendingLocationPermission != null) {
            request.deny()
            return
        }

        if (sitePermissionKey(origin, currentOrigin) in approvedWebMediaOrigins && permissions.all {
                ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }) {
            request.grant(requested)
            return
        }

        val requestedCapabilities = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requested) add("camera")
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requested) add("microphone")
        }.joinToString(" and ")

        MaterialAlertDialogBuilder(this)
            .setTitle("Allow $requestedCapabilities?")
            .setMessage("$origin is asking to use your $requestedCapabilities. Approve here; Android may also ask for system permission.")
            .setNegativeButton("Deny") { _, _ -> request.deny() }
            .setPositiveButton("Continue") { _, _ ->
                val pending = PendingWebPermission(request, webView, requested, permissions, origin, currentOrigin)
                pendingWebPermission = pending
                val missing = permissions.filter {
                    ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    pendingWebPermission = null
                    if (webView.url?.let(::getWebOrigin) == currentOrigin && tabs.any { it.webView === webView }) {
                        approvedWebMediaOrigins.add(sitePermissionKey(origin, currentOrigin))
                        request.grant(requested)
                    } else request.deny()
                } else {
                    webPermissionLauncher.launch(missing.toTypedArray())
                }
            }
            .setOnCancelListener { request.deny() }
            .show()
    }

    private fun cancelPendingWebPermission(webView: WebView) {
        val pending = pendingWebPermission ?: return
        if (pending.webView === webView) {
            pendingWebPermission = null
            pending.request.deny()
        }
    }

    private fun sitePermissionKey(requestingOrigin: String, topLevelOrigin: String?): String =
        "${topLevelOrigin.orEmpty()}|$requestingOrigin"

    private fun handleGeolocationPrompt(
        webView: WebView,
        rawOrigin: String?,
        callback: GeolocationPermissions.Callback
    ) {
        val origin = rawOrigin?.trimEnd('/') ?: run {
            callback.invoke(rawOrigin.orEmpty(), false, false)
            return
        }
        val currentOrigin = getWebOrigin(webView.url)
        if (getWebOrigin(origin) != origin || currentOrigin == null || webView !== activeWebView ||
            pendingLocationPermission != null || pendingWebPermission != null) {
            callback.invoke(origin, false, false)
            return
        }

        val locationPermissions = listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasLocation = locationPermissions.any {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (sitePermissionKey(origin, currentOrigin) in approvedLocationOrigins && hasLocation) {
            callback.invoke(origin, true, false)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Allow location access?")
            .setMessage("$origin is asking for your device location. You can choose approximate or precise access in Android's system prompt.")
            .setNegativeButton("Deny") { _, _ -> callback.invoke(origin, false, false) }
            .setPositiveButton("Continue") { _, _ ->
                val pending = PendingLocationPermission(callback, origin, webView, currentOrigin)
                pendingLocationPermission = pending
                val missing = locationPermissions.filter {
                    ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    pendingLocationPermission = null
                    if (webView.url?.let(::getWebOrigin) == currentOrigin && webView === activeWebView) {
                        approvedLocationOrigins.add(sitePermissionKey(origin, currentOrigin))
                        callback.invoke(origin, true, false)
                    } else callback.invoke(origin, false, false)
                } else {
                    locationPermissionLauncher.launch(missing.toTypedArray())
                }
            }
            .setOnCancelListener { callback.invoke(origin, false, false) }
            .show()
    }

    private fun cancelPendingLocationPermission(webView: WebView) {
        val pending = pendingLocationPermission ?: return
        if (pending.webView === webView) {
            pendingLocationPermission = null
            pending.callback.invoke(pending.origin, false, false)
        }
    }

    private fun loadUrlInWebView(webView: WebView?, url: String) {
        if (webView == null) return
        val targetUrl = stripTrackingParameters(url)
        tabs.firstOrNull { it.webView === webView }?.isOfflineReader = false
        val tabIsPrivate = tabs.firstOrNull { it.webView === webView }?.isPrivate == true
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            webView,
            !tabIsPrivate && isThirdPartyMediaService(targetUrl)
        )
        pageUrlsByWebView[webView] = targetUrl
        setBridgeEnabled(webView, targetUrl)
        webView.loadUrl(targetUrl)
    }

    private fun stripTrackingParameters(rawUrl: String): String {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return rawUrl
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") ||
            uri.host.isNullOrBlank() || uri.encodedQuery.isNullOrEmpty()) return rawUrl
        val parameterNames = uri.queryParameterNames.map { it.lowercase(Locale.ROOT) }.toSet()
        if (parameterNames.any(::isAuthenticationOrSignatureParameter)) return rawUrl
        val builder = uri.buildUpon().clearQuery()
        var removedAny = false
        uri.queryParameterNames.forEach { name ->
            val normalized = name.lowercase(Locale.ROOT)
            val isTrackingParameter = normalized.startsWith("utm_") || normalized in TRACKING_QUERY_PARAMETERS
            if (isTrackingParameter) {
                removedAny = true
            } else {
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
        }
        return if (removedAny) builder.build().toString() else rawUrl
    }

    private fun isAuthenticationOrSignatureParameter(name: String): Boolean =
        name in AUTHENTICATION_OR_SIGNATURE_PARAMETERS ||
            AUTHENTICATION_OR_SIGNATURE_PREFIXES.any { name.startsWith(it) } ||
            name.endsWith("_signature") || name.endsWith("_token")

    private fun loadUrlInActiveWebView(url: String) = loadUrlInWebView(activeWebView, url)

    private fun isWebUrl(rawUrl: String): Boolean = runCatching {
        val uri = Uri.parse(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun confirmExternalAppLaunch(uri: Uri, parsedIntent: Intent?) {
        val destination = uri.host ?: uri.scheme ?: "another app"
        MaterialAlertDialogBuilder(this)
            .setTitle("Open another app?")
            .setMessage("This page wants to open $destination outside Fokk Browser. Continue only if you expected this.")
            .setNegativeButton("Stay in browser", null)
            .setPositiveButton("Open") { _, _ ->
                try {
                    val externalIntent = parsedIntent ?: Intent(Intent.ACTION_VIEW, uri)
                    externalIntent.component = null
                    externalIntent.selector = null
                    externalIntent.setPackage(null)
                    externalIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    externalIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                    startActivity(Intent.createChooser(externalIntent, "Open with"))
                } catch (_: Exception) {
                    Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun hideCustomView() {
        val activeCustomView = customView ?: return
        binding.fullscreenContainer.removeView(activeCustomView)
        binding.fullscreenContainer.visibility = View.GONE
        binding.mainContentLayout.visibility = View.VISIBLE
        setBrowserChromeVisible(true)

        val decor = window.decorView
        previousSystemUiVisibility?.let { decor.systemUiVisibility = it }
        if (previousWindowWasFullscreen == false) {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        previousRequestedOrientation?.let { requestedOrientation = it }

        val callback = customViewCallback
        customView = null
        customViewCallback = null
        previousSystemUiVisibility = null
        previousRequestedOrientation = null
        previousWindowWasFullscreen = null
        callback?.onCustomViewHidden()
    }

    private fun setBrowserChromeVisible(visible: Boolean) {
        if (visible) {
            val wasHidden = browserChromeHidden
            browserChromeHidden = false
            binding.topBar.animate().cancel()
            if (wasHidden) {
                binding.topBar.visibility = View.VISIBLE
                binding.topBar.alpha = 0f
                binding.topBar.translationY = binding.topBar.height.toFloat()
                binding.topBar.animate().alpha(1f).translationY(0f).setDuration(160).start()
            } else {
                binding.topBar.visibility = View.VISIBLE
                binding.topBar.alpha = 1f
                binding.topBar.translationY = 0f
            }
        } else {
            if (browserChromeHidden) return
            browserChromeHidden = true
            binding.topBar.animate().cancel()
            binding.topBar.animate()
                .alpha(0f)
                .translationY(binding.topBar.height.toFloat())
                .setDuration(160)
                .withEndAction {
                    if (browserChromeHidden) binding.topBar.visibility = View.GONE
                }
                .start()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url: String, isPrivate: Boolean = false): BrowserTab {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // First-party cookies stay available. Third-party cookies are limited to supported service pages.
        cookieManager.setAcceptThirdPartyCookies(webView, !isPrivate && isThirdPartyMediaService(url))

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            // Do not let an HTTPS page silently load active content over HTTP.
            // This blocks downgrade/injection paths; a site with legacy HTTP embeds may need its own fix.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            userAgentString = cleanMobileUa
        }

        // Intercept downloads cleanly
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            val confirmPrivateDownload = tabs.firstOrNull { it.webView === webView }?.isPrivate == true
            showDownloadOptions(
                downloadUrl,
                userAgent,
                contentDisposition,
                mimetype,
                isPrivate = confirmPrivateDownload,
                sourceUrl = webView.url.orEmpty(),
                allowThirdPartyCookies = cookieManager.acceptThirdPartyCookies(webView)
            )
        }

        // Content filters are applied after navigation and only on supported, non-platform sites.

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqUrl = request?.url?.toString() ?: return false
                return handleBrowserNavigation(view, reqUrl, request?.hasGesture() == true)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                return handleBrowserNavigation(view, url, false)
            }

            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, pageUrl, favicon)
                val loadingTab = tabs.firstOrNull { it.webView === view }
                if (loadingTab?.isOfflineReader == true && pageUrl?.startsWith(OFFLINE_PAGE_BASE_URL) != true) {
                    loadingTab.isOfflineReader = false
                }
                view?.let {
                    cancelPendingWebPermission(it)
                    cancelPendingLocationPermission(it)
                }
                if (view != null && pageUrl != null) pageUrlsByWebView[view] = pageUrl
                if (view != null) {
                    // Do not attach delayed callbacks from the previous document to this page.
                    synchronized(filteredCountLock) {
                        pendingFilteredCounts.remove(view)
                        pendingFilteredHosts.remove(view)
                        pendingThirdPartyCounts.remove(view)
                        pendingThirdPartyHosts.remove(view)
                    }
                }
                if (view != null) {
                    val tabIsPrivate = tabs.firstOrNull { it.webView === view }?.isPrivate == true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(
                        view,
                        !tabIsPrivate && isThirdPartyMediaService(pageUrl)
                    )
                }
                setBridgeEnabled(view, pageUrl)
                loadingTab?.filteredRequests = 0
                loadingTab?.filteredRequestHosts?.clear()
                loadingTab?.thirdPartyRequests = 0
                loadingTab?.thirdPartyRequestHosts?.clear()
                if (view == activeWebView) {
                    setBrowserChromeVisible(true)
                    isShieldEnabled = isShieldEnabledFor(pageUrl)
                    binding.progressBar.visibility = View.VISIBLE
                    // page reset
                    if (pageUrl != null && !binding.urlInput.hasFocus()) {
                        val displayUrl = if (loadingTab?.isOfflineReader == true) loadingTab.url else pageUrl
                        updateUrlBar(displayUrl, loadingTab?.title)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && view == activeWebView) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.w(TAG, "Navigation failed for ${request.url}: ${error?.description}")
                    Toast.makeText(
                        this@MediaHubActivity,
                        "This page could not load. Check your connection or try turning off the site shield.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                if (view == null) return true
                val tabIndex = tabs.indexOfFirst { it.webView === view }
                if (tabIndex < 0) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                    return true
                }

                val oldTab = tabs[tabIndex]
                val crashedUrl = view.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: oldTab.url
                val wasActive = tabIndex == activeTabIndex
                if (wasActive && customView != null) hideCustomView()
                binding.tabContainer.removeView(view)
                pageUrlsByWebView.remove(view)
                view.destroy()

                val replacement = createTab(crashedUrl, oldTab.isPrivate).apply {
                    title = oldTab.title
                    filteredRequests = oldTab.filteredRequests
                    filteredRequestHosts.putAll(oldTab.filteredRequestHosts)
                    thirdPartyRequests = oldTab.thirdPartyRequests
                    thirdPartyRequestHosts.putAll(oldTab.thirdPartyRequestHosts)
                    isDesktop = oldTab.isDesktop
                    shouldLoadOnSelect = wasActive
                }
                tabs[tabIndex] = replacement
                val replacementWebView = replacement.webView ?: return true
                applyDesktopMode(replacementWebView, replacement.isDesktop)
                if (wasActive) switchToTab(tabIndex) else {
                    replacementWebView.visibility = View.GONE
                    loadUrlInWebView(replacementWebView, crashedUrl)
                }
                if (wasActive) {
                    Toast.makeText(
                        this@MediaHubActivity,
                        "A page process stopped. Fokk restored this tab; reload if it remains blank.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return true
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                if (view != null && pageUrl != null) pageUrlsByWebView[view] = pageUrl
                if (view == activeWebView) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                }
                val loadedTab = tabs.firstOrNull { it.webView == view }
                if (loadedTab?.isOfflineReader != true) {
                    injectAdblockScript(view, pageUrl)
                    if (isDarkMode && !isThirdPartyMediaService(pageUrl)) injectDarkModeScript(view, true)
                }
                pageUrl?.let { loadedUrl ->
                    if (loadedTab?.isOfflineReader != true) {
                        loadedTab?.url = loadedUrl
                        if (loadedUrl.contains("fokk_start.html")) {
                            loadedTab?.title = "New Tab"
                        } else if (loadedTab != null) {
                            recordHistory(view?.title ?: loadedUrl, loadedUrl, loadedTab.isPrivate)
                        }
                    } else if (view == activeWebView) {
                        loadedTab?.let { updateUrlBar(it.url, it.title) }
                    }
                    if (view == activeWebView && loadedTab?.isOfflineReader != true) updateUrlBar(loadedUrl, view?.title)
                }
                view?.title?.let {
                    if (it.isNotEmpty() && loadedTab?.url?.contains("fokk_start.html") != true) {
                        loadedTab?.title = it
                    }
                }

                // If tab is in desktop mode, inject desktop viewport meta override
                if (loadedTab?.isDesktop == true && !isThirdPartyMediaService(pageUrl)) {
                    val js = "javascript:(function(){let vp=document.querySelector('meta[name=\"viewport\"]');if(vp){vp.setAttribute('content','width=1280, initial-scale=0.85');}})();"
                    view?.evaluateJavascript(js, null)
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                // Never replace a requested document with a synthetic empty response.
                if (request?.isForMainFrame == true) return super.shouldInterceptRequest(view, request)

                val pageUrl = view?.let { pageUrlsByWebView[it] }
                val requestUrl = request?.url?.toString()
                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                val pageHost = pageUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                val isCrossSiteRequest = pageHost != null && !isSameSite(pageHost, host)
                if (isCrossSiteRequest) recordThirdPartyRequest(view, host)

                if (isThirdPartyMediaService(pageUrl) || isThirdPartyMediaService(requestUrl) ||
                    !isShieldEnabledFor(pageUrl ?: requestUrl)) {
                    return super.shouldInterceptRequest(view, request)
                }

                if (pageHost != null && !isCrossSiteRequest) {
                    return super.shouldInterceptRequest(view, request)
                }
                val urlStr = request.url.toString().lowercase()

                if (isStreamingHost(host)) {
                    return super.shouldInterceptRequest(view, request)
                }

                val isStrictAdblock = prefs.getBoolean(PREF_STRICT_ADBLOCK, true)
                val isSocialShield = prefs.getBoolean(PREF_SOCIAL_WIDGETS, false)

                val isSocialTracker = isSocialShield && (
                    host.contains("connect.facebook.net") ||
                    host.contains("graph.facebook.com") ||
                    host.contains("analytics.tiktok.com") ||
                    host.contains("pixel.wp.com")
                )

                val isAdScript = urlStr.contains("/pagead/") ||
                        urlStr.contains("adsbygoogle.js") ||
                        urlStr.contains("googletagservices.com") ||
                        urlStr.contains("googleadservices.com") ||
                        urlStr.contains("/gpt.js") ||
                        urlStr.contains("adservice.google.")

                if (isSocialTracker || (isStrictAdblock && (isAdScript || AdShieldApp.instance.blocklistEngine.isDomainBlocked(host)))) {
                    recordFilteredRequest(view, host)
                    val mimeType = if (urlStr.endsWith(".js") || urlStr.contains(".js?")) "application/javascript" else "text/plain"
                    val emptyPayload = if (mimeType == "application/javascript") "/* blocked by fokk shield */" else ""
                    return WebResourceResponse(
                        mimeType,
                        "UTF-8",
                        403,
                        "Blocked by Fokk Shield",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(emptyPayload.toByteArray(Charsets.UTF_8))
                    )
                }

                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread { handleWebPermissionRequest(webView, request) }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (request != null && pendingWebPermission?.request === request) {
                    pendingWebPermission = null
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (callback == null) return
                runOnUiThread { handleGeolocationPrompt(webView, origin, callback) }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (!isUserGesture) return false
                val message = resultMsg ?: return false
                val transport = message.obj as? WebView.WebViewTransport ?: return false
                val sourceTab = tabs.firstOrNull { it.webView === view }
                val newTab = createTab("about:blank", isPrivate = sourceTab?.isPrivate == true)
                sourceTab?.let { newTab.isDesktop = it.isDesktop }
                tabs.add(newTab)
                switchToTab(tabs.lastIndex)
                val popupWebView = newTab.webView ?: return false
                applyDesktopMode(popupWebView, newTab.isDesktop)
                transport.webView = popupWebView
                message.sendToTarget()
                updateTabCounter()
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view == activeWebView) {
                    binding.progressBar.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty() && activeTab?.url?.contains("fokk_start.html") != true) {
                    tabs.find { it.webView == view }?.title = title
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(intent)
                    return true
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (view == null || customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                val decor = window.decorView
                previousSystemUiVisibility = decor.systemUiVisibility
                previousRequestedOrientation = requestedOrientation
                previousWindowWasFullscreen = (window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
                customView = view
                customViewCallback = callback
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                decor.systemUiVisibility = decor.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                binding.fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.mainContentLayout.visibility = View.GONE
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                hideCustomView()
            }
        }

        // Dual Scroll Auto-Hide Toolbar mechanics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener { scrolledView, _, scrollY, _, oldScrollY ->
                if (scrolledView !== activeWebView) return@setOnScrollChangeListener
                val dy = scrollY - oldScrollY
                if (dy > 12 && scrollY > 80) {
                    setBrowserChromeVisible(false)
                    binding.cardSuggestions.visibility = View.GONE
                } else if (dy < -12 || scrollY < 20) {
                    setBrowserChromeVisible(true)
                }
            }
        }

        val initialTitle = if (url.contains("fokk_start.html")) "New Tab" else "Fokk Browser"
        val tab = BrowserTab(title = initialTitle, url = url, isPrivate = isPrivate, webView = webView)
        binding.tabContainer.addView(webView)
        return tab
    }

    private fun showDownloadOptions(
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        isPrivate: Boolean,
        sourceUrl: String,
        allowThirdPartyCookies: Boolean
    ) {
        val privateNotice = if (isPrivate) {
            " Fokk may send cookies scoped to the download URL to Android Downloads. The file and download record remain after private tabs close."
        } else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isPrivate) "Save outside private tabs?" else "Download")
            .setMessage("Choose when this file can download.$privateNotice")
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Wi-Fi only") { _, _ ->
                requestDownloadPermissionThenQueue(
                    downloadUrl, userAgent, contentDisposition, mimeType,
                    sourceUrl, allowThirdPartyCookies, wifiOnly = true
                )
            }
            .setPositiveButton(if (isPrivate) "Download anyway" else "Download now") { _, _ ->
                requestDownloadPermissionThenQueue(
                    downloadUrl, userAgent, contentDisposition, mimeType,
                    sourceUrl, allowThirdPartyCookies, wifiOnly = false
                )
            }
            .show()
    }

    private fun requestDownloadPermissionThenQueue(
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        sourceUrl: String,
        allowThirdPartyCookies: Boolean,
        wifiOnly: Boolean
    ) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            enqueueDownload(
                downloadUrl, userAgent, contentDisposition, mimeType,
                sourceUrl, allowThirdPartyCookies, wifiOnly
            )
            return
        }

        pendingDownload = PendingDownload(
            downloadUrl, userAgent, contentDisposition, mimeType,
            sourceUrl, allowThirdPartyCookies, wifiOnly
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow download storage access?")
            .setMessage("Android 9 and earlier require storage access to save this file in Downloads.")
            .setNegativeButton("Cancel") { _, _ -> pendingDownload = null }
            .setPositiveButton("Continue") { _, _ ->
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setOnCancelListener { pendingDownload = null }
            .show()
    }

    private fun enqueueDownload(
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        sourceUrl: String,
        allowThirdPartyCookies: Boolean,
        wifiOnly: Boolean
    ) {
        try {
            val downloadUri = Uri.parse(downloadUrl)
            val downloadScheme = downloadUri.scheme?.lowercase(Locale.ROOT)
            if (downloadScheme !in setOf("https", "http") ||
                downloadUri.host.isNullOrBlank() || downloadUri.userInfo != null
            ) {
                Toast.makeText(this, "This download link is not supported", Toast.LENGTH_SHORT).show()
                return
            }
            val request = DownloadManager.Request(downloadUri).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                // DownloadManager does not inherit this WebView's cookie store. Pass only
                // cookies scoped by Android to the requested URL so signed-in downloads work.
                val sourceHost = runCatching { Uri.parse(sourceUrl).host }.getOrNull()
                val downloadHost = downloadUri.host
                val maySendCookies = allowThirdPartyCookies ||
                    (sourceHost != null && downloadHost != null && isSameSite(sourceHost, downloadHost))
                if (maySendCookies) {
                    runCatching { CookieManager.getInstance().getCookie(downloadUrl) }
                        .getOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?.let { addRequestHeader("Cookie", it) }
                }
                setDescription("Downloading via Fokk Browser...")
                setAllowedNetworkTypes(
                    if (wifiOnly) DownloadManager.Request.NETWORK_WIFI
                    else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                val fileName = safeDownloadFileName(
                    URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                )
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                allowScanningByMediaScanner()
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            val message = if (wifiOnly) {
                "Queued for Wi-Fi; Android will resume it when Wi-Fi is available"
            } else {
                "Download queued in Android Downloads"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Could not queue browser download", e)
            Toast.makeText(this, "Could not queue this download. Check the link and try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun safeDownloadFileName(suggestedName: String): String {
        val leafName = suggestedName.substringAfterLast('/').substringAfterLast('\\')
        val safeName = leafName
            .filterNot(Char::isISOControl)
            .trim()
            .trimEnd('.')
            .take(MAX_DOWNLOAD_FILENAME_LENGTH)
        return safeName.ifBlank { "download" }
    }

    private fun openIsolatedPrivateBrowser(url: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Private tabs with isolated site data require Android 9 or newer", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            startActivity(Intent(this, PrivateBrowserActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }.onFailure {
            Toast.makeText(this, "Could not open the isolated private browser", Toast.LENGTH_LONG).show()
        }
    }

    private fun addNewTab(
        url: String = "file:///android_asset/fokk_start.html",
        isPrivate: Boolean = false,
        isDesktop: Boolean = false
    ) {
        if (isIsolatedPrivateProfile && !privateProfileReady) {
            if (isWebUrl(url)) pendingPrivateUrls.add(url)
            return
        }
        if (isPrivate && !isIsolatedPrivateProfile) {
            openIsolatedPrivateBrowser(url)
            return
        }
        val tabIsPrivate = isPrivate || isIsolatedPrivateProfile
        val tab = createTab(url, tabIsPrivate).apply { this.isDesktop = isDesktop }
        val webView = tab.webView ?: return
        applyDesktopMode(webView, tab.isDesktop)
        tabs.add(tab)
        switchToTab(tabs.size - 1)
        loadUrlInWebView(webView, url)
        updateTabCounter()
        persistSession()
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        setBrowserChromeVisible(true)
        activeTabIndex = index
        val current = tabs[index]
        if (current.webView == null) {
            val restored = createTab(current.url, current.isPrivate)
            val restoredWebView = restored.webView ?: return
            current.webView = restoredWebView
            current.isSuspended = false
            current.shouldLoadOnSelect = true
            applyDesktopMode(restoredWebView, current.isDesktop)
            if (current.suspendedForMemory) {
                current.suspendedForMemory = false
                Toast.makeText(this, "Reloaded this background tab to free memory", Toast.LENGTH_SHORT).show()
            }
        }
        for (i in tabs.indices) {
            tabs[i].webView?.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        if (current.shouldLoadOnSelect) {
            current.shouldLoadOnSelect = false
            loadUrlInWebView(current.webView, current.url)
        }
        updateUrlBar(current.url, current.title)

        // Apply Private Tab styling if active
        if (current.isPrivate) {
            binding.topBar.setBackgroundColor(android.graphics.Color.parseColor("#1B1229"))
            binding.bottomNav.setBackgroundColor(android.graphics.Color.parseColor("#120A1C"))
            binding.imgEngineIcon.setImageResource(R.drawable.ic_lock)
            binding.imgEngineIcon.imageTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#C084FC"))
        } else {
            binding.topBar.setBackgroundColor(android.graphics.Color.parseColor("#F0090D14"))
            binding.bottomNav.setBackgroundColor(android.graphics.Color.parseColor("#0C101A"))
            binding.imgEngineIcon.setImageResource(currentSearchEngine.iconRes)
            binding.imgEngineIcon.imageTintList = null
        }

        updateTabCounter()
        persistSession()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val previousActiveIndex = activeTabIndex
        val removed = tabs.removeAt(index)
        removed.webView?.let { webView ->
            cancelPendingWebPermission(webView)
            cancelPendingLocationPermission(webView)
            binding.tabContainer.removeView(webView)
            pageUrlsByWebView.remove(webView)
            webView.destroy()
        }

        if (tabs.isEmpty()) {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
        } else {
            val newIndex = when {
                index < previousActiveIndex -> previousActiveIndex - 1
                index == previousActiveIndex -> minOf(index, tabs.lastIndex)
                else -> previousActiveIndex
            }
            switchToTab(newIndex)
        }
        updateTabCounter()
    }

    private fun updateTabCounter() {
        binding.textTabCount.text = tabs.size.toString()
    }

    private fun isShieldEnabledFor(url: String?): Boolean {
        val host = try { Uri.parse(url).host?.lowercase() } catch (_: Exception) { null } ?: return true
        if (isThirdPartyMediaServiceHost(host)) return false
        val disabledSites = localPrefs.getStringSet(PREF_SHIELD_DISABLED_SITES, emptySet()).orEmpty()
        return host !in disabledSites && registrableDomain(host) !in disabledSites
    }

    /** Keep ad filters and injected scripts out of third-party streaming services. */
    private fun isThirdPartyMediaService(url: String?): Boolean {
        val host = try { Uri.parse(url).host?.lowercase() } catch (_: Exception) { null } ?: return false
        return isThirdPartyMediaServiceHost(host)
    }

    private fun isThirdPartyMediaServiceHost(host: String): Boolean =
        THIRD_PARTY_MEDIA_SERVICE_DOMAINS.any { host == it || host.endsWith(".$it") }

    /** Standard protection leaves same-site requests alone to reduce breakage. */
    private fun isSameSite(firstHost: String, secondHost: String): Boolean =
        registrableDomain(firstHost) == registrableDomain(secondHost)

    private fun registrableDomain(rawHost: String): String {
        val host = rawHost.lowercase().trim('.').substringBefore('%')
        if (host.isBlank() || ':' in host || IPV4_ADDRESS_REGEX.matches(host)) return host
        val labels = host.split('.').filter(String::isNotBlank)
        if (labels.size < 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val suffixLabels = if (lastTwo in MULTI_LABEL_PUBLIC_SUFFIXES) 2 else 1
        return labels.takeLast(minOf(labels.size, suffixLabels + 1)).joinToString(".")
    }

    private fun setShieldEnabledForCurrentSite(enabled: Boolean) {
        val currentUrl = activeWebView?.url ?: activeTab?.url
        val host = try { currentUrl?.let { Uri.parse(it).host?.lowercase() } } catch (_: Exception) { null }
        if (host == null) {
            Toast.makeText(this, "Open a website to change its shield setting", Toast.LENGTH_SHORT).show()
            return
        }
        val site = registrableDomain(host)
        val disabled = localPrefs.getStringSet(PREF_SHIELD_DISABLED_SITES, emptySet()).orEmpty().toMutableSet()
        if (enabled) {
            disabled.remove(host)
            disabled.remove(site)
        } else {
            disabled.add(site)
        }
        localPrefs.edit().putStringSet(PREF_SHIELD_DISABLED_SITES, disabled).apply()
        isShieldEnabled = enabled
    }

    private fun persistSession() {
        if (isIsolatedPrivateProfile) return
        val state = JSONArray()
        val publicTabs = tabs.filter { !it.isPrivate && isRestorableSessionUrl(sessionUrl(it)) }
        val activePublicIndex = publicTabs.indexOfFirst { it === activeTab }
        val firstSavedIndex = if (publicTabs.size <= MAX_RESTORED_TABS) {
            0
        } else {
            val preferredStart = if (activePublicIndex >= 0) activePublicIndex - (MAX_RESTORED_TABS / 2) else 0
            preferredStart.coerceIn(0, publicTabs.size - MAX_RESTORED_TABS)
        }
        val restorableTabs = publicTabs.drop(firstSavedIndex).take(MAX_RESTORED_TABS)
        restorableTabs.forEach { tab ->
            state.put(JSONObject().put("url", sessionUrl(tab)).put("desktop", tab.isDesktop))
        }
        val activeRestorableIndex = if (activePublicIndex >= 0) activePublicIndex - firstSavedIndex else -1
        localPrefs.edit()
            .putString(PREF_SESSION_TABS, state.toString())
            .putInt(PREF_SESSION_ACTIVE_TAB, activeRestorableIndex.takeIf { it >= 0 } ?: (restorableTabs.lastIndex))
            .apply()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        if (isMediaPlaying || System.currentTimeMillis() - lastMediaPlayingTime < MEDIA_RECENT_ACTIVITY_WINDOW_MS) return

        flushFilteredRequestCounts()
        tabs.forEachIndexed { index, tab ->
            if (index == activeTabIndex || tab.isOfflineReader) return@forEachIndexed
            val webView = tab.webView ?: return@forEachIndexed
            tab.url = webView.url?.takeIf { isWebUrl(it) || it.startsWith("file:///android_asset/") } ?: tab.url
            tab.title = webView.title?.takeIf(String::isNotBlank) ?: tab.title
            cancelPendingWebPermission(webView)
            cancelPendingLocationPermission(webView)
            runCatching { webView.stopLoading() }
            binding.tabContainer.removeView(webView)
            pageUrlsByWebView.remove(webView)
            runCatching { webView.destroy() }
            tab.webView = null
            tab.isSuspended = true
            tab.suspendedForMemory = true
            tab.shouldLoadOnSelect = true
        }
    }

    private fun restoreLastSession() {
        val saved = localPrefs.getString(PREF_SESSION_TABS, null)
        val savedActiveIndex = localPrefs.getInt(PREF_SESSION_ACTIVE_TAB, -1)
        var restoredActiveIndex = -1
        try {
            val items = JSONArray(saved)
            for (i in 0 until items.length()) {
                if (tabs.size >= MAX_RESTORED_TABS) break
                val item = items.getJSONObject(i)
                val url = item.optString("url")
                if (isRestorableSessionUrl(url)) {
                    if (i == savedActiveIndex) restoredActiveIndex = tabs.size
                    tabs.add(BrowserTab(
                        title = Uri.parse(url).host ?: "New Tab",
                        url = url,
                        isDesktop = item.optBoolean("desktop", false),
                        shouldLoadOnSelect = true,
                        isSuspended = true,
                        isPrivate = false,
                        webView = null
                    ))
                }
            }
        } catch (_: Exception) { }
        if (tabs.isEmpty()) {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
        } else {
            val restoredIndex = (restoredActiveIndex.takeIf { it >= 0 } ?: tabs.lastIndex)
                .coerceIn(0, tabs.lastIndex)
            switchToTab(restoredIndex)
            updateTabCounter()
        }
    }

    private fun sessionUrl(tab: BrowserTab): String =
        if (tab.isOfflineReader || tab.isSuspended) tab.url else (tab.webView?.url ?: tab.url)

    private fun isRestorableSessionUrl(url: String): Boolean =
        isWebUrl(url) || url.startsWith("file:///android_asset/")

    private fun injectAdblockScript(webView: WebView?, pageUrl: String? = webView?.url) {
        if (webView == null) return
        if (isThirdPartyMediaService(pageUrl) || !isShieldEnabledFor(pageUrl)) return
        if (injectionScript.isNotEmpty()) {
            webView.evaluateJavascript(injectionScript, null)
            val isAiFilter = prefs.getBoolean(PREF_AI_FILTER, true)
            if (!isAiFilter) {
                webView.evaluateJavascript("if (typeof setAiFilterEnabled === 'function') setAiFilterEnabled(false);", null)
            }
        }

        // Custom User CSS
        val customCss = prefs.getString(PREF_CUSTOM_CSS, "") ?: ""
        if (customCss.isNotEmpty()) {
            val safeCss = customCss.replace("'", "\\'").replace("\n", " ")
            val js = "javascript:(function(){let s=document.getElementById('__fokk_custom_css');if(!s){s=document.createElement('style');s.id='__fokk_custom_css';(document.head||document.documentElement).appendChild(s);}s.innerHTML='$safeCss';})();"
            webView.evaluateJavascript(js, null)
        }

        // Custom Userscript
        val customJs = prefs.getString(PREF_CUSTOM_JS, "") ?: ""
        if (customJs.isNotEmpty()) {
            webView.evaluateJavascript(customJs, null)
        }
    }

    /** The native bridge is required only by our bundled start page, never by remote content. */
    private fun setBridgeEnabled(webView: WebView?, url: String?) {
        if (webView == null) return
        webView.removeJavascriptInterface("FokkShieldBridge")
        webView.removeJavascriptInterface("FokkMediaBridge")
        if (!isThirdPartyMediaService(url) && url?.startsWith(OFFLINE_PAGE_BASE_URL) != true) {
            webView.addJavascriptInterface(MediaPlaybackBridge(this), "FokkMediaBridge")
        }
        if (url?.startsWith("file:///android_asset/") == true) {
            webView.addJavascriptInterface(WebAppInterface(this), "FokkShieldBridge")
        }
    }

    private fun injectDarkModeScript(webView: WebView?, enable: Boolean) {
        if (webView == null) return
        if (isThirdPartyMediaService(webView.url)) return
        val js = if (enable) {
            "javascript:(function(){let d=document.getElementById('__fokk_dark');if(!d){let s=document.createElement('style');s.id='__fokk_dark';s.innerHTML='html { filter: invert(90%) hue-rotate(180deg) !important; background: #111 !important; } img, video, iframe, canvas { filter: invert(100%) hue-rotate(180deg) !important; }';document.head.appendChild(s);}})();"
        } else {
            "javascript:(function(){let d=document.getElementById('__fokk_dark');if(d)d.remove();})();"
        }
        webView.evaluateJavascript(js, null)
    }

    private fun isStreamingHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower.contains("adservice") ||
            lower.contains("doubleclick") ||
            lower.contains("googlesyndication") ||
            lower.contains("googleadservices") ||
            lower.contains("googletag") ||
            lower.contains("admob") ||
            lower.contains("analytics")) {
            return false
        }
        return STREAMING_MEDIA_DOMAINS.any { lower == it || lower.endsWith(".$it") }
    }

    private fun setupControls() {
        binding.imgEngineIcon.setOnClickListener {
            showSearchEngineDialog()
        }

        binding.btnVoiceSearch.setOnClickListener {
            startVoiceSearch()
        }

        binding.btnNavTabs.setOnClickListener {
            showTabSwitcherDialog()
        }

        binding.btnNavMenu.setOnClickListener {
            showBrowserMenuDialog()
        }

        binding.urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                binding.btnClearUrl.visibility = if (text.isNotEmpty() && binding.urlInput.hasFocus()) View.VISIBLE else View.GONE
                if (binding.urlInput.hasFocus()) {
                    showRecentHistoryOrSuggestions(text)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setBrowserChromeVisible(true)
                val currentUrl = activeWebView?.url ?: ""
                if (!currentUrl.contains("fokk_start.html") && currentUrl.isNotBlank()) {
                    binding.urlInput.setText(currentUrl)
                    binding.urlInput.selectAll()
                }
                binding.btnClearUrl.visibility = if (binding.urlInput.text.isNotEmpty()) View.VISIBLE else View.GONE
                showRecentHistoryOrSuggestions(binding.urlInput.text.toString().trim())
            } else {
                binding.cardSuggestions.visibility = View.GONE
                binding.btnClearUrl.visibility = View.GONE
                updateUrlBar(activeWebView?.url, activeWebView?.title)
            }
        }

        binding.btnClearUrl.setOnClickListener {
            binding.urlInput.setText("")
            showRecentHistoryOrSuggestions("")
        }

        val navigateOrSearch: () -> Unit = {
            val query = binding.urlInput.text.toString().trim()
            binding.cardSuggestions.visibility = View.GONE
            if (query.isNotEmpty()) {
                val rawTarget = when {
                    query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://") -> query
                    query.contains(".") && !query.contains(" ") -> "https://$query"
                    else -> currentSearchEngine.searchUrl + Uri.encode(query)
                }
                loadUrlInActiveWebView(rawTarget)
                recordHistory(query, rawTarget)
                binding.urlInput.clearFocus()
            }
        }

        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                navigateOrSearch()
                true
            } else {
                false
            }
        }
    }

    private fun showRecentHistoryOrSuggestions(query: String) {
        suggestionJob?.cancel()
        suggestionJob = null
        // Do not expose normal-profile history or send partially typed private queries to
        // DuckDuckGo's suggestion endpoint. The chosen search engine only receives a query
        // after the user submits it.
        if (activeTab?.isPrivate == true) {
            binding.cardSuggestions.visibility = View.GONE
            return
        }
        if (query.isEmpty()) {
            val historyList = getRecentHistory()
            displaySuggestions(historyList)
            return
        }

        val historyList = getRecentHistory().filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        }

        if (query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://")) {
            displaySuggestions(historyList)
            return
        }
        if (!prefs.getBoolean(PREF_REMOTE_SUGGESTIONS, false)) {
            displaySuggestions(historyList)
            return
        }

        suggestionJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(300)
            val mergedList = historyList.toMutableList()
            var conn: HttpURLConnection? = null
            try {
                val endpoint = "https://duckduckgo.com/ac/?q=${Uri.encode(query)}&type=list"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 800
                    readTimeout = 800
                    setRequestProperty("User-Agent", cleanMobileUa)
                }
                conn = connection
                if (connection.responseCode == 200) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = JSONArray(text)
                    if (jsonArr.length() >= 2) {
                        val suggestionsArr = jsonArr.getJSONArray(1)
                        for (i in 0 until suggestionsArr.length()) {
                            if (mergedList.size >= 10) break
                            val s = suggestionsArr.getString(i)
                            if (mergedList.none { it.title.equals(s, ignoreCase = true) }) {
                                mergedList.add(HistorySuggestion(title = s, url = currentSearchEngine.searchUrl + Uri.encode(s), isHistory = false))
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            } finally {
                conn?.disconnect()
            }
            withContext(Dispatchers.Main) {
                if (binding.urlInput.hasFocus() && binding.urlInput.text.toString().trim() == query) {
                    displaySuggestions(mergedList)
                }
            }
        }
    }

    private fun displaySuggestions(list: List<HistorySuggestion>) {
        if (list.isEmpty() || !binding.urlInput.hasFocus()) {
            binding.cardSuggestions.visibility = View.GONE
            return
        }

        binding.layoutSuggestionsList.removeAllViews()
        val density = resources.displayMetrics.density

        for (item in list) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
                setBackgroundResource(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt())
                val iconRes = if (item.isHistory) R.drawable.ic_history else R.drawable.ic_search
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(
                    if (item.isHistory) getColor(R.color.secondary) else getColor(R.color.fokk_cyan)
                )
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * density).toInt()
                }
            }

            val titleTv = TextView(this).apply {
                text = item.title
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val urlTv = TextView(this).apply {
                text = item.url
                setTextColor(getColor(R.color.text_secondary))
                textSize = 10.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            textLayout.addView(titleTv)
            if (item.isHistory && !item.url.startsWith("fokk://")) {
                textLayout.addView(urlTv)
            }

            row.addView(icon)
            row.addView(textLayout)

            row.setOnClickListener {
                binding.cardSuggestions.visibility = View.GONE
                binding.urlInput.clearFocus()
                val target = if (item.url.startsWith("http://") || item.url.startsWith("https://") || item.url.startsWith("file://")) {
                    item.url
                } else {
                    currentSearchEngine.searchUrl + Uri.encode(item.title)
                }
                loadUrlInActiveWebView(target)
                recordHistory(item.title, target)
            }
            binding.layoutSuggestionsList.addView(row)
        }

        binding.cardSuggestions.visibility = View.VISIBLE
    }

    private fun updateUrlBar(url: String?, title: String?) {
        if (binding.urlInput.hasFocus()) return
        if (url == null || url.contains("fokk_start.html")) {
            binding.urlInput.setText("")
            binding.urlInput.hint = "Search with ${currentSearchEngine.displayName} or enter address"
        } else {
            try {
                val host = Uri.parse(url).host ?: url
                binding.urlInput.setText(host.removePrefix("www."))
            } catch (e: Exception) {
                binding.urlInput.setText(url)
            }
        }
    }

    private fun openSystemDownloads() {
        if (isIsolatedPrivateProfile) {
            Toast.makeText(this, "System downloads are outside the private browser", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path)
                    setDataAndType(uri, "*/*")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Downloads folder: Download/", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchEngineDialog() {
        val engines = SearchEngine.values()
        val items = engines.map { it.displayName }.toTypedArray()
        val selectedIdx = engines.indexOf(currentSearchEngine).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Default Search Engine")
            .setSingleChoiceItems(items, selectedIdx) { dialog, which ->
                val chosen = engines[which]
                applySearchEngine(chosen)
                Toast.makeText(this, "${chosen.displayName} set as default search engine", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExtensionsManagerDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetExtensionsManagerBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.switchStrictAdblock.isChecked = prefs.getBoolean(PREF_STRICT_ADBLOCK, true)
        sheetBinding.switchSocialWidgets.isChecked = prefs.getBoolean(PREF_SOCIAL_WIDGETS, false)
        sheetBinding.editCustomJs.setText(prefs.getString(PREF_CUSTOM_JS, "") ?: "")
        sheetBinding.editCustomCss.setText(prefs.getString(PREF_CUSTOM_CSS, "") ?: "")

        sheetBinding.btnSaveExtensions.setOnClickListener {
            prefs.edit()
                .putBoolean(PREF_STRICT_ADBLOCK, sheetBinding.switchStrictAdblock.isChecked)
                .putBoolean(PREF_SOCIAL_WIDGETS, sheetBinding.switchSocialWidgets.isChecked)
                .putString(PREF_CUSTOM_JS, sheetBinding.editCustomJs.text.toString().trim())
                .putString(PREF_CUSTOM_CSS, sheetBinding.editCustomCss.text.toString().trim())
                .apply()

            Toast.makeText(this, "Browser filters saved. Reloading the current page…", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            activeWebView?.reload()
        }

        dialog.show()
    }

    private fun showHistoryDialog() {
        if (isIsolatedPrivateProfile) {
            Toast.makeText(this, "Browsing history is unavailable in private tabs", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        fun loadList(filter: String = "") {
            sheetBinding.layoutHistoryList.removeAllViews()
            try {
                val historyJson = localPrefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
                val jsonArr = JSONArray(historyJson)
                var count = 0
                val density = resources.displayMetrics.density

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val title = obj.optString("title", "Website")
                    val url = obj.optString("url", "")

                    if (filter.isNotEmpty() && !title.contains(filter, true) && !url.contains(filter, true)) {
                        continue
                    }
                    count++

                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
                        setBackgroundResource(android.R.drawable.list_selector_background)
                        isClickable = true
                    }

                    val icon = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt())
                        setImageResource(R.drawable.ic_clock)
                        imageTintList = ColorStateList.valueOf(getColor(R.color.text_secondary))
                    }

                    val infoCol = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = (10 * density).toInt()
                        }
                        orientation = LinearLayout.VERTICAL
                    }

                    val titleTv = TextView(this).apply {
                        text = title
                        setTextColor(getColor(R.color.text_primary))
                        textSize = 13f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    val urlTv = TextView(this).apply {
                        text = url
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 10.5f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }

                    infoCol.addView(titleTv)
                    infoCol.addView(urlTv)
                    row.addView(icon)
                    row.addView(infoCol)

                    row.setOnClickListener {
                        loadUrlInActiveWebView(url)
                        dialog.dismiss()
                    }

                    sheetBinding.layoutHistoryList.addView(row)
                }

                sheetBinding.textHistoryEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                sheetBinding.textHistoryEmpty.visibility = View.VISIBLE
            }
        }

        loadList()

        sheetBinding.editHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadList(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sheetBinding.btnClearHistory.setOnClickListener {
            localPrefs.edit().remove(PREF_BROWSING_HISTORY).apply()
            loadList()
            Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun bindTabCard(
        cardBinding: ItemTabCardBinding,
        tab: BrowserTab,
        globalIndex: Int,
        dialog: BottomSheetDialog,
        onRefresh: () -> Unit
    ) {
        cardBinding.textTabTitle.text = tab.title.ifEmpty { "New Tab" }
        val lowerUrl = tab.url.lowercase()

        when {
            lowerUrl.contains("fokk_start.html") -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_home)
                cardBinding.textTabTitle.text = "New Tab"
                cardBinding.textTabUrl.text = "fokk://home"
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_home)
            }
            lowerUrl.contains("youtube.com") -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_youtube)
                cardBinding.textTabUrl.text = "m.youtube.com"
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_youtube)
            }
            lowerUrl.contains("reddit.com") -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_reddit)
                cardBinding.textTabUrl.text = "reddit.com"
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_reddit)
            }
            lowerUrl.contains("x.com") || lowerUrl.contains("twitter.com") -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_twitter)
                cardBinding.textTabUrl.text = "x.com"
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_twitter)
            }
            lowerUrl.contains("instagram.com") -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_instagram)
                cardBinding.textTabUrl.text = "instagram.com"
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_instagram)
            }
            else -> {
                cardBinding.imgTabIcon.setImageResource(R.drawable.ic_launch)
                cardBinding.textTabUrl.text = try { Uri.parse(tab.url).host ?: tab.url } catch (e: Exception) { tab.url }
                cardBinding.imgPreviewLogo.setImageResource(R.drawable.ic_launch)
            }
        }

        if (globalIndex == activeTabIndex) {
            cardBinding.cardTab.strokeColor = android.graphics.Color.parseColor("#7C6CF6")
            cardBinding.cardTab.strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
            cardBinding.badgeActiveTab.visibility = View.VISIBLE
        } else {
            cardBinding.cardTab.strokeColor = getColor(R.color.card_border)
            cardBinding.cardTab.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            cardBinding.badgeActiveTab.visibility = View.GONE
        }

        if (tab.isPrivate) {
            cardBinding.badgePrivateTab.visibility = View.VISIBLE
            cardBinding.layoutCardHeader.setBackgroundColor(android.graphics.Color.parseColor("#2E1644"))
        } else {
            cardBinding.badgePrivateTab.visibility = View.GONE
            cardBinding.layoutCardHeader.setBackgroundColor(android.graphics.Color.parseColor("#172233"))
        }

        cardBinding.cardTab.setOnClickListener {
            switchToTab(globalIndex)
            dialog.dismiss()
        }

        cardBinding.btnCloseTab.setOnClickListener {
            closeTab(globalIndex)
            onRefresh()
            if (tabs.isEmpty()) {
                dialog.dismiss()
            }
        }
    }

    private fun showTabSwitcherDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetTabSwitcherBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        fun updateTabsView() {
            val normalTabs = tabs.filter { !it.isPrivate }
            val privateTabs = tabs.filter { it.isPrivate }

            sheetBinding.btnFilterNormal.text = "Tabs (${normalTabs.size})"
            sheetBinding.btnFilterPrivate.text = "Private (${privateTabs.size})"

            if (showingPrivateTabs) {
                sheetBinding.btnFilterPrivate.setBackgroundResource(R.drawable.bg_stat_chip)
                sheetBinding.btnFilterPrivate.setTextColor(getColor(R.color.fokk_cyan))
                sheetBinding.btnFilterNormal.background = null
                sheetBinding.btnFilterNormal.setTextColor(getColor(R.color.text_secondary))
            } else {
                sheetBinding.btnFilterNormal.setBackgroundResource(R.drawable.bg_stat_chip)
                sheetBinding.btnFilterNormal.setTextColor(getColor(R.color.fokk_cyan))
                sheetBinding.btnFilterPrivate.background = null
                sheetBinding.btnFilterPrivate.setTextColor(getColor(R.color.text_secondary))
            }

            val currentTabsList = tabs.filter { it.isPrivate == showingPrivateTabs }
            sheetBinding.layoutTabsGrid.removeAllViews()

            val density = resources.displayMetrics.density

            for (i in currentTabsList.indices step 2) {
                val row = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }
                    orientation = LinearLayout.HORIZONTAL
                    weightSum = 2f
                }

                val tab1 = currentTabsList[i]
                val globalIdx1 = tabs.indexOf(tab1)
                val cardBinding1 = ItemTabCardBinding.inflate(layoutInflater, row, false)
                bindTabCard(cardBinding1, tab1, globalIdx1, dialog) {
                    updateTabsView()
                }
                cardBinding1.root.layoutParams = LinearLayout.LayoutParams(0, (170 * density).toInt(), 1f)
                row.addView(cardBinding1.root)

                if (i + 1 < currentTabsList.size) {
                    val tab2 = currentTabsList[i + 1]
                    val globalIdx2 = tabs.indexOf(tab2)
                    val cardBinding2 = ItemTabCardBinding.inflate(layoutInflater, row, false)
                    bindTabCard(cardBinding2, tab2, globalIdx2, dialog) {
                        updateTabsView()
                    }
                    cardBinding2.root.layoutParams = LinearLayout.LayoutParams(0, (170 * density).toInt(), 1f)
                    row.addView(cardBinding2.root)
                } else {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, (170 * density).toInt(), 1f)
                    }
                    row.addView(spacer)
                }

                sheetBinding.layoutTabsGrid.addView(row)
            }
        }

        sheetBinding.btnFilterNormal.setOnClickListener {
            showingPrivateTabs = false
            updateTabsView()
        }

        sheetBinding.btnFilterPrivate.setOnClickListener {
            authenticateForPrivateTabs {
                if (isIsolatedPrivateProfile) {
                    showingPrivateTabs = true
                    updateTabsView()
                } else {
                    openIsolatedPrivateBrowser("file:///android_asset/fokk_start.html")
                    dialog.dismiss()
                }
            }
        }

        sheetBinding.btnNewTabSheet.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            showingPrivateTabs = isIsolatedPrivateProfile
            dialog.dismiss()
        }

        sheetBinding.btnNewPrivateTab.setOnClickListener {
            authenticateForPrivateTabs {
                addNewTab("file:///android_asset/fokk_start.html", isPrivate = true)
                showingPrivateTabs = true
                dialog.dismiss()
            }
        }

        sheetBinding.btnCloseAllTabs.setOnClickListener {
            val toRemove = tabs.filter { it.isPrivate == showingPrivateTabs }
            for (tab in toRemove) {
                tab.webView?.let { webView ->
                    binding.tabContainer.removeView(webView)
                    webView.destroy()
                }
                tabs.remove(tab)
            }
            if (tabs.isEmpty()) {
                addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            } else {
                switchToTab(0)
            }
            dialog.dismiss()
        }

        if (isIsolatedPrivateProfile) {
            sheetBinding.btnFilterNormal.visibility = View.GONE
            sheetBinding.btnFilterPrivate.text = "Private tabs (${tabs.size})"
            sheetBinding.btnNewTabSheet.contentDescription = "New private tab"
        }

        updateTabsView()
        dialog.show()
    }

    private fun saveCurrentPage() {
        val webView = activeWebView ?: return
        val currentTab = activeTab ?: return
        if (currentTab.isPrivate) {
            Toast.makeText(this, "Offline saves are unavailable in private tabs", Toast.LENGTH_LONG).show()
            return
        }
        if (currentTab.isOfflineReader) {
            Toast.makeText(this, "This page is already saved for offline reading", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUrl = webView.url ?: currentTab.url
        if (!isWebUrl(currentUrl)) {
            Toast.makeText(this, "Only open web pages can be saved for offline reading", Toast.LENGTH_SHORT).show()
            return
        }
        val currentTitle = webView.title?.ifBlank { currentUrl } ?: currentUrl
        val extractScript = """(function(){
            var root=document.querySelector('article')||document.querySelector('main')||document.body;
            var text=(root.innerText||'').replace(/\r/g,'').replace(/\n{3,}/g,'\n\n').trim().slice(0,200000);
            return JSON.stringify({title:document.title||'',text:text});
        })()""".trimIndent()
        webView.evaluateJavascript(extractScript) { result ->
            if (activeTab !== currentTab || currentTab.webView !== webView || webView.url != currentUrl) {
                Toast.makeText(this, "Page changed before it could be saved. Try again.", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            val extracted = runCatching {
                val encoded = org.json.JSONTokener(result ?: "null").nextValue() as? String
                    ?: throw IllegalArgumentException("No article content")
                JSONObject(encoded)
            }.getOrNull()
            val text = extracted?.optString("text")?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "This page has no readable text to save", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }

            val title = extracted?.optString("title")?.ifBlank { currentTitle } ?: currentTitle
            lifecycleScope.launch {
                val saved = runCatching {
                    withContext(Dispatchers.IO) { offlinePageStore.save(title, currentUrl, text) }
                }.getOrNull()
                if (saved != null) {
                    Toast.makeText(this@MediaHubActivity, "Readable page saved for offline use", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MediaHubActivity, "Could not save this page", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSavedPagesDialog() {
        if (isIsolatedPrivateProfile) {
            Toast.makeText(this, "Saved pages are unavailable in private tabs", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val savedPages = offlinePageStore.list()
            if (savedPages.isEmpty()) {
                Toast.makeText(this, "No saved pages yet. Tap Save Page on any website!", Toast.LENGTH_LONG).show()
                return
            }
            val content = layoutInflater.inflate(R.layout.dialog_saved_pages, null)
            val searchInput = content.findViewById<EditText>(R.id.editSavedPageSearch)
            val status = content.findViewById<TextView>(R.id.textSavedPageSearchStatus)
            val results = content.findViewById<LinearLayout>(R.id.layoutSavedPageResults)
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle("Saved pages")
                .setView(content)
                .setNegativeButton("Close", null)
                .create()

            fun renderPages(pages: List<OfflinePageStore.SavedPage>, query: String) {
                results.removeAllViews()
                status.text = if (query.isBlank()) {
                    "${pages.size} saved pages · search titles and text · long press to delete"
                } else {
                    "${pages.size} result${if (pages.size == 1) "" else "s"}"
                }
                val horizontalPadding = (12 * resources.displayMetrics.density).toInt()
                pages.forEach { page ->
                    val hasOfflineCopy = page.id?.let(offlinePageStore::hasOfflineCopy) == true
                    val row = TextView(this).apply {
                        text = "${page.title}${if (hasOfflineCopy) "" else " · online link only"}\n${Uri.parse(page.url).host ?: page.url}"
                        setTextColor(ContextCompat.getColor(this@MediaHubActivity, R.color.text_primary))
                        textSize = 14f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(horizontalPadding, horizontalPadding / 2, horizontalPadding, horizontalPadding / 2)
                        isClickable = true
                        isFocusable = true
                        contentDescription = "Open saved page ${page.title}. Long press to delete."
                        setOnClickListener {
                            dialog.dismiss()
                            openSavedPage(page)
                        }
                        setOnLongClickListener {
                            MaterialAlertDialogBuilder(this@MediaHubActivity)
                                .setTitle("Delete saved page?")
                                .setMessage("Remove the offline text and its saved-page entry from this device?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete") { _, _ ->
                                    lifecycleScope.launch {
                                        val deleted = withContext(Dispatchers.IO) { offlinePageStore.delete(page) }
                                        val query = searchInput.text.toString()
                                        val updated = withContext(Dispatchers.IO) {
                                            offlinePageStore.search(query)
                                        }
                                        if (dialog.isShowing) {
                                            renderPages(updated, query)
                                            if (!deleted) {
                                                Toast.makeText(
                                                    this@MediaHubActivity,
                                                    "Could not update saved pages",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                                .show()
                            true
                        }
                    }
                    results.addView(row, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (60 * resources.displayMetrics.density).toInt()
                    ))
                    val divider = View(this).apply {
                        setBackgroundColor(android.graphics.Color.parseColor("#263246"))
                    }
                    results.addView(divider, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ))
                }
                if (pages.isEmpty()) {
                    status.text = "No saved pages match that search"
                }
            }

            renderPages(savedPages, "")
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                    val query = editable?.toString().orEmpty()
                    savedPagesSearchJob?.cancel()
                    savedPagesSearchJob = lifecycleScope.launch {
                        delay(250)
                        val matches = withContext(Dispatchers.IO) { offlinePageStore.search(query) }
                        if (dialog.isShowing) renderPages(matches, query)
                    }
                }
            })
            dialog.setOnDismissListener { savedPagesSearchJob?.cancel() }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to load saved pages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSavedPage(page: OfflinePageStore.SavedPage) {
        val pageUri = runCatching { Uri.parse(page.url) }.getOrNull()
        if (!isWebUrl(page.url) || pageUri?.userInfo != null) {
            Toast.makeText(this, "This saved link is invalid and cannot be opened", Toast.LENGTH_LONG).show()
            return
        }
        val targetTab = activeTab ?: return
        val targetWebView = targetTab.webView ?: return
        val id = page.id
        if (id == null) {
            Toast.makeText(this, "This is an older saved link; internet is needed to open it", Toast.LENGTH_LONG).show()
            if (page.url.isNotBlank()) loadUrlInActiveWebView(page.url)
            return
        }
        lifecycleScope.launch {
            val articleText = withContext(Dispatchers.IO) { offlinePageStore.readText(id) }
            if (articleText.isNullOrBlank()) {
                Toast.makeText(this@MediaHubActivity, "The offline copy is missing. Opening the site online.", Toast.LENGTH_LONG).show()
                if (page.url.isNotBlank()) loadUrlInActiveWebView(page.url)
                return@launch
            }
            if (!tabs.contains(targetTab) || targetTab.webView !== targetWebView) return@launch
            targetTab.url = page.url
            targetTab.title = page.title
            targetTab.isOfflineReader = true
            updateUrlBar(page.url, page.title)
            setBridgeEnabled(targetWebView, OFFLINE_PAGE_BASE_URL)
            targetWebView.loadDataWithBaseURL(
                OFFLINE_PAGE_BASE_URL,
                buildOfflineArticleHtml(page.title, page.url, articleText),
                "text/html",
                "UTF-8",
                OFFLINE_PAGE_BASE_URL
            )
        }
    }

    private fun buildOfflineArticleHtml(title: String, sourceUrl: String, text: String): String {
        val safeTitle = TextUtils.htmlEncode(title)
        val safeSource = TextUtils.htmlEncode(sourceUrl)
        val paragraphs = text.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("") { "<p>${TextUtils.htmlEncode(it)}</p>" }
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
            <title>$safeTitle</title><style>
            *{box-sizing:border-box}body{margin:0;background:#f7f3e9;color:#282720;font:18px/1.75 Georgia,serif}
            header{position:sticky;top:0;background:#f7f3e9;border-bottom:1px solid #ded9cc;padding:12px 20px;font:14px system-ui,sans-serif;color:#555}
            main{max-width:720px;margin:28px auto;padding:0 22px 56px;overflow-wrap:anywhere}
            h1{font:700 30px/1.2 system-ui,sans-serif;margin:0 0 18px}p{margin:0 0 1.1em}
            .source{font:13px/1.5 system-ui,sans-serif;color:#666;margin-bottom:28px}
            @media(prefers-color-scheme:dark){body,header{background:#17191d;color:#e8e5dd}header{border-color:#373b42}.source{color:#b8b5ac}}
            </style></head><body><header>Saved for offline reading</header><main><h1>$safeTitle</h1>
            <div class="source">Source: $safeSource<br>Text snapshot; images and interactive parts are not included.</div>$paragraphs
            </main></body></html>""".trimIndent()
    }

    private fun showBrowserMenuDialog() {
        flushFilteredRequestCounts()
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetBrowserMenuBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val isStartPage = activeWebView?.url?.contains("fokk_start.html") == true || activeWebView?.url.isNullOrBlank()
        val isPrivateProfile = isIsolatedPrivateProfile

        // 1. Back button
        val canGoBack = activeWebView?.canGoBack() == true
        sheetBinding.btnMenuBack.alpha = if (canGoBack) 1.0f else 0.35f
        sheetBinding.btnMenuBack.isEnabled = canGoBack
        sheetBinding.btnMenuBack.setOnClickListener {
            if (activeWebView?.canGoBack() == true) {
                activeWebView?.goBack()
                dialog.dismiss()
            }
        }

        // 2. Forward button
        val canGoForward = activeWebView?.canGoForward() == true
        sheetBinding.btnMenuForward.alpha = if (canGoForward) 1.0f else 0.35f
        sheetBinding.btnMenuForward.isEnabled = canGoForward
        sheetBinding.btnMenuForward.setOnClickListener {
            if (activeWebView?.canGoForward() == true) {
                activeWebView?.goForward()
                dialog.dismiss()
            }
        }

        // 3. Home button
        sheetBinding.btnMenuHome.alpha = if (isStartPage) 0.45f else 1.0f
        sheetBinding.btnMenuHome.setOnClickListener {
            loadUrlInActiveWebView("file:///android_asset/fokk_start.html")
            dialog.dismiss()
        }

        // 4. Save Page button
        sheetBinding.btnMenuSave.alpha = if (isStartPage) 0.35f else 1.0f
        sheetBinding.btnMenuSave.isEnabled = !isStartPage && !isPrivateProfile
        if (isPrivateProfile) sheetBinding.btnMenuSave.alpha = 0.35f
        sheetBinding.btnMenuSave.setOnClickListener {
            dialog.dismiss()
            saveCurrentPage()
        }

        // 5. Downloads button (opens standard downloads manager)
        sheetBinding.btnMenuDownloads.setOnClickListener {
            dialog.dismiss()
            openSystemDownloads()
        }

        // Saved Pages row
        sheetBinding.rowSavedPages.setOnClickListener {
            dialog.dismiss()
            showSavedPagesDialog()
        }

        // These views expose app-wide history, saved files, or DownloadManager records.
        // The private process has a separate WebView profile, but these stores are shared.
        if (isPrivateProfile) {
            sheetBinding.btnMenuDownloads.visibility = View.GONE
            sheetBinding.rowSavedPages.visibility = View.GONE
        }

        val activePrivacyTab = activeTab
        sheetBinding.textMenuBlockedInfo.text =
            "WebView observed ${activePrivacyTab?.thirdPartyRequests ?: 0} cross-site requests · Fokk filtered ${activePrivacyTab?.filteredRequests ?: 0}"
        sheetBinding.switchMenuShield.isChecked = isShieldEnabled
        sheetBinding.badgeSocialShield.alpha = if (isShieldEnabled) 1.0f else 0.4f
        sheetBinding.badgeAiFilter.alpha = if (isShieldEnabled) 1.0f else 0.4f

        sheetBinding.rowShieldHub.setOnClickListener {
            dialog.dismiss()
            openShieldDashboard()
        }

        sheetBinding.textAppVersionInfo.text = "v${AppUpdateManager.getCurrentVersionName(this)} • Tap to check for updates"
        sheetBinding.rowAppUpdates.setOnClickListener {
            dialog.dismiss()
            AppUpdateManager.checkForUpdates(this, manualCheck = true)
        }
        sheetBinding.switchDesktop.isChecked = activeTab?.isDesktop ?: false
        sheetBinding.switchDarkMode.isChecked = isDarkMode
                val isBioEnabled = prefs.getBoolean(PREF_BIOMETRIC_PRIVATE_TABS, false)
        sheetBinding.switchBiometricLock.isChecked = isBioEnabled

        sheetBinding.switchBiometricLock.setOnClickListener {
            val targetState = sheetBinding.switchBiometricLock.isChecked
            if (targetState) {
                if (!canUseBiometric()) {
                    sheetBinding.switchBiometricLock.isChecked = false
                    Toast.makeText(this, "Set up a fingerprint or device PIN in Android Settings first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit().putBoolean(PREF_BIOMETRIC_PRIVATE_TABS, true).apply()
                Toast.makeText(this, "Private tabs protected by fingerprint/PIN", Toast.LENGTH_SHORT).show()
            } else {
                sheetBinding.switchBiometricLock.isChecked = true
                authenticateUser(
                    title = "Disable Private Tab Lock",
                    subtitle = "Verify fingerprint or device PIN to turn off lock",
                    onSuccess = {
                        prefs.edit().putBoolean(PREF_BIOMETRIC_PRIVATE_TABS, false).apply()
                        sheetBinding.switchBiometricLock.isChecked = false
                        Toast.makeText(this, "Private tab lock disabled", Toast.LENGTH_SHORT).show()
                    },
                    onFail = {
                        sheetBinding.switchBiometricLock.isChecked = true
                        Toast.makeText(this, "Authentication required to disable lock", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        val showShortcuts = prefs.getBoolean(PREF_SHOW_HOMEPAGE_SHORTCUTS, true)
        sheetBinding.switchShowShortcuts.isChecked = showShortcuts
        sheetBinding.textCurrentEngine.text = "${currentSearchEngine.displayName} (Active)"

        sheetBinding.switchShowShortcuts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SHOW_HOMEPAGE_SHORTCUTS, isChecked).apply()
            activeWebView?.evaluateJavascript("if (typeof toggleHomepageShortcutsVisibility === 'function') toggleHomepageShortcutsVisibility($isChecked);", null)
            val msg = if (isChecked) "Shortcuts displayed on homepage" else "Shortcuts hidden from homepage"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        val isAiFilterOn = prefs.getBoolean(PREF_AI_FILTER, true)
        sheetBinding.switchAiFilter.isChecked = isAiFilterOn
        sheetBinding.badgeAiFilter.text = if (isAiFilterOn) "AI Filter Active" else "AI Filter Off"
        sheetBinding.badgeAiFilter.alpha = if (isShieldEnabled && isAiFilterOn) 1.0f else 0.4f

        sheetBinding.switchAiFilter.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_AI_FILTER, isChecked).apply()
            sheetBinding.badgeAiFilter.text = if (isChecked) "AI Filter Active" else "AI Filter Off"
            sheetBinding.badgeAiFilter.alpha = if (isShieldEnabled && isChecked) 1.0f else 0.4f
            activeWebView?.evaluateJavascript("if (typeof setAiFilterEnabled === 'function') setAiFilterEnabled($isChecked);", null)
            val msg = if (isChecked) "AI Overviews & Slop Filter Enabled" else "AI Overviews & Slop Filter Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        sheetBinding.badgeAiFilter.setOnClickListener {
            sheetBinding.switchAiFilter.isChecked = !sheetBinding.switchAiFilter.isChecked
        }

        sheetBinding.switchMenuShield.setOnCheckedChangeListener { _, isChecked ->
            if (isThirdPartyMediaService(activeWebView?.url)) {
                sheetBinding.switchMenuShield.isChecked = false
                Toast.makeText(this, "Filtering is disabled for this service", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            setShieldEnabledForCurrentSite(isChecked)
            sheetBinding.badgeSocialShield.alpha = if (isChecked) 1.0f else 0.4f
            val isAiOn = prefs.getBoolean(PREF_AI_FILTER, true)
            sheetBinding.badgeAiFilter.alpha = if (isChecked && isAiOn) 1.0f else 0.4f
            val msg = if (isChecked) "Fokk Shield Protection Enabled" else "Fokk Shield Paused"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            activeWebView?.reload()
        }

        sheetBinding.switchDesktop.setOnCheckedChangeListener { _, isChecked ->
            activeTab?.let { tab ->
                tab.isDesktop = isChecked
                tab.webView?.let { webView ->
                    applyDesktopMode(webView, tab.isDesktop)
                    webView.reload()
                }
            }
            dialog.dismiss()
        }

        sheetBinding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            if (!isThirdPartyMediaService(activeWebView?.url)) {
                injectDarkModeScript(activeWebView, isDarkMode)
            }
            dialog.dismiss()
        }

        sheetBinding.rowPrivateTab.setOnClickListener {
            authenticateForPrivateTabs {
                addNewTab("file:///android_asset/fokk_start.html", isPrivate = true)
                dialog.dismiss()
            }
        }

        sheetBinding.rowHistory.setOnClickListener {
            dialog.dismiss()
            showHistoryDialog()
        }

        if (isPrivateProfile) {
            sheetBinding.rowHistory.visibility = View.GONE
            sheetBinding.rowDownloads.visibility = View.GONE
            sheetBinding.rowPrivateTab.visibility = View.GONE
        }

        sheetBinding.rowDownloads.setOnClickListener {
            dialog.dismiss()
            showDownloadsDialog()
        }

        sheetBinding.rowTranslate.setOnClickListener {
            val currentUrl = activeTab?.url
            if (!currentUrl.isNullOrEmpty() && !currentUrl.contains("fokk_start.html")) {
                val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=" + Uri.encode(currentUrl)
                loadUrlInActiveWebView(translateUrl)
                Toast.makeText(this, "Translating page...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Open a website to translate", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        sheetBinding.rowReaderMode.setOnClickListener {
            dialog.dismiss()
            openReaderView()
        }

        sheetBinding.rowSearchEngine.setOnClickListener {
            dialog.dismiss()
            showSearchEngineDialog()
        }

        sheetBinding.rowExtensions.setOnClickListener {
            dialog.dismiss()
            showExtensionsManagerDialog()
        }

        sheetBinding.rowReload.setOnClickListener {
            activeWebView?.reload()
            dialog.dismiss()
        }

        sheetBinding.rowNewTab.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            dialog.dismiss()
        }

        sheetBinding.rowShare.setOnClickListener {
            val url = activeTab?.url ?: "fokk://home"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Page Link"))
            dialog.dismiss()
        }

        sheetBinding.rowWallpapers.setOnClickListener {
            dialog.dismiss()
            openWallpapersCustomizer()
        }

        sheetBinding.rowCustomizeShortcuts.setOnClickListener {
            dialog.dismiss()
            openShortcutsCustomizer()
        }

        sheetBinding.rowClearData.setOnClickListener {
            clearBrowsingData()
            dialog.dismiss()
        }

        sheetBinding.rowSitePrivacy.setOnClickListener {
            dialog.dismiss()
            showSitePrivacyDialog()
        }

        sheetBinding.rowReportSite.setOnClickListener {
            dialog.dismiss()
            reportBrokenSite()
        }

        sheetBinding.rowSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun clearBrowsingData() {
        for (tab in tabs) {
            tab.webView?.let { webView ->
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
            }
        }
        approvedWebMediaOrigins.clear()
        approvedLocationOrigins.clear()
        pendingWebPermission?.let { pending ->
            pendingWebPermission = null
            runCatching { pending.request.deny() }
        }
        pendingLocationPermission?.let { pending ->
            pendingLocationPermission = null
            runCatching { pending.callback.invoke(pending.origin, false, false) }
        }
        if (!isIsolatedPrivateProfile) {
            localPrefs.edit().remove(PREF_BROWSING_HISTORY).apply()
        }

        val cookieManager = CookieManager.getInstance()
        val finishClear: () -> Unit = {
            runOnUiThread {
                runCatching { WebStorage.getInstance().deleteAllData() }
                runCatching {
                    WebViewDatabase.getInstance(this).apply {
                        clearFormData()
                        clearHttpAuthUsernamePassword()
                        clearUsernamePassword()
                    }
                }
                runCatching { cookieManager.flush() }
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(
                        this,
                        if (isIsolatedPrivateProfile) {
                            "Private site data, form data and cache cleared. System downloads are separate."
                        } else {
                            "History, cookies, site storage, form data and cache cleared. Saved pages and downloads were kept."
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        runCatching {
            cookieManager.removeAllCookies { finishClear() }
        }.onFailure {
            Log.e(TAG, "Could not start cookie cleanup", it)
            finishClear()
        }
    }

    private fun showDownloadsDialog() {
        if (isIsolatedPrivateProfile) {
            Toast.makeText(this, "Download history is outside the private browser", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query())
        data class DownloadEntry(val id: Long, val url: String, val title: String, val status: Int, val label: String)
        val entries = mutableListOf<DownloadEntry>()
        cursor?.use {
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val uriCol = it.getColumnIndex(DownloadManager.COLUMN_URI)
            while (it.moveToNext() && entries.size < 20) {
                val title = it.getString(titleCol) ?: "Download"
                val rawStatus = it.getInt(statusCol)
                val status = when (rawStatus) {
                    DownloadManager.STATUS_RUNNING -> "Downloading"
                    DownloadManager.STATUS_PENDING -> "Queued"
                    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Paused"
                }
                entries.add(DownloadEntry(it.getLong(idCol), it.getString(uriCol) ?: "", title, rawStatus, "$title — $status"))
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Downloads")
            .setItems(if (entries.isEmpty()) arrayOf("No downloads yet") else entries.map { it.label }.toTypedArray()) { _, which ->
                val entry = entries.getOrNull(which) ?: return@setItems
                when (entry.status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                        manager.remove(entry.id)
                        Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show()
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Toast.makeText(this, "Download failed. Reopen the page and retry the download from its link.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setPositiveButton("Open system Downloads") { _, _ -> openSystemDownloads() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSitePrivacyDialog() {
        flushFilteredRequestCounts()
        val url = activeTab?.takeIf { it.isOfflineReader }?.url ?: activeWebView?.url ?: activeTab?.url ?: return
        val host = Uri.parse(url).host ?: return
        val shield = if (isShieldEnabledFor(url)) "On" else "Off"
        val privateTabDetails = if (activeTab?.isPrivate == true) {
            "\nPrivate tab: uses a separate WebView data profile and is omitted from Fokk history and session restore. Android downloads and items you share may remain outside this profile."
        } else ""
        val currentTab = activeTab
        val filteredHosts = currentTab?.filteredRequestHosts
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(8)
            ?.joinToString { "${it.key} (${it.value})" }
            .orEmpty()
        val observedHosts = currentTab?.thirdPartyRequestHosts
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(8)
            ?.joinToString { "${it.key} (${it.value})" }
            .orEmpty()
        val observedFiltering = buildString {
            append("WebView observed ${currentTab?.thirdPartyRequests ?: 0} cross-site resource requests on this page, including requests Fokk filtered.")
            if (observedHosts.isNotEmpty()) append("\nThird-party hosts: $observedHosts.")
            append("\nFokk filtered ${currentTab?.filteredRequests ?: 0} requests.")
            if (filteredHosts.isNotEmpty()) append("\nFiltered hosts: $filteredHosts.")
            append("\nThis is a partial view: WebView may omit requests, and site-boundary matching is approximate. These counts are not a full tracker or ad audit; unlisted and server-side ads may remain.")
        }
        AlertDialog.Builder(this)
            .setTitle("Privacy: $host")
            .setMessage("$observedFiltering\n\nShield: $shield\nConnection: ${if (Uri.parse(url).scheme.equals("https", true)) "HTTPS" else "Not HTTPS"}\nThird-party cookies: blocked by default; allowed on supported media services for playback compatibility. First-party cookies remain enabled for sign-in and site functions.\nMixed content: active HTTP content blocked on HTTPS pages\nCamera, microphone and location: ask before use, then require Android permission.$privateTabDetails")
            .setPositiveButton("Clear web storage") { _, _ ->
                val origins = getHttpStorageOrigins(url)
                origins.forEach { origin ->
                    runCatching { WebStorage.getInstance().deleteOrigin(origin) }
                }
                activeWebView?.clearFormData()
                val message = if (origins.isEmpty()) {
                    "Could not identify this site's web storage origin"
                } else {
                    "Web storage cleared for $host. Cookies and saved form suggestions remain."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openReaderView() {
        val webView = activeWebView ?: return
        if (isThirdPartyMediaService(webView.url)) {
            Toast.makeText(this, "Reader View is for article pages", Toast.LENGTH_SHORT).show()
            return
        }
        val script = """(function(){
            if(document.getElementById('__fokk_reader_root')) return 'already';
            var source=document.querySelector('article')||document.querySelector('main')||document.body;
            var content=source.cloneNode(true);
            content.querySelectorAll('script,style,nav,header,footer,aside,form,button,[role="navigation"],[role="complementary"],iframe,video,svg').forEach(function(n){n.remove()});
            var text=(content.innerText||'').trim();
            if(text.length<180) return 'unsupported';
            var root=document.createElement('div'); root.id='__fokk_reader_root';
            root.innerHTML='<div id="__fokk_reader_bar"><b>Reader View</b><span><button id="__fokk_theme" aria-label="Toggle reading theme">◐</button><button id="__fokk_smaller" aria-label="Smaller text">A−</button><button id="__fokk_larger" aria-label="Larger text">A+</button><button id="__fokk_exit">Done</button></span></div><article id="__fokk_reader_article"><h1></h1><div id="__fokk_reader_content"></div></article>';
            root.querySelector('h1').textContent=(document.querySelector('h1')||{}).innerText||document.title||'Article';
            root.querySelector('#__fokk_reader_content').appendChild(content);
            var style=document.createElement('style'); style.id='__fokk_reader_style';
            style.textContent='html,body{margin:0!important;padding:0!important;background:#f7f3e9!important;color:#222!important}body>*:not(#__fokk_reader_root){display:none!important}#__fokk_reader_root{position:fixed;inset:0;z-index:2147483647;overflow:auto;background:#f7f3e9;color:#222;font:19px/1.75 Georgia,serif;padding:0 22px 48px;box-sizing:border-box}#__fokk_reader_bar{position:sticky;top:0;display:flex;justify-content:space-between;align-items:center;background:#f7f3e9;border-bottom:1px solid #ddd;padding:12px 0;font:14px/1.2 system-ui,sans-serif}#__fokk_reader_bar button{border:0;border-radius:18px;background:#e6e1d6;color:#222;padding:9px 12px;margin-left:6px;font:inherit}#__fokk_reader_article{max-width:700px;margin:34px auto}#__fokk_reader_article h1{font:700 32px/1.2 system-ui,sans-serif;margin:0 0 24px}#__fokk_reader_content{overflow-wrap:anywhere}#__fokk_reader_content img{max-width:100%;height:auto}#__fokk_reader_content a{color:#245fa8}#__fokk_reader_content p{margin:0 0 1.25em}';
            document.head.appendChild(style); document.body.appendChild(root);
            var size=19; root.querySelector('#__fokk_larger').onclick=function(){size=Math.min(30,size+2);root.style.fontSize=size+'px'};
            root.querySelector('#__fokk_smaller').onclick=function(){size=Math.max(14,size-2);root.style.fontSize=size+'px'};
            root.querySelector('#__fokk_theme').onclick=function(){var dark=root.dataset.theme!=='dark';root.dataset.theme=dark?'dark':'light';root.style.background=dark?'#17191d':'#f7f3e9';root.style.color=dark?'#e8e5dd':'#222';root.querySelector('#__fokk_reader_bar').style.background=root.style.background;root.querySelector('#__fokk_reader_bar').style.color=root.style.color;root.querySelector('#__fokk_reader_bar').style.borderColor=dark?'#373b42':'#ddd';root.querySelectorAll('a').forEach(function(a){a.style.color=dark?'#8ab4f8':'#245fa8'})};
            root.querySelector('#__fokk_exit').onclick=function(){location.reload()};
            return 'ok';
        })();""".trimIndent()
        webView.evaluateJavascript(script) { result ->
            when (result?.trim('"')) {
                "ok", "already" -> Toast.makeText(this, "Reader View opened · use Done to return", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this, "This page does not appear to contain a readable article", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun reportBrokenSite() {
        val url = activeWebView?.url ?: activeTab?.url ?: return
        val details = "Fokk Browser broken-site report\nURL: $url\nShield: ${if (isShieldEnabledFor(url)) "on" else "off"}\nAndroid: ${Build.VERSION.RELEASE}\nApp: ${AppUpdateManager.getCurrentVersionName(this)}\n\nDescribe what did not work:"
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Broken site report", details))
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, details)
        }, "Send broken-site report"))
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
        activeWebView?.resumeTimers()

        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!inPip) {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            if (!isThirdPartyMediaService(activeWebView?.url)) activeWebView?.evaluateJavascript(
                """(function() {
                    document.documentElement.classList.remove('fokk-pip-active');
                    if (typeof window.__fokk_exit_pip_view === 'function') window.__fokk_exit_pip_view();
                    window.dispatchEvent(new Event('resize'));
                })();""".trimIndent(),
                null
            )
            activeWebView?.requestLayout()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isStartPage = activeTab?.url?.contains("fokk_start.html") == true
        val recentMedia = (System.currentTimeMillis() - lastMediaPlayingTime) < 5000L
        if (!isStartPage && (isMediaPlaying || recentMedia || customView != null)) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))

                // Only set sourceRectHint if customView is active and wide (landscape); otherwise let system scale smoothly without blank space
                if (customView != null) {
                    val rect = android.graphics.Rect()
                    customView?.getGlobalVisibleRect(rect)
                    if (!rect.isEmpty && rect.width() > rect.height()) {
                        builder.setSourceRectHint(rect)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                enterPictureInPictureMode(builder.build())
            } catch (e: Exception) {
                Toast.makeText(this, "Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save the latest navigation before Android backgrounds or reclaims this browser process.
        persistSession()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            activeWebView?.onResume()
            return
        }
        // Do not pause the WebView here: Android can call onPause before it reports the
        // Picture-in-Picture transition, which would leave an audio-only PiP window.
        CookieManager.getInstance().flush()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.topBar.visibility = View.GONE
            binding.bottomNav.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            binding.cardSuggestions.visibility = View.GONE
            activeWebView?.onResume()
            activeWebView?.resumeTimers()
            if (!isThirdPartyMediaService(activeWebView?.url)) {
                activeWebView?.evaluateJavascript("if (typeof window.__fokk_enter_pip_view === 'function') window.__fokk_enter_pip_view();", null)
            }
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            if (!isThirdPartyMediaService(activeWebView?.url)) {
                activeWebView?.evaluateJavascript("if (typeof window.__fokk_exit_pip_view === 'function') window.__fokk_exit_pip_view();", null)
            }
            activeWebView?.requestLayout()
        }
    }

    fun openShieldDashboard() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.cardSuggestions.visibility == View.VISIBLE) {
            binding.cardSuggestions.visibility = View.GONE
            return
        }
        if (customView != null) {
            hideCustomView()
        } else if (activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        } else if (isIsolatedPrivateProfile) {
            closePrivateBrowser()
        } else {
            openShieldDashboard()
        }
    }

    override fun onDestroy() {
        suggestionJob?.cancel()
        pendingWebPermission?.request?.deny()
        pendingWebPermission = null
        pendingLocationPermission?.let { it.callback.invoke(it.origin, false, false) }
        pendingLocationPermission = null
        mainHandler.removeCallbacks(filteredCountFlushRunnable)
        flushFilteredRequestCounts()
        for (tab in tabs) {
            tab.webView?.let { webView ->
                binding.tabContainer.removeView(webView)
                webView.destroy()
            }
        }
        tabs.clear()
        // Never let a failed isolated-profile setup fall through to the default WebView
        // profile: cleanup here would otherwise erase normal browsing data.
        if (isIsolatedPrivateProfile && privateProfileConfigurationReady &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isFinishing && !privateProfileDataClearedOnClose
        ) {
            clearPrivateProfileData()
        }
        super.onDestroy()
    }

    companion object {
        private val THIRD_PARTY_MEDIA_SERVICE_DOMAINS = setOf(
            "youtube.com", "youtube-nocookie.com", "youtu.be",
            "spotify.com", "spotify.link", "scdn.co", "spotifycdn.com",
            "instagram.com", "instagr.am"
        )
        private val STREAMING_MEDIA_DOMAINS = setOf(
            "googlevideo.com", "ytimg.com", "ggpht.com", "mux.com", "bitmovin.com",
            "peer5.com", "streamroot.io", "conviva.com", "nicepeopleatwork.com"
        )
        private val IPV4_ADDRESS_REGEX = Regex("\\d{1,3}(\\.\\d{1,3}){3}")
        // Includes India suffixes from the Mozilla Public Suffix List snapshot dated 2026-10-28
        // (MPL-2.0), plus common public and shared-hosting boundaries. This is not a full PSL.
        private val MULTI_LABEL_PUBLIC_SUFFIXES = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
            "co.nz", "com.br", "com.cn", "com.sg", "com.tr", "co.in", "firm.in",
            "net.in", "org.in", "com.mx", "co.za", "co.jp", "ne.jp", "com.tw",
            "github.io", "gitlab.io", "pages.dev", "workers.dev", "appspot.com",
            "blogspot.com", "cloudfront.net", "firebaseapp.com", "web.app", "vercel.app",
            "netlify.app", "herokuapp.com", "azurewebsites.net", "wordpress.com",
            "wixsite.com", "tumblr.com",
            "5g.in", "6g.in", "ac.in", "aero.in", "ai.in", "alumni.in", "am.in",
            "bank.in", "bihar.in", "biz.in", "business.in", "ca.in", "cn.in", "com.in",
            "coop.in", "cs.in", "delhi.in", "dr.in", "edu.in", "er.in", "fin.in",
            "gen.in", "gov.in", "gujarat.in", "ind.in", "info.in", "int.in", "internet.in",
            "io.in", "me.in", "mil.in", "nic.in", "pg.in", "post.in", "pro.in", "res.in",
            "school.in", "travel.in", "tv.in", "ub.in", "uk.in", "up.in", "us.in"
        )
        private val TRACKING_QUERY_PARAMETERS = setOf(
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "mc_eid",
            "igshid", "yclid", "twclid", "ttclid", "li_fat_id", "vero_id", "vero_conv"
        )
        private val AUTHENTICATION_OR_SIGNATURE_PARAMETERS = setOf(
            "sig", "signature", "awsaccesskeyid", "token", "access_token", "refresh_token",
            "id_token", "auth", "authorization", "jwt", "hmac", "policy", "key-pair-id",
            "expires", "exp", "code", "state", "nonce", "oauth_token", "oauth_signature",
            "se", "sp", "sv", "st", "spr", "sr", "hdnts", "hdnea", "hdntl"
        )
        private val AUTHENTICATION_OR_SIGNATURE_PREFIXES = setOf(
            "x-amz-", "x-goog-", "x-oss-", "x-ms-", "oauth_"
        )
        private const val OFFLINE_PAGE_BASE_URL = "https://offline.fokk.invalid/"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_OPEN_ACTION = "extra_open_action"
        private const val TAG = "FokkBrowser"
        private const val PREFS_NAME = "fokk_browser_settings_v2"
        private const val LEGACY_PREFS_NAME = "fokk_browser_prefs"
        private const val LOCAL_PREFS_NAME = "fokk_browser_local"
        private const val PREF_SEARCH_ENGINE = "fokk_search_engine"
        private const val PREF_REMOTE_SUGGESTIONS = "fokk_remote_search_suggestions"
        private const val PREF_STRICT_ADBLOCK = "filter_strict_adblock"
        private const val PREF_SOCIAL_WIDGETS = "filter_social_trackers"
        private const val PREF_CUSTOM_JS = "custom_userscript"
        private const val PREF_CUSTOM_CSS = "custom_usercss"
        private const val PREF_BROWSING_HISTORY = "key_browsing_history_v2"
        private const val PREF_SESSION_TABS = "key_session_tabs_v1"
        private const val PREF_SESSION_ACTIVE_TAB = "key_session_active_tab_v1"
        private const val MAX_RESTORED_TABS = 12
        private const val MAX_REPORTED_HOSTS = 12
        private const val MAX_DOWNLOAD_FILENAME_LENGTH = 180
        private const val MEDIA_RECENT_ACTIVITY_WINDOW_MS = 5_000L
        private const val PREF_SHIELD_DISABLED_SITES = "key_shield_disabled_sites"
        private const val PREF_SHOW_HOMEPAGE_SHORTCUTS = "fokk_show_shortcuts"
        private const val PREF_BIOMETRIC_PRIVATE_TABS = "fokk_biometric_private_tabs"
        private const val PREF_AI_FILTER = "fokk_ai_filter_enabled"
        private val LOCAL_BROWSER_DATA_KEYS = setOf(
            PREF_BROWSING_HISTORY,
            PREF_SESSION_TABS,
            PREF_SESSION_ACTIVE_TAB,
            PREF_SHIELD_DISABLED_SITES,
            OfflinePageStore.PREF_SAVED_PAGES
        )
    }
}
