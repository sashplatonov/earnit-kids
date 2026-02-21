export function setupPullToRefresh(refreshCallback) {
    const indicator = document.getElementById('pull-refresh-indicator');
    const indicatorText = document.getElementById('pull-refresh-indicator-text');
    if (!indicator || !indicatorText || typeof refreshCallback !== 'function') return;

    const isTouchCapable = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
    const isMobileViewport = window.matchMedia('(max-width: 900px)').matches;
    if (!isTouchCapable || !isMobileViewport) return;

    const triggerDistance = 72;
    const maxPull = 110;
    let startY = null;
    let pullDistance = 0;
    let pulling = false;
    let refreshing = false;

    const resetIndicator = () => {
        indicator.classList.remove('active', 'ready', 'loading');
        indicator.style.transform = 'translate(-50%, -140%)';
        indicatorText.textContent = 'Потяните для обновления';
        startY = null;
        pullDistance = 0;
        pulling = false;
    };

    document.addEventListener('touchstart', (event) => {
        if (refreshing) return;
        if ((window.scrollY || document.documentElement.scrollTop || 0) > 0) return;

        const touch = event.touches[0];
        startY = touch.clientY;
        pullDistance = 0;
        pulling = true;
    }, { passive: true });

    document.addEventListener('touchmove', (event) => {
        if (!pulling || startY === null || refreshing) return;

        const touch = event.touches[0];
        const deltaY = touch.clientY - startY;
        if (deltaY <= 0) {
            resetIndicator();
            return;
        }

        pullDistance = Math.min(deltaY, maxPull);
        const progress = Math.min(pullDistance / triggerDistance, 1);
        const translateY = -140 + (progress * 165);

        indicator.classList.add('active');
        indicator.classList.toggle('ready', pullDistance >= triggerDistance);
        indicator.style.transform = `translate(-50%, ${translateY}%)`;
        indicatorText.textContent = pullDistance >= triggerDistance
            ? 'Отпустите, чтобы обновить'
            : 'Потяните для обновления';
    }, { passive: true });

    document.addEventListener('touchend', async () => {
        if (!pulling) return;
        pulling = false;

        if (pullDistance < triggerDistance || refreshing) {
            resetIndicator();
            return;
        }

        refreshing = true;
        indicator.classList.add('active', 'loading');
        indicator.classList.remove('ready');
        indicator.style.transform = 'translate(-50%, 0%)';
        indicatorText.textContent = 'Обновляем...';

        await refreshCallback();

        refreshing = false;
        resetIndicator();
    }, { passive: true });
}
