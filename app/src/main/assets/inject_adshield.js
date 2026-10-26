// AdShield Injected Content Blocker & YouTube Video Ad Neutralizer
(function() {
    'use strict';
    if (window.__adshield_loaded) return;
    window.__adshield_loaded = true;

    // 1. YouTube Player Ad Config Stripper
    function interceptYouTubeConfigs() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // Strip adPlacements from ytInitialPlayerResponse
        const originalYtInitialPlayerResponse = window.ytInitialPlayerResponse;
        if (originalYtInitialPlayerResponse && originalYtInitialPlayerResponse.adPlacements) {
            delete originalYtInitialPlayerResponse.adPlacements;
            delete originalYtInitialPlayerResponse.playerAds;
        }

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

    // 2. Video Ad Fast-Forwarder & Auto-Skipper (Strictly for real ads only)
    function monitorAndKillVideoAds() {
        const videos = document.querySelectorAll('video');
        videos.forEach(video => {
            const player = video.closest('.html5-video-player, .video-player');
            // ONLY treat as ad if YouTube explicitly flags it with 'ad-showing' or 'ad-interrupting'
            const isRealAdShowing = player && (
                player.classList.contains('ad-showing') ||
                player.classList.contains('ad-interrupting')
            );

            if (isRealAdShowing && video.duration > 0 && !isNaN(video.duration)) {
                video.muted = true;
                video.playbackRate = 16.0;
                video.currentTime = video.duration;
            }
        });

        // Click skip button when clickable
        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
        if (skipBtn && typeof skipBtn.click === 'function') {
            skipBtn.click();
        }
    }

    // 3. Inject CSS to hide banners without breaking layout
    const cosmeticCss = `
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
        shreddit-post[is-promoted="true"],
        .promotedlink,
        .promoted {
            display: none !important;
        }
    `;

    const styleEl = document.createElement('style');
    styleEl.type = 'text/css';
    styleEl.appendChild(document.createTextNode(cosmeticCss));
    (document.head || document.documentElement).appendChild(styleEl);

    // 4. Background Playback Enabler
    try {
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        window.addEventListener('visibilitychange', (e) => {
            e.stopImmediatePropagation();
        }, true);
    } catch (e) {}

    // Check periodically with a moderate interval (500ms) for high performance
    setInterval(monitorAndKillVideoAds, 500);
})();
