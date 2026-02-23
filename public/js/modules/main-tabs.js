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
    if (moreDropdown) {
        moreDropdown.classList.add('hidden');
        moreDropdown.classList.remove('is-floating');
        moreDropdown.style.top = '';
        moreDropdown.style.right = '';
        moreDropdown.style.bottom = '';
        moreDropdown.style.left = '';
        restoreDropdownFromBody(moreDropdown);
    }
    if (moreBtn) moreBtn.setAttribute('aria-expanded', 'false');
    document.querySelectorAll('.child-menu.active').forEach(el => el.classList.remove('active'));
}

function moveDropdownToBody(moreDropdown) {
    if (!moreDropdown || moreDropdown.dataset.portalHost === 'body') return;

    const parent = moreDropdown.parentElement;
    if (!parent) return;

    moreDropdown.__portalParent = parent;
    moreDropdown.__portalNext = moreDropdown.nextElementSibling;
    document.body.appendChild(moreDropdown);
    moreDropdown.dataset.portalHost = 'body';
}

function restoreDropdownFromBody(moreDropdown) {
    if (!moreDropdown || moreDropdown.dataset.portalHost !== 'body') return;

    const parent = moreDropdown.__portalParent;
    const next = moreDropdown.__portalNext;

    if (parent) {
        if (next && next.parentElement === parent) {
            parent.insertBefore(moreDropdown, next);
        } else {
            parent.appendChild(moreDropdown);
        }
    }

    delete moreDropdown.__portalParent;
    delete moreDropdown.__portalNext;
    delete moreDropdown.dataset.portalHost;
}

function positionMoreDropdown(moreBtn, moreDropdown) {
    if (!moreBtn || !moreDropdown || moreDropdown.classList.contains('hidden')) return;

    const rect = moreBtn.getBoundingClientRect();
    const gap = 8;
    const screenPadding = 8;
    const isMobile = window.matchMedia('(max-width: 768px)').matches;

    moreDropdown.classList.add('is-floating');

    const dropdownWidth = Math.max(moreDropdown.offsetWidth || 0, 220);
    const maxLeft = window.innerWidth - dropdownWidth - screenPadding;
    const left = Math.min(Math.max(screenPadding, rect.right - dropdownWidth), maxLeft);
    moreDropdown.style.left = `${Math.round(left)}px`;

    if (isMobile) {
        const bottom = (window.innerHeight - rect.top) + gap;
        moreDropdown.style.top = '';
        moreDropdown.style.right = '';
        moreDropdown.style.bottom = `${Math.round(bottom)}px`;
    } else {
        const top = rect.bottom + gap;
        moreDropdown.style.top = `${Math.round(top)}px`;
        moreDropdown.style.right = '';
        moreDropdown.style.bottom = '';
    }
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
            if (hide) {
                moreDropdown.classList.add('hidden');
                restoreDropdownFromBody(moreDropdown);
            } else {
                moveDropdownToBody(moreDropdown);
                moreDropdown.classList.remove('hidden');
            }
            moreBtn.setAttribute('aria-expanded', String(!hide));
            if (!hide) {
                document.querySelectorAll('.child-menu.active').forEach(el => el.classList.remove('active'));
                requestAnimationFrame(() => positionMoreDropdown(moreBtn, moreDropdown));
            }
        });
        document.addEventListener('click', (e) => {
            if (e.target.closest('.nav__more-wrapper')) return;
            if (moreDropdown.contains(e.target)) return;
            closeDropdowns(moreBtn, moreDropdown);
            restoreDropdownFromBody(moreDropdown);
        });
        window.addEventListener('resize', () => positionMoreDropdown(moreBtn, moreDropdown), { passive: true });
        document.addEventListener('scroll', () => positionMoreDropdown(moreBtn, moreDropdown), true);
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
