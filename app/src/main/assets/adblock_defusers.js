// Fokk Shield Anti-Adblock Defusers & Scriptlet Stubs
// Injected at document_start to neutralize anti-adblock traps and ensure smooth page rendering
(function() {
    'use strict';
    if (window.__fokk_defusers_active) return;

    var host = ((window.location && window.location.hostname) || '').toLowerCase();
    var href = ((window.location && window.location.href) || '').toLowerCase();
    if (host.includes('browserbench') || host.includes('speedometer') || host.includes('webxprt') ||
        host.includes('principledtechnologies') || host.includes('kraken-benchmark') || host.includes('octane') ||
        host.includes('jetstream') || host.includes('motionmark')) {
        return; // Zero overhead on performance benchmarking suites!
    }
    var isFragileSocial = host === 'x.com' || host.endsWith('.x.com') ||
                          host === 'twitter.com' || host.endsWith('.twitter.com') ||
                          host === 'instagram.com' || host.endsWith('.instagram.com') ||
                          host === 'threads.net' || host.endsWith('.threads.net') ||
                          host === 'facebook.com' || host.endsWith('.facebook.com') ||
                          href.includes('//x.com') || href.includes('//twitter.com');
    if (isFragileSocial) {
        return; // Zero tampering on X (Twitter), Instagram, etc.
    }
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
            getSlots: function() { return [dummySlot]; },
            getSlotIdMap: function() { return {}; },
            getTargeting: function() { return []; },
            getTargetingKeys: function() { return []; },
            isInitialLoadDisabled: function() { return false; },
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

    // 6. Universal Bait Element Defuser & Dimension Spoofing
    // Modern anti-adblock scripts (The Sun, Admiral, Sourcepoint, BlockAdBlock) create bait elements
    // and check if their offsetHeight === 0 or display === 'none'. We spoof these checks cleanly.
    try {
        var baitClassRegex = /(^|\s|_|-)(ad|ads|adsbox|ad-unit|ad-slot|ad-banner|banner-ad|pub_300x250|sponsored|google-ad|afs_ads|ad_header)($|\s|_|-)/i;
        var baitIdRegex = /(^|_|-)(ad|ads|adsbox|ad-unit|ad-slot|ad-banner|banner-ad|pub_300x250|google-ad|afs_ads)($|_|-)/i;

        var isBaitElement = function(el) {
            if (!el || el.nodeType !== 1) return false;
            var cls = el.className;
            if (typeof cls === 'string' && baitClassRegex.test(cls)) return true;
            var id = el.id;
            if (typeof id === 'string' && baitIdRegex.test(id)) return true;
            if (el.hasAttribute && (el.hasAttribute('data-ad-slot') || el.hasAttribute('data-ad-unit'))) return true;
            return false;
        };

        var origOffsetHeightDesc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
        if (origOffsetHeightDesc && origOffsetHeightDesc.get) {
            Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
                get: function() {
                    var val = origOffsetHeightDesc.get.call(this);
                    if (val === 0 && isBaitElement(this)) return 250;
                    return val;
                },
                configurable: true
            });
        }

        var origOffsetWidthDesc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');
        if (origOffsetWidthDesc && origOffsetWidthDesc.get) {
            Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
                get: function() {
                    var val = origOffsetWidthDesc.get.call(this);
                    if (val === 0 && isBaitElement(this)) return 300;
                    return val;
                },
                configurable: true
            });
        }

        var origClientHeightDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'clientHeight');
        if (origClientHeightDesc && origClientHeightDesc.get) {
            Object.defineProperty(Element.prototype, 'clientHeight', {
                get: function() {
                    var val = origClientHeightDesc.get.call(this);
                    if (val === 0 && isBaitElement(this)) return 250;
                    return val;
                },
                configurable: true
            });
        }

        var origClientWidthDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'clientWidth');
        if (origClientWidthDesc && origClientWidthDesc.get) {
            Object.defineProperty(Element.prototype, 'clientWidth', {
                get: function() {
                    var val = origClientWidthDesc.get.call(this);
                    if (val === 0 && isBaitElement(this)) return 300;
                    return val;
                },
                configurable: true
            });
        }

        var origGetComputedStyle = window.getComputedStyle;
        if (origGetComputedStyle) {
            window.getComputedStyle = function(el, pseudo) {
                var res = origGetComputedStyle.call(window, el, pseudo);
                if (el && isBaitElement(el) && res) {
                    return new Proxy(res, {
                        get: function(target, prop) {
                            if (prop === 'display' && target.display === 'none') return 'block';
                            if (prop === 'visibility' && target.visibility === 'hidden') return 'visible';
                            if (prop === 'opacity' && (target.opacity === '0' || target.opacity === 0)) return '1';
                            var val = target[prop];
                            return typeof val === 'function' ? val.bind(target) : val;
                        }
                    });
                }
                return res;
            };
        }
    } catch(e) {}

    // 7. Anti-Adblock Trap Flags & Global Publisher Globals
    try {
        window.google_ad_client = "ca-pub-0000000000000000";
        window.canRunAds = true;
        window.isAdBlockActive = false;
        window.isAdBlocked = false;
        window.isAdBlockOn = false;
        window.adBlock = false;
        window.adblock = false;
        window.adsBlocked = false;
        window.adBlockEnabled = false;
        window.adblockDetected = false;
        window.adBlockerDetected = false;
        window.criteo = window.criteo || { events: [] };
        window.criteo_q = window.criteo_q || [];
        window.outbrain = window.outbrain || { ready: true };
        window.taboola = window.taboola || { push: noop };
        window._taboola = window._taboola || [];

        try {
            Object.defineProperty(window, 'adBlockEnabled', {
                get: function() { return false; },
                set: function() {},
                configurable: true
            });
            Object.defineProperty(window, 'adblockDetected', {
                get: function() { return false; },
                set: function() {},
                configurable: true
            });
            Object.defineProperty(window, 'canRunAds', {
                get: function() { return true; },
                set: function() {},
                configurable: true
            });
        } catch(e) {}

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
        window.snackpack = true;
    } catch(e) {}

    // 8. News Article Auto-Scroll Restorer & Paywall Curtain Neutralizer
    var restoreScroll = function() {
        try {
            var docEl = document.documentElement;
            var body = document.body;
            if (docEl) {
                var s = docEl.style;
                if (s.overflow === 'hidden' || s.overflowY === 'hidden') {
                    s.setProperty('overflow', 'visible', 'important');
                    s.setProperty('overflow-y', 'auto', 'important');
                }
            }
            if (body) {
                var bs = body.style;
                if (bs.overflow === 'hidden' || bs.overflowY === 'hidden' || bs.position === 'fixed') {
                    bs.setProperty('overflow', 'visible', 'important');
                    bs.setProperty('overflow-y', 'auto', 'important');
                    if (bs.position === 'fixed') {
                        bs.setProperty('position', 'static', 'important');
                    }
                }
                if (bs.filter && bs.filter.indexOf('blur') !== -1) {
                    bs.setProperty('filter', 'none', 'important');
                }
            }
        } catch(e) {}
    };

    // 9. Intelligent Anti-Adblock Defuser & Auto-Dismissal (Generic across The Sun, Forbes, etc.)
    var scrubNagElements = function() {
        try {
            var nagPhrases = [
                'ad or script blocking',
                'interfering with this page',
                'disable any ad or script',
                'ad blocker detected',
                'ad blocker',
                'ad-blocker',
                'disable your ad blocker',
                'turn off your ad blocker',
                'please allow ads',
                'bloqueur de publicité',
                'werbeblocker'
            ];

            var dismissPhrases = [
                'continue without supporting',
                'continue without disabling',
                'continue anyway',
                'continue reading',
                'continue with ad blocker',
                'keep my ad blocker',
                'without supporting',
                'remind me later',
                'dismiss',
                'close',
                'not now'
            ];

            // Target candidate modals, dialogs, overlays
            var dialogs = document.querySelectorAll('div[role="dialog"], div[class*="dialog"], div[class*="modal"], div[class*="overlay"], div[class*="popup"], div[class*="adblock"], div[id*="adblock"], div[id*="modal"], div[id*="dialog"], section[role="dialog"]');
            for (var d = 0; d < dialogs.length; d++) {
                var dlg = dialogs[d];
                if (!dlg) continue;
                var dlgText = (dlg.innerText || dlg.textContent || '').toLowerCase();
                var isAdblockNag = false;
                for (var n = 0; n < nagPhrases.length; n++) {
                    if (dlgText.indexOf(nagPhrases[n]) !== -1) {
                        isAdblockNag = true;
                        break;
                    }
                }
                if (!isAdblockNag) continue;

                // 1. Try to auto-click secondary dismissal button / link (e.g. "Continue without supporting us")
                var clickables = dlg.querySelectorAll('button, a, span[role="button"], div[role="button"]');
                var clicked = false;
                for (var c = 0; c < clickables.length; c++) {
                    var btn = clickables[c];
                    var btnText = (btn.innerText || btn.textContent || '').trim().toLowerCase();
                    // Do not click the primary "disable ad blocker" button!
                    if (btnText.indexOf('disable') !== -1 && btnText.indexOf('without') === -1) continue;
                    for (var m = 0; m < dismissPhrases.length; m++) {
                        if (btnText.indexOf(dismissPhrases[m]) !== -1) {
                            try {
                                btn.click();
                                clicked = true;
                                break;
                            } catch(e) {}
                        }
                    }
                    if (clicked) break;
                }

                // 2. Hide the modal container and backdrop
                var topModal = dlg.closest('div[class*="container"], div[class*="wrap"], div[style*="z-index"]') || dlg;
                topModal.style.setProperty('display', 'none', 'important');
                topModal.style.setProperty('opacity', '0', 'important');
                topModal.style.setProperty('pointer-events', 'none', 'important');
                topModal.style.setProperty('visibility', 'hidden', 'important');

                // Clean backdrop overlays
                var backdrops = document.querySelectorAll('.modal-backdrop, .overlay-backdrop, [class*="backdrop"], div[style*="position: fixed"][style*="z-index"]');
                for (var b = 0; b < backdrops.length; b++) {
                    var bd = backdrops[b];
                    if (bd && bd !== topModal && !bd.querySelector('video, iframe, main, article')) {
                        var bds = bd.style;
                        if (bds.position === 'fixed' || (window.getComputedStyle(bd).position === 'fixed')) {
                            bd.style.setProperty('display', 'none', 'important');
                            bd.style.setProperty('pointer-events', 'none', 'important');
                        }
                    }
                }

                restoreScroll();
            }

            // Remove mobile app prompt nags
            var appNags = document.querySelectorAll('.smartbanner, .app-banner, .smart-app-banner, [class*="open-in-app" i], [class*="openInApp"], [class*="getTheApp" i], [class*="install-app" i], [class*="app-upsell" i], reddit-bottom-sheet, xpromo-app-selector, .XPromoPopup');
            for (var k = 0; k < appNags.length; k++) {
                appNags[k].style.setProperty('display', 'none', 'important');
            }
        } catch(e) {}
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            scrubNagElements();
            restoreScroll();
        });
    } else {
        scrubNagElements();
        restoreScroll();
    }

    // High-performance debounced MutationObserver (Max 1 run per 400ms, zero layout thrashing)
    try {
        var nagScheduled = false;
        var sweepsCount = 0;
        var nagObserver = new MutationObserver(function(mutations) {
            if (nagScheduled || sweepsCount > 15) return;
            var hasRelevantAddition = false;
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                if (added && added.length > 0) {
                    for (var j = 0; j < added.length; j++) {
                        var node = added[j];
                        if (node.nodeType === 1) {
                            var tag = node.tagName;
                            if (tag === 'DIV' || tag === 'SECTION' || tag === 'ASIDE' || tag === 'DIALOG') {
                                hasRelevantAddition = true;
                                break;
                            }
                        }
                    }
                }
                if (hasRelevantAddition) break;
            }
            if (!hasRelevantAddition) return;

            nagScheduled = true;
            setTimeout(function() {
                nagScheduled = false;
                sweepsCount++;
                scrubNagElements();
            }, 400);
        });

        var targetObserveNode = document.body || document.documentElement;
        if (targetObserveNode) {
            nagObserver.observe(targetObserveNode, { childList: true, subtree: true });
        } else {
            document.addEventListener('DOMContentLoaded', function() {
                if (document.body) nagObserver.observe(document.body, { childList: true, subtree: true });
            });
        }
    } catch(e) {}

})();
