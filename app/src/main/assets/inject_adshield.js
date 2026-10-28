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
    // 1. YouTube Player Ad Config Stripper (Safe In-Memory Initialization)
    // =========================================================================
    function sanitizePlayerResponse(resp) {
        if (!resp || typeof resp !== 'object') return;
        try {
            if (resp.adPlacements && resp.adPlacements.length > 0) resp.adPlacements = [];
            if (resp.playerAds && resp.playerAds.length > 0) resp.playerAds = [];
            if (resp.adSlots && resp.adSlots.length > 0) resp.adSlots = [];
        } catch (e) {}
    }

    try {
        let _ytPlayerResp = window.ytInitialPlayerResponse;
        if (_ytPlayerResp) sanitizePlayerResponse(_ytPlayerResp);
        Object.defineProperty(window, 'ytInitialPlayerResponse', {
            get() { return _ytPlayerResp; },
            set(val) {
                sanitizePlayerResponse(val);
                _ytPlayerResp = val;
            },
            configurable: true
        });
    } catch (e) {}

    // =========================================================================
    // 2. Video Ad Fast-Forward & Skip (Zero-Lag Engine)
    // =========================================================================
    function checkAndSkipWebAd() {
        const player = document.querySelector('.html5-video-player, #movie_player, ytm-mobile-player');
        // Strictly check if YouTube is actively playing an ad via its native state classes
        const isYtAd = !!(player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')));
        const genericAdVideo = !isYtAd && !!document.querySelector('.ad-showing video, .ad-interrupting video');
        const isAdShowing = isYtAd || genericAdVideo;

        if (isAdShowing) {
            // 1. Instant player API skip if exposed by YouTube
            if (player && typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch (e) {}
            }

            // 2. Fast-forward video instantly at 16x speed with sound muted (no seek to duration to prevent media decoder stall/black screen)
            const video = (player ? player.querySelector('video') : null) || document.querySelector('.ad-showing video, .ad-interrupting video');
            if (video) {
                try {
                    video.muted = true;
                    video.playbackRate = 16.0;
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
                button[aria-label*="skip"]
            `);
            skipBtns.forEach(btn => {
                try {
                    btn.click();
                    reportBlocked(1);
                } catch (e) {}
            });
        } else {
            // Ensure normal video playback speed is preserved and not stuck at 16x
            if (player) {
                const mainVideo = player.querySelector('video');
                if (mainVideo && mainVideo.playbackRate > 2.0) {
                    mainVideo.playbackRate = 1.0;
                }
            }
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
        
        /* Instagram Sponsored & Meta AI Clutter */
        [data-blade="ad"],
        [aria-label="Sponsored"],
        a[href*="/ads/"],
        a[href*="/about/ads/"],
        [aria-label*="Meta AI"],
        svg[aria-label*="Meta AI"],
        a[href*="meta_ai"],

        /* X / Twitter Promoted Tweets, Trends & Grok AI */
        [data-testid="placementTracking"],
        [data-testid="promotedIndicator"],
        [aria-label="Grok"],
        [data-testid="GrokDrawer"],
        a[href="/i/grok"],
        div[data-testid="inlineGrokSummary"],

        /* Google AI Overviews & Bing Copilot Search Clutter */
        #O26Dg,
        div[data-attrid*="ai_overview"],
        div[data-attrid="wa:/description"],
        div[aria-label="AI Overview"],
        div[data-md*="ai_overview"],
        cib-serp,
        #b_copilot,

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

        /* Fullscreen PiP Video Mode (Smooth native scaling without locking DOM layout) */
        html.fokk-pip-active video {
            width: 100% !important;
            height: 100% !important;
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
        }
    };

    window.__fokk_exit_pip_view = function() {
        document.documentElement.classList.remove('fokk-pip-active');
        const video = document.querySelector('video');
        if (video) {
            window.dispatchEvent(new Event('resize'));
            if (video.paused && !video.ended && video.currentTime > 0) {
                video.play().catch(() => {});
            }
        }
    };

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
            window.__fokk_exit_pip_view();
            window.dispatchEvent(new Event('resize'));
        }
    });

    window.addEventListener('focus', () => {
        window.__fokk_exit_pip_view();
        window.dispatchEvent(new Event('resize'));
    });

    window.addEventListener('pageshow', () => {
        window.__fokk_exit_pip_view();
        window.dispatchEvent(new Event('resize'));
    });

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

    // =========================================================================
    // 7. Advanced Social Media Shield (Instagram, X, Reddit) & AI Slop Filter
    // =========================================================================

    function isXPromotedItem(entry) {
        if (!entry || typeof entry !== 'object') return false;
        if (typeof entry.entryId === 'string' && (
            entry.entryId.startsWith('promoted-tweet-') ||
            entry.entryId.startsWith('promotedTweet-') ||
            entry.entryId.startsWith('promoted-')
        )) {
            return true;
        }
        const itemContent = entry.content && (
            entry.content.itemContent ||
            (entry.content.items && entry.content.items[0] && entry.content.items[0].item && entry.content.items[0].item.itemContent)
        );
        if (itemContent && (itemContent.promotedMetadata || itemContent.isPromoted)) {
            return true;
        }
        if (entry.promotedMetadata) return true;
        return false;
    }

    function scrubXGraphQLTimeline(obj) {
        if (!obj || typeof obj !== 'object') return false;
        let modified = false;

        function walk(node) {
            if (!node || typeof node !== 'object') return;
            if (Array.isArray(node)) {
                for (let i = node.length - 1; i >= 0; i--) {
                    const item = node[i];
                    if (isXPromotedItem(item)) {
                        node.splice(i, 1);
                        modified = true;
                    } else {
                        walk(item);
                    }
                }
            } else {
                for (const key of Object.keys(node)) {
                    if (key === 'promotedMetadata' && node[key]) {
                        delete node[key];
                        modified = true;
                    } else if (key === 'entries' && Array.isArray(node[key])) {
                        for (let i = node[key].length - 1; i >= 0; i--) {
                            if (isXPromotedItem(node[key][i])) {
                                node[key].splice(i, 1);
                                modified = true;
                            } else {
                                walk(node[key][i]);
                            }
                        }
                    } else {
                        walk(node[key]);
                    }
                }
            }
        }

        walk(obj);
        return modified;
    }

    function scrubRedditJsonFeed(obj) {
        if (!obj || typeof obj !== 'object') return false;
        let modified = false;

        function walk(node) {
            if (!node || typeof node !== 'object') return;
            if (Array.isArray(node)) {
                for (let i = node.length - 1; i >= 0; i--) {
                    const item = node[i];
                    if (item && typeof item === 'object' && (item.isPromoted || item.is_promoted || item.promoted || item.advertisement)) {
                        node.splice(i, 1);
                        modified = true;
                    } else {
                        walk(item);
                    }
                }
            } else {
                for (const key of Object.keys(node)) {
                    if (key === 'posts' && Array.isArray(node[key])) {
                        for (let i = node[key].length - 1; i >= 0; i--) {
                            const p = node[key][i];
                            if (p && (p.isPromoted || p.is_promoted || p.promoted)) {
                                node[key].splice(i, 1);
                                modified = true;
                            } else {
                                walk(p);
                            }
                        }
                    } else {
                        walk(node[key]);
                    }
                }
            }
        }

        walk(obj);
        return modified;
    }

    function scrubInstagramFeed() {
        if (!window.location.hostname.includes('instagram.com')) return;
        const articles = document.querySelectorAll('article, div[role="presentation"] article, main article');
        for (let i = 0; i < articles.length; i++) {
            const article = articles[i];
            if (article.__fokk_sponsored) continue;

            let isSponsored = false;
            const labels = article.querySelectorAll('span, a, header span, header a, div');
            for (let j = 0; j < labels.length; j++) {
                const txt = labels[j].textContent ? labels[j].textContent.trim().toLowerCase() : '';
                if (txt === 'sponsored' || txt === 'sponsrad' || txt === 'gesponsert' || txt === 'sponsorisé' || txt === 'publicidad' || txt === 'patrocinado') {
                    isSponsored = true;
                    break;
                }
            }

            if (!isSponsored && article.querySelector('a[href*="/ads/"], [data-blade="ad"], [aria-label="Sponsored"]')) {
                isSponsored = true;
            }

            if (isSponsored) {
                article.__fokk_sponsored = true;
                article.style.setProperty('display', 'none', 'important');
                article.style.setProperty('height', '0px', 'important');
                article.style.setProperty('min-height', '0px', 'important');
                article.style.setProperty('margin', '0px', 'important');
                article.style.setProperty('padding', '0px', 'important');
                article.style.setProperty('overflow', 'hidden', 'important');
                article.style.setProperty('pointer-events', 'none', 'important');
                reportBlocked(1);
            }
        }

        const metaAi = document.querySelectorAll(`
            [aria-label*="Meta AI"],
            svg[aria-label*="Meta AI"],
            a[href*="meta_ai"],
            div[role="button"][aria-label*="Meta AI"]
        `);
        metaAi.forEach(el => {
            const container = el.closest('div[role="button"], a, div') || el;
            container.style.setProperty('display', 'none', 'important');
        });
    }

    function scrubXFeedAndGrok() {
        if (!window.location.hostname.includes('twitter.com') && !window.location.hostname.includes('x.com')) return;
        const tweets = document.querySelectorAll('article[data-testid="tweet"]');
        for (let i = 0; i < tweets.length; i++) {
            const tweet = tweets[i];
            if (tweet.__fokk_promoted) continue;

            let isPromoted = false;
            if (tweet.querySelector('[data-testid="placementTracking"], [data-testid="promotedIndicator"]')) {
                isPromoted = true;
            } else {
                const spans = tweet.querySelectorAll('span, div');
                for (let j = 0; j < spans.length; j++) {
                    const txt = spans[j].textContent ? spans[j].textContent.trim() : '';
                    if (txt === 'Ad' || txt === 'Promoted' || txt.startsWith('Promoted by') || txt === 'Sponsorisé' || txt === 'Gesponsert') {
                        isPromoted = true;
                        break;
                    }
                }
            }

            if (isPromoted) {
                tweet.__fokk_promoted = true;
                const cell = tweet.closest('div[data-testid="cellInnerDiv"]') || tweet;
                cell.style.setProperty('display', 'none', 'important');
                cell.style.setProperty('height', '0px', 'important');
                cell.style.setProperty('min-height', '0px', 'important');
                cell.style.setProperty('margin', '0px', 'important');
                cell.style.setProperty('padding', '0px', 'important');
                cell.style.setProperty('overflow', 'hidden', 'important');
                cell.style.setProperty('pointer-events', 'none', 'important');
                reportBlocked(1);
            }
        }

        const grokEls = document.querySelectorAll(`
            [aria-label="Grok"],
            [data-testid="GrokDrawer"],
            a[href="/i/grok"],
            div[data-testid="inlineGrokSummary"]
        `);
        grokEls.forEach(el => {
            el.style.setProperty('display', 'none', 'important');
        });
    }

    function scrubRedditFeedAndAi() {
        if (!window.location.hostname.includes('reddit.com')) return;
        const promotedReddit = document.querySelectorAll(`
            shreddit-ad-post,
            shreddit-comment-ad,
            [data-adclicklocation],
            div[data-testid="ad-post"],
            div[adcontent],
            shreddit-post[is-promoted="true"],
            shreddit-post[is-promoted],
            .promotedlink,
            .promoted,
            div[id*="ad-element"],
            shreddit-summary-card,
            div[data-testid="ai-summary"],
            div[class*="ai-answer"]
        `);
        promotedReddit.forEach(el => {
            el.style.setProperty('display', 'none', 'important');
            el.style.setProperty('height', '0px', 'important');
            el.style.setProperty('min-height', '0px', 'important');
            el.style.setProperty('pointer-events', 'none', 'important');
            if (!el.__fokk_counted) {
                el.__fokk_counted = true;
                reportBlocked(1);
            }
        });
    }

    function scrubAiOverviewsAndWidgets() {
        const host = window.location.hostname;
        if (host.includes('google.')) {
            // 1. Text-based card detector for Google Mobile AI Overview (matches "✦ AI Overview" & "AI Overview")
            const headings = document.querySelectorAll('h1, h2, h3, span, div, button, [role="heading"]');
            for (let i = 0; i < headings.length; i++) {
                const el = headings[i];
                const text = el.textContent ? el.textContent.trim() : '';
                if (text === 'AI Overview' || text === '✦ AI Overview' || text.startsWith('AI Overview') || text.startsWith('✦ AI Overview') || (text.includes('AI Overview') && text.length < 30)) {
                    // Find outer search result card container
                    const container = el.closest('[data-hveid], .MjjYud, [data-sokoban-container], .g, [jscontroller], div[data-attrid], div.wDYxhc');
                    if (container && container !== document.body && container !== document.documentElement &&
                        !container.id?.includes('rcnt') && !container.id?.includes('center_col') && !container.id?.includes('rso')) {
                        container.style.setProperty('display', 'none', 'important');
                        container.style.setProperty('height', '0px', 'important');
                        container.style.setProperty('min-height', '0px', 'important');
                        container.style.setProperty('margin', '0px', 'important');
                        container.style.setProperty('padding', '0px', 'important');
                        container.style.setProperty('visibility', 'hidden', 'important');
                        container.style.setProperty('pointer-events', 'none', 'important');
                        if (!container.__fokk_counted) {
                            container.__fokk_counted = true;
                            reportBlocked(1);
                        }
                    }
                }
            }

            // 2. Hide "AI Mode" tab from mobile search header chips
            const tabs = document.querySelectorAll('div[role="tab"], a[role="tab"], div[role="listitem"]');
            for (let i = 0; i < tabs.length; i++) {
                const t = tabs[i];
                const txt = t.textContent ? t.textContent.trim() : '';
                if (txt === 'AI Mode' || txt.includes('AI Mode')) {
                    t.style.setProperty('display', 'none', 'important');
                }
            }

            // 3. Known Google AI Overview selector fallbacks
            const aiBlocks = document.querySelectorAll(`
                #O26Dg,
                div[data-attrid*="ai_overview"],
                div[data-attrid="wa:/description"],
                div[aria-label*="AI Overview"],
                div[data-md*="ai_overview"],
                div[data-attrid="SGE_Search"],
                .g-blk[data-hveid]
            `);
            aiBlocks.forEach(el => {
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('height', '0px', 'important');
                el.style.setProperty('margin', '0px', 'important');
                el.style.setProperty('padding', '0px', 'important');
                el.style.setProperty('visibility', 'hidden', 'important');
                el.style.setProperty('pointer-events', 'none', 'important');
                if (!el.__fokk_counted) {
                    el.__fokk_counted = true;
                    reportBlocked(1);
                }
            });
        }

        if (host.includes('bing.com')) {
            const bingAi = document.querySelectorAll('cib-serp, #b_copilot, div.b_ans:has([aria-label*="Copilot"])');
            bingAi.forEach(el => {
                el.style.setProperty('display', 'none', 'important');
            });
        }
    }

    function scrubSocialAndAi() {
        try {
            scrubInstagramFeed();
            scrubXFeedAndGrok();
            scrubRedditFeedAndAi();
            scrubAiOverviewsAndWidgets();
        } catch (e) {}
    }

    // =========================================================================
    // 8. Universal Media Sniffer: Detects downloadable video & audio streams
    // =========================================================================
    let lastSniffedUrl = '';
    function reportMediaFound(src, title, tag = 'video') {
        if (!src || typeof src !== 'string' || src === lastSniffedUrl) return;
        const low = src.toLowerCase();
        if (low.includes('doubleclick') || low.includes('googleads') || low.includes('adservice') || low.includes('/pagead/')) return;
        lastSniffedUrl = src;
        const cleanTitle = (title || document.title || 'Media').replace(/[\\/:*?"<>|]/g, '').trim();
        try {
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.onMediaSourceFound === 'function') {
                window.FokkShieldBridge.onMediaSourceFound(src, cleanTitle, tag);
            }
        } catch (e) {}
    }

    // Network request hook for MSE and streaming videos (YouTube, X, Reddit, Instagram)
    try {
        const _origFetch = window.fetch;
        window.fetch = function(...args) {
            try {
                const reqUrl = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url);
                if (reqUrl && typeof reqUrl === 'string') {
                    const low = reqUrl.toLowerCase();
                    if (low.includes('.mp4') || low.includes('.webm') || low.includes('.m3u8') ||
                        low.includes('googlevideo.com/videoplayback') || low.includes('video.twimg.com') ||
                        low.includes('v.redd.it') || low.includes('fbcdn.net')) {
                        reportMediaFound(reqUrl, document.title, 'video');
                    }
                }
            } catch (e) {}
            const promise = _origFetch.apply(this, args);
            try {
                const reqUrl = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url);
                const host = window.location.hostname;
                if (reqUrl && typeof reqUrl === 'string') {
                    if ((host.includes('x.com') || host.includes('twitter.com')) && (reqUrl.includes('/graphql/') || reqUrl.includes('Timeline'))) {
                        return promise.then(async response => {
                            try {
                                const clone = response.clone();
                                const text = await clone.text();
                                let data = JSON.parse(text);
                                let cleaned = scrubXGraphQLTimeline(data);
                                if (cleaned) {
                                    reportBlocked(1);
                                    return new Response(JSON.stringify(data), {
                                        status: response.status,
                                        statusText: response.statusText,
                                        headers: response.headers
                                    });
                                }
                            } catch (err) {}
                            return response;
                        }).catch(() => promise);
                    }
                    if (host.includes('reddit.com') && (reqUrl.includes('/svc/shreddit/') || reqUrl.includes('/graphql') || reqUrl.includes('feed'))) {
                        return promise.then(async response => {
                            try {
                                const clone = response.clone();
                                const text = await clone.text();
                                let data = JSON.parse(text);
                                let cleaned = scrubRedditJsonFeed(data);
                                if (cleaned) {
                                    reportBlocked(1);
                                    return new Response(JSON.stringify(data), {
                                        status: response.status,
                                        statusText: response.statusText,
                                        headers: response.headers
                                    });
                                }
                            } catch (err) {}
                            return response;
                        }).catch(() => promise);
                    }
                }
            } catch (e) {}
            return promise;
        };

        const _origXhrOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url, ...rest) {
            try {
                if (url && typeof url === 'string') {
                    const low = url.toLowerCase();
                    if (low.includes('.mp4') || low.includes('.webm') || low.includes('.m3u8') ||
                        low.includes('googlevideo.com/videoplayback') || low.includes('video.twimg.com') ||
                        low.includes('v.redd.it') || low.includes('fbcdn.net')) {
                        reportMediaFound(url, document.title, 'video');
                    }
                }
            } catch (e) {}
            return _origXhrOpen.call(this, method, url, ...rest);
        };
    } catch (e) {}

    function sniffMediaSources() {
        const mediaEls = document.querySelectorAll('video, audio');
        mediaEls.forEach(el => {
            let src = el.currentSrc || el.src;
            if (!src) {
                const source = el.querySelector('source');
                if (source) src = source.src;
            }
            if (src && typeof src === 'string' && (src.startsWith('http://') || src.startsWith('https://'))) {
                reportMediaFound(src, document.title, el.tagName.toLowerCase());
            }
        });
    }

    function hookVideoPlayback(video) {
        if (!video || video.__fokk_hooked) return;
        video.__fokk_hooked = true;

        const updateState = () => {
            const videos = Array.from(document.querySelectorAll('video'));
            const isPlaying = videos.some(v => !v.paused && !v.ended);
            const hasActiveVideo = videos.some(v => v.currentTime > 0 && !v.ended);
            notifyNativePlayback(isPlaying, hasActiveVideo);
            sniffMediaSources();
        };

        video.addEventListener('play', updateState);
        video.addEventListener('playing', updateState);
        video.addEventListener('pause', updateState);
        video.addEventListener('ended', updateState);
        video.addEventListener('emptied', updateState);
        video.addEventListener('loadeddata', sniffMediaSources);

        if (!video.paused && !video.ended) {
            notifyNativePlayback(true, true);
            sniffMediaSources();
        }
    }

    document.querySelectorAll('video').forEach(hookVideoPlayback);
    sniffMediaSources();

    // =========================================================================
    // 9. Sub-millisecond MutationObserver for instant ad skip & cosmetic collapse
    // =========================================================================
    try {
        const observer = new MutationObserver(() => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWallsAndNags();
            scrubSocialAndAi();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
            sniffMediaSources();
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
        scrubSocialAndAi();
        sniffMediaSources();
    }, 350);

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            checkAndSkipWebAd();
            collapseEmptyAdBoxes();
            dismissCookieWallsAndNags();
            scrubSocialAndAi();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
            sniffMediaSources();
        });
    } else {
        checkAndSkipWebAd();
        collapseEmptyAdBoxes();
        dismissCookieWallsAndNags();
        sniffMediaSources();
    }
})();
