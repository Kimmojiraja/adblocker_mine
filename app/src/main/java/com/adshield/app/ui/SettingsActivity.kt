package com.adshield.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.adshield.app.AdShieldApp
import com.adshield.app.databinding.ActivitySettingsBinding
import com.adshield.app.util.AppUpdateManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupCustomRuleButtons()
        setupPrivateDnsGuide()
        setupPersonalizationButtons()
        setupUpdateChecker()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCustomRuleButtons() {
        binding.btnAddBlacklist.setOnClickListener {
            val domain = binding.editBlacklistDomain.text.toString().trim()
            if (domain.isNotEmpty()) {
                AdShieldApp.instance.blocklistEngine.addCustomBlockedDomain(domain)
                binding.editBlacklistDomain.text?.clear()
                Toast.makeText(this, "Blocked domain: $domain", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddWhitelist.setOnClickListener {
            val domain = binding.editWhitelistDomain.text.toString().trim()
            if (domain.isNotEmpty()) {
                AdShieldApp.instance.blocklistEngine.addCustomWhitelistedDomain(domain)
                binding.editWhitelistDomain.text?.clear()
                Toast.makeText(this, "Whitelisted domain: $domain", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPrivateDnsGuide() {
        binding.btnCopyPrivateDns.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Private DNS Provider", "p2.freedns.controld.com")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied 'p2.freedns.controld.com' to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
    private fun setupPersonalizationButtons() {
        binding.btnWallpapersThemes.setOnClickListener {
            val intent = Intent(this, MediaHubActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MediaHubActivity.EXTRA_OPEN_ACTION, "wallpapers")
            }
            startActivity(intent)
            finish()
        }

        binding.btnCustomizeSpeedDials.setOnClickListener {
            val intent = Intent(this, MediaHubActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MediaHubActivity.EXTRA_OPEN_ACTION, "shortcuts")
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupUpdateChecker() {
        val vName = AppUpdateManager.getCurrentVersionName(this)
        binding.textSettingsVersionInfo.text = "Installed Version: $vName"
        binding.btnCheckForUpdates.setOnClickListener {
            AppUpdateManager.checkForUpdates(this, manualCheck = true)
        }
    }
}
