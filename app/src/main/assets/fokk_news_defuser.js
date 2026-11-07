// Fokk Shield News Defuser - Scoped Strictly to News & Publisher Sites
// Unlocks scroll locks and dismisses CMP modals without breaking article layouts or modifying core prototypes.
(function() {
    'use strict';

    if (window.__fokk_news_defuser_loaded) return;
    window.__fokk_news_defuser_loaded = true;

    const NEWS_HOST_PATTERNS = [
        'thesun.co.uk', 'the-sun.com', 'thescottishsun.co.uk',
        'foxnews.com', 'theguardian.com', 'forbes.com', 'independent.co.uk',
        'mirror.co.uk', 'express.co.uk', 'standard.co.uk', 'dailymail.co.uk',
        'telegraph.co.uk', 'bloomberg.com', 'reuters.com', 'nytimes.com',
        'washingtonpost.com', 'usatoday.com', 'latimes.com',
        'onmanorama.com', 'indiatimes.com', 'ndtv.com'
    ];

    const host = (location.hostname || '').toLowerCase();
    const isTargetNews = NEWS_HOST_PATTERNS.some(function(p) { return host.includes(p); });
    if (!isTargetNews) return;

    // 1. Scoped Scroll Lock Restorer
    function unlockScroll() {
        const lockClasses = [
            'has-scroll-lock', 'no-scroll', 'modal-open',
            'fc-dialog-open', 'sp-message-open', 'gu-overflow-hidden',
            'didomi-popup-open', 'didomi-lock'
        ];
        lockClasses.forEach(function(cls) {
            if (document.documentElement && document.documentElement.classList.contains(cls)) {
                document.documentElement.classList.remove(cls);
            }
            if (document.body && document.body.classList.contains(cls)) {
                document.body.classList.remove(cls);
            }
        });

        // Ensure dynamic scroll enforcer is present
        if (!document.getElementById('__fokk_scroll_unlocker')) {
            const st = document.createElement('style');
            st.id = '__fokk_scroll_unlocker';
            st.textContent = 'html, body { overflow-y: auto !important; }';
            (document.head || document.documentElement).appendChild(st);
        }
    }

    // 2. CMP Dismissal
    function dismissCmpModals() {
        // Sourcepoint CMP
        const spClose = document.querySelector('.sp_message_container button[aria-label*="Close" i], .sp_message_container button[title*="Close" i], .sp_message_container .message-component button:last-child');
        if (spClose) {
            try { spClose.click(); } catch(e) {}
        }

        // Google Funding Choices
        const fcClose = document.querySelector('.fc-ab-root button.fc-cta-consent, .fc-ab-root .fc-close');
        if (fcClose) {
            try { fcClose.click(); } catch(e) {}
        }

        // Didomi
        const didomiBtn = document.querySelector('#didomi-notice-agree-button, #didomi-host button:first-child');
        if (didomiBtn) {
            try { didomiBtn.click(); } catch(e) {}
        }

        // Hide overlay backdrops that cover the entire viewport
        const badOverlays = document.querySelectorAll('.fc-ab-root, .sp_message_container, #didomi-host, [id^="sp_message_container"]');
        for (let i = 0; i < badOverlays.length; i++) {
            badOverlays[i].style.setProperty('display', 'none', 'important');
            badOverlays[i].style.setProperty('pointer-events', 'none', 'important');
        }

        unlockScroll();
    }

    // Run on ready and watch mutations smoothly (debounced 300ms)
    let scheduled = false;
    function triggerDismiss() {
        if (scheduled) return;
        scheduled = true;
        setTimeout(function() {
            scheduled = false;
            dismissCmpModals();
        }, 300);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', triggerDismiss);
    } else {
        triggerDismiss();
    }

    const obs = new MutationObserver(function() {
        triggerDismiss();
    });
    obs.observe(document.documentElement, { childList: true, attributes: true, attributeFilter: ['class'] });
})();
