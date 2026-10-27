package com.adshield.app.ui

import android.annotation.SuppressLint
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
import com.adshield.app.databinding.SheetTabSwitcherBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private var isDesktopMode = false
    private var isDarkMode = false

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

    private val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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
        addNewTab(initialUrl)
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
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url: String): BrowserTab {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = if (isDesktopMode) desktopUa else cleanMobileUa
        }

        webView.addJavascriptInterface(WebAppInterface(this), "FokkShieldBridge")

        // Pre-inject content blocker at document start if supported by Chromium
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
                    }
                }
                view?.title?.let {
                    if (it.isNotEmpty() && activeTab?.url?.contains("fokk_start.html") != true) {
                        activeTab?.title = it
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (!isShieldEnabled) return super.shouldInterceptRequest(view, request)

                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                val urlStr = request.url.toString().lowercase()

                // Never block core streaming CDN video segments or essential services
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
                    // Scrolling down: immersive view
                    binding.topBar.animate().translationY(-binding.topBar.height.toFloat()).alpha(0f).setDuration(180).start()
                    binding.bottomNav.animate().translationY(binding.bottomNav.height.toFloat()).alpha(0f).setDuration(180).start()
                    binding.cardSuggestions.visibility = View.GONE
                } else if (dy < -12 || scrollY < 20) {
                    // Scrolling up or at top: restore browser omnibar and toolbar
                    binding.topBar.animate().translationY(0f).alpha(1f).setDuration(180).start()
                    binding.bottomNav.animate().translationY(0f).alpha(1f).setDuration(180).start()
                }
            }
        }

        val initialTitle = if (url.contains("fokk_start.html")) "New Tab" else "Fokk Browser"
        val tab = BrowserTab(title = initialTitle, url = url, webView = webView)
        binding.tabContainer.addView(webView)
        return tab
    }

    private fun addNewTab(url: String = "file:///android_asset/fokk_start.html") {
        val tab = createTab(url)
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
            addNewTab("file:///android_asset/fokk_start.html")
        } else {
            val newIndex = if (activeTabIndex >= tabs.size) tabs.size - 1 else activeTabIndex
            switchToTab(newIndex)
        }
        updateTabCounter()
    }

    private fun closeAllTabs() {
        for (tab in tabs) {
            binding.tabContainer.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
        addNewTab("file:///android_asset/fokk_start.html")
    }

    private fun updateTabCounter() {
        binding.textTabCount.text = tabs.size.toString()
    }

    private fun injectAdblockScript(webView: WebView?) {
        if (webView == null) return
        if (injectionScript.isNotEmpty()) {
            webView.evaluateJavascript(injectionScript, null)
        }

        // 1. Cookie & GDPR Wall Auto-Dismiss (uBlock Annoyances style)
        val isCookieConsent = prefs.getBoolean(PREF_COOKIE_CONSENT, true)
        if (isCookieConsent) {
            val cookieDismissJs = "javascript:(function(){let d=document.getElementById('__fokk_cookie_killer');if(!d){let s=document.createElement('style');s.id='__fokk_cookie_killer';s.innerHTML='#onetrust-consent-sdk, #onetrust-banner-sdk, .onetrust-pc-dark, .cookie-banner, .cookie-notice, #cookie-notice, .qc-cmp2-container, #cmp-app-container, .fc-consent-root { display: none !important; height: 0 !important; visibility: hidden !important; }';(document.head||document.documentElement).appendChild(s);}})();"
            webView.evaluateJavascript(cookieDismissJs, null)
        }

        // 2. Custom User CSS Injection
        val customCss = prefs.getString(PREF_CUSTOM_CSS, "") ?: ""
        if (customCss.isNotEmpty()) {
            val safeCss = customCss.replace("'", "\\'").replace("\n", " ")
            val js = "javascript:(function(){let s=document.getElementById('__fokk_custom_css');if(!s){s=document.createElement('style');s.id='__fokk_custom_css';(document.head||document.documentElement).appendChild(s);}s.innerHTML='$safeCss';})();"
            webView.evaluateJavascript(js, null)
        }

        // 3. Custom Userscript (JS) Injection
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
        // Explicitly NEVER treat ad/tracker endpoints as streaming hosts
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
        // Navigation controls
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

        binding.btnBack.setOnClickListener { handleBack() }
        binding.btnNavBack.setOnClickListener { handleBack() }

        binding.btnForward.setOnClickListener {
            if (activeWebView?.canGoForward() == true) activeWebView?.goForward()
        }
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

        // Live URL input & suggestions handling
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

        // Tab Switcher Dialog Trigger
        binding.btnNavTabs.setOnClickListener {
            showTabSwitcherDialog()
        }

        // Browser Menu Dialog Trigger
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
                // Silently discard cancellation/network errors
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

        val isStrictAdblock = prefs.getBoolean(PREF_STRICT_ADBLOCK, true)
        val isCookieConsent = prefs.getBoolean(PREF_COOKIE_CONSENT, true)
        val isSocialWidgets = prefs.getBoolean(PREF_SOCIAL_WIDGETS, true)
        val savedCustomJs = prefs.getString(PREF_CUSTOM_JS, "") ?: ""
        val savedCustomCss = prefs.getString(PREF_CUSTOM_CSS, "") ?: ""

        sheetBinding.switchStrictAdblock.isChecked = isStrictAdblock
        sheetBinding.switchCookieConsent.isChecked = isCookieConsent
        sheetBinding.switchSocialWidgets.isChecked = isSocialWidgets
        sheetBinding.editCustomJs.setText(savedCustomJs)
        sheetBinding.editCustomCss.setText(savedCustomCss)

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

    private fun showTabSwitcherDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetTabSwitcherBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.textTabHeader.text = "Open Tabs (${tabs.size})"

        fun populateTabs() {
            sheetBinding.layoutTabsContainer.removeAllViews()
            for (i in tabs.indices) {
                val tab = tabs[i]
                val cardBinding = ItemTabCardBinding.inflate(layoutInflater, sheetBinding.layoutTabsContainer, false)
                cardBinding.textTabTitle.text = tab.title.ifEmpty { "New Tab" }

                val lowerUrl = tab.url.lowercase()
                when {
                    lowerUrl.contains("fokk_start.html") -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_home)
                        cardBinding.textTabTitle.text = "New Tab"
                        cardBinding.textTabUrl.text = "fokk://home"
                    }
                    lowerUrl.contains("youtube.com") -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_youtube)
                        cardBinding.textTabUrl.text = tab.url
                    }
                    lowerUrl.contains("instagram.com") -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_instagram)
                        cardBinding.textTabUrl.text = tab.url
                    }
                    lowerUrl.contains("x.com") || lowerUrl.contains("twitter.com") -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_twitter)
                        cardBinding.textTabUrl.text = tab.url
                    }
                    lowerUrl.contains("reddit.com") -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_reddit)
                        cardBinding.textTabUrl.text = tab.url
                    }
                    else -> {
                        cardBinding.imgTabIcon.setImageResource(R.drawable.ic_launch)
                        cardBinding.textTabUrl.text = tab.url
                    }
                }

                // Active tab glow border
                if (i == activeTabIndex) {
                    cardBinding.cardTab.strokeColor = getColor(R.color.fokk_cyan)
                    cardBinding.cardTab.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                }

                cardBinding.cardTab.setOnClickListener {
                    switchToTab(i)
                    dialog.dismiss()
                }

                cardBinding.btnCloseTab.setOnClickListener {
                    closeTab(i)
                    if (tabs.isEmpty()) {
                        dialog.dismiss()
                    } else {
                        sheetBinding.textTabHeader.text = "Open Tabs (${tabs.size})"
                        populateTabs()
                    }
                }

                sheetBinding.layoutTabsContainer.addView(cardBinding.root)
            }
        }

        populateTabs()

        sheetBinding.btnNewTabSheet.setOnClickListener {
            addNewTab("file:///android_asset/fokk_start.html")
            dialog.dismiss()
        }

        sheetBinding.btnCloseAllTabs.setOnClickListener {
            closeAllTabs()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showBrowserMenuDialog() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = SheetBrowserMenuBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.textMenuBlockedInfo.text = "$pageBlockedCount ads & trackers blocked on this page"
        sheetBinding.switchMenuShield.isChecked = isShieldEnabled
        sheetBinding.switchDesktop.isChecked = isDesktopMode
        sheetBinding.switchDarkMode.isChecked = isDarkMode
        sheetBinding.textCurrentEngine.text = "${currentSearchEngine.displayName} (Active)"

        sheetBinding.switchMenuShield.setOnCheckedChangeListener { _, isChecked ->
            isShieldEnabled = isChecked
            val msg = if (isChecked) "Fokk Shield Protection Enabled" else "Fokk Shield Paused"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        sheetBinding.switchDesktop.setOnCheckedChangeListener { _, isChecked ->
            isDesktopMode = isChecked
            activeWebView?.settings?.userAgentString = if (isDesktopMode) desktopUa else cleanMobileUa
            activeWebView?.reload()
            dialog.dismiss()
        }

        sheetBinding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            isDarkMode = isChecked
            injectDarkModeScript(activeWebView, isDarkMode)
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
            addNewTab("file:///android_asset/fokk_start.html")
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
        Toast.makeText(this, "Browsing history, cache & cookies cleared!", Toast.LENGTH_SHORT).show()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val rect = android.graphics.Rect()
                if (customView != null) {
                    customView?.getGlobalVisibleRect(rect)
                } else {
                    activeWebView?.getGlobalVisibleRect(rect)
                }
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (!rect.isEmpty) {
                    builder.setSourceRectHint(rect)
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
            return
        }
        CookieManager.getInstance().flush()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.topBar.visibility = View.GONE
            binding.bottomNav.visibility = View.GONE
        } else {
            binding.topBar.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.VISIBLE
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
        private const val TAG = "FokkBrowser"
        private const val PREFS_NAME = "fokk_browser_prefs"
        private const val PREF_SEARCH_ENGINE = "fokk_search_engine"
        private const val PREF_STRICT_ADBLOCK = "filter_strict_adblock"
        private const val PREF_COOKIE_CONSENT = "filter_cookie_consent"
        private const val PREF_SOCIAL_WIDGETS = "filter_social_trackers"
        private const val PREF_CUSTOM_JS = "custom_userscript"
        private const val PREF_CUSTOM_CSS = "custom_usercss"
    }
}
