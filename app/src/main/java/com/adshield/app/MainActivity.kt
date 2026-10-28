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
import com.adshield.app.receiver.SpotifyAdMuterReceiver
import com.adshield.app.service.YouTubeAdSkipperService
import com.adshield.app.ui.BrandGuideActivity
import com.adshield.app.ui.MediaHubActivity
import com.adshield.app.ui.SettingsActivity
import java.util.Locale

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
                Toast.makeText(this, "To disable, turn off 'Fokk Shield Assistant' in Accessibility Settings", Toast.LENGTH_LONG).show()
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

        // Instagram Skipper Switch
        val isInstaEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_INSTAGRAM_SKIPPER_ENABLED, true)
        binding.switchInstagramSkipper.isChecked = isInstaEnabled
        binding.switchInstagramSkipper.setOnCheckedChangeListener { _, isChecked ->
            AdShieldApp.instance.prefs.edit().putBoolean(AdShieldApp.KEY_INSTAGRAM_SKIPPER_ENABLED, isChecked).apply()
            val isServiceActive = isAccessibilityServiceEnabled(this, YouTubeAdSkipperService::class.java)
            if (isChecked && !isServiceActive) {
                promptEnableAccessibility()
            } else {
                val msg = if (isChecked) "Instagram Sponsored Skipper active" else "Instagram Sponsored Skipper paused"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }



        // Feature 3: Ad-Free Media Hub Launcher
        binding.btnOpenMediaHub.setOnClickListener {
            startActivity(Intent(this, MediaHubActivity::class.java))
        }

        // Feature 4: One-Tap Zero-VPN System Ad Blocker
        binding.btnSetupPrivateDns.setOnClickListener {
            setupNativePrivateDns()
        }

        // Feature 5: Brand Battery Optimization
        binding.cardBrandOptimization.setOnClickListener {
            startActivity(Intent(this, BrandGuideActivity::class.java))
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Transparency & Community Modules
        binding.btnPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        binding.btnDisclaimer.setOnClickListener {
            showDisclaimerDialog()
        }

        binding.btnSupport.setOnClickListener {
            showSupportDialog()
        }

        binding.btnFundraising.setOnClickListener {
            showFundraisingDialog()
        }
    }

    private fun showSpotifyGuide() {
        AlertDialog.Builder(this)
            .setTitle("Spotify Ad Silencer Setup")
            .setMessage("To enable Fokk Shield to detect and silence Spotify ads without VPN:\n\n1. Open the official Spotify app.\n2. Tap Settings (gear icon in top-right).\n3. Scroll down and turn ON 'Device Broadcast Status'.\n\nFokk Shield automatically drops media volume to zero during ads and restores volume the exact millisecond music resumes.")
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
            .setMessage("Fokk Shield automatically clicks 'Skip Ad' in 0.05 seconds and silences unskippable bumper ads.\n\nOn OnePlus, Oppo, Xiaomi, and Vivo:\n1. Tap 'Open Settings'\n2. Scroll down to 'Downloaded apps'\n3. Select 'Fokk Shield Assistant' and switch it ON.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton("Fix Restricted Settings") { _, _ ->
                openAppInfoScreen()
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchYtSkipper.isChecked = false
            }
            .show()
    }

    private fun showRestrictedSettingsGuide() {
        AlertDialog.Builder(this)
            .setTitle("Unlock Restricted Settings")
            .setMessage("Android 13, 14, and 15 lock accessibility permissions for sideloaded APKs by default.\n\nTo unlock:\n1. Tap 'Open App Info' below.\n2. Tap the menu (three dots) in top-right corner.\n3. Tap 'Allow restricted settings'.\n4. Confirm with PIN/Fingerprint, then return to activate the assistant.")
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
            Toast.makeText(this, "Please open Settings > Apps > Fokk Shield manually", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNativePrivateDns() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Control D Ad Blocker", "p2.freedns.controld.com")
        clipboard.setPrimaryClip(clip)

        AlertDialog.Builder(this)
            .setTitle("Copied DNS Hostname")
            .setMessage("Control D DNS address copied to clipboard.\n\nSetup instructions:\n1. Open your phone's Network & Internet Settings.\n2. Tap 'Private DNS' (or Specified DNS).\n3. Select 'Private DNS provider hostname'.\n4. Paste 'p2.freedns.controld.com' and tap Save.\n\nBlocks popup banners in Chrome, apps, and games system-wide with zero battery drain and zero VPN.")
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

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacy & Security Architecture")
            .setMessage("Fokk Shield is built with a 100% on-device architecture:\n\n• Zero Telemetry: No browsing data, traffic, or analytics leave your device.\n• No Remote Servers: All blocking logic and skip execution happen locally in real-time.\n• No VPN Tunnel: Does not inspect or intercept sensitive banking/UPI transactions.\n• Minimal Permissions: Accessibility is strictly used for YouTube skip-ad detection and never reads keystrokes or passwords.")
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun showDisclaimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Legal Disclaimer")
            .setMessage("Fokk Shield is an independent utility and assistive tool.\n\nIt is not affiliated with, endorsed by, or sponsored by Google LLC, YouTube, Spotify AB, or any related entities. All brand names and trademarks belong to their respective owners.\n\nThis application is provided under open-source community terms for accessibility enhancement and user convenience.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSupportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Fokk Shield Support")
            .setMessage("Version: 1.1.0\nArchitecture: 100% No-VPN Native Assistant\nSupported OS: Android 10, 11, 12, 13, 14, 15\n\nFor bug reports, pull requests, or feature inquiries, visit our official repository or contact the project maintainers.")
            .setPositiveButton("GitHub Repository") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kimmojiraja/adblocker_mine"))
                try {
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to launch browser", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFundraisingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Community Development")
            .setMessage("Fokk Shield is committed to remaining 100% free with zero paywalls and zero data monetization.\n\n• Organization & Supporter Channels: Official donation accounts and contribution methods will be published here in an upcoming release.\n\nThank you for supporting open, independent digital privacy tools!")
            .setPositiveButton("Got It", null)
            .show()
    }

    private fun updateDashboard() {
        val isYtActive = isAccessibilityServiceEnabled(this, YouTubeAdSkipperService::class.java)
        binding.switchYtSkipper.isChecked = isYtActive

        val skippedYtCount = AdShieldApp.instance.getSkippedYouTubeAdsCount()
        binding.textYtSkippedCount.text = skippedYtCount.toString()

        val mutedSpotifyCount = AdShieldApp.instance.getMutedSpotifyAdsCount()
        binding.textSpotifyMutedCount.text = mutedSpotifyCount.toString()

        val totalBlocked = AdShieldApp.instance.getTotalAdsEliminated()
        binding.textTotalAdsCount.text = totalBlocked.toString()

        val webBlocked = AdShieldApp.instance.getBlockedAdsCount()
        val socialBlocked = AdShieldApp.instance.getSkippedSocialAdsCount()
        // Calculate Time Reclaimed (20s per YT ad, 30s per Spotify ad, 5s per web ad, 10s per social ad)
        val totalSeconds = (skippedYtCount * 20) + (mutedSpotifyCount * 30) + (webBlocked * 5) + (socialBlocked * 10)
        binding.textTimeReclaimed.text = if (totalSeconds < 60) {
            "${totalSeconds}s"
        } else {
            val minutes = totalSeconds / 60.0
            if (minutes < 10) String.format(Locale.US, "%.1fm", minutes) else "${minutes.toInt()}m"
        }

        val isSpotifyEnabled = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_SPOTIFY_MUTER_ENABLED, true)
        binding.switchSpotifyMuter.isChecked = isSpotifyEnabled

        val isInstaOn = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_INSTAGRAM_SKIPPER_ENABLED, true)
        binding.switchInstagramSkipper.isChecked = isInstaOn



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
