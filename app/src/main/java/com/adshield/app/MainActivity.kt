package com.adshield.app

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adshield.app.databinding.ActivityMainBinding
import com.adshield.app.model.BrandOptimizationHelper
import com.adshield.app.service.YouTubeAdSkipperService
import com.adshield.app.ui.BrandGuideActivity
import com.adshield.app.ui.MediaHubActivity
import com.adshield.app.ui.SettingsActivity

import com.adshield.app.receiver.SpotifyAdMuterReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateDashboard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(YouTubeAdSkipperService.ACTION_YT_STATE_CHANGED)
            addAction(YouTubeAdSkipperService.ACTION_YT_AD_SKIPPED)
            addAction(SpotifyAdMuterReceiver.ACTION_SPOTIFY_AD_MUTED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        updateDashboard()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(receiver)
        } catch (ignored: Exception) {}
    }

    private fun setupListeners() {
        // YouTube Ad Skipper Switch
        binding.switchYtSkipper.setOnCheckedChangeListener { _, isChecked ->
            val isServiceActive = isAccessibilityServiceEnabled(this, YouTubeAdSkipperService::class.java)
            if (isChecked && !isServiceActive) {
                promptEnableAccessibility()
            } else if (!isChecked && isServiceActive) {
                Toast.makeText(this, "To disable, turn off 'AdShield YouTube Assistant' in Accessibility Settings", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Help button for OnePlus / Xiaomi / Restricted Settings
        binding.btnPermissionHelp.setOnClickListener {
            showRestrictedSettingsGuide()
        }

        // Spotify Ad Silencer Switch
        val isSpotifyEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_SPOTIFY_MUTER_ENABLED, true)
        binding.switchSpotifyMuter.isChecked = isSpotifyEnabled
        binding.switchSpotifyMuter.setOnCheckedChangeListener { _, isChecked ->
            AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_SPOTIFY_MUTER_ENABLED, isChecked).apply()
            val msg = if (isChecked) "Spotify Ad Silencer active" else "Spotify Ad Silencer paused"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Spotify Help Button
        binding.btnSpotifyHelp.setOnClickListener {
            showSpotifyGuide()
        }

        // Feature 2: Ad-Free Media Hub Launcher
        binding.btnOpenMediaHub.setOnClickListener {
            startActivity(Intent(this, MediaHubActivity::class.java))
        }

        // Feature 3: One-Tap Zero-VPN System Ad Blocker
        binding.btnSetupPrivateDns.setOnClickListener {
            setupNativePrivateDns()
        }

        // Feature 4: Brand Battery Optimization
        binding.cardBrandOptimization.setOnClickListener {
            startActivity(Intent(this, BrandGuideActivity::class.java))
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showSpotifyGuide() {
        AlertDialog.Builder(this)
            .setTitle("Spotify Ad Silencer Setup")
            .setMessage("To allow AdShield to detect and silence Spotify ads without VPN:\n\n1. Open Spotify.\n2. Tap Settings (⚙️ icon in top-right).\n3. Scroll down and turn ON 'Device Broadcast Status'.\n\nAdShield will now automatically drop volume to 0 during ads and restore it the second your song resumes!")
            .setPositiveButton("Open Spotify") { _, _ ->
                val launchIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "Spotify app not installed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Done", null)
            .show()
    }

    private fun promptEnableAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Enable YouTube Ad Assistant")
            .setMessage("AdShield automatically clicks 'Skip Ad' in 0.05 seconds and mutes unskippable ads.\n\n👉 On OnePlus / Oppo / Xiaomi:\n1. Tap 'Open Settings'\n2. Scroll to the bottom and tap 'Downloaded apps'\n3. Tap 'AdShield YouTube Assistant' and turn it ON.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("Fix 'Denied Access'") { _, _ ->
                openAppInfoScreen()
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchYtSkipper.isChecked = false
            }
            .show()
    }

    private fun showRestrictedSettingsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Fix 'App was denied access'")
            .setMessage("Android 13 & 14 locks accessibility for sideloaded apps by default.\n\nTo unlock it:\n1. Tap 'Open App Info' below.\n2. Tap the 3 dots (⋮) in the top-right corner.\n3. Tap 'Allow restricted settings'.\n4. Confirm with fingerprint/PIN, then return to turn on the assistant!")
            .setPositiveButton("Open App Info") { _, _ ->
                openAppInfoScreen()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAppInfoScreen() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please open Settings > Apps > AdShield manually", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNativePrivateDns() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Control D Ad Blocker", "p2.freedns.controld.com")
        clipboard.setPrimaryClip(clip)

        AlertDialog.Builder(this)
            .setTitle("Copied 'p2.freedns.controld.com'!")
            .setMessage("Now opening your phone's Network Settings:\n\n1. Tap 'Private DNS' (or Specified DNS).\n2. Select 'Private DNS provider hostname'.\n3. Paste 'p2.freedns.controld.com' and tap Save!\n\n(100% unblocked in India on Jio, Airtel & 5G — blocks ads in games and Chrome with zero VPN!)")
            .setPositiveButton("Open Network Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (ex: Exception) {
                        Toast.makeText(this, "Please open phone Settings > Network & Internet > Private DNS", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateDashboard() {
        val isYtActive = isAccessibilityServiceEnabled(this, YouTubeAdSkipperService::class.java)
        binding.switchYtSkipper.isChecked = isYtActive

        val skippedYtCount = AdShieldApp.instance.getSkippedYouTubeAdsCount()
        binding.textYtSkippedCount.text = skippedYtCount.toString()

        val mutedSpotifyCount = AdShieldApp.instance.getMutedSpotifyAdsCount()
        binding.textSpotifyMutedCount.text = mutedSpotifyCount.toString()

        val isSpotifyEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_SPOTIFY_MUTER_ENABLED, true)
        binding.switchSpotifyMuter.isChecked = isSpotifyEnabled

        // Brand Card details
        val brandName = BrandOptimizationHelper.getBrandName()
        val isIgnoring = BrandOptimizationHelper.isIgnoringBatteryOptimizations(this)
        binding.textBrandTitle.text = "$brandName Background Protection"
        binding.textBrandStatus.text = if (isIgnoring) "Protected from system battery killer" else "Tap to whitelist on $brandName"
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, service).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
