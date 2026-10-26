package com.adshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adshield.app.AdShieldApp

class YouTubeAdSkipperService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private var isAdMuted = false
    private var originalVolume = -1
    private var lastMuteActionTime = 0L
    private var lastSkipClickTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    // Known specific View IDs for the official Skip Ad button
    private val skipButtonResourceIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/ad_skip_button"
    )

    // Strict skip button texts (NEVER match "skip" alone or "skip in" to avoid clicking comments/chapters)
    private val strictSkipTexts = listOf(
        "skip ad",
        "skip ads",
        "ignorar anuncio",
        "passer l'annonce",
        "überspringen"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, true).apply()
        sendBroadcast(Intent(ACTION_YT_STATE_CHANGED).putExtra("enabled", true))
        Log.i(TAG, "YouTube Ad Skipper Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Ignore events from other apps
        val pkg = event.packageName?.toString() ?: ""
        if (!pkg.contains("youtube")) return

        val now = System.currentTimeMillis()
        val rootNode = rootInActiveWindow ?: return

        try {
            // 1. Scan for and click Skip Button (debounced to at most once per 1000ms)
            if (now - lastSkipClickTime > 1000) {
                val skipClicked = searchAndClickSkipButton(rootNode)
                if (skipClicked) {
                    lastSkipClickTime = now
                }
            }

            // 2. Audio Muting during unskippable ads (with 2-second debounce to prevent fluttering)
            val autoMuteEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, false)
            if (autoMuteEnabled && (now - lastMuteActionTime > 2000)) {
                handleAdAudioMuting(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in YouTube accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun searchAndClickSkipButton(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Try View IDs first (fastest and most reliable)
        for (resId in skipButtonResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (performDirectClick(node)) {
                        onAdSkipped()
                        recycleNodes(nodes)
                        return true
                    }
                }
                recycleNodes(nodes)
            }
        }

        // 2. Search strictly for exact "Skip Ad" / "Skip Ads" text
        for (skipText in strictSkipTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(skipText)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()?.lowercase()?.trim() ?: ""
                    // Strict equality check: only click if text is exactly "skip ad" or "skip ads"
                    if (text == skipText || text == "skip ad" || text == "skip ads") {
                        if (performDirectClick(node)) {
                            onAdSkipped()
                            recycleNodes(nodes)
                            return true
                        }
                    }
                }
                recycleNodes(nodes)
            }
        }

        return false
    }

    private fun performDirectClick(node: AccessibilityNodeInfo): Boolean {
        // Only click the node itself if clickable
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "Clicked skip ad button directly")
                return true
            }
        }

        // Or click parent ONLY if it's explicitly a Button class (NEVER FrameLayout or ViewGroup)
        val parent = node.parent
        if (parent != null) {
            val parentClass = parent.className?.toString() ?: ""
            if (parent.isClickable && parentClass.endsWith("Button")) {
                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                if (clicked) {
                    Log.d(TAG, "Clicked parent Button")
                    return true
                }
            } else {
                parent.recycle()
            }
        }
        return false
    }

    private fun handleAdAudioMuting(rootNode: AccessibilityNodeInfo) {
        val adNodes = rootNode.findAccessibilityNodeInfosByText("ad •")
        val isAdShowing = !adNodes.isNullOrEmpty()
        recycleNodes(adNodes ?: emptyList())

        val now = System.currentTimeMillis()
        if (isAdShowing) {
            if (!isAdMuted) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol > 0) {
                    originalVolume = currentVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    isAdMuted = true
                    lastMuteActionTime = now
                    Log.d(TAG, "Ad confirmed on screen. Muting audio")
                }
            }
        } else {
            // Restore audio when ad is gone
            if (isAdMuted) {
                if (originalVolume > 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    Log.d(TAG, "Ad ended. Audio restored to $originalVolume")
                }
                isAdMuted = false
                originalVolume = -1
                lastMuteActionTime = now
            }
        }
    }

    private fun onAdSkipped() {
        AdShieldApp.instance.incrementSkippedYouTubeAds()
        sendBroadcast(Intent(ACTION_YT_AD_SKIPPED))
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        for (node in nodes) {
            try {
                node.recycle()
            } catch (ignored: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "YouTube Ad Skipper Service interrupted")
    }

    override fun onDestroy() {
        if (isAdMuted && originalVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, false).apply()
        sendBroadcast(Intent(ACTION_YT_STATE_CHANGED).putExtra("enabled", false))
        super.onDestroy()
    }

    companion object {
        const val ACTION_YT_STATE_CHANGED = "com.adshield.app.ACTION_YT_STATE_CHANGED"
        const val ACTION_YT_AD_SKIPPED = "com.adshield.app.ACTION_YT_AD_SKIPPED"
        private const val TAG = "YouTubeAdSkipper"
    }
}
