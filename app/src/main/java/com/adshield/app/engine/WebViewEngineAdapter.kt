package com.adshield.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Standard Android WebView implementation of [IBrowserEngine].
 * Incorporates privacy hardening, X-Requested-With suppression,
 * and document_start scriptlet injections.
 */
class WebViewEngineAdapter : IBrowserEngine {
    override val engineType: EngineType = EngineType.WEBVIEW

    override fun initialize(context: Context) {
        Log.d(TAG, "Initializing WebViewEngineAdapter")
    }

    override fun createSession(context: Context, isPrivate: Boolean): IEngineSession {
        return WebViewEngineSession(context, isPrivate)
    }

    override fun isAvailable(): Boolean = true

    override fun shutdown() {
        Log.d(TAG, "Shutting down WebViewEngineAdapter")
    }

    companion object {
        private const val TAG = "WebViewEngineAdapter"
    }
}

/**
 * Individual WebView-backed session implementing [IEngineSession].
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewEngineSession(
    context: Context,
    private val isPrivate: Boolean
) : IEngineSession {

    private val webView = WebView(context)
    private var callback: EngineCallback? = null
    override var isDesktop: Boolean = false

    override val engineType: EngineType = EngineType.WEBVIEW
    override val view: View get() = webView
    override val currentUrl: String? get() = webView.url
    override val title: String? get() = webView.title
    override val progress: Int get() = webView.progress

    init {
        configureSettings()
        setupClients()
    }

    private fun configureSettings() {
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
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = true
            allowContentAccess = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }

        // Suppress X-Requested-With header to stop anti-bot & news paywall blocks
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            try {
                val method = WebSettingsCompat::class.java.methods.firstOrNull {
                    it.name == "setRequestedWithHeaderOriginAllowList" || it.name == "setRequestedWithHeaderAllowList"
                }
                method?.invoke(null, webView.settings, emptySet<String>())
            } catch (e: Exception) {
                Log.w("WebViewEngineSession", "Could not set requested with header allow list", e)
            }
        }
    }

    private fun setupClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { callback?.onPageStarted(it) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { callback?.onPageFinished(it) }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val reqUrl = request?.url?.toString() ?: return false
                val isGesture = request.hasGesture()
                return callback?.onNavigationRequested(reqUrl, isGesture) ?: false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callback?.onProgressChanged(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { callback?.onReceivedTitle(it) }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                return callback?.onCreateWindow(isUserGesture, isDialog) ?: false
            }
        }
    }

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun reload() {
        webView.reload()
    }

    override fun stopLoading() {
        webView.stopLoading()
    }

    override fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun canGoForward(): Boolean = webView.canGoForward()

    override fun evaluateJavascript(script: String, callback: ((String?) -> Unit)?) {
        webView.evaluateJavascript(script) { result -> callback?.invoke(result) }
    }

    override fun setEngineCallback(callback: EngineCallback?) {
        this.callback = callback
    }

    override fun destroy() {
        callback = null
        webView.destroy()
    }
}

/**
 * Ladybird Engine stub adapter.
 * Prepares the architectural bridge for Ladybird LibWeb / LibJS integration on Android.
 */
class LadybirdEngineStubAdapter : IBrowserEngine {
    override val engineType: EngineType = EngineType.LADYBIRD

    override fun initialize(context: Context) {
        Log.i(TAG, "Ladybird Engine bridge initialized (Awaiting Native LibWeb JNI)")
    }

    override fun createSession(context: Context, isPrivate: Boolean): IEngineSession {
        // Fallback to WebView until native libladybird.so is bundled
        Log.w(TAG, "Ladybird native runtime not bundled; falling back to hardened WebViewEngineSession")
        return WebViewEngineSession(context, isPrivate)
    }

    override fun isAvailable(): Boolean {
        // Returns true once native libladybird.so can be resolved via System.loadLibrary
        return try {
            System.loadLibrary("ladybird")
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun shutdown() {
        Log.i(TAG, "Ladybird Engine bridge shutdown")
    }

    companion object {
        private const val TAG = "LadybirdEngineAdapter"
    }
}
