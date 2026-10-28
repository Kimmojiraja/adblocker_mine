package com.adshield.app.util

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdateManager {

    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/Kimmojiraja/adblocker_mine/main/version.json"
    private const val GITHUB_RELEASE_API = "https://api.github.com/repos/Kimmojiraja/adblocker_mine/releases/latest"

    fun checkForUpdates(activity: Activity, manualCheck: Boolean = false) {
        val currentCode = getCurrentVersionCode(activity)
        val currentName = getCurrentVersionName(activity)

        thread {
            var latestCode = currentCode
            var latestName = currentName
            var downloadUrl = ""
            var changelog = "General performance improvements and bug fixes."

            // Try version.json first
            try {
                val urlWithCache = "$VERSION_JSON_URL?t=${System.currentTimeMillis()}"
                val conn = URL(urlWithCache).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "FokkShield-App")
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    latestCode = json.optInt("versionCode", currentCode)
                    latestName = json.optString("versionName", currentName)
                    downloadUrl = json.optString("downloadUrl", "")
                    changelog = json.optString("changelog", changelog)
                }
            } catch (e: Exception) {
                // Fallback to GitHub Release API
                try {
                    val conn = URL(GITHUB_RELEASE_API).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "FokkShield-App")
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        changelog = json.optString("body", changelog)
                        val assets = json.optJSONArray("assets")
                        if (assets != null && assets.length() > 0) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    latestCode = currentCode + 1
                                    latestName = json.optString("tag_name", "Latest")
                                    break
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {}
            }

            activity.runOnUiThread {
                if (latestCode > currentCode && downloadUrl.isNotBlank()) {
                    showUpdateDialog(activity, latestName, changelog, downloadUrl)
                } else if (manualCheck) {
                    Toast.makeText(activity, "FokkShield is up to date (v$currentName)!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, newVersion: String, notes: String, downloadUrl: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available: FokkShield v$newVersion")
            .setMessage("$notes\n\nWould you like to download and install this update now?")
            .setPositiveButton("Update Now") { _, _ ->
                checkInstallPermissionAndDownload(activity, downloadUrl, newVersion)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun checkInstallPermissionAndDownload(activity: Activity, downloadUrl: String, newVersion: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity)
                    .setTitle("Allow App Installation")
                    .setMessage("To install direct updates, please allow FokkShield to install apps in Android Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }
        startApkDownload(activity, downloadUrl, newVersion)
    }

    @Suppress("DEPRECATION")
    private fun startApkDownload(activity: Activity, downloadUrl: String, newVersion: String) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Downloading FokkShield v$newVersion")
            setMessage("Downloading update package...")
            isIndeterminate = false
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        thread {
            try {
                var currentUrl = downloadUrl
                var conn: HttpURLConnection
                var redirectCount = 0
                while (true) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "FokkShield-App")
                    conn.connect()
                    val code = conn.responseCode
                    if ((code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) && redirectCount < 5) {
                        val newLocation = conn.getHeaderField("Location")
                        if (!newLocation.isNullOrBlank()) {
                            currentUrl = newLocation
                            redirectCount++
                            continue
                        }
                    }
                    break
                }

                val fileLength = conn.contentLength
                val apkFile = File(activity.cacheDir, "FokkShield_update.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = conn.inputStream
                val output = FileOutputStream(apkFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        activity.runOnUiThread { progressDialog.progress = progress }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                activity.runOnUiThread {
                    progressDialog.dismiss()
                    promptInstallApk(activity, apkFile)
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun promptInstallApk(activity: Activity, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
