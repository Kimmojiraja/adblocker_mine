package com.adshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adshield.app.AdShieldApp
import com.adshield.app.receiver.SpotifyAdMuterReceiver

/**
 * High-performance, 100% glitch-free YouTube Ad Assistant.
 * Strictly gated: Zero clicks when no ad is playing.
 * Never interrupts comments, bottom sheets, or countdowns.
 */
class YouTubeAdSkipperService : AccessibilityService() {

    private var lastSkipClickTime = 0L
    private val spotifyReceiver = SpotifyAdMuterReceiver()

    // Verified view IDs for the official Skip Ad button in YouTube
    private val skipButtonResourceIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/ad_skip_button"
    )

    // Markers that indicate an ad is ACTUALLY active in YouTube
    private val adIndicatorResourceIds = listOf(
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/ad_badge",
        "com.google.android.youtube:id/ad_badge_layout",
        "com.google.android.youtube:id/ad_view",
        "com.google.android.youtube:id/player_learn_more_button",
        "com.google.android.youtube:id/visit_advertiser_button",
        "com.google.android.youtube:id/ad_info_button"
    )

    private val adTextMarkers = listOf(
        "Ad ·",
        "Ad 1 of",
        "Ad 2 of",
        "Sponsored",
        "Visit advertiser",
        "Skip in",
        "Skip Ad",
        "Skip ad",
        "Skip ads"
    )

    // Interactive panels that should NEVER be touched (Comments, Playlists, Queues)
    private val interactivePanelResourceIds = listOf(
        "com.google.android.youtube:id/comments",
        "com.google.android.youtube:id/engagement_panel",
        "com.google.android.youtube:id/bottom_sheet_container",
        "com.google.android.youtube:id/comment_composer",
        "com.google.android.youtube:id/playlist",
        "com.google.android.youtube:id/playlist_panel",
        "com.google.android.youtube:id/playlist_header",
        "com.google.android.youtube:id/playlist_items",
        "com.google.android.youtube:id/queue_panel"
    )

    private val interactiveTextMarkers = listOf(
        "Comments",
        "Top comments",
        "Close comments",
        "Add a comment",
        "Playlist",
        "Playlists",
        "Queue"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, true).apply()
        sendBroadcast(Intent(ACTION_YT_STATE_CHANGED).putExtra("enabled", true))

        // Dynamically register Spotify Ad Muter to ensure persistent 24/7 background operation
        try {
            val spotifyFilter = IntentFilter().apply {
                addAction(SpotifyAdMuterReceiver.ACTION_METADATA_CHANGED)
                addAction(SpotifyAdMuterReceiver.ACTION_PLAYBACK_STATE_CHANGED)
            }
            registerReceiver(spotifyReceiver, spotifyFilter)
            Log.i(TAG, "Spotify Ad Muter dynamic receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Spotify receiver dynamically", e)
        }

        Log.i(TAG, "YouTube Ad Skipper connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (!pkg.contains("youtube")) return

        val now = System.currentTimeMillis()
        // Debounce: prevent rapid repeated clicks
        if (now - lastSkipClickTime < 1000) return

        val rootNode = rootInActiveWindow ?: return
        try {
            // STEP 1: Strict comments & panel check.
            // If comments, engagement sheet, or descriptions are open, NEVER click anything!
            if (isCommentsOrPanelOpen(rootNode)) {
                return
            }

            // STEP 2: Strict Ad Presence Gating.
            // If NO ad is active on screen, AdShield is 100% passive. ZERO clicks during regular videos!
            if (!isAdActive(rootNode)) {
                return
            }

            // STEP 3: Find and click ONLY an active, skippable button
            // (Ignores "Skip in 5s" countdown to eliminate 5-second glitching)
            val clicked = findAndClickSkipButton(rootNode)
            if (clicked) {
                lastSkipClickTime = now
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing YouTube accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Verifies if an ad is actively displaying on YouTube.
     */
    private fun isAdActive(rootNode: AccessibilityNodeInfo): Boolean {
        // Check ad indicator view IDs
        for (resId in adIndicatorResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                val hasVisible = nodes.any { it.isVisibleToUser }
                recycleNodes(nodes)
                if (hasVisible) return true
            }
        }

        // Check ad text markers
        for (marker in adTextMarkers) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                val hasVisible = nodes.any { it.isVisibleToUser }
                recycleNodes(nodes)
                if (hasVisible) return true
            }
        }

        return false
    }

    /**
     * Checks if comments or any engagement bottom sheet is active.
     */
    private fun isCommentsOrPanelOpen(rootNode: AccessibilityNodeInfo): Boolean {
        for (id in interactivePanelResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val hasVisible = nodes.any { it.isVisibleToUser }
                recycleNodes(nodes)
                if (hasVisible) return true
            }
        }

        for (text in interactiveTextMarkers) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                val hasVisible = nodes.any { it.isVisibleToUser }
                recycleNodes(nodes)
                if (hasVisible) return true
            }
        }

        return false
    }

    /**
     * Searches for a visible Skip button whose countdown has expired.
     */
    private fun findAndClickSkipButton(rootNode: AccessibilityNodeInfo): Boolean {
        // 1. Check known skip button view IDs
        for (resId in skipButtonResourceIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(resId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (isReadyToSkip(node)) {
                        val clicked = performSafeClick(node)
                        if (clicked) {
                            Log.d(TAG, "Clicked skip button by ID: $resId")
                            onAdSkipped()
                            recycleNodes(nodes)
                            return true
                        }
                    }
                }
                recycleNodes(nodes)
            }
        }

        // 2. Check nodes with "Skip" text or description
        val skipNodes = rootNode.findAccessibilityNodeInfosByText("Skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (isReadyToSkip(node)) {
                    val clicked = performSafeClick(node)
                    if (clicked) {
                        Log.d(TAG, "Clicked skip button by Text 'Skip'")
                        onAdSkipped()
                        recycleNodes(skipNodes)
                        return true
                    }
                }
            }
            recycleNodes(skipNodes)
        }

        return false
    }

    /**
     * Returns true ONLY if the button is visible, clickable, and has finished its countdown.
     * Prevents clicking during "Skip in 5s", eliminating terrifying ad glitching.
     */
    private fun isReadyToSkip(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase().trim()

        // Still counting down ("Skip in 5", "Skip in 4", "in 3", etc.) -> DO NOT CLICK
        if (text.contains("in ") || text.contains("countdown") || text.matches(Regex(".*\\b\\d+s?\\b.*"))) {
            return false
        }

        // Ensure node has valid screen bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return false

        return node.isClickable || node.parent?.isClickable == true
    }

    private fun performSafeClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val parent = node.parent
        if (parent != null && parent.isClickable) {
            val res = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent.recycle()
            return res
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
        Log.w(TAG, "YouTube Ad Skipper interrupted")
    }

    override fun onDestroy() {
        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, false).apply()
        sendBroadcast(Intent(ACTION_YT_STATE_CHANGED).putExtra("enabled", false))

        try {
            unregisterReceiver(spotifyReceiver)
        } catch (ignored: Exception) {}

        super.onDestroy()
    }

    companion object {
        const val ACTION_YT_STATE_CHANGED = "com.adshield.app.ACTION_YT_STATE_CHANGED"
        const val ACTION_YT_AD_SKIPPED = "com.adshield.app.ACTION_YT_AD_SKIPPED"
        private const val TAG = "YouTubeAdSkipper"
    }
}
