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

function buildDashboardHtml() {
    const css = read('public/css/style.css');
    const header = read('views/components/header.html');
    const nav = read('views/components/nav.html');
    const mainStart = read('views/components/main_start.html');
    const mainEnd = normalizeTemplate(read('views/components/main_end.html'));

    const sectionFiles = [
        'views/components/section_tasks.html',
        'views/components/section_requests.html',
        'views/components/section_shop.html',
        'views/components/section_history.html',
        'views/components/section_catalog.html',
        'views/components/section_rules.html',
        'views/components/section_about.html',
        'views/components/section_friends.html',
        'views/components/section_settings.html',
    ];

    const sections = sectionFiles.map(read).join('\n');

    return `<!doctype html>
<html lang="ru">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>UI Layout Fixture</title>
  <style>${css}</style>
</head>
<body>
${header}
${nav}
${mainStart}
${sections}
${mainEnd}
<script>
(() => {
    const moreBtn = document.getElementById('nav-more-btn');
    const moreDropdown = document.getElementById('nav-more-dropdown');

    if (moreBtn && moreDropdown) {
        moreBtn.addEventListener('click', () => {
            moreDropdown.classList.toggle('hidden');
        });
    }

    function activateTab(tab) {
        document.querySelectorAll('.section').forEach((section) => {
            section.classList.add('hidden');
        });

        const sectionId = tab === 'child-link' ? 'child-link-section' : tab + '-section';
        const target = document.getElementById(sectionId);
        if (target) target.classList.remove('hidden');

        document.querySelectorAll('.nav__btn').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
    }

    document.querySelectorAll('[data-tab]').forEach((button) => {
        button.addEventListener('click', (event) => {
            event.preventDefault();
            const tab = button.dataset.tab;
            activateTab(tab);
        });
    });

    activateTab('tasks');
})();
</script>
</body>
</html>`;
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
    const primaryTabs = ['tasks', 'requests', 'shop', 'history'];
    for (const tab of primaryTabs) {
        await page.click(`.nav__btn[data-tab="${tab}"]`);
        await expect(page.locator(`#${tab}-section`)).toBeVisible();
        await assertNoHorizontalOverflow(page);
    }

    const moreTabs = ['rules', 'about', 'friends', 'settings'];
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
