// Fokk On-Device AI Summarizer - 100% Private, Zero-Cloud Extractive NLP Engine
// Runs locally in < 150ms with zero network transmission.

(function() {
    'use strict';

    if (window.__fokk_summarizer_loaded) return;
    window.__fokk_summarizer_loaded = true;

    const STOP_WORDS = new Set([
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can",
        "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
        "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have", "haven't", "having",
        "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him", "himself", "his", "how",
        "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's", "its", "itself",
        "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only",
        "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd",
        "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", "that", "that's", "the", "their",
        "theirs", "them", "themselves", "then", "there", "there's", "these", "they", "they'd", "they'll", "they're",
        "they've", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we",
        "we'd", "we'll", "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's",
        "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you",
        "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves"
    ]);

    window.generatePageSummary = function() {
        const title = document.title || '';
        let text = window.__fokk_current_article_text || '';
        if (!text || text.length < 200) {
            const bodyClone = document.body.cloneNode(true);
            bodyClone.querySelectorAll('script, style, noscript, nav, header, footer, aside, .ad, .ads').forEach(el => el.remove());
            text = bodyClone.innerText;
        }

        // 1. Sentence Tokenization
        const rawSentences = text
            .split(/(?<=[.?!])\s+(?=[A-Z0-9\"\'\‘\“])/)
            .map(s => s.trim())
            .filter(s => s.length >= 35 && s.length <= 320 && !s.includes('{') && !s.includes('function('));

        if (rawSentences.length === 0) {
            return {
                title: title,
                bullets: ["Page does not contain enough clean textual content to generate an AI summary."],
                metrics: [],
                readingTime: "1 min",
                wordCount: 0
            };
        }

        // 2. Word Frequencies
        const titleTokens = new Set(title.toLowerCase().match(/\b[a-z]{3,}\b/g) || []);
        const wordFreq = {};
        rawSentences.forEach(s => {
            const words = s.toLowerCase().match(/\b[a-z]{3,}\b/g) || [];
            words.forEach(w => {
                if (!STOP_WORDS.has(w)) {
                    wordFreq[w] = (wordFreq[w] || 0) + 1;
                }
            });
        });

        // 3. Score Sentences
        const scoredSentences = rawSentences.map((s, idx) => {
            const words = s.toLowerCase().match(/\b[a-z]{3,}\b/g) || [];
            let score = 0;

            words.forEach(w => {
                if (wordFreq[w]) score += wordFreq[w];
                if (titleTokens.has(w)) score += 5; // Title relevance boost
            });

            // Length normalizer
            score = score / Math.sqrt(words.length || 1);

            // Position bias: lead paragraphs often contain crucial summary information
            if (idx === 0) score *= 2.2;
            else if (idx === 1) score *= 1.8;
            else if (idx < 5) score *= 1.4;

            // Numbers / stats bonus
            if (/\d+%|\$\d+|\b\d{4}\b|\b\d+(?:\.\d+)?\s*(?:billion|million|trillion|percent|users|shares)\b/i.test(s)) {
                score *= 1.35;
            }

            return { sentence: s, score: score, index: idx };
        });

        // Select top 3-4 distinct sentences preserving flow
        scoredSentences.sort((a, b) => b.score - a.score);
        const topSentences = scoredSentences.slice(0, 4);
        topSentences.sort((a, b) => a.index - b.index);

        // 4. Extract Key Metrics & Numbers
        const metricRegex = /(?:\$\d+(?:\.\d+)?\s*(?:billion|million|trillion|k|m|b)?|\d+(?:\.\d+)?%|\b\d{1,3}(?:,\d{3})+(?:\.\d+)?\b|\b20\d{2}\b)/gi;
        const matches = text.match(metricRegex) || [];
        const uniqueMetrics = Array.from(new Set(matches)).slice(0, 6);

        const wordCount = text.split(/\s+/).length;
        const readingTime = Math.max(1, Math.round(wordCount / 200)) + " min read";

        return {
            title: title,
            bullets: topSentences.map(item => item.sentence),
            metrics: uniqueMetrics,
            readingTime: readingTime,
            wordCount: wordCount
        };
    };

    window.triggerFokkSummarizer = function() {
        const summary = generatePageSummary();
        if (window.FokkShieldBridge && typeof window.FokkShieldBridge.showAiSummaryData === 'function') {
            window.FokkShieldBridge.showAiSummaryData(JSON.stringify(summary));
        } else {
            // In-page fallback modal
            renderInPageSummaryModal(summary);
        }
    };

    function renderInPageSummaryModal(data) {
        let existing = document.getElementById('__fokk_ai_summary_modal');
        if (existing) existing.remove();

        const modal = document.createElement('div');
        modal.id = '__fokk_ai_summary_modal';
        modal.style.cssText = `
            position: fixed; inset: 0; background: rgba(0,0,0,0.75); backdrop-filter: blur(14px);
            z-index: 1000000; display: flex; align-items: flex-end; justify-content: center;
        `;

        modal.onclick = (e) => { if (e.target === modal) modal.remove(); };

        modal.innerHTML = `
            <div style="background: #111726; border: 1px solid rgba(0,229,255,0.25); border-radius: 24px 24px 0 0; width: 100%; max-width: 580px; padding: 22px; max-height: 85vh; overflow-y: auto; color: #FFFFFF; font-family: -apple-system, sans-serif; box-shadow: 0 -10px 40px rgba(0,0,0,0.6);">
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px;">
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="background: rgba(0,229,255,0.15); color: #00E5FF; padding: 4px 10px; border-radius: 8px; font-size: 11px; font-weight: 800; border: 1px solid rgba(0,229,255,0.3);">ON-DEVICE AI</span>
                        <span style="font-size: 15px; font-weight: 800;">Key Takeaways</span>
                    </div>
                    <button onclick="document.getElementById('__fokk_ai_summary_modal').remove()" style="background: rgba(255,255,255,0.08); border: none; color: #fff; width: 32px; height: 32px; border-radius: 50%; font-size: 14px; cursor: pointer;">✕</button>
                </div>
                <div style="font-size: 12px; color: #94A3B8; margin-bottom: 16px;">${data.readingTime} &bull; 100% Private &bull; Zero Server Latency</div>
                <div style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px;">
                    ${data.bullets.map(b => `
                        <div style="display: flex; gap: 10px; font-size: 13.5px; line-height: 1.5; background: rgba(255,255,255,0.03); padding: 12px; border-radius: 12px; border: 1px solid rgba(255,255,255,0.06);">
                            <span style="color: #00E5FF; font-weight: bold;">•</span>
                            <span>${b}</span>
                        </div>
                    `).join('')}
                </div>
                ${data.metrics.length > 0 ? `
                    <div style="font-size: 11px; font-weight: 800; color: #94A3B8; text-transform: uppercase; margin-bottom: 8px;">Key Figures Detected</div>
                    <div style="display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 18px;">
                        ${data.metrics.map(m => `<span style="background: rgba(0,229,255,0.08); color: #00E5FF; border: 1px solid rgba(0,229,255,0.2); padding: 4px 8px; border-radius: 6px; font-size: 11.5px; font-weight: 700;">${m}</span>`).join('')}
                    </div>
                ` : ''}
                <button onclick="navigator.clipboard.writeText(${JSON.stringify(data.bullets.join('\n\n'))}); this.innerText='Copied to Clipboard!';" style="width: 100%; background: linear-gradient(135deg, #00E5FF, #3B82F6); border: none; border-radius: 14px; padding: 12px; color: #000; font-weight: 800; font-size: 13px; cursor: pointer;">Copy Summary</button>
            </div>
        `;
        document.body.appendChild(modal);
    }
})();
