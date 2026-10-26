package com.adshield.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adshield.app.databinding.ActivityBrandGuideBinding
import com.adshield.app.model.BrandOptimizationHelper

class BrandGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrandGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrandGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        populateDeviceInfo()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateOptimizationStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun populateDeviceInfo() {
        val brand = BrandOptimizationHelper.getBrandName()
        val model = Build.MODEL
        val androidVer = Build.VERSION.RELEASE

        binding.textDeviceName.text = "$brand $model"
        binding.textAndroidVer.text = "Android $androidVer (API ${Build.VERSION.SDK_INT})"
        binding.textInstructions.text = BrandOptimizationHelper.getBrandInstructions()
        binding.btnOpenOemSettings.text = "Open $brand Autostart Settings"
    }

    private fun updateOptimizationStatus() {
        val isIgnoring = BrandOptimizationHelper.isIgnoringBatteryOptimizations(this)
        if (isIgnoring) {
            binding.textStatusBadge.text = "BATTERY EXEMPTION: ACTIVE"
            binding.textStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B5E20"))
            binding.textStatusDesc.text = "AdShield has permission to run continuously in the background without being killed."
        } else {
            binding.textStatusBadge.text = "BATTERY EXEMPTION: NOT GRANTED"
            binding.textStatusBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B71C1C"))
            binding.textStatusDesc.text = "Aggressive system cleaners may kill the ad blocker when your screen is locked. Please grant exemptions below."
        }
    }

    private fun setupListeners() {
        binding.btnOpenOemSettings.setOnClickListener {
            val opened = BrandOptimizationHelper.openOemAutostartSettings(this)
            if (!opened) {
                Toast.makeText(this, "Could not open brand settings directly. Please check Phone Settings manually.", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnRequestExemption.setOnClickListener {
            BrandOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }
    }
}
