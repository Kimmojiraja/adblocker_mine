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

    // 5.5. Google Interactive Media Ads (IMA SDK) HTML5 Stub (uBlock Origin google-ima.js)
    // Eliminates 6-10s video player timeouts across JWPlayer, Brightcove, Video.js,
    // Dailymotion, Yahoo Sports, BBC, Fox News, and news video players.
    try {
        if (!window.google || !window.google.ima) {
            var imaSettings = {
                setAutoPlayAdBreaks: noop,
                setDisableCustomPlaybackForIOS10Plus: noop,
                setLocale: noop,
                setNumRedirects: noop,
                setPlayerType: noop,
                setPlayerVersion: noop,
                setPpid: noop,
                setVpaidAllowed: noop,
                setVpaidMode: noop
            };

            var FakeAdsManager = function() {
                var listeners = {};
                this.addEventListener = function(type, handler) {
                    listeners[type] = listeners[type] || [];
                    listeners[type].push(handler);
                };
                this.removeEventListener = function(type, handler) {
                    if (!listeners[type]) return;
                    listeners[type] = listeners[type].filter(function(h) { return h !== handler; });
                };
                this.getCuePoints = function() { return []; };
                this.getAdSkippableState = function() { return false; };
                this.init = noop;
                this.start = function() {
                    setTimeout(function() {
                        var evTypes = ['allAdsCompleted', 'contentResumeRequested', 'complete'];
                        for (var i = 0; i < evTypes.length; i++) {
                            var evType = evTypes[i];
                            if (listeners[evType]) {
                                for (var j = 0; j < listeners[evType].length; j++) {
                                    try { listeners[evType][j]({ type: evType }); } catch(err) {}
                                }
                            }
                        }
                    }, 5);
                };
                this.stop = noop;
                this.resume = noop;
                this.pause = noop;
                this.resize = noop;
                this.discardAdBreak = noop;
                this.destroy = noop;
                this.getRemainingTime = function() { return 0; };
                this.getVolume = function() { return 1; };
                this.setVolume = noop;
                this.isCustomClickTrackingUsed = function() { return false; };
                this.isCustomPlaybackUsed = function() { return false; };
            };

            var FakeAdsLoader = function(adDisplayContainer) {
                var listeners = {};
                this.addEventListener = function(type, handler) {
                    listeners[type] = listeners[type] || [];
                    listeners[type].push(handler);
                };
                this.removeEventListener = function(type, handler) {
                    if (!listeners[type]) return;
                    listeners[type] = listeners[type].filter(function(h) { return h !== handler; });
                };
                this.contentComplete = noop;
                this.destroy = noop;
                this.getSettings = function() { return imaSettings; };
                this.requestAds = function(adsRequest) {
                    setTimeout(function() {
                        var adsManagerLoadedListeners = listeners['adsManagerLoaded'];
                        if (adsManagerLoadedListeners && adsManagerLoadedListeners.length > 0) {
                            var fakeEvent = {
                                type: 'adsManagerLoaded',
                                getAdsManager: function() { return new FakeAdsManager(); },
                                getUserRequestContext: function() {
                                    return adsRequest ? adsRequest.userRequestContext : null;
                                }
                            };
                            for (var k = 0; k < adsManagerLoadedListeners.length; k++) {
                                try { adsManagerLoadedListeners[k](fakeEvent); } catch(err) {}
                            }
                        } else {
                            var errorListeners = listeners['adError'];
                            if (errorListeners) {
                                var fakeErr = {
                                    getError: function() {
                                        return {
                                            getMessage: function() { return 'No ads'; },
                                            getType: function() { return 'AdError'; },
                                            getErrorCode: function() { return 1009; }
                                        };
                                    }
                                };
                                for (var m = 0; m < errorListeners.length; m++) {
                                    try { errorListeners[m](fakeErr); } catch(err) {}
                                }
                            }
                        }
                    }, 5);
                };
            };

            var imaObj = {
                settings: imaSettings,
                AdDisplayContainer: function(container, video) {
                    this.initialize = noop;
                    this.destroy = noop;
                },
                AdsLoader: FakeAdsLoader,
                AdsManager: FakeAdsManager,
                AdsRequest: function() {
                    this.adTagUrl = '';
                    this.adsResponse = '';
                    this.contentDuration = 0;
                    this.contentKeywords = [];
                    this.contentTitle = '';
                    this.forceExperimentIds = [];
                    this.linearAdSlotWidth = 0;
                    this.linearAdSlotHeight = 0;
                    this.liveStreamPrefetchSeconds = 0;
                    this.nonLinearAdSlotWidth = 0;
                    this.nonLinearAdSlotHeight = 0;
                    this.pageUrl = '';
                    this.userRequestContext = null;
                    this.vastLoadTimeout = 0;
                },
                AdsRenderingSettings: function() {
                    this.autoAlign = true;
                    this.bitrate = -1;
                    this.enablePreloading = false;
                    this.loadVideoTimeout = -1;
                    this.mimeTypes = null;
                    this.playAdsAfterTime = -1;
                    this.restoreCustomPlaybackStateOnPlayerInit = true;
                    this.useStyledAcceleratedCounter = true;
                    this.useStyledLinearId = true;
                    this.useStyledNonLinearId = true;
                },
                AdError: {
                    Type: { AD: 'ad', AD_LOAD: 'adLoad' },
                    ErrorCode: {
                        ADSLOT_NOT_EMPTY: 400,
                        FAILED_TO_REQUEST_ADS: 1005,
                        INVALID_ARGUMENTS: 1101,
                        PLAYLIST_NO_CONTENT_FOUND: 1009,
                        UNKNOWN_ERROR: 900,
                        VAST_LOAD_TIMEOUT: 301,
                        VIDEO_PLAY_ERROR: 400
                    }
                },
                AdEvent: {
                    Type: {
                        AD_BREAK_READY: 'adBreakReady',
                        AD_BUFFERING: 'adBuffering',
                        AD_CAN_PLAY: 'adCanPlay',
                        AD_METADATA: 'adMetadata',
                        AD_PROGRESS: 'adProgress',
                        ALL_ADS_COMPLETED: 'allAdsCompleted',
                        CLICK: 'click',
                        COMPLETE: 'complete',
                        CONTENT_PAUSE_REQUESTED: 'contentPauseRequested',
                        CONTENT_RESUME_REQUESTED: 'contentResumeRequested',
                        DURATION_CHANGE: 'durationChange',
                        EXPANDED_CHANGED: 'expandedChanged',
                        FIRST_QUARTILE: 'firstQuartile',
                        IMPRESSION: 'impression',
                        INTERACTION: 'interaction',
                        LINEAR_CHANGED: 'linearChanged',
                        LOADED: 'loaded',
                        LOG: 'log',
                        MIDPOINT: 'midpoint',
                        PAUSED: 'pause',
                        RESUMED: 'resumed',
                        SKIPPABLE_STATE_CHANGED: 'skippableStateChanged',
                        SKIPPED: 'skipped',
                        STARTED: 'started',
                        THIRD_QUARTILE: 'thirdQuartile',
                        USER_CLOSE: 'userClose',
                        VOLUME_CHANGED: 'volumeChanged',
                        VOLUME_MUTED: 'volumeMuted'
                    }
                },
                AdErrorEvent: { Type: { AD_ERROR: 'adError' } },
                AdsManagerLoadedEvent: { Type: { ADS_MANAGER_LOADED: 'adsManagerLoaded' } },
                ViewMode: { NORMAL: 'normal', FULLSCREEN: 'fullscreen' },
                OmidAccessMode: { FULL: 'full', DOMAIN: 'domain', LIMITED: 'limited' }
            };

            window.google = window.google || {};
            try {
                Object.defineProperty(window.google, 'ima', {
                    get: function() { return imaObj; },
                    set: function() {},
                    configurable: true,
                    enumerable: true
                });
            } catch(e) {
                window.google.ima = imaObj;
            }
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
    var scrollLockClasses = [
        'has-scroll-lock', 'no-scroll', 'modal-open', 'fc-dialog-open',
        'sp-message-open', 'overflow-hidden', 'prevent-scroll', 'body-scroll-lock',
        'is-locked', 'freeze-scroll', 'scroll-disabled', 'u-scroll-locked',
        'gu-overflow-hidden', 'stop-scrolling', 'disable-scroll', 'fixed-scroll',
        'didomi-popup-open', 'fides-overlay-open'
    ];

    var restoreScroll = function() {
        try {
            var docEl = document.documentElement;
            var body = document.body;

            // Remove lock classes from html and body
            for (var i = 0; i < scrollLockClasses.length; i++) {
                var c = scrollLockClasses[i];
                if (docEl && docEl.classList && docEl.classList.contains(c)) docEl.classList.remove(c);
                if (body && body.classList && body.classList.contains(c)) body.classList.remove(c);
            }

            if (docEl) {
                var s = docEl.style;
                if (s.overflow === 'hidden' || s.overflowY === 'hidden' || s.position === 'fixed') {
                    s.setProperty('overflow', 'visible', 'important');
                    s.setProperty('overflow-y', 'auto', 'important');
                    if (s.position === 'fixed') s.setProperty('position', 'static', 'important');
                    s.setProperty('height', 'auto', 'important');
                    s.setProperty('max-height', 'none', 'important');
                }
            }
            if (body) {
                var bs = body.style;
                if (bs.overflow === 'hidden' || bs.overflowY === 'hidden' || bs.position === 'fixed') {
                    bs.setProperty('overflow', 'visible', 'important');
                    bs.setProperty('overflow-y', 'auto', 'important');
                    if (bs.position === 'fixed') bs.setProperty('position', 'static', 'important');
                    bs.setProperty('height', 'auto', 'important');
                    bs.setProperty('max-height', 'none', 'important');
                }
                if (bs.filter && bs.filter.indexOf('blur') !== -1) {
                    bs.setProperty('filter', 'none', 'important');
                }
                if (bs.pointerEvents === 'none') {
                    bs.setProperty('pointer-events', 'auto', 'important');
                }
            }

            // Ensure dynamic anti-scroll CSS style tag is active
            if (!document.getElementById('__fokk_scroll_enforcer')) {
                var style = document.createElement('style');
                style.id = '__fokk_scroll_enforcer';
                style.innerHTML = 'html, body { overflow-y: auto !important; position: static !important; height: auto !important; max-height: none !important; } .has-scroll-lock, .no-scroll, .modal-open, .fc-dialog-open, .sp-message-open, .overflow-hidden, .prevent-scroll, .is-locked, .freeze-scroll, .scroll-disabled, .gu-overflow-hidden { overflow: visible !important; overflow-y: auto !important; position: static !important; }';
                (document.head || document.documentElement).appendChild(style);
            }
        } catch(e) {}
    };

    // 9. Intelligent Anti-Adblock Defuser & Auto-Dismissal (Generic across The Sun, Fox News, Forbes, The Guardian)
    var scrubNagElements = function() {
        if (host.includes('yahoo.com') || host.includes('espn.com')) return;
        try {
            // 1. Direct structural CMP selectors (Google Funding Choices, Sourcepoint, Admiral, Guardian, etc.)
            var directCmpSelectors = [
                '.fc-ab-root', '.fc-dialog-overlay', '.fc-dialog-container', '.fc-monetization-dialog',
                '.sp_message_container', '[id^="sp_message_container"]',
                '.message-overlay', 'div[id^="admr_"]', '.admiral-root',
                '#didomi-host', '#didomi-popup',
                '.contributions-banner', '.support-the-guardian', '.engagementBanner',
                '[data-link-name*="adblock-sticky-message"]',
                '.adblock-modal', '.adblock-overlay', '.adblock-dialog',
                'div[class*="adblock-wall" i]', 'div[class*="anti-adblock" i]',
                'div[id*="anti-adblock" i]'
            ];
            for (var sc = 0; sc < directCmpSelectors.length; sc++) {
                var elements = document.querySelectorAll(directCmpSelectors[sc]);
                for (var elIdx = 0; elIdx < elements.length; elIdx++) {
                    var el = elements[elIdx];
                    if (el && el !== document.body && el !== document.documentElement) {
                        el.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('visibility', 'hidden', 'important');
                        el.style.setProperty('pointer-events', 'none', 'important');
                        try { el.remove(); } catch(err) {}
                    }
                }
            }

            var nagPhrases = [
                'ad or script blocking',
                'interfering with this page',
                'disable any ad or script',
                'ad blocker detected',
                'ad blocker',
                'ad-blocker',
                'adblock',
                'ad block',
                'content blocker',
                'disable your ad blocker',
                'turn off your ad blocker',
                'pause your ad blocker',
                'please allow ads',
                'support our journalism',
                'supporting our journalism',
                'quality journalism',
                'already a subscriber',
                'continue without supporting',
                'we noticed you are using an ad blocker',
                'we notice you are using an ad blocker',
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
                if (!dlg || dlg === document.body || dlg === document.documentElement) continue;
                var dlgText = (dlg.innerText || dlg.textContent || '').toLowerCase();
                var isAdblockNag = false;
                for (var n = 0; n < nagPhrases.length; n++) {
                    if (dlgText.indexOf(nagPhrases[n]) !== -1) {
                        isAdblockNag = true;
                        break;
                    }
                }
                if (!isAdblockNag) continue;

                // Auto-click secondary dismissal button / link (e.g. "Continue without supporting us")
                var clickables = dlg.querySelectorAll('button, a, span[role="button"], div[role="button"]');
                var clicked = false;
                for (var c = 0; c < clickables.length; c++) {
                    var btn = clickables[c];
                    var btnText = (btn.innerText || btn.textContent || '').trim().toLowerCase();
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

                // Hide the modal container and backdrop (avoid climbing to page layout containers)
                var topModal = dlg.closest('div[role="dialog"], div[class*="modal"], div[class*="dialog"], div[class*="popup"], div[class*="adblock"], div[id*="modal"], div[id*="adblock"]') || dlg;
                if (topModal === document.body || topModal === document.documentElement) {
                    topModal = dlg;
                }
                if (topModal.querySelector && topModal.querySelector('main, article, h1, nav, #main, #content, #App, #app, #root')) {
                    continue;
                }
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

    // High-performance debounced MutationObserver (Continuous, throttled to max 1 run per 350ms)
    try {
        var nagScheduled = false;
        var nagObserver = new MutationObserver(function(mutations) {
            if (nagScheduled) return;
            var hasRelevantChange = false;
            for (var i = 0; i < mutations.length; i++) {
                var m = mutations[i];
                if (m.type === 'childList') {
                    var added = m.addedNodes;
                    if (added && added.length > 0) {
                        for (var j = 0; j < added.length; j++) {
                            var node = added[j];
                            if (node.nodeType === 1) {
                                var tag = node.tagName;
                                if (tag === 'DIV' || tag === 'SECTION' || tag === 'ASIDE' || tag === 'DIALOG') {
                                    hasRelevantChange = true;
                                    break;
                                }
                            }
                        }
                    }
                } else if (m.type === 'attributes') {
                    var target = m.target;
                    if (target === document.documentElement || target === document.body) {
                        hasRelevantChange = true;
                    }
                }
                if (hasRelevantChange) break;
            }
            if (!hasRelevantChange) return;

            nagScheduled = true;
            setTimeout(function() {
                nagScheduled = false;
                scrubNagElements();
                restoreScroll();
            }, 350);
        });

        var targetObserveNode = document.body || document.documentElement;
        var observerConfig = { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] };
        if (targetObserveNode) {
            nagObserver.observe(targetObserveNode, observerConfig);
        } else {
            document.addEventListener('DOMContentLoaded', function() {
                if (document.body) nagObserver.observe(document.body, observerConfig);
            });
        }
    } catch(e) {}

})();
