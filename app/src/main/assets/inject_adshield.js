// AdShield Injected Content Blocker & YouTube Video Ad Neutralizer
(function() {
    'use strict';
    if (window.__adshield_loaded) return;
    window.__adshield_loaded = true;

    // 1. YouTube Player Ad Config Stripper (Safe array clearing)
    function sanitizePlayerResponse(resp) {
        if (resp && typeof resp === 'object') {
            if (resp.adPlacements) resp.adPlacements = [];
            if (resp.playerAds) resp.playerAds = [];
            if (resp.adSlots) resp.adSlots = [];
            if (resp.playbackTracking) {
                delete resp.playbackTracking.videostatsPlaybackUrl;
                delete resp.playbackTracking.videostatsDelayplayUrl;
            }
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

        // uBlock Origin / Brave Engine: Dynamic Fetch Interceptor for /youtubei/v1/player
        // Eliminates both pre-roll and mid-roll ad breaks before playback begins
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

        // uBlock Origin / Brave Engine: Dynamic XHR Interceptor
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

    // 2. Video Ad Fast-Forward & Skip
    function checkAndSkipWebAd() {
        const player = document.querySelector('.html5-video-player');
        if (!player) return;

        const isAdShowing = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
        if (isAdShowing) {
            const video = player.querySelector('video');
            if (video) {
                video.muted = true;
                if (video.duration && !isNaN(video.duration) && video.duration < 120) {
                    video.currentTime = video.duration;
                }
            }

            const skipBtn = document.querySelector(
                '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .videoAdUiSkipButton, button.ytp-ad-skip-button-text'
            );
            if (skipBtn && typeof skipBtn.click === 'function') {
                skipBtn.click();
            }
        }
    }

    // 3. Inject CSS to hide banners without breaking comments or video layout
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
        .ad-showing .ytp-ad-player-overlay,
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

    // Run ad killer periodically (every 500ms for responsiveness)
    setInterval(checkAndSkipWebAd, 500);
})();
