package com.adshield.app

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.adshield.app.databinding.ActivityMainBinding
import com.adshield.app.databinding.SheetFundraisingContributeBinding
import com.adshield.app.model.BrandOptimizationHelper
import com.adshield.app.ui.BrandGuideActivity
import com.adshield.app.ui.MediaHubActivity
import com.adshield.app.ui.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateDashboard()
    }

    override fun onResume() {
        super.onResume()
        updateDashboard()
    }

    private fun setupListeners() {
        // Built-in browser
        binding.btnOpenMediaHub.setOnClickListener {
            startActivity(Intent(this, MediaHubActivity::class.java))
        }

        // Optional user-configured Private DNS
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

    private fun setupNativePrivateDns() {
        val providers = arrayOf(
            "AdGuard DNS (Recommended: Maximum Ad & Tracker Blocking)\nHostname: dns.adguard-dns.com",
            "NextDNS (Advanced Cloud & Privacy Protection)\nHostname: dns.nextdns.io",
            "Mullvad Adblock (Strict Privacy & Zero Logs)\nHostname: adblock.dns.mullvad.net",
            "Cloudflare Security & Speed (Malware Protection)\nHostname: security.cloudflare-dns.com",
            "Control D Ad Blocker\nHostname: p2.freedns.controld.com"
        )
        val hostnames = arrayOf(
            "dns.adguard-dns.com",
            "dns.nextdns.io",
            "adblock.dns.mullvad.net",
            "security.cloudflare-dns.com",
            "p2.freedns.controld.com"
        )

        AlertDialog.Builder(this)
            .setTitle("Select Mobile Private DNS")
            .setItems(providers) { _, which ->
                val chosenHost = hostnames[which]
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Private DNS Provider", chosenHost))
                Toast.makeText(this, "Copied '$chosenHost' to clipboard!", Toast.LENGTH_SHORT).show()

                AlertDialog.Builder(this)
                    .setTitle("Apply in Android Settings")
                    .setMessage("Copied '$chosenHost'.\n\nTo activate system-wide:\n1. Tap 'Open Settings' below.\n2. Tap 'Private DNS'.\n3. Select 'Private DNS provider hostname'.\n4. Paste '$chosenHost' and tap Save.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        } catch (e: Exception) {
                            try {
                                startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (ex: Exception) {
                                Toast.makeText(this, "Please open phone Settings > Network & Internet > Private DNS", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Done", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacy & Security Architecture")
            .setMessage("Website filters run locally in the built-in browser. Fokk Shield does not use an accessibility service or intercept traffic from other apps. The optional Private DNS setup uses a third-party provider only if you choose to configure it in Android Settings.")
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun showDisclaimerDialog() {
        AlertDialog.Builder(this)
            .setTitle("Legal Disclaimer & Apache 2.0")
            .setMessage("Fokk Shield is open-source software distributed under the Apache License, Version 2.0 (http://www.apache.org/licenses/LICENSE-2.0).\n\nIt is an independent browser privacy utility and is not affiliated with, endorsed by, or sponsored by Google LLC, YouTube, Spotify AB, Meta Platforms, Inc., or Microsoft Corporation. All brand names and trademarks belong to their respective owners.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showSupportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Fokk Shield Support & License")
            .setMessage("Version: 2.1.0\nLicense: Apache License 2.0\nArchitecture: Android WebView browser with local Rust/EasyList website filters\nSupported OS: Android 10, 11, 12, 13, 14, 15\n\nFor bug reports, pull requests, or feature inquiries, visit our official repository or contact the project maintainers.")
            .setPositiveButton("GitHub Repository") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kimmojiraja/fokk-project"))
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
        val dialog = BottomSheetDialog(this)
        val b = SheetFundraisingContributeBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        b.btnCloseFundraising.setOnClickListener { dialog.dismiss() }

        b.cardSponsorGitHub.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors/Kimmojiraja")))
        }

        b.cardBuyMeCoffee.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/k_raja")))
        }

        b.cardContributeCode.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Kimmojiraja/fokk-project")))
        }

        val copyAddress: (String, String) -> Unit = { label, text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(this, "$label address copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        val btcAddress = "bc1qwxnv6uphj0ke8q50gdx6ew8j3lgvwhk6k2kaet"
        val ethAddress = "0x54BB111a33Cae41F63Bad6E9D1Eb65341f561525"
        val bnbAddress = "0x54BB111a33Cae41F63Bad6E9D1Eb65341f561525"
        val solAddress = "DqgAgDj4PjvR4K1wtYfpaZuYNeNZgj2fQoKnXApWKuTj"

        b.cardCryptoBtc.setOnClickListener { copyAddress("Bitcoin", btcAddress) }
        b.btnCopyBtc.setOnClickListener { copyAddress("Bitcoin", btcAddress) }

        b.cardCryptoEth.setOnClickListener { copyAddress("Ethereum", ethAddress) }
        b.btnCopyEth.setOnClickListener { copyAddress("Ethereum", ethAddress) }

        b.cardCryptoBnb.setOnClickListener { copyAddress("BNB Smart Chain", bnbAddress) }
        b.btnCopyBnb.setOnClickListener { copyAddress("BNB Smart Chain", bnbAddress) }

        b.cardCryptoSol.setOnClickListener { copyAddress("Solana", solAddress) }
        b.btnCopySol.setOnClickListener { copyAddress("Solana", solAddress) }

        dialog.show()
    }

    private fun updateDashboard() {
        val total = AdShieldApp.instance.getBlockedAdsCount()
        val crossSite = AdShieldApp.instance.getCrossSiteTrackersCount()
        val social = AdShieldApp.instance.getSocialTrackersCount()
        val traps = AdShieldApp.instance.getDeceptiveTrapsCount()

        binding.textTotalAdsCount.text = String.format(Locale.US, "%,d", total)
        binding.textCrossSiteCount.text = String.format(Locale.US, "%,d", crossSite)
        binding.textSocialTrackersCount.text = String.format(Locale.US, "%,d", social)
        binding.textDeceptiveTrapsCount.text = String.format(Locale.US, "%,d", traps)

        val brandName = BrandOptimizationHelper.getBrandName()
        val isIgnoring = BrandOptimizationHelper.isIgnoringBatteryOptimizations(this)
        binding.textBrandTitle.text = "$brandName Background Protection"
        binding.textBrandStatus.text = if (isIgnoring) "Protected from system battery killer" else "Tap to whitelist on $brandName"
    }

}
