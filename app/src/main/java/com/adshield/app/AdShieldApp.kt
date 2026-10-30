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

    fun incrementBlockedAds(count: Int = 1) {
        val current = prefs.getLong(KEY_BLOCKED_ADS, 0L)
        prefs.edit().putLong(KEY_BLOCKED_ADS, current + count).apply()
    }

    fun getBlockedAdsCount(): Long = prefs.getLong(KEY_BLOCKED_ADS, 0L)

    companion object {
        const val CHANNEL_ID_VPN = "adshield_vpn_channel"
        const val PREFS_NAME = "adshield_prefs"
        const val KEY_BLOCKED_ADS = "key_blocked_ads_count"
        const val KEY_VPN_ENABLED = "key_vpn_enabled"

        lateinit var instance: AdShieldApp
            private set
    }
}
