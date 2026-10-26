# AdShield - Universal Android Ad Blocker & YouTube Shield

<p align="center">
  <b>Comprehensive Ad Blocker for all Android brands (Samsung, Xiaomi, Oppo, Vivo, OnePlus, Realme, Pixel, Motorola)</b><br>
  Blocks ads across apps, games, browsers, and features dedicated solutions for <b>YouTube</b> and <b>social media platforms</b>.
</p>

---

## 🛡️ Why AdShield?

Blocking ads on modern Android phones has two major hurdles:
1. **Aggressive Brand Battery Killers**: Phones from **Xiaomi (MIUI/HyperOS)**, **Samsung (One UI)**, **Oppo/Realme (ColorOS)**, and **Vivo (FuntouchOS)** aggressively terminate background VPN and filtering services when the screen locks.
2. **YouTube & Social Media Architecture**: YouTube video ads and Meta (Instagram/Facebook) sponsored posts are served from the **exact same CDN IP addresses** as normal video chunks (`*.googlevideo.com`, `*.fbcdn.net`) with **TLS Certificate Pinning**. Traditional DNS blockers either fail or completely break video playback.

**AdShield solves both challenges with a unified, 3-tiered architecture that requires NO ROOT.**

---

## 🚀 Key Features

| Tier | Component | Function | Target Apps |
| :--- | :--- | :--- | :--- |
| **Tier 1** | **Local DNS `VpnService`** | Intercepts port 53 DNS queries on a local loopback (`10.1.10.1`) and instantly drops ad domains (`0.0.0.0` / `NXDOMAIN`). Zero latency, zero external servers, battery-efficient. | Chrome, Firefox, In-App Banners, Unity Ads, AppLovin, Games, News apps. |
| **Tier 2** | **YouTube Ad Assistant (`AccessibilityService`)** | Monitors the official YouTube app in real-time. Automatically clicks **"Skip Ad" in under 50 milliseconds**, automatically mutes audio during unskippable ads, and closes overlay sponsor banners. | Official YouTube app, YouTube Music. |
| **Tier 3** | **Ad-Free Media Hub (Injected Web Shield)** | High-performance isolated web player with script injection (`inject_adshield.js`). Strips YouTube `adPlacements` configs, supports **background audio playback** with the screen off, and provides **Picture-in-Picture (PiP)**. | YouTube, Instagram, Twitter / X, Reddit. |
| **Tier 4** | **Brand Optimization Engine** | Auto-detects device brand (`Build.MANUFACTURER`) and launches manufacturer-specific settings screens for Autostart, Battery Whitelist, and Memory Lock. | Xiaomi, Samsung, Oppo, Vivo, OnePlus, Huawei. |

---

## 📱 Supported Phone Brands & Compatibility

AdShield includes dedicated intent hooks for manufacturer battery managers:

- **Xiaomi / Redmi / POCO**: MIUI & HyperOS Autostart and Battery Saver Whitelist (`com.miui.securitycenter`).
- **Samsung Galaxy**: One UI "Never sleeping apps" and Device Care background limit exemption (`com.samsung.android.sm`).
- **Oppo & Realme**: ColorOS Startup Manager and background run authorization (`com.coloros.safecenter`).
- **Vivo & iQOO**: FuntouchOS & OriginOS iManager autostart permissions (`com.iqoo.secure`).
- **OnePlus**: OxygenOS Deep Optimization exemption and App Lock.
- **Huawei & Honor**: EMUI / MagicOS App Launch manager.
- **Google Pixel / Motorola / Sony / Nothing**: Standard Android `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 🏗️ Project Architecture

```
adblocker_me/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── blocklist_core.txt      # 280+ core mobile ad networks, telemetry & trackers
│       │   └── inject_adshield.js       # Content blocker script & cosmetic filters for web & video
│       ├── java/com/adshield/app/
│       │   ├── AdShieldApp.kt           # Singleton application & statistics manager
│       │   ├── MainActivity.kt          # Material 3 control dashboard
│       │   ├── model/
│       │   │   ├── BlocklistEngine.kt   # Fast in-memory subdomain hierarchy filter
│       │   │   └── BrandOptimizationHelper.kt # OEM brand detection & autostart launchers
│       │   ├── service/
│       │   │   ├── AdShieldVpnService.kt       # Local loopback DNS sinkholing service
│       │   │   ├── DnsPacketParser.kt          # Raw IPv4 / UDP DNS packet synthesizer
│       │   │   └── YouTubeAdSkipperService.kt  # Official YouTube auto-skip & audio muter
│       │   └── ui/
│       │       ├── BrandGuideActivity.kt       # Step-by-step OEM brand guide
│       │       ├── MediaHubActivity.kt         # Ad-free YouTube & social web browser (PiP + Background audio)
│       │       └── SettingsActivity.kt         # Upstream DNS & custom domain rules
│       └── res/
│           ├── layout/                 # Modern responsive layouts
│           ├── values/                 # Colors, strings, dark themes
│           └── xml/                    # Accessibility & network security configs
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔨 How to Build & Install

### Option A: Using Android Studio (Recommended)
1. Open **Android Studio** (Hedgehog or newer recommended).
2. Select **File -> Open...** and select the `adblocker_me` folder.
3. Allow Gradle to sync dependencies.
4. Connect your Android phone via USB (with **USB Debugging** enabled in Developer Options) or start an Android Emulator.
5. Click the green **Run (Shift + F10)** button to install and launch AdShield.

### Option B: Build via Command Line
```bash
# If Gradle is installed on your system:
gradle assembleDebug

# Output APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk

# Install via ADB:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Initial Setup on Device

1. **Turn ON System Ad Shield**:
   - Toggle the **"System-Wide Ad Shield"** switch on the dashboard.
   - Tap **OK** on the Android system VPN connection prompt.
2. **Turn ON YouTube Ad Assistant**:
   - Toggle the **"YouTube Ad Assistant"** switch.
   - In Android Accessibility Settings, tap **AdShield YouTube Assistant** and switch it **ON**.
3. **Configure Brand Battery Protection**:
   - Tap the **Brand Protection Helper** card.
   - Tap **"Open Autostart Settings"** to ensure your phone's OS does not kill AdShield in the background.

---

## 💡 Native Zero-Battery Alternative: Android Private DNS
For users who want ad blocking without running any background VPN app:
1. Open phone **Settings -> Network & internet -> Private DNS**.
2. Select **Private DNS provider hostname**.
3. Enter: `dns.adguard-dns.com`
4. Tap **Save**. (AdShield includes a 1-tap copy button for this in the Settings screen).
