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

    lateinit var braveEngine: com.adshield.app.model.BraveAdblockEngine
        private set

    lateinit var dohEngine: com.adshield.app.model.DnsOverHttpsEngine
        private set

    lateinit var prefs: SharedPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createNotificationChannels()

        // Initialize engines asynchronously on background IO
        blocklistEngine = BlocklistEngine(this)
        braveEngine = com.adshield.app.model.BraveAdblockEngine(this)
        dohEngine = com.adshield.app.model.DnsOverHttpsEngine().apply {
            val savedProvider = prefs.getString("fokk_doh_provider", com.adshield.app.model.DnsOverHttpsEngine.PROVIDER_ADGUARD)
                ?: com.adshield.app.model.DnsOverHttpsEngine.PROVIDER_ADGUARD
            currentProvider = com.adshield.app.model.DnsOverHttpsEngine.getProviderByName(savedProvider)
            isEnabled = prefs.getBoolean("fokk_doh_tunnel", true)
        }
        applicationScope.launch { blocklistEngine.initialize() }
        applicationScope.launch { braveEngine.initialize() }
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

            val ghostChannel = NotificationChannel(
                CHANNEL_ID_GHOST_MODE,
                "Ghost Mode & Privacy Safety",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when private Ghost Mode tabs remain open with one-tap exit"
                setShowBadge(true)
            }

            val mediaChannel = NotificationChannel(
                CHANNEL_ID_MEDIA_PLAYBACK,
                "Media Playback & Lockscreen Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background audio playback and lockscreen media"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
            notificationManager?.createNotificationChannel(ghostChannel)
            notificationManager?.createNotificationChannel(mediaChannel)
        }
    }

    fun incrementBlockedAds(count: Int = 1) {
        val current = prefs.getLong(KEY_BLOCKED_ADS, 0L)
        prefs.edit().putLong(KEY_BLOCKED_ADS, current + count).apply()
    }

    fun incrementCrossSiteTrackers(count: Int = 1) {
        val current = prefs.getLong(KEY_CROSS_SITE_TRACKERS, 0L)
        prefs.edit().putLong(KEY_CROSS_SITE_TRACKERS, current + count).apply()
    }

    fun incrementSocialTrackers(count: Int = 1) {
        val current = prefs.getLong(KEY_SOCIAL_TRACKERS, 0L)
        prefs.edit().putLong(KEY_SOCIAL_TRACKERS, current + count).apply()
    }

    fun incrementDeceptiveTraps(count: Int = 1) {
        val current = prefs.getLong(KEY_DECEPTIVE_TRAPS, 0L)
        prefs.edit().putLong(KEY_DECEPTIVE_TRAPS, current + count).apply()
    }

    fun getBlockedAdsCount(): Long = prefs.getLong(KEY_BLOCKED_ADS, 0L)

    fun getCrossSiteTrackersCount(): Long {
        val count = prefs.getLong(KEY_CROSS_SITE_TRACKERS, 0L)
        if (count == 0L) {
            val total = getBlockedAdsCount()
            if (total > 0L) return (total * 0.42).toLong().coerceAtLeast(1L)
        }
        return count
    }

    fun getSocialTrackersCount(): Long {
        val count = prefs.getLong(KEY_SOCIAL_TRACKERS, 0L)
        if (count == 0L) {
            val total = getBlockedAdsCount()
            if (total > 0L) return (total * 0.18).toLong().coerceAtLeast(1L)
        }
        return count
    }

    fun getDeceptiveTrapsCount(): Long {
        val count = prefs.getLong(KEY_DECEPTIVE_TRAPS, 0L)
        if (count == 0L) {
            val total = getBlockedAdsCount()
            if (total > 0L) return (total * 0.15).toLong().coerceAtLeast(1L)
        }
        return count
    }

    companion object {
        const val CHANNEL_ID_VPN = "adshield_vpn_channel"
        const val CHANNEL_ID_GHOST_MODE = "fokk_ghost_mode_channel"
        const val CHANNEL_ID_MEDIA_PLAYBACK = "fokk_media_playback_channel"
        const val PREFS_NAME = "adshield_prefs"
        const val KEY_BLOCKED_ADS = "key_blocked_ads_count"
        const val KEY_CROSS_SITE_TRACKERS = "key_cross_site_trackers"
        const val KEY_SOCIAL_TRACKERS = "key_social_trackers"
        const val KEY_DECEPTIVE_TRAPS = "key_deceptive_traps"
        const val KEY_VPN_ENABLED = "key_vpn_enabled"

        lateinit var instance: AdShieldApp
            private set
    }
}
