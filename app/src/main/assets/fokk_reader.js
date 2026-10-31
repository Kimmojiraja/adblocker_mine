// Fokk Reader Mode Engine - High-Performance Client-Side Article Extractor
// Adapted from Mozilla Readability principles with zero external dependencies.

(function() {
    'use strict';

    if (window.__fokk_reader_loaded) return;
    window.__fokk_reader_loaded = true;

    // Check if page has sufficient article content
    window.isArticleCandidate = function() {
        const bodyText = document.body ? document.body.innerText : '';
        if (bodyText.length < 400) return false;
        const paragraphs = document.querySelectorAll('p');
        return paragraphs.length >= 3;
    };

    // Extract core article content
    window.extractArticleData = function() {
        // 1. Title
        let title = '';
        const ogTitle = document.querySelector('meta[property="og:title"]');
        if (ogTitle && ogTitle.content) title = ogTitle.content;
        else if (document.querySelector('h1')) title = document.querySelector('h1').innerText.trim();
        else title = document.title;

        // 2. Author / Byline
        let author = '';
        const authorMeta = document.querySelector('meta[name="author"]') || document.querySelector('meta[property="article:author"]');
        if (authorMeta && authorMeta.content) author = authorMeta.content;
        else {
            const authorEl = document.querySelector('[class*="author"], [rel="author"], [class*="byline"]');
            if (authorEl) author = authorEl.innerText.trim();
        }

        // 3. Hero image
        let heroImg = '';
        const ogImg = document.querySelector('meta[property="og:image"]');
        if (ogImg && ogImg.content) heroImg = ogImg.content;

        // 4. Find best content container
        const candidates = [];
        const contentContainers = document.querySelectorAll('article, [role="main"], main, .post-content, .article-content, .entry-content, .story-body, #content');
        
        if (contentContainers.length > 0) {
            contentContainers.forEach(el => candidates.push(el));
        } else {
            // Score div / section containers based on paragraph and text density
            const allDivs = document.querySelectorAll('div, section');
            allDivs.forEach(div => {
                const pCount = div.querySelectorAll('p').length;
                if (pCount >= 3) {
                    const textLen = div.innerText.length;
                    const linkTextLen = Array.from(div.querySelectorAll('a')).reduce((acc, a) => acc + a.innerText.length, 0);
                    const linkDensity = linkTextLen / (textLen || 1);
                    if (linkDensity < 0.4) {
                        candidates.push({ el: div, score: pCount * 10 + (textLen / 100) });
                    }
                }
            });
            candidates.sort((a, b) => (b.score || 0) - (a.score || 0));
        }

        const bestContainer = candidates[0] ? (candidates[0].el || candidates[0]) : document.body;

        // Extract paragraphs and headings
        const elements = bestContainer.querySelectorAll('p, h2, h3, h4, blockquote, figure, img');
        let articleHtml = '';
        let totalWords = 0;
        let plainText = '';

        elements.forEach(node => {
            const tag = node.tagName.toLowerCase();
            const text = node.innerText ? node.innerText.trim() : '';

            // Skip boilerplates
            if (node.closest('header, footer, nav, aside, .sidebar, .comments, .related, .social-share, [class*="advert"], [class*="ad-"]')) {
                return;
            }

            if (tag === 'p' && text.length > 25) {
                articleHtml += `<p>${node.innerHTML}</p>`;
                totalWords += text.split(/\s+/).length;
                plainText += text + '\n\n';
            } else if (['h2', 'h3', 'h4'].includes(tag) && text.length > 3) {
                articleHtml += `<${tag}>${text}</${tag}>`;
            } else if (tag === 'blockquote' && text.length > 15) {
                articleHtml += `<blockquote>${text}</blockquote>`;
            } else if (tag === 'img' && node.src && node.src.startsWith('http') && node.naturalWidth > 200) {
                articleHtml += `<figure><img src="${node.src}" alt="${node.alt || 'Article Image'}"></figure>`;
            }
        });

        const readingTimeMin = Math.max(1, Math.round(totalWords / 200));

        return {
            title: title || 'Article',
            author: author || '',
            readingTime: readingTimeMin,
            wordCount: totalWords,
            heroImg: heroImg,
            html: articleHtml || '<p>Could not format article cleanly.</p>',
            plainText: plainText
        };
    };

    // Toggle Reader Overlay UI
    window.toggleFokkReaderMode = function(forceOpen) {
        let overlay = document.getElementById('__fokk_reader_overlay');
        if (overlay) {
            if (forceOpen === false || overlay.classList.contains('active')) {
                overlay.classList.remove('active');
                document.documentElement.style.overflow = '';
                if (window.FokkShieldBridge && typeof window.FokkShieldBridge.stopSpeech === 'function') {
                    window.FokkShieldBridge.stopSpeech();
                }
                return;
            }
        }

        const article = extractArticleData();

        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = '__fokk_reader_overlay';
            overlay.className = 'fokk-reader-view theme-obsidian font-sans';
            document.body.appendChild(overlay);
        }

        overlay.innerHTML = `
            <div class="fokk-reader-toolbar">
                <button class="fokk-reader-btn" onclick="toggleFokkReaderMode(false)" title="Exit Reader">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m15 18-6-6 6-6"/></svg>
                    <span>Back</span>
                </button>
                <div class="fokk-reader-tools">
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderChangeSize(-1)" title="Smaller Text">A-</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderChangeSize(1)" title="Larger Text">A+</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderCycleFont()" id="fokkReaderFontBtn" title="Switch Font">Serif</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderCycleTheme()" title="Switch Theme">🎨</button>
                    <button class="fokk-reader-tool-btn pulse-accent" onclick="fokkReaderSpeak()" title="Read Aloud">🔊</button>
                    <button class="fokk-reader-tool-btn ai-badge" onclick="fokkTriggerSummarizer()" title="AI Summary">✨ AI</button>
                </div>
            </div>
            <div class="fokk-reader-content">
                <header class="fokk-reader-header">
                    <h1 class="fokk-reader-title">${article.title}</h1>
                    <div class="fokk-reader-meta">
                        ${article.author ? `<span>By ${article.author}</span> &bull; ` : ''}
                        <span>${article.readingTime} min read (${article.wordCount} words)</span>
                    </div>
                </header>
                ${article.heroImg ? `<div class="fokk-reader-hero"><img src="${article.heroImg}" alt="Hero Image"></div>` : ''}
                <article class="fokk-reader-body">
                    ${article.html}
                </article>
            </div>
        `;

        overlay.classList.add('active');
        document.documentElement.style.overflow = 'hidden';
        window.__fokk_current_article_text = article.plainText;
    };

    let currentFontSize = 18;
    window.fokkReaderChangeSize = function(delta) {
        currentFontSize = Math.max(14, Math.min(26, currentFontSize + delta * 2));
        const body = document.querySelector('.fokk-reader-body');
        if (body) body.style.fontSize = currentFontSize + 'px';
    };

    const fonts = ['font-sans', 'font-serif', 'font-mono'];
    let currentFontIdx = 0;
    window.fokkReaderCycleFont = function() {
        const overlay = document.getElementById('__fokk_reader_overlay');
        if (!overlay) return;
        fonts.forEach(f => overlay.classList.remove(f));
        currentFontIdx = (currentFontIdx + 1) % fonts.length;
        overlay.classList.add(fonts[currentFontIdx]);
        const btn = document.getElementById('fokkReaderFontBtn');
        if (btn) btn.innerText = fonts[currentFontIdx].replace('font-', '').toUpperCase();
    };

    const themes = ['theme-obsidian', 'theme-sepia', 'theme-dark', 'theme-paper'];
    let currentThemeIdx = 0;
    window.fokkReaderCycleTheme = function() {
        const overlay = document.getElementById('__fokk_reader_overlay');
        if (!overlay) return;
        themes.forEach(t => overlay.classList.remove(t));
        currentThemeIdx = (currentThemeIdx + 1) % themes.length;
        overlay.classList.add(themes[currentThemeIdx]);
    };

    let isSpeaking = false;
    window.fokkReaderSpeak = function() {
        if (!window.FokkShieldBridge) return;
        if (isSpeaking) {
            isSpeaking = false;
            window.FokkShieldBridge.stopSpeech();
        } else {
            isSpeaking = true;
            const text = window.__fokk_current_article_text || document.querySelector('.fokk-reader-body')?.innerText || '';
            window.FokkShieldBridge.speakText(text.slice(0, 10000));
        }
    };

    window.fokkTriggerSummarizer = function() {
        if (typeof window.triggerFokkSummarizer === 'function') {
            window.triggerFokkSummarizer();
        } else if (window.FokkShieldBridge && typeof window.FokkShieldBridge.showAiSummary === 'function') {
            const text = window.__fokk_current_article_text || document.body.innerText.slice(0, 8000);
            window.FokkShieldBridge.showAiSummary(document.title, text);
        }
    };

    // Auto-detect articles and notify native bridge
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkReader);
    } else {
        checkReader();
    }

    function checkReader() {
        setTimeout(() => {
            if (isArticleCandidate() && window.FokkShieldBridge && typeof window.FokkShieldBridge.onArticleDetected === 'function') {
                window.FokkShieldBridge.onArticleDetected(true);
            }
        }, 1200);
    }
})();
