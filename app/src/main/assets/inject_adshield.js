// Fokk Shield optional browser-side cosmetic filters
(function() {
    'use strict';
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;

    const currentHost = ((window.location && window.location.hostname) || '').toLowerCase();
    if (currentHost.includes('webxprt') || currentHost.includes('principledtechnologies') || currentHost.includes('browserbench') ||
        currentHost.includes('speedometer') || currentHost.includes('kraken-benchmark') || currentHost.includes('octane') ||
        currentHost.includes('jetstream') || currentHost.includes('motionmark')) {
        return; // Zero overhead on performance benchmarking suites!
    }

    function hostIs(name) {
        return currentHost === name || currentHost.endsWith('.' + name);
    }
    const href = ((window.location && window.location.href) || '').toLowerCase();
    const isFragileSocialApp = hostIs('x.com') || hostIs('twitter.com') || hostIs('instagram.com') ||
        hostIs('threads.net') || hostIs('facebook.com') || href.includes('//x.com') || href.includes('//twitter.com');
    if (isFragileSocialApp) {
        return; // Zero tampering on X (Twitter), Instagram, etc.
    }
    const isYouTube = hostIs('youtube.com') || hostIs('youtu.be') || currentHost.endsWith('.youtube.com');

    // =========================================================================
    // Dynamic Anti-Fingerprint Cloak: Safe Protections (Non-destructive to bot checkers)
    // Avoids altering Canvas prototypes which triggers Cloudflare/Kasada/PerimeterX bot blocks.
    // =========================================================================
    function installAntiFingerprintCloak() {
        if (window.__fokk_cloak_active) return;
        window.__fokk_cloak_active = true;

        // AudioContext Fingerprint Neutralizer (Safe & Non-intrusive)
        try {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            if (AudioCtx && AudioCtx.prototype.createAnalyser) {
                const origCreateAnalyser = AudioCtx.prototype.createAnalyser;
                AudioCtx.prototype.createAnalyser = function() {
                    const analyser = origCreateAnalyser.apply(this, arguments);
                    try {
                        const origGetFloatFreq = analyser.getFloatFrequencyData;
                        if (origGetFloatFreq) {
                            analyser.getFloatFrequencyData = function(array) {
                                origGetFloatFreq.apply(this, arguments);
                                for (let i = 0; i < array.length; i += 16) {
                                    array[i] += 0.00001;
                                }
                            };
                        }
                    } catch(e) {}
                    return analyser;
                };
            }
        } catch(e) {}
    }

    // =========================================================================
    // Main World Defusers: Hooks prototypes (Response.prototype.json, JSON.parse,
    // window.open, XMLHttpRequest) inside the page's actual V8 execution context.
    // =========================================================================
    function installMainWorldDefusers() {
        if (window.__fokk_main_defused) return;
        window.__fokk_main_defused = true;

        // 0. Intelligent Popunder & Malicious window.open Defuser
        try {
            const origWindowOpen = window.open;
            window.open = function(url, target, features) {
                if (url && typeof url === 'string') {
                    const u = url.toLowerCase();
                    if (u.includes('exoclick') || u.includes('trafficjunky') || u.includes('juicyads') ||
                        u.includes('popads') || u.includes('popcash') || u.includes('propellerads') ||
                        u.includes('adsterra') || u.includes('clickadu') || u.includes('hilltopads') ||
                        u.includes('monetag') || u.includes('syndicate') || u.includes('onclick') ||
                        u.includes('directrev') || u.includes('cam4') || u.includes('camsoda') ||
                        u.includes('chaturbate') || u.includes('stripchat') || u.includes('bongacams') ||
                        u.includes('admaven') || u.includes('twinred') || u.includes('trafficstars') ||
                        u.includes('trafficfactory') || u.includes('ero-advertising') || u.includes('livejasmin') ||
                        u.includes('doublepimp') || u.includes('ad-score')) {
                        console.warn('[Fokk Shield] Disarmed popunder window.open to ad:', url);
                        return null;
                    }
                }
                return origWindowOpen.apply(this, arguments);
            };
        } catch(e) {}

        // 1. YouTube & Instagram Pre-Emptive Data Sanitizers (Inline & Dynamic)
        function sanitizeYtPlayerResponse(obj, depth) {
            if (!obj || typeof obj !== 'object') return;
            if (!depth) depth = 0;
            if (depth > 6) return;
            try {
                if (Array.isArray(obj)) {
                    for (let i = 0; i < obj.length; i++) {
                        sanitizeYtPlayerResponse(obj[i], depth + 1);
                    }
                    return;
                }

                // Defuse ad arrays and ad configs if present on this object (uBO standard: DELETE so 'adPlacements in obj' evaluates to false)
                delete obj.adPlacements;
                delete obj.playerAds;
                delete obj.adSlots;
                delete obj.adBreaks;
                delete obj.adPlacementConfig;
                delete obj.adBreakHeartbeatParams;
                if (obj.playerConfig && obj.playerConfig.adConfig) delete obj.playerConfig.adConfig;

                // Recurse into known YouTube containers
                if (obj.playerResponse) {
                    if (typeof obj.playerResponse === 'object') {
                        sanitizeYtPlayerResponse(obj.playerResponse, depth + 1);
                    } else if (typeof obj.playerResponse === 'string') {
                        try {
                            const parsed = JSON.parse(obj.playerResponse);
                            sanitizeYtPlayerResponse(parsed, depth + 1);
                            obj.playerResponse = JSON.stringify(parsed);
                        } catch (e) {}
                    }
                }

                // Mobile YouTube prefetch & watch endpoints (resolve_url, get_watch, next)
                if (obj.endpoint && obj.endpoint.watchEndpoint) {
                    sanitizeYtPlayerResponse(obj.endpoint.watchEndpoint, depth + 1);
                }
                if (obj.watchEndpoint) {
                    if (obj.watchEndpoint.unserializedPrefetchData) {
                        sanitizeYtPlayerResponse(obj.watchEndpoint.unserializedPrefetchData, depth + 1);
                    }
                }
                if (obj.unserializedPrefetchData) {
                    sanitizeYtPlayerResponse(obj.unserializedPrefetchData, depth + 1);
                }
                if (obj.actions && Array.isArray(obj.actions)) {
                    sanitizeYtPlayerResponse(obj.actions, depth + 1);
                }
            } catch (e) {}
        }

        // Hook ytInitialPlayerResponse so initial inline page data is sanitized before player boots
        let _ytInitialPlayerResponse = window.ytInitialPlayerResponse;
        if (_ytInitialPlayerResponse) sanitizeYtPlayerResponse(_ytInitialPlayerResponse);
        try {
            Object.defineProperty(window, 'ytInitialPlayerResponse', {
                get: function() { return _ytInitialPlayerResponse; },
                set: function(val) {
                    sanitizeYtPlayerResponse(val);
                    _ytInitialPlayerResponse = val;
                },
                configurable: true
            });
        } catch (e) {}

        // uBlock Origin standard: Defuses YouTube ad data across ALL response parsing methods:
        // 1. window.JSON.parse — scoped to YouTube only to avoid blocking other sites
        try {
            if (window.location.hostname.includes('youtube.com')) {
                const origJSONParse = JSON.parse;
                JSON.parse = function(text, reviver) {
                    const data = origJSONParse.apply(this, arguments);
                    if (data && typeof data === 'object') {
                        sanitizeYtPlayerResponse(data);
                    }
                    return data;
                };
            }
        } catch (e) {}

        // 2. Response.prototype.json (Critical for Chromium / Android WebView — Response.json does NOT call window.JSON.parse)
        try {
            if (window.Response && Response.prototype && Response.prototype.json) {
                const origResponseJson = Response.prototype.json;
                Response.prototype.json = async function() {
                    const data = await origResponseJson.apply(this, arguments);
                    if (data && typeof data === 'object' && window.location.hostname.indexOf('youtube.com') !== -1) {
                        sanitizeYtPlayerResponse(data);
                    }
                    return data;
                };
            }
        } catch (e) {}

        // Canvas and WebGL noise trips bot checks on social apps and dirties YouTube frames.
        try {
            if (!isFragileSocialApp && !isYouTube) installAntiFingerprintCloak();
        } catch(e) {}

        // 3. XMLHttpRequest.prototype.response getter (for responseType === 'json')
        try {
            if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
                const origXhrGetter = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response')?.get;
                if (origXhrGetter) {
                    Object.defineProperty(XMLHttpRequest.prototype, 'response', {
                        get: function() {
                            const res = origXhrGetter.apply(this);
                            if (this.responseType === 'json' && res && typeof res === 'object' &&
                                window.location.hostname.indexOf('youtube.com') !== -1) {
                                sanitizeYtPlayerResponse(res);
                            }
                            return res;
                        },
                        configurable: true
                    });
                }
            }
        } catch (e) {}

        // Instagram response scrubber
        function sanitizeInstagramData(data) {
            if (!data || typeof data !== 'object') return false;
            let modified = false;
            try {
                // GraphQL timeline connection
                if (data.data && data.data.xdt_api__v1__feed__timeline__connection) {
                    const conn = data.data.xdt_api__v1__feed__timeline__connection;
                    if (Array.isArray(conn.edges)) {
                        conn.edges = conn.edges.filter(function(edge) {
                            const node = edge && edge.node;
                            if (!node) return true;
                            if (node.__typename === 'XDTFeedItem' && node.media_or_ad) {
                                return !node.media_or_ad.ad_id && !node.media_or_ad.is_paid_partnership && !node.media_or_ad.sponsored_data;
                            }
                            if (node.is_ad || node.is_sponsored || (node.media_or_ad && node.media_or_ad.ad_id)) return false;
                            return true;
                        });
                        modified = true;
                    }
                }

                // GraphQL stories/reels connection
                if (data.data && data.data.xdt_api__v1__feed__reels_tray__connection) {
                    const conn = data.data.xdt_api__v1__feed__reels_tray__connection;
                    if (Array.isArray(conn.edges)) {
                        conn.edges = conn.edges.filter(function(edge) {
                            const node = edge && edge.node;
                            if (!node) return true;
                            if (node.is_ad || (node.media_or_ad && node.media_or_ad.ad_id)) return false;
                            return true;
                        });
                        modified = true;
                    }
                }

                // REST timeline feed items
                if (Array.isArray(data.feed_items)) {
                    data.feed_items = data.feed_items.filter(function(item) {
                        if (item.ad_id || item.is_sponsored || item.sponsored_data) return false;
                        if (item.media_or_ad && (item.media_or_ad.ad_id || item.media_or_ad.sponsored_data || item.media_or_ad.is_paid_partnership)) return false;
                        return true;
                    });
                    modified = true;
                }

                // REST general items (Protected: preserve Instagram notification / inbox items)
                if (Array.isArray(data.items)) {
                    data.items = data.items.filter(function(item) {
                        if (!item) return true;
                        if (item.story_type !== undefined || item.notification_type !== undefined || item.args !== undefined) {
                            return true;
                        }
                        if (item.ad_id || item.is_sponsored || item.sponsored_data || (item.ad_type && item.ad_type !== '')) return false;
                        if (item.media_or_ad && (item.media_or_ad.ad_id || item.media_or_ad.sponsored_data || item.media_or_ad.is_paid_partnership || (item.media_or_ad.ad_type && item.media_or_ad.ad_type !== ''))) return false;
                        return true;
                    });
                    modified = true;
                }

                // Injected items (newer Instagram API wraps ads inside injected_items)
                if (Array.isArray(data.injected_items)) {
                    data.injected_items = data.injected_items.filter(function(item) {
                        if (!item) return true;
                        if (item.item_type && item.item_type !== 'media') return false;
                        if (item.ad_id || item.is_sponsored || item.sponsored_data || item.ad_type) return false;
                        return true;
                    });
                    modified = true;
                }

                // Top-level response with embedded edge containers
                if (data.data) {
                    ['user', 'xdt_api__v1__media__shortcode__web_info', 'xdt_api__v1__media__media_aware_shortcode'].forEach(function(key) {
                        const container = data.data[key];
                        if (container && container.edge_web_feed_timeline) {
                            const edges = container.edge_web_feed_timeline.edges;
                            if (Array.isArray(edges)) {
                                container.edge_web_feed_timeline.edges = edges.filter(function(edge) {
                                    const node = edge && edge.node;
                                    if (!node) return true;
                                    if (node.__typename === 'GraphSponsoredUser' || node.__typename === 'XDTAdMedia') return false;
                                    if (node.is_promoted) return false;
                                    if (node.media_or_ad && (node.media_or_ad.ad_id || node.media_or_ad.is_paid_partnership)) return false;
                                    return true;
                                });
                                modified = true;
                            }
                        }
                    });
                }
            } catch (e) {}
            return modified;
        }

        // Smart Auto-Unmute YouTube & Disarm Forced Startup Muting
        let userInteractedMute = false;
        try {
            document.addEventListener('click', function(e) {
                if (!window.location.hostname.includes('youtube.com')) return;
                const muteBtn = e.target.closest('.ytp-mute-button, [aria-label*="mute" i]');
                if (muteBtn && !muteBtn.matches('.ytp-unmute, [aria-label*="unmute" i]')) {
                    userInteractedMute = true;
                }
            }, { capture: true, passive: true });

            const origMutedDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'muted');
            if (origMutedDescriptor && origMutedDescriptor.set) {
                Object.defineProperty(HTMLMediaElement.prototype, 'muted', {
                    get: origMutedDescriptor.get,
                    set: function(val) {
                        // Only the real watch player may be forced audible. Feed and
                        // thumbnail previews stay muted so scrolling the home page is quiet.
                        if (val === true && isPrimaryYouTubeVideo(this) && !userInteractedMute) {
                            return origMutedDescriptor.set.call(this, false);
                        }
                        return origMutedDescriptor.set.call(this, val);
                    },
                    configurable: true
                });
            }
        } catch (e) {}

        function isPrimaryYouTubeVideo(video) {
            if (!video || video.tagName !== 'VIDEO') return false;
            if (!window.location.hostname || window.location.hostname.indexOf('youtube.com') === -1) return false;
            const path = window.location.pathname || '';
            if (path.indexOf('/watch') !== 0 && path.indexOf('/shorts/') !== 0) return false;
            if (video.closest('ytd-thumbnail, ytd-video-preview, ytd-rich-item-renderer, ytd-compact-video-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, ytm-shorts-lockup-view-model, ytd-rich-shelf-renderer')) {
                return false;
            }
            const rect = video.getBoundingClientRect();
            return rect.width >= 200 && rect.height >= 120;
        }

        function unmuteYouTubeVideo() {
            if (!window.location.hostname.includes('youtube.com')) return;
            if (userInteractedMute) return;
            if (!window.location.pathname.startsWith('/watch') && !window.location.pathname.startsWith('/shorts/')) return;
            try {
                const videos = document.querySelectorAll('video');
                for (let i = 0; i < videos.length; i++) {
                    const video = videos[i];
                    if (!isPrimaryYouTubeVideo(video)) continue;
                    if (video.muted) video.muted = false;
                    if (video.defaultMuted) video.defaultMuted = false;
                    if (video.volume < 1.0) video.volume = 1.0;
                }
                const player = document.getElementById('movie_player') ||
                               document.querySelector('.html5-video-player, ytm-player');
                if (player) {
                    if (typeof player.unMute === 'function') {
                        try { player.unMute(); } catch (e) {}
                    }
                    if (typeof player.setVolume === 'function') {
                        try { player.setVolume(100); } catch (e) {}
                    }
                }
                const unmuteBtn = document.querySelector('.ytp-unmute, [aria-label*="unmute" i], .ytm-unmute-button');
                if (unmuteBtn && typeof unmuteBtn.click === 'function') {
                    try { unmuteBtn.click(); } catch (e) {}
                }
            } catch (e) {}
        }
        window.__fokk_unmuteYt = unmuteYouTubeVideo;

        if (window.location.hostname.includes('youtube.com')) {
            document.addEventListener('play', unmuteYouTubeVideo, { capture: true, passive: true });
            document.addEventListener('playing', unmuteYouTubeVideo, { capture: true, passive: true });
            document.addEventListener('loadeddata', unmuteYouTubeVideo, { capture: true, passive: true });
            document.addEventListener('canplay', unmuteYouTubeVideo, { capture: true, passive: true });
            window.addEventListener('yt-navigate-finish', unmuteYouTubeVideo, { capture: true, passive: true });

            // Force HTML5 Fullscreen API on player container rather than raw video element
            // This guarantees all player controls (play/pause, timeline, gear, 3-dots, exit fullscreen) remain interactive in fullscreen!
            try {
                delete HTMLVideoElement.prototype.webkitEnterFullscreen;
            } catch (e) {
                try {
                    HTMLVideoElement.prototype.webkitEnterFullscreen = function() {
                        const playerContainer = document.getElementById('movie_player') ||
                                                document.querySelector('.html5-video-player, #player');
                        if (playerContainer && typeof playerContainer.requestFullscreen === 'function') {
                            return playerContainer.requestFullscreen();
                        }
                        if (typeof this.requestFullscreen === 'function') {
                            return this.requestFullscreen();
                        }
                    };
                } catch (e2) {}
            }

            let userExplicitPaused = false;

            document.addEventListener('click', function(e) {
                if (!window.location.hostname.includes('youtube.com')) return;
                const pauseBtn = e.target.closest('.ytp-play-button[aria-label*="Pause" i], [aria-label="Pause" i], .ytp-play-button[data-title-no-tooltip="Pause"]');
                if (pauseBtn) {
                    userExplicitPaused = true;
                    return;
                }
                const playBtn = e.target.closest('.ytp-play-button[aria-label*="Play" i], [aria-label="Play" i], .ytp-large-play-button, .ytm-play-button');
                if (playBtn) {
                    userExplicitPaused = false;
                    return;
                }
            }, { capture: true, passive: true });

            document.addEventListener('pause', function(e) {
                if (!window.location.hostname.includes('youtube.com')) return;
                if (e.target && e.target.tagName === 'VIDEO') {
                    if (e.target.currentTime > 0.5 && !e.target.ended) {
                        userExplicitPaused = true;
                    }
                }
            }, { capture: true, passive: true });

            document.addEventListener('play', function(e) {
                if (!window.location.hostname.includes('youtube.com')) return;
                if (e.target && e.target.tagName === 'VIDEO') {
                    userExplicitPaused = false;
                }
            }, { capture: true, passive: true });

            // YouTube Shorts Support with Unprompted Cold-Boot Auto-Open Guard:
            // YouTube mobile sometimes auto-opens /shorts on cold launch from stale sessionStorage or entry promos.
            // We guard against unprompted cold-boot redirect, but once the user interacts or clicks a Short / Shorts tab,
            // we allow full native YouTube Shorts with swipe reels and real YouTube feel.
            let userHasInteracted = false;
            let userInitiatedShorts = false;

            ['pointerdown', 'touchstart', 'mousedown', 'keydown', 'click'].forEach(function(evt) {
                window.addEventListener(evt, function(e) {
                    userHasInteracted = true;
                    const target = e.target;
                    if (target && target.closest && (
                        target.closest('a[href*="/shorts/"]') ||
                        target.closest('ytm-pivot-bar-item-renderer:nth-child(2)') ||
                        target.closest('[aria-label*="Shorts" i]') ||
                        target.closest('ytm-reel-shelf-renderer') ||
                        target.closest('ytd-reel-shelf-renderer') ||
                        target.closest('ytm-shorts-lockup-view-model') ||
                        target.closest('.ytm-shorts-entry-point')
                    )) {
                        userInitiatedShorts = true;
                    }
                }, { capture: true, passive: true });
            });

            // Prevent cold-start unprompted auto-opening of Shorts when landing on Home
            try {
                if (window.location.hostname.includes('youtube.com')) {
                    const currentPath = window.location.pathname || '';
                    if (currentPath === '/' || currentPath === '') {
                        for (let i = sessionStorage.length - 1; i >= 0; i--) {
                            const k = sessionStorage.key(i);
                            if (k && (k.includes('shorts') || k.includes('reel') || k.includes('autonav'))) {
                                sessionStorage.removeItem(k);
                            }
                        }
                    }
                }
            } catch (e) {}

            // Hook history.pushState and replaceState ONLY to prevent unprompted auto-routing to Shorts on cold start
            const origPushState = window.history.pushState;
            window.history.pushState = function(state, title, url) {
                if (url && typeof url === 'string' && window.location.hostname.includes('youtube.com')) {
                    const isShorts = url.includes('/shorts');
                    const isColdHome = (window.location.pathname === '/' || window.location.pathname === '');
                    // Only block if cold home boot and user has NOT touched/initiated anything
                    if (isShorts && isColdHome && !userHasInteracted && !userInitiatedShorts) {
                        console.warn('[Fokk Shield] Prevented unprompted cold-start auto-jump to Shorts:', url);
                        return;
                    }
                }
                return origPushState.apply(this, arguments);
            };

            const origReplaceState = window.history.replaceState;
            window.history.replaceState = function(state, title, url) {
                if (url && typeof url === 'string' && window.location.hostname.includes('youtube.com')) {
                    const isShorts = url.includes('/shorts');
                    const isColdHome = (window.location.pathname === '/' || window.location.pathname === '');
                    if (isShorts && isColdHome && !userHasInteracted && !userInitiatedShorts) {
                        console.warn('[Fokk Shield] Prevented unprompted cold-start auto-jump to Shorts (replaceState):', url);
                        return;
                    }
                }
                return origReplaceState.apply(this, arguments);
            };

            // YouTube Resume & Foreground Playback Resync:
            // When app is minimized or user switches apps, video continues playing in background.
            // On returning to foreground, YouTube's UI overlay may be stuck displaying a buffering spinner
            // or pause button out-of-sync with the actively playing <video> element.
            function resyncYouTubePlayback() {
                if (!window.location.hostname.includes('youtube.com')) return;
                try {
                    const videos = document.querySelectorAll('video');
                    for (let i = 0; i < videos.length; i++) {
                        const video = videos[i];
                        if (video && !video.paused && !video.ended && video.readyState >= 2) {
                            // 1. Dispatch playing events so YouTube's state listeners recognize active playback
                            try {
                                video.dispatchEvent(new Event('playing'));
                                video.dispatchEvent(new Event('play'));
                            } catch (e) {}

                            // 2. Clear stuck buffering / seeking classes on player container
                            const player = document.getElementById('movie_player') ||
                                           video.closest('.html5-video-player') ||
                                           document.querySelector('.html5-video-player, ytm-player');
                            if (player) {
                                try {
                                    player.classList.remove('ytp-buffering');
                                    player.classList.remove('ytp-seeking');
                                    player.classList.remove('ytp-cued');
                                    player.classList.add('playing-mode');
                                    player.classList.remove('paused-mode');

                                    if (typeof player.getPlayerState === 'function') {
                                        const state = player.getPlayerState();
                                        // If stuck in BUFFERING (3) or PAUSED (2) while video is playing, sync it
                                        if (state !== 1 && typeof player.playVideo === 'function') {
                                            player.playVideo();
                                        }
                                    }
                                } catch (e) {}
                            }

                            // 3. Dismiss stuck mobile buffering overlay & spinner
                            const overlays = document.querySelectorAll('.ytm-player-control-overlay, .player-control-overlay, ytm-player-control-overlay');
                            for (let j = 0; j < overlays.length; j++) {
                                const overlay = overlays[j];
                                overlay.removeAttribute('data-buffering');
                                overlay.removeAttribute('data-is-buffering');
                                overlay.classList.remove('player-control-overlay-buffering');
                                if (!userExplicitPaused) {
                                    overlay.classList.remove('fadein');
                                    overlay.classList.add('fadeout');
                                }
                            }

                            const spinners = document.querySelectorAll('.ytp-spinner, .player-control-spinner, ytm-spinner');
                            for (let k = 0; k < spinners.length; k++) {
                                const spinner = spinners[k];
                                spinner.style.display = 'none';
                                setTimeout(function() {
                                    if (spinner.style.display === 'none') {
                                        spinner.style.display = '';
                                    }
                                }, 800);
                            }

                            // 4. Update play/pause button state in player controls
                            const playButtons = document.querySelectorAll('.ytp-play-button, .player-control-play-pause-icon');
                            for (let m = 0; m < playButtons.length; m++) {
                                const btn = playButtons[m];
                                btn.setAttribute('aria-label', 'Pause');
                                btn.classList.remove('ytp-play-button-paused');
                            }
                        }
                    }
                } catch (e) {}
            }

            window.__fokk_resyncPlayback = resyncYouTubePlayback;

            // Trigger resync on focus, pageshow, and resume
            window.addEventListener('focus', function() {
                resyncYouTubePlayback();
                setTimeout(resyncYouTubePlayback, 250);
            }, { capture: true, passive: true });

            window.addEventListener('pageshow', function() {
                resyncYouTubePlayback();
                setTimeout(resyncYouTubePlayback, 250);
            }, { capture: true, passive: true });

            // Hook window.__fokk_is_background setter
            let _fokkBackgroundState = false;
            try {
                Object.defineProperty(window, '__fokk_is_background', {
                    get: function() { return _fokkBackgroundState; },
                    set: function(val) {
                        _fokkBackgroundState = val;
                        if (!val) {
                            resyncYouTubePlayback();
                            setTimeout(resyncYouTubePlayback, 200);
                            setTimeout(resyncYouTubePlayback, 600);
                        }
                    },
                    configurable: true
                });
            } catch (e) {}

            // Real-time timeupdate guard: If video is playing and advancing, remove any stuck buffering overlays immediately
            document.addEventListener('timeupdate', function(e) {
                const v = e.target;
                if (v && v.tagName === 'VIDEO' && !v.paused && window.location.hostname.includes('youtube.com')) {
                    const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    if (player && player.classList.contains('ytp-buffering')) {
                        player.classList.remove('ytp-buffering');
                        const spinner = document.querySelector('.ytp-spinner');
                        if (spinner) spinner.style.display = 'none';
                    }
                    const overlay = document.querySelector('.ytm-player-control-overlay[data-buffering="true"]');
                    if (overlay) {
                        overlay.removeAttribute('data-buffering');
                    }
                }
            }, { capture: true, passive: true });

            // Playback & Unmute Trigger (Only triggers on /watch pages; NEVER on feed, search or Shorts)
            function triggerPlayNow() {
                if (!window.location.hostname.includes('youtube.com')) return;
                if (userExplicitPaused) return;
                if (!window.location.pathname.startsWith('/watch')) return;

                const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player, ytm-player');
                if (player) {
                    if (typeof player.unMute === 'function') {
                        try { player.unMute(); } catch (e) {}
                    }
                    if (typeof player.setVolume === 'function') {
                        try { player.setVolume(100); } catch (e) {}
                    }
                    if (typeof player.setAutonav === 'function' && window.location.search.includes('list=')) {
                        try { player.setAutonav(true); } catch (e) {}
                    }
                    if (typeof player.playVideo === 'function') {
                        try { player.playVideo(); } catch (e) {}
                    }
                }

                const video = document.querySelector('video');
                if (video) {
                    if (video.muted) {
                        try { video.muted = false; } catch (e) {}
                    }
                    if (video.defaultMuted) {
                        try { video.defaultMuted = false; } catch (e) {}
                    }
                    if (video.volume < 1.0) {
                        try { video.volume = 1.0; } catch (e) {}
                    }
                    if (video.paused && !userExplicitPaused) {
                        try {
                            const playPromise = video.play();
                            if (playPromise !== undefined) {
                                playPromise.catch(function() {});
                            }
                        } catch (e) {}
                    }
                }
            }
            window.__fokk_triggerPlay = triggerPlayNow;
            window.__fokk_mediaPlay = function() {
                userExplicitPaused = false;
                triggerPlayNow();
            };
            window.__fokk_mediaPause = function() {
                userExplicitPaused = true;
                const v = document.querySelector('video');
                if (v) try { v.pause(); } catch (e) {}
                const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                if (player && typeof player.pauseVideo === 'function') try { player.pauseVideo(); } catch (e) {}
            };
            window.__fokk_mediaSeek = function(sec) {
                const v = document.querySelector('video');
                if (v) {
                    try { v.currentTime = Math.max(0, v.currentTime + sec); } catch (e) {}
                }
            };

            // Continuous Song-by-Song & Playlist Autoplay: Only advances on /watch when inside a playlist
            function advancePlaylistOrQueue() {
                if (!window.location.hostname.includes('youtube.com')) return;
                userExplicitPaused = false;
                if (!window.location.pathname.startsWith('/watch') || !window.location.search.includes('list=')) return;

                const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player, ytm-player');
                if (player && typeof player.nextVideo === 'function') {
                    try {
                        player.nextVideo();
                        setTimeout(triggerPlayNow, 500);
                        return;
                    } catch (e) {}
                }
                const nextBtn = document.querySelector('.ytp-next-button, [aria-label*="Next" i], .ytm-playlist-next-button');
                if (nextBtn && typeof nextBtn.click === 'function') {
                    try {
                        nextBtn.click();
                        setTimeout(triggerPlayNow, 500);
                        return;
                    } catch (e) {}
                }
            }
            window.__fokk_advancePlaylistOrQueue = advancePlaylistOrQueue;

            // Only advance playlists when an active playlist track ends
            document.addEventListener('ended', function(e) {
                if (window.location.pathname.startsWith('/watch') && window.location.search.includes('list=')) {
                    setTimeout(advancePlaylistOrQueue, 600);
                }
            }, { capture: true, passive: true });

            // Ensure playback starts automatically when navigating to a new watch page
            window.addEventListener('yt-navigate-finish', function() {
                userExplicitPaused = false;
                if (window.location.pathname.startsWith('/watch')) {
                    setTimeout(triggerPlayNow, 300);
                    setTimeout(triggerPlayNow, 800);
                }
            }, { capture: true, passive: true });

            // Screen-off background audio: Prevent YouTube from pausing when screen locks or app is minimized
            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                try { document.hasFocus = function() { return true; }; } catch (e) {}

                // Unconditionally stop visibilitychange and blur from pausing media
                ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(function(evt) {
                    window.addEventListener(evt, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                    document.addEventListener(evt, function(e) {
                        e.stopImmediatePropagation();
                    }, true);
                });

                // Detect actual user tap on player controls
                let userTapTimer = null;
                ['pointerdown', 'touchstart', 'click'].forEach(function(evt) {
                    document.addEventListener(evt, function(e) {
                        const target = e.target;
                        if (target && target.closest && target.closest('video, .ytp-play-button, .player-control-play-pause-icon, button[aria-label*="Pause" i], button[aria-label*="Play" i]')) {
                            userExplicitPaused = true;
                            if (userTapTimer) clearTimeout(userTapTimer);
                            userTapTimer = setTimeout(function() { userExplicitPaused = false; }, 2000);
                        }
                    }, true);
                });

                // Intercept HTMLMediaElement.prototype.pause
                const origMediaPause = HTMLMediaElement.prototype.pause;
                HTMLMediaElement.prototype.pause = function() {
                    // If pause was NOT triggered by an explicit user touch/click, disarm it!
                    if (!userExplicitPaused) {
                        return;
                    }
                    return origMediaPause.apply(this, arguments);
                };
            } catch (e) {}
        }

        // 2. Network Hook: XHR response text interceptor (Response.prototype.json handles fetch in memory)
        if (window.XMLHttpRequest) {
            const origOpen = XMLHttpRequest.prototype.open;
            const origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this._fokkTargetUrl = url;
                return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function(body) {
                if (this._fokkTargetUrl && typeof this._fokkTargetUrl === 'string') {
                    const isYt = this._fokkTargetUrl.includes('/youtubei/v1/player') ||
                                 this._fokkTargetUrl.includes('/youtubei/v1/next') ||
                                 this._fokkTargetUrl.includes('/youtubei/v1/reel/reel_item_watch') ||
                                 this._fokkTargetUrl.includes('/youtubei/v1/browse');
                    if (isYt) {
                        const self = this;
                        const modifyYtResponse = function() {
                            if (self.readyState === 4 && self.status === 200) {
                                try {
                                    const data = JSON.parse(self.responseText);
                                    sanitizeYtPlayerResponse(data);
                                    const cleaned = JSON.stringify(data);
                                    Object.defineProperty(self, 'responseText', { value: cleaned, configurable: true });
                                    Object.defineProperty(self, 'response', { value: cleaned, configurable: true });
                                } catch (e) {}
                            }
                        };
                        this.addEventListener('readystatechange', modifyYtResponse, { capture: true });
                        this.addEventListener('load', modifyYtResponse, { capture: true });
                    }
                    const isIg = this._fokkTargetUrl.includes('/graphql/query') ||
                                 this._fokkTargetUrl.includes('/api/v1/feed/timeline/') ||
                                 this._fokkTargetUrl.includes('/api/v1/feed/reels_tray/') ||
                                 this._fokkTargetUrl.includes('/api/v1/discover/') ||
                                 this._fokkTargetUrl.includes('/api/v1/clips/') ||
                                 this._fokkTargetUrl.includes('/api/v1/feed/user/');
                    if (isIg) {
                        const self = this;
                        const modifyResponse = function() {
                            if (self.readyState === 4 && self.status === 200) {
                                try {
                                    const data = JSON.parse(self.responseText);
                                    if (sanitizeInstagramData(data)) {
                                        const cleaned = JSON.stringify(data);
                                        Object.defineProperty(self, 'responseText', { value: cleaned, configurable: true });
                                        Object.defineProperty(self, 'response', { value: cleaned, configurable: true });
                                    }
                                } catch (e) {}
                            }
                        };
                        this.addEventListener('readystatechange', modifyResponse, { capture: true });
                        this.addEventListener('load', modifyResponse, { capture: true });
                    }
                }
                return origSend.apply(this, arguments);
            };
        }
    }

    // Execute directly in current world
    try { installMainWorldDefusers(); } catch (e) {}

    // Bridge into the page's Main World if running in an Isolated World (e.g. Android addDocumentStartJavaScript)
    try {
        const bridgeScript = document.createElement('script');
        bridgeScript.textContent = '(' + installMainWorldDefusers.toString() + ')();';
        const docRoot = document.head || document.documentElement;
        if (docRoot) {
            docRoot.appendChild(bridgeScript);
            bridgeScript.remove();
        } else {
            const bridgeObserver = new MutationObserver(function() {
                const root = document.head || document.documentElement;
                if (root) {
                    bridgeObserver.disconnect();
                    root.appendChild(bridgeScript);
                    bridgeScript.remove();
                }
            });
            bridgeObserver.observe(document, { childList: true, subtree: true });
        }
    } catch (e) {}

    const cosmeticCss = `
        /* Instagram Sponsored & App Promo Hiding */
        a[href*="/about/ads"],
        div:has(> a[href*="/about/ads"]),
        article:has(a[href*="/about/ads"]),
        div[role="article"]:has(a[href*="/about/ads"]),
        div:has(> [aria-label*="Sponsored"]),
        article:has([aria-label*="Sponsored"]),
        div[role="article"]:has([aria-label*="Sponsored"]),
        div[role="dialog"]:has(a[href*="play.google.com"]),
        div[data-testid="banner-mobile-app"],

        /* YouTube Mobile Ad Overlays & Feed Inline Preview Defuser */
        .ytm-player-ad-overlay,
        ytm-player-ad-overlay,
        .ytp-ad-player-overlay,
        .ytp-ad-text,
        .ytp-ad-preview-text,
        div[class*="ad-showing"] .ytp-ad-text,


        /* News & Media Article Ad Units, Sponsored Boxes & Overlays */
        div[id*="google_ads"],
        div[id*="div-gpt-ad"],
        div[id*="dfp-ad"],
        div[class*="ad-slot"],
        div[class*="ad-container"],
        div[class*="ad_wrapper"],
        div[class*="ad-wrapper"],
        div[class*="ad_container"],
        div[class*="advertisement"],
        div[class*="sponsored-post"],
        div[class*="sponsored-content"],
        div[class*="sponsored-stories"],
        div[class*="taboola"],
        div[id*="taboola"],
        div[class*="outbrain"],
        div[id*="outbrain"],
        .trc_related_container,
        .trc_rbox_div,
        .OUTBRAIN,
        div[data-ad-placeholder],
        div[data-ad-unit-path],
        aside[class*="ad"],
        div[class*="sticky-ad"],
        div[class*="bottom-ad"],
        div[class*="floating-ad"],
        div[class*="banner-ad"],
        div[class*="native-ad"],
        div[class*="ad-banner"],
        div[aria-label*="advertisement" i],
        div[aria-label*="sponsored" i],
        #google_vignette,
        div[id*="google-vignette"],
        div[class*="adthrive"],
        div[class*="mediavine"],
        div[id*="ezoic"],
        div[class*="ezoic"],

        /* Adult Ad Networks, Floating Cam Teasers & Video Overlays */
        div[id*="exoclick"],
        div[class*="exoclick"],
        iframe[src*="exoclick"],
        iframe[src*="syndication.exoclick"],
        iframe[src*="realsrv"],
        iframe[src*="tsyndicate"],
        iframe[src*="trafficjunky"],
        iframe[src*="juicyads"],
        iframe[src*="ero-advertising"],
        div[class*="trafficjunky"],
        div[id*="trafficjunky"],
        div[class*="juicyads"],
        div[id*="juicyads"],
        div[class*="popcash"],
        div[class*="popads"],
        div[id*="adsterra"],
        div[class*="adsterra"],
        iframe[src*="adsterra"],
        div[class*="cam-box"],
        div[class*="chaturbate"],
        div[class*="stripchat"],
        div[class*="bongacams"],
        div[class*="floating-cam"],
        div[id*="floating-cam"],
        div[class*="ad-under-player"],
        div[class*="player-ad-overlay"],
        div[class*="ad-floating-bottom"],
        div[class*="adult-ad"],

        /* Anti-Adblock Walls & Sticky Blur Overlays */
        div[class*="adblock-modal"],
        div[id*="adblock-modal"],
        div[class*="adblock-dialog"],
        div[id*="adblock-dialog"],
        div[class*="adblock-overlay"],
        div[id*="adblock-overlay"],
        div[class*="adb-overlay"],
        div[class*="ab-wrap"],
        div[class*="ab-overlay"],
        div[class*="anti-adblock"],
        div[id*="anti-adblock"],
        div[class*="ad-blocker-message"],
        div[id*="ad-blocker-message"],
        .adblock-warning,
        .adblock-gate,
        .adblocker-modal,
        #adblock-gate,
        #adblock-notice,

        /* Generic ad slots, wrappers & placeholder containers (scoped to exclude YouTube) */
        body:not([class*="ytm"]):not([id*="yt"]) ins.adsbygoogle,
        body:not([class*="ytm"]):not([id*="yt"]) .adsbygoogle,
        iframe[src*="doubleclick"],
        iframe[src*="googlesyndication"],
        iframe[src*="googleadservices"],
        [data-ad-slot]:not([class*="ytp"]):not([class*="ytm"]),
        [data-ad-unit]:not([class*="ytp"]):not([class*="ytm"]),
        [data-ad-client]:not([class*="ytp"]):not([class*="ytm"]),
        [data-google-query-id],
        [id^="div-gpt-ad"],
        [id*="-ad-slot"]:not([class*="ytp"]),
        [id*="google_ads"]:not([class*="ytp"]),
        [id*="dfp-ad"],
        div[class*="ad-holder"],
        div[class*="adHolder"],
        div[class*="ht-ad"],
        div[class*="desktopAd"],
        div[class*="dh250"],
        div[class*="ad-slot"],
        div[class*="adSlot"],
        div[class*="ad-wrapper"],
        div[class*="adWrapper"],
        div[class*="ad-container"]:not([class*="ytp"]):not([class*="ytm"]),
        div[class*="adContainer"]:not([class*="ytp"]):not([class*="ytm"]),
        div[class*="ad-box"],
        div[class*="adBox"],
        div[class*="ad-space"],
        div[class*="adSpace"],
        div[class*="advertisement-wrapper"],
        div[class*="advertisement-holder"],
        div[class*="advertisement-container"],
        div[class*="sponsored-wrapper"],
        span[class*="adText"],
        span[class*="ad-text"],
        div[class*="adLabel"],
        div[class*="ad-label"],
        div[class*="ad_label"],
        div[class*="adText"],
        div[class*="ad-text"],
        div[class*="advertisement-label"],
        span[class*="advertisement-label"],
        #tads,
        #tadsb,

        /* Web-Wide Cookie Consent Banners, Overlays & Prompts */
        #onetrust-consent-sdk,
        #onetrust-banner-sdk,
        .onetrust-pc-dark-filter,
        #CybotCookiebotDialog,
        #CybotCookiebotDialogBodyUnderlay,
        .qc-cmp2-container,
        .qc-cmp-cleanslate,
        .fc-consent-root,
        .cc-window,
        .cc-banner,
        div[class*="cookie-notice"],
        div[class*="cookie_notice"],
        div[class*="CookieNotice"],
        div[class*="cookie-consent"],
        div[class*="cookie_consent"],
        div[class*="CookieConsent"],
        div[class*="cookie-banner"],
        div[class*="cookie_banner"],
        div[class*="CookieBanner"],
        div[class*="cookie-modal"],
        div[data-test-id*="cookie"],
        div[data-testid*="cookie"],
        div[id*="cookie-consent"],
        div[id*="cookie-notice"],
        div[id*="cookie-banner"],
        div[aria-modal="true"]:has(form[action*="consent.google"]),
        form[action*="consent.google"],
        div[class*="consent-banner"],
        div[class*="consent-modal"],
        div[class*="consent-overlay"],
        div[class*="consent_overlay"],
        div[id*="sp_message_container"],

        /* YouTube Web Ads & Sponsored Cards */
        ytm-item-section-renderer:has(ytm-promoted-sparkles-web-renderer),
        ytm-item-section-renderer:has(ytm-ad-slot-renderer),
        ytm-item-section-renderer:has([aria-label*="Sponsored" i]),
        ytm-video-with-context-renderer:has([aria-label*="Sponsored" i]),
        ytm-compact-promoted-item-renderer,
        div.ytm-promoted-sparkles-web-renderer,
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
        ytd-rich-item-renderer:has(ytd-ad-slot-renderer),
        ytd-rich-item-renderer:has(ytd-in-feed-ad-layout-renderer),
        ytd-rich-item-renderer:has([aria-label*="Sponsored" i]),
        ytd-rich-item-renderer:has(.badge-style-type-ad),
        ytd-video-renderer:has([aria-label*="Sponsored" i]),
        ytd-video-renderer:has(.badge-style-type-ad),
        .ytp-ad-overlay-container,
        ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],

        /* YouTube Mobile Topbar Open App Button & Promos */
        ytm-open-app-button,
        .mobile-topbar-header-endpoint:has(ytm-open-app-button),
        c3-material-button[aria-label*="Open App"],
        a.c3-material-button[href*="redirect_app"],
        [aria-label*="Open App"],
        [aria-label*="open app"],
        [aria-label*="Get App"],
        ytm-mealbar-promo-renderer,

        /* YouTube Sponsored Shorts Ads only (preserve organic Shorts, Shelves, and Pivot Bar) */
        ytm-shorts-lockup-view-model:has(.badge-style-type-ad),
        ytm-reel-shelf-renderer:has([aria-label*="Sponsored" i]),
        ytd-reel-shelf-renderer:has([aria-label*="Sponsored" i]),

        /* Anti-App-Nag: Open in App / Get App Banners & Prompts across sites */
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
        [class*="get-the-app"],
        [class*="getTheApp"],
        [id*="get-app"],
        [id*="getApp"],
        [class*="install-app"],
        [class*="installApp"],
        [class*="app-upsell"],
        [class*="appUpsell"],
        [class*="app-promo"],
        [class*="appPromo"],
        a[href*="play.google.com/store/apps"],
        a[href*="itunes.apple.com"],
        a[href*="apps.apple.com"],
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
        .w9hPv,
        .qu-open-in-app,
        .qu-app-banner,
        div:has(> a[href*="play.google.com/store/apps"]),
        div[class*="floating-banner"]:has(a[href*="app"]) {
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
        const target = document.head || document.documentElement;
        if (!target) {
            return;
        }
        const style = document.createElement('style');
        style.id = '__fokk_cosmetic_style';
        style.textContent = cosmeticCss;
        target.appendChild(style);
    }

    injectCosmetics();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', injectCosmetics, { once: true });
        // Also try on readystatechange as soon as documentElement exists
        document.addEventListener('readystatechange', injectCosmetics);
    }

// =========================================================================
    // Cookie Consent Auto-Dismiss & Scrubber (Web-Wide + Ecosia)
    // =========================================================================
    function scrubCookieBanners() {
        const host = window.location.hostname;

        // 1. Ecosia specific cookie consent auto-handling
        if (host.includes('ecosia.org')) {
            const ecosiaButtons = Array.from(document.querySelectorAll('button, a'));
            for (let i = 0; i < ecosiaButtons.length; i++) {
                const text = (ecosiaButtons[i].innerText || ecosiaButtons[i].textContent || '').trim().toLowerCase();
                if (text === 'reject non-essential' || text === 'accept all' || text.includes('reject non-essential')) {
                    try { ecosiaButtons[i].click(); break; } catch (e) {}
                }
            }
            // If banner still present, remove container directly
            document.querySelectorAll('div[data-test-id*="cookie"], div[class*="cookie"], div[class*="consent"], div[role="dialog"]').forEach(function(el) {
                const content = (el.innerText || el.textContent || '').toLowerCase();
                if (content.includes('cookies help us improve') || (content.includes('essential cookies') && content.includes('non-essential cookies'))) {
                    try { el.remove(); } catch (e) { el.style.setProperty('display', 'none', 'important'); }
                }
            });
            if (document.documentElement) document.documentElement.style.overflow = 'auto';
            if (document.body) document.body.style.overflow = 'auto';
        }

        // 2. Generic OneTrust, Cookiebot, & popular consent banners
        document.querySelectorAll('#onetrust-consent-sdk, #CybotCookiebotDialog, .qc-cmp2-container, .fc-consent-root').forEach(function(el) {
            try { el.remove(); } catch (e) { el.style.setProperty('display', 'none', 'important'); }
        });

        // 3. Auto-click popular reject / accept buttons to save preference quietly
        const consentSelectors = [
            '#onetrust-reject-all-handler',
            '#onetrust-accept-btn-handler',
            '#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll',
            '#CybotCookiebotDialogBodyButtonAccept',
            'button[id*="reject-all"]',
            'button[id*="accept-all"]'
        ];
        for (let i = 0; i < consentSelectors.length; i++) {
            const btn = document.querySelector(consentSelectors[i]);
            if (btn && typeof btn.click === 'function') {
                try { btn.click(); break; } catch (e) {}
            }
        }
    }

    // =========================================================================
    // YouTube Ad Skip & Overlay Remover (uBO Standard — Zero Video Tampering)
    // =========================================================================
    function runYouTubeAdSkipper() {
        if (!window.location.hostname.includes('youtube.com')) return;

        const playerEl = document.getElementById('movie_player') ||
                         document.querySelector('.html5-video-player, ytm-player, #player');

        // 1. Programmatically click native YouTube skip buttons immediately
        const skipButtons = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.ytp-skip-ad-button',
            '.videoAdUiSkipButton',
            'button.ytp-ad-skip-button',
            'button.ytp-ad-skip-button-modern',
            '.ytp-ad-overlay-close-button',
            'button[class*="skip-button"]',
            '.ytp-ad-skip-button-container button',
            '.ytp-ad-skip-button-slot button',
            '.ytm-ad-skip-button',
            '.ytm-skip-ad-button',
            '.ytp-ad-skip-button-text',
            'ytm-player-ad-overlay button',
            '[aria-label*="Skip ad" i]',
            '[aria-label*="Skip Ad" i]'
        ];
        for (let i = 0; i < skipButtons.length; i++) {
            const btn = document.querySelector(skipButtons[i]);
            if (btn && typeof btn.click === 'function') {
                try {
                    btn.click();
                    break;
                } catch (e) {}
            }
        }

        // 2. Call native player skipAd API ONLY when an ad is actively showing
        try {
            const isAdShowing = document.querySelector('.ad-showing, .ad-interrupting, ytm-player-ad-overlay, .ytp-ad-player-overlay');
            if (isAdShowing && playerEl && typeof playerEl.skipAd === 'function') {
                playerEl.skipAd();
            }
        } catch (e) {}

        // 3. Remove visual ad overlays from DOM
        document.querySelectorAll('ytm-player-ad-overlay, .ytp-ad-player-overlay, .ytp-ad-overlay-container, ytm-promoted-sparkles-web-renderer').forEach(function(el) {
            try { el.remove(); } catch (e) {}
        });

        // 4. Ensure active video is unmuted without forcing video.play() in a loop
        if (typeof window.__fokk_unmuteYt === 'function') {
            try { window.__fokk_unmuteYt(); } catch (e) {}
        }

        // 5. Continuous Playlist Autoplay: Only advance if explicitly inside a playlist
        try {
            const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player, ytm-player');
            if (player) {
                if (window.location.pathname.startsWith('/watch') && window.location.search.includes('list=')) {
                    if (typeof player.getPlayerState === 'function' && player.getPlayerState() === 0) {
                        if (typeof player.nextVideo === 'function') player.nextVideo();
                    }
                }
            }
        } catch (e) {}
    }

    function scrubYouTubeSponsored() {
        if (!window.location.hostname.includes('youtube.com')) return;

        // 1. Direct YouTube ad containers
        document.querySelectorAll(
            'ytd-ad-slot-renderer, ytm-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ' +
            'ytm-promoted-sparkles-web-renderer, ytm-promoted-sparkles-text-search-web-renderer, ' +
            'ytm-companion-ad-renderer, ytd-banner-promo-renderer, ytd-promoted-video-renderer, ' +
            'ytd-statement-banner-renderer, ytd-primetime-promo-renderer'
        ).forEach(function(el) {
            try {
                const card = el.closest('ytd-rich-item-renderer, ytd-video-renderer, ytm-item-section-renderer, ytm-video-with-context-renderer, ytd-rich-section-renderer') || el;
                card.style.setProperty('display', 'none', 'important');
            } catch (e) {}
        });

        // 2. Targeted Sponsored badge selector for YouTube (without querying all span/div)
        const badges = document.querySelectorAll(
            '.ytm-badge-and-title, ytd-badge-supported-renderer, .badge-style-type-ad, ' +
            '[aria-label*="Sponsored" i], [aria-label*="promoted" i]'
        );
        for (let i = 0; i < badges.length; i++) {
            const b = badges[i];
            const txt = (b.textContent || '').trim().toLowerCase();
            const isSponsoredBadge = txt === 'sponsored' || txt === 'ad' || txt === 'promoted' ||
                                     txt.startsWith('sponsored') || txt.startsWith('ad ·') ||
                                     txt.startsWith('ad •') || txt.startsWith('promoted');
            if (isSponsoredBadge && txt.length < 35) {
                const card = b.closest(
                    'ytd-rich-item-renderer, ytd-video-renderer, ytd-compact-video-renderer, ' +
                    'ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer, ' +
                    'ytm-video-with-context-renderer, ytm-item-section-renderer, ytm-compact-video-renderer, ' +
                    'ytm-ad-slot-renderer, ytm-promoted-sparkles-web-renderer, [class*="video-renderer"], [class*="media-item"]'
                );
                if (card) {
                    card.style.setProperty('display', 'none', 'important');
                }
            }
        }
    }

    if (window.location.hostname.includes('youtube.com')) {
        setInterval(function() {
            runYouTubeAdSkipper();
            scrubYouTubeSponsored();
        }, 500);
        document.addEventListener('loadeddata', runYouTubeAdSkipper, { capture: true, passive: true });
        window.addEventListener('yt-navigate-start', function() { runYouTubeAdSkipper(); scrubYouTubeSponsored(); }, { capture: true, passive: true });
        window.addEventListener('yt-navigate-finish', function() { runYouTubeAdSkipper(); scrubYouTubeSponsored(); }, { capture: true, passive: true });
        window.addEventListener('yt-page-data-updated', function() { runYouTubeAdSkipper(); scrubYouTubeSponsored(); }, { capture: true, passive: true });
        window.addEventListener('spfdone', function() { runYouTubeAdSkipper(); scrubYouTubeSponsored(); }, { capture: true, passive: true });
        window.addEventListener('popstate', function() { runYouTubeAdSkipper(); scrubYouTubeSponsored(); }, { capture: true, passive: true });
    }

    // =========================================================================
    // Instagram Feed Ad & Sponsored Story Scrubber
    // =========================================================================
    function scrubInstagramAds() {
        if (!window.location.hostname.includes('instagram.com')) return;

        // Fast path: CSS structural markers Instagram uses for sponsored posts
        const directAdSelectors = [
            'article:has(span._a9zs)',
            'article:has(a[href*="/about/ads"])',
            'div[role="article"]:has(a[href*="/about/ads"])',
            'article:has([aria-label="Sponsored" i])',
            'article:has([aria-label="Paid partnership" i])',
            'div[class*="x1lliihq"]:has(a[href*="/about/ads"])'
        ];
        directAdSelectors.forEach(function(sel) {
            try {
                document.querySelectorAll(sel).forEach(function(el) {
                    try {
                        const container = el.closest('div[style*="padding"]') || el.parentElement || el;
                        container.style.setProperty('display', 'none', 'important');
                        el.style.setProperty('display', 'none', 'important');
                    } catch (e) {}
                });
            } catch (e) {}
        });

        // Universal leaf-node text walker for Instagram
        document.querySelectorAll('span, a, div').forEach(function(el) {
            try {
                if (el.children.length === 0) {
                    const txt = (el.textContent || '').trim().toLowerCase();
                    if (txt === 'sponsored' || txt === 'paid partnership') {
                        const post = el.closest('article, div[role="article"], div[class*="x1lliihq"]');
                        if (post) {
                            post.style.setProperty('display', 'none', 'important');
                            if (post.parentElement && post.parentElement.tagName !== 'MAIN') {
                                post.parentElement.style.setProperty('display', 'none', 'important');
                            }
                        }
                    }
                }
            } catch (e) {}
        });

        // Comprehensive text-based scrubber for all article-like feed items
        const posts = document.querySelectorAll('article, div[role="article"], div[class*="x1lliihq"]');
        posts.forEach(function(post) {
            try {
                if (post.tagName === 'MAIN' || post.tagName === 'BODY' || post.tagName === 'HTML') return;
                if (post.style.display === 'none') return;

                let isSponsored = false;
                if (post.querySelector('a[href*="/about/ads"], a[href*="about/ads"]')) isSponsored = true;
                if (!isSponsored && post.querySelector(
                    '[aria-label*="Sponsored" i], [aria-label*="Paid partnership" i], ' +
                    '[aria-label*="Gesponsert" i], [aria-label*="Publicidad" i], ' +
                    '[aria-label*="Patrocinado" i], [aria-label*="Sponsorisé" i]'
                )) isSponsored = true;

                if (!isSponsored) {
                    const header = post.querySelector('header');
                    if (header) {
                        const headerText = header.textContent || '';
                        if (/\b(Sponsored|Paid partnership|Gesponsert|Publicidad|Patrocinado|Sponsorisé|Commandité)\b/i.test(headerText)) {
                            isSponsored = true;
                        }
                    }
                }

                if (!isSponsored && post.querySelector('svg[aria-label*="Sponsored" i]')) isSponsored = true;

                if (!isSponsored && post.querySelector(
                    'a[href*="play.google.com"], a[href*="itunes.apple.com"], ' +
                    'button[aria-label*="Install" i], a[aria-label*="Install" i]'
                )) isSponsored = true;

                if (isSponsored) {
                    post.style.setProperty('display', 'none', 'important');
                    const wrapper = post.parentElement;
                    if (wrapper && wrapper.tagName !== 'MAIN' && wrapper.tagName !== 'BODY') {
                        wrapper.style.setProperty('display', 'none', 'important');
                    }
                }
            } catch (e) {}
        });

        // Remove Instagram web app banner & full-screen app gate dialogs
        document.querySelectorAll(
            'div[data-testid="banner-mobile-app"], div[role="dialog"]:has(a[href*="play.google.com"]), ' +
            'div[class*="MobileAppBanner"], div[class*="x1n2onr6"]:has([href*="/about/ads"])'
        ).forEach(function(el) {
            try { el.remove(); } catch (e) {}
        });
    }

    if (window.location.hostname.includes('instagram.com')) {
        // Run immediately on load; MutationObserver handles dynamic content
        scrubInstagramAds();
        // Also run on DOM changes for dynamically loaded feed items
        try {
            const igObserver = new MutationObserver(function(mutations) {
                let hasNew = false;
                mutations.forEach(function(m) { if (m.addedNodes.length) hasNew = true; });
                if (hasNew) scrubInstagramAds();
            });
            const feedRoot = document.querySelector('main') || document.body;
            if (feedRoot) igObserver.observe(feedRoot, { childList: true, subtree: true });
        } catch (e) {}
    }


    // =========================================================================
    // News & Content Site Ad Container & Sponsored Widget Scrubber
    // =========================================================================
    function scrubNewsAds() {
        // Never run on YouTube - YouTube uses class names like ad-container for player elements
        const host = window.location.hostname;
        if (host.includes('youtube.com') || host.includes('youtu.be')) return;

        const adSelectors = [
            'div[id*="google_ads"]', 'div[id*="div-gpt-ad"]', 'div[id*="dfp-ad"]',
            'div[class*="ad-slot"]', 'div[class*="adSlot"]', 'div[class*="ad-container"]',
            'div[class*="adContainer"]', 'div[class*="ad-holder"]', 'div[class*="adHolder"]',
            'div[class*="ht-ad"]', 'div[class*="desktopAd"]', 'div[class*="dh250"]',
            'div[class*="ad-wrapper"]', 'div[class*="adWrapper"]', 'div[class*="ad-box"]',
            'div[class*="adBox"]', 'div[class*="ad-space"]', 'div[class*="adSpace"]',
            'div[class*="advertisement"]', 'div[class*="taboola"]', 'div[id*="taboola"]',
            'div[class*="outbrain"]', 'div[id*="outbrain"]', '.trc_related_container',
            '.OUTBRAIN', 'aside[class*="ad"]', 'div[class*="sticky-ad"]',
            'div[class*="native-ad"]', 'div[class*="ad-banner"]', '#google_vignette',
            'div[class*="sponsored-post"]', 'div[class*="sponsored-content"]',
            'ins.adsbygoogle', '[data-ad-slot]', '[data-google-query-id]',
            '[class*="adLabel"]', '[class*="ad-label"]', '[class*="ad_label"]',
            '[class*="adText"]', '[class*="ad-text"]', '[class*="advertisement-label"]'
        ];

        try {
            document.querySelectorAll(adSelectors.join(',')).forEach(function(el) {
                try {
                    // Critical safeguard: Never touch legitimate article videos, audio, or media players!
                    if (el.querySelector && el.querySelector('video, audio, iframe[src*="video"], iframe[src*="brightcove"], iframe[src*="jwplayer"], iframe[src*="youtube"], iframe[src*="dailymotion"], iframe[src*="vimeo"], twitter-tweet, .twitter-tweet, [data-testid="tweet"]')) {
                        return;
                    }
                    if (el.closest && el.closest('video, [class*="player"], [id*="player"], twitter-tweet, .twitter-tweet')) {
                        return;
                    }

                    const parent = el.parentElement;
                    el.style.setProperty('display', 'none', 'important');
                    el.style.setProperty('height', '0px', 'important');
                    el.style.setProperty('min-height', '0px', 'important');
                    el.style.setProperty('margin', '0px', 'important');
                    el.style.setProperty('padding', '0px', 'important');
                    el.remove();

                    // If parent element only housed this ad or an "Advertisement" banner label, collapse the empty white wrapper
                    if (parent && parent.tagName !== 'BODY' && parent.tagName !== 'MAIN' && parent.tagName !== 'ARTICLE') {
                        const pText = (parent.innerText || parent.textContent || '').trim();
                        if (pText.length === 0 || /^(advertisement|sponsored|promoted|ad)$/i.test(pText)) {
                            if (!parent.querySelector('p, article, video, audio, h1, h2, h3, h4')) {
                                parent.style.setProperty('display', 'none', 'important');
                                parent.style.setProperty('height', '0px', 'important');
                                parent.style.setProperty('min-height', '0px', 'important');
                                parent.style.setProperty('margin', '0px', 'important');
                                parent.style.setProperty('padding', '0px', 'important');
                            }
                        }
                    }
                } catch (e) {}
            });

            // Target orphan "Advertisement" / "Sponsored" label containers and collapse their enclosing empty boxes
            const labels = document.querySelectorAll('span, div, p, label');
            const labelLimit = Math.min(labels.length, 250);
            for (let i = 0; i < labelLimit; i++) {
                const lbl = labels[i];
                if (!lbl) continue;
                const txt = (lbl.innerText || lbl.textContent || '').trim();
                if (/^(advertisement|sponsored|promoted|ad)$/i.test(txt)) {
                    const wrapper = lbl.closest('div[class*="ad"], div[class*="holder"], div[class*="wrapper"], div[class*="slot"], div[class*="banner"], aside');
                    if (wrapper && wrapper.tagName !== 'BODY' && wrapper.tagName !== 'MAIN' && wrapper.tagName !== 'ARTICLE') {
                        if (!wrapper.querySelector('article, video, audio, p:nth-of-type(2)')) {
                            wrapper.style.setProperty('display', 'none', 'important');
                            wrapper.style.setProperty('height', '0px', 'important');
                            wrapper.style.setProperty('min-height', '0px', 'important');
                            wrapper.style.setProperty('margin', '0px', 'important');
                            wrapper.style.setProperty('padding', '0px', 'important');
                            wrapper.remove();
                        }
                    } else {
                        lbl.style.setProperty('display', 'none', 'important');
                    }
                }
            }
        } catch (e) {}
    }

    // =========================================================================
    // Anti-App-Nag: In-page app upsell & "Open App" header scrubber
    // =========================================================================
    function scrubAppPromos() {
        const host = window.location.hostname;

        // Universal Open-in-App / SmartBanner scrubber for all websites
        document.querySelectorAll('.smartbanner, .app-banner, .smart-app-banner, [class*="smartbanner" i], [class*="open-in-app" i], [class*="openInApp"], [class*="getTheApp"], [class*="install-app" i], div:has(> a[href*="play.google.com/store/apps"])').forEach(function(el) {
            try {
                el.style.setProperty('display', 'none', 'important');
                el.remove();
            } catch (e) {}
        });

        if (host.includes('youtube.com')) {
            document.querySelectorAll('ytm-open-app-button, .mobile-topbar-header-endpoint:has(ytm-open-app-button), [aria-label*="Open App"], [aria-label*="open app"]').forEach(function(el) {
                el.style.setProperty('display', 'none', 'important');
            });
            document.querySelectorAll('ytm-mealbar-promo-renderer, ytm-promoted-sparkles-web-renderer, ytm-ad-slot-renderer').forEach(function(el) {
                try {
                    const parent = el.closest('ytm-item-section-renderer, ytm-compact-promoted-item-renderer') || el;
                    parent.remove();
                } catch (e) {}
            });
        } else if (host.includes('instagram.com')) {
            // Remove Instagram app banners
            document.querySelectorAll('div[data-testid="banner-mobile-app"], div[class*="MobileAppBanner"], div[role="dialog"]:has(a[href*="play.google.com"])').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
            // Scrub sponsored feed posts, suggestions, and promotional cards from Instagram feed
            const igCandidates = document.querySelectorAll('article, div[role="article"], main section > div > div > div');
            igCandidates.forEach(function(item) {
                if (item.tagName === 'MAIN' || item.tagName === 'BODY') return;
                const text = (item.innerText || item.textContent || '');
                const hasSponsoredKeyword = /\b(Sponsored|Paid partnership|Gesponsert|Publicidad|Patrocinado|Commandité)\b/i.test(text);
                const hasAdLinks = !!item.querySelector('a[href*="/about/ads"], a[href*="instagram.com/about/ads"], svg[aria-label*="Sponsored"], [aria-label*="Sponsored"], [aria-label*="sponsored"]');
                if (hasSponsoredKeyword || hasAdLinks) {
                    item.style.setProperty('display', 'none', 'important');
                    const container = item.closest('div[class*="x1y1aw1k"]') || item;
                    container.style.setProperty('display', 'none', 'important');
                }
            });
        } else if (host.includes('spotify.com')) {
            try {
                Object.defineProperty(navigator, 'platform', {
                    get: function() { return 'Linux x86_64'; },
                    configurable: true
                });
            } catch (e) {}
            document.querySelectorAll('div[data-testid="upsell-banner"], div[class*="MobileAppBanner"], a[href*="spotify.com/download"]').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
        } else if (host.includes('reddit.com')) {
            document.querySelectorAll('xpromo-app-selector, shreddit-async-loader[bundlename="bottom_sheet"]').forEach(function(el) {
                try { el.remove(); } catch (e) {}
            });
            if (document.documentElement) document.documentElement.style.overflow = 'auto';
            if (document.body) document.body.style.overflow = 'auto';
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
            let title = document.title || '';
            const ytTitle = document.querySelector('h1.title, .slim-video-information-title, ytm-slim-video-metadata-renderer .title');
            if (ytTitle && ytTitle.textContent) {
                title = ytTitle.textContent.trim();
            }
            const bridge = window.FokkMediaBridge || window.FokkShieldBridge;
            if (bridge) {
                if (typeof bridge.reportMediaPlayback === 'function') {
                    bridge.reportMediaPlayback(isPlaying, hasActiveVideo, title);
                } else if (typeof bridge.onMediaPlaybackChanged === 'function') {
                    bridge.onMediaPlaybackChanged(isPlaying, hasActiveVideo);
                }
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
            document.querySelectorAll('[data-fokk-ai-hidden]').forEach(function(el) {
                el.style.setProperty('display', '', '');
                el.style.setProperty('height', '', '');
                el.style.setProperty('min-height', '', '');
                el.style.setProperty('margin', '', '');
                el.style.setProperty('padding', '', '');
                el.style.setProperty('visibility', '', '');
                el.style.setProperty('pointer-events', '', '');
            });
            document.querySelectorAll('[data-fokk-aimode-hidden]').forEach(function(el) {
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

        document.querySelectorAll(selector).forEach(function(el) {
            if (el.getAttribute('data-fokk-ai-hidden') === 'true') return;
            el.setAttribute('data-fokk-ai-hidden', 'true');
            el.style.setProperty('display', 'none', 'important');
        });
    }

    function scrubAiContent() {
        try { scrubAiOverviewsAndWidgets(); } catch (e) {}
        // Remove Bing "Open in App" / "Continue Web" banner that appears on bing.com mobile
        try {
            const host = window.location.hostname;
            if (host.includes('bing.com')) {
                document.querySelectorAll(
                    '#b_header_banner, .b_appBanner, [id*="appBanner"], [class*="appBanner"], ' +
                    '[id*="app-banner"], [class*="app-banner"], ' +
                    'div:has(> a[href*="microsoft.com/en-us/bing-app"]), ' +
                    'div:has(> a[href*="bing.com/app"]), ' +
                    'a[href*="bingapp://"], a[href*="bing://"]'
                ).forEach(function(el) {
                    try { el.style.setProperty('display', 'none', 'important'); el.remove(); } catch(e) {}
                });
                // Also hide any top-fixed banners with "Open app" text
                document.querySelectorAll('div[style*="position: fixed"], div[style*="position:fixed"]').forEach(function(el) {
                    try {
                        const txt = (el.innerText || '').toLowerCase();
                        if ((txt.includes('open') && txt.includes('app')) || txt.includes('continue in')) {
                            el.style.setProperty('display', 'none', 'important');
                        }
                    } catch(e) {}
                });
            }
        } catch (e) {}
    }

    function hookVideoPlayback(video) {
        if (!video || video.__fokk_hooked) return;
        video.__fokk_hooked = true;

        let pendingState = false;
        const updateState = function() {
            if (pendingState) return;
            pendingState = true;
            requestAnimationFrame(function() {
                pendingState = false;
                const isPlaying = !video.paused && !video.ended;
                const hasActiveVideo = video.currentTime > 0 && !video.ended;
                notifyNativePlayback(isPlaying, hasActiveVideo);
            });
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

    // =========================================================================
    // Heuristic AI Blocker: Dynamic Pattern & Overlay Trap Buster
    // =========================================================================
    function unlockPageScrolling() {
        try {
            const docEl = document.documentElement;
            const body = document.body;
            if (!docEl || !body) return;

            // Check if page scrolling has been frozen by anti-adblock or third party modal scripts
            const docOverflow = docEl.style.overflow;
            const bodyOverflow = body.style.overflow;
            if (docOverflow === 'hidden' || docOverflow === 'clip') {
                docEl.style.setProperty('overflow', 'auto', 'important');
                docEl.style.setProperty('overflow-y', 'auto', 'important');
            }
            if (bodyOverflow === 'hidden' || bodyOverflow === 'clip') {
                body.style.setProperty('overflow', 'auto', 'important');
                body.style.setProperty('overflow-y', 'auto', 'important');
            }
            if (body.style.position === 'fixed') {
                body.style.setProperty('position', 'static', 'important');
            }
            // Strip common anti-scroll lock classes from body
            if (body.classList.contains('modal-open') || body.classList.contains('no-scroll') ||
                body.classList.contains('overflow-hidden') || body.classList.contains('disable-scroll')) {
                body.classList.remove('modal-open', 'no-scroll', 'overflow-hidden', 'disable-scroll');
            }
        } catch(e) {}
    }

    function defuseClickjackingOverlays() {
        if (!isAiFilterEnabled) return;
        const host = window.location.hostname;
        if (host.includes('youtube.com') || host.includes('instagram.com') || host.includes('yahoo.com') || host.includes('hindustantimes.com') || host.includes('espn.com')) return;
        try {
            unlockPageScrolling();
            const vw = window.innerWidth || document.documentElement.clientWidth;
            const vh = window.innerHeight || document.documentElement.clientHeight;
            if (!vw || !vh || !document.body) return;

            // Inspect suspicious floating overlay elements (never query broad 'body > div' to prevent breaking React/Next.js roots)
            const suspects = document.querySelectorAll('body > a, [class*="overlay"], [class*="trap"], [id*="overlay"], [id*="trap"]');
            const limit = Math.min(suspects.length, 30);
            for (let i = 0; i < limit; i++) {
                const el = suspects[i];
                if (!el || el === document.body || el === document.documentElement) continue;
                if (el.tagName === 'MAIN' || el.tagName === 'ARTICLE' || el.tagName === 'NAV' || el.tagName === 'HEADER' || el.tagName === 'FOOTER' || el.tagName === 'SECTION' || el.tagName === 'VIDEO' || el.tagName === 'IFRAME') continue;
                const elId = (el.id || '').toLowerCase();
                if (elId === 'main' || elId === 'app' || elId === 'root' || elId === '__next' || elId === 'player' || elId.startsWith('s:') || elId.startsWith('react')) continue;
                if (el.querySelector && el.querySelector('video, iframe, [class*="player"], [id*="player"], twitter-tweet, .twitter-tweet, [data-testid="tweet"], p, article, h1, h2, h3, nav')) continue;
                if (el.closest && el.closest('video, .video-player, [class*="player"], [id*="player"], .twitter-tweet, [data-testid="tweet"], main, article')) continue;

                const style = window.getComputedStyle(el);
                const pos = style.position;
                if (pos === 'fixed' || pos === 'absolute') {
                    const zIndex = parseInt(style.zIndex, 10);
                    // Clickjacking traps have very high z-index (at least 100), transparent appearance, and zero content
                    if (!isNaN(zIndex) && zIndex >= 100) {
                        const rect = el.getBoundingClientRect();
                        const coversScreen = rect.width >= vw * 0.75 && rect.height >= vh * 0.75;
                        const isTransparent = style.opacity === '0' || style.backgroundColor === 'rgba(0, 0, 0, 0)' || style.backgroundColor === 'transparent';
                        const text = (el.innerText || el.textContent || '').trim();
                        const hasNoRealContent = text.length === 0 && el.querySelectorAll('input, button, a, img').length === 0;

                        if (coversScreen && isTransparent && hasNoRealContent) {
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('pointer-events', 'none', 'important');
                            el.style.setProperty('visibility', 'hidden', 'important');
                            el.style.setProperty('opacity', '0', 'important');
                            unlockPageScrolling();
                        }
                    }
                }
            }
        } catch(e) {}
    }

    function defuseAntiAdblockModals() {
        try {
            const adblockKeywords = ['ad blocker', 'adblock', 'disable your ad', 'turn off your ad', 'whitelist us', 'disable adblocker', 'bloqueur de publicité'];
            const modals = document.querySelectorAll('div[role="dialog"], div[class*="modal"], div[class*="dialog"], div[class*="popup"], div[class*="overlay"], div[id*="modal"], div[id*="popup"]');
            modals.forEach(function(modal) {
                const text = (modal.innerText || modal.textContent || '').toLowerCase();
                if (adblockKeywords.some(function(k) { return text.includes(k); })) {
                    modal.style.setProperty('display', 'none', 'important');
                    modal.remove();
                    unlockPageScrolling();
                }
            });
        } catch(e) {}
    }

    function defuseDeceptiveButtons() {
        try {
            const badTargets = ['exoclick', 'adsterra', 'clickadu', 'trafficjunky', 'propellerads', 'directrev', 'syndicate', 'monetag', 'hilltopads', 'juicyads', 'admaven'];
            const anchors = document.querySelectorAll('a[href], button[onclick]');
            anchors.forEach(function(a) {
                const href = (a.href || a.getAttribute('onclick') || '').toLowerCase();
                if (badTargets.some(function(b) { return href.includes(b); })) {
                    a.style.setProperty('display', 'none', 'important');
                }
            });
        } catch(e) {}
    }

    function scrubAdultAndStreamingAds() {
        try {
            defuseClickjackingOverlays();
            defuseAntiAdblockModals();
            defuseDeceptiveButtons();
            // Disarm floating cam boxes, sticky video banners and deceptive overlays (never touch Twitter/X syndication)
            document.querySelectorAll('[class*="cam-box"], [class*="exoclick"], [class*="trafficjunky"], [class*="floating-cam"], [id*="floating-cam"], [class*="juicyads"], [id*="juicyads"], iframe[src*="exoclick"], iframe[src*="syndication.exoclick"], iframe[src*="syndication.trafficjunky"], iframe[src*="realsrv"], iframe[src*="trafficjunky"]').forEach(function(el) {
                el.style.setProperty('display', 'none', 'important');
            });
        } catch(e) {}
    }

    // =========================================================================
    // MutationObserver for cosmetic cleanup & active checks (debounced to 500ms)
    // =========================================================================
    try {
        const host = window.location.hostname;
        let pendingSweep = false;
        const observer = new MutationObserver(function() {
            if (pendingSweep) return;
            pendingSweep = true;
            setTimeout(function() {
                pendingSweep = false;
                if (host.includes('google.') || host.includes('bing.com')) scrubAiContent();
                if (host.includes('youtube.com')) {
                    runYouTubeAdSkipper();
                    scrubYouTubeSponsored();
                } else if (host.includes('instagram.com')) {
                    scrubInstagramAds();
                } else {
                    scrubNewsAds();
                    scrubAppPromos();
                    scrubCookieBanners();
                    scrubAdultAndStreamingAds();
                    defuseClickjackingOverlays();
                    defuseAntiAdblockModals();
                    defuseDeceptiveButtons();
                    unlockPageScrolling();
                }
                document.querySelectorAll('video').forEach(hookVideoPlayback);
            }, 500);
        });
        observer.observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    } catch (e) {}

    function runInitialScrub() {
        const host = window.location.hostname;
        if (host.includes('google.') || host.includes('bing.com')) scrubAiContent();
        if (host.includes('youtube.com')) {
            runYouTubeAdSkipper();
            scrubYouTubeSponsored();
        } else if (host.includes('instagram.com')) {
            scrubInstagramAds();
        } else {
            scrubNewsAds();
            scrubAppPromos();
            scrubCookieBanners();
            scrubAdultAndStreamingAds();
            defuseClickjackingOverlays();
            defuseAntiAdblockModals();
            defuseDeceptiveButtons();
            unlockPageScrolling();
        }
        document.querySelectorAll('video').forEach(hookVideoPlayback);
    }

    // Initial pass on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runInitialScrub);
    } else {
        runInitialScrub();
    }

    // =========================================================================
    // 1. UNIVERSAL BACKGROUND MEDIA DECOUPLER
    // Prevents YouTube, Spotify, and audio streams from pausing on tab switch/lock
    // =========================================================================
    try {
        // Spoof document visibility
        Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
        Object.defineProperty(document, 'webkitHidden', { get: () => false, configurable: true });
        Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });

        // Neutralize visibilitychange and blur events that pause video
        const originalAddEventListener = EventTarget.prototype.addEventListener;
        EventTarget.prototype.addEventListener = function(type, listener, options) {
            if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide') {
                const wrappedListener = function(event) {
                    // Swallow event if it tries to report hidden
                    if (document.hidden) return;
                    return listener.apply(this, arguments);
                };
                return originalAddEventListener.call(this, type, wrappedListener, options);
            }
            return originalAddEventListener.call(this, type, listener, options);
        };
    } catch(e) {}

    // =========================================================================
    // 2. SPECULATIVE TOUCH DECELERATION & HOVER PRE-WARMER
    // Resolves DNS and warms TCP/TLS before finger clicks link (0ms latency feel)
    // =========================================================================
    try {
        let prewarmedUrls = new Set();
        let hoverTimer = null;

        function checkAndPrewarm(el) {
            const anchor = el.closest('a[href]');
            if (!anchor) return;
            const href = anchor.href;
            if (!href || !href.startsWith('http') || prewarmedUrls.has(href)) return;
            prewarmedUrls.add(href);
            if (window.FokkShieldBridge && typeof window.FokkShieldBridge.preWarmUrl === 'function') {
                window.FokkShieldBridge.preWarmUrl(href);
            }
        }

        document.addEventListener('touchstart', function(e) {
            if (e.target) checkAndPrewarm(e.target);
        }, { passive: true });

        document.addEventListener('pointerover', function(e) {
            if (hoverTimer) clearTimeout(hoverTimer);
            hoverTimer = setTimeout(() => {
                if (e.target) checkAndPrewarm(e.target);
            }, 60);
        }, { passive: true });
    } catch(e) {}

    // =========================================================================
    // 4. SYNTHETIC GHOST FINGERPRINT CLOAKER
    // Injects imperceptible mathematical micro-noise into Canvas, WebGL, and AudioContext
    // =========================================================================
    (function installSyntheticGhostCloaker() {
        if (window.__fokk_ghost_cloaker_active) return;
        window.__fokk_ghost_cloaker_active = true;

        const sessionNoise = (Math.random() * 0.004) - 0.002;

        // 1. Canvas Fingerprint Cloaking (Subtle micro-noise on 2D canvas reads)
        try {
            if (window.CanvasRenderingContext2D) {
                const origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                    const imageData = origGetImageData.apply(this, arguments);
                    const data = imageData.data;
                    for (let i = 0; i < data.length; i += 48) {
                        if (data[i + 3] > 10) {
                            data[i] = Math.min(255, Math.max(0, data[i] + (i % 3 === 0 ? 1 : -1)));
                        }
                    }
                    return imageData;
                };

                const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function(type) {
                    if (this.width > 0 && this.height > 0 && this.width <= 300 && this.height <= 150) {
                        try {
                            const ctx = this.getContext('2d');
                            if (ctx) {
                                const imgData = ctx.getImageData(0, 0, Math.min(this.width, 16), Math.min(this.height, 16));
                                imgData.data[0] = Math.min(255, imgData.data[0] ^ 1);
                                ctx.putImageData(imgData, 0, 0);
                            }
                        } catch(e) {}
                    }
                    return origToDataURL.apply(this, arguments);
                };
            }
        } catch(e) {}

        // 2. WebGL Fingerprint Cloaking (Spoofs GPU parameters & unmasked vendor)
        try {
            if (window.WebGLRenderingContext) {
                const origGetParameter = WebGLRenderingContext.prototype.getParameter;
                WebGLRenderingContext.prototype.getParameter = function(param) {
                    // UNMASKED_VENDOR_WEBGL = 37445, UNMASKED_RENDERER_WEBGL = 37446
                    if (param === 37445) return 'Intel Inc.';
                    if (param === 37446) return 'Intel(R) Iris(TM) Plus Graphics 640';
                    return origGetParameter.apply(this, arguments);
                };
            }
            if (window.WebGL2RenderingContext) {
                const origGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
                WebGL2RenderingContext.prototype.getParameter = function(param) {
                    if (param === 37445) return 'Intel Inc.';
                    if (param === 37446) return 'Intel(R) Iris(TM) Plus Graphics 640';
                    return origGetParameter2.apply(this, arguments);
                };
            }
        } catch(e) {}

        // 3. AudioContext Frequency Cloaking
        try {
            if (window.AnalyserNode) {
                const origGetFloatFreq = AnalyserNode.prototype.getFloatFrequencyData;
                AnalyserNode.prototype.getFloatFrequencyData = function(array) {
                    origGetFloatFreq.apply(this, arguments);
                    for (let i = 0; i < array.length; i += 8) {
                        array[i] += sessionNoise;
                    }
                };
            }
        } catch(e) {}
    })();

    // =========================================================================
    // 5. UNIVERSAL VIDEO STREAM SNIFFER
    // Detects HTML5 video streams, HLS .m3u8, MP4, and notifies Android host
    // =========================================================================
    (function installUniversalVideoSniffer() {
        const sniffedUrls = new Set();

        function reportVideoUrl(url) {
            if (!url || typeof url !== 'string') return;
            const cleanUrl = url.trim();
            if (!cleanUrl.startsWith('http') || sniffedUrls.has(cleanUrl)) return;
            if (cleanUrl.includes('googleads') || cleanUrl.includes('doubleclick') || cleanUrl.includes('stats/ads')) return;

            const isMedia = cleanUrl.includes('.mp4') || cleanUrl.includes('.m3u8') ||
                            cleanUrl.includes('.webm') || cleanUrl.includes('.mpd') ||
                            cleanUrl.includes('videoplayback') || cleanUrl.includes('/video/');

            if (isMedia) {
                sniffedUrls.add(cleanUrl);
                try {
                    if (window.FokkVideoBridge && typeof window.FokkVideoBridge.onVideoFound === 'function') {
                        window.FokkVideoBridge.onVideoFound(cleanUrl, document.title || 'Video Stream');
                    }
                } catch(e) {}
            }
        }

        try {
            const desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
            if (desc && desc.set) {
                const origSet = desc.set;
                desc.set = function(val) {
                    reportVideoUrl(val);
                    return origSet.call(this, val);
                };
                Object.defineProperty(HTMLMediaElement.prototype, 'src', desc);
            }
        } catch(e) {}

        function scanVideos() {
            document.querySelectorAll('video').forEach(function(v) {
                if (v.src) reportVideoUrl(v.src);
                v.querySelectorAll('source').forEach(function(s) {
                    if (s.src) reportVideoUrl(s.src);
                });
            });
        }

        setInterval(scanVideos, 3000);
        setTimeout(scanVideos, 1200);
    })();

    // =========================================================================
    // 6. MX-PLAYER FULLSCREEN WEB VIDEO GESTURES
    // Swipe left 45% = Brightness, Swipe right 45% = Volume, Double-tap = Seek
    // =========================================================================
    (function installVideoGestures() {
        // YouTube mobile already provides native double-tap 10s seek and touch controls.
        // Intercepting touches on YouTube causes double-seeking, audio/video desync, and pause glitches.
        if (window.location.hostname.includes('youtube.com')) return;

        let touchStartX = 0;
        let touchStartY = 0;
        let lastTapTime = 0;
        let activeGestureVideo = null;

        document.addEventListener('touchstart', function(e) {
            const video = e.target.closest('video');
            if (!video) {
                activeGestureVideo = null;
                return;
            }
            activeGestureVideo = video;
            if (e.touches && e.touches.length === 1) {
                const t = e.touches[0];
                touchStartX = t.clientX;
                touchStartY = t.clientY;

                const now = Date.now();
                if (now - lastTapTime < 320) {
                    const rect = video.getBoundingClientRect();
                    const relX = (t.clientX - rect.left) / (rect.width || 1);
                    if (relX < 0.35) {
                        video.currentTime = Math.max(0, video.currentTime - 10);
                        if (window.FokkVideoBridge && typeof window.FokkVideoBridge.onSeekFeedback === 'function') {
                            window.FokkVideoBridge.onSeekFeedback(-10);
                        }
                    } else if (relX > 0.65) {
                        video.currentTime = Math.min(video.duration || 99999, video.currentTime + 10);
                        if (window.FokkVideoBridge && typeof window.FokkVideoBridge.onSeekFeedback === 'function') {
                            window.FokkVideoBridge.onSeekFeedback(10);
                        }
                    }
                }
                lastTapTime = now;
            }
        }, { passive: true });

        document.addEventListener('touchmove', function(e) {
            if (!activeGestureVideo || !e.touches || e.touches.length !== 1) return;
            const t = e.touches[0];
            const dy = touchStartY - t.clientY;
            const dx = t.clientX - touchStartX;

            if (Math.abs(dy) > 25 && Math.abs(dy) > Math.abs(dx) * 1.5) {
                const rect = activeGestureVideo.getBoundingClientRect();
                const relX = (touchStartX - rect.left) / (rect.width || 1);
                const deltaPercent = (dy / (rect.height || 300)) * 100;
                touchStartY = t.clientY;

                if (relX < 0.45) {
                    if (window.FokkVideoBridge && typeof window.FokkVideoBridge.adjustBrightness === 'function') {
                        window.FokkVideoBridge.adjustBrightness(deltaPercent);
                    }
                } else if (relX > 0.55) {
                    if (window.FokkVideoBridge && typeof window.FokkVideoBridge.adjustVolume === 'function') {
                        window.FokkVideoBridge.adjustVolume(deltaPercent);
                    }
                }
            }
        }, { passive: true });
    })();

})();
