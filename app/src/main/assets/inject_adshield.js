// Fokk Shield optional browser-side cosmetic filters
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    // YouTube / Media Player Network Interceptor (Pre-Emptive Ad Stripper)
    (function installNetworkInterceptor() {
        if (window.__fokk_net_hooked) return;
        window.__fokk_net_hooked = true;

        if (window.fetch) {
            const origFetch = window.fetch;
            window.fetch = async function(...args) {
                const response = await origFetch.apply(this, args);
                try {
                    const url = (typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '');
                    if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next')) {
                        const clone = response.clone();
                        const data = await clone.json();
                        let modified = false;
                        if (data.adPlacements) { delete data.adPlacements; modified = true; }
                        if (data.playerAds) { delete data.playerAds; modified = true; }
                        if (data.adSlots) { delete data.adSlots; modified = true; }
                        if (data.playbackTracking) {
                            delete data.playbackTracking.videostatsPlaybackUrl;
                            delete data.playbackTracking.videostatsDelayplayUrl;
                            delete data.playbackTracking.videostatsWatchtimeUrl;
                            delete data.playbackTracking.videostatsPtrackingUrl;
                            modified = true;
                        }
                        if (modified) {
                            return new Response(JSON.stringify(data), {
                                status: response.status,
                                statusText: response.statusText,
                                headers: response.headers
                            });
                        }
                    }
                } catch (e) {}
                return response;
            };
        }

        if (window.XMLHttpRequest) {
            const origOpen = XMLHttpRequest.prototype.open;
            const origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this._fokkTargetUrl = url;
                return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function(body) {
                if (this._fokkTargetUrl && (typeof this._fokkTargetUrl === 'string') &&
                    (this._fokkTargetUrl.includes('/youtubei/v1/player') || this._fokkTargetUrl.includes('/youtubei/v1/next'))) {
                    const self = this;
                    const origOnReady = this.onreadystatechange;
                    this.onreadystatechange = function() {
                        if (self.readyState === 4 && self.status === 200) {
                            try {
                                const data = JSON.parse(self.responseText);
                                let modified = false;
                                if (data.adPlacements) { delete data.adPlacements; modified = true; }
                                if (data.playerAds) { delete data.playerAds; modified = true; }
                                if (data.adSlots) { delete data.adSlots; modified = true; }
                                if (modified) {
                                    const cleaned = JSON.stringify(data);
                                    Object.defineProperty(self, 'responseText', { value: cleaned, configurable: true });
                                    Object.defineProperty(self, 'response', { value: cleaned, configurable: true });
                                }
                            } catch (e) {}
                        }
                        if (origOnReady) origOnReady.apply(this, arguments);
                    };
                }
                return origSend.apply(this, arguments);
            };
        }
    })();

    const cosmeticCss = `
        /* Instagram Sponsored & App Promo Hiding */
        a[href*="/about/ads"],
        div:has(> a[href*="/about/ads"]),
        article:has(a[href*="/about/ads"]),
        div[role="article"]:has(a[href*="/about/ads"]),
        div:has(> [aria-label*="Sponsored"]),
        article:has([aria-label*="Sponsored"]),
        div[role="article"]:has([aria-label*="Sponsored"]),
        div[role="dialog"]:has(a[href*="play.google.com"]),
        div[data-testid="banner-mobile-app"],

        /* YouTube Mobile Ad Overlays */
        .ytm-player-ad-overlay,
        ytm-player-ad-overlay,
        .ytp-ad-player-overlay,
        .ytp-ad-text,
        .ytp-ad-preview-text,
        .ytp-ad-module,
        div[class*="ad-showing"] .ytp-ad-text,

        /* Generic ad slots & banners */
        ins.adsbygoogle,
        .adsbygoogle,
        iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"],
        iframe[src*="googleadservices"],
        [data-ad-slot],
        [data-ad-unit],
        [data-ad-client],
        [id^="div-gpt-ad"],
        [id*="-ad-slot"],
        .ad-container,
        .ad-banner,
        .advertisement,
        #tads,
        #tadsb,

        /* Web-Wide Cookie Consent Banners, Overlays & Prompts */
        #onetrust-consent-sdk,
        #onetrust-banner-sdk,
        .onetrust-pc-dark-filter,
        #CybotCookiebotDialog,
        #CybotCookiebotDialogBodyUnderlay,
        .qc-cmp2-container,
        .qc-cmp-cleanslate,
        .fc-consent-root,
        .cc-window,
        .cc-banner,
        div[class*="cookie-notice"],
        div[class*="cookie_notice"],
        div[class*="CookieNotice"],
        div[class*="cookie-consent"],
        div[class*="cookie_consent"],
        div[class*="CookieConsent"],
        div[class*="cookie-banner"],
        div[class*="cookie_banner"],
        div[class*="CookieBanner"],
        div[class*="cookie-modal"],
        div[data-test-id*="cookie"],
        div[data-testid*="cookie"],
        div[id*="cookie-consent"],
        div[id*="cookie-notice"],
        div[id*="cookie-banner"],
        div[aria-modal="true"]:has(form[action*="consent.google"]),
        form[action*="consent.google"],
        div[class*="consent-banner"],
        div[class*="consent-modal"],
        div[class*="consent-overlay"],
        div[class*="consent_overlay"],
        div[id*="sp_message_container"],

        /* YouTube Web Ads & Sponsored Cards */
        ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer),
        ytm-item-section-renderer:has(ytm-ad-slot-renderer),
        ytm-compact-promoted-item-renderer,
        div.ytm-promoted-sparkles-web-renderer,
        ytm-promoted-sparkles-web-renderer,
        ytm-promoted-sparkles-text-search-web-renderer,
        ytm-companion-ad-renderer,
        ytm-ad-slot-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-promoted-video-renderer,
        ytd-display-ad-renderer,
        ytd-banner-promo-renderer,
        ytd-ad-slot-renderer,
        ytd-in-feed-ad-layout-renderer,
        .ytp-ad-overlay-container,
        ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],

        /* YouTube Mobile Topbar Open App Button & Promos */
        ytm-open-app-button,
        .mobile-topbar-header-endpoint:has(ytm-open-app-button),
        c3-material-button[aria-label*="Open App"],
        a.c3-material-button[href*="redirect_app"],
        [aria-label*="Open App"],
        [aria-label*="open app"],
        [aria-label*="Get App"],
        [aria-label*="get app"],
        ytm-mealbar-promo-renderer,

        /* Anti-App-Nag: Open in App / Get App Banners & Prompts across sites */
        .smartbanner,
        .app-banner,
        .smart-app-banner,
        [class*="smartbanner"],
        [class*="SmartBanner"],
        [id*="smartbanner"],
        [class*="open-in-app"],
        [class*="openInApp"],
        [class*="OpenInApp"],
        [id*="open-in-app"],
        [id*="openInApp"],
        div[data-testid="upsell-banner"],
        div[data-testid="banner-mobile-app"],
        div[class*="MobileAppBanner"],
        div[class*="mobile-app-banner"],
        div[class*="OpenAppBanner"],
        a[href*="spotify.com/download"],
        xpromo-app-selector,
        shreddit-async-loader[bundlename="bottom_sheet"],
        .XPromoPopup,
        reddit-bottom-sheet,
        div[class*="xpromo"],
        .w9hPv {
            display: none !important;
        }

        html.fokk-pip-active video {
            width: 100% !important;
            height: 100% !important;
            object-fit: contain !important;
        }
    `;

    function injectCosmetics() {
        if (document.getElementById('__fokk_cosmetic_style')) return;
        const style = document.createElement('style');
        style.id = '__fokk_cosmetic_style';
        style.textContent = cosmeticCss;
        (document.head || document.documentElement).appendChild(style);
    }

    injectCosmetics();

// =========================================================================
    // Cookie Consent Auto-Dismiss & Scrubber (Web-Wide + Ecosia)
    // =========================================================================
    function scrubCookieBanners() {
        const host = window.location.hostname;

        // 1. Ecosia specific cookie consent auto-handling
        if (host.includes('ecosia.org')) {
            const ecosiaButtons = Array.from(document.querySelectorAll('button, a'));
            for (let i = 0; i < ecosiaButtons.length; i++) {
                const text = (ecosiaButtons[i].innerText || ecosiaButtons[i].textContent || '').trim().toLowerCase();
                if (text === 'reject non-essential' || text === 'accept all' || text.includes('reject non-essential')) {
                    try { ecosiaButtons[i].click(); break; } catch (e) {}
                }
            }
            // If banner still present, remove container directly
            document.querySelectorAll('div[data-test-id*="cookie"], div[class*="cookie"], div[class*="consent"], div[role="dialog"]').forEach(function(el) {
                const content = (el.innerText || el.textContent || '').toLowerCase();
                if (content.includes('cookies help us improve') || (content.includes('essential cookies') && content.includes('non-essential cookies'))) {
                    try { el.remove(); } catch (e) { el.style.setProperty('display', 'none', 'important'); }
                }
            });
            if (document.documentElement) document.documentElement.style.overflow = 'auto';
            if (document.body) document.body.style.overflow = 'auto';
        }

        // 2. Generic OneTrust, Cookiebot, & popular consent banners
        document.querySelectorAll('#onetrust-consent-sdk, #CybotCookiebotDialog, .qc-cmp2-container, .fc-consent-root').forEach(function(el) {
            try { el.remove(); } catch (e) { el.style.setProperty('display', 'none', 'important'); }
        });

        // 3. Auto-click popular reject / accept buttons to save preference quietly
        const consentSelectors = [
            '#onetrust-reject-all-handler',
            '#onetrust-accept-btn-handler',
            '#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll',
            '#CybotCookiebotDialogBodyButtonAccept',
            'button[id*="reject-all"]',
            'button[id*="accept-all"]'
        ];
        for (let i = 0; i < consentSelectors.length; i++) {
            const btn = document.querySelector(consentSelectors[i]);
            if (btn && typeof btn.click === 'function') {
                try { btn.click(); break; } catch (e) {}
            }
        }
    }

    // =========================================================================
    // YouTube Web Video Ad Auto-Skipper (Zero-Blank-Screen Instant Skip Engine)
    // =========================================================================
    let adWasMutedByShield = false;

    function runYouTubeAdSkipper() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // 1. Programmatically click all modern YouTube skip buttons
        const skipButtons = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.ytp-skip-ad-button',
            '.videoAdUiSkipButton',
            'button.ytp-ad-skip-button',
            'button.ytp-ad-skip-button-modern',
            '.ytp-ad-overlay-close-button',
            'button[class*="skip-button"]',
            '.ytp-ad-skip-button-container button',
            '.ytp-ad-skip-button-slot button',
            '.ytm-ad-skip-button',
            '[aria-label*="Skip ad"]',
            '[aria-label*="Skip Ad"]'
        ];
        for (let i = 0; i < skipButtons.length; i++) {
            const btn = document.querySelector(skipButtons[i]);
            if (btn && typeof btn.click === 'function') {
                try {
                    btn.click();
                    break;
                } catch (e) {}
            }
        }

        // 2. Trigger YouTube internal player API skip if available
        try {
            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player, #player');
            if (player && typeof player.skipAd === 'function') {
                player.skipAd();
            }
        } catch (e) {}

        // 3. Detect mobile YouTube sponsored ad or 0:00 stall state
        const adBadge = document.querySelector('.ytp-ad-text, [class*="badge-and-title"], [aria-label*="Sponsored"], .ytm-ad-preview-text, .ytp-ad-preview-text');
        const isSponsoredText = adBadge && /sponsored/i.test(adBadge.textContent || '');
        const playerEl = document.getElementById('movie_player') || document.querySelector('.html5-video-player, #player');
        const isAdShowing = isSponsoredText ||
                            (playerEl && (playerEl.classList.contains('ad-showing') || playerEl.classList.contains('ad-interrupting'))) ||
                            !!document.querySelector('.ad-showing, .ad-interrupting, ytm-player-ad-overlay, .ytp-ad-player-overlay, [class*="ad-showing"]');
        const video = document.querySelector('video.html5-main-video, video');

        if (isAdShowing && video) {
            try {
                if (!video.muted) {
                    video.muted = true;
                    adWasMutedByShield = true;
                }
                video.playbackRate = 16.0;
                if (!isNaN(video.duration) && video.duration > 0 && !video.ended) {
                    video.currentTime = Math.max(0, video.duration - 0.05);
                } else {
                    // Blocked ad media (duration is 0 or NaN, stalled at 0:00): trigger ended to release the player
                    video.dispatchEvent(new Event('ended'));
                }
                if (video.paused) {
                    video.play().catch(function() {});
                }
            } catch (e) {}
        } else if (video) {
            // Content video active: restore 1.0x speed and audio
            if (video.playbackRate > 1.0) {
                video.playbackRate = 1.0;
            }
            if (adWasMutedByShield) {
                video.muted = false;
                adWasMutedByShield = false;
            }
        }
    }

    if (window.location.hostname.includes('youtube.com')) {
        setInterval(runYouTubeAdSkipper, 100);
        document.addEventListener('timeupdate', runYouTubeAdSkipper, { capture: true, passive: true });
        document.addEventListener('loadeddata', runYouTubeAdSkipper, { capture: true, passive: true });
    }

    // =========================================================================
    // Anti-App-Nag: In-page app upsell & "Open App" header scrubber
    // =========================================================================
    function scrubAppPromos() {
        const host = window.location.hostname;
        const isAppNagHost = host.includes('ecosia.org') || host.includes('youtube.com') || host.includes('spotify.com') ||
            host.includes('reddit.com') || host.includes('instagram.com') || host.includes('quora.com');
        if (!isAppNagHost) return;

        if (host.includes('youtube.com')) {
            document.querySelectorAll('ytm-open-app-button, .mobile-topbar-header-endpoint:has(ytm-open-app-button), [aria-label*="Open App"], [aria-label*="open app"]').forEach(function(el) {
                el.style.setProperty('display', 'none', 'important');
            });
            document.querySelectorAll('ytm-mealbar-promo-renderer, ytm-promoted-sparkles-web-renderer, ytm-ad-slot-renderer').forEach(function(el) {
                try {
                    const parent = el.closest('ytm-item-section-renderer, ytm-compact-promoted-item-renderer') || el;
                    parent.remove();
                } catch (e) {}
            });
        } else if (host.includes('instagram.com')) {
            // Remove Instagram app banners
            document.querySelectorAll('div[data-testid="banner-mobile-app"], div[class*="MobileAppBanner"], div[role="dialog"]:has(a[href*="play.google.com"])').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
            // Scrub sponsored feed posts, suggestions, and promotional cards from Instagram feed
            const igCandidates = document.querySelectorAll('article, div[role="article"], main section > div > div > div');
            igCandidates.forEach(function(item) {
                if (item.tagName === 'MAIN' || item.tagName === 'BODY') return;
                const text = (item.innerText || item.textContent || '');
                const hasSponsoredKeyword = /\b(Sponsored|Paid partnership|Gesponsert|Publicidad|Patrocinado|Commandité)\b/i.test(text);
                const hasAdLinks = !!item.querySelector('a[href*="/about/ads"], a[href*="instagram.com/about/ads"], svg[aria-label*="Sponsored"], [aria-label*="Sponsored"], [aria-label*="sponsored"]');
                if (hasSponsoredKeyword || hasAdLinks) {
                    item.style.setProperty('display', 'none', 'important');
                    const container = item.closest('div[class*="x1y1aw1k"]') || item;
                    container.style.setProperty('display', 'none', 'important');
                }
            });
        } else if (host.includes('spotify.com')) {
            try {
                Object.defineProperty(navigator, 'platform', {
                    get: function() { return 'Linux x86_64'; },
                    configurable: true
                });
            } catch (e) {}
            document.querySelectorAll('div[data-testid="upsell-banner"], div[class*="MobileAppBanner"], a[href*="spotify.com/download"]').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
        } else if (host.includes('reddit.com')) {
            document.querySelectorAll('xpromo-app-selector, shreddit-async-loader[bundlename="bottom_sheet"]').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
            if (document.documentElement) document.documentElement.style.overflow = 'auto';
            if (document.body) document.body.style.overflow = 'auto';
        }
    }

    // =========================================================================
    // Media Playback State Reporter & native PiP coordination
    // =========================================================================
    window.__fokk_enter_pip_view = function() {
        document.documentElement.classList.add('fokk-pip-active');
    };

    window.__fokk_exit_pip_view = function() {
        document.documentElement.classList.remove('fokk-pip-active');
        window.dispatchEvent(new Event('resize'));
    };

    function notifyNativePlayback(isPlaying, hasActiveVideo) {
        try {
            if (window.FokkMediaBridge && typeof window.FokkMediaBridge.onMediaPlaybackChanged === 'function') {
                window.FokkMediaBridge.onMediaPlaybackChanged(isPlaying, hasActiveVideo);
            }
        } catch (e) {}
    }

    // =========================================================================
    // AI result filters for supported search pages
    // =========================================================================

    let isAiFilterEnabled = true;
    window.setAiFilterEnabled = function(enabled) {
        isAiFilterEnabled = !!enabled;
        if (!isAiFilterEnabled) {
            document.querySelectorAll('[data-fokk-ai-hidden]').forEach(function(el) {
                el.style.setProperty('display', '', '');
                el.style.setProperty('height', '', '');
                el.style.setProperty('min-height', '', '');
                el.style.setProperty('margin', '', '');
                el.style.setProperty('padding', '', '');
                el.style.setProperty('visibility', '', '');
                el.style.setProperty('pointer-events', '', '');
            });
            document.querySelectorAll('[data-fokk-aimode-hidden]').forEach(function(el) {
                el.style.setProperty('display', '', '');
            });
        } else {
            scrubAiOverviewsAndWidgets();
        }
    };

    function scrubAiOverviewsAndWidgets() {
        if (!isAiFilterEnabled) return;
        const host = window.location.hostname;
        let selector = '';
        if (host.includes('google.')) {
            selector = '#O26Dg, div[data-attrid*="ai_overview"], div[data-attrid="SGE_Search"], div[aria-label*="AI Overview"]';
        } else if (host.includes('bing.com')) {
            selector = 'cib-serp, #b_copilot';
        }
        if (!selector) return;

        document.querySelectorAll(selector).forEach(function(el) {
            if (el.getAttribute('data-fokk-ai-hidden') === 'true') return;
            el.setAttribute('data-fokk-ai-hidden', 'true');
            el.style.setProperty('display', 'none', 'important');
        });
    }

    function scrubAiContent() {
        try { scrubAiOverviewsAndWidgets(); } catch (e) {}
    }

    function hookVideoPlayback(video) {
        if (!video || video.__fokk_hooked) return;
        video.__fokk_hooked = true;

        let pendingState = false;
        const updateState = function() {
            if (pendingState) return;
            pendingState = true;
            requestAnimationFrame(function() {
                pendingState = false;
                const isPlaying = !video.paused && !video.ended;
                const hasActiveVideo = video.currentTime > 0 && !video.ended;
                notifyNativePlayback(isPlaying, hasActiveVideo);
            });
        };

        video.addEventListener('play', updateState);
        video.addEventListener('playing', updateState);
        video.addEventListener('pause', updateState);
        video.addEventListener('ended', updateState);
        video.addEventListener('emptied', updateState);

        if (!video.paused && !video.ended) {
            notifyNativePlayback(true, true);
        }
    }

    document.querySelectorAll('video').forEach(hookVideoPlayback);

    // =========================================================================
    // MutationObserver for cosmetic cleanup & active checks
    // =========================================================================
    try {
        const host = window.location.hostname;
        const needsObserver = host.includes('ecosia.org') || host.includes('youtube.com') || host.includes('spotify.com') ||
            host.includes('reddit.com') || host.includes('google.') || host.includes('bing.com') ||
            host.includes('instagram.com');

        if (needsObserver) {
            let pendingSweep = false;
            const observer = new MutationObserver(function() {
                if (pendingSweep) return;
                pendingSweep = true;
                setTimeout(function() {
                    pendingSweep = false;
                    if (host.includes('google.') || host.includes('bing.com')) scrubAiContent();
                    if (host.includes('youtube.com')) runYouTubeAdSkipper();
                    scrubAppPromos();
                    scrubCookieBanners();
                    document.querySelectorAll('video').forEach(hookVideoPlayback);
                }, 400);
            });
            observer.observe(document.documentElement, {
                childList: true,
                subtree: true
            });
        }
    } catch (e) {}

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            scrubAiContent();
            scrubAppPromos();
            scrubCookieBanners();
            runYouTubeAdSkipper();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        scrubAiContent();
        scrubAppPromos();
        scrubCookieBanners();
        runYouTubeAdSkipper();
        document.querySelectorAll('video').forEach(hookVideoPlayback);
    }

    // =========================================================================
    // 1. UNIVERSAL BACKGROUND MEDIA DECOUPLER
    // Prevents YouTube, Spotify, and audio streams from pausing on tab switch/lock
    // =========================================================================
    try {
        // Spoof document visibility
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        Object.defineProperty(document, 'webkitHidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });

        // Neutralize visibilitychange and blur events that pause video
        const originalAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide') {
                const wrappedListener = function(event) {
                    // Swallow event if it tries to report hidden
                    if (document.hidden) return;
                    return listener.apply(this, arguments);
                };
                return originalAddEventListener.call(this, type, wrappedListener, options);
            }
            return originalAddEventListener.call(this, type, listener, options);
        };
    } catch(e) {}

    // =========================================================================
    // 2. SPECULATIVE TOUCH DECELERATION & HOVER PRE-WARMER
    // Resolves DNS and warms TCP/TLS before finger clicks link (0ms latency feel)
    // =========================================================================
    try {
        let prewarmedUrls = new Set();
        let hoverTimer = null;

        function checkAndPrewarm(el) {
            const anchor = el.closest('a[href]');
            if (!anchor) return;
            const href = anchor.href;
            if (!href || !href.startsWith('http') || prewarmedUrls.has(href)) return;
            prewarmedUrls.add(href);
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.preWarmUrl === 'function') {
                window.FokkShieldBridge.preWarmUrl(href);
            }
        }

        document.addEventListener('touchstart', function(e) {
            if (e.target) checkAndPrewarm(e.target);
        }, { passive: true });

        document.addEventListener('pointerover', function(e) {
            if (hoverTimer) clearTimeout(hoverTimer);
            hoverTimer = setTimeout(() => {
                if (e.target) checkAndPrewarm(e.target);
            }, 60);
        }, { passive: true });
    } catch(e) {}

    // =========================================================================
    // 3. PAYWALL & RESTRICTIVE OVERLAY DETECTOR
    // Detects paywalls and notifies native UI to show 1-tap "Mirror View" badge
    // =========================================================================
    try {
        function detectPaywall() {
            const paywallSelectors = [
                '[class*="paywall"]', '[id*="paywall"]', '.subscription-wall',
                '[class*="article-gate"]', '[id*="article-gate"]', '.metered-paywall',
                'div[data-component="paywall"]', '#piano-id', '.piano-container'
            ];
            for (let sel of paywallSelectors) {
                const el = document.querySelector(sel);
                if (el && el.offsetParent !== null) {
                    if (window.FokkShieldBridge && typeof window.FokkShieldBridge.onPaywallDetected === 'function') {
                        window.FokkShieldBridge.onPaywallDetected(window.location.href);
                    }
                    break;
                }
            }
        }
        setTimeout(detectPaywall, 2000);
    } catch(e) {}

})();
