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

    private val skipButtonResourceIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/sub_ad_skip_button",
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/skip_ad_button_text"
    )

    private val skipButtonTexts = listOf(
        "skip ad",
        "skip ads",
        "skip in",
        "skip",
        "ignorar",
        "passer",
        "überspringen",
        "saltar"
    )

    private val adIndicatorTexts = listOf(
        "ad ·",
        "ad •",
        "sponsored",
        "visit advertiser",
        "install now",
        "anuncio"
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

        val rootNode = rootInActiveWindow ?: return
        try {
            val autoMuteEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, true)

            // 1. Scan for and click Skip Button
            val skipClicked = searchAndClickSkipButton(rootNode)

            // 2. Scan for and dismiss overlay/banner ads
            dismissOverlayBanners(rootNode)

            // 3. Audio Muting during unskippable ads
            if (autoMuteEnabled) {
                handleAdAudioMuting(rootNode, skipClicked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing YouTube accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun searchAndClickSkipButton(rootNode: AccessibilityNodeInfo): Boolean {
        // Try resource IDs first
        for (resId in skipButtonResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (tryClickNode(node)) {
                        onAdSkipped()
                        recycleNodes(nodes)
                        return true
                    }
                }
                recycleNodes(nodes)
            }
        }

        // Search by text content
        for (skipText in skipButtonTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(skipText)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (tryClickNode(node)) {
                        onAdSkipped()
                        recycleNodes(nodes)
                        return true
                    }
                }
                recycleNodes(nodes)
            }
        }

        return false
    }

    private fun tryClickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Log.d(TAG, "Successfully clicked skip ad button")
                return true
            }
        }

        // If parent is clickable (common for styled wrapper buttons)
        val parent = node.parent
        if (parent != null) {
            if (parent.isClickable) {
                val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                if (success) {
                    Log.d(TAG, "Successfully clicked parent skip ad button")
                    return true
                }
            } else {
                parent.recycle()
            }
        }
        return false
    }

    private fun dismissOverlayBanners(rootNode: AccessibilityNodeInfo) {
        val closeIds = listOf(
            "com.google.android.youtube:id/close_button",
            "com.google.android.youtube:id/dismiss_button"
        )
        for (closeId in closeIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(closeId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
                recycleNodes(nodes)
            }
        }
    }

    private fun handleAdAudioMuting(rootNode: AccessibilityNodeInfo, skipClicked: Boolean) {
        val isAdShowing = isAdPresentOnScreen(rootNode)

        if (isAdShowing && !skipClicked) {
            if (!isAdMuted) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol > 0) {
                    originalVolume = currentVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    isAdMuted = true
                    Log.d(TAG, "Unskippable ad detected. Audio muted (saved volume $currentVol)")
                }
            }
        } else {
            // Restore audio if previously muted
            if (isAdMuted) {
                mainHandler.postDelayed({
                    if (originalVolume > 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                        Log.d(TAG, "Ad ended. Audio restored to $originalVolume")
                    }
                    isAdMuted = false
                    originalVolume = -1
                }, 300)
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
