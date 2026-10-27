package com.adshield.app.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.adshield.app.AdShieldApp
import com.adshield.app.R
import com.adshield.app.databinding.ActivityMediaHubBinding
import com.adshield.app.databinding.ItemTabCardBinding
import com.adshield.app.databinding.SheetBrowserMenuBinding
import com.adshield.app.databinding.SheetTabSwitcherBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.UUID

class MediaHubActivity : AppCompatActivity() {

    data class BrowserTab(
        val id: String = UUID.randomUUID().toString(),
        var title: String = "Fokk Browser",
        var url: String = "https://m.youtube.com",
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

        loadInjectionScript()
        setupControls()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "https://m.youtube.com"
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
                        binding.urlInput.setText(pageUrl)
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
                pageUrl?.let { activeTab?.url = it }
                view?.title?.let { if (it.isNotEmpty()) activeTab?.title = it }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (!isShieldEnabled) return super.shouldInterceptRequest(view, request)

                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                val urlStr = request.url.toString().lowercase()

                // Never block core streaming CDN video segments or essential services
                if (isStreamingHost(host)) {
                    return super.shouldInterceptRequest(view, request)
                }

                val isAdScript = urlStr.contains("/pagead/") ||
                        urlStr.contains("adsbygoogle.js") ||
                        urlStr.contains("googletagservices.com") ||
                        urlStr.contains("googleadservices.com") ||
                        urlStr.contains("/gpt.js") ||
                        urlStr.contains("adservice.google.")

                if (isAdScript || AdShieldApp.instance.blocklistEngine.isDomainBlocked(host)) {
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
                if (!title.isNullOrEmpty()) {
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
                } else if (dy < -12 || scrollY < 20) {
                    // Scrolling up or at top: restore browser omnibar and toolbar
                    binding.topBar.animate().translationY(0f).alpha(1f).setDuration(180).start()
                    binding.bottomNav.animate().translationY(0f).alpha(1f).setDuration(180).start()
                }
            }
        }

        val tab = BrowserTab(url = url, webView = webView)
        binding.tabContainer.addView(webView)
        return tab
    }

    private fun addNewTab(url: String) {
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
        binding.urlInput.setText(current.url)
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
            addNewTab("https://m.youtube.com")
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
        addNewTab("https://m.youtube.com")
    }

    private fun updateTabCounter() {
        binding.textTabCount.text = tabs.size.toString()
    }

    private fun injectAdblockScript(webView: WebView?) {
        if (injectionScript.isNotEmpty() && webView != null) {
            webView.evaluateJavascript(injectionScript, null)
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
            activeWebView?.loadUrl("https://m.youtube.com")
        }

        binding.btnRefresh.setOnClickListener {
            activeWebView?.reload()
            Toast.makeText(this, "Reloading page...", Toast.LENGTH_SHORT).show()
        }

        binding.imgSecurityLock.setOnClickListener {
            activeWebView?.reload()
            Toast.makeText(this, "Encrypted SSL Connection Active", Toast.LENGTH_SHORT).show()
        }

        binding.btnPip.setOnClickListener {
            enterPipMode()
        }

        val navigateOrSearch: () -> Unit = {
            val query = binding.urlInput.text.toString().trim()
            if (query.isNotEmpty()) {
                val targetUrl = when {
                    query.startsWith("http://") || query.startsWith("https://") -> query
                    query.contains(".") && !query.contains(" ") -> "https://$query"
                    else -> "https://m.youtube.com/results?search_query=${Uri.encode(query)}"
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
                cardBinding.textTabUrl.text = tab.url

                // Platform icon styling
                val lowerUrl = tab.url.lowercase()
                when {
                    lowerUrl.contains("youtube.com") -> cardBinding.imgTabIcon.setImageResource(R.drawable.ic_youtube)
                    lowerUrl.contains("instagram.com") -> cardBinding.imgTabIcon.setImageResource(R.drawable.ic_instagram)
                    lowerUrl.contains("x.com") || lowerUrl.contains("twitter.com") -> cardBinding.imgTabIcon.setImageResource(R.drawable.ic_twitter)
                    lowerUrl.contains("reddit.com") -> cardBinding.imgTabIcon.setImageResource(R.drawable.ic_reddit)
                    else -> cardBinding.imgTabIcon.setImageResource(R.drawable.ic_launch)
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
            addNewTab("https://m.youtube.com")
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

        sheetBinding.rowReload.setOnClickListener {
            activeWebView?.reload()
            dialog.dismiss()
        }

        sheetBinding.rowNewTab.setOnClickListener {
            addNewTab("https://m.youtube.com")
            dialog.dismiss()
        }

        sheetBinding.rowShare.setOnClickListener {
            val url = activeTab?.url ?: "https://m.youtube.com"
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
    }
}
