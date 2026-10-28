# AdShield - 100% No-VPN Android Ad Blocker Suite

<p align="center">
  <b>The lightweight, zero-VPN ad blocker for you and your friends</b><br>
  Blocks ads across the official YouTube app, social media, and games without running any VPN.
</p>

---

## 🛡️ Why 100% No-VPN?

Traditional ad blockers force a permanent VPN loopback, which causes:
- ❌ Broken banking & UPI apps (Google Pay, PhonePe, Paytm).
- ❌ Annoying permanent "VPN Key" icon in the status bar.
- ❌ Network slowdowns and battery drain.

**AdShield requires ZERO VPN:**
1. **Official YouTube Ad Assistant (`AccessibilityService`)**: Runs silently in the background. Detects video ads and clicks **"Skip Ad" in under 50 milliseconds**. Automatically mutes unskippable ads.
2. **Dedicated Ad-Free YouTube & Social App**: Built-in media player with **100% zero video ads**, **background audio play with the screen off**, and **floating Picture-in-Picture (PiP)**.
3. **One-Tap System Ad Blocker (Native Android Private DNS)**: 1-tap shortcut to enable Android's native Private DNS (`dns.adguard-dns.com`). Blocks ads in games, Chrome, and browsers with **0% extra battery and NO VPN**.
4. **Universal Brand Optimization Helper**: Guides users on OnePlus, Xiaomi, Samsung, Oppo, and Vivo to whitelist AdShield from aggressive system battery killers.

---

## 📱 How to Use on Your Phone

## 📦 Publishing an in-app update

Pushing source code does not update phones already using the app. For the **Check for updates** button to offer an update, publish a newer, signed APK and then update `version.json` with a `versionCode` higher than the APK users have installed, its version name, and the HTTPS URL of that release asset. The uploaded APK must be signed with the same signing key as the installed app; Android will reject a different signature.

### 1. Enable YouTube Ad Assistant
- Open **AdShield** ➡️ toggle **YouTube Ad Assistant** to **ON**.
- On OnePlus / Oppo / Xiaomi / Vivo:
  1. In Accessibility settings, scroll to the bottom and tap **"Downloaded apps"**.
  2. Tap **AdShield YouTube Assistant** and turn it **ON**.
  *(If Android shows "App was denied access / Restricted setting": Long-press AdShield on home screen ➡️ App info ➡️ tap the 3 dots (⋮) top-right ➡️ "Allow restricted settings")*.

### 2. Enjoy 100% Ad-Free YouTube Web & Social Feeds
- Tap **"Launch"** on the **Ad-Free YouTube & Social App** card.
- Play any video with zero ads, background audio with screen locked, and floating PiP!

### 3. Block Ads in Games & Browsers (No VPN)
- Tap **"🚀 Setup in 1 Tap (No VPN)"**.
- It copies `dns.adguard-dns.com` and opens your phone's Network Settings.
- Select **Private DNS provider hostname**, paste, and tap **Save**!

---

## 🏗️ Architecture

```
adblocker_me/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   ├── blocklist_core.txt      # Domain filters for web media hub
│   │   │   └── inject_adshield.js       # Ad-stripping script for web & video
│   │   ├── java/com/adshield/app/
│   │   │   ├── MainActivity.kt          # Clean 100% No-VPN Dashboard
│   │   │   ├── model/
│   │   │   │   ├── BlocklistEngine.kt   # Fast domain filter for media hub
│   │   │   │   └── BrandOptimizationHelper.kt # OEM brand detection
│   │   │   ├── service/
│   │   │   │   └── YouTubeAdSkipperService.kt # Official YouTube auto-skipper & muter
│   │   │   └── ui/
│   │   │       ├── BrandGuideActivity.kt # Battery exemption helper
│   │   │       ├── MediaHubActivity.kt   # Ad-free YouTube player (PiP + Background audio)
│   │   │       └── SettingsActivity.kt   # Preferences & Private DNS guides
│   │   └── res/layout/activity_main.xml
├── build.gradle.kts
└── settings.gradle.kts
```
