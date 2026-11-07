// Fokk Shield Media Enhancer - Scoped Strictly to YouTube
// Provides Native Ad Skipping & Crowdsourced SponsorBlock Segment Skipping with Zero Buffering Delay.
(function() {
    'use strict';

    if (window.__fokk_media_enhancer_loaded) return;
    window.__fokk_media_enhancer_loaded = true;

    if (!location.hostname.includes('youtube.com')) return;

    let activeVideoId = null;
    let sponsorSegments = [];
    let isSkipping = false;
    let toastTimeout = null;

    // 1. Subtle On-Screen SponsorBlock Toast
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

    // 2. Fetch Open-Source SponsorBlock Segments
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

    // 3. Extract Video ID from current URL
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

    // 4. Native Ad Skipper: Clean, Event-Driven Button Trigger
    function handleAdSlots() {
        const player = document.getElementById('movie_player');
        if (!player) return;

        // Auto-click native YouTube skip buttons as soon as exposed
        const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, [id^="skip-button:"], .ytp-ad-overlay-close-button');
        if (skipBtn) {
            skipBtn.click();
            return;
        }

        // Call official player skipAd API cleanly if player is showing an ad
        if (player.classList && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) {
            if (typeof player.skipAd === 'function') {
                try { player.skipAd(); } catch(e) {}
            }
        }
    }

    // 5. Video Playback Monitor: SponsorBlock Skipper
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

    // Attach listeners
    updateActiveVideo();
    window.addEventListener('yt-navigate-finish', updateActiveVideo);
    window.addEventListener('popstate', updateActiveVideo);

    // Continuous event loop on active video
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
