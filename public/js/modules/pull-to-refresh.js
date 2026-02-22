const ptrState = {
    indicator: null, indicatorText: null, refreshCallback: null,
    startY: null, pullDistance: 0, pulling: false, refreshing: false
};
const triggerDistance = 72;
const maxPull = 110;

function resetIndicator() {
    ptrState.indicator.classList.remove('active', 'ready', 'loading');
    ptrState.indicator.style.transform = 'translate(-50%, -140%)';
    ptrState.indicatorText.textContent = 'Потяните для обновления';
    ptrState.startY = null; ptrState.pullDistance = 0; ptrState.pulling = false;
}

function handleTouchStart(event) {
    if (ptrState.refreshing || (window.scrollY || document.documentElement.scrollTop || 0) > 0) return;
    ptrState.startY = event.touches[0].clientY;
    ptrState.pullDistance = 0; ptrState.pulling = true;
}

function handleTouchMove(event) {
    if (!ptrState.pulling || ptrState.startY === null || ptrState.refreshing) return;
    const deltaY = event.touches[0].clientY - ptrState.startY;
    if (deltaY <= 0) return resetIndicator();

    ptrState.pullDistance = Math.min(deltaY, maxPull);
    const progress = Math.min(ptrState.pullDistance / triggerDistance, 1);

    ptrState.indicator.classList.add('active');
    ptrState.indicator.classList.toggle('ready', ptrState.pullDistance >= triggerDistance);
    ptrState.indicator.style.transform = `translate(-50%, ${-140 + (progress * 165)}%)`;
    ptrState.indicatorText.textContent = ptrState.pullDistance >= triggerDistance
        ? 'Отпустите, чтобы обновить' : 'Потяните для обновления';
}

async function handleTouchEnd() {
    if (!ptrState.pulling) return;
    ptrState.pulling = false;

    if (ptrState.pullDistance < triggerDistance || ptrState.refreshing) return resetIndicator();

    ptrState.refreshing = true;
    ptrState.indicator.classList.add('active', 'loading');
    ptrState.indicator.classList.remove('ready');
    ptrState.indicator.style.transform = 'translate(-50%, 0%)';
    ptrState.indicatorText.textContent = 'Обновляем...';

    await ptrState.refreshCallback();
    ptrState.refreshing = false;
    resetIndicator();
}

export function setupPullToRefresh(refreshCallback) {
    ptrState.indicator = document.getElementById('pull-refresh-indicator');
    ptrState.indicatorText = document.getElementById('pull-refresh-indicator-text');
    if (!ptrState.indicator || !ptrState.indicatorText || typeof refreshCallback !== 'function') return;

    if (!('ontouchstart' in window || navigator.maxTouchPoints > 0) || !window.matchMedia('(max-width: 900px)').matches) return;

    ptrState.refreshCallback = refreshCallback;
    document.addEventListener('touchstart', handleTouchStart, { passive: true });
    document.addEventListener('touchmove', handleTouchMove, { passive: true });
    document.addEventListener('touchend', handleTouchEnd, { passive: true });
}
