package com.adshield.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.*
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adshield.app.databinding.ActivityMainBinding
import com.adshield.app.model.BrandOptimizationHelper
import com.adshield.app.service.AdShieldVpnService
import com.adshield.app.service.YouTubeAdSkipperService
import com.adshield.app.ui.BrandGuideActivity
import com.adshield.app.ui.MediaHubActivity
import com.adshield.app.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnServiceInternal()
        } else {
            Toast.makeText(this, "VPN permission is required for system-wide ad blocking", Toast.LENGTH_LONG).show()
            binding.switchVpn.isChecked = false
        }
    }

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
            addAction(AdShieldVpnService.ACTION_VPN_STATE_CHANGED)
            addAction(YouTubeAdSkipperService.ACTION_YT_STATE_CHANGED)
            addAction(YouTubeAdSkipperService.ACTION_YT_AD_SKIPPED)
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
        // Master VPN Switch
        binding.switchVpn.setOnCheckedChangeListener { _, isChecked ->
            val isRunning = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_VPN_ENABLED, false)
            if (isChecked && !isRunning) {
                requestVpnStart()
            } else if (!isChecked && isRunning) {
                stopVpnServiceInternal()
            }
        }

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

        // Action Buttons
        binding.btnOpenMediaHub.setOnClickListener {
            startActivity(Intent(this, MediaHubActivity::class.java))
        }

        binding.cardBrandOptimization.setOnClickListener {
            startActivity(Intent(this, BrandGuideActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun requestVpnStart() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnServiceInternal()
        }
    }

    private fun startVpnServiceInternal() {
        val intent = Intent(this, AdShieldVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateDashboard()
    }

    private fun stopVpnServiceInternal() {
        val intent = Intent(this, AdShieldVpnService::class.java).apply {
            action = AdShieldVpnService.ACTION_STOP
        }
        startService(intent)
        updateDashboard()
    }

    private fun promptEnableAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Enable YouTube Ad Assistant")
            .setMessage("AdShield requires Accessibility permission to automatically detect YouTube ad timers and click 'Skip Ad' the instant it appears.\n\n👉 On OnePlus/Oppo/Vivo/Xiaomi:\n1. Tap 'Downloaded apps' at the bottom of the screen.\n2. Tap 'AdShield YouTube Assistant' and switch it ON.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchYtSkipper.isChecked = false
            }
            .show()
    }

    private fun updateDashboard() {
        val isVpnActive = AdShieldApp.instance.prefs.getBoolean(AdShieldApp.KEY_VPN_ENABLED, false)
        val isYtActive = isAccessibilityServiceEnabled(this, YouTubeAdSkipperService::class.java)

        binding.switchVpn.isChecked = isVpnActive
        binding.switchYtSkipper.isChecked = isYtActive

        val blockedAdsCount = AdShieldApp.instance.getBlockedAdsCount()
        val skippedYtCount = AdShieldApp.instance.getSkippedYouTubeAdsCount()

        binding.textBlockedCount.text = blockedAdsCount.toString()
        binding.textYtSkippedCount.text = skippedYtCount.toString()

        val rulesLoaded = AdShieldApp.instance.blocklistEngine.getLoadedCount()
        binding.textRulesCount.text = "$rulesLoaded active rules"

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
