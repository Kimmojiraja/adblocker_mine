// Fokk Shield optional browser-side cosmetic filters
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    // =========================================================================
    // 1. YouTube & Instagram Pre-Emptive Data Sanitizers (Inline & Dynamic)
    // =========================================================================
    function sanitizeYtPlayerResponse(obj) {
        if (!obj || typeof obj !== 'object') return;
        try {
            if (obj.adPlacements) delete obj.adPlacements;
            if (obj.playerAds) delete obj.playerAds;
            if (obj.adSlots) delete obj.adSlots;
            if (obj.playbackTracking) {
                delete obj.playbackTracking.videostatsPlaybackUrl;
                delete obj.playbackTracking.videostatsDelayplayUrl;
                delete obj.playbackTracking.videostatsWatchtimeUrl;
                delete obj.playbackTracking.videostatsPtrackingUrl;
            }
        } catch (e) {}
    }

    function sanitizeYtData(obj) {
        if (!obj || typeof obj !== 'object') return;
        try {
            if (obj.adPlacements) delete obj.adPlacements;
            if (obj.playerAds) delete obj.playerAds;
            if (obj.adSlots) delete obj.adSlots;
        } catch (e) {}
    }

    // Hook ytInitialPlayerResponse so initial inline page data is sanitized before player boots
    let _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
    if (_ytInitialPlayerResponse) sanitizeYtPlayerResponse(_ytInitialPlayerResponse);
    try {
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            get: function() { return _ytInitialPlayerResponse; },
            set: function(val) {
                sanitizeYtPlayerResponse(val);
                _ytInitialPlayerResponse = val;
            },
            configurable: true
        });
    } catch (e) {}

    let _ytInitialData = window.ytInitialData;
    if (_ytInitialData) sanitizeYtData(_ytInitialData);
    try {
        Object.defineProperty(window, 'ytInitialData', {
            get: function() { return _ytInitialData; },
            set: function(val) {
                sanitizeYtData(val);
                _ytInitialData = val;
            },
            configurable: true
        });
    } catch (e) {}

    // Instagram response scrubber
    function sanitizeInstagramData(data) {
        if (!data || typeof data !== 'object') return false;
        let modified = false;
        try {
            // GraphQL timeline connection
            if (data.data && data.data.xdt_api__v1__feed__timeline__connection) {
                const conn = data.data.xdt_api__v1__feed__timeline__connection;
                if (Array.isArray(conn.edges)) {
                    conn.edges = conn.edges.filter(function(edge) {
                        const node = edge && edge.node;
                        if (!node) return true;
                        if (node.__typename === 'XDTFeedItem' && node.media_or_ad) {
                            return !node.media_or_ad.ad_id && !node.media_or_ad.is_paid_partnership && !node.media_or_ad.sponsored_data;
                        }
                        if (node.is_ad || node.is_sponsored || (node.media_or_ad && node.media_or_ad.ad_id)) return false;
                        return true;
                    });
                    modified = true;
                }
            }

            // GraphQL stories/reels connection
            if (data.data && data.data.xdt_api__v1__feed__reels_tray__connection) {
                const conn = data.data.xdt_api__v1__feed__reels_tray__connection;
                if (Array.isArray(conn.edges)) {
                    conn.edges = conn.edges.filter(function(edge) {
                        const node = edge && edge.node;
                        if (!node) return true;
                        if (node.is_ad || (node.media_or_ad && node.media_or_ad.ad_id)) return false;
                        return true;
                    });
                    modified = true;
                }
            }

            // REST timeline feed items
            if (Array.isArray(data.feed_items)) {
                data.feed_items = data.feed_items.filter(function(item) {
                    if (item.ad_id || item.is_sponsored || item.sponsored_data) return false;
                    if (item.media_or_ad && (item.media_or_ad.ad_id || item.media_or_ad.sponsored_data || item.media_or_ad.is_paid_partnership)) return false;
                    return true;
                });
                modified = true;
            }

            // REST general items
            if (Array.isArray(data.items)) {
                data.items = data.items.filter(function(item) {
                    if (item.ad_id || item.is_sponsored || item.sponsored_data) return false;
                    if (item.media_or_ad && (item.media_or_ad.ad_id || item.media_or_ad.sponsored_data || item.media_or_ad.is_paid_partnership)) return false;
                    return true;
                });
                modified = true;
            }
        } catch (e) {}
        return modified;
    }

    // =========================================================================
    // 2. Network Hook: Fetch & XHR Interceptor (YouTube + Instagram)
    // =========================================================================
    (function installNetworkInterceptor() {
        if (window.__fokk_net_hooked) return;
        window.__fokk_net_hooked = true;

        if (window.fetch) {
            const origFetch = window.fetch;
            window.fetch = async function(...args) {
                const response = await origFetch.apply(this, args);
                try {
                    const url = (typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url) || '');

                    // YouTube player data
                    if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next') || url.includes('/youtubei/v1/reel/')) {
                        const clone = response.clone();
                        const data = await clone.json();
                        sanitizeYtPlayerResponse(data);
                        return new Response(JSON.stringify(data), {
                            status: response.status,
                            statusText: response.statusText,
                            headers: response.headers
                        });
                    }

                    // Instagram feed & GraphQL data
                    if (url.includes('/graphql/query') || url.includes('/api/v1/feed/timeline/') || url.includes('/api/v1/feed/reels_tray/')) {
                        const clone = response.clone();
                        const data = await clone.json();
                        if (sanitizeInstagramData(data)) {
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
                if (this._fokkTargetUrl && typeof this._fokkTargetUrl === 'string') {
                    const isYt = this._fokkTargetUrl.includes('/youtubei/v1/player') || this._fokkTargetUrl.includes('/youtubei/v1/next') || this._fokkTargetUrl.includes('/youtubei/v1/reel/');
                    const isIg = this._fokkTargetUrl.includes('/graphql/query') || this._fokkTargetUrl.includes('/api/v1/feed/timeline/') || this._fokkTargetUrl.includes('/api/v1/feed/reels_tray/');
                    if (isYt || isIg) {
                        const self = this;
                        const origOnReady = this.onreadystatechange;
                        this.onreadystatechange = function() {
                            if (self.readyState === 4 && self.status === 200) {
                                try {
                                    const data = JSON.parse(self.responseText);
                                    let modified = false;
                                    if (isYt) {
                                        sanitizeYtPlayerResponse(data);
                                        modified = true;
                                    } else if (isIg) {
                                        modified = sanitizeInstagramData(data);
                                    }
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

        /* News & Media Article Ad Units, Sponsored Boxes & Overlays */
        div[id*="google_ads"],
        div[id*="div-gpt-ad"],
        div[id*="dfp-ad"],
        div[class*="ad-slot"],
        div[class*="ad-container"],
        div[class*="ad_wrapper"],
        div[class*="ad-wrapper"],
        div[class*="ad_container"],
        div[class*="advertisement"],
        div[class*="sponsored-post"],
        div[class*="sponsored-content"],
        div[class*="sponsored-stories"],
        div[class*="taboola"],
        div[id*="taboola"],
        div[class*="outbrain"],
        div[id*="outbrain"],
        .trc_related_container,
        .trc_rbox_div,
        .OUTBRAIN,
        div[data-ad-placeholder],
        div[data-ad-unit-path],
        aside[class*="ad"],
        div[class*="sticky-ad"],
        div[class*="bottom-ad"],
        div[class*="floating-ad"],
        div[class*="banner-ad"],
        div[class*="native-ad"],
        div[class*="ad-banner"],
        div[aria-label*="advertisement" i],
        div[aria-label*="sponsored" i],
        #google_vignette,
        div[id*="google-vignette"],
        div[class*="adthrive"],
        div[class*="mediavine"],
        div[id*="ezoic"],
        div[class*="ezoic"],

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

    // =========================================================================
    // YouTube Web Video Ad Auto-Skipper & Instant Stall Bypass Engine
    // =========================================================================
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

        if (isAdShowing) {
            // Remove ad overlay containers immediately
            document.querySelectorAll('ytm-player-ad-overlay, .ytp-ad-player-overlay, .ytp-ad-overlay-container, ytm-promoted-sparkles-web-renderer, [class*="ad-overlay"]').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
            if (playerEl) {
                playerEl.classList.remove('ad-showing', 'ad-interrupting');
            }
            if (video) {
                try {
                    if (!video.muted) {
                        video.muted = true;
                        adWasMutedByShield = true;
                    }
                    video.playbackRate = 16.0;
                    if (!isNaN(video.duration) && video.duration > 0 && !video.ended) {
                        video.currentTime = Math.max(0, video.duration - 0.05);
                    } else {
                        // Dead/blocked ad stream (paused at 0:00 or duration is 0/NaN): trigger ended to release player
                        video.dispatchEvent(new Event('ended'));
                    }
                    if (video.paused) {
                        video.play().catch(function() {});
                    }
                } catch (e) {}
            }
        } else if (video) {
            // Content video active: restore normal speed and un-mute
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
    // Instagram Feed Ad & Sponsored Story Scrubber
    // =========================================================================
    function scrubInstagramAds() {
        if (!window.location.hostname.includes('instagram.com')) return;

        // 1. Scrub Sponsored Articles / Posts from feed
        const articles = document.querySelectorAll('article, div[role="presentation"] > div > div, main div > div > article');
        articles.forEach(function(post) {
            try {
                let isSponsored = false;

                // Check 1: Direct link to /about/ads
                if (post.querySelector('a[href*="/about/ads"], a[href*="about/ads"]')) {
                    isSponsored = true;
                }

                // Check 2: Sponsored / Paid partnership aria-label
                if (!isSponsored && post.querySelector('[aria-label*="Sponsored" i], [aria-label*="Paid partnership" i]')) {
                    isSponsored = true;
                }

                // Check 3: Text content in headers/spans
                if (!isSponsored) {
                    const headers = post.querySelectorAll('header span, header a, span[dir="auto"]');
                    for (let i = 0; i < headers.length; i++) {
                        const t = (headers[i].textContent || '').trim().toLowerCase();
                        if (t === 'sponsored' || t === 'paid partnership' || t.startsWith('sponsored ·')) {
                            isSponsored = true;
                            break;
                        }
                    }
                }

                // Check 4: App install / shop prompts in feed
                if (!isSponsored && post.querySelector('a[href*="play.google.com"], a[href*="itunes.apple.com"]')) {
                    isSponsored = true;
                }

                if (isSponsored) {
                    post.style.setProperty('display', 'none', 'important');
                    post.remove();
                }
            } catch (e) {}
        });

        // 2. Remove Instagram web app banner & full-screen app gate dialogs
        document.querySelectorAll('div[data-testid="banner-mobile-app"], div[role="dialog"]:has(a[href*="play.google.com"])').forEach(function(el) {
            try { el.remove(); } catch (e) {}
        });
    }

    if (window.location.hostname.includes('instagram.com')) {
        setInterval(scrubInstagramAds, 300);
    }


    // =========================================================================
    // News & Content Site Ad Container & Sponsored Widget Scrubber
    // =========================================================================
    function scrubNewsAds() {
        const adSelectors = [
            'div[id*="google_ads"]', 'div[id*="div-gpt-ad"]', 'div[id*="dfp-ad"]',
            'div[class*="ad-slot"]', 'div[class*="ad-container"]', 'div[class*="advertisement"]',
            'div[class*="taboola"]', 'div[id*="taboola"]', 'div[class*="outbrain"]', 'div[id*="outbrain"]',
            '.trc_related_container', '.OUTBRAIN', 'aside[class*="ad"]', 'div[class*="sticky-ad"]',
            'div[class*="native-ad"]', 'div[class*="ad-banner"]', '#google_vignette',
            'div[class*="sponsored-post"]', 'div[class*="sponsored-content"]'
        ];
        try {
            document.querySelectorAll(adSelectors.join(',')).forEach(function(el) {
                try {
                    el.style.setProperty('display', 'none', 'important');
                    el.style.setProperty('height', '0px', 'important');
                    el.style.setProperty('min-height', '0px', 'important');
                    el.remove();
                } catch (e) {}
            });
        } catch (e) {}
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
                    if (host.includes('instagram.com')) scrubInstagramAds();
                    scrubNewsAds();
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
            scrubNewsAds();
            scrubAppPromos();
            scrubCookieBanners();
            runYouTubeAdSkipper();
            scrubInstagramAds();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        scrubAiContent();
        scrubNewsAds();
        scrubAppPromos();
        scrubCookieBanners();
        runYouTubeAdSkipper();
        scrubInstagramAds();
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
