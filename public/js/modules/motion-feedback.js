/** @file Motion helpers for stagger reveal, coin burst feedback, and rich animations */
function allowMotion() {
    return typeof window !== 'undefined'
        && typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: no-preference)').matches;
}

function getAnchor(anchor) {
    if (anchor instanceof Element) return anchor;
    return document.querySelector(anchor) || document.body;
}

export function triggerCoinBurst(anchor = '.header__balance') {
    if (!allowMotion()) return;

    const anchorEl = getAnchor(anchor);
    const rect = anchorEl.getBoundingClientRect();
    const burst = document.createElement('div');
    burst.className = 'coin-burst';
    burst.style.left = `${rect.left + rect.width / 2}px`;
    burst.style.top = `${rect.top + rect.height / 2}px`;

    const vectors = [
        { x: 0, y: -70 },
        { x: 48, y: -48 },
        { x: 70, y: 0 },
        { x: 48, y: 48 },
        { x: 0, y: 70 },
        { x: -48, y: 48 },
        { x: -70, y: 0 },
        { x: -48, y: -48 }
    ];

    vectors.forEach((vector) => {
        const coin = document.createElement('span');
        coin.className = 'coin-burst__coin';
        coin.style.setProperty('--burst-x', `${vector.x}px`);
        coin.style.setProperty('--burst-y', `${vector.y}px`);
        burst.appendChild(coin);
    });

    document.body.appendChild(burst);
    window.setTimeout(() => burst.remove(), 520);
}

export function applyStaggerReveal(container) {
    if (!allowMotion() || !container) return;
    const cards = container.querySelectorAll('.card, .friend-item');
    cards.forEach((node, index) => {
        node.style.setProperty('--stagger-index', String(index));
        node.classList.remove('stagger-in');
        void node.offsetWidth;
        node.classList.add('stagger-in');
    });
}

/**
 * Loads canvas-confetti dynamically.
 */
function loadConfetti(successCallback, fallbackAnchor) {
    import('https://cdn.jsdelivr.net/npm/canvas-confetti@1.9.2/dist/confetti.module.mjs')
        .then((module) => {
            successCallback(module.default);
        })
        .catch((err) => {
            console.warn('Falling back to basic coin burst animation due to a module load error:', err);
            triggerCoinBurst(fallbackAnchor);
        });
}

export function triggerTaskAnimation(anchor = '.header__balance') {
    if (!allowMotion()) return;
    loadConfetti((confetti) => {
        // A star burst animation for task completion
        const defaults = {
            spread: 360,
            ticks: 50,
            gravity: 1,
            decay: 0.94,
            startVelocity: 30,
            shapes: ['star'],
            colors: ['#FFC107', '#FF9800', '#FFEB3B', '#4CAF50'],
            zIndex: 4000
        };

        const anchorEl = getAnchor(anchor);
        const rect = anchorEl.getBoundingClientRect();
        const originX = (rect.left + rect.width / 2) / window.innerWidth;
        const originY = (rect.top + rect.height / 2) / window.innerHeight;

        function shoot() {
            confetti({
                ...defaults,
                particleCount: 40,
                scalar: 1.2,
                origin: { x: originX, y: originY }
            });
            confetti({
                ...defaults,
                particleCount: 15,
                scalar: 0.75,
                origin: { x: originX, y: originY }
            });
        }
        shoot();
        setTimeout(shoot, 150);
        setTimeout(shoot, 300);
    }, anchor);
}

export function triggerPurchaseAnimation() {
    if (!allowMotion()) return;
    loadConfetti((confetti) => {
        // School pride (celebration from edges) for purchase
        const duration = 2500;
        const animationEnd = Date.now() + duration;
        let skew = 1;
        
        let frame;
        (function doFrame() {
            const timeLeft = animationEnd - Date.now();
            const ticks = Math.max(200, 500 * (timeLeft / duration));
            skew = Math.max(0.8, skew - 0.001);

            confetti({
                particleCount: 2,
                startVelocity: 0,
                ticks: ticks,
                origin: {
                    x: Math.random(),
                    // since particles fall down, skew start toward the top
                    y: (Math.random() * skew) - 0.2
                },
                colors: ['#FFC107', '#4CAF50', '#2196F3', '#E91E63', '#9C27B0'],
                shapes: ['square', 'circle'],
                gravity: 0.6,
                scalar: Math.random() * 0.8 + 0.6,
                drift: Math.random() - 0.5,
                zIndex: 4000
            });

            if (timeLeft > 0) {
                frame = requestAnimationFrame(doFrame);
            }
        }());
    });
}
