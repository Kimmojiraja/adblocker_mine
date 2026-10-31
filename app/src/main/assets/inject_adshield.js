// Fokk Shield optional browser-side cosmetic filters
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    const cosmeticCss = `
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
        #player-ads,
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .video-ads,
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
    // YouTube Web Video Ad Auto-Skipper (Zero-Stall, Audio-Safe)
    // =========================================================================
    let adWasMutedByShield = false;

    function runYouTubeAdSkipper() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // 1. Trigger YouTube player API skip if available
        try {
            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (player && typeof player.skipAd === 'function') {
                player.skipAd();
            }
        } catch (e) {}

        // 2. Click any visible skip button
        const skipButtons = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.ytp-skip-ad-button',
            '.videoAdUiSkipButton',
            'button.ytp-ad-skip-button',
            '.ytp-ad-overlay-close-button'
        ];
        for (let i = 0; i < skipButtons.length; i++) {
            const btn = document.querySelector(skipButtons[i]);
            if (btn && typeof btn.click === 'function') {
                btn.click();
                break;
            }
        }

        // 3. Fast-forward video ad without black-screen pause stall
        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
        const isAdShowing = document.querySelector('.ad-showing, .ad-interrupting') || (player && player.classList.contains('ad-showing'));
        const video = document.querySelector('video');

        if (isAdShowing) {
            if (video) {
                // Video ads are short clips (<= 75 seconds). NEVER seek if duration > 75s
                // because that means it is the main user video!
                if (!isNaN(video.duration) && video.duration > 0 && video.duration <= 75 && !video.ended) {
                    if (!video.muted) {
                        video.muted = true;
                        adWasMutedByShield = true;
                    }
                    video.playbackRate = 16.0;
                    video.currentTime = Math.max(0, video.duration - 0.1);
                }
                if (video.paused) {
                    video.play().catch(function() {});
                }
            }
        } else {
            // Main video is active: restore normal audio and normal 1.0x playback speed!
            if (video) {
                if (video.playbackRate > 1.0) {
                    video.playbackRate = 1.0;
                }
                if (adWasMutedByShield || (video.muted && video.currentTime > 0.5)) {
                    video.muted = false;
                    adWasMutedByShield = false;
                }
            }
        }
    }

    if (window.location.hostname.includes('youtube.com')) {
        setInterval(runYouTubeAdSkipper, 300);
    }

    // =========================================================================
    // Anti-App-Nag: In-page app upsell & "Open App" header scrubber
    // =========================================================================
    function scrubAppPromos() {
        const host = window.location.hostname;
        const isAppNagHost = host.includes('youtube.com') || host.includes('spotify.com') ||
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
            // Scrub sponsored posts & suggestions from Instagram feed
            document.querySelectorAll('article').forEach(function(article) {
                if (article.dataset.fokkChecked) return;
                const hasAdIndicator = article.querySelector('a[href*="/about/ads"], svg[aria-label="Sponsored"]') ||
                    Array.from(article.querySelectorAll('span, a')).some(function(s) {
                        const t = (s.innerText || s.textContent || '').trim().toLowerCase();
                        return t === 'sponsored' || t === 'suggested for you' || t === 'suggested post';
                    });
                if (hasAdIndicator) {
                    article.dataset.fokkChecked = 'true';
                    article.style.setProperty('display', 'none', 'important');
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
        const needsObserver = host.includes('youtube.com') || host.includes('spotify.com') ||
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
            runYouTubeAdSkipper();
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        scrubAiContent();
        scrubAppPromos();
        runYouTubeAdSkipper();
        document.querySelectorAll('video').forEach(hookVideoPlayback);
    }
})();
