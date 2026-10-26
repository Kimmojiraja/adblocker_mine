package com.adshield.app.model

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BrandOptimizationHelper {

    private const val TAG = "BrandOptimizationHelper"

    enum class DeviceBrand {
        SAMSUNG,
        XIAOMI,
        OPPO_REALME,
        VIVO,
        ONEPLUS,
        HUAWEI_HONOR,
        GENERIC
    }

    fun getDeviceBrand(): DeviceBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> DeviceBrand.SAMSUNG
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> DeviceBrand.XIAOMI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> DeviceBrand.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> DeviceBrand.VIVO
            manufacturer.contains("oneplus") -> DeviceBrand.ONEPLUS
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> DeviceBrand.HUAWEI_HONOR
            else -> DeviceBrand.GENERIC
        }
    }

    fun getBrandName(): String {
        return Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open battery optimization settings", ex)
            }
        }
    }

    /**
     * Attempts to open the OEM-specific Autostart / Background Management settings screen.
     * Returns true if a specific OEM intent was launched, false if falling back to system settings.
     */
    fun openOemAutostartSettings(context: Context): Boolean {
        val brand = getDeviceBrand()
        val intents = mutableListOf<Intent>()

        when (brand) {
            DeviceBrand.XIAOMI -> {
                intents.add(createIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
                intents.add(createIntent("com.miui.securitycenter", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            }
            DeviceBrand.OPPO_REALME -> {
                intents.add(createIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"))
                intents.add(createIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"))
                intents.add(createIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
                intents.add(createIntent("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelga设置.PowerUsageModelActivity"))
            }
            DeviceBrand.VIVO -> {
                intents.add(createIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
                intents.add(createIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"))
                intents.add(createIntent("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity"))
            }
            DeviceBrand.SAMSUNG -> {
                intents.add(createIntent("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
                intents.add(createIntent("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity"))
            }
            DeviceBrand.HUAWEI_HONOR -> {
                intents.add(createIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
                intents.add(createIntent("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity"))
            }
            DeviceBrand.ONEPLUS -> {
                intents.add(createIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            }
            DeviceBrand.GENERIC -> {}
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Try next intent
            }
        }

        // Fallback: App Details Settings
        return try {
            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appInfoIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, cls)
        }
    }

    fun getBrandInstructions(): String {
        return when (getDeviceBrand()) {
            DeviceBrand.XIAOMI -> """
                1. Enable 'Autostart' for AdShield.
                2. Set Battery Saver to 'No restrictions'.
                3. Lock AdShield in the Recent Apps screen by holding the app card and tapping the Lock icon.
            """.trimIndent()

            DeviceBrand.SAMSUNG -> """
                1. Go to Settings -> Battery and device care -> Battery.
                2. Tap 'Background usage limits' -> 'Never sleeping apps'.
                3. Tap '+' and add AdShield to ensure 24/7 continuous ad blocking.
            """.trimIndent()

            DeviceBrand.OPPO_REALME -> """
                1. Open Phone Manager -> Privacy Permissions -> Startup Manager.
                2. Turn ON 'Allow Auto-startup' for AdShield.
                3. In App Info, allow background activity.
            """.trimIndent()

            DeviceBrand.VIVO -> """
                1. Open iManager -> App Manager -> Autostart Manager.
                2. Enable AdShield.
                3. Under Battery management, set 'High background power consumption' to Allowed.
            """.trimIndent()

            DeviceBrand.ONEPLUS -> """
                1. Settings -> Battery -> Battery Optimization.
                2. Select AdShield and choose 'Don't optimize'.
                3. In Recent Apps, tap the 3 dots on AdShield and select 'Lock'.
            """.trimIndent()

            DeviceBrand.HUAWEI_HONOR -> """
                1. Settings -> Battery -> App launch.
                2. Find AdShield, toggle off 'Manage automatically'.
                3. Enable 'Auto-launch', 'Secondary launch', and 'Run in background'.
            """.trimIndent()

            DeviceBrand.GENERIC -> """
                1. Disable Battery Optimization for AdShield.
                2. Allow background activity and autostart in system settings.
            """.trimIndent()
        }
    }
}
