package com.adshield.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Base64
import androidx.activity.result.contract.ActivityResultContracts
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.adshield.app.AdShieldApp
import com.adshield.app.R
import com.adshield.app.databinding.ActivityMediaHubBinding
import com.adshield.app.databinding.ItemTabCardBinding
import com.adshield.app.databinding.SheetBrowserMenuBinding
import com.adshield.app.databinding.SheetExtensionsManagerBinding
import com.adshield.app.databinding.SheetHistoryBinding
import com.adshield.app.databinding.SheetTabSwitcherBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MediaHubActivity : AppCompatActivity() {

    enum class SearchEngine(val displayName: String, val searchUrl: String) {
        BRAVE("Brave Search", "https://search.brave.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
        GOOGLE("Google Clean", "https://www.google.com/search?q=")
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

    private lateinit var binding: ActivityMediaHubBinding
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var injectionScript = ""
    private var pageBlockedCount = 0

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private var isShieldEnabled = true
    private var isDarkMode = false
    private var showingPrivateTabs = false
    private var isMediaPlaying = false
    private var lastMediaPlayingTime = 0L

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

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

    private var currentSearchEngine: SearchEngine = SearchEngine.BRAVE
    private var suggestionJob: Job? = null

    private val activeTab: BrowserTab?
        get() = tabs.getOrNull(activeTabIndex)

    private val activeWebView: WebView?
        get() = activeTab?.webView

    private val cleanMobileUa: String by lazy {
        val defaultUa = WebSettings.getDefaultUserAgent(this)
        defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
    }

    private val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedEngineName = prefs.getString(PREF_SEARCH_ENGINE, SearchEngine.BRAVE.name)
        currentSearchEngine = try {
            SearchEngine.valueOf(savedEngineName ?: SearchEngine.BRAVE.name)
        } catch (e: Exception) {
            SearchEngine.BRAVE
        }

        loadInjectionScript()
        setupControls()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "file:///android_asset/fokk_start.html"
        addNewTab(initialUrl, isPrivate = false)
        handleOpenAction(intent)
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
        pageBlockedCount += count
        activeTab?.let { it.blockedAds += count }
        AdShieldApp.instance.incrementBlockedAds(count)
        binding.badgeBlocked.visibility = View.VISIBLE
        binding.textPageBlockedCount.text = pageBlockedCount.toString()
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
            val total = AdShieldApp.instance.getTotalAdsEliminated().coerceAtLeast(1420L)
            val trackers = (total * 0.65).toLong()
            val savedMb = (total * 0.035).toLong() + 12L
            return """{"blocked":$total,"trackers":$trackers,"savedMb":$savedMb}"""
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

    private fun recordHistory(title: String, url: String) {
        if (activeTab?.isPrivate == true) return
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url: String, isPrivate: Boolean = false): BrowserTab {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // Block third-party tracking cookies globally for privacy
        cookieManager.setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = cleanMobileUa
        }

        webView.addJavascriptInterface(WebAppInterface(this), "FokkShieldBridge")

        // Intercept downloads cleanly
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            try {
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

        // Pre-inject content blocker at document start if supported
        if (injectionScript.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, injectionScript, setOf("*"))
            } catch (e: Exception) {
                Log.w(TAG, "Document start script error", e)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                super.onPageStarted(view, pageUrl, favicon)
                if (view == activeWebView) {
                    binding.progressBar.visibility = View.VISIBLE
                    pageBlockedCount = 0
                    binding.badgeBlocked.visibility = View.GONE
                    if (pageUrl != null && !binding.urlInput.hasFocus()) {
                        if (pageUrl.contains("fokk_start.html")) {
                            binding.urlInput.setText("")
                            binding.urlInput.hint = "Search with ${currentSearchEngine.displayName} or enter URL..."
                        } else {
                            binding.urlInput.setText(pageUrl)
                        }
                    }
                }
                injectAdblockScript(view)
                if (isDarkMode) injectDarkModeScript(view, true)
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                if (view == activeWebView) {
                    binding.progressBar.visibility = View.GONE
                }
                injectAdblockScript(view)
                pageUrl?.let {
                    activeTab?.url = it
                    if (it.contains("fokk_start.html")) {
                        activeTab?.title = "New Tab"
                    } else {
                        recordHistory(view?.title ?: it, it)
                    }
                }
                view?.title?.let {
                    if (it.isNotEmpty() && activeTab?.url?.contains("fokk_start.html") != true) {
                        activeTab?.title = it
                    }
                }

                // If tab is in desktop mode, inject desktop viewport meta override
                if (activeTab?.isDesktop == true) {
                    val js = "javascript:(function(){let vp=document.querySelector('meta[name=\"viewport\"]');if(vp){vp.setAttribute('content','width=1280, initial-scale=0.85');}})();"
                    view?.evaluateJavascript(js, null)
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (!isShieldEnabled) return super.shouldInterceptRequest(view, request)

                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                val urlStr = request.url.toString().lowercase()

                if (isStreamingHost(host)) {
                    return super.shouldInterceptRequest(view, request)
                }

                val isStrictAdblock = prefs.getBoolean(PREF_STRICT_ADBLOCK, true)
                val isSocialShield = prefs.getBoolean(PREF_SOCIAL_WIDGETS, true)

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
                    runOnUiThread { onPageAdsBlocked(1) }
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
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                binding.fullscreenContainer.addView(
                    customView,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                binding.fullscreenContainer.visibility = View.VISIBLE
                binding.mainContentLayout.visibility = View.GONE
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                if (customView == null) return

                binding.fullscreenContainer.removeView(customView)
                binding.fullscreenContainer.visibility = View.GONE
                binding.mainContentLayout.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }
        }

        // Dual Scroll Auto-Hide Toolbar mechanics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 12 && scrollY > 80) {
                    binding.topBar.animate().translationY(-binding.topBar.height.toFloat()).alpha(0f).setDuration(180).start()
                    binding.bottomNav.animate().translationY(binding.bottomNav.height.toFloat()).alpha(0f).setDuration(180).start()
                    binding.cardSuggestions.visibility = View.GONE
                } else if (dy < -12 || scrollY < 20) {
                    binding.topBar.animate().translationY(0f).alpha(1f).setDuration(180).start()
                    binding.bottomNav.animate().translationY(0f).alpha(1f).setDuration(180).start()
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
        tab.webView.loadUrl(url)
        updateTabCounter()
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        for (i in tabs.indices) {
            tabs[i].webView.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        val current = tabs[index]
        if (current.url.contains("fokk_start.html")) {
            binding.urlInput.setText("")
            binding.urlInput.hint = "Search with ${currentSearchEngine.displayName} or enter URL..."
        } else {
            binding.urlInput.setText(current.url)
        }

        // Apply Private Tab styling if active
        if (current.isPrivate) {
            binding.topBar.setBackgroundColor(android.graphics.Color.parseColor("#1B1229"))
            binding.bottomNav.setBackgroundColor(android.graphics.Color.parseColor("#120A1C"))
            binding.imgEngineIcon.setImageResource(R.drawable.ic_lock)
            binding.imgEngineIcon.imageTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#C084FC"))
        } else {
            binding.topBar.setBackgroundColor(android.graphics.Color.parseColor("#F0090D14"))
            binding.bottomNav.setBackgroundColor(android.graphics.Color.parseColor("#0C101A"))
            binding.imgEngineIcon.setImageResource(R.drawable.ic_search)
            binding.imgEngineIcon.imageTintList = ColorStateList.valueOf(getColor(R.color.fokk_cyan))
        }

        pageBlockedCount = current.blockedAds
        if (pageBlockedCount > 0) {
            binding.badgeBlocked.visibility = View.VISIBLE
            binding.textPageBlockedCount.text = pageBlockedCount.toString()
        } else {
            binding.badgeBlocked.visibility = View.GONE
        }
        updateTabCounter()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        binding.tabContainer.removeView(removed.webView)
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

    private fun injectAdblockScript(webView: WebView?) {
        if (webView == null) return
        if (injectionScript.isNotEmpty()) {
            webView.evaluateJavascript(injectionScript, null)
        }

        // Cookie & GDPR Wall Auto-Dismiss
        val isCookieConsent = prefs.getBoolean(PREF_COOKIE_CONSENT, true)
        if (isCookieConsent) {
            val cookieDismissJs = "javascript:(function(){let d=document.getElementById('__fokk_cookie_killer');if(!d){let s=document.createElement('style');s.id='__fokk_cookie_killer';s.innerHTML='#onetrust-consent-sdk, #onetrust-banner-sdk, .onetrust-pc-dark, .cookie-banner, .cookie-notice, #cookie-notice, .qc-cmp2-container, #cmp-app-container, .fc-consent-root { display: none !important; height: 0 !important; visibility: hidden !important; }';(document.head||document.documentElement).appendChild(s);}})();"
            webView.evaluateJavascript(cookieDismissJs, null)
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

    private fun injectDarkModeScript(webView: WebView?, enable: Boolean) {
        if (webView == null) return
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
        return lower.endsWith("googlevideo.com") ||
                lower.endsWith("ytimg.com") ||
                lower.endsWith("ggpht.com") ||
                lower == "youtube.com" ||
                lower.endsWith(".youtube.com") ||
                lower == "youtu.be"
    }

    private fun setupControls() {
        val handleBack: () -> Unit = {
            if (binding.cardSuggestions.visibility == View.VISIBLE) {
                binding.cardSuggestions.visibility = View.GONE
            } else if (customView != null) {
                binding.fullscreenContainer.removeView(customView)
                binding.fullscreenContainer.visibility = View.GONE
                binding.mainContentLayout.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            } else if (activeWebView?.canGoBack() == true) {
                activeWebView?.goBack()
            } else {
                finish()
            }
        }

        binding.btnNavBack.setOnClickListener { handleBack() }

        binding.btnNavForward.setOnClickListener {
            if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
        }

        binding.btnNavHome.setOnClickListener {
            activeWebView?.loadUrl("file:///android_asset/fokk_start.html")
        }

        binding.btnRefresh.setOnClickListener {
            activeWebView?.reload()
            Toast.makeText(this, "Reloading page...", Toast.LENGTH_SHORT).show()
        }

        binding.imgEngineIcon.setOnClickListener {
            showSearchEngineDialog()
        }

        binding.btnPip.setOnClickListener {
            enterPipMode()
        }

        binding.urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                binding.btnClearUrl.visibility = if (text.isNotEmpty() && binding.urlInput.hasFocus()) View.VISIBLE else View.GONE
                if (binding.urlInput.hasFocus()) {
                    fetchSuggestions(text)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.cardSuggestions.visibility = View.GONE
                binding.btnClearUrl.visibility = View.GONE
            } else {
                val text = binding.urlInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    binding.btnClearUrl.visibility = View.VISIBLE
                    fetchSuggestions(text)
                }
            }
        }

        binding.btnClearUrl.setOnClickListener {
            binding.urlInput.setText("")
            binding.cardSuggestions.visibility = View.GONE
        }

        val navigateOrSearch: () -> Unit = {
            val query = binding.urlInput.text.toString().trim()
            binding.cardSuggestions.visibility = View.GONE
            if (query.isNotEmpty()) {
                val targetUrl = when {
                    query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://") -> query
                    query.contains(".") && !query.contains(" ") -> "https://$query"
                    else -> currentSearchEngine.searchUrl + Uri.encode(query)
                }
                activeWebView?.loadUrl(targetUrl)
                binding.urlInput.clearFocus()
            }
        }

        binding.btnGo.setOnClickListener { navigateOrSearch() }

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

        binding.btnNavTabs.setOnClickListener {
            showTabSwitcherDialog()
        }

        binding.btnNavMenu.setOnClickListener {
            showBrowserMenuDialog()
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.length < 2 || query.startsWith("http://") || query.startsWith("https://") || query.startsWith("file://")) {
            binding.cardSuggestions.visibility = View.GONE
            return
        }

        suggestionJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val endpoint = "https://duckduckgo.com/ac/?q=${Uri.encode(query)}&type=list"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1200
                    readTimeout = 1200
                    setRequestProperty("User-Agent", cleanMobileUa)
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArr = JSONArray(text)
                    if (jsonArr.length() >= 2) {
                        val suggestionsArr = jsonArr.getJSONArray(1)
                        val list = mutableListOf<String>()
                        for (i in 0 until minOf(suggestionsArr.length(), 5)) {
                            list.add(suggestionsArr.getString(i))
                        }
                        withContext(Dispatchers.Main) {
                            displaySuggestions(list)
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun displaySuggestions(list: List<String>) {
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
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                setBackgroundResource(android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((15 * density).toInt(), (15 * density).toInt())
                setImageResource(R.drawable.ic_search)
                imageTintList = ColorStateList.valueOf(getColor(R.color.fokk_cyan))
            }

            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * density).toInt()
                }
                text = item
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
            }

            row.addView(icon)
            row.addView(tv)
            row.setOnClickListener {
                binding.urlInput.setText(item)
                binding.cardSuggestions.visibility = View.GONE
                binding.urlInput.clearFocus()
                val targetUrl = currentSearchEngine.searchUrl + Uri.encode(item)
                activeWebView?.loadUrl(targetUrl)
            }
            binding.layoutSuggestionsList.addView(row)
        }
        binding.cardSuggestions.visibility = View.VISIBLE
    }

    private fun showSearchEngineDialog() {
        val engines = SearchEngine.values()
        val items = engines.map { it.displayName }.toTypedArray()
        val selectedIdx = engines.indexOf(currentSearchEngine).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Default Privacy Search Engine")
            .setSingleChoiceItems(items, selectedIdx) { dialog, which ->
                currentSearchEngine = engines[which]
                prefs.edit().putString(PREF_SEARCH_ENGINE, currentSearchEngine.name).apply()
                binding.urlInput.hint = "Search with ${currentSearchEngine.displayName} or enter URL..."
                Toast.makeText(this, "Search Engine: ${currentSearchEngine.displayName}", Toast.LENGTH_SHORT).show()
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
        sheetBinding.switchCookieConsent.isChecked = prefs.getBoolean(PREF_COOKIE_CONSENT, true)
        sheetBinding.switchSocialWidgets.isChecked = prefs.getBoolean(PREF_SOCIAL_WIDGETS, true)
        sheetBinding.editCustomJs.setText(prefs.getString(PREF_CUSTOM_JS, "") ?: "")
        sheetBinding.editCustomCss.setText(prefs.getString(PREF_CUSTOM_CSS, "") ?: "")

        sheetBinding.btnSaveExtensions.setOnClickListener {
            prefs.edit()
                .putBoolean(PREF_STRICT_ADBLOCK, sheetBinding.switchStrictAdblock.isChecked)
                .putBoolean(PREF_COOKIE_CONSENT, sheetBinding.switchCookieConsent.isChecked)
                .putBoolean(PREF_SOCIAL_WIDGETS, sheetBinding.switchSocialWidgets.isChecked)
                .putString(PREF_CUSTOM_JS, sheetBinding.editCustomJs.text.toString().trim())
                .putString(PREF_CUSTOM_CSS, sheetBinding.editCustomCss.text.toString().trim())
                .apply()

            injectAdblockScript(activeWebView)
            Toast.makeText(this, "Custom Filters & Extensions Applied!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
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
                        activeWebView?.loadUrl(url)
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
            cardBinding.cardTab.strokeColor = getColor(R.color.fokk_cyan)
            cardBinding.cardTab.strokeWidth = (2 * resources.displayMetrics.density).toInt()
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
            showingPrivateTabs = true
            updateTabsView()
        }

        sheetBinding.btnNewTabSheet.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = false)
            showingPrivateTabs = false
            dialog.dismiss()
        }

        sheetBinding.btnNewPrivateTab.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = true)
            showingPrivateTabs = true
            dialog.dismiss()
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

    private fun showBrowserMenuDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetBrowserMenuBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.textMenuBlockedInfo.text = "$pageBlockedCount ads & trackers blocked on this page"
        sheetBinding.switchMenuShield.isChecked = isShieldEnabled
        sheetBinding.switchDesktop.isChecked = activeTab?.isDesktop ?: false
        sheetBinding.switchDarkMode.isChecked = isDarkMode
        val showShortcuts = prefs.getBoolean(PREF_SHOW_HOMEPAGE_SHORTCUTS, true)
        sheetBinding.switchShowShortcuts.isChecked = showShortcuts
        sheetBinding.textCurrentEngine.text = "${currentSearchEngine.displayName} (Active)"

        sheetBinding.switchShowShortcuts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SHOW_HOMEPAGE_SHORTCUTS, isChecked).apply()
            activeWebView?.evaluateJavascript("if (typeof toggleHomepageShortcutsVisibility === 'function') toggleHomepageShortcutsVisibility($isChecked);", null)
            val msg = if (isChecked) "Shortcuts displayed on homepage" else "Shortcuts hidden from homepage"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        sheetBinding.switchMenuShield.setOnCheckedChangeListener { _, isChecked ->
            isShieldEnabled = isChecked
            val msg = if (isChecked) "Fokk Shield Protection Enabled" else "Fokk Shield Paused"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        sheetBinding.switchDesktop.setOnCheckedChangeListener { _, isChecked ->
            activeTab?.let { tab ->
                tab.isDesktop = isChecked
                applyDesktopMode(tab.webView, tab.isDesktop)
                var currentUrl = tab.webView.url ?: tab.url
                if (tab.isDesktop && currentUrl.contains("m.youtube.com")) {
                    currentUrl = currentUrl.replace("m.youtube.com", "www.youtube.com")
                    tab.webView.loadUrl(currentUrl)
                } else if (!tab.isDesktop && currentUrl.contains("www.youtube.com")) {
                    currentUrl = currentUrl.replace("www.youtube.com", "m.youtube.com")
                    tab.webView.loadUrl(currentUrl)
                } else {
                    tab.webView.reload()
                }
            }
            dialog.dismiss()
        }

        sheetBinding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            injectDarkModeScript(activeWebView, isDarkMode)
            dialog.dismiss()
        }

        sheetBinding.rowPrivateTab.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html", isPrivate = true)
            dialog.dismiss()
        }

        sheetBinding.rowHistory.setOnClickListener {
            dialog.dismiss()
            showHistoryDialog()
        }

        sheetBinding.rowDownloads.setOnClickListener {
            try {
                startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (e: Exception) {
                Toast.makeText(this, "Opening Downloads folder...", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        sheetBinding.rowTranslate.setOnClickListener {
            val currentUrl = activeTab?.url
            if (!currentUrl.isNullOrEmpty() && !currentUrl.contains("fokk_start.html")) {
                val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=" + Uri.encode(currentUrl)
                activeWebView?.loadUrl(translateUrl)
                Toast.makeText(this, "Translating page...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Open a website to translate", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isStartPage = activeTab?.url?.contains("fokk_start.html") == true
        val recentMedia = (System.currentTimeMillis() - lastMediaPlayingTime) < 5000L
        val isVideoSite = activeTab?.url?.let { url ->
            url.contains("youtube.com") || url.contains("twitch.tv") || url.contains("vimeo.com")
        } ?: false

        if (!isStartPage && (isMediaPlaying || recentMedia || (isVideoSite && customView == null) || customView != null)) {
            enterPipMode()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Maximize HTML5 video element so it fills 100% of the PiP viewport cleanly
                activeWebView?.evaluateJavascript("if (typeof window.__fokk_enter_pip_view === 'function') window.__fokk_enter_pip_view();", null)

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
            activeWebView?.evaluateJavascript("if (typeof window.__fokk_enter_pip_view === 'function') window.__fokk_enter_pip_view();", null)
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
            activeWebView?.evaluateJavascript("if (typeof window.__fokk_exit_pip_view === 'function') window.__fokk_exit_pip_view();", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.cardSuggestions.visibility == View.VISIBLE) {
            binding.cardSuggestions.visibility = View.GONE
            return
        }
        if (customView != null) {
            binding.fullscreenContainer.removeView(customView)
            binding.fullscreenContainer.visibility = View.GONE
            binding.mainContentLayout.visibility = View.VISIBLE
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else if (activeWebView?.canGoBack() == true) {
            activeWebView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        suggestionJob?.cancel()
        for (tab in tabs) {
            binding.tabContainer.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_OPEN_ACTION = "extra_open_action"
        private const val TAG = "FokkBrowser"
        private const val PREFS_NAME = "fokk_browser_prefs"
        private const val PREF_SEARCH_ENGINE = "fokk_search_engine"
        private const val PREF_STRICT_ADBLOCK = "filter_strict_adblock"
        private const val PREF_COOKIE_CONSENT = "filter_cookie_consent"
        private const val PREF_SOCIAL_WIDGETS = "filter_social_trackers"
        private const val PREF_CUSTOM_JS = "custom_userscript"
        private const val PREF_CUSTOM_CSS = "custom_usercss"
        private const val PREF_BROWSING_HISTORY = "key_browsing_history_v2"
        private const val PREF_SHOW_HOMEPAGE_SHORTCUTS = "fokk_show_shortcuts"
    }
}
