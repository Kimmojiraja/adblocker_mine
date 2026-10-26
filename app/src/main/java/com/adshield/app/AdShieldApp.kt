package com.adshield.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.adshield.app.model.BlocklistEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AdShieldApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var blocklistEngine: BlocklistEngine
        private set

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createNotificationChannels()

        // Initialize Blocklist Engine asynchronously
        blocklistEngine = BlocklistEngine(this)
        applicationScope.launch {
            blocklistEngine.initialize()
            com.adshield.app.model.SelectorSyncManager.syncSelectorsAsync()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_VPN,
                "AdShield Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live status and statistics for active ad blocking"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun incrementBlockedAds() {
        val current = prefs.getLong(KEY_BLOCKED_ADS, 0L)
        prefs.edit().putLong(KEY_BLOCKED_ADS, current + 1).apply()
    }

    fun incrementSkippedYouTubeAds() {
        val current = prefs.getLong(KEY_SKIPPED_YT_ADS, 0L)
        prefs.edit().putLong(KEY_SKIPPED_YT_ADS, current + 1).apply()
    }

    fun incrementMutedSpotifyAds() {
        val current = prefs.getLong(KEY_MUTED_SPOTIFY_ADS, 0L)
        prefs.edit().putLong(KEY_MUTED_SPOTIFY_ADS, current + 1).apply()
    }

    fun getBlockedAdsCount(): Long = prefs.getLong(KEY_BLOCKED_ADS, 0L)
    fun getSkippedYouTubeAdsCount(): Long = prefs.getLong(KEY_SKIPPED_YT_ADS, 0L)
    fun getMutedSpotifyAdsCount(): Long = prefs.getLong(KEY_MUTED_SPOTIFY_ADS, 0L)

    companion object {
        const val CHANNEL_ID_VPN = "adshield_vpn_channel"
        const val PREFS_NAME = "adshield_prefs"
        const val KEY_BLOCKED_ADS = "key_blocked_ads_count"
        const val KEY_SKIPPED_YT_ADS = "key_skipped_yt_ads_count"
        const val KEY_MUTED_SPOTIFY_ADS = "key_muted_spotify_ads_count"
        const val KEY_VPN_ENABLED = "key_vpn_enabled"
        const val KEY_YT_SKIPPER_ENABLED = "key_yt_skipper_enabled"
        const val KEY_SPOTIFY_MUTER_ENABLED = "key_spotify_muter_enabled"
        const val KEY_AUTO_MUTE_ENABLED = "key_auto_mute_enabled"
        const val KEY_UPSTREAM_DNS = "key_upstream_dns"

        lateinit var instance: AdShieldApp
            private set
    }
}
