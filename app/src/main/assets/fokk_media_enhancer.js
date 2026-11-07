// Fokk Shield Media Enhancer - Universal YouTube Mobile (m.youtube.com) & Desktop
// Seamless Native Ad Skipping, Promo Banner Collapse & Open-Source SponsorBlock.
(function() {
    'use strict';

    if (window.__fokk_media_enhancer_loaded) return;
    window.__fokk_media_enhancer_loaded = true;

    if (!location.hostname.includes('youtube.com')) return;

    let activeVideoId = null;
    let sponsorSegments = [];
    let isSkipping = false;
    let toastTimeout = null;

    // 1. GPU/Blink Static CSS Ad Cleaner (Hides Bajaj Electronics, Promoted Cards & Banners)
    function ensureAdStyles() {
        if (!document.getElementById('__fokk_yt_cleaner')) {
            const st = document.createElement('style');
            st.id = '__fokk_yt_cleaner';
            st.textContent = `
                ytm-promoted-sparkles-web-renderer,
                ytm-companion-ad-renderer,
                ytm-companion-slot,
                ytm-promoted-video-renderer,
                ytm-compact-promoted-item-renderer,
                .ytm-promoted-sparkles-web-renderer,
                ad-slot-renderer,
                ytm-ad-badge,
                .ytp-ad-overlay-container,
                .ytp-ad-message-container,
                .ytp-ad-action-interstitial,
                .ytp-ad-image-overlay,
                #player-ads,
                #masthead-ad,
                ytd-promoted-sparkles-web-renderer,
                ytd-companion-slot-renderer,
                ytd-promoted-video-renderer,
                ytd-banner-promo-renderer,
                ytd-ad-slot-renderer,
                ytd-in-feed-ad-layout-renderer {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                    min-height: 0 !important;
                    max-height: 0 !important;
                    overflow: hidden !important;
                    pointer-events: none !important;
                }
            `;
            (document.head || document.documentElement).appendChild(st);
        }
    }

    // 2. Subtle On-Screen SponsorBlock Toast
    function showSponsorToast(category, durationSec) {
        let toast = document.getElementById('__fokk_sb_toast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = '__fokk_sb_toast';
            toast.style.cssText = 'position:fixed;bottom:72px;left:20px;z-index:999999;background:rgba(18,20,26,0.92);color:#38bdf8;padding:8px 14px;border-radius:10px;font-size:12px;font-weight:600;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;box-shadow:0 4px 16px rgba(0,0,0,0.5);border:1px solid rgba(56,189,248,0.3);backdrop-filter:blur(8px);pointer-events:none;transition:opacity 0.3s ease;opacity:0;';
            document.documentElement.appendChild(toast);
        }
        toast.textContent = '⚡ Skipped ' + (category || 'sponsor') + ' (' + Math.round(durationSec) + 's saved)';
        toast.style.opacity = '1';
        clearTimeout(toastTimeout);
        toastTimeout = setTimeout(function() {
            if (toast) toast.style.opacity = '0';
        }, 2500);
    }

    // 3. Fetch Open-Source SponsorBlock Segments
    function fetchSponsorSegments(videoId) {
        if (!videoId || videoId === activeVideoId) return;
        activeVideoId = videoId;
        sponsorSegments = [];

        const categories = encodeURIComponent('["sponsor","intro","outro","selfpromo","interaction"]');
        const url = 'https://sponsor.ajay.app/api/skipSegments?videoID=' + encodeURIComponent(videoId) + '&categories=' + categories;

        fetch(url, { method: 'GET', credentials: 'omit', mode: 'cors' })
            .then(function(res) {
                if (!res.ok) return [];
                return res.json();
            })
            .then(function(data) {
                if (Array.isArray(data)) {
                    sponsorSegments = data.map(function(item) {
                        return {
                            start: item.segment[0],
                            end: item.segment[1],
                            category: item.category,
                            skipped: false
                        };
                    });
                }
            })
            .catch(function() {
                sponsorSegments = [];
            });
    }

    // 4. Extract Video ID from current URL
    function updateActiveVideo() {
        const urlParams = new URLSearchParams(window.location.search);
        const vid = urlParams.get('v');
        if (vid) {
            fetchSponsorSegments(vid);
        } else if (window.location.pathname.startsWith('/shorts/')) {
            const shortId = window.location.pathname.split('/shorts/')[1]?.split('?')[0];
            if (shortId) fetchSponsorSegments(shortId);
        }
    }

    // 5. Resolve Player Container (Mobile m.youtube.com & Desktop)
    function getPlayer() {
        return document.getElementById('movie_player') ||
            document.querySelector('.html5-video-player') ||
            document.querySelector('#player-container-id') ||
            document.querySelector('ytm-player') ||
            document.getElementById('player');
    }

    function isAdShowing() {
        const player = getPlayer();
        if (player && player.classList && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
            return true;
        }
        return !!document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, ytm-ad-badge, [class*="ad-showing"]');
    }

    // 6. Universal Ad Skipper: Handles Both Pre-Roll Video Ads & Banner Overlays
    function handleAdSlots() {
        ensureAdStyles();

        // A. Auto-click native skip button immediately if exposed (Mobile + Desktop)
        const skipBtn = document.querySelector(
            '.ytp-ad-skip-button, .ytp-ad-skip-button-modern, button.ytp-ad-skip-button-text, ' +
            '.ytp-ad-skip-button-slot button, .videoAdUiSkipButton, [class*="skip-button"], ' +
            'button[class*="skip"], [aria-label*="Skip" i], [aria-label*="skip ad" i], .ytp-ad-overlay-close-button'
        );
        if (skipBtn) {
            try { skipBtn.click(); } catch(e) {}
            return;
        }

        const player = getPlayer();
        if (player && typeof player.skipAd === 'function') {
            try { player.skipAd(); } catch(e) {}
        }
    }

    // 7. Video Playback Monitor: SponsorBlock Skipper
    function checkSponsorSegments() {
        if (sponsorSegments.length === 0 || isSkipping) return;
        const video = document.querySelector('video');
        if (!video || video.paused || video.ended) return;

        const current = video.currentTime;
        for (let i = 0; i < sponsorSegments.length; i++) {
            const seg = sponsorSegments[i];
            if (!seg.skipped && current >= seg.start && current < seg.end - 0.5) {
                isSkipping = true;
                seg.skipped = true;
                const duration = seg.end - current;
                video.currentTime = seg.end + 0.1;
                showSponsorToast(seg.category, duration);
                setTimeout(function() { isSkipping = false; }, 300);
                break;
            }
        }
    }

    // Init & Listeners
    ensureAdStyles();
    updateActiveVideo();
    window.addEventListener('yt-navigate-finish', updateActiveVideo);
    window.addEventListener('popstate', updateActiveVideo);

    function attachVideoMonitor() {
        const video = document.querySelector('video');
        if (video && !video.__fokk_attached) {
            video.__fokk_attached = true;
            video.addEventListener('timeupdate', function() {
                checkSponsorSegments();
                handleAdSlots();
            });
        }
    }

    attachVideoMonitor();
    const obs = new MutationObserver(function() {
        attachVideoMonitor();
        handleAdSlots();
    });
    obs.observe(document.documentElement, { childList: true, subtree: true });
})();
