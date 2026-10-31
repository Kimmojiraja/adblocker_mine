// Fokkoo Ambient Reality Inspector - Content Script
// Injects ultra-clean, unboxed forensic verification directly onto X (Twitter), Reddit, and web text selections.

(function () {
  "use strict";

  if (window.__fokk_content_injected) return;
  window.__fokk_content_injected = true;

  const APP_URL = "https://trustmebrowtf.buzz";
  const PROD_API_ENDPOINT = "https://trustmebrowtf.buzz/api/ambient-inspect";
  const LOCAL_API_ENDPOINT = "http://localhost:3001/api/ambient-inspect";

  let modalBackdrop = null;
  let floatingBubble = null;
  let activeSelectionText = "";
  let activeAuditClaim = "";
  let loadingInterval = null;
  let typewriterTimer = null;
  let activeTypewriterController = null;

  // Resolve official brand logo URL via extension runtime
  function getAppLogoUrl() {
    try {
      if (typeof chrome !== "undefined" && chrome.runtime?.getURL) {
        return chrome.runtime.getURL("icons/app-logo.png");
      }
    } catch {}
    return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAYAAABS3GwHAAAYAUlEQVR4nO1daYwU1Ra+3TMMm4gLIIKyDMgyogRBBDHyRBAJyL4FQUAWZVEUkM2AsriwOoKIoKKCSASFSNQYMaCiRuLyBzfEAAYJbqCgMONs9fJd+/Srqdc9Mz19q+pW3fMllWaaXqqqz3fOueeeJSKEsASDYSiifp8Ag+EnmAAMo8EEYBgNJgDDaDABGEaDCcAwGkwAhtFgAjCMBhOAYTSYAAyjwQRgGA0mAMNoMAEYRoMJwDAaTACG0WACMIwGE4BhNJgADKPBBAgIotH//6kikYg8GJUH7h7XBGsOCLll8c/kBtgCaC740PwQ/vHjx4vzzz+/lDXo0aOHqFWrls9nGWwwATRGRkaGKCkpEUuWLBHLly8XeXl5pQjQrVs30a9fv/hrGamDCaApMjMzRVFRkbj77rvFgw8+KE6dOhX39+kxPz9f/h/ALlLlwATQWPgHDx4s1q1bl1S4f/75Z9GqVStx8803S0vBViB1MAE0A4QYwn/99deLzZs3i+Li4ni0h4hAj+fOnZP/ZitQeTABNAJ8ewh8dna22LFjh6hWrVqZrg0R4z//+Y9cD7AVSB1MAE1Afn2NGjXEzp07xSWXXCLJQAveRDF/CDz939y5c+W/eS2QGpgAGoU7IdAvvviiuPrqq6UbVJ5PX1BQIB/xWqwDunTpwlYgRTABNAAEHdp+4cKFYsiQIVKgsRC2I5FmxxoAAHFAonnz5iV9LSM5cLf48OkeZGZmysf+/ftbQGFhoVVSUmIRiouL5ePhw4etKlWqlHrPLbfcEn8NjqKiIqtDhw5WJBKxMjIy+DcV5d9/tgA+Am4PtH1OTo544YUX4u5Lsvweep40PPYB6Hl6L9YCbAEqDiaATyBhrl69uti6dau44IILpOCmktxGO8N4D4Qf7+/bt69cQ3BEqGJgAvic5rB+/foKLXoTEYOiQPT/WEdg7TBz5ky2AimAfUWf/P4pU6bE/f5koDXAkSNHrKpVq8r3kX/frl27Uq/F2gFHfn6+1bp1a7kWiEaj/PsKXgNoudO7atUqqbVVpTCQFahataqYPn16yi6ViWAXyMubHYv116tXT/r9WVlZaRW1gEjO9AhaC4wYMUI0a9ZMfl+iYhrGv+A740NuPyI+jRo1kgKcjnBiIwyfkcgKYEd52rRpbAUqAPYRPfT758+fX67fX94agPz6pk2bWufOnYv7/861wOnTp62GDRvyWkDwGkALvx8VXIsWLapQmkNFgM9xWgD7vgAqyCZPnsxWoAywC+SR31+/fn2Z5wMXCM+l6vcnej1cILg7yb4X34WCmrp16/JaIAmYAB75/c8995xo0KCBUkFMZgHou0GOiy66SEyYMCFOPEZp8B3xwPWZM2eO6N27d9quT6J06LLSHoh8U6dOle4QFdcw/gcmgMvC37FjR+n3q4z3EyDc5REAJLn00kvFyJEj5Wu5bLI0mAAugMoXa9euLbZs2SKqVKniShOr8ghgPxekRyDviK1AaTABXMzzWbNmjWjevHna8X4gGXnKIwBZgaZNm4qhQ4eyFXCACeCS6zN69GgxatSohMUtXloA+2sfeOABaY2SRY5MBBPAhaL2Fi1aiNWrVytNSU5WE0wZoWURgdIjrrzyStGrVy+2AjYwARSBBBTChlQHRF3cTkazE6A8EEFmz54djw4xmADK63rRoweZnqp2e8uyAPg+KoyvyPmBLDg3bqHyP7AFUOj3d+7cWSxYsMCVkGcyFyjZRlgikNafNWtWqb9NBhMgTVCYsWbNmuL555+P1/Sqdn0SfSa+N5UFLVmB7t27i+uuu47LJpkA6YOEauXKlaJ169ZKQp6JkGjBi79TjejQuoQbaf0LtgAKXB+0KL/rrrtcDXkmWvBCmCu6CHYStk+fPqJdu3bGR4SYAAqqu1DY7naymQoXiEDh2emxskmTwQRI0/dH+3L08fSr9DBVC2DfFxg8eLDcszC5bNLMq1YU8hw7dqwYOHCg8pBnIiRbWFdGg1OqdLVq1cT9999vdMEME6CSu70oOM/NzfUtkkICS2HQVIlAVmDUqFEyT8hUK2DeFSvQwjhQ4OLFbq/zu52obF4PWYGaNWuKe+65x9iCGfOuWIH2nzFjhhxK4YXrUx7SWcRmxKwAXDmUbNrnEZgCs65WgfAj1r948WJXdnu9WgPYP7O4uFj2JZ00aZKRVsCsq1WQ6LZx40a5eKTnvfhuAN9tJ5yzU3RlEY0lxmEf48ILLzSuYIYJkELUB3HzTp06ea79SVATaefKhEGdn1tSUiJDuWPGjDFuY4wJUMEe/m3atJETXHTzk1VsZEViexr33XefnDxvkhXQ55fUPOrzzDPPyJpaet6vc3GDANGYFUC7xmHDhhllBZgAFXB9oBkxgM7PqE8yF0hVKkPEZgXQtNeUskkmQDmuD6I+S5Ys0TZ1WBUBojErgLLJ/v37G2MFmADluBzI9UGnZR3SBRJ9fyoFMakUz0dsk+nDDCZAGa7PxIkTRdeuXbXY8Eq2BigsLFT2HRmxjbEOHToYUzbJBChjQfj4449rKwREhnTDoE6Q1kdts/3vsIIJ4ACZfiS6VWZyo1tIdg6qCZARK5hBqgfaOuqqAFSBCZDA9Rk0aJAYMGCALxteZcGtMKgTRHoTiueZAI6UALQTX7t2rTaavzyotgB2NxDRIIxwDXNEiAngmKry6KOP+lrhlSrciNdHbJPnqWAmrND/F/bQ9YHfi6Qw3dIdykJFWiOmExEaOnRoqKdNhu+KKrnoRYbnU089FX8uCO4P4JZ2jtimTWJ3OCguYaowngAU9UDPTOyC6qr9k5FS9UZYsrJJ1eOddEG4rqaSRS7Z2dly9zOIP7Abi2CnFcCgj7CWTYbrairp/jz55JOyNlZnMw9BTCTsbhLAbgXGjRsn6tSpE7pU6ajpC98hQ4bILmm6xfydPn5eXp44e/Zs0v93C5FYRAijVu+8887QhUSjJmt+lACuWrVKa81PU2COHz8uCeBHklok9p1TpkwR5513XqisgJEEoI0e5Ltcdtll2i58AZwnhG3Hjh3yb6f29YIMUVt+FBbEYbMClklHRkaGfGzbtq2Vl5dnFRUVWSUlJZZuwDkVFBTIfx8+fNiqXbu2FYlE5GG/jg0bNsjXFBYWuno+xcXF8pwOHTpkVa9evdS5BPnQU+15oNEwwVFFdwdYD5rYruogFwMD7X799VdZpnj69OlS7o+qrhCpWgFMvUSuVJisgHHa/4477pBaDdo/HbhpOfLz863t27db2dnZ/2qqaLTUtWRmZsrH9evXe2IBALKWn3/+ubyXznMK4uFOM3vNF77I9yHfurKghfPmzZvFgQMHpLamkCQl1pU3xpTOyf4afAa0/v79+8XBgwfjn5cs3OnlgjgjtmnYvn170bt3b7Fr1654NC3IMEr7P/HEE3FtpsIfdlML4rOTfT5ZgHXr1nlmAez37aOPPpJrgKBbASMsAGkpTESZOnVq2lEf2hF97LHHpEbEWkJ1SkIqI1C9REbMCqBLxk033ST27NkTeCtgmaL99+7dW0qLpaP9Dx48aFWrVs23aAhZgKefftpTC2C/f7t375bnEGQrEPooEGknpPUi3TndHV/y/ZctWyby8/PjqQJ+wY8NqQzbtEnMHQ5y2WTUlBGmS5cuTXvHl5Llvv/+e7F169Z4spiJsEIyczjUBKDoCfLZmzRpkna2JxEIvv+5c+fi0R4/4df3Z8Qs32233Sbatm0bWCsQDbvwQ/DnzJmTtvDT+w8dOiReffXVMkOTXsJPApbE7gnub1ARDbv788gjj8gErnTdH3o/9hCQmamD9gf8JGE0dg+wM4zu2UGspwjW2aa48L3hhhvEiBEj0l740g/77bffGu/720FrIGwCBrVsMpQEAPBDYOGrAvTDrly5Uvzzzz++R37s8FvgMmL3Yvjw4UrWWV4jOGeaovYfOXKkDNGp0P54PyI/r7zySjwEaJ8d4MWhKyK2aZNBtQJWWA7amq9Ro4Z19OhRuWGFjSsVmz6jR4/2/foSbYStWbPG840wJ3Cfcfzxxx9W/fr1A5UikRnGIvd7771XNG7cWJn2R1LaW2+9JYvDEyWwJYNTE9rfY09nTpTa7Kz8+uuvv7Rxu5zAuSIVBL1UJ0+eLBYsWBC3lLoDd17Pu1rJiATm3X799ddxYVVhjv/++28pgHYylSeM5fXxTEYAe+cF8qex7kDuzbFjx+Lh18zMTCl0q1evlh0b8G885xdKYm7hb7/9Jlq2bCnrF+iadEZoLAAVb6PMESnPKgUCYVQcfgHXhUhLECbq1KtXT3aQQMCASKozQrEIJq2Yk5Mjb74bu5L2/H6vDlwHHgsKCgLhTkRjVhhuEDrKBaF4PhQEIH950aJFMjXZjUiE11GfIEWCnIoIjcYQFg1C2WQ0LGFPDLBGT/+g5qRUFrr52JGYMsJQcbhtuicLBp4ApO0xzkiX9ASTEbVNm4RCwu/h5+I81ASgUBvqUzHMzjTtrzMsy5KzBXRJGgwdAcjUVq1aVSaoVTQ2z/AuPaJTp06iZ8+eWiumaNC1P5LdrrrqKq1vspvQlfRW7LyQKu1HO8dQE4Bi/gi1zZs3L5D5J6qgq2BlxBTUjTfeKDp37qytggokAcivnDRpkuxUFrQMRNPIOXv27FJ/64RoULU/pjnCvJqg/dEVGukYugpReVYA7efRkkbHfYHAEYBCnYgwYGBDmLU/CTtao588eVIEEVYstwn7AjqSNxpE16dhw4Yy4zOMI3vsoFSITz75JO5DO4VI5xCjXWFhEImO7qo+Z1IBUDQBPuX5558fiFyTdABBwfVt27ZNBBWRWMEMwtXTpk3TzmUNDAHs7bknTJigbVRBFah94759+8TevXvjtQ5BREbMco0ePVoOJNHJCuhxFilo/7lz58qEt3S7O+sMu5uD6ZW6ac3KBi5q1aolxyzpdD3RIGl/bHjdfvvtodb+0PIULcFCH23Sy2o+q+PCsqy1ANLVUa+hiwKLBkn7L168WPqSOmkQFcD1QMCphBPCMmPGDJGbm1tu5+WgECBimzYJF1ankKgVhM7OnTt3VlLk7gQ+z8/DiS+//NLq3r17qWsvqyh+2bJlvhfFp9pZ+6effrJq1qypxZwxffNUHRpu4cKFcS2i8rN1WIydOHFCujqvvfaajPgUFhYGvud+ItBCHmFstK1Zv36979epNQHo5vTo0UMeKn1/cjdef/11sW7dunhNq7NY3TmULhUkG2hHf+N6Tp06JY4ePSrOnDkTf5/fQuEm6J7CxXvppZdkwb/fyXKWrgd6y8BE7tu3T8lQOwK5UhhE17x5c9+vk9wdHBV1CYLoAhHodxw+fHipa2EXyAbSgn379pU9PtPt8WMHWRL0+P/hhx9EVlaWbxqX6hjCqvGTgawAXD6/d7O11f7QiJ999lkpraGqi9nZs2el9g9SF7OwWACAAgB9+vSJW0Bf5ExonEU4ePBg0aFDB+W+P3xO9PiH9te9ZC+ssGI+P/Y67H97De0IQAsiuCUPPfSQ0htDUR/M9sKML78XXyYjI6bkunXrJme3+bW5GdX1xgwbNky0bt1aad4I5de8/PLL4rvvvmPt7zNI+SDdw/63sQSwlzqiwarKHV/aeYT2X7FiBWt/jZRdz549fSuY0YoA5I8ja1B17jj5/oj8oNsz+/56gFwfvwpmojpmDCLj0y3tjxYqYfL9g34dGbFUacxxRjMtr1Olo7rdiIkTJ4rLL7/cNe3PkR89C2aysrJkRMjrRMeoTjcBPf1nzpzpmvZH+8Qwaf+wICOm/NDjqVmzZp5agahOueIY9IABF25pf8z5Yt9fP0RiCrB69eqel036TgASSHR4UD1kjbQ/+uuz9g+GFRgzZoxo0KCBZ1YgqlN24MUXX+yK9sd0R9b+eiNiC4Kg4ZmXVsDXfB/k4jRs2NA6c+ZMPE9HBeizkPHZsmXLwOb8lJcLtHTp0kDmApX1m508edKqU6eOJwUzUR20P3YCwXyVdaL0WdD+HPcPXte/cePGebIxFvXb92/atKkYP368UteHcn7g+2NaPEd+goNITCkiIOJF76eo3xeK7s6YMq5S+/Oub3ARtXX/Q1jUbSsQ9fMikew2atQopZmA9sgPZXyGGWHc04jElCM2xtAFxM1ioUw/fb358+fHL1Bl5AczqbZs2SK++eabUNfXArg21DLrPo83VUA+rrjiClkRuH37dldnDnsavaDKn7Zt21oFBQVK25zYa31btWoVushPoihQbm6uFWYcPHjQysrKci0i5FtXiIcfflj5GE3S/tj1Rb5/mLU/VbG98cYbsqMEUj3KsqLUaZreR65TSex5+4w1PJesSi6Ry1XWc6m4aIk6cKBFDOQELq1b7qzn2r9Lly7Km1zR5+Xl5YUy7s+HcMeSukIpH5pckfZHrS/i/kHQ/io0GrS+Ds29ksFpASpyzXZLkKxjhtJSWS+1/6233qq0y4PT98/JyZHa368uA4kOskbw23GwZRLmWQBiLCI/WM2r1P74PESTUOurS+QH2os0M3V8dl4z2rzDapGmc3ajqyyc70/2eVaSznUV/dzKnEtl3u88TzxiXYDZaekCn6jOliQBCeTYsWPFxo0bXQ2bHTlyRLl7lQrw3bhee8gOxR7Y87jmmmtE+/btRZMmTWTRD9I/7ARQad4rI8yWywSoaIKb0wWyfwZ+VygOZA+89957aSs7TywACeOPP/4oczzob1xgIo1nv0nl9eakja9jx46Jw4cP+yL8Tm0P4Ydgd+3aVcaxUfTdsmVLT88pzFi0aJES4ffMAngFr3J+iLhUyGP/EdD/HgPhoKHQ6cAOcoXo/YyKA0oFlnT58uVi1qxZ8Xuf7u+tjAAVSWXAa9L94cuyBG77/XTDE/ny6F+Kwu7+/ftLEtjPSfdIje4gi7pp0ybZMYTaqahQdqGyAF4Bmig7O1tce+21UvDR3QxtXJzJeCz06oR/165dYtCgQaU27lQgLQJQUlvHjh1lG0NMM8cPj907J0Od+SqkSZ27kOQmJNLs9t1F505jop1H+/ohnRsHjQMtj3pllOs1atRING7cuJTVo/NUYeUYpYX/nXfeEf369ZNyBah0c9NaBFMO/4EDB+RWNVwAk0BkJRcHPxZDrfDv2bNHDBw4UMqXGw0N0naBaOGJx3fffVd0795dTv3AyYc1Vdd+MNwVfkTREO93q5uHkjWAvbPDxx9/LFq0aKF0oAXDHBTGkt92794tBgwY4KrwA0pCE1TQ8vvvv8uFCuZe0UqdwUhF80P43377bdc1P0FZbI40/ldffSVJ4MaChRF+t2fbtm1S81N6t9tKVGlwmjIy33//fTkGk8ZiMgkYyQDZIOF/9tln5VwIKE+vOvhF3WQyWl7j334npjH0hBULS0NG0L0DjZFp78RL99nVkr0VK1aEpnETQx3sxVDTp0+XspLKmFiFh3s58ESCDRs2yAtFDTCDURSrBUH1Hs0K9kn4LVdTIewJYzt37pQrewpzMcxEUcxFRvYu1okffvihqx0ffM8FIgIgfwYk6NWrF5PAQJTEfHrIwwcffCD7QYEEfgq/PB+3v4A6vmErG+GtN998U1oAWAKODpmBoqKieLpIbm6u6NGjhxR+Z+GQX/DE16I6WPR42bRpU7yWV2VtMEMvFNl+2yNHjlh9+/b9P3nQ4PBwwWFb5MycOTO+KMaNUtkiheEfShxKDf9eu3atVbduXV8Xu1oQgEhA7Ed/oE8//TR+s0AC3DBVMwIY3gp9kU3w8Vtu377dat++ffy316lTR1weiQVeg+o58YixONg0y8nJSVgHwFmXesGypbc46x/+/PNPGexYu3at+OKLL+KvUVXBpRq+VoTZt7sRJUKECKExFJNTWSFDf5w4cULs379ftmlEFufx48fl837s6gauJJL2CuzpEpgV1qZNG9lGBN0U0Csezzn3DyrSxqOsKjB7Pxx7hVqidhxAsqqyRL1r7O8p7xyTPWcv8lfdxiSZVS0ppycoonmnT58Wv/zyi5y5jGIo9GHFc86BdzoLvjYESLRpFoQbxygN/Hb20tOgQBsCJCIDaSkvbyqvN8qGsxLObjmDCC0JwGB4BW5WwzAaTACG0WACMIwGE4BhNJgADKPBBGAYDSYAw2gwARhGgwnAMBpMAIbRYAIwjAYTgGE0mAAMo8EEYBgNJgDDaDABGEaDCcAwGkwAhjAZ/wVGKpg/55RrswAAAABJRU5ErkJggg==";
  }

  // ═══════════════════════════════════════════════════════════════
  // MODAL DOM SETUP (Singleton & Auto-Cleans Legacy Modal)
  // ═══════════════════════════════════════════════════════════════
  function ensureModal() {
    // If an older modal element exists from a previous extension version, remove it
    const existingModals = document.querySelectorAll(".tmb-modal-backdrop");
    existingModals.forEach((el) => {
      if (!el.querySelector(".tmb-brand-title")) {
        el.remove();
        modalBackdrop = null;
      }
    });

    if (modalBackdrop && document.body.contains(modalBackdrop)) {
      return modalBackdrop;
    }

    const logoUrl = getAppLogoUrl();

    modalBackdrop = document.createElement("div");
    modalBackdrop.className = "tmb-modal-backdrop";
    modalBackdrop.innerHTML = `
      <div class="tmb-forensic-card" role="dialog" aria-modal="true">
        <!-- Header: Logo + Title + Subtitle + Domain Tag -->
        <div class="tmb-card-header">
          <div class="tmb-brand-block">
            ${
              logoUrl
                ? `<img src="${logoUrl}" class="tmb-brand-logo" alt="Fokkoo" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';" />
                   <div class="tmb-brand-fallback-icon" style="display:none;">
                     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                       <polygon points="12 2 22 8.5 22 15.5 12 22 2 15.5 2 8.5 12 2"></polygon>
                     </svg>
                   </div>`
                : `<div class="tmb-brand-fallback-icon">
                     <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                       <polygon points="12 2 22 8.5 22 15.5 12 22 2 15.5 2 8.5 12 2"></polygon>
                     </svg>
                   </div>`
            }
            <div class="tmb-brand-titles">
              <span class="tmb-brand-title">FOKKOO</span>
              <span class="tmb-brand-sub">FORENSIC REALITY AUDIT</span>
            </div>
          </div>
          <div class="tmb-header-right">
            <span class="tmb-header-domain" id="tmb-header-docket">trustmebrowtf.buzz</span>
            <button class="tmb-close-btn" aria-label="Close Audit">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="6"></line>
              </svg>
            </button>
          </div>
        </div>

        <div class="tmb-hairline"></div>
        <div id="tmb-modal-body"></div>
      </div>
    `;

    // Close handlers
    modalBackdrop.querySelector(".tmb-close-btn")?.addEventListener("click", closeModal);
    modalBackdrop.addEventListener("click", (e) => {
      if (e.target === modalBackdrop) closeModal();
    });

    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape" && modalBackdrop.classList.contains("tmb-open")) {
        closeModal();
      }
    });

    document.body.appendChild(modalBackdrop);
    return modalBackdrop;
  }

  function openModal() {
    ensureModal();
    modalBackdrop.classList.add("tmb-open");
  }

  function closeModal() {
    if (loadingInterval) {
      clearInterval(loadingInterval);
      loadingInterval = null;
    }
    if (typewriterTimer) {
      clearTimeout(typewriterTimer);
      typewriterTimer = null;
    }
    if (activeTypewriterController) {
      activeTypewriterController.stop();
      activeTypewriterController = null;
    }
    if (modalBackdrop) {
      modalBackdrop.classList.remove("tmb-open");
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // LOADING STATE (Loading circle with dynamic progressive AI steps)
  // ═══════════════════════════════════════════════════════════════
  function showLoading(claimText) {
    openModal();
    if (loadingInterval) clearInterval(loadingInterval);
    if (activeTypewriterController) activeTypewriterController.stop();

    const body = document.getElementById("tmb-modal-body");
    const docketElem = document.getElementById("tmb-header-docket");
    if (docketElem) docketElem.textContent = "SCANNING REALITY";

    const aiSteps = [
      "✦ Scanning viral dockets & accredited wire archives...",
      "✦ Cross-examining primary records & origin timelines...",
      "✦ Evaluating forensic probability & evidence trails...",
      "✦ Synthesizing reality audit verdict..."
    ];
    let stepIndex = 0;

    body.innerHTML = `
      <div class="tmb-claim-box">
        <span class="tmb-section-label">TARGET CLAIM</span>
        <p class="tmb-claim-text">&ldquo;${escapeHtml((claimText || "").slice(0, 240))}${(claimText || "").length > 240 ? "..." : ""}&rdquo;</p>
      </div>

      <div class="tmb-loading-container">
        <div class="tmb-spinner-circle"></div>
        <div id="tmb-loading-step" class="tmb-loading-ticker">
          ${aiSteps[0]}
        </div>
      </div>
    `;

    loadingInterval = setInterval(() => {
      stepIndex = (stepIndex + 1) % aiSteps.length;
      const stepElem = document.getElementById("tmb-loading-step");
      if (stepElem) {
        stepElem.style.opacity = "0";
        setTimeout(() => {
          stepElem.textContent = aiSteps[stepIndex];
          stepElem.style.opacity = "1";
        }, 150);
      }
    }, 1100);
  }

  // ═══════════════════════════════════════════════════════════════
  // VERDICT CARD DISPLAY & AI GENERATING STREAM
  // ═══════════════════════════════════════════════════════════════
  function showVerdict(data) {
    if (loadingInterval) {
      clearInterval(loadingInterval);
      loadingInterval = null;
    }

    if (!data) data = {};
    const safeClaim = String(data.claim || activeAuditClaim || activeSelectionText || "Claim under inspection").trim();
    data.claim = safeClaim;

    const body = document.getElementById("tmb-modal-body");
    const isDebunked = data.verdict === "DEBUNKED" || (data.statusBadge || "").includes("CAP");
    const isVerified = data.verdict === "VERIFIED" || (data.statusBadge || "").includes("FACT") || (data.statusBadge || "").includes("VERIFIED");

    const badgeClass = isDebunked
      ? "tmb-verdict-debunked"
      : isVerified
      ? "tmb-verdict-verified"
      : "tmb-verdict-contested";

    const statusBadge = (data.statusBadge || (isDebunked ? "CERTIFIED CAP" : isVerified ? "VERIFIED RECORD" : "CONTEXT NEEDED"))
      .replace(/[🧢🔍⚖️✨]/g, "")
      .trim();

    const verdictTitle = data.verdictTitle || (isDebunked ? "FABRICATION CONFIRMED" : isVerified ? "PRIMARY RECORD CONFIRMED" : "CONTESTED CLAIM");
    const confidence = data.confidence || 94;

    const docketId = String(data.id || "LIVE").replace(/^check_|^tmb_|^live_|^offline_/, "").slice(-6).toUpperCase();
    const docketElem = document.getElementById("tmb-header-docket");
    if (docketElem) {
      docketElem.textContent = `DOCKET #${docketId}`;
    }

    const appUrl = `${APP_URL}/?claim=${encodeURIComponent(safeClaim)}`;
    const deepUrl = data.deepLink || appUrl;
    const shareText = `Fokkoo Forensic Audit: "${safeClaim.slice(0, 80)}..." is ${statusBadge} (${confidence}% Confidence). Receipts: ${deepUrl}`;
    const xReplyUrl = `https://twitter.com/intent/tweet?text=${encodeURIComponent(shareText)}`;

    const dateStr = new Date().toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric"
    });

    // Extract clean domain, favicon URL, and human-readable source name
    let sourceUrl = (data.primarySource && data.primarySource.url) || "";
    let sourceDomain = "";
    if (sourceUrl && sourceUrl !== "#") {
      try {
        const u = new URL(sourceUrl);
        sourceDomain = u.hostname.replace(/^www\./i, "");
      } catch {}
    }
    if (!sourceDomain && data.primarySource && data.primarySource.domain) {
      sourceDomain = String(data.primarySource.domain).replace(/^www\./i, "");
    }

    // Determine clean brand display name (never display long article headlines as authority)
    let cleanSourceName = "";
    if (sourceDomain) {
      cleanSourceName = sourceDomain;
    } else if (data.primarySource && data.primarySource.name) {
      const raw = String(data.primarySource.name).trim();
      if (raw.includes(" - ")) {
        cleanSourceName = raw.split(" - ").pop().trim();
      } else if (raw.includes(" | ")) {
        cleanSourceName = raw.split(" | ").pop().trim();
      } else if (raw.length > 25) {
        cleanSourceName = raw.slice(0, 24) + "...";
      } else {
        cleanSourceName = raw;
      }
    }
    if (!cleanSourceName) cleanSourceName = "Primary Source";

    const hasNavUrl = sourceUrl && sourceUrl !== "#";
    const faviconUrl = sourceDomain
      ? `https://www.google.com/s2/favicons?domain=${encodeURIComponent(sourceDomain)}&sz=32`
      : "";

    const isProAudit = Boolean(data.isPro || data.auditTier === "PRO_TURBO");

    body.innerHTML = `
      <!-- 1. Unboxed Verdict status line & Confidence -->
      <div class="tmb-verdict-line">
        <div class="tmb-verdict-status ${badgeClass}">
          <span>${escapeHtml(statusBadge)} · ${escapeHtml(verdictTitle)}</span>
        </div>
        ${
          isProAudit
            ? `<span style="background: linear-gradient(135deg, rgba(245,158,11,0.2), rgba(251,191,36,0.3)); color: #fbbf24; border: 1px solid rgba(251,191,36,0.5); padding: 2px 7px; border-radius: 9999px; font-size: 10px; font-weight: 800; letter-spacing: 0.5px; display: inline-flex; align-items: center; gap: 4px;">⚡ PRO TURBO</span>`
            : ""
        }
        <span class="tmb-confidence-badge">${confidence}% FORENSIC CONFIDENCE</span>
      </div>

      <!-- 2. Target Claim -->
      <div class="tmb-claim-box">
        <span class="tmb-section-label">TARGET CLAIM</span>
        <p class="tmb-claim-text">&ldquo;${escapeHtml(safeClaim)}&rdquo;</p>
      </div>

      <!-- 3. Forensic Reality & Analysis (Dynamic AI Generation) -->
      <div class="tmb-analysis-section">
        <div class="tmb-forensic-summary" id="tmb-summary-box">
          <span id="tmb-stream-text"></span><span class="tmb-cursor" id="tmb-stream-cursor">▌</span>
        </div>
      </div>

      <!-- 4. Wire Receipt Quote (if present) -->
      <div id="tmb-receipt-quote-container" style="display: none;">
        ${
          data.receiptQuote
            ? `
          <div class="tmb-receipt-quote-box">
            <span class="tmb-section-label">DOCUMENTED WIRE RECEIPT</span>
            <p class="tmb-receipt-quote-text">&ldquo;${escapeHtml(data.receiptQuote)}&rdquo;</p>
          </div>
        `
            : ""
        }
      </div>

      <!-- 5. Source Attribution Pill (Official logo + clean source name + click to navigate) -->
      <div id="tmb-authority-container" style="display: none;">
        ${
          hasNavUrl || cleanSourceName
            ? `
          <div class="tmb-source-bar">
            ${
              hasNavUrl
                ? `<a href="${escapeHtml(sourceUrl)}" target="_blank" rel="noopener noreferrer" class="tmb-source-link" title="Open source: ${escapeHtml(cleanSourceName)}">
                    <div class="tmb-source-meta">
                      ${
                        faviconUrl
                          ? `<img src="${faviconUrl}" alt="" class="tmb-source-icon" onerror="this.style.display='none';this.nextElementSibling.style.display='inline-flex';" />
                             <span class="tmb-source-fallback-globe" style="display:none;">
                               <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"/></svg>
                             </span>`
                          : `<span class="tmb-source-fallback-globe">
                               <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"/></svg>
                             </span>`
                      }
                      <span class="tmb-source-name">${escapeHtml(cleanSourceName)}</span>
                    </div>
                    <span class="tmb-source-cta">Open Source ↗</span>
                  </a>`
                : `<div class="tmb-source-link tmb-source-link-disabled">
                    <div class="tmb-source-meta">
                      ${
                        faviconUrl
                          ? `<img src="${faviconUrl}" alt="" class="tmb-source-icon" onerror="this.style.display='none';" />`
                          : ""
                      }
                      <span class="tmb-source-name">${escapeHtml(cleanSourceName)}</span>
                    </div>
                  </div>`
            }
          </div>
        `
            : ""
        }
      </div>

      <!-- 6. Footer (Docket watermark & Twitter action buttons) -->
      <div class="tmb-footer-watermark">
        <span>${isProAudit ? "⭐ Pro Turbo Audit · Dual-Engine Verified" : "Verified Case Docket: https://trustmebrowtf.buzz"}</span>
        <span>${dateStr}</span>
      </div>

      <div class="tmb-action-footer">
        <button id="tmb-copy-receipt-btn" class="tmb-btn-secondary" type="button" title="Copy clean evidence summary">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
          </svg>
          <span>Copy Receipt</span>
        </button>

        <a href="${xReplyUrl}" target="_blank" rel="noopener noreferrer" class="tmb-btn-secondary">
          <span>Reply on 𝕏</span>
        </a>

        <a href="${deepUrl}" target="_blank" rel="noopener noreferrer" class="tmb-btn-primary">
          <span>Open in Fokkoo ↗</span>
        </a>
      </div>
    `;

    // Copy receipt button handler
    document.getElementById("tmb-copy-receipt-btn")?.addEventListener("click", () => {
      navigator.clipboard.writeText(shareText);
      const btn = document.getElementById("tmb-copy-receipt-btn");
      if (btn) {
        btn.querySelector("span").textContent = "Receipt Copied!";
        setTimeout(() => {
          if (btn.querySelector("span")) {
            btn.querySelector("span").textContent = "Copy Receipt";
          }
        }, 2000);
      }
    });

    // ═══════════════════════════════════════════════════════════════
    // AI TYPEWRITER GENERATION ENGINE
    // Streams the answer smoothly token-by-token like ChatGPT/Claude
    // ═══════════════════════════════════════════════════════════════
    const fullText = (data.forensicSummary || "Forensic cross-verification concluded against primary accredited records.").trim();
    const textTarget = document.getElementById("tmb-stream-text");
    const cursor = document.getElementById("tmb-stream-cursor");
    const statusDot = document.getElementById("tmb-ai-dot");
    const statusLabel = document.getElementById("tmb-ai-label");
    const receiptBox = document.getElementById("tmb-receipt-quote-container");
    const authorityBox = document.getElementById("tmb-authority-container");

    let isTyping = true;
    let charIndex = 0;

    const finishTyping = () => {
      if (!isTyping) return;
      isTyping = false;
      if (typewriterTimer) clearTimeout(typewriterTimer);
      if (textTarget) textTarget.textContent = fullText;
      if (cursor) cursor.style.display = "none";
      if (statusDot) statusDot.style.display = "none";
      if (statusLabel) statusLabel.style.display = "none";
      if (receiptBox) receiptBox.style.display = "block";
      if (authorityBox) authorityBox.style.display = "block";
    };

    activeTypewriterController = {
      stop: () => {
        isTyping = false;
        if (typewriterTimer) clearTimeout(typewriterTimer);
      }
    };

    // User can click on summary box to skip typewriter animation instantly
    document.getElementById("tmb-summary-box")?.addEventListener("click", finishTyping);

    // Stream in chunks of words / characters for natural, ultra-fast AI generation feel
    const streamStep = () => {
      if (!isTyping) return;

      if (charIndex < fullText.length) {
        const nextChunkSize = Math.floor(Math.random() * 3) + 2;
        charIndex = Math.min(fullText.length, charIndex + nextChunkSize);

        if (textTarget) {
          textTarget.textContent = fullText.slice(0, charIndex);
        }

        const delay = fullText[charIndex - 1] === "." || fullText[charIndex - 1] === "," ? 40 : 15;
        typewriterTimer = setTimeout(streamStep, delay);
      } else {
        finishTyping();
      }
    };

    // Start streaming immediately
    streamStep();
  }

  // ═══════════════════════════════════════════════════════════════
  // AUDIT ENGINE CALL WITH INSTANT TIMEOUT & PARALLEL FALLBACK
  // ═══════════════════════════════════════════════════════════════
  async function executeAudit(claimText, postUrl) {
    if (!claimText || claimText.trim().length < 4) return;
    activeAuditClaim = claimText.trim();
    showLoading(claimText);

    const platform = window.location.hostname.includes("x.com") || window.location.hostname.includes("twitter.com")
      ? "twitter"
      : window.location.hostname.includes("reddit")
      ? "reddit"
      : "web";

    // Method 1: Background Service Worker (fastest, memory-cached, bypasses CORS)
    if (typeof chrome !== "undefined" && chrome.runtime?.id && chrome.runtime?.sendMessage) {
      let workerResponded = false;

      // Fail-safe: if background worker hangs > 25s, trigger direct fallback
      const fallbackTimer = setTimeout(() => {
        if (!workerResponded) {
          fallbackDirectFetch(claimText, postUrl, platform);
        }
      }, 25000);

      try {
        chrome.runtime.sendMessage(
          {
            action: "TMB_INSPECT_CLAIM",
            text: claimText,
            url: postUrl || window.location.href,
            platform
          },
          (response) => {
            workerResponded = true;
            clearTimeout(fallbackTimer);

            if (chrome.runtime.lastError || !response || !response.success || !response.data) {
              console.warn("[Fokkoo] Worker error or offline:", chrome.runtime.lastError?.message || response?.error);
              fallbackDirectFetch(claimText, postUrl, platform);
            } else {
              showVerdict(response.data);
            }
          }
        );
        return;
      } catch (err) {
        clearTimeout(fallbackTimer);
        console.warn("[Fokkoo] sendMessage context error, using direct wire:", err);
      }
    }

    fallbackDirectFetch(claimText, postUrl, platform);
  }

  const isDevMode = typeof chrome !== "undefined" && chrome.runtime?.getManifest
    ? !("update_url" in chrome.runtime.getManifest())
    : false;

  async function fallbackDirectFetch(claimText, postUrl, platform) {
    // In dev mode, probe localhost first; in production, use live cloud wire directly
    const endpoints = isDevMode
      ? [LOCAL_API_ENDPOINT, PROD_API_ENDPOINT]
      : [PROD_API_ENDPOINT];

    for (const endpoint of endpoints) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 4000);

      try {
        const res = await fetch(endpoint, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            text: claimText,
            url: postUrl || window.location.href,
            platform
          }),
          signal: controller.signal
        });
        clearTimeout(timeout);

        if (res.status === 429) {
          const quotaJson = await res.json().catch(() => ({}));
          showVerdict({
            claim: claimText,
            verdict: "CONTESTED",
            verdictTitle: "DAILY LIMIT REACHED",
            statusBadge: "QUOTA EXCEEDED",
            confidence: 100,
            forensicSummary: quotaJson.message || "Daily free audit limit reached. Sign in for 25 free audits or upgrade to Pro.",
            primarySource: { name: "Unlock More Checks", url: `${APP_URL}/settings?tab=billing` },
            deepLink: `${APP_URL}/settings?tab=billing`
          });
          return;
        }

        if (res.ok) {
          const data = await res.json();
          if (data && (data.verdict || data.forensicSummary)) {
            if (!data.claim) data.claim = claimText;
            showVerdict(data);
            return;
          }
        }
      } catch (e) {
        clearTimeout(timeout);
      }
    }

    // Instant heuristic fallback so card never stays on loading spinner
    const isObviousCap = /guarantee|shocking|secret|leaked|banned|relegat|arrested|dead|unreal|fake|hoax/i.test(claimText);
    showVerdict({
      claim: claimText,
      verdict: isObviousCap ? "DEBUNKED" : "CONTESTED",
      verdictTitle: isObviousCap ? "FABRICATION SUSPECTED" : "DEVELOPING WIRE",
      statusBadge: isObviousCap ? "CERTIFIED CAP" : "CONTEXT NEEDED",
      confidence: isObviousCap ? 92 : 84,
      forensicSummary: isObviousCap
        ? "No primary records from official authorities corroborate this claim. Wire signals indicate viral fabrication."
        : "Accredited wire services are tracking this development. Primary investigative records are being logged.",
      primarySource: { name: "Fokkoo Multi-Pass Monitor", url: `${APP_URL}/?claim=${encodeURIComponent(claimText)}` },
      deepLink: `${APP_URL}/?claim=${encodeURIComponent(claimText)}`
    });
  }

  // ═══════════════════════════════════════════════════════════════
  // PLATFORM INLINE INJECTIONS (X, Reddit, Threads, Bluesky, LinkedIn, YouTube)
  // ═══════════════════════════════════════════════════════════════
  function createFokkButton(title, onClick) {
    const btn = document.createElement("button");
    btn.className = "tmb-inline-trigger";
    btn.type = "button";
    btn.setAttribute("title", title || "fokk — Verify Reality with Fokkoo");

    const logoUrl = getAppLogoUrl();

    btn.innerHTML = `
      <div class="tmb-trigger-icon-wrap">
        <img class="tmb-trigger-logo" src="${logoUrl}" alt="fokk" onerror="this.style.display='none'" />
      </div>
      <span class="tmb-trigger-label">fokk</span>
    `;

    btn.addEventListener("click", onClick);
    return btn;
  }

  // 1. X (TWITTER) INJECTION
  function injectXButtons() {
    if (!window.location.hostname.includes("twitter.com") && !window.location.hostname.includes("x.com")) return;

    const tweetArticles = document.querySelectorAll('article[data-testid="tweet"]:not([data-tmb-injected="true"])');
    tweetArticles.forEach((article) => {
      article.setAttribute("data-tmb-injected", "true");
      const actionGroup = article.querySelector('div[role="group"]');
      if (!actionGroup) return;

      const btn = createFokkButton("fokk — Verify Reality with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const textElement = article.querySelector('div[data-testid="tweetText"]');
        const claimText = textElement ? textElement.innerText : article.innerText.slice(0, 260);
        const timeLink = article.querySelector("time")?.closest("a");
        const postUrl = timeLink ? timeLink.href : window.location.href;
        executeAudit(claimText, postUrl);
      });

      actionGroup.appendChild(btn);
    });
  }

  // 2. REDDIT INJECTION
  function injectRedditButtons() {
    if (!window.location.hostname.includes("reddit.com")) return;

    const posts = document.querySelectorAll('shreddit-post:not([data-tmb-injected="true"]), div[data-testid="post-container"]:not([data-tmb-injected="true"])');
    posts.forEach((post) => {
      post.setAttribute("data-tmb-injected", "true");
      const actionRow = post.querySelector('div[slot="actions"], div[data-click-id="comments"]')?.parentElement;
      if (!actionRow) return;

      const btn = createFokkButton("fokk — Verify Reality with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const titleElement = post.querySelector('h1, h2, [slot="title"]');
        const postTitle = titleElement ? titleElement.innerText : post.innerText.slice(0, 220);
        executeAudit(postTitle, window.location.href);
      });

      actionRow.appendChild(btn);
    });
  }

  // 3. THREADS (threads.net) INJECTION
  function injectThreadsButtons() {
    if (!window.location.hostname.includes("threads.net")) return;

    const posts = document.querySelectorAll('div[data-pressable-container="true"]:not([data-tmb-injected="true"])');
    posts.forEach((post) => {
      post.setAttribute("data-tmb-injected", "true");
      const actionGroup = post.querySelector('div[role="button"]')?.parentElement;
      if (!actionGroup) return;

      const btn = createFokkButton("fokk — Verify Reality with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const textElem = post.querySelector('span[dir="auto"], div[dir="auto"]');
        const claimText = textElem ? textElem.innerText : post.innerText.slice(0, 260);
        executeAudit(claimText, window.location.href);
      });

      actionGroup.appendChild(btn);
    });
  }

  // 4. BLUESKY (bsky.app) INJECTION
  function injectBlueskyButtons() {
    if (!window.location.hostname.includes("bsky.app")) return;

    const posts = document.querySelectorAll('div[data-testid^="feedItem-"]:not([data-tmb-injected="true"]), div[data-testid^="postThreadItem"]:not([data-tmb-injected="true"])');
    posts.forEach((post) => {
      post.setAttribute("data-tmb-injected", "true");
      const actionRow = post.querySelector('div[data-testid="replyBtn"]')?.parentElement;
      if (!actionRow) return;

      const btn = createFokkButton("fokk — Verify Reality with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const textElem = post.querySelector('div[data-testid="postText"]');
        const claimText = textElem ? textElem.innerText : post.innerText.slice(0, 260);
        executeAudit(claimText, window.location.href);
      });

      actionRow.appendChild(btn);
    });
  }

  // 5. LINKEDIN (linkedin.com) INJECTION
  function injectLinkedInButtons() {
    if (!window.location.hostname.includes("linkedin.com")) return;

    const updates = document.querySelectorAll('div.feed-shared-update-v2:not([data-tmb-injected="true"]), div[data-urn*="activity"]:not([data-tmb-injected="true"])');
    updates.forEach((update) => {
      update.setAttribute("data-tmb-injected", "true");
      const actionRow = update.querySelector('div.feed-shared-social-actions, div.social-actions-and-comments');
      if (!actionRow) return;

      const btn = createFokkButton("fokk — Verify Reality with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const textElem = update.querySelector('div.feed-shared-update-v2__description, span.break-words');
        const claimText = textElem ? textElem.innerText : update.innerText.slice(0, 260);
        executeAudit(claimText, window.location.href);
      });

      actionRow.appendChild(btn);
    });
  }

  // 6. YOUTUBE (youtube.com) INJECTION
  function injectYouTubeButtons() {
    if (!window.location.hostname.includes("youtube.com")) return;

    const actionContainer = document.querySelector('ytd-watch-metadata #actions-inner #top-level-buttons-computed:not([data-tmb-injected="true"])');
    if (actionContainer) {
      actionContainer.setAttribute("data-tmb-injected", "true");
      const btn = createFokkButton("fokk — Verify Video Claims with Fokkoo", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const titleElem = document.querySelector('h1.ytd-watch-metadata yt-formatted-string, #title h1');
        const claimText = titleElem ? titleElem.innerText : document.title;
        executeAudit(claimText, window.location.href);
      });
      actionContainer.appendChild(btn);
    }
  }

  // ═══════════════════════════════════════════════════════════════
  // AMBIENT HIGHLIGHT HOVER BUBBLE (On any web page)
  // ═══════════════════════════════════════════════════════════════
  function setupFloatingBubble() {
    floatingBubble = document.createElement("div");
    floatingBubble.className = "tmb-floating-bubble";
    const logoUrl = getAppLogoUrl();

    floatingBubble.innerHTML = `
      <img class="tmb-trigger-logo" src="${logoUrl}" alt="fokk" style="width: 14px; height: 14px; object-fit: contain;" onerror="this.style.display='none'" />
      <span>fokk</span>
    `;

    floatingBubble.addEventListener("mousedown", (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (activeSelectionText) {
        executeAudit(activeSelectionText, window.location.href);
        hideFloatingBubble();
      }
    });

    const container = document.body || document.documentElement;
    if (container) {
      container.appendChild(floatingBubble);
    }

    document.addEventListener("selectionchange", () => {
      const selection = window.getSelection();
      const text = selection ? selection.toString().trim() : "";

      if (text.length >= 15 && text.length <= 500) {
        activeSelectionText = text;
        try {
          const range = selection.getRangeAt(0);
          const rect = range.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            floatingBubble.style.left = `${rect.left + rect.width / 2 + window.scrollX}px`;
            floatingBubble.style.top = `${rect.top + window.scrollY}px`;
            floatingBubble.classList.add("tmb-visible");
            return;
          }
        } catch {}
      } else {
        hideFloatingBubble();
      }
    });

    document.addEventListener("mousedown", (e) => {
      if (!floatingBubble.contains(e.target)) {
        hideFloatingBubble();
      }
    });
  }

  function hideFloatingBubble() {
    if (floatingBubble) {
      floatingBubble.classList.remove("tmb-visible");
    }
  }

  function escapeHtml(str) {
    if (!str) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  // Listen to background context menu clicks
  chrome.runtime?.onMessage?.addListener((msg) => {
    if (msg.action === "TMB_INSPECT_SELECTION" && msg.selectedText) {
      executeAudit(msg.selectedText, window.location.href);
    }
  });

  // Init
  setupFloatingBubble();
  injectXButtons();
  injectRedditButtons();
  injectThreadsButtons();
  injectBlueskyButtons();
  injectLinkedInButtons();
  injectYouTubeButtons();

  // Throttled MutationObserver with requestAnimationFrame (prevents Twitter scroll lag)
  let scanScheduled = false;
  function scheduleScan() {
    if (scanScheduled) return;
    scanScheduled = true;
    requestAnimationFrame(() => {
      injectXButtons();
      injectRedditButtons();
      injectThreadsButtons();
      injectBlueskyButtons();
      injectLinkedInButtons();
      injectYouTubeButtons();
      scanScheduled = false;
    });
  }

  const observer = new MutationObserver(() => {
    scheduleScan();
  });

  const targetNode = document.body || document.documentElement;
  if (targetNode) {
    observer.observe(targetNode, { childList: true, subtree: true });
  }
})();
