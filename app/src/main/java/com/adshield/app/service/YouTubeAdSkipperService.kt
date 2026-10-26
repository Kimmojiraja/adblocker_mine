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
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastSkipClickTime = 0L

    // Resource IDs of the actual "Skip Ad" button
    private val skipButtonResourceIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/ad_skip_button"
    )

    // Exact text of the skip button when clickable (NEVER include "skip in" as that is a timer)
    private val skipButtonTexts = listOf(
        "skip ad",
        "skip ads",
        "skip",
        "ignorar anuncio",
        "passer l'annonce",
        "überspringen",
        "saltar anuncio"
    )

    private val adIndicatorTexts = listOf(
        "ad ·",
        "ad •",
        "sponsored",
        "visit advertiser"
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

        // Throttle checks to prevent CPU loops and UI jitter
        val now = System.currentTimeMillis()

        val rootNode = rootInActiveWindow ?: return
        try {
            // 1. Scan for and click Skip Button (debounced to once every 800ms)
            if (now - lastSkipClickTime > 800) {
                val skipClicked = searchAndClickSkipButton(rootNode)
                if (skipClicked) {
                    lastSkipClickTime = now
                }
            }

            // 2. Audio Muting during unskippable ads
            val autoMuteEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, true)
            if (autoMuteEnabled) {
                handleAdAudioMuting(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing YouTube accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun searchAndClickSkipButton(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Check known skip button view IDs
        for (resId in skipButtonResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (performSafeClick(node)) {
                        onAdSkipped()
                        recycleNodes(nodes)
                        return true
                    }
                }
                recycleNodes(nodes)
            }
        }

        // 2. Search strictly for "Skip Ad" / "Skip Ads" text
        for (skipText in skipButtonTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(skipText)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()?.lowercase()?.trim() ?: ""
                    // Ensure it is an actual skip button, NOT a countdown timer like "Skip in 5s"
                    if (text == skipText || text == "skip ad" || text == "skip ads") {
                        if (performSafeClick(node)) {
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

    private fun performSafeClick(node: AccessibilityNodeInfo): Boolean {
        // Direct click
        if (node.isClickable) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                Log.d(TAG, "Safely clicked skip ad button directly")
                return true
            }
        }

        // Only check immediate parent if it's explicitly a Button or Frame
        val parent = node.parent
        if (parent != null) {
            val className = parent.className?.toString() ?: ""
            // Do NOT click arbitrary parents (like VideoView or WebLayout) as that opens the ad
            if (parent.isClickable && (className.contains("Button") || className.contains("Frame") || className.contains("Touch"))) {
                val clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                if (clicked) {
                    Log.d(TAG, "Safely clicked parent skip wrapper")
                    return true
                }
            } else {
                parent.recycle()
            }
        }
        return false
    }

    private fun handleAdAudioMuting(rootNode: AccessibilityNodeInfo) {
        val isAdShowing = isAdPresentOnScreen(rootNode)

        if (isAdShowing) {
            if (!isAdMuted) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol > 0) {
                    originalVolume = currentVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    isAdMuted = true
                    Log.d(TAG, "Ad detected on screen. Muting audio")
                }
            }
        } else {
            // Restore audio when ad is gone
            if (isAdMuted) {
                mainHandler.postDelayed({
                    if (originalVolume > 0 && isAdMuted) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                        Log.d(TAG, "Ad ended. Audio restored to $originalVolume")
                    }
                    isAdMuted = false
                    originalVolume = -1
                }, 400)
            }
        }
    }

    private fun isAdPresentOnScreen(rootNode: AccessibilityNodeInfo): Boolean {
        for (adText in adIndicatorTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(adText)
            if (!nodes.isNullOrEmpty()) {
                recycleNodes(nodes)
                return true
            }
        }
        return false
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
