// Fokk Shield page-level defusers. Network filtering remains the primary guard;
// this handles overlays that are created after the document has loaded.
(function() {
	'use strict';

	if (window.__fokk_defusers_loaded) return;
	window.__fokk_defusers_loaded = true;

	var overlaySelector = [
		'[id*="cookie" i]', '[class*="cookie" i]',
		'[id*="consent" i]', '[class*="consent" i]',
		'[id*="gdpr" i]', '[class*="gdpr" i]',
		'[id*="privacy" i][role="dialog"]', '[class*="privacy" i][role="dialog"]',
		'[id*="adblock" i]', '[class*="adblock" i]',
		'[id*="anti-adblock" i]', '[class*="anti-adblock" i]'
	].join(',');

	function isOverlay(element) {
		if (!element || element === document.body || element === document.documentElement) return false;
		var role = (element.getAttribute('role') || '').toLowerCase();
		var text = (element.textContent || '').toLowerCase();
		var style = window.getComputedStyle(element);
		var fixed = style.position === 'fixed' || style.position === 'sticky';
		var dialog = role === 'dialog' || element.hasAttribute('aria-modal');
		var blockingCopy = /cookie|consent|privacy|ad blocker|disable your ad blocker|enable javascript/.test(text);
		return (fixed || dialog) && blockingCopy;
	}

	function defuse(root) {
		if (!root || !root.querySelectorAll) return;
		var candidates = [];
		if (root.matches && root.matches(overlaySelector)) candidates.push(root);
		candidates = candidates.concat(Array.prototype.slice.call(root.querySelectorAll(overlaySelector)));
		candidates.forEach(function(element) {
			if (!isOverlay(element)) return;
			element.style.setProperty('display', 'none', 'important');
			element.setAttribute('aria-hidden', 'true');
		});
		document.documentElement.style.removeProperty('overflow');
		if (document.body) document.body.style.removeProperty('overflow');
	}

	defuse(document.documentElement);
	new MutationObserver(function(records) {
		records.forEach(function(record) {
			Array.prototype.forEach.call(record.addedNodes, defuse);
		});
	}).observe(document.documentElement, { childList: true, subtree: true });
})();
