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

    // =========================================================================
    // 1. YouTube Player Ad Config Stripper (Fetch & XHR Interceptor)
    // =========================================================================
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

    // =========================================================================
    // 2. Video Ad Fast-Forward & Skip (Zero-Lag Engine)
    // =========================================================================
    function checkAndSkipWebAd() {
        const player = document.querySelector('.html5-video-player, #movie_player, #player, ytm-mobile-player');
        const adIndicators = document.querySelectorAll(
            '.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .video-ads, ' +
            'ytm-ad-slot-renderer, .ytp-ad-text, [class*="ytp-ad-"], [class*="ad-showing"]'
        );
        const isAdShowing = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) || (adIndicators.length > 0);

        if (isAdShowing) {
            // 1. Instant player API skip if exposed by YouTube
            if (player && typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch (e) {}
            }

            // 2. Fast-forward video instantly at 16x speed with sound muted
            const video = (player ? player.querySelector('video') : null) || document.querySelector('video');
            if (video) {
                try {
                    video.muted = true;
                    video.playbackRate = 16.0;
                    if (video.duration && !isNaN(video.duration)) {
                        video.currentTime = video.duration;
                    }
                } catch (e) {}
            }

            // 3. Trigger all possible skip buttons instantly
            const skipBtns = document.querySelectorAll(`
                .ytp-ad-skip-button,
                .ytp-ad-skip-button-modern,
                .ytp-skip-ad-button,
                .videoAdUiSkipButton,
                button.ytp-ad-skip-button-text,
                .ytp-ad-skip-button-slot button,
                .ytm-skip-ad-button,
                ytm-skip-ad-button,
                button[class*="skip-button"],
                button[aria-label*="Skip"],
                button[aria-label*="skip"],
                .ytp-ad-overlay-close-button,
                [class*="ytp-ad-skip"]
            `);
            skipBtns.forEach(btn => {
                try {
                    btn.click();
                    reportBlocked(1);
                } catch (e) {}
            });
        }

        // Hide "Visit Advertiser", companion ads & sponsored chips
        if (window.location.hostname.includes('youtube.com')) {
            const sponsoredCards = document.querySelectorAll(`
                ytm-promoted-sparkles-web-renderer,
                ytm-promoted-sparkles-text-search-renderer,
                ytm-promoted-video-renderer,
                ytm-compact-promoted-video-renderer,
                ytm-companion-ad-renderer,
                ytm-in-feed-ad-layout-renderer,
                ytm-ad-slot-renderer,
                .ytm-companion-ad,
                .ytm-promoted-sparkles-web-renderer,
                ytm-mealbar-promo-renderer,
                ytm-brand-header-renderer,
                [target-id="engagement-panel-ads"],
                .sparkles-light-cta,
                .ytm-ad-badge,
                div[class*="companion-ad"],
                div[class*="ad-slot"],
                div[class*="promoted-sparkles"]
            `);
            sponsoredCards.forEach(card => {
                card.style.setProperty('display', 'none', 'important');
                card.style.setProperty('height', '0px', 'important');
                card.style.setProperty('min-height', '0px', 'important');
                card.style.setProperty('pointer-events', 'none', 'important');
            });
        }
    }

    // =========================================================================
    // 3. Universal Brave/uBlock Cosmetic CSS (Zero-box collapse & Annoyance suppression)
    // =========================================================================
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
        [class*="ad-placeholder"],
        [id*="ad-placeholder"],
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
        
        /* YouTube Mobile Sponsored Ads & Companion Banners */
        ytd-promoted-video-renderer,
        ytd-compact-promoted-video-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-display-ad-renderer,
        ytd-ad-slot-renderer,
        ytm-promoted-sparkles-web-renderer,
        ytm-promoted-sparkles-text-search-renderer,
        ytm-promoted-video-renderer,
        ytm-compact-promoted-video-renderer,
        ytm-companion-ad-renderer,
        ytm-in-feed-ad-layout-renderer,
        ytm-ad-slot-renderer,
        ytm-mealbar-promo-renderer,
        ytm-brand-header-renderer,
        [target-id="engagement-panel-ads"],
        .ytm-companion-ad,
        #player-ads,
        #masthead-ad,
        .sparkles-light-cta,
        .ytm-ad-badge,
        .ad-showing .ytp-ad-player-overlay,
        [class*="companion-ad"],
        
        /* Modern Reddit Web Ad Selectors */
        shreddit-ad-post,
        shreddit-comment-ad,
        [data-adclicklocation],
        div[data-testid="ad-post"],
        div[adcontent],
        shreddit-post[is-promoted="true"],
        .promotedlink,
        .promoted,

        /* Annoyance Overlays, "Read in App" Nags & Interstitials */
        .read-on-app,
        .readOnApp,
        .appCta,
        .read_app,
        .et_app_download,
        [data-plugin="readOnApp"],
        .stick_banner,
        .floating-btn,
        .app-sticky-banner,
        [class*="smartbanner"],
        [class*="app-banner"],
        [class*="app-download"],
        [class*="open-in-app"],
        [class*="openInApp"],
        [id*="app-banner"],
        [class*="download-app-banner"],
        .app-download-bar,
        .modal-backdrop,
        div[class*="dimmer"],
        .paywall,
        [class*="paywall"],
        [id*="paywall"],
        [class*="gate-container"],

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
        html.fokk-pip-active ytm-mobile-player,
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
    // 4. Multi-Level DOM Parent Collapser & "Advertisement" Placeholder Killer
    // =========================================================================
    function collapseEmptyAdBoxes() {
        let collapsed = 0;

        // A. Eradicate containers containing only "Advertisement" or "Sponsored" labels
        const labels = document.querySelectorAll('span, div, p, label, b, strong, small');
        for (let i = 0; i < labels.length; i++) {
            const el = labels[i];
            const txt = el.textContent ? el.textContent.trim().toLowerCase() : '';
            if (txt === 'advertisement' || txt === 'advertisements' || txt === 'sponsored' || txt === 'promoted' || txt === 'advertorial') {
                if (el.tagName === 'P' && el.textContent.length > 25) continue;
                const parent = el.closest('[class*="ad"], [id*="ad"], [class*="banner"], [id*="banner"], [class*="slot"], [id*="slot"], div, section, aside');
                if (parent && parent !== document.body && parent !== document.documentElement) {
                    const parentText = parent.textContent.trim().toLowerCase();
                    // If parent only contains the label or very little text, collapse completely
                    if (parentText.length < 80 || parent.querySelector('ins, iframe, [id*="google_ads"], [id*="dfp"]')) {
                        parent.style.setProperty('display', 'none', 'important');
                        parent.style.setProperty('height', '0px', 'important');
                        parent.style.setProperty('min-height', '0px', 'important');
                        parent.style.setProperty('margin', '0px', 'important');
                        parent.style.setProperty('padding', '0px', 'important');
                        parent.style.setProperty('border', 'none', 'important');
                        parent.style.setProperty('background', 'transparent', 'important');
                        collapsed++;
                    }
                }
            }
        }

        // B. Process all ad elements, wrappers, and iframes
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
            [class*="ad-placeholder"],
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

                // Collapse parent container if it's an empty wrapper leaving dark/blank spaces
                let parent = el.parentElement;
                for (let level = 0; level < 3 && parent && parent !== document.body && parent !== document.documentElement; level++) {
                    const text = (parent.innerText || '').trim();
                    const visibleNonAdChildren = Array.from(parent.children).filter(child => {
                        const style = window.getComputedStyle(child);
                        return style.display !== 'none' && !child.matches('ins, iframe, [id*="ad"], [class*="ad"], style, script');
                    });

                    if (visibleNonAdChildren.length === 0 && text.length < 35) {
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

        // C. Collapse empty or ad iframes
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

        if (collapsed > 0) {
            reportBlocked(collapsed);
        }
    }

    // =========================================================================
    // 5. Anti-Auto-Pause & Background Playback Engine
    // =========================================================================
    let lastUserTapTime = 0;
    ['click', 'touchstart', 'touchend', 'keydown', 'mousedown'].forEach(evt => {
        window.addEventListener(evt, () => {
            lastUserTapTime = Date.now();
        }, { capture: true, passive: true });
    });

    try {
        // Blind websites to background / visibility state changes so they cannot pause media
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'webkitHidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });

        // Stop propagation of visibility events in capture phase
        const stopVisibilityPropagation = (e) => {
            e.stopImmediatePropagation();
        };
        ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur'].forEach(evt => {
            window.addEventListener(evt, stopVisibilityPropagation, true);
            document.addEventListener(evt, stopVisibilityPropagation, true);
        });

        // Prevent YouTube & media players from attaching visibility change listeners to auto-pause
        const origAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide') {
                return;
            }
            if (type === 'blur' && (this === window || this === document)) {
                return;
            }
            return origAddEventListener.call(this, type, listener, options);
        };

        // Discard unwanted background auto-pauses on HTMLMediaElement
        const origMediaPause = HTMLMediaElement.prototype.pause;
        HTMLMediaElement.prototype.pause = function(...args) {
            const isUserTap = (Date.now() - lastUserTapTime) < 600;
            const inPip = document.documentElement.classList.contains('fokk-pip-active');
            if (inPip || (!isUserTap && this.currentTime > 0 && !this.ended)) {
                // Background auto-pause discarded - keep video & audio playing!
                return;
            }
            return origMediaPause.apply(this, args);
        };
    } catch (e) {}

    // =========================================================================
    // 6. Media Playback State Reporter & Universal PiP Controllers
    // =========================================================================
    window.__fokk_enter_pip_view = function() {
        document.documentElement.classList.add('fokk-pip-active');
        const video = document.querySelector('video');
        if (video) {
            video.play().catch(() => {});
            // Move video directly to body so no parent container transforms can clip or offset it
            if (!video.__origParent) {
                video.__origParent = video.parentElement;
                video.__origNextSibling = video.nextSibling;
            }
            document.body.appendChild(video);
        }
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
    }, 300);

    // =========================================================================
    // 7. Universal Cookie Banner & "Read on App" Nags Suppressor
    // =========================================================================
    function dismissCookieWallsAndNags() {
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

        // B. Hide all known cookie elements and App install sticky nags
        const suppressEls = document.querySelectorAll(`
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
            [class*="gdpr-banner"], [id*="gdpr-banner"],
            .read-on-app, .readOnApp, .appCta, .read_app, .et_app_download,
            [data-plugin="readOnApp"], .stick_banner, .floating-btn, .app-sticky-banner,
            [class*="smartbanner"], [class*="app-banner"], [class*="app-download"],
            [class*="open-in-app"], [class*="openInApp"], [id*="app-banner"],
            [class*="download-app-banner"], .app-download-bar
        `);
        suppressEls.forEach(el => {
            el.style.setProperty('display', 'none', 'important');
            el.style.setProperty('visibility', 'hidden', 'important');
            el.style.setProperty('height', '0px', 'important');
            el.style.setProperty('pointer-events', 'none', 'important');
        });

        // C. Clear backdrop overlays & unlock page scroll
        const overlays = document.querySelectorAll('.modal-backdrop, .fade, .sp_message_panel_container, .fc-dialog-overlay, div[class*="dimmer"], .paywall');
        overlays.forEach(o => o.style.setProperty('display', 'none', 'important'));

        if (document.body) {
            document.body.style.setProperty('overflow', 'auto', 'important');
            document.body.style.setProperty('position', 'static', 'important');
        }
        if (document.documentElement) {
            document.documentElement.style.setProperty('overflow', 'auto', 'important');
        }
    }

    function notifyNativePlayback(isPlaying, hasActiveVideo) {
        try {
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.onMediaPlaybackChanged === 'function') {
                window.FokkShieldBridge.onMediaPlaybackChanged(isPlaying, hasActiveVideo);
            }
        } catch (e) {}
    }

    function hookVideoPlayback(video) {
        if (!video || video.__fokk_hooked) return;
        video.__fokk_hooked = true;

        const updateState = () => {
            const videos = Array.from(document.querySelectorAll('video'));
            const isPlaying = videos.some(v => !v.paused && !v.ended);
            const hasActiveVideo = videos.some(v => v.currentTime > 0 && !v.ended);
            notifyNativePlayback(isPlaying, hasActiveVideo);
        };

        video.addEventListener('play', updateState);
        video.addEventListener('playing', updateState);
        video.addEventListener('pause', updateState);
        video.addEventListener('ended', updateState);
        video.addEventListener('timeupdate', () => {
            if (!video.paused && !video.ended) {
                notifyNativePlayback(true, true);
            }
        });

        if (!video.paused && !video.ended) {
            notifyNativePlayback(true, true);
        }
    }

    document.querySelectorAll('video').forEach(hookVideoPlayback);

    // =========================================================================
    // 8. Sub-millisecond MutationObserver for instant ad skip & cosmetic collapse
    // =========================================================================
    try {
        const observer = new MutationObserver(() => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWallsAndNags();
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
        dismissCookieWallsAndNags();
    }, 350);

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWallsAndNags();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        checkAndSkipWebAd();
        collapseEmptyAdBoxes();
        dismissCookieWallsAndNags();
    }
})();
