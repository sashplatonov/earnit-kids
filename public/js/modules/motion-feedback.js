/** @file Motion helpers for stagger reveal and coin burst feedback */
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
