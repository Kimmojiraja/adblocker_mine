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
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
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

class MediaHubActivity : AppCompatActivity() {

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
        var blockedAds: Int = 0,
        var isDesktop: Boolean = false,
        val isPrivate: Boolean = false,
        val webView: WebView
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

    private lateinit var binding: ActivityMediaHubBinding
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var previousSystemUiVisibility: Int? = null
    private var previousRequestedOrientation: Int? = null
    private var previousWindowWasFullscreen: Boolean? = null
    private var browserChromeHidden = false
    private var injectionScript = ""
    private var pageBlockedCount = 0

    private val tabs = mutableListOf<BrowserTab>()
    private val pageUrlsByWebView = Collections.synchronizedMap(WeakHashMap<WebView, String>())
    private val pendingFilteredCounts = WeakHashMap<WebView, Int>()
    private val approvedWebMediaOrigins = mutableSetOf<String>()
    private val approvedLocationOrigins = mutableSetOf<String>()
    private val filteredCountLock = Any()
    private val pendingFilteredCountFlush = AtomicBoolean(false)
    private var pendingFilteredCountTotal = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val filteredCountFlushRunnable = Runnable { flushFilteredRequestCounts() }
    private var activeTabIndex = 0
    private var isShieldEnabled = true
    private var isDarkMode = false
    private var showingPrivateTabs = false
    private var isMediaPlaying = false
    private var isBiometricAuthenticatedForSession = false

    private var lastMediaPlayingTime = 0L

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PendingWebPermission? = null
    private var pendingLocationPermission: PendingLocationPermission? = null

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
            val historyJson = prefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (initialUrl.isNullOrBlank()) restoreLastSession() else addNewTab(initialUrl, isPrivate = false)
        handleOpenAction(intent)

        AppUpdateManager.checkForUpdates(this, manualCheck = false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenAction(intent)
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

    fun onPageAdsBlocked(count: Int) {
        // Keep page and app totals in sync with the cosmetic filter callback.
        pageBlockedCount += count
        activeTab?.let { it.blockedAds += count }
        AdShieldApp.instance.incrementBlockedAds(count)
    }

    private fun recordFilteredRequest(webView: WebView?) {
        synchronized(filteredCountLock) {
            pendingFilteredCountTotal += 1
            if (webView != null) {
                pendingFilteredCounts[webView] = (pendingFilteredCounts[webView] ?: 0) + 1
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
        val snapshot: Map<WebView, Int>
        val total: Int
        synchronized(filteredCountLock) {
            snapshot = pendingFilteredCounts.toMap()
            total = pendingFilteredCountTotal
            pendingFilteredCounts.clear()
            pendingFilteredCountTotal = 0
        }

        snapshot.forEach { (webView, count) ->
            tabs.firstOrNull { it.webView === webView }?.let { tab ->
                tab.blockedAds += count
                if (tab.webView === activeWebView) pageBlockedCount += count
            }
        }
        if (total > 0) AdShieldApp.instance.incrementBlockedAds(total)

        pendingFilteredCountFlush.set(false)
        val hasMorePending = synchronized(filteredCountLock) { pendingFilteredCountTotal > 0 }
        if (hasMorePending) scheduleFilteredCountFlush()
    }

    class WebAppInterface(private val activity: MediaHubActivity) {
        @JavascriptInterface
        fun onAdsBlocked(count: Int) {
            activity.runOnUiThread {
                activity.onPageAdsBlocked(count)
            }
        }

        @JavascriptInterface
        fun getStats(): String {
            val total = AdShieldApp.instance.getBlockedAdsCount()
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
            val historyJson = prefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
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
            prefs.edit().putString(PREF_BROWSING_HISTORY, newArr.toString()).apply()
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
        val tabIsPrivate = tabs.firstOrNull { it.webView === webView }?.isPrivate == true
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            webView,
            !tabIsPrivate && isThirdPartyMediaService(url)
        )
        pageUrlsByWebView[webView] = url
        setBridgeEnabled(webView, url)
        webView.loadUrl(url)
    }

    private fun loadUrlInActiveWebView(url: String) = loadUrlInWebView(activeWebView, url)

    private fun isWebUrl(rawUrl: String): Boolean = runCatching {
        val scheme = Uri.parse(rawUrl).scheme?.lowercase(Locale.ROOT)
        scheme == "http" || scheme == "https"
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
                binding.topBar.translationY = -binding.topBar.height.toFloat()
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
                .translationY(-binding.topBar.height.toFloat())
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
            try {
                val downloadUri = Uri.parse(downloadUrl)
                if (downloadUri.scheme != "https" && downloadUri.scheme != "http") {
                    Toast.makeText(this, "This download link is not supported", Toast.LENGTH_SHORT).show()
                    return@setDownloadListener
                }
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading via Fokk Browser...")
                    val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading file to Downloads folder...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                } catch (ex: Exception) {
                    Toast.makeText(this, "Cannot download this file", Toast.LENGTH_SHORT).show()
                }
            }
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
                view?.let {
                    cancelPendingWebPermission(it)
                    cancelPendingLocationPermission(it)
                }
                if (view != null && pageUrl != null) pageUrlsByWebView[view] = pageUrl
                if (view != null) {
                    val tabIsPrivate = tabs.firstOrNull { it.webView === view }?.isPrivate == true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(
                        view,
                        !tabIsPrivate && isThirdPartyMediaService(pageUrl)
                    )
                }
                setBridgeEnabled(view, pageUrl)
                if (view == activeWebView) {
                    setBrowserChromeVisible(true)
                    isShieldEnabled = isShieldEnabledFor(pageUrl)
                    binding.progressBar.visibility = View.VISIBLE
                    pageBlockedCount = 0
                    // page reset
                    if (pageUrl != null && !binding.urlInput.hasFocus()) {
                        updateUrlBar(pageUrl, null)
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
                    blockedAds = oldTab.blockedAds
                    isDesktop = oldTab.isDesktop
                }
                tabs[tabIndex] = replacement
                applyDesktopMode(replacement.webView, replacement.isDesktop)
                if (wasActive) switchToTab(tabIndex) else replacement.webView.visibility = View.GONE
                loadUrlInWebView(replacement.webView, crashedUrl)
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
                injectAdblockScript(view, pageUrl)
                if (isDarkMode && !isThirdPartyMediaService(pageUrl)) injectDarkModeScript(view, true)
                val loadedTab = tabs.firstOrNull { it.webView == view }
                pageUrl?.let { loadedUrl ->
                    loadedTab?.url = loadedUrl
                    if (loadedUrl.contains("fokk_start.html")) {
                        loadedTab?.title = "New Tab"
                    } else if (loadedTab != null) {
                        recordHistory(view?.title ?: loadedUrl, loadedUrl, loadedTab.isPrivate)
                    }
                    if (view == activeWebView) updateUrlBar(loadedUrl, view?.title)
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
                if (isThirdPartyMediaService(pageUrl) || isThirdPartyMediaService(requestUrl) ||
                    !isShieldEnabledFor(pageUrl ?: requestUrl)) {
                    return super.shouldInterceptRequest(view, request)
                }

                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                val pageHost = pageUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                if (pageHost != null && isSameSite(pageHost, host)) {
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
                    recordFilteredRequest(view)
                    val mimeType = if (urlStr.endsWith(".js") || urlStr.contains(".js?")) "application/javascript" else "text/plain"
                    val emptyPayload = if (mimeType == "application/javascript") "/* blocked by fokk shield */" else ""
                    return WebResourceResponse(
                        mimeType,
                        "UTF-8",
                        200,
                        "OK",
                        mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                            "Access-Control-Allow-Headers" to "*"
                        ),
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
                applyDesktopMode(newTab.webView, newTab.isDesktop)
                transport.webView = newTab.webView
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

    private fun addNewTab(url: String = "file:///android_asset/fokk_start.html", isPrivate: Boolean = false) {
        val tab = createTab(url, isPrivate)
        tabs.add(tab)
        switchToTab(tabs.size - 1)
        loadUrlInWebView(tab.webView, url)
        updateTabCounter()
        persistSession()
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        setBrowserChromeVisible(true)
        activeTabIndex = index
        for (i in tabs.indices) {
            tabs[i].webView.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        val current = tabs[index]
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

        pageBlockedCount = current.blockedAds
        updateTabCounter()
        persistSession()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        cancelPendingWebPermission(removed.webView)
        cancelPendingLocationPermission(removed.webView)
        binding.tabContainer.removeView(removed.webView)
        pageUrlsByWebView.remove(removed.webView)
        removed.webView.destroy()

        if (tabs.isEmpty()) {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
        } else {
            val newIndex = if (activeTabIndex >= tabs.size) tabs.size - 1 else activeTabIndex
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
        val disabledSites = prefs.getStringSet(PREF_SHIELD_DISABLED_SITES, emptySet()).orEmpty()
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
        val disabled = prefs.getStringSet(PREF_SHIELD_DISABLED_SITES, emptySet()).orEmpty().toMutableSet()
        if (enabled) {
            disabled.remove(host)
            disabled.remove(site)
        } else {
            disabled.add(site)
        }
        prefs.edit().putStringSet(PREF_SHIELD_DISABLED_SITES, disabled).apply()
        isShieldEnabled = enabled
    }

    private fun persistSession() {
        val state = JSONArray()
        tabs.filter { !it.isPrivate }.take(12).forEach { tab ->
            state.put(JSONObject().put("url", tab.webView.url ?: tab.url).put("desktop", tab.isDesktop))
        }
        prefs.edit().putString(PREF_SESSION_TABS, state.toString()).apply()
    }

    private fun restoreLastSession() {
        val saved = prefs.getString(PREF_SESSION_TABS, null)
        try {
            val items = JSONArray(saved)
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val url = item.optString("url")
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:///android_asset/")) {
                    addNewTab(url, isPrivate = false)
                    activeTab?.isDesktop = item.optBoolean("desktop", false)
                    activeTab?.let { applyDesktopMode(it.webView, it.isDesktop) }
                }
            }
        } catch (_: Exception) { }
        if (tabs.isEmpty()) addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
    }

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
        if (!isThirdPartyMediaService(url)) {
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
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetHistoryBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        fun loadList(filter: String = "") {
            sheetBinding.layoutHistoryList.removeAllViews()
            try {
                val historyJson = prefs.getString(PREF_BROWSING_HISTORY, "[]") ?: "[]"
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
            prefs.edit().remove(PREF_BROWSING_HISTORY).apply()
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
                showingPrivateTabs = true
                updateTabsView()
            }
        }

        sheetBinding.btnNewTabSheet.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            showingPrivateTabs = false
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
                binding.tabContainer.removeView(tab.webView)
                tab.webView.destroy()
                tabs.remove(tab)
            }
            if (tabs.isEmpty()) {
                addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            } else {
                switchToTab(0)
            }
            dialog.dismiss()
        }

        updateTabsView()
        dialog.show()
    }

    private fun saveCurrentPage() {
        val currentUrl = activeWebView?.url ?: return
        if (currentUrl.contains("fokk_start.html")) {
            Toast.makeText(this, "Start page cannot be saved", Toast.LENGTH_SHORT).show()
            return
        }
        val currentTitle = activeWebView?.title?.ifBlank { currentUrl } ?: currentUrl
        try {
            val savedJson = prefs.getString("key_saved_offline_pages_v35", "[]") ?: "[]"
            val jsonArr = org.json.JSONArray(savedJson)
            val newArr = org.json.JSONArray()
            newArr.put(org.json.JSONObject().apply {
                put("title", currentTitle)
                put("url", currentUrl)
                put("time", System.currentTimeMillis())
            })
            for (i in 0 until minOf(jsonArr.length(), 40)) {
                val item = jsonArr.getJSONObject(i)
                if (item.optString("url") != currentUrl) {
                    newArr.put(item)
                }
            }
            prefs.edit().putString("key_saved_offline_pages_v35", newArr.toString()).apply()

            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val filename = "page_${System.currentTimeMillis()}.mhtml"
            val archiveFile = File(dir, filename)
            activeWebView?.saveWebArchive(archiveFile.absolutePath, false) { path ->
                if (path != null) {
                    Toast.makeText(this, "Page saved for offline reading!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Page saved to Bookmarks!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Saved to Bookmarks!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSavedPagesDialog() {
        try {
            val savedJson = prefs.getString("key_saved_offline_pages_v35", "[]") ?: "[]"
            val jsonArr = org.json.JSONArray(savedJson)
            if (jsonArr.length() == 0) {
                Toast.makeText(this, "No saved pages yet. Tap Save Page on any website!", Toast.LENGTH_LONG).show()
                return
            }
            val titles = Array(jsonArr.length()) { i ->
                val item = jsonArr.getJSONObject(i)
                item.optString("title")
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Saved Pages & Offline Reading")
                .setItems(titles) { _, which ->
                    val item = jsonArr.getJSONObject(which)
                    val url = item.optString("url")
                    if (url.isNotBlank()) {
                        loadUrlInActiveWebView(url)
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to load saved pages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBrowserMenuDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetBrowserMenuBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val isStartPage = activeWebView?.url?.contains("fokk_start.html") == true || activeWebView?.url.isNullOrBlank()

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
        sheetBinding.btnMenuSave.isEnabled = !isStartPage
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

        sheetBinding.textMenuBlockedInfo.text = "$pageBlockedCount requests filtered on this page"
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
                applyDesktopMode(tab.webView, tab.isDesktop)
                tab.webView.reload()
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
            tab.webView.clearCache(true)
            tab.webView.clearHistory()
            tab.webView.clearFormData()
        }
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
        prefs.edit().remove(PREF_BROWSING_HISTORY).apply()
        Toast.makeText(this, "Browsing history, cache & cookies cleared!", Toast.LENGTH_SHORT).show()
    }

    private fun showDownloadsDialog() {
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
        val url = activeWebView?.url ?: activeTab?.url ?: return
        val host = Uri.parse(url).host ?: return
        val shield = if (isShieldEnabledFor(url)) "On" else "Off"
        AlertDialog.Builder(this)
            .setTitle("Privacy: $host")
            .setMessage("Shield: $shield\nConnection: ${if (Uri.parse(url).scheme.equals("https", true)) "HTTPS" else "Not HTTPS"}\nThird-party cookies: blocked by default; allowed on supported media services for playback compatibility. First-party cookies remain enabled for sign-in and site functions.\nMixed content: active HTTP content blocked on HTTPS pages\nCamera, microphone and location: ask before use, then require Android permission.")
            .setPositiveButton("Clear site storage") { _, _ ->
                WebStorage.getInstance().deleteOrigin("https://$host")
                WebStorage.getInstance().deleteOrigin("http://$host")
                activeWebView?.clearFormData()
                Toast.makeText(this, "Site storage cleared for $host", Toast.LENGTH_SHORT).show()
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
            binding.tabContainer.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
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
        private val MULTI_LABEL_PUBLIC_SUFFIXES = setOf(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au",
            "co.nz", "com.br", "com.cn", "com.sg", "com.tr", "co.in", "firm.in",
            "net.in", "org.in", "com.mx", "co.za", "co.jp", "ne.jp", "com.tw"
        )

        const val EXTRA_URL = "extra_url"
        const val EXTRA_OPEN_ACTION = "extra_open_action"
        private const val TAG = "FokkBrowser"
        private const val PREFS_NAME = "fokk_browser_prefs"
        private const val PREF_SEARCH_ENGINE = "fokk_search_engine"
        private const val PREF_STRICT_ADBLOCK = "filter_strict_adblock"
        private const val PREF_SOCIAL_WIDGETS = "filter_social_trackers"
        private const val PREF_CUSTOM_JS = "custom_userscript"
        private const val PREF_CUSTOM_CSS = "custom_usercss"
        private const val PREF_BROWSING_HISTORY = "key_browsing_history_v2"
        private const val PREF_SESSION_TABS = "key_session_tabs_v1"
        private const val PREF_SHIELD_DISABLED_SITES = "key_shield_disabled_sites"
        private const val PREF_SHOW_HOMEPAGE_SHORTCUTS = "fokk_show_shortcuts"
        private const val PREF_BIOMETRIC_PRIVATE_TABS = "fokk_biometric_private_tabs"
        private const val PREF_AI_FILTER = "fokk_ai_filter_enabled"
    }
}
