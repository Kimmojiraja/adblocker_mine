# Fokk Browser: product gaps and quality direction

This note records a product direction from the current Android implementation and public browser/platform research. It is not a claim that Fokk already provides the capabilities below.

## What users still need from browsers

1. **Privacy that explains itself.** Blocking cookies and known tracker domains does not stop browser fingerprinting. The W3C describes fingerprinting as a cross-origin tracking risk even when cookie controls prevent cross-site cookie access. The site panel reports cross-site requests observed by WebView, requests Fokk filtered, and capped host summaries. It states that WebView may omit requests and site-boundary matching is approximate; this is not a complete tracker audit and does not detect fingerprinting. The panel can clear Web Storage for the current HTTP(S) origin, while cookies and saved form suggestions remain. Browser history, open-tab restore data, saved-page metadata/text, per-site shield exceptions, and WebView profiles are excluded from Android cloud backup and device transfer; user-facing browser settings remain eligible to transfer.
2. **Permissions with context.** Permission prompts are often shown before users understand why a site needs access. Large-scale Chrome research has studied user sentiment and context-aware quieting of permission prompts. Fokk should ask only after a site action, show the requesting origin and purpose, and offer a durable per-site choice.
3. **Tabs people can recover.** Recent CHI work documents tab overload and difficulty finding previously visited pages. Fokk should improve tab grouping, search, and lightweight suspension before adding more background features.
4. **Private browsing with a precise promise.** Hiding local history is not the same as isolating cookies, storage, cache, or service workers. Private tabs now run in a separate WebView process and data directory on Android 9+; Android 8.1 and earlier do not offer this path, so Fokk disables private tabs there rather than silently sharing normal-tab data. A normal Back exit waits for cookie, Web Storage, cache, and WebView form/auth cleanup before finishing; a fresh private session also clears its profile before loading a page, covering interrupted teardown. Filtered-request counts stay in memory for the private session and do not update the persistent app-wide counter. Address-bar autocomplete is local-only by default; users can opt regular tabs into sending partial queries to DuckDuckGo, while private tabs never show regular-profile history or send partial queries. Submitting a search still sends it to the selected search engine. Android-managed downloads and data not exposed by those cleanup APIs can still remain; do not claim complete data erasure or anonymity without device verification.
5. **Compatibility without pretending to be a full browser engine.** Android documents WebView as a web-content component, not a fully developed browser. Mozilla's GeckoView documentation notes that WebView does not expose some advanced browser APIs and offers an extension-management API. Real extension support therefore means a browser-engine migration, not adding Chrome extension files to the current app.
6. **Performance on ordinary phones.** Android's WebView guidance warns that unmanaged WebView instances can cause memory pressure, degraded performance, and out-of-memory crashes. Session restore now creates only the selected WebView, and `TRIM_MEMORY_RUNNING_LOW` releases inactive tabs for reload on selection. This intentionally loses their in-page navigation/scroll state; memory and crash behavior still need measurement on target phones.
7. **Downloads that respect data limits.** Browser downloads now let users choose any available network or Wi-Fi only. Android `DownloadManager` owns the persistent transfer and waits for Wi-Fi when selected. It does not yet provide overnight windows, queue reordering, or Fokk-controlled retries; Android owns transfer progress and final files. Private downloads require an explicit confirmation because they remain outside the private WebView profile.
8. **Links from other apps.** The browser activity now registers for Android HTTP(S) view intents and plain-text share intents, validates incoming web URLs, and routes them through Fokk navigation so tracking cleanup applies. The chooser/default-browser experience and real messaging-app behavior still need device verification; this manifest support alone does not make every app hand links to Fokk automatically.

## Proposed product identity

Build Fokk as an **explainable, mobile-first privacy browser**: clear site controls, useful protection without surprise breakage, reliable reading and downloads, permission decisions that make sense, and transparent private-session behavior. Do not claim universal ad removal, anonymity, or fingerprint-proof browsing.

## Recommended sequence

### P0 — Trust and correctness

- Keep HTTPS pages from loading active HTTP mixed content by default.
- Make the site privacy panel distinguish encrypted transport, tracker filtering, cookie behavior, and storage clearing; avoid presenting a blocked-request count as a complete privacy score.
- Add origin-aware camera, microphone, and location prompts with explicit allow/deny and Android runtime permission handling. These now have per-origin prompts and Android runtime permission gates; decisions are limited to the current browser session, and pending requests are denied on navigation or tab close.
- Verify the isolated private process on Android 9+ and the explicit unavailable-state fallback on Android 8.1 and earlier. Add device checks for storage cleanup, download disclosure, and renderer/process restart before describing it as fully private.

### P1 — Engine and protection quality

- Evaluate GeckoView plus Android Components as the Android browser engine if installable extensions are a core product promise. Use a reviewed extension catalog and permission prompts; do not accept arbitrary APKs or silently install extensions.
- If retaining WebView, keep the claim to domain-based filtering until a maintained, licensed filter engine is integrated. Publish filter-list sources, update cadence, exclusions, and an easy per-site recovery path.
- Add automated compatibility and performance coverage for sign-in, checkout, media playback, redirects, downloads, refresh, and low-memory devices before expanding block rules.

### P2 — Distinctive mobile features

- Add searchable/grouped tabs and reopen-recently-closed tabs, with a memory-aware background-tab policy.
- Extend the existing site privacy panel into a permission and site-data dashboard. It can clear Web Storage for the current origin, but it cannot selectively clear that site's cookies or WebView saved-form data; keep that limitation visible and offer browser-wide clearing where Android only exposes global controls.
- Save text-only offline reading snapshots with local search and on-device deletion; continue toward adjustable text and a scheduled download queue with overnight windows and explicit retry controls.

## Platform boundary

This repository is an Android Kotlin app. Android GeckoView/WebView changes do not create an iOS browser. A future iOS app would need its own native browser implementation using Apple's supported WebKit/Safari extension model, plus a shared product specification and separate platform testing.

## Research references

- [W3C: Mitigating Browser Fingerprinting in Web Specifications](https://www.w3.org/TR/fingerprinting-guidance/)
- [EFF: Cover Your Tracks methodology and fingerprint uniqueness](https://coveryourtracks.eff.org/about)
- [Google Research: Websites Need Your Permission Too](https://research.google/pubs/websites-need-your-permission-too-user-sentiment-and-decision-making-on-web-permission-prompts-in-desktop-chrome/)
- [CHI 2026: From Tabs to Structures](https://doi.org/10.1145/3772318.3791979)
- [Android Developers: Build web apps in WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [Android Developers: Manage and diagnose WebView memory](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory)
- [Android Developers: WebView data directory suffix](https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String))
- [Mozilla: GeckoView extension management API](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/design/managing-extensions.html)
