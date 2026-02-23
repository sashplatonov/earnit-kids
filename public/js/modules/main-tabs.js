async function toggleTab(tabButtons, tabName) {
    if (!tabName) return;

    const performSwitch = async () => {
        tabButtons.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tabName));
        document.querySelectorAll('.section').forEach(s => s.classList.add('hidden'));
        const target = document.getElementById(`${tabName}-section`);
        if (target) target.classList.remove('hidden');
        if (tabName === 'analytics') {
            const { loadAnalytics } = await import('./analytics-ui.js');
            loadAnalytics();
        }
    };

    if (document.startViewTransition) {
        document.startViewTransition(performSwitch);
    } else {
        await performSwitch();
    }
}

function closeDropdowns(moreBtn, moreDropdown) {
    if (moreDropdown) moreDropdown.classList.add('hidden');
    if (moreBtn) moreBtn.setAttribute('aria-expanded', 'false');
    document.querySelectorAll('.child-menu.active').forEach(el => el.classList.remove('active'));
}

function setupSwipeGestures(activate) {
    let touchStartX = 0;
    let touchEndX = 0;

    const handleGesture = () => {
        const diff = touchStartX - touchEndX;
        if (Math.abs(diff) < 80) return;

        if (document.activeElement?.closest('.about-gallery, .chart-container')) return;

        const currentActiveBtn = document.querySelector('.nav__btn.active');
        if (!currentActiveBtn) return;

        const visibleTabs = Array.from(document.querySelectorAll('.nav__btn'))
            .filter(btn => !btn.classList.contains('hidden') &&
                getComputedStyle(btn).display !== 'none');

        const currentIndex = visibleTabs.indexOf(currentActiveBtn);
        if (currentIndex === -1) return;

        if (diff > 0 && currentIndex < visibleTabs.length - 1) {
            activate(visibleTabs[currentIndex + 1].dataset.tab);
        } else if (diff < 0 && currentIndex > 0) {
            activate(visibleTabs[currentIndex - 1].dataset.tab);
        }
    };

    document.addEventListener('touchstart', e => { touchStartX = e.changedTouches[0].screenX; }, { passive: true });
    document.addEventListener('touchend', e => {
        touchEndX = e.changedTouches[0].screenX;
        handleGesture();
    }, { passive: true });
}

export function setupTabControls() {
    const tabButtons = document.querySelectorAll('.nav__btn, .nav__dropdown-item');
    const moreBtn = document.getElementById('nav-more-btn');
    const moreDropdown = document.getElementById('nav-more-dropdown');

    const activate = (name) => {
        toggleTab(tabButtons, name);
        closeDropdowns(moreBtn, moreDropdown);
    };

    tabButtons.forEach(btn => btn.addEventListener('click', () => activate(btn.dataset.tab)));
    setupSwipeGestures(activate);

    if (moreBtn && moreDropdown) {
        moreBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const hide = !moreDropdown.classList.contains('hidden');
            moreDropdown.classList.toggle('hidden', hide);
            moreBtn.setAttribute('aria-expanded', String(!hide));
            if (!hide) document.querySelectorAll('.child-menu.active').forEach(el => el.classList.remove('active'));
        });
        document.addEventListener('click', (e) => { if (!e.target.closest('.nav__more-wrapper')) closeDropdowns(moreBtn, moreDropdown); });
    }

    document.querySelector('.header__balance')?.addEventListener('click', () => activate('history'));
    document.getElementById('analytics-timeframe-group')?.addEventListener('click', (e) => {
        const btn = e.target.closest('.tab-btn');
        if (btn) {
            document.querySelectorAll('#analytics-timeframe-group .tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            import('./analytics-ui.js').then(({ loadAnalytics }) => loadAnalytics(btn.dataset.timeframe));
        }
    });
    activate('tasks');
}
