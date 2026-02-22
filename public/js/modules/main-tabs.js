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

export function setupTabControls() {
    const tabButtons = document.querySelectorAll('.nav__btn, .nav__dropdown-item');
    const moreBtn = document.getElementById('nav-more-btn');
    const moreDropdown = document.getElementById('nav-more-dropdown');

    const activate = (name) => {
        toggleTab(tabButtons, name);
        closeDropdowns(moreBtn, moreDropdown);
    };

    tabButtons.forEach(btn => btn.addEventListener('click', () => activate(btn.dataset.tab)));

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

    const balanceBtn = document.querySelector('.header__balance');
    if (balanceBtn) balanceBtn.addEventListener('click', () => activate('history'));

    const timeframeGroup = document.getElementById('analytics-timeframe-group');
    if (timeframeGroup) {
        timeframeGroup.addEventListener('click', (e) => {
            const btn = e.target.closest('.tab-btn');
            if (btn) {
                timeframeGroup.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                import('./analytics-ui.js').then(({ loadAnalytics }) => {
                    loadAnalytics(btn.dataset.timeframe);
                });
            }
        });
    }
    activate('tasks');
}
