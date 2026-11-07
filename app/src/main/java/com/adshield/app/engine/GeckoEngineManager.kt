package com.adshield.app.engine

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Singleton manager for Mozilla GeckoView (Firefox Quantum Engine).
 * Pre-configures native strict tracking protection, third-party cookie isolation,
 * and hardware-accelerated rendering.
 */
object GeckoEngineManager {
    private const val TAG = "GeckoEngineManager"

    @Volatile
    private var runtime: GeckoRuntime? = null

    fun getRuntime(context: Context): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: buildRuntime(context.applicationContext).also { runtime = it }
        }
    }

    private fun buildRuntime(appContext: Context): GeckoRuntime {
        Log.i(TAG, "Initializing Mozilla GeckoView Quantum Engine with Strict Content Blocking")
        val contentBlockingSettings = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .build()

        val settings = GeckoRuntimeSettings.Builder()
            .aboutConfigEnabled(false)
            .automaticFontSizeAdjustment(true)
            .javaScriptEnabled(true)
            .consoleOutput(false)
            .contentBlocking(contentBlockingSettings)
            .build()

        val instance = GeckoRuntime.create(appContext, settings)
        Log.i(TAG, "GeckoRuntime created successfully")
        instance.webExtensionController.ensureBuiltIn(
            "resource://android/assets/fokk_extension/",
            "adshield@fokk.internal"
        ).accept(
            { Log.i(TAG, "Fokk AdShield Native Extension registered") },
            { e -> Log.e(TAG, "Failed to register Fokk AdShield Extension", e) }
        )
        return instance
    }
}
