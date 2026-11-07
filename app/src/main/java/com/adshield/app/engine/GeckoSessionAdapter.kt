package com.adshield.app.engine

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Adapter encapsulating a GeckoSession with lifecycle callbacks
 * mapped cleanly to standard browser events.
 */
class GeckoSessionAdapter(
    private val context: Context,
    val isPrivate: Boolean = false,
    private val listener: Listener
) {
    interface Listener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, success: Boolean)
        fun onProgressChanged(progress: Int)
        fun onTitleReceived(title: String)
        fun onLocationChanged(url: String, canGoBack: Boolean, canGoForward: Boolean)
        fun onTrackerBlocked(category: Int)
        fun onFullScreenRequested(fullScreen: Boolean)
    }

    val session: GeckoSession

    var currentUrl: String = ""
        private set

    private var currentCanGoBack = false
    private var currentCanGoForward = false

    init {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(true)
            .build()
        session = GeckoSession(settings)
        setupDelegates()
        session.open(GeckoEngineManager.getRuntime(context))
    }

    private fun setupDelegates() {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(s: GeckoSession, url: String) {
                currentUrl = url
                listener.onPageStarted(url)
            }

            override fun onPageStop(s: GeckoSession, success: Boolean) {
                listener.onPageFinished(currentUrl, success)
            }

            override fun onProgressChange(s: GeckoSession, progress: Int) {
                listener.onProgressChanged(progress)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(s: GeckoSession, canGoBack: Boolean) {
                currentCanGoBack = canGoBack
            }

            override fun onCanGoForward(s: GeckoSession, canGoForward: Boolean) {
                currentCanGoForward = canGoForward
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(s: GeckoSession, title: String?) {
                if (!title.isNullOrEmpty()) {
                    listener.onTitleReceived(title)
                }
            }

            override fun onFullScreen(s: GeckoSession, fullScreen: Boolean) {
                listener.onFullScreenRequested(fullScreen)
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(s: GeckoSession, event: ContentBlocking.BlockEvent) {
                listener.onTrackerBlocked(event.antiTrackingCategory)
            }
        }
    }

    fun loadUri(uri: String) {
        currentUrl = uri
        val targetUri = if (uri.startsWith("file:///android_asset/")) {
            uri.replace("file:///android_asset/", "resource://android/assets/")
        } else {
            uri
        }
        session.loadUri(targetUri)
    }

    fun reload() {
        session.reload()
    }

    fun goBack() {
        session.goBack()
    }

    fun goForward() {
        session.goForward()
    }

    fun canGoBack(): Boolean = currentCanGoBack
    fun canGoForward(): Boolean = currentCanGoForward

    fun close() {
        session.close()
    }
}
