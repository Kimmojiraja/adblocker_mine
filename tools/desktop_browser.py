#!/usr/bin/env python3
"""
Fokk Shield Desktop Test Browser
Uses PyQt6 WebEngine (Chromium) to replicate Android WebView environment locally.
Injects inject_adshield.js at document creation and applies network blocking interceptor.
"""

import sys
import os
from pathlib import Path

# Configure audio backend for PulseAudio / PipeWire on Linux
os.environ["ALSA_PLUGIN_DIR"] = "/usr/lib/x86_64-linux-gnu/alsa-lib"
os.environ["ALSA_CONFIG_PATH"] = "/usr/share/alsa/alsa.conf"
os.environ["PULSE_SERVER"] = f"unix:/run/user/{os.getuid()}/pulse/native"

# Add Chromium flags before QApplication initializes
sys.argv.extend([
    "--autoplay-policy=no-user-gesture-required",
    "--enable-features=AudioServiceOutOfProcess",
    "--disable-features=AudioServiceSandbox"
])

from PyQt6.QtCore import QUrl
from PyQt6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLineEdit, QPushButton, QLabel, QComboBox
)
from PyQt6.QtWebEngineCore import (
    QWebEngineProfile, QWebEnginePage, QWebEngineScript,
    QWebEngineUrlRequestInterceptor, QWebEngineUrlRequestInfo, QWebEngineSettings
)
from PyQt6.QtWebEngineWidgets import QWebEngineView

# Paths
ROOT_DIR = Path(__file__).resolve().parent.parent
INJECT_JS_PATH = ROOT_DIR / "app" / "src" / "main" / "assets" / "inject_adshield.js"

MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230805.019; wv) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.113 Mobile Safari/537.36 Fokk/1.0.70"
)
DESKTOP_UA = (
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

class AdblockRequestInterceptor(QWebEngineUrlRequestInterceptor):
    def __init__(self, on_blocked_callback=None):
        super().__init__()
        self.on_blocked_callback = on_blocked_callback
        self.blocked_count = 0

    def interceptRequest(self, info: QWebEngineUrlRequestInfo):
        url = info.requestUrl().toString()
        url_lower = url.lower()
        host = info.requestUrl().host().lower()

        # Check if request is media
        resource_type = info.resourceType()
        is_media = resource_type in [
            QWebEngineUrlRequestInfo.ResourceType.ResourceTypeMedia
        ] or any(url_lower.split('?')[0].endswith(ext) for ext in [".mp4", ".m3u8", ".mpd", ".webm", ".ts", ".m4s"])

        # YouTube specific isolation rule
        is_yt = "youtube.com" in host or "youtu.be" in host or "googlevideo.com" in host or "ytimg.com" in host
        if is_yt:
            is_yt_ad = (
                "/pagead/" in url_lower or
                "/api/stats/ads" in url_lower or
                "ad_break" in url_lower or
                "doubleclick.net" in host
            )
            if is_yt_ad and not is_media:
                self._block(info, url, "YouTube Ad Endpoint")
            return

        # Generic Ad Scripts and Networks
        is_ad = (
            "/pagead/" in url_lower or
            "adsbygoogle" in url_lower or
            "doubleclick.net" in host or
            "googlesyndication.com" in host or
            "googleadservices.com" in host or
            "taboola.com" in host or
            "outbrain.com" in host or
            "criteo" in host or
            "amazon-adsystem.com" in host or
            "exoclick" in host or
            "trafficjunky" in host or
            "juicyads" in host or
            "popcash" in host or
            "popads" in host or
            "adsterra" in host or
            "rubiconproject" in host or
            "adnxs.com" in host or
            "smartadserver" in host
        )

        if is_ad and not is_media:
            self._block(info, url, "Ad Network")

    def _block(self, info: QWebEngineUrlRequestInfo, url: str, reason: str):
        info.block(True)
        self.blocked_count += 1
        print(f"\033[91m[BLOCKED: {reason}]\033[0m {url[:90]}...")
        if self.on_blocked_callback:
            self.on_blocked_callback(self.blocked_count)

class CustomWebEnginePage(QWebEnginePage):
    def javaScriptConsoleMessage(self, level, message, lineNumber, sourceID):
        src = os.path.basename(sourceID) if sourceID else "inline"
        prefix = "\033[94m[JS]\033[0m"
        if "fokk" in message.lower() or "shield" in message.lower():
            prefix = "\033[92m[FokkShield]\033[0m"
        elif level == QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:
            prefix = "\033[91m[JS ERR]\033[0m"
        print(f"{prefix} ({src}:{lineNumber}) {message}")

class DesktopTestBrowser(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Fokk Shield — Desktop Test Harness")
        self.resize(1200, 850)

        # Main Layout
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QVBoxLayout(central_widget)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Toolbar
        toolbar = QHBoxLayout()
        toolbar.setContentsMargins(8, 8, 8, 8)

        self.btn_back = QPushButton("◀")
        self.btn_forward = QPushButton("▶")
        self.btn_reload = QPushButton("⟳")

        self.url_bar = QLineEdit()
        self.url_bar.setPlaceholderText("Enter URL or search...")
        self.url_bar.returnPressed.connect(self.navigate_to_url)

        self.btn_go = QPushButton("Go")
        self.btn_go.clicked.connect(self.navigate_to_url)

        # Quick Links
        btn_yt = QPushButton("YouTube")
        btn_yt.clicked.connect(lambda: self.load_url("https://m.youtube.com"))

        btn_ig = QPushButton("Instagram")
        btn_ig.clicked.connect(lambda: self.load_url("https://www.instagram.com"))

        # UA Selector
        self.ua_combo = QComboBox()
        self.ua_combo.addItems(["Mobile View (Android)", "Desktop View"])
        self.ua_combo.currentIndexChanged.connect(self.on_ua_changed)

        # Unmute Button
        self.btn_unmute = QPushButton("🔊 Unmute Video")
        self.btn_unmute.clicked.connect(self.force_unmute_videos)

        # Inspect Sponsored Button
        btn_inspect = QPushButton("🔍 Inspect Sponsored")
        btn_inspect.setToolTip("Print exact HTML of any Sponsored elements currently on screen")
        btn_inspect.clicked.connect(self.inspect_sponsored)

        # Reload Script Button
        btn_reload_script = QPushButton("⚡ Reload Script")
        btn_reload_script.setToolTip("Reload inject_adshield.js from disk and re-inject without restarting browser")
        btn_reload_script.clicked.connect(self.reload_injected_script)

        # Blocked Counter Label
        self.lbl_blocked = QLabel("Blocked: 0")
        self.lbl_blocked.setStyleSheet("color: #4CAF50; font-weight: bold; padding: 0 8px;")

        toolbar.addWidget(self.btn_back)
        toolbar.addWidget(self.btn_forward)
        toolbar.addWidget(self.btn_reload)
        toolbar.addWidget(self.url_bar, 1)
        toolbar.addWidget(self.btn_go)
        toolbar.addWidget(btn_yt)
        toolbar.addWidget(btn_ig)
        toolbar.addWidget(self.ua_combo)
        toolbar.addWidget(self.btn_unmute)
        toolbar.addWidget(btn_inspect)
        toolbar.addWidget(btn_reload_script)
        toolbar.addWidget(self.lbl_blocked)

        layout.addLayout(toolbar)

        # Profile & Interceptor
        self.profile = QWebEngineProfile("FokkDesktopProfile", self)
        self.profile.setHttpUserAgent(MOBILE_UA)

        # Enable features
        settings = self.profile.settings()
        settings.setAttribute(QWebEngineSettings.WebAttribute.JavascriptEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.LocalStorageEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.PlaybackRequiresUserGesture, False)
        settings.setAttribute(QWebEngineSettings.WebAttribute.AllowRunningInsecureContent, True)

        self.interceptor = AdblockRequestInterceptor(self.update_blocked_counter)
        self.profile.setUrlRequestInterceptor(self.interceptor)

        # Install inject_adshield.js
        self.install_adshield_script()

        # Web View
        self.view = QWebEngineView()
        self.page = CustomWebEnginePage(self.profile, self.view)
        self.view.setPage(self.page)
        self.page.setAudioMuted(False)

        self.btn_back.clicked.connect(self.view.back)
        self.btn_forward.clicked.connect(self.view.forward)
        self.btn_reload.clicked.connect(self.view.reload)
        self.view.urlChanged.connect(self.on_url_changed)

        layout.addWidget(self.view, 1)

        # Start with mobile YouTube
        self.load_url("https://m.youtube.com")

    def force_unmute_videos(self):
        js = """
        (function() {
            var videos = document.querySelectorAll('video');
            var unmutedCount = 0;
            videos.forEach(function(v) {
                v.muted = false;
                v.volume = 1.0;
                unmutedCount++;
            });
            console.log('[FokkShield] Unmuted ' + unmutedCount + ' videos, volume=1.0');
        })();
        """
        self.view.page().runJavaScript(js)

    def inspect_sponsored(self):
        js = """
        (function() {
            var hits = [];
            var all = document.querySelectorAll('*');
            for (var i = 0; i < all.length; i++) {
                var el = all[i];
                var text = (el.textContent || '').trim();
                var aria = el.getAttribute('aria-label') || '';
                var isHit = false;
                if (el.children.length === 0 && text) {
                    var t = text.toLowerCase();
                    if (t.includes('sponsored') || t.startsWith('ad ·') || t.startsWith('ad •') || t === 'ad') {
                        isHit = true;
                    }
                }
                if (aria.toLowerCase().includes('sponsored')) isHit = true;
                if (isHit) {
                    var card = el.closest('ytd-rich-item-renderer, ytd-video-renderer, ytm-video-with-context-renderer, ytm-item-section-renderer, article, div[role="article"], [class*="video-renderer"]') || el.parentElement;
                    hits.push({
                        text: text.slice(0, 80),
                        aria: aria,
                        tag: el.tagName,
                        className: String(el.className),
                        cardTag: card ? card.tagName : 'NONE',
                        cardClass: card ? String(card.className) : 'NONE',
                        cardOuterHTML: card ? card.outerHTML.slice(0, 300) : ''
                    });
                }
            }
            return JSON.stringify(hits, null, 2);
        })();
        """
        def on_result(res):
            print("\033[93m--- [INSPECT SPONSORED RESULT] ---\033[0m")
            print(res)
            print("\033[93m----------------------------------\033[0m")
        self.view.page().runJavaScript(js, on_result)

    def install_adshield_script(self):
        scripts = self.profile.scripts()
        for s in scripts.toList():
            if s.name() == "__fokk_adshield__":
                scripts.remove(s)

        if INJECT_JS_PATH.exists():
            js_code = INJECT_JS_PATH.read_text(encoding="utf-8")
            script = QWebEngineScript()
            script.setName("__fokk_adshield__")
            script.setInjectionPoint(QWebEngineScript.InjectionPoint.DocumentCreation)
            script.setWorldId(QWebEngineScript.ScriptWorldId.MainWorld)
            script.setSourceCode(js_code)
            script.setRunsOnSubFrames(True)
            scripts.insert(script)
            print(f"\033[92m[FokkShield]\033[0m Loaded {INJECT_JS_PATH} ({len(js_code)} bytes)")
        else:
            print(f"\033[91m[FokkShield ERR]\033[0m File not found: {INJECT_JS_PATH}")

    def reload_injected_script(self):
        self.install_adshield_script()
        self.view.reload()
        print("\033[93m[FokkShield]\033[0m Re-injected updated script and reloaded page!")

    def on_ua_changed(self, index):
        if index == 0:
            self.profile.setHttpUserAgent(MOBILE_UA)
            print("[UA] Switched to Mobile (Android)")
        else:
            self.profile.setHttpUserAgent(DESKTOP_UA)
            print("[UA] Switched to Desktop")
        self.view.reload()

    def update_blocked_counter(self, count):
        self.lbl_blocked.setText(f"Blocked: {count}")

    def on_url_changed(self, qurl):
        self.url_bar.setText(qurl.toString())

    def navigate_to_url(self):
        text = self.url_bar.text().strip()
        if not text:
            return
        if not text.startswith("http://") and not text.startswith("https://"):
            if "." in text and " " not in text:
                text = "https://" + text
            else:
                text = f"https://www.google.com/search?q={text}"
        self.load_url(text)

    def load_url(self, url_str):
        self.view.load(QUrl(url_str))

def main():
    app = QApplication(sys.argv)
    browser = DesktopTestBrowser()
    browser.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
