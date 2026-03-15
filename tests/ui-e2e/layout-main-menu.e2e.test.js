const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '../..');

function read(relativePath) {
    return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function normalizeTemplate(html) {
    return html
        .replace(/\{\{APP_VERSION\}\}/g, '1.0.0-test')
        .replace(/\{\{BUILD_VERSION\}\}/g, 'test-build')
        .replace(/\{\{CLARITY_SCRIPT\}\}/g, '');
}

function getInlineScript() {
    return `<script>
    (() => {
        const moreBtn = document.getElementById('nav-more-btn');
        const moreDropdown = document.getElementById('nav-more-dropdown');
        if (moreBtn && moreDropdown) {
            moreBtn.addEventListener('click', () => moreDropdown.classList.toggle('hidden'));
        }
        function activateTab(tab) {
            document.querySelectorAll('.section').forEach(s => s.classList.add('hidden'));
            const sId = tab === 'child-link' ? 'child-link-section' : tab + '-section';
            const target = document.getElementById(sId);
            if (target) target.classList.remove('hidden');
            document.querySelectorAll('.nav__btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
        }
        document.querySelectorAll('[data-tab]').forEach(b => {
            b.addEventListener('click', e => { e.preventDefault(); activateTab(b.dataset.tab); });
        });
        activateTab('tasks');
    })();
    </script>`;
}

function buildDashboardHtml() {
    const css = read('public/css/style.css');
    const header = read('views/components/header.html');
    const nav = read('views/components/nav.html');
    const mStart = read('views/components/main_start.html');
    const mEnd = normalizeTemplate(read('views/components/main_end.html'));
    const sFiles = [
        'views/components/section_today.html', 'views/components/section_tasks.html',
        'views/components/section_shop.html',
        'views/components/section_requests.html', 'views/components/section_analytics.html',
        'views/components/section_history.html',
        'views/components/section_catalog.html', 'views/components/section_rules.html',
        'views/components/section_friends.html',
        'views/components/section_settings.html'
    ];
    const ss = sFiles.map(read).join('\n');

    return `<!doctype html><html lang="ru"><head><meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>UI Fixture</title><style>${css}</style></head>
<body>${header}${nav}${mStart}${ss}${mEnd}${getInlineScript()}</body></html>`;
}

async function loadFixture(page) {
    await page.setContent(buildDashboardHtml(), { waitUntil: 'domcontentloaded' });
}

async function assertNoHorizontalOverflow(page) {
    const metrics = await page.evaluate(() => ({
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: window.innerWidth,
    }));

    expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.innerWidth + 2);
}

async function assertMainMenuTabs(page) {
    const primaryTabs = ['today', 'tasks', 'shop'];
    for (const tab of primaryTabs) {
        await page.click(`.nav__btn[data-tab="${tab}"]`);
        await expect(page.locator(`#${tab}-section`)).toBeVisible();
        await assertNoHorizontalOverflow(page);
    }

    const moreTabs = ['requests', 'history', 'analytics', 'rules', 'friends', 'settings'];
    for (const tab of moreTabs) {
        await page.click('#nav-more-btn');
        await page.click(`.nav__dropdown-item[data-tab="${tab}"]`);
        await expect(page.locator(`#${tab}-section`)).toBeVisible();
        await assertNoHorizontalOverflow(page);
    }
}

test('desktop: main menu sections are displayed correctly', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await loadFixture(page);

    await assertMainMenuTabs(page);
});

test('mobile: main menu sections are displayed correctly', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await loadFixture(page);

    await assertMainMenuTabs(page);
});
