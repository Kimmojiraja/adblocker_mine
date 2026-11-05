// Fokk Shield - safe, lightweight, compatibility-first page helpers.
// Avoids risky prototype/navigator/history hooks that trigger breakage or detection.
(function () {
  'use strict';
  try {
    if (window.__fokkshield_loaded) return;
    window.__fokkshield_loaded = true;
  } catch (e) { return; }

  var host = '';
  try { host = String(location.hostname || '').toLowerCase(); } catch (e) {}
  if (host.indexOf('webxprt') !== -1 || host.indexOf('principledtechnologies') !== -1 || host.indexOf('browserbench') !== -1) {
    return;
  }

  function safeAppendStyle(id, css) {
    try {
      if (!css || document.getElementById(id)) return;
      var root = document.head || document.documentElement;
      if (!root) return;
      var style = document.createElement('style');
      style.id = id;
      style.type = 'text/css';
      style.appendChild(document.createTextNode(css));
      root.appendChild(style);
    } catch (e) {}
  }

  // Cosmetic-only hiding with narrow selectors. Never touch YouTube player internals.
  var commonCss = [
    '#onetrust-consent-sdk,#onetrust-banner-sdk,.onetrust-pc-dark-filter{display:none!important}',
    '#CybotCookiebotDialog,#CybotCookiebotDialogBodyUnderlay{display:none!important}',
    '.fc-consent-root,.qc-cmp2-container{display:none!important}',
    'iframe[src*="doubleclick.net"],iframe[src*="googlesyndication.com/pagead"]{display:none!important}'
  ].join('');

  var youtubeCss = host.indexOf('youtube.com') !== -1 || host.indexOf('youtu.be') !== -1 ? [
    'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-promoted-sparkles-web-renderer,',
    'ytd-display-ad-renderer,ytd-banner-promo-renderer,ytm-ad-slot-renderer,',
    'ytm-promoted-sparkles-web-renderer,.ytp-ad-overlay-container,',
    'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"]{display:none!important}'
  ].join('') : '';

  function install() {
    safeAppendStyle('__fokk_cosmetic_common', commonCss);
    if (youtubeCss) safeAppendStyle('__fokk_cosmetic_yt', youtubeCss);

    // Conservative scroll unlock: only when an obvious consent/backdrop left the page locked.
    try {
      var body = document.body;
      var de = document.documentElement;
      if (body && de) {
        var bodyStyle = window.getComputedStyle(body);
        var hasOpenModal = !!document.querySelector('[role="dialog"][aria-modal="true"], .fc-consent-root, #onetrust-consent-sdk, #CybotCookiebotDialog');
        if (!hasOpenModal && bodyStyle && (bodyStyle.overflow === 'hidden' || bodyStyle.overflowY === 'hidden') && body.scrollHeight > (window.innerHeight || 0) + 20) {
          // Do not force if site intentionally uses fixed app shell with internal scrollers.
          var mainScrollable = document.querySelector('main,[role="main"]');
          var mainStyle = mainScrollable ? window.getComputedStyle(mainScrollable) : null;
          var mainScrolls = mainStyle && /(auto|scroll)/.test(mainStyle.overflowY || '') && mainScrollable.scrollHeight > mainScrollable.clientHeight + 20;
          if (!mainScrolls) body.style.setProperty('overflow-y', 'auto', 'important');
        }
      }
    } catch (e) {}
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', install, { once: true });
  } else {
    install();
  }
  // One delayed pass for late consent overlays; no continuous observers.
  setTimeout(install, 1200);
})();
