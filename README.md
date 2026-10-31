# Fokk Shield Browser

<p align="center">
  <img width="800" alt="Fokk Shield Browser" src="https://github.com/user-attachments/assets/70e9a0ca-0f16-46ad-ae70-c9cf176e186e" />
</p>

<p align="center">
  <strong>Ultra-Fast • Zero-Telemetry • Pre-Emptive Ad Neutralization • In-Browser Torrent Streaming • Dual-Deck Viewport</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.0.62-00E5FF?style=flat-square" alt="Version 1.0.62" />
  <img src="https://img.shields.io/badge/Platform-Android_7.0%2B-10B981?style=flat-square" alt="Android 7.0+" />
  <img src="https://img.shields.io/badge/Package_Size-%3C_20_MB-8B5CF6?style=flat-square" alt="Size < 20 MB" />
  <img src="https://img.shields.io/badge/Telemetry-Zero-EF4444?style=flat-square" alt="Zero Telemetry" />
  <img src="https://img.shields.io/badge/Crypto_Bloat-None-F59E0B?style=flat-square" alt="No Crypto" />
</p>

---

## ⚡ What is Fokk Shield?

**Fokk Shield** is a next-generation Android power-browser engineered for pure web autonomy, high-performance multimedia, and uncompromising privacy. Unlike commercial mobile browsers bloated with crypto wallets, background telemetry, and sponsored news cards, Fokk Shield delivers a featherweight native footprint with desktop-grade power tools built directly into the core app.

---

## 🚀 Why Fokk Shield Is Different

Most "privacy browsers" on mobile are heavy 200 MB+ Chromium forks loaded with token rewards (Brave), slow Gecko engines requiring complex extension configurations (Firefox), or corporate proxies bundled with sponsored deals and telemetry (Opera).

Fokk Shield combines Android's optimized hardware-accelerated pipeline with custom document-start network hooks, local domain blocking, and unique power tools found nowhere else on mobile:

### 1. Pre-Emptive JSON Network Hooking (YouTube & Instagram)
- **Zero Black Screens**: Traditional ad blockers rely on simple network URL blocking. When applied to modern platforms like YouTube, this causes 5–10 second black screens, infinite buffering loops, or anti-adblock playback refusals.
- **Document-Start Injection**: Fokk Shield injects hooks at document-start (`WebViewCompat.addDocumentStartJavaScript`) into `ytInitialPlayerResponse`, `fetch()`, and `XMLHttpRequest`. Ad placements, player ads, and tracking beacons are stripped directly from `/youtubei/v1/player` JSON payloads **before the video player boots**.
- **Instagram GraphQL Scrubbing**: Intercepts timeline connections, GraphQL queries, and Reels feeds in real-time, purging sponsored nodes and app install banners.

### 2. Built-in In-Browser WebTorrent & Magnet Streaming
- No need to download heavy, battery-draining third-party torrent client apps.
- Tap any `magnet:` link or open a `.torrent` file to **stream video and audio files instantly inside your browser tab** as data downloads sequentially over WebRTC and BitTorrent protocols.

### 3. Integrated 1-Tap Paywall Mirror & AI Summarizer
- **Instant Paywall Mirror**: Tap the dedicated mirror button directly on the command bar to bypass hard paywalls on news publications and read clean, archived mirrors.
- **AI Smart Summarizer**: Locally extracts the core text of lengthy articles and produces instant TL;DR key takeaways and reading time estimates.
- **Distraction-Free Cyber Reader**: Strips away sticky headers, newsletter popups, and sidebars into an OLED-friendly high-contrast reader view.

### 4. Dual-Deck Split Screen Viewport
- Multitask without toggling between tabs.
- The **Dual-Deck Viewport** allows you to split the mobile screen horizontally and run two independent, live web tabs simultaneously (e.g., watching a stream on the top deck while taking notes or reading on the bottom deck).

### 5. Physical Multi-Process Private Profile ("Ghost Mode")
- Conventional "Incognito" tabs share the same process memory and directory space as regular tabs.
- Fokk Shield leverages Android P's `WebView.setDataDirectorySuffix("fokk_private")`. Private tabs run in an **entirely segregated OS directory and process**. Cookies, cache, WebSQL, and IndexedDB are physically walled off from normal browsing and wiped with zero residual storage on exit.

### 6. Fluid Floating Command Bar
- Designed specifically for modern one-handed mobile ergonomics.
- **Choreographed Fluid Expansion**: When you tap the bottom input card to search or type a URL, the tab switcher and menu smoothly slide to the right and fade away with Material motion, while the input bar expands across the full width of the screen.
- Separated with a single metallic hairline divider and a minimalist borderless tab counter.

### 7. Pure-Black AMOLED Dark Engine
- Unlike superficial CSS inverters that wash out photos and video colors, Fokk Shield uses an algorithmic AMOLED theme that forces webpage background surfaces to true `#000000` hex black, maximizing battery life on OLED displays.

---

## 🛡️ Head-to-Head Comparison

| Capability | **Fokk Shield** | **Brave** | **Firefox (Mobile)** | **Opera (Mobile)** | **Chrome** |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Download / Install Size** | **< 20 MB** | ~180–250 MB | ~110–140 MB | ~130–180 MB | ~130 MB |
| **RAM & Startup Speed** | **Near-instant / Ultra-low** | Heavy Chromium | Moderate–High | Heavy | Heavy |
| **Telemetry & Analytics** | **Zero** | Diagnostic telemetry | Opt-out | Proxy data collection | Pervasive tracking |
| **Crypto / Token Bloat** | **None** | BAT tokens, Web3 wallet | None | Crypto / Deals | None |
| **YouTube Ad Handling** | **Pre-Emptive JSON (0 Black Screens)** | Network filter (Buffering delays) | Requires extensions | Weak / Allowed | Full ads |
| **Instagram Web Scrubbing** | **GraphQL Network Intercept** | Basic CSS hiding | Requires extensions | None | Full ads |
| **WebTorrent / Magnet Streaming** | **Built-in Streaming** | Desktop only (None on mobile) | None | None | None |
| **Dual-Deck Split Tabs** | **Built-in Stacked Viewport** | None | None | None | None |
| **1-Tap Paywall Mirror** | **Integrated on Command Bar** | None | Requires extensions | None | None |
| **Private Profile Isolation** | **Physical Directory (`fokk_private`)** | Shared process | Session cache | Shared process | Shared process |
| **Address Bar Ergonomics** | **Bottom Fluid Animated Card** | Fixed Bar | Top Bar | Fixed Bar | Fixed Bar |

---

## 🔒 Security Truth: Why No Fake "Free VPN"?

Browsers like Opera heavily advertise a "free built-in VPN". In reality:
1. **It is an unencrypted HTTP proxy**, not a real VPN tunnel.
2. Operating global proxy infrastructure costs millions of dollars per month. Free proxies fund this by **logging browsing metadata and selling analytical traffic profiles**.
3. Millions of shared proxy IPs trigger constant Cloudflare CAPTCHAs and severely throttle speeds.
4. Brave's "VPN" is a paid subscription upsell costing **$9.99/month**.

### How Fokk Shield Protects You Honestly:
* **Encrypted DNS-over-HTTPS (DoH) & Private DNS**: Native integration with audited, zero-log providers:
  * **AdGuard DNS** (`dns.adguard-dns.com`) – Maximum ad and tracker blocking
  * **Mullvad Privacy DNS** (`adblock.dns.mullvad.net`) – Strict zero-log Swedish privacy
  * **Cloudflare Security** (`security.cloudflare-dns.com`) – Speed & malware protection
  * **Control D** (`p2.freedns.controld.com`) – Precision tracker filtration
* **100% On-Device Filtering**: All filtering occurs locally in your phone's memory. No third party ever inspects or relays your traffic.
* **Full Compatibility with True OS VPNs**: Fokk Shield functions seamlessly with WireGuard, Mullvad, ProtonVPN, or Tailscale running at the Android operating system level.
* **Unthrottled Line Speeds**: P2P WebTorrent streaming and general web downloads operate at the maximum bandwidth of your 5G or Wi-Fi connection.

---

## 🛠️ Build & Installation

### Download Release APK
Grab the latest release APK directly from GitHub Releases:
- **Latest Version**: [Download Fokk.apk](https://github.com/Kimmojiraja/adblocker_mine/releases/latest/download/Fokk.apk)

### Building from Source
Ensure you have JDK 17 and Android SDK 34 installed:

```bash
# Clone the repository
git clone https://github.com/Kimmojiraja/adblocker_mine.git
cd adblocker_mine

# Build debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 License & Disclaimers

- Built with modern Android Jetpack, Kotlin Coroutines, Material Components, and open-source ad-blocking specifications.
- Filtering and script scrubbing operate strictly on the client side under user autonomy principles. All trademarks, service marks, and brand logos belong to their respective owners.

