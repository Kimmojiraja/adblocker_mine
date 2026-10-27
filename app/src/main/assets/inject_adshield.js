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
        const player = document.querySelector('.html5-video-player') || document.getElementById('movie_player');
        if (!player) return;

        const isAdShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
        if (isAdShowing) {
            // 1. Instant player API skip if exposed by YouTube
            if (typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch (e) {}
            }

            // 2. Fast-forward video - STRICTLY guarded to ads (duration < 120s) to never affect regular videos!
            const video = player.querySelector('video') || document.querySelector('video');
            if (video) {
                video.muted = true;
                if (video.duration && !isNaN(video.duration) && video.duration < 120) {
                    video.currentTime = video.duration;
                }
            }

            // 3. Trigger skip button click instantly
            const skipBtn = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button-text, .ytp-ad-skip-button-slot button, [class*="skip-button"]'
            );
            if (skipBtn && typeof skipBtn.click === 'function') {
                skipBtn.click();
                reportBlocked(1);
            }
        }
    }

    // 3. Universal Brave/uBlock Cosmetic CSS (Zero-box collapse & GDPR suppression)
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

        /* Sponsored Search Result Ads */
        #tads,
        #tadsb,
        #bottomads,
        [data-text-ad],
        .commercial-unit-desktop-top,
        .commercial-unit-mobile-top,
        .uEierd,
        .pla-unit-container,
        
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
        .ad-showing .ytp-ad-player-overlay,
        
        /* Modern Reddit Web Ad Selectors */
        shreddit-ad-post,
        shreddit-comment-ad,
        [data-adclicklocation],
        div[data-testid="ad-post"],
        div[adcontent],
        shreddit-post[is-promoted="true"],
        .promotedlink,
        .promoted,

        /* Universal Cookie & GDPR Consent Walls Auto-Dismiss (uBlock Annoyances Grade) */
        #onetrust-consent-sdk,
        #onetrust-banner-sdk,
        .onetrust-pc-dark,
        #onetrust-policy,
        .cookie-banner,
        .cookie-notice,
        #cookie-notice,
        .cookie-bar,
        #cookie-bar,
        .cookie-box,
        #cookie-box,
        [id*="cookie-banner"],
        [class*="cookie-banner"],
        [id*="cookie-notice"],
        [class*="cookie-notice"],
        [id*="cookie-consent"],
        [class*="cookie-consent"],
        [id*="cookie-popup"],
        [class*="cookie-popup"],
        .qc-cmp2-container,
        #cmp-app-container,
        #cmpbox,
        #cmp-banner,
        .fc-consent-root,
        .fc-dialog-container,
        .fc-dialog-overlay,
        .cc-window,
        .cc-banner,
        .cc-floating,
        #CybotCookiebotDialog,
        #CybotCookiebotDialogBody,
        div[aria-label="cookieconsent"],
        div[aria-label*="cookie"],
        div[aria-label*="Cookie"],
        .osano-cm-window,
        #didomi-host,
        #didomi-notice,
        #didomi-popup,
        #usercentrics-root,
        div[id*="usercentrics"],
        div.sp_message_container,
        #sp_message_panel_container,
        ytm-consent-bump-renderer,
        .consent-bump-v2,
        #consent-bump,
        .eom-v1-container,
        div[data-dialog-type="CONSENT_DIALOG"],
        div[id*="gdpr"],
        div[class*="gdpr"],
        .gdpr-banner,
        #gdpr-consent,
        .axeptio_mount,
        #axeptio_overlay,
        .trustarc-banner-container,
        #truste-consent-track,
        .cookieyes,
        #cookieyes {
            display: none !important;
            height: 0px !important;
            min-height: 0px !important;
            max-height: 0px !important;
            margin: 0px !important;
            padding: 0px !important;
            border: none !important;
            visibility: hidden !important;
            opacity: 0 !important;
            pointer-events: none !important;
            background: transparent !important;
        }
    `;


        /* Fullscreen PiP Video Mode (Ensures video fills floating PiP window without blank spaces) */
        html.fokk-pip-active, html.fokk-pip-active body {
            margin: 0 !important;
            padding: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            background: #000 !important;
            overflow: hidden !important;
        }
        html.fokk-pip-active .html5-video-player,
        html.fokk-pip-active #player,
        html.fokk-pip-active .player-container,
        html.fokk-pip-active #movie_player,
        html.fokk-pip-active video {
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100vw !important;
            height: 100vh !important;
            max-width: 100vw !important;
            max-height: 100vh !important;
            z-index: 2147483647 !important;
            transform: none !important;
            background: #000 !important;
        }
        html.fokk-pip-active video {
            object-fit: contain !important;
        }
        html.fokk-pip-active .ytp-chrome-bottom,
        html.fokk-pip-active .ytp-chrome-top,
        html.fokk-pip-active .ytp-gradient-top,
        html.fokk-pip-active .ytp-gradient-bottom,
        html.fokk-pip-active #masthead-container,
        html.fokk-pip-active ytm-mobile-topbar-renderer,
        html.fokk-pip-active ytm-pivot-bar-renderer,
        html.fokk-pip-active .related-items,
        html.fokk-pip-active #comments,
        html.fokk-pip-active ytm-comment-section-renderer,
        html.fokk-pip-active ytm-item-section-renderer,
        html.fokk-pip-active .header-bar,
        html.fokk-pip-active #player-control-overlay,
        html.fokk-pip-active ytm-engagement-panel {
            display: none !important;
        }

    const styleEl = document.createElement('style');
    styleEl.type = 'text/css';
    styleEl.appendChild(document.createTextNode(cosmeticCss));
    (document.head || document.documentElement).appendChild(styleEl);

    // 4. Multi-Level DOM Parent Collapser (Eradicates empty black/dark rectangular boxes)
    function collapseEmptyAdBoxes() {
        let collapsed = 0;

        // A. Process all ad elements and iframes
        const adElements = document.querySelectorAll(`
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
            [class*="ad-banner"],
            [class*="ad-wrapper"],
            [class*="ad-container"],
            [class*="ad-slot"],
            [id*="ad-slot"],
            [id*="ad-banner"],
            [class*="adbox"],
            [id*="adbox"],
            [class*="ad-holder"],
            [class*="ad_holder"],
            div[data-ad-unit],
            div[data-ad],
            div[data-ad-slot],
            div[data-ad-placeholder],
            .OUTBRAIN,
            [id*="outbrain"],
            [class*="taboola"],
            [id*="taboola"],
            #tads, #tadsb, #bottomads, [data-text-ad], .commercial-unit-desktop-top, .commercial-unit-mobile-top, .uEierd
        `);

        adElements.forEach(el => {
            if (el && el.style.display !== 'none') {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0px', 'important');
                el.style.setProperty('min-height', '0px', 'important');
                el.style.setProperty('margin', '0px', 'important');
                el.style.setProperty('padding', '0px', 'important');
                el.style.setProperty('background', 'transparent', 'important');
                collapsed++;

                // Collapse parent container if it's an empty wrapper leaving dark spaces
                let parent = el.parentElement;
                for (let level = 0; level < 3 && parent && parent !== document.body && parent !== document.documentElement; level++) {
                    const text = (parent.innerText || '').trim();
                    const visibleNonAdChildren = Array.from(parent.children).filter(child => {
                        const style = window.getComputedStyle(child);
                        return style.display !== 'none' && !child.matches('ins, iframe, [id*="ad"], [class*="ad"], style, script');
                    });

                    if (visibleNonAdChildren.length === 0 && text.length < 20) {
                        parent.style.setProperty('display', 'none', 'important');
                        parent.style.setProperty('height', '0px', 'important');
                        parent.style.setProperty('min-height', '0px', 'important');
                        parent.style.setProperty('margin', '0px', 'important');
                        parent.style.setProperty('padding', '0px', 'important');
                        parent.style.setProperty('background', 'transparent', 'important');
                        parent = parent.parentElement;
                    } else {
                        break;
                    }
                }
            }
        });

        // B. Collapse remaining empty or ad iframes
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
                    if (parent && parent !== document.body && parent.offsetHeight > 5) {
                        parent.style.setProperty('display', 'none', 'important');
                        parent.style.setProperty('height', '0px', 'important');
                        parent.style.setProperty('min-height', '0px', 'important');
                        parent.style.setProperty('margin', '0px', 'important');
                        parent.style.setProperty('padding', '0px', 'important');
                        parent.style.setProperty('background', 'transparent', 'important');
                        collapsed++;
                    }
                    iframe.style.setProperty('display', 'none', 'important');
                }
            } catch (e) {}
        });

        // C. Reddit in-feed promoted posts
        const redditAds = document.querySelectorAll('shreddit-ad-post, shreddit-comment-ad, [data-adclicklocation], div[data-testid="ad-post"]');
        redditAds.forEach(el => {
            if (el.style.display !== 'none') {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0px', 'important');
                collapsed++;
            }
        });

        // D. Reddit & generic feed posts containing "• Ad" text
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

        // E. Unlock body scroll if blocked by cookie banners
        const cookieBanners = document.querySelectorAll(`
            #onetrust-consent-sdk,
            #onetrust-banner-sdk,
            .cookie-banner,
            .cookie-notice,
            #cookie-notice,
            .qc-cmp2-container,
            #cmp-app-container,
            .fc-consent-root,
            .cc-window,
            #CybotCookiebotDialog
        `);
        if (cookieBanners.length > 0) {
            cookieBanners.forEach(banner => {
                banner.style.setProperty('display', 'none', 'important');
            });
            if (document.body && window.getComputedStyle(document.body).overflow === 'hidden') {
                document.body.style.setProperty('overflow', 'auto', 'important');
            }
            if (document.documentElement && window.getComputedStyle(document.documentElement).overflow === 'hidden') {
                document.documentElement.style.setProperty('overflow', 'auto', 'important');
            }
        }

        if (collapsed > 0) {
            reportBlocked(collapsed);
        }
    }

    // 5. Flawless Background Playback & PiP Enabler (Never pause on minimize/app switch)
    let lastUserTapTime = 0;
    ['click', 'touchstart', 'touchend', 'keydown', 'mousedown'].forEach(evt => {
        window.addEventListener(evt, () => {
            lastUserTapTime = Date.now();
        }, { capture: true, passive: true });
    });

    try {
        // Prevent websites from listening to visibility/blur to pause videos
        const origAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (type === 'visibilitychange' || type === 'pagehide' || (type === 'blur' && this === window)) {
                return;
            }
            return origAddEventListener.call(this, type, listener, options);
        };

        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });

        // Discard background auto-pause (only pause when user intentionally tapped within 400ms)
        const origPause = HTMLVideoElement.prototype.pause;
        HTMLVideoElement.prototype.pause = function(...args) {
            const isUserTap = (Date.now() - lastUserTapTime) < 400;
            if (!isUserTap && this.currentTime > 0 && !this.ended) {
                // Background auto-pause discarded - keep video & audio playing!
                return;
            }
            return origPause.apply(this, args);
        };
    } catch (e) {}

    // 6. Media Playback State Reporter & Universal PiP Controllers
    window.__fokk_enter_pip_view = function() {
        const video = document.querySelector('video');
        if (!video) return;

        // Ensure video is playing
        if (video.paused && video.currentTime > 0) {
            video.play().catch(() => {});
        }

        // Move video directly to body so no parent container transforms can clip or offset it
        if (!video.__origParent) {
            video.__origParent = video.parentElement;
            video.__origNextSibling = video.nextSibling;
        }
        document.body.appendChild(video);
        document.documentElement.classList.add('fokk-pip-active');
    };

    window.__fokk_exit_pip_view = function() {
        const video = document.querySelector('video');
        if (video && video.__origParent) {
            if (video.__origNextSibling) {
                video.__origParent.insertBefore(video, video.__origNextSibling);
            } else {
                video.__origParent.appendChild(video);
            }
            delete video.__origParent;
            delete video.__origNextSibling;
        }
        document.documentElement.classList.remove('fokk-pip-active');
    };

    // Continuous PiP Playback Watchdog
    setInterval(() => {
        if (document.documentElement.classList.contains('fokk-pip-active')) {
            const v = document.querySelector('video');
            if (v && v.paused && v.currentTime > 0 && !v.ended) {
                v.play().catch(() => {});
            }
        }
    }, 400);

    // Universal Cookie Banner Auto-Dismiss & Scroll Unlocker
    function dismissCookieWalls() {
        // A. Auto-click Accept/Reject/Dismiss buttons on banners
        const cookieButtons = document.querySelectorAll(`
            #onetrust-accept-btn-handler,
            #onetrust-reject-all-handler,
            .cc-btn.cc-dismiss,
            .cc-btn.cc-allow,
            button.fc-cta-consent,
            .fc-cta-do-not-consent,
            #didomi-notice-agree-button,
            #CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll,
            #CybotCookiebotDialogBodyButtonDecline,
            ytm-consent-bump-renderer button,
            .eom-button-row button,
            button[aria-label*="Accept all"],
            button[aria-label*="Reject all"],
            button[aria-label*="I agree"],
            button[id*="accept-cookie"],
            button[id*="reject-cookie"],
            button[class*="accept-cookie"],
            button[class*="reject-cookie"]
        `);
        cookieButtons.forEach(btn => {
            try { btn.click(); } catch(e) {}
        });

        // B. Hide all known cookie elements
        const cookieEls = document.querySelectorAll(`
            #onetrust-consent-sdk, #onetrust-banner-sdk, .onetrust-pc-dark,
            .cookie-banner, .cookie-notice, #cookie-notice, .cookie-bar, #cookie-bar,
            .qc-cmp2-container, #cmp-app-container, #cmpbox,
            .fc-consent-root, .fc-dialog-container, .fc-dialog-overlay,
            .cc-window, .cc-banner,
            #CybotCookiebotDialog,
            div[aria-label*="cookie"], div[aria-label*="Cookie"],
            .osano-cm-window, #didomi-host, #usercentrics-root,
            div.sp_message_container, #sp_message_panel_container,
            ytm-consent-bump-renderer, .consent-bump-v2, #consent-bump,
            .eom-v1-container, div[data-dialog-type="CONSENT_DIALOG"],
            [class*="cookie-popup"], [id*="cookie-popup"],
            [class*="cookie-consent"], [id*="cookie-consent"],
            [class*="gdpr-banner"], [id*="gdpr-banner"]
        `);
        cookieEls.forEach(el => {
            el.style.setProperty('display', 'none', 'important');
            el.style.setProperty('visibility', 'hidden', 'important');
            el.style.setProperty('height', '0px', 'important');
            el.style.setProperty('pointer-events', 'none', 'important');
        });

        // C. Clear backdrop overlays & unlock page scroll
        const overlays = document.querySelectorAll('.modal-backdrop, .fade, .sp_message_panel_container, .fc-dialog-overlay');
        overlays.forEach(o => o.style.setProperty('display', 'none', 'important'));

        if (document.body) {
            document.body.style.setProperty('overflow', 'auto', 'important');
            document.body.style.setProperty('position', 'static', 'important');
        }
        if (document.documentElement) {
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
        }
    }

    function notifyNativePlayback(isPlaying) {
        try {
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.onMediaPlaybackChanged === 'function') {
                window.FokkShieldBridge.onMediaPlaybackChanged(isPlaying);
            }
        } catch (e) {}
    }

    function hookVideoPlayback(video) {
        if (!video || video.__fokk_hooked) return;
        video.__fokk_hooked = true;
        video.addEventListener('play', () => notifyNativePlayback(true));
        video.addEventListener('playing', () => notifyNativePlayback(true));
        video.addEventListener('pause', () => {
            const anyPlaying = Array.from(document.querySelectorAll('video')).some(v => !v.paused && !v.ended);
            notifyNativePlayback(anyPlaying);
        });
        video.addEventListener('ended', () => {
            const anyPlaying = Array.from(document.querySelectorAll('video')).some(v => !v.paused && !v.ended);
            notifyNativePlayback(anyPlaying);
        });
        if (!video.paused && !video.ended) {
            notifyNativePlayback(true);
        }
    }

    document.querySelectorAll('video').forEach(hookVideoPlayback);

    // 6. Sub-millisecond MutationObserver for instant ad skip & cosmetic collapse
    try {
        const observer = new MutationObserver(() => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWalls();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
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
        dismissCookieWalls();
    }, 400);

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWalls();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        checkAndSkipWebAd();
        collapseEmptyAdBoxes();
        dismissCookieWalls();
    }
})();
