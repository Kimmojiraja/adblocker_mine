package com.adshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.app.ActivityManager
import android.util.Log
import com.adshield.app.AdShieldApp

class SpotifyAdMuterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val isEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_SPOTIFY_MUTER_ENABLED, true)
        val autoMuteEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, true)
        if (!isEnabled || !autoMuteEnabled || !isSpotifyInForeground(context)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val action = intent.action ?: return

        if (action == ACTION_METADATA_CHANGED || action == ACTION_PLAYBACK_STATE_CHANGED) {
            val trackId = intent.getStringExtra("id") ?: ""
            val trackName = intent.getStringExtra("track") ?: ""
            val isPlaying = intent.getBooleanExtra("playing", false)

            Log.d(TAG, "Spotify event: trackId=$trackId, trackName=$trackName, playing=$isPlaying")

            val isAd = isSpotifyAd(trackId, trackName)

            if (isAd && isPlaying) {
                if (!isMuted) {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (currentVol > 0) {
                        savedVolume = currentVol
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                        isMuted = true
                        AdShieldApp.instance.incrementMutedSpotifyAds()
                        context.sendBroadcast(Intent(ACTION_SPOTIFY_AD_MUTED))
                        Log.i(TAG, "Muted Spotify advertisement ($trackName)")
                    }
                }
            } else {
                // Real track playing
                if (isMuted) {
                    // Do not overwrite a volume adjustment the user made while the ad was muted.
                    if (savedVolume > 0 && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                        Log.i(TAG, "Restored audio volume for song: $trackName")
                    }
                    isMuted = false
                    savedVolume = -1
                }
            }
        }
    }

    private fun isSpotifyAd(trackId: String, trackName: String): Boolean {
        if (trackId.contains(":ad:") || trackId.startsWith("spotify:ad:")) return true
        val lowerTrack = trackName.lowercase().trim()
        if (lowerTrack == "advertisement" || lowerTrack == "spotify") return true
        return false
    }

    /** Only act while the Spotify UI is actually foregrounded, never during background playback. */
    private fun isSpotifyInForeground(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val processes = manager.runningAppProcesses ?: return false
        return processes.any { process ->
            process.processName == SPOTIFY_PACKAGE &&
                process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    companion object {
        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_SPOTIFY_AD_MUTED = "com.adshield.app.ACTION_SPOTIFY_AD_MUTED"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val TAG = "SpotifyAdMuter"

        private var isMuted = false
        private var savedVolume = -1
    }
}
