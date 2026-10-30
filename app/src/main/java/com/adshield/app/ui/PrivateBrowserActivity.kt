package com.adshield.app.ui

import android.os.Build
import android.os.Bundle
import android.webkit.WebView

/** Hosts private tabs in a WebView profile that is separate from the regular browser process. */
class PrivateBrowserActivity : MediaHubActivity() {

    override val isIsolatedPrivateProfile: Boolean
        get() = true

    override val privateProfileConfigurationReady: Boolean
        get() = privateProfileConfigured && !privateProfileSetupFailed

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !privateProfileConfigured) {
            try {
                WebView.setDataDirectorySuffix(PRIVATE_WEBVIEW_SUFFIX)
                privateProfileConfigured = true
            } catch (e: IllegalStateException) {
                privateProfileSetupFailed = true
            } catch (e: IllegalArgumentException) {
                privateProfileSetupFailed = true
            }
        }
        super.onCreate(savedInstanceState)
    }

    companion object {
        private const val PRIVATE_WEBVIEW_SUFFIX = "fokk_private"
        private var privateProfileConfigured = false
        private var privateProfileSetupFailed = false
    }
}
