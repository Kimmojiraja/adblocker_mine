// Fokk Shield optional browser-side cosmetic filters
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    const cosmeticCss = `
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
        #tadsb {
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
    // 5. Media Playback State Reporter & native PiP coordination
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
    // MutationObserver for cosmetic cleanup
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
                while (addedNodes.length) {
                    const node = addedNodes.pop();
                    if (!(node instanceof Element)) continue;
                    if (node.matches('video')) hookVideoPlayback(node);
                    node.querySelectorAll('video').forEach(hookVideoPlayback);
                }
            }, 350);
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
            document.querySelectorAll('video').forEach(hookVideoPlayback);
        });
    } else {
        scrubAiContent();
        document.querySelectorAll('video').forEach(hookVideoPlayback);
        }
})();
