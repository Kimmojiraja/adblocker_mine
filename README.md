# Fokk Shield

Fokk Shield is an Android browser with a built-in, on-device filter for known ad and tracker domains, plus a small set of cosmetic filters. Protection can be turned off for an individual site from the browser menu. Same-site requests are left alone to reduce breakage. Third-party cookies are blocked by default, with compatibility exceptions for supported media services; first-party cookies remain available for sign-in and site features. The browser supports pull-to-refresh and collapses its address bar while scrolling.

## Scope

- Filtering applies only to pages opened in Fokk Shield Browser. It does not inspect or change traffic in other apps.
- YouTube, Spotify, Instagram, and their related web domains are exempt from request filtering and injected scripts. The browser does not skip, hide, or mute ads in those services.
- Private DNS is configured by the user in Android Settings. Fokk Shield does not silently change the device's DNS provider.
- The browser uses Android WebView and its Safe Browsing support where available.

The filter is a lightweight domain and cosmetic filter, not a full implementation of Brave Shields or EasyList rules. Site behavior can vary; turn the shield off for a site if needed.

## Build

Open this Android project in Android Studio and build the `app` module with a compatible Android Gradle Plugin installation.
