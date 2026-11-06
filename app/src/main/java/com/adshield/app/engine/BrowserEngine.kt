package com.adshield.app.engine

import android.content.Context
import android.view.View

/**
 * Enumeration of supported and prospective browser engine types.
 */
enum class EngineType(val displayName: String) {
    WEBVIEW("Android System Engine (WebView)"),
    LADYBIRD("Ladybird Engine (LibWeb / LibJS)")
}

/**
 * High-level abstraction for the underlying browser engine.
 * Decouples Fokk browser UI (tabs, command bar, downloader, reader, AI)
 * from the specific rendering engine, preparing for Ladybird integration.
 */
interface IBrowserEngine {
    val engineType: EngineType

    fun initialize(context: Context)
    fun createSession(context: Context, isPrivate: Boolean = false): IEngineSession
    fun isAvailable(): Boolean
    fun shutdown()
}

/**
 * Represents an individual browsing instance / session (bound to a Tab).
 */
interface IEngineSession {
    val engineType: EngineType
    val view: View
    val currentUrl: String?
    val title: String?
    val progress: Int
    var isDesktop: Boolean

    fun loadUrl(url: String)
    fun reload()
    fun stopLoading()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null)
    fun setEngineCallback(callback: EngineCallback?)
    fun destroy()
}

/**
 * Lifecycle and navigation callbacks from the rendering engine to the browser shell.
 */
interface EngineCallback {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onProgressChanged(progress: Int)
    fun onReceivedTitle(title: String)
    fun onCreateWindow(isUserGesture: Boolean, isDialog: Boolean): Boolean
    fun onNavigationRequested(url: String, userGesture: Boolean): Boolean
}

/**
 * Central engine manager that delegates session creation to the active engine.
 */
object EngineManager {
    private var activeEngineType = EngineType.WEBVIEW

    fun getActiveEngineType(): EngineType = activeEngineType

    fun setActiveEngineType(type: EngineType) {
        activeEngineType = type
    }

    fun createEngine(context: Context, type: EngineType = activeEngineType): IBrowserEngine {
        return when (type) {
            EngineType.WEBVIEW -> WebViewEngineAdapter().apply { initialize(context) }
            EngineType.LADYBIRD -> LadybirdEngineStubAdapter().apply { initialize(context) }
        }
    }
}
