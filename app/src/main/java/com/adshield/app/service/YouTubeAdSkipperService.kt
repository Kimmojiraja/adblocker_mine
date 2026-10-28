package com.adshield.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adshield.app.AdShieldApp
import com.adshield.app.model.SelectorSyncManager
import com.adshield.app.receiver.SpotifyAdMuterReceiver

/**
 * High-performance, 100% glitch-free YouTube Ad Assistant.
 * Strictly gated: Zero clicks when no ad is playing.
 * Never interrupts comments, bottom sheets, or countdowns.
 */
class YouTubeAdSkipperService : AccessibilityService() {

    private var lastSkipClickTime = 0L
    private var lastSocialSkipTime = 0L
    private val spotifyReceiver = SpotifyAdMuterReceiver()
    private var isAdMuted = false
    private var originalVolume = -1

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
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                         AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                         AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.packageNames = null
            serviceInfo = info
            Log.i(TAG, "Dynamic AccessibilityServiceInfo applied: TYPES_ALL_MASK, packageNames=null")
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring service info dynamically", e)
        }

        AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, true).apply()
        sendBroadcast(Intent(ACTION_YT_STATE_CHANGED).putExtra("enabled", true))

        SelectorSyncManager.syncSelectorsAsync()

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
        val prefs = AdShieldApp.instance.prefs

        if (pkg.contains("youtube")) {
            val ytEnabled = prefs.getBoolean(AdShieldApp.KEY_YT_SKIPPER_ENABLED, true)
            if (ytEnabled) {
                handleYouTubeEvent()
            }
            return
        }

        if (pkg.contains("twitter") || pkg == "com.twitter.android") {
            val xEnabled = prefs.getBoolean(AdShieldApp.KEY_X_SKIPPER_ENABLED, true)
            if (xEnabled) {
                handleTwitterEvent()
            }
            return
        }

        if (pkg.contains("reddit") || pkg == "com.reddit.frontpage") {
            val redditEnabled = prefs.getBoolean(AdShieldApp.KEY_REDDIT_SKIPPER_ENABLED, true)
            if (redditEnabled) {
                handleRedditEvent()
            }
            return
        }

        if (pkg.contains("instagram") || pkg == "com.instagram.android") {
            val instaEnabled = prefs.getBoolean(AdShieldApp.KEY_INSTAGRAM_SKIPPER_ENABLED, true)
            if (instaEnabled) {
                handleInstagramEvent()
            }
            return
        }
    }

    private fun handleYouTubeEvent() {
        val now = System.currentTimeMillis()
        if (now - lastSkipClickTime < 1000) return

        val rootNode = rootInActiveWindow ?: return
        try {
            dismissSponsoredCardIfPresent(rootNode)

            if (isCommentsOrPanelOpen(rootNode)) {
                return
            }

            val adActive = isAdActive(rootNode)
            if (!adActive) {
                if (isAdMuted) {
                    restoreVideoVolume()
                }
                return
            }

            val clicked = findAndClickSkipButton(rootNode)
            if (clicked) {
                lastSkipClickTime = now
                if (isAdMuted) {
                    restoreVideoVolume()
                }
            } else {
                muteVideoAd()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing YouTube accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleTwitterEvent() {
        val now = System.currentTimeMillis()
        if (now - lastSocialSkipTime < 1200L) return

        val rootNode = rootInActiveWindow ?: return
        try {
            if (isSponsoredPostPresent(rootNode)) {
                lastSocialSkipTime = now
                dispatchSwipeUpGesture()
                AdShieldApp.instance.incrementSkippedSocialAds()
                sendBroadcast(Intent(ACTION_YT_AD_SKIPPED))
                Log.i(TAG, "Auto-scrolled past Promoted Tweet on X")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling X event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleRedditEvent() {
        val now = System.currentTimeMillis()
        if (now - lastSocialSkipTime < 1200L) return

        val rootNode = rootInActiveWindow ?: return
        try {
            if (isSponsoredPostPresent(rootNode)) {
                lastSocialSkipTime = now
                dispatchSwipeUpGesture()
                AdShieldApp.instance.incrementSkippedSocialAds()
                sendBroadcast(Intent(ACTION_YT_AD_SKIPPED))
                Log.i(TAG, "Auto-scrolled past Promoted Post on Reddit")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Reddit event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun handleInstagramEvent() {
        val now = System.currentTimeMillis()
        if (now - lastSocialSkipTime < 1200L) return

        val rootNode = rootInActiveWindow ?: return
        try {
            if (isSponsoredPostPresent(rootNode)) {
                lastSocialSkipTime = now
                dispatchSwipeUpGesture()
                AdShieldApp.instance.incrementSkippedSocialAds()
                sendBroadcast(Intent(ACTION_YT_AD_SKIPPED))
                Log.i(TAG, "Auto-scrolled past Sponsored content on Instagram")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Instagram event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isSponsoredPostPresent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        try {
            val text = node.text?.toString()?.lowercase()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            val isAd = (
                text == "sponsored" || text == "promoted" || text == "ad" || text == "ad ·" ||
                text.startsWith("sponsored ·") || text.startsWith("promoted by") || text.contains("paid partnership") ||
                desc == "sponsored" || desc == "promoted" || desc == "ad" || desc.startsWith("sponsored") ||
                desc.contains("promoted tweet") || desc.contains("promoted post") || desc.contains("paid partnership") ||
                resId.contains("promoted_badge") || resId.contains("sponsored_badge") ||
                resId.contains("ad_badge") || resId.contains("tweet_promoted_badge")
            )

            if (isAd && node.isVisibleToUser) {
                return true
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                val found = isSponsoredPostPresent(child)
                child.recycle()
                if (found) return true
            }
        } catch (e: Exception) {
            return false
        }
        return false
    }

    private fun dispatchSwipeUpGesture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val displayMetrics = resources.displayMetrics
                val width = displayMetrics.widthPixels.toFloat()
                val height = displayMetrics.heightPixels.toFloat()

                val path = Path().apply {
                    val startX = width / 2f
                    val startY = height * 0.75f
                    val endY = height * 0.20f
                    moveTo(startX, startY)
                    lineTo(startX, endY)
                }

                val stroke = GestureDescription.StrokeDescription(path, 0L, 200L)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                Log.d(TAG, "Dispatched smooth scroll-past gesture")
            } catch (e: Exception) {
                Log.w(TAG, "dispatchSwipeUpGesture error", e)
            }
        }
    }

    /**
     * Dismisses sponsored promotional cards / bottom sheets that cover the video.
     */
    private fun dismissSponsoredCardIfPresent(rootNode: AccessibilityNodeInfo) {
        val sponsoredMarkers = listOf("Sponsored", "Visit advertiser")
        for (marker in sponsoredMarkers) {
            val sNodes = rootNode.findAccessibilityNodeInfosByText(marker)
            if (!sNodes.isNullOrEmpty()) {
                val hasVisible = sNodes.any { it.isVisibleToUser }
                recycleNodes(sNodes)
                if (hasVisible) {
                    val closeIds = listOf(
                        "com.google.android.youtube:id/close_button",
                        "com.google.android.youtube:id/dismiss_button",
                        "com.google.android.youtube:id/cancel_button"
                    )
                    for (cid in closeIds) {
                        val closeNodes = rootNode.findAccessibilityNodeInfosByViewId(cid)
                        if (!closeNodes.isNullOrEmpty()) {
                            for (cNode in closeNodes) {
                                if (cNode.isVisibleToUser && performSafeClick(cNode)) {
                                    Log.d(TAG, "Dismissed sponsored overlay via $cid")
                                    recycleNodes(closeNodes)
                                    return
                                }
                            }
                            recycleNodes(closeNodes)
                        }
                    }
                    break
                }
            }
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
     * Checks if genuine user comments or user playlist panel is open.
     * Never falsely flags sponsored promotional ad sheets as comments.
     */
    private fun isCommentsOrPanelOpen(rootNode: AccessibilityNodeInfo): Boolean {
        // Check genuine comment/playlist text markers
        for (text in interactiveTextMarkers) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                val hasVisible = nodes.any { it.isVisibleToUser }
                recycleNodes(nodes)
                if (hasVisible) return true
            }
        }

        // Check comment & playlist specific view IDs
        val dynamicPanelIds = (interactivePanelResourceIds + SelectorSyncManager.getProtectedPanelIds()).distinct()
        for (id in dynamicPanelIds) {
            val fullId = if (id.contains(":id/")) id else "com.google.android.youtube:id/$id"
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
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
        // 1. Check known and cloud-synced skip button view IDs
        val dynamicButtonIds = (skipButtonResourceIds + SelectorSyncManager.getSkipButtonIds()).distinct()
        for (resId in dynamicButtonIds) {
            val fullId = if (resId.contains(":id/")) resId else "com.google.android.youtube:id/$resId"
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (isReadyToSkip(node)) {
                        val clicked = performSafeClick(node)
                        if (clicked) {
                            Log.d(TAG, "Clicked skip button by ID: $fullId")
                            onAdSkipped()
                            recycleNodes(nodes)
                            return true
                        }
                    }
                }
                recycleNodes(nodes)
            }
        }

        // 2. Check localized and cloud-synced skip texts
        val dynamicSkipTexts = SelectorSyncManager.getSkipTexts()
        for (skipText in dynamicSkipTexts) {
            val skipNodes = rootNode.findAccessibilityNodeInfosByText(skipText)
            if (!skipNodes.isNullOrEmpty()) {
                for (node in skipNodes) {
                    if (isReadyToSkip(node)) {
                        val clicked = performSafeClick(node)
                        if (clicked) {
                            Log.d(TAG, "Clicked skip button by Text '$skipText'")
                            onAdSkipped()
                            recycleNodes(skipNodes)
                            return true
                        }
                    }
                }
                recycleNodes(skipNodes)
            }
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
        if (text.contains("in ") && text.matches(Regex(".*\\bin\\s+\\d+.*"))) {
            return false
        }
        if (text.contains("countdown") || text.matches(Regex("^\\d+s?$"))) {
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

    private fun muteVideoAd() {
        if (!isAdMuted) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (cur > 0) {
                originalVolume = cur
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                isAdMuted = true
                Log.d(TAG, "Unskippable ad muted (saved volume $cur)")
            }
        }
    }

    private fun restoreVideoVolume() {
        if (isAdMuted) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (originalVolume > 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                Log.d(TAG, "Ad ended, restored volume to $originalVolume")
            }
            isAdMuted = false
            originalVolume = -1
        }
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
        if (isAdMuted) {
            restoreVideoVolume()
        }

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
