# Fokk Browser: product gaps and quality direction

This note records a product direction from the current Android implementation and public browser/platform research. It is not a claim that Fokk already provides the capabilities below.

## What users still need from browsers

1. **Privacy that explains itself.** Blocking cookies and known tracker domains does not stop browser fingerprinting. The W3C describes fingerprinting as a cross-origin tracking risk even when cookie controls prevent cross-site cookie access. Fokk should report what it actually blocked and be clear about protections it cannot provide.
2. **Permissions with context.** Permission prompts are often shown before users understand why a site needs access. Large-scale Chrome research has studied user sentiment and context-aware quieting of permission prompts. Fokk should ask only after a site action, show the requesting origin and purpose, and offer a durable per-site choice.
3. **Tabs people can recover.** Recent CHI work documents tab overload and difficulty finding previously visited pages. Fokk should improve tab grouping, search, and lightweight suspension before adding more background features.
4. **Private browsing with a precise promise.** Hiding local history is not the same as isolating cookies, storage, cache, or service workers. Fokk's current WebView tabs share the app WebView data store; the current private-tab flag avoids history and cache but does not isolate first-party cookies or all site storage. Do not claim full incognito isolation until the storage model changes.
5. **Compatibility without pretending to be a full browser engine.** Android documents WebView as a web-content component, not a fully developed browser. Mozilla's GeckoView documentation notes that WebView does not expose some advanced browser APIs and offers an extension-management API. Real extension support therefore means a browser-engine migration, not adding Chrome extension files to the current app.
6. **Performance on ordinary phones.** Android's WebView guidance warns that unmanaged WebView instances can cause memory pressure, degraded performance, and out-of-memory crashes. Multiple live tabs need a measured lifecycle and a low-memory strategy.

## Proposed product identity

Build Fokk as an **explainable, mobile-first privacy browser**: clear site controls, useful protection without surprise breakage, reliable reading and downloads, permission decisions that make sense, and transparent private-session behavior. Do not claim universal ad removal, anonymity, or fingerprint-proof browsing.

## Recommended sequence

### P0 — Trust and correctness

- Keep HTTPS pages from loading active HTTP mixed content by default.
- Make the site privacy panel distinguish encrypted transport, tracker filtering, cookie behavior, and storage clearing; avoid presenting a blocked-request count as a complete privacy score.
- Add origin-aware camera, microphone, and location prompts with explicit allow/deny and Android runtime permission handling. These now have per-origin prompts and Android runtime permission gates; decisions are limited to the current browser session, and pending requests are denied on navigation or tab close.
- Redesign private tabs around genuinely isolated WebView storage. Android WebView supports a distinct data directory per process starting at API 28, but it must be configured before WebView initialization. This needs a separate-process design and an explicit fallback for older Android versions.

### P1 — Engine and protection quality

- Evaluate GeckoView plus Android Components as the Android browser engine if installable extensions are a core product promise. Use a reviewed extension catalog and permission prompts; do not accept arbitrary APKs or silently install extensions.
- If retaining WebView, keep the claim to domain-based filtering until a maintained, licensed filter engine is integrated. Publish filter-list sources, update cadence, exclusions, and an easy per-site recovery path.
- Add automated compatibility and performance coverage for sign-in, checkout, media playback, redirects, downloads, refresh, and low-memory devices before expanding block rules.

### P2 — Distinctive mobile features

- Add searchable/grouped tabs and reopen-recently-closed tabs, with a memory-aware background-tab policy.
- Add a permission and site-data dashboard that explains what is stored and lets users clear a site without wiping the whole browser.
- Keep Reader View, then consider offline reading and adjustable text as opt-in features.

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
