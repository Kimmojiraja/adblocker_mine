// AdShield / Fokk Shield Brave-Grade Injected Content Blocker & Media Assistant
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    function reportBlocked(count = 1) {
        try {
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.onAdsBlocked === 'function') {
                window.FokkShieldBridge.onAdsBlocked(count);
            }
        } catch (e) {}
    }

    // 1. YouTube Player Ad Config Stripper (Fetch & XHR Interceptor)
    function sanitizePlayerResponse(resp) {
        if (resp && typeof resp === 'object') {
            let foundAds = false;
            if (resp.adPlacements && resp.adPlacements.length > 0) {
                resp.adPlacements = [];
                foundAds = true;
            }
            if (resp.playerAds && resp.playerAds.length > 0) {
                resp.playerAds = [];
                foundAds = true;
            }
            if (resp.adSlots && resp.adSlots.length > 0) {
                resp.adSlots = [];
                foundAds = true;
            }
            if (resp.playbackTracking) {
                delete resp.playbackTracking.videostatsPlaybackUrl;
                delete resp.playbackTracking.videostatsDelayplayUrl;
            }
            if (foundAds) reportBlocked(1);
        }
    }

    function interceptYouTubeConfigs() {
        if (!window.location.hostname.includes('youtube.com')) return;

        if (window.ytInitialPlayerResponse) {
            sanitizePlayerResponse(window.ytInitialPlayerResponse);
        }

        try {
            let playerResponseVal = window.ytInitialPlayerResponse;
            Object.defineProperty(window, 'ytInitialPlayerResponse', {
                get() { return playerResponseVal; },
                set(val) {
                    sanitizePlayerResponse(val);
                    playerResponseVal = val;
                },
                configurable: true
            });
        } catch (e) {}

        const origFetch = window.fetch;
        if (typeof origFetch === 'function') {
            window.fetch = async function(...args) {
                const url = args[0] ? (typeof args[0] === 'string' ? args[0] : (args[0].url || '')) : '';
                const response = await origFetch.apply(this, args);
                if (url && typeof url === 'string' && url.includes('/youtubei/v1/player')) {
                    try {
                        const cloned = response.clone();
                        const json = await cloned.json();
                        sanitizePlayerResponse(json);
                        return new Response(JSON.stringify(json), {
                            status: response.status,
                            statusText: response.statusText,
                            headers: response.headers
                        });
                    } catch (e) {}
                }
                return response;
            };
        }

        const origXhrOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            this._reqUrl = url;
            return origXhrOpen.apply(this, [method, url, ...rest]);
        };

        const origResponseTextDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
        if (origResponseTextDesc) {
            Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
                get() {
                    let text = origResponseTextDesc.get.call(this);
                    if (this._reqUrl && typeof this._reqUrl === 'string' && this._reqUrl.includes('/youtubei/v1/player')) {
                        try {
                            const json = JSON.parse(text);
                            sanitizePlayerResponse(json);
                            return JSON.stringify(json);
                        } catch (e) {}
                    }
                    return text;
                }
            });
        }
    }

    interceptYouTubeConfigs();

    // 2. Video Ad Fast-Forward & Skip (Zero-Lag Engine)
    function checkAndSkipWebAd() {
        const moviePlayer = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
        if (!moviePlayer) return;

        const isAd = moviePlayer.classList.contains('ad-showing') || 
                     moviePlayer.classList.contains('ad-interrupting') ||
                     document.querySelector('.ytp-ad-player-overlay, .ytm-ad-badge, .ytp-ad-module');

        if (isAd) {
            // 1. YouTube native player API instant ad dismissal
            if (typeof moviePlayer.skipAd === 'function') {
                try { moviePlayer.skipAd(); } catch (e) {}
            }

            // 2. Trigger skip button click instantly
            const skipBtn = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button-text, .ytp-ad-skip-button-slot button, [class*="skip-button"]'
            );
            if (skipBtn && typeof skipBtn.click === 'function') {
                skipBtn.click();
                reportBlocked(1);
            }

            // 3. Fast-forward video without decoder stall (no blank screen!)
            const video = moviePlayer.querySelector('video') || document.querySelector('video');
            if (video) {
                video.muted = true;
                video.playbackRate = 16.0;
                if (video.duration && !isNaN(video.duration) && video.duration > 0.1) {
                    video.currentTime = Math.max(0, video.duration - 0.05);
                }
            }
        }
    }

    // 3. Universal Brave/uBlock Cosmetic CSS (Collapses blank spaces on livescore.in, Reddit, news)
    const cosmeticCss = `
        /* Generic Ad Containers & Empty Box Collapsers */
        ins.adsbygoogle,
        .adsbygoogle,
        [id*="google_ads"],
        [class*="google_ads"],
        iframe[id*="google_ads"],
        iframe[id*="aswift"],
        iframe[src*="doubleclick"],
        iframe[src*="adservice"],
        iframe[src*="googlesyndication"],
        iframe[src*="googleadservices"],
        div[id^="div-gpt-ad"],
        div[id*="gpt-ad"],
        div[data-ad-type],
        #banner_top,
        #banner_bottom,
        .banner_wrapper,
        [id*="banner"],
        [class*="banner-container"],
        [class*="ad-banner"],
        [class*="ad-wrapper"],
        [class*="ad-container"],
        [class*="ad-slot"],
        [id*="ad-slot"],
        [id*="ad-banner"],
        [class*="ad_wrapper"],
        [class*="ad_container"],
        [class*="adbox"],
        [id*="adbox"],
        [class*="ad-holder"],
        [class*="ad_holder"],
        [class*="advert-"],
        [id*="advert-"],
        [class*="banner-ad"],
        [id*="banner-ad"],
        div[data-ad-unit],
        div[data-ad],
        div[data-ad-slot],
        div[data-ad-placeholder],
        .OUTBRAIN,
        [id*="outbrain"],
        [class*="taboola"],
        [id*="taboola"],
        
        /* YouTube Web Ad Selectors */
        ytd-promoted-video-renderer,
        ytd-compact-promoted-video-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-display-ad-renderer,
        ytd-ad-slot-renderer,
        ytm-promoted-sparkles-web-renderer,
        ytm-companion-ad-renderer,
        #player-ads,
        #masthead-ad,
        .sparkles-light-cta,
        .ytm-ad-badge,
        .ytp-ad-player-overlay,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .ad-showing .ytp-ad-player-overlay,
        
        /* Modern Reddit Web Ad Selectors */
        shreddit-ad-post,
        shreddit-comment-ad,
        [data-adclicklocation],
        div[data-testid="ad-post"],
        div[adcontent],
        shreddit-post[is-promoted="true"],
        .promotedlink,
        .promoted {
            display: none !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            margin: 0 !important;
            padding: 0 !important;
            border: none !important;
            visibility: hidden !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
    `;

    const styleEl = document.createElement('style');
    styleEl.type = 'text/css';
    styleEl.appendChild(document.createTextNode(cosmeticCss));
    (document.head || document.documentElement).appendChild(styleEl);

    // 4. Dynamic Empty Box Collapser (Eliminates empty black rectangles on livescore.in & other sites)
    function collapseEmptyAdBoxes() {
        let collapsed = 0;

        // A. Collapse empty or blocked iframes and their reserved wrappers
        const iframes = document.querySelectorAll('iframe');
        iframes.forEach(iframe => {
            try {
                const src = (iframe.getAttribute('src') || '').toLowerCase();
                const id = (iframe.id || '').toLowerCase();
                const isAdFrame = src.includes('doubleclick') || src.includes('adservice') || 
                                  src.includes('googlesyndication') || src.includes('googleadservices') ||
                                  src.includes('ad') || id.includes('google_ads') || id.includes('aswift') ||
                                  id.includes('ad_') || id.includes('gpt');
                
                if (isAdFrame || !iframe.src || iframe.src === 'about:blank') {
                    const parent = iframe.parentElement;
                    if (parent && parent !== document.body && parent.offsetHeight > 10) {
                        parent.style.setProperty('display', 'none', 'important');
                        parent.style.setProperty('height', '0px', 'important');
                        parent.style.setProperty('min-height', '0px', 'important');
                        parent.style.setProperty('margin', '0px', 'important');
                        parent.style.setProperty('padding', '0px', 'important');
                        collapsed++;
                    }
                    iframe.style.setProperty('display', 'none', 'important');
                }
            } catch (e) {}
        });

        // B. Reddit in-feed promoted posts
        const redditAds = document.querySelectorAll('shreddit-ad-post, shreddit-comment-ad, [data-adclicklocation], div[data-testid="ad-post"]');
        redditAds.forEach(el => {
            if (el.style.display !== 'none') {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0px', 'important');
                collapsed++;
            }
        });

        // C. Reddit & generic feed posts containing "• Ad" text
        const badges = document.querySelectorAll('span, div');
        badges.forEach(badge => {
            if (badge.textContent && badge.textContent.trim() === 'Ad' && badge.children.length === 0) {
                const post = badge.closest('article, shreddit-post, div[data-testid="post-container"]');
                if (post && post.style.display !== 'none') {
                    post.style.setProperty('display', 'none', 'important');
                    post.style.setProperty('height', '0px', 'important');
                    collapsed++;
                }
            }
        });

        if (collapsed > 0) {
            reportBlocked(collapsed);
        }
    }

    // 5. Flawless Background Playback & PiP Enabler
    try {
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        window.addEventListener('visibilitychange', (e) => e.stopImmediatePropagation(), true);
        window.addEventListener('pagehide', (e) => e.stopImmediatePropagation(), true);

        // Prevent YouTube/browser from auto-pausing video when switching apps or minimizing
        const origPause = HTMLVideoElement.prototype.pause;
        HTMLVideoElement.prototype.pause = function(...args) {
            if (document.hidden || document.visibilityState === 'hidden') {
                return; // Discard background auto-pause!
            }
            return origPause.apply(this, args);
        };
    } catch (e) {}

    // 6. Sub-millisecond MutationObserver for instant ad skip & cosmetic collapse
    try {
        const observer = new MutationObserver(() => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['class', 'src', 'id']
        });
    } catch (e) {}

    // Periodic safety sweep
    setInterval(() => {
        checkAndSkipWebAd();
        collapseEmptyAdBoxes();
    }, 400);

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
        });
    } else {
        checkAndSkipWebAd();
        collapseEmptyAdBoxes();
    }
})();
