// AdShield Injected Content Blocker & YouTube Video Ad Neutralizer
(function() {
    'use strict';
    if (window.__adshield_loaded) return;
    window.__adshield_loaded = true;

    console.log('[AdShield] Shield initialized for:', window.location.hostname);

    // 1. YouTube Player Ad Config Stripper
    function interceptYouTubeConfigs() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // Strip adPlacements from ytInitialPlayerResponse
        const originalYtInitialPlayerResponse = window.ytInitialPlayerResponse;
        if (originalYtInitialPlayerResponse && originalYtInitialPlayerResponse.adPlacements) {
            delete originalYtInitialPlayerResponse.adPlacements;
            delete originalYtInitialPlayerResponse.playerAds;
        }

        // Intercept future assignments
        try {
            let playerResponseVal = window.ytInitialPlayerResponse;
            Object.defineProperty(window, 'ytInitialPlayerResponse', {
                get() { return playerResponseVal; },
                set(val) {
                    if (val && typeof val === 'object') {
                        delete val.adPlacements;
                        delete val.playerAds;
                        delete val.adSlots;
                    }
                    playerResponseVal = val;
                },
                configurable: true
            });
        } catch (e) {}
    }

    interceptYouTubeConfigs();

    // 2. Video Ad Fast-Forwarder & Auto-Skipper
    function monitorAndKillVideoAds() {
        const videos = document.querySelectorAll('video');
        videos.forEach(video => {
            // Check if player container has ad indicators
            const player = video.closest('.html5-video-player, .video-player');
            const isAdShowing = player && (
                player.classList.contains('ad-showing') ||
                player.classList.contains('ad-interrupting') ||
                document.querySelector('.ytp-ad-player-overlay, .ytp-ad-module, .ad-container') !== null
            );

            if (isAdShowing && video.duration > 0 && !isNaN(video.duration)) {
                // If it's an ad video, instantly jump to the end and click skip
                video.muted = true;
                video.playbackRate = 16.0;
                video.currentTime = video.duration;
            }
        });

        // Click any skip buttons
        const skipButtons = document.querySelectorAll(
            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, ' +
            '[class*="skip-button"], button[id*="skip"], .ytp-skip-ad-button'
        );
        skipButtons.forEach(btn => {
            if (btn && typeof btn.click === 'function') {
                btn.click();
            }
        });

        // Close ad overlays
        const overlayCloseButtons = document.querySelectorAll(
            '.ytp-ad-overlay-close-button, .close-ad-button'
        );
        overlayCloseButtons.forEach(btn => {
            if (btn && typeof btn.click === 'function') {
                btn.click();
            }
        });
    }

    // 3. Inject Universal Cosmetic Hiding CSS
    const cosmeticCss = `
        /* YouTube Web Ad Selectors */
        .ytp-ad-module,
        .ytp-ad-overlay-container,
        .ytp-ad-message-container,
        ytd-promoted-video-renderer,
        ytd-compact-promoted-video-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-display-ad-renderer,
        ytd-ad-slot-renderer,
        ytd-statement-banner-renderer,
        ytd-in-feed-ad-layout-renderer,
        ytm-promoted-sparkles-web-renderer,
        ytm-companion-ad-renderer,
        #player-ads,
        #masthead-ad,
        .sparkles-light-cta,
        .ytm-ad-badge,
        .ad-showing .ytp-ad-player-overlay,

        /* Instagram Sponsored Selectors */
        article:has(a[href*="/ads/"]),
        article:has(span[dir="auto"]:contains("Sponsored")),

        /* Twitter / X Promoted Tweets */
        [data-testid="placementTracking"],
        [data-testid="trend"]:has(span:contains("Promoted")),

        /* Reddit Promoted Posts */
        shreddit-post[is-promoted="true"],
        .promotedlink,
        .promoted,

        /* Generic Banner & Floating Ads */
        .ad-container,
        .banner-ad,
        .ad-banner,
        .dfp-ad,
        .adsbygoogle,
        [id*="google_ads_iframe"] {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            width: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
    `;

    const styleEl = document.createElement('style');
    styleEl.type = 'text/css';
    styleEl.appendChild(document.createTextNode(cosmeticCss));
    (document.head || document.documentElement).appendChild(styleEl);

    // 4. Background Playback Enabler
    // Prevents YouTube / websites from pausing video when tab loses visibility
    try {
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        window.addEventListener('visibilitychange', (e) => {
            e.stopImmediatePropagation();
        }, true);
    } catch (e) {}

    // Run ad killer periodically and on mutations
    setInterval(monitorAndKillVideoAds, 250);

    const observer = new MutationObserver(() => {
        monitorAndKillVideoAds();
    });

    observer.observe(document.documentElement || document.body, {
        childList: true,
        subtree: true
    });
})();
