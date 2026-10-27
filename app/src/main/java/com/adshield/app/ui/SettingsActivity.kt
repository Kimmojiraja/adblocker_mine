package com.adshield.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.adshield.app.AdShieldApp
import com.adshield.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val dnsProviders = listOf(
        "Cloudflare (1.1.1.1) - Ultra Fast & Privacy First",
        "Quad9 (9.9.9.9) - Blocks Malware & Phishing",
        "AdGuard DNS (94.140.14.14) - Dual Layer Ad Filtering",
        "Google Public DNS (8.8.8.8) - Reliable & Global"
    )

    private val dnsIps = listOf("1.1.1.1", "9.9.9.9", "94.140.14.14", "8.8.8.8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDnsSelector()
        setupSwitches()
        setupCustomRuleButtons()
        setupPrivateDnsGuide()
        setupPersonalizationButtons()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDnsSelector() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dnsProviders)
        binding.spinnerDns.adapter = adapter

        val currentDns = AdShieldApp.instance.prefs.getString(AdShieldApp.KEY_UPSTREAM_DNS, "1.1.1.1")
        val index = dnsIps.indexOf(currentDns).coerceAtLeast(0)
        binding.spinnerDns.setSelection(index)

        binding.btnSaveDns.setOnClickListener {
            val selectedIndex = binding.spinnerDns.selectedItemPosition
            val selectedIp = dnsIps[selectedIndex]
            AdShieldApp.instance.prefs.edit().putString(AdShieldApp.KEY_UPSTREAM_DNS, selectedIp).apply()
            Toast.makeText(this, "Upstream DNS updated to $selectedIp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwitches() {
        val prefs = AdShieldApp.instance.prefs
        binding.switchAutoMute.isChecked = prefs.getBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, true)

        binding.switchAutoMute.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(AdShieldApp.KEY_AUTO_MUTE_ENABLED, isChecked).apply()
        }
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
}
