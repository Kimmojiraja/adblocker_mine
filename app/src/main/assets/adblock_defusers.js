// Fokk Shield Anti-Adblock Defusers & Scriptlet Stubs
// Injected at document_start to neutralize anti-adblock traps and ensure smooth page rendering
(function() {
    'use strict';
    if (window.__fokk_defusers_active) return;
    window.__fokk_defusers_active = true;

    var noop = function() {};
    var noopObjInstance = new Proxy({}, {
        get: function(target, prop) {
            if (prop === 'then') return undefined;
            return function() { return noopObjInstance; };
        }
    });

    // 1. Google Ads (adsbygoogle) Stub
    try {
        var adsbygoogleObj = [];
        adsbygoogleObj.push = function(args) {
            if (args && typeof args === 'object') {
                try {
                    if (typeof args.onload === 'function') setTimeout(args.onload, 10);
                } catch(e) {}
            }
            return Array.prototype.push.apply(this, arguments);
        };
        adsbygoogleObj.loaded = true;
        Object.defineProperty(window, 'adsbygoogle', {
            get: function() { return adsbygoogleObj; },
            set: function() {},
            configurable: true,
            enumerable: true
        });
    } catch(e) {}

    // 2. Google Publisher Tags (googletag / GPT) Stub
    try {
        var dummyService = {
            addEventListener: noop,
            clear: noop,
            clearCategoryExclusions: noop,
            clearTargeting: noop,
            collapseEmptyDivs: noop,
            disableInitialLoad: noop,
            display: noop,
            enableAsyncRendering: noop,
            enableLazyLoad: noop,
            enableSingleRequest: noop,
            enableSyncRendering: noop,
            enableVideoAds: noop,
            get: function() { return null; },
            getAttributeKeys: function() { return []; },
            getTargeting: function() { return []; },
            getTargetingKeys: function() { return []; },
            refresh: noop,
            set: noop,
            setCategoryExclusion: noop,
            setCentering: noop,
            setCookieOptions: noop,
            setForceSafeFrame: noop,
            setLocation: noop,
            setPublisherProvidedId: noop,
            setRequestNonPersonalizedAds: noop,
            setSafeFrameConfig: noop,
            setTargeting: noop,
            setVideoContent: noop
        };

        var dummySlot = {
            addService: function() { return dummySlot; },
            clearCategoryExclusions: function() { return dummySlot; },
            clearTargeting: function() { return dummySlot; },
            defineSizeMapping: function() { return dummySlot; },
            get: function() { return null; },
            getAdUnitPath: function() { return '/0/dummy'; },
            getAttributeKeys: function() { return []; },
            getCategoryExclusions: function() { return []; },
            getResponseInformation: function() { return null; },
            getSlotElementId: function() { return 'dummy-slot'; },
            getTargeting: function() { return []; },
            getTargetingKeys: function() { return []; },
            set: function() { return dummySlot; },
            setCategoryExclusion: function() { return dummySlot; },
            setClickUrl: function() { return dummySlot; },
            setCollapseEmptyDiv: function() { return dummySlot; },
            setForceSafeFrame: function() { return dummySlot; },
            setSafeFrameConfig: function() { return dummySlot; },
            setTargeting: function() { return dummySlot; }
        };

        var googletagObj = {
            apiReady: true,
            cmd: {
                push: function(fn) {
                    if (typeof fn === 'function') {
                        try { fn(); } catch(err) {}
                    }
                    return 1;
                }
            },
            companionAds: function() { return dummyService; },
            content: function() { return dummyService; },
            defineOutOfPageSlot: function() { return dummySlot; },
            defineSlot: function() { return dummySlot; },
            destroySlots: noop,
            disablePublisherConsole: noop,
            display: noop,
            enableServices: noop,
            getVersion: function() { return 'fokk-gpt-stub-1.0'; },
            openConsole: noop,
            pubads: function() { return dummyService; },
            pubadsReady: true,
            setAdIframeTitle: noop,
            sizeMapping: function() {
                return {
                    addSize: function() { return this; },
                    build: function() { return []; }
                };
            }
        };

        Object.defineProperty(window, 'googletag', {
            get: function() { return googletagObj; },
            set: function() {},
            configurable: true,
            enumerable: true
        });
    } catch(e) {}

    // 3. Prebid.js (pbjs) Stub
    try {
        var pbjsObj = {
            adUnits: [],
            cmd: {
                push: function(fn) {
                    if (typeof fn === 'function') {
                        try { fn(); } catch(err) {}
                    }
                    return 1;
                }
            },
            addAdUnits: noop,
            requestBids: function(cfg) {
                if (cfg && typeof cfg.bidsBackHandler === 'function') {
                    setTimeout(cfg.bidsBackHandler, 10);
                }
            },
            setTargetingForGPTAsync: noop,
            enableAnalytics: noop
        };
        Object.defineProperty(window, 'pbjs', {
            get: function() { return pbjsObj; },
            set: function() {},
            configurable: true,
            enumerable: true
        });
    } catch(e) {}

    // 4. Amazon Apstag (apstag) Stub
    try {
        var apstagObj = {
            init: noop,
            fetchBids: function(cfg, callback) {
                if (typeof callback === 'function') setTimeout(callback, 10);
            },
            setDisplayBids: noop,
            targetingKeys: function() { return []; }
        };
        Object.defineProperty(window, 'apstag', {
            get: function() { return apstagObj; },
            set: function() {},
            configurable: true,
            enumerable: true
        });
    } catch(e) {}

    // 5. Google Analytics (ga, gtag) & Facebook Pixel (fbq) Stubs
    try {
        if (!window.ga) {
            window.ga = function() {
                var args = Array.prototype.slice.call(arguments);
                var last = args[args.length - 1];
                if (typeof last === 'function') {
                    try { last(); } catch(e) {}
                } else if (last && typeof last.hitCallback === 'function') {
                    try { last.hitCallback(); } catch(e) {}
                }
            };
            window.ga.loaded = true;
        }
        if (!window.gtag) {
            window.gtag = noop;
        }
        if (!window.fbq) {
            window.fbq = noop;
            window.fbq.loaded = true;
        }
    } catch(e) {}

    // 6. Anti-Adblock Trap Flags (BlockAdBlock, canRunAds, etc.)
    try {
        window.canRunAds = true;
        window.isAdBlockActive = false;

        var fakeBab = {
            check: function() { return false; },
            on: function(isAdblock, callback) {
                if (!isAdblock && typeof callback === 'function') {
                    setTimeout(callback, 10);
                }
                return this;
            },
            onDetected: function() { return this; },
            onNotDetected: function(cb) {
                if (typeof cb === 'function') setTimeout(cb, 10);
                return this;
            },
            setOption: noop
        };
        window.blockAdBlock = fakeBab;
        window.BlockAdBlock = fakeBab;
        window.snackpack = fakeBab;
    } catch(e) {}

    // 7. News Article Auto-Scroll Restorer & Paywall Curtain Neutralizer
    try {
        var restoreScroll = function() {
            var docEl = document.documentElement;
            var body = document.body;
            if (docEl) {
                var s = window.getComputedStyle(docEl);
                if (s.overflow === 'hidden' || s.overflowY === 'hidden') {
                    docEl.style.setProperty('overflow', 'visible', 'important');
                    docEl.style.setProperty('overflow-y', 'auto', 'important');
                }
            }
            if (body) {
                var bs = window.getComputedStyle(body);
                if (bs.overflow === 'hidden' || bs.overflowY === 'hidden' || bs.position === 'fixed') {
                    body.style.setProperty('overflow', 'visible', 'important');
                    body.style.setProperty('overflow-y', 'auto', 'important');
                    if (bs.position === 'fixed') {
                        body.style.setProperty('position', 'static', 'important');
                    }
                }
            }
        };

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', restoreScroll);
        } else {
            restoreScroll();
        }

        // Periodic check for dynamic anti-adblock scroll locking
        var checkCount = 0;
        var scrollTimer = setInterval(function() {
            restoreScroll();
            checkCount++;
            if (checkCount > 10) clearInterval(scrollTimer);
        }, 800);
    } catch(e) {}

})();
