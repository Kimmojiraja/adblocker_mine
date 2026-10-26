package com.adshield.app.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adshield.app.AdShieldApp
import com.adshield.app.R
import com.adshield.app.databinding.ActivityMediaHubBinding
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class MediaHubActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaHubBinding
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var injectionScript = ""
    private var isDockExpanded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaHubBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadInjectionScript()
        setupWebView()
        setupControls()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "https://m.youtube.com"
        binding.webView.loadUrl(initialUrl)
    }

    private fun loadInjectionScript() {
        try {
            assets.open("inject_adshield.js").use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    injectionScript = reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // High-performance hardware acceleration
        binding.webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Enable third-party cookies for seamless media streaming
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        val defaultUa = WebSettings.getDefaultUserAgent(this)
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = defaultUa
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                if (url != null && !binding.urlInput.hasFocus()) {
                    binding.urlInput.setText(url)
                }
                injectAdblockScript(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                injectAdblockScript(view)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                
                // Never block core video streaming, thumbnail, or Google video infrastructure
                if (isStreamingHost(host)) {
                    return super.shouldInterceptRequest(view, request)
                }

                if (AdShieldApp.instance.blocklistEngine.isDomainBlocked(host)) {
                    AdShieldApp.instance.incrementBlockedAds()
                    // Return valid 200 OK empty response with CORS headers to avoid network stalls
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                            "Access-Control-Allow-Headers" to "*"
                        ),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
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
    }

    private fun injectAdblockScript(webView: WebView?) {
        if (injectionScript.isNotEmpty() && webView != null) {
            webView.evaluateJavascript(injectionScript, null)
        }
    }

    private fun isStreamingHost(host: String): Boolean {
        val lower = host.lowercase()
        return lower.contains("googlevideo.com") ||
                lower.contains("youtube.com") ||
                lower.contains("ytimg.com") ||
                lower.contains("ggpht.com") ||
                lower.contains("googleapis.com") ||
                lower.contains("gstatic.com") ||
                lower.contains("google.com")
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
        }

        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }

        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
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
                binding.webView.loadUrl(targetUrl)
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

        // Collapsible Dock Mechanics
        fun expandDock() {
            isDockExpanded = true
            binding.btnStudioToggle.visibility = View.GONE
            binding.bottomNav.visibility = View.VISIBLE
            binding.bottomNav.alpha = 0f
            binding.bottomNav.translationY = 50f
            binding.bottomNav.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }

        fun collapseDock() {
            isDockExpanded = false
            binding.bottomNav.animate().alpha(0f).translationY(50f).setDuration(200).withEndAction {
                binding.bottomNav.visibility = View.GONE
                binding.btnStudioToggle.visibility = View.VISIBLE
                binding.btnStudioToggle.alpha = 0f
                binding.btnStudioToggle.animate().alpha(1f).setDuration(200).start()
            }.start()
        }

        binding.btnMinimizeDock.setOnClickListener {
            collapseDock()
        }

        binding.btnStudioToggle.setOnClickListener {
            expandDock()
        }

        binding.btnPlatformMenu.setOnClickListener {
            if (isDockExpanded) collapseDock() else expandDock()
        }

        // Quick platform shortcuts in the Floating Island Dock
        binding.btnPlatformYoutube.setOnClickListener {
            binding.imgTogglePlatform.setImageResource(R.drawable.ic_youtube)
            binding.webView.loadUrl("https://m.youtube.com")
            collapseDock()
        }
        binding.btnPlatformInstagram.setOnClickListener {
            binding.imgTogglePlatform.setImageResource(R.drawable.ic_instagram)
            binding.webView.loadUrl("https://www.instagram.com")
            collapseDock()
        }
        binding.btnPlatformTwitter.setOnClickListener {
            binding.imgTogglePlatform.setImageResource(R.drawable.ic_twitter)
            binding.webView.loadUrl("https://mobile.twitter.com")
            collapseDock()
        }
        binding.btnPlatformReddit.setOnClickListener {
            binding.imgTogglePlatform.setImageResource(R.drawable.ic_reddit)
            binding.webView.loadUrl("https://www.reddit.com")
            collapseDock()
        }

        // Auto-hide bottom controls on scroll down so web navigation is 100% accessible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 12) {
                    // Scrolling down: hide both dock and toggle chip
                    binding.bottomNav.animate().translationY(150f).alpha(0f).setDuration(180).start()
                    binding.btnStudioToggle.animate().translationY(150f).alpha(0f).setDuration(180).start()
                } else if (dy < -12) {
                    // Scrolling up: reveal active control
                    if (isDockExpanded) {
                        binding.bottomNav.animate().translationY(0f).alpha(1f).setDuration(180).start()
                    } else {
                        binding.btnStudioToggle.animate().translationY(0f).alpha(1f).setDuration(180).start()
                    }
                }
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.topBar.visibility = View.GONE
            binding.bottomNav.visibility = View.GONE
            binding.btnStudioToggle.visibility = View.GONE
        } else {
            binding.topBar.visibility = View.VISIBLE
            if (isDockExpanded) {
                binding.bottomNav.visibility = View.VISIBLE
            } else {
                binding.btnStudioToggle.visibility = View.VISIBLE
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            binding.webView.webChromeClient?.onHideCustomView()
        } else if (isDockExpanded) {
            binding.btnMinimizeDock.performClick()
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
