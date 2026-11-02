// Fokk Reader Mode Engine - High-Performance Client-Side Article Extractor
// Adapted from Mozilla Readability principles with zero external dependencies.

(function() {
    'use strict';

    // Inject base reader styles if not already injected
    function ensureReaderStyles() {
        if (!document.getElementById('__fokk_reader_style')) {
            const style = document.createElement('style');
            style.id = '__fokk_reader_style';
            style.textContent = `
                #__fokk_reader_overlay {
                    position: fixed !important;
                    top: 0 !important;
                    left: 0 !important;
                    width: 100vw !important;
                    height: 100vh !important;
                    z-index: 2147483647 !important;
                    display: none;
                    flex-direction: column !important;
                    overflow-y: auto !important;
                    -webkit-overflow-scrolling: touch !important;
                    background: #0A0E18 !important;
                    color: #E2E8F0 !important;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                }
                #__fokk_reader_overlay.active {
                    display: flex !important;
                }
                .theme-obsidian { background-color: #0A0E18 !important; color: #E2E8F0 !important; }
                .theme-sepia { background-color: #F6EEDF !important; color: #433422 !important; }
                .theme-sepia a { color: #8F5902 !important; }
                .theme-dark { background-color: #000000 !important; color: #CCCCCC !important; }
                .theme-paper { background-color: #FFFFFF !important; color: #1A1A1A !important; }
                .theme-paper a { color: #0066CC !important; }
                .font-sans { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important; }
                .font-serif { font-family: "Georgia", "Merriweather", serif !important; }
                .font-mono { font-family: "Menlo", "Consolas", monospace !important; }
                .fokk-reader-toolbar {
                    position: sticky;
                    top: 0;
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 12px 16px;
                    backdrop-filter: blur(16px);
                    -webkit-backdrop-filter: blur(16px);
                    background: rgba(12, 17, 29, 0.92);
                    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                    z-index: 10;
                }
                .fokk-reader-btn {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                    background: rgba(255, 255, 255, 0.08);
                    border: 1px solid rgba(255, 255, 255, 0.12);
                    color: #E2E8F0;
                    font-size: 13px;
                    font-weight: 700;
                    padding: 7px 14px;
                    border-radius: 20px;
                    cursor: pointer;
                }
                .fokk-reader-tools {
                    display: flex;
                    align-items: center;
                    gap: 6px;
                }
                .fokk-reader-tool-btn {
                    background: rgba(255, 255, 255, 0.06);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    color: #94A3B8;
                    font-size: 12px;
                    font-weight: 700;
                    padding: 6px 10px;
                    border-radius: 12px;
                    cursor: pointer;
                }
                .fokk-reader-tool-btn:hover, .fokk-reader-tool-btn:active {
                    color: #FFF;
                    background: rgba(255, 255, 255, 0.14);
                }
                .fokk-reader-content {
                    max-width: 680px;
                    width: 100%;
                    margin: 0 auto;
                    padding: 24px 20px 80px 20px;
                    box-sizing: border-box;
                }
                .fokk-reader-title {
                    font-size: 26px;
                    line-height: 1.3;
                    font-weight: 800;
                    margin: 0 0 12px 0;
                }
                .fokk-reader-meta {
                    font-size: 12.5px;
                    color: #94A3B8;
                    margin-bottom: 20px;
                    padding-bottom: 12px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
                }
                .fokk-reader-hero img {
                    width: 100%;
                    max-height: 340px;
                    object-fit: cover;
                    border-radius: 12px;
                    margin-bottom: 24px;
                }
                .fokk-reader-body {
                    font-size: 18px;
                    line-height: 1.75;
                }
                .fokk-reader-body p {
                    margin-bottom: 18px;
                }
                .fokk-reader-body img {
                    max-width: 100%;
                    height: auto;
                    border-radius: 10px;
                    margin: 16px 0;
                }
            `;
            (document.head || document.documentElement).appendChild(style);
        }
    }

    // Check if page has sufficient article content
    window.isArticleCandidate = function() {
        const bodyText = document.body ? document.body.innerText : '';
        if (bodyText.length < 300) return false;
        const paragraphs = document.querySelectorAll('p');
        return paragraphs.length >= 2;
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
            const allDivs = document.querySelectorAll('div, section');
            allDivs.forEach(div => {
                const pCount = div.querySelectorAll('p').length;
                if (pCount >= 2) {
                    const textLen = div.innerText.length;
                    const linkTextLen = Array.from(div.querySelectorAll('a')).reduce((acc, a) => acc + a.innerText.length, 0);
                    const linkDensity = linkTextLen / (textLen || 1);
                    if (linkDensity < 0.45) {
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
            if (node.closest('header, footer, nav, aside, .sidebar, .comments, .related, .social-share, [class*="advert"]')) {
                return;
            }

            if (tag === 'p' && text.length > 20) {
                articleHtml += `<p>${node.innerHTML}</p>`;
                totalWords += text.split(/\s+/).length;
                plainText += text + '\n\n';
            } else if (['h2', 'h3', 'h4'].includes(tag) && text.length > 3) {
                articleHtml += `<${tag}>${text}</${tag}>`;
            } else if (tag === 'blockquote' && text.length > 10) {
                articleHtml += `<blockquote>${text}</blockquote>`;
            } else if (tag === 'img' && node.src && node.src.startsWith('http') && (node.naturalWidth > 150 || !node.naturalWidth)) {
                articleHtml += `<figure><img src="${node.src}" alt="${node.alt || 'Article Image'}"></figure>`;
            }
        });

        // Fallback: If container returned too little, collect all substantial paragraphs from document.body
        if (!articleHtml || totalWords < 30) {
            articleHtml = '';
            totalWords = 0;
            plainText = '';
            const allP = document.querySelectorAll('p');
            allP.forEach(node => {
                const text = node.innerText ? node.innerText.trim() : '';
                if (text.length > 25 && !node.closest('header, footer, nav, aside')) {
                    articleHtml += `<p>${node.innerHTML}</p>`;
                    totalWords += text.split(/\s+/).length;
                    plainText += text + '\n\n';
                }
            });
        }

        const readingTimeMin = Math.max(1, Math.round(totalWords / 200));

        return {
            title: title || 'Article',
            author: author || '',
            readingTime: readingTimeMin,
            wordCount: totalWords,
            heroImg: heroImg,
            html: articleHtml || '<p>Could not extract readable article text from this page.</p>',
            plainText: plainText
        };
    };

    // Toggle Reader Overlay UI
    window.toggleFokkReaderMode = function(forceOpen) {
        ensureReaderStyles();
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
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m15 18-6-6 6-6"/></svg>
                    <span>Back</span>
                </button>
                <div class="fokk-reader-tools">
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderChangeSize(-1)" title="Smaller Text">A-</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderChangeSize(1)" title="Larger Text">A+</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderCycleFont()" id="fokkReaderFontBtn" title="Switch Font">Serif</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderCycleTheme()" title="Switch Theme">Theme</button>
                    <button class="fokk-reader-tool-btn" onclick="fokkReaderSpeak()" title="Read Aloud">Read</button>
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
        overlay.scrollTop = 0;
        document.documentElement.style.overflow = 'hidden';
        window.__fokk_current_article_text = article.plainText;

        // Register popstate so back button naturally closes reader view
        try {
            history.pushState({ fokkReaderView: true }, '');
            window.addEventListener('popstate', function onReaderPop(e) {
                const ov = document.getElementById('__fokk_reader_overlay');
                if (ov && ov.classList.contains('active')) {
                    ov.classList.remove('active');
                    document.documentElement.style.overflow = '';
                }
                window.removeEventListener('popstate', onReaderPop);
            });
        } catch (_) {}
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
