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

        /* Anti-App-Nag: Open in App / Get App Banners & Prompts */
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
        ytm-mealbar-promo-renderer,
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
    // YouTube Web Video Ad Auto-Skipper
    // =========================================================================
    function runYouTubeAdSkipper() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // 1. Auto-click any skip ad button
        const skipButtons = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.ytp-skip-ad-button',
            '.videoAdUiSkipButton',
            'button.ytp-ad-skip-button'
        ];
        for (let i = 0; i < skipButtons.length; i++) {
            const btn = document.querySelector(skipButtons[i]);
            if (btn && typeof btn.click === 'function') {
                btn.click();
                break;
            }
        }

        // 2. Fast-forward preroll or midroll video ad
        const adContainer = document.querySelector('.ad-showing, .ad-interrupting');
        if (adContainer) {
            const video = document.querySelector('video');
            if (video && !isNaN(video.duration) && video.duration > 0 && !video.ended) {
                video.muted = true;
                video.playbackRate = 16.0;
                video.currentTime = video.duration;
            }
        }
    }

    if (window.location.hostname.includes('youtube.com')) {
        setInterval(runYouTubeAdSkipper, 350);
    }

    // =========================================================================
    // Anti-App-Nag: In-page app upsell scrubber
    // =========================================================================
    function scrubAppPromos() {
        const host = window.location.hostname;
        if (host.includes('spotify.com')) {
            document.querySelectorAll('div[data-testid="upsell-banner"], div[class*="MobileAppBanner"], a[href*="spotify.com/download"]').forEach(el => {
                try { el.remove(); } catch (e) {}
            });
        }
        if (host.includes('reddit.com')) {
            document.querySelectorAll('xpromo-app-selector, shreddit-async-loader[bundlename="bottom_sheet"]').forEach(el => {
                try { el.remove(); } catch (e) {}
            });
            if (document.documentElement) document.documentElement.style.overflow = 'auto';
            if (document.body) document.body.style.overflow = 'auto';
        }
        if (host.includes('youtube.com')) {
            document.querySelectorAll('ytm-mealbar-promo-renderer').forEach(el => {
                try { el.remove(); } catch (e) {}
            });
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
            document.querySelectorAll('[data-fokk-ai-hidden]').forEach(el => {
                el.style.setProperty('display', '', '');
                el.style.setProperty('height', '', '');
                el.style.setProperty('min-height', '', '');
                el.style.setProperty('margin', '', '');
                el.style.setProperty('padding', '', '');
                el.style.setProperty('visibility', '', '');
                el.style.setProperty('pointer-events', '', '');
            });
            document.querySelectorAll('[data-fokk-aimode-hidden]').forEach(el => {
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

        document.querySelectorAll(selector).forEach(el => {
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
        let pendingSweep = false;
        const addedNodes = [];
        const observer = new MutationObserver(mutations => {
            mutations.forEach(mutation => mutation.addedNodes.forEach(node => addedNodes.push(node)));
            if (pendingSweep) return;
            pendingSweep = true;
            setTimeout(() => {
                pendingSweep = false;
                scrubAiContent();
                scrubAppPromos();
                runYouTubeAdSkipper();
                while (addedNodes.length) {
                    const node = addedNodes.pop();
                    if (!(node instanceof Element)) continue;
                    if (node.matches('video')) hookVideoPlayback(node);
                    node.querySelectorAll('video').forEach(hookVideoPlayback);
                }
            }, 300);
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    } catch (e) {}

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
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
