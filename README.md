# Fokk Shield Browser

<p align="center">
  <img width="800" alt="Fokk Shield Browser" src="https://github.com/user-attachments/assets/70e9a0ca-0f16-46ad-ae70-c9cf176e186e" />
</p>

Fokk Shield is a lightweight, privacy-focused Android web browser built for fast media playback, built-in torrent streaming, and clean web reading without background tracking.

---

## Overview

Fokk Shield is designed to give users complete control over their mobile browsing experience. By running all content filtering and script sanitization locally on your device, the browser eliminates intrusive video ads, sponsored clutter, and analytics tracking with zero reliance on remote proxy servers.

---

## Core Capabilities

### Document-Start Video & Social Filtering
- Intercepts page responses and network payloads at document start (`WebViewCompat.addDocumentStartJavaScript`).
- Strips ad metadata from YouTube player responses and Instagram feeds before the player initializes.
- Content plays smoothly without buffering pauses or blank screen delays.

### In-Browser WebTorrent & Magnet Streaming
- Stream audio and video files directly in your browser tab from magnet links and .torrent files.
- Operates over native WebRTC and BitTorrent protocols without requiring third-party torrent client apps.

### Dual-Deck Split Viewport
- Run two live, independent web tabs simultaneously in a stacked vertical view.
- Browse documentation, read news, or take notes while watching video playback on the same screen.

### 1-Tap Paywall Mirror & Reader Tools
- Instant access to clean, archived mirrors of paywalled news articles directly from the address bar.
- Integrated high-contrast reader view that strips away sticky banners, newsletter popups, and visual clutter for long-form reading.
- On-device text summarizer providing quick bullet points and reading time estimates.

### Isolated Private Profile (fokk_private)
- Private tabs operate in a dedicated, physically isolated WebView process and file directory (`fokk_private`).
- Cookies, cache, WebSQL, and IndexedDB data are strictly separated from standard browsing and purged upon exit.

### Ergonomic Animated Command Bar
- Bottom-positioned navigation card engineered for one-handed mobile reach.
- Features smooth fluid expansion: when tapping the input to type, navigation actions slide away as the address bar opens across the full screen width.
- Integrated minimalist tab counter and quick engine switcher.

### AMOLED Pure-Black Dark Theme
- Hardware-accelerated dark engine that formats web backgrounds to true #000000 black.
- Enhances contrast and reduces display power consumption on OLED screens.

### Lightweight Native Footprint
- Installs under 20 MB with near-instant cold launch times.
- Zero crypto bloat, zero background sync services, and minimal memory usage.

---

## Privacy Architecture

### 100% On-Device Execution
All request blocking, cosmetic filtering, and payload sanitization occur locally in memory on your phone. Browsing requests, search queries, and navigation history are never routed through intermediate proxy servers.

### Encrypted DNS Integration
Fokk Shield supports encrypted DNS-over-HTTPS (DoH) and Android Private DNS providers to shield domain lookups from network-level snooping:
- AdGuard DNS (`dns.adguard-dns.com`)
- Mullvad Privacy DNS (`adblock.dns.mullvad.net`)
- Cloudflare Security (`security.cloudflare-dns.com`)
- Control D (`p2.freedns.controld.com`)

### Zero Telemetry
The application contains no analytics libraries, tracking SDKs, or background telemetry services. Bookmarks, history, and user settings remain strictly within your local app sandbox.

---

## Build & Installation

### Releases
Download the latest APK release:
- [Download Fokk.apk](https://github.com/Kimmojiraja/adblocker_mine/releases/latest/download/Fokk.apk)

### Building from Source
Requirements: JDK 17, Android SDK 34.

```bash
git clone https://github.com/Kimmojiraja/adblocker_mine.git
cd adblocker_mine
./gradlew assembleDebug
```

The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License & Fair Use

Fokk Shield is built using Android Jetpack, Kotlin Coroutines, and open-source ad-blocking specifications under Apache 2.0 / MIT licenses. All content filtering and DOM sanitization operate locally on content delivered to your device under user autonomy principles. All trademarks and brand names belong to their respective owners.

