#!/usr/bin/env node
/**
 * Capture Mini App screenshots without a real Telegram session.
 *
 * This script uses Playwright to render the real SvelteKit Mini App while
 * stubbing the Telegram SDK and the backend API with realistic mock data.
 * Screenshots are written to the public site asset directory.
 */
import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { mkdir, writeFile } from 'node:fs/promises';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SITE_URL = process.env.SITE_URL ?? 'http://127.0.0.1:5001';
const OUT_DIR = join(__dirname, '..', 'static', 'public', 'assets', 'screenshots');

const VIEWPORT = { width: 375, height: 667 };
const CHILD_ID = 101;
const SECOND_CHILD_ID = 102;

const MOCK_ACCOUNT_CONNECTION = { ok: true };

const MOCK_AUTH_ADMIN = { role: 'admin', familyId: 'family-screenshots' };
const MOCK_AUTH_CHILD = { role: 'child', familyId: 'family-screenshots' };

const DETAILS_DATA = {
    requests: [
        {
            id: 1001,
            taskId: 2,
            taskName: '📚 Читать 20 минут',
            title: 'Выполнить задание',
            description: 'Mia просит отметить задание',
            groupName: 'Учёба',
            coins: 20,
            status: 'pending',
            requestType: 'task_completion',
            createdAt: '2025-01-15T09:15:00Z',
            childId: CHILD_ID,
        },
        {
            id: 1002,
            itemId: 10,
            itemName: '🎮 Игра 30 минут',
            title: 'Купить награду',
            description: 'Mia просит награду',
            groupName: 'Развлечения',
            coins: 30,
            status: 'pending',
            requestType: 'shop_purchase',
            createdAt: '2025-01-15T10:00:00Z',
            childId: CHILD_ID,
        },
    ],
    history: [
        {
            id: 2001,
            type: 'earn',
            amount: 20,
            title: '📚 Читать 20 минут',
            description: 'Читать 20 минут',
            taskId: 2,
            taskName: '📚 Читать 20 минут',
            groupName: 'Учёба',
            createdAt: '2025-01-14T08:30:00Z',
            childId: CHILD_ID,
        },
        {
            id: 2002,
            type: 'purchase',
            amount: -20,
            title: '🍦 Мороженое',
            description: 'Мороженое',
            itemId: 11,
            itemName: '🍦 Мороженое',
            groupName: 'Развлечения',
            createdAt: '2025-01-13T16:00:00Z',
            childId: CHILD_ID,
        },
        {
            id: 2003,
            type: 'earn',
            amount: 10,
            title: '🛏️ Заправить кровать',
            description: 'Заправить кровать',
            taskId: 1,
            taskName: '🛏️ Заправить кровать',
            groupName: 'Ежедневные дела',
            createdAt: '2025-01-12T07:45:00Z',
            childId: CHILD_ID,
        },
    ],
    friends: [],
};

const FAMILY_DATA_ADMIN = {
    isAdmin: true,
    balance: 120,
    rules: 'Earn coins by completing tasks, spend them on rewards.',
    monthlyLimit: 10000,
    dailyCoinLimit: 0,
    tasks: [
        {
            id: 1,
            name: '🛏️ Заправить кровать',
            coins: 10,
            groupName: 'Ежедневные дела',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
        {
            id: 2,
            name: '📚 Читать 20 минут',
            coins: 20,
            groupName: 'Учёба',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: '2025-01-14T08:30:00Z',
        },
        {
            id: 3,
            name: '🐕 Выгулять собаку',
            coins: 15,
            groupName: 'Ежедневные дела',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
        {
            id: 4,
            name: '🧹 Убраться в комнате',
            coins: 25,
            groupName: 'Ежедневные дела',
            isActive: false,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
    ],
    shop: [
        {
            id: 10,
            name: '🎮 Игра 30 минут',
            price: 30,
            groupName: 'Развлечения',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: null,
        },
        {
            id: 11,
            name: '🍦 Мороженое',
            price: 20,
            groupName: 'Развлечения',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: '2025-01-13T16:00:00Z',
        },
        {
            id: 12,
            name: '📺 Мультфильм 1 час',
            price: 40,
            groupName: 'Экранное время',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: null,
        },
    ],
    requests: DETAILS_DATA.requests,
    history: DETAILS_DATA.history.slice(0, 3),
    children: [
        {
            id: CHILD_ID,
            name: 'Mia',
            balance: 45,
            monthlyLimit: 10000,
            dailyCoinLimit: 0,
            dailyRewardLimit: 0,
            theme: null,
            taskGroupOrder: ['Daily routine', 'Study'],
            shopGroupOrder: ['Fun', 'Screen time'],
            childTaskGroupOrder: [],
            childShopGroupOrder: [],
            hiddenTaskGroups: [],
            hiddenShopGroups: [],
            rewardGoalItemId: null,
            status: 'ACTIVE',
        },
        {
            id: SECOND_CHILD_ID,
            name: 'Leo',
            balance: 12,
            monthlyLimit: 10000,
            dailyCoinLimit: 0,
            dailyRewardLimit: 0,
            theme: null,
            taskGroupOrder: [],
            shopGroupOrder: [],
            childTaskGroupOrder: [],
            childShopGroupOrder: [],
            hiddenTaskGroups: [],
            hiddenShopGroups: [],
            rewardGoalItemId: null,
            status: 'ACTIVE',
        },
    ],
    lastSelectedChildId: CHILD_ID,
    activeChildId: CHILD_ID,
    childNickname: null,
};

const FAMILY_DATA_CHILD = {
    isAdmin: false,
    balance: 45,
    rules: null,
    monthlyLimit: 10000,
    dailyCoinLimit: 0,
    childNickname: 'Mia',
    tasks: FAMILY_DATA_ADMIN.tasks,
    shop: FAMILY_DATA_ADMIN.shop,
    requests: DETAILS_DATA.requests,
    history: DETAILS_DATA.history.slice(0, 3),
    children: [],
    activeChildId: CHILD_ID,
};

const BASE_DATA = {
    tasks: [
        {
            id: 1,
            name: '🛏️ Заправить кровать',
            coins: 10,
            groupName: 'Ежедневные дела',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
        {
            id: 2,
            name: '📚 Читать 20 минут',
            coins: 20,
            groupName: 'Учёба',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: '2025-01-14T08:30:00Z',
        },
        {
            id: 3,
            name: '🐕 Выгулять собаку',
            coins: 15,
            groupName: 'Ежедневные дела',
            isActive: true,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
        {
            id: 4,
            name: '🧹 Убраться в комнате',
            coins: 25,
            groupName: 'Ежедневные дела',
            isActive: false,
            childId: CHILD_ID,
            lastCompletedAt: null,
        },
    ],
    products: [
        {
            id: 10,
            name: '🎮 Игра 30 минут',
            price: 30,
            groupName: 'Развлечения',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: null,
        },
        {
            id: 11,
            name: '🍦 Мороженое',
            price: 20,
            groupName: 'Развлечения',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: '2025-01-13T16:00:00Z',
        },
        {
            id: 12,
            name: '📺 Мультфильм 1 час',
            price: 40,
            groupName: 'Экранное время',
            isActive: true,
            childId: CHILD_ID,
            lastPurchasedAt: null,
        },
    ],
};

const HISTORY_PAGE = {
    items: DETAILS_DATA.history,
    total: 3,
    page: 1,
    limit: 20,
};

const PERF_INIT_SCRIPT = `
(() => {
    self.__miniAppPerf = { lcp: null, cls: 0, fcp: null, fp: null, ttfb: null };
    try {
        const lcpObserver = new PerformanceObserver((list) => {
            const entries = list.getEntries();
            if (entries.length) {
                self.__miniAppPerf.lcp = entries[entries.length - 1].startTime;
            }
        });
        lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true });
    } catch (_) {}
    try {
        const clsObserver = new PerformanceObserver((list) => {
            for (const entry of list.getEntries()) {
                if (!entry.hadRecentInput) {
                    self.__miniAppPerf.cls += entry.value;
                }
            }
        });
        clsObserver.observe({ type: 'layout-shift', buffered: true });
    } catch (_) {}
})();
`;

function json(body) {
    return { status: 200, contentType: 'application/json', body: JSON.stringify(body) };
}

async function collectMetrics(page, viewName) {
    const metrics = await page.evaluate(() => {
        const nav = performance.getEntriesByType('navigation')[0] || {};
        const paints = performance.getEntriesByType('paint');
        const fpEntry = paints.find((p) => p.name === 'first-paint');
        const fcpEntry = paints.find((p) => p.name === 'first-contentful-paint');
        const perf = self.__miniAppPerf || {};
        return {
            navigationStart: nav.startTime ?? 0,
            ttfb: nav.responseStart ?? perf.ttfb ?? null,
            domContentLoaded: nav.domContentLoadedEventEnd ?? null,
            loadComplete: nav.loadEventEnd ?? null,
            firstPaint: fpEntry ? fpEntry.startTime : null,
            firstContentfulPaint: fcpEntry ? fcpEntry.startTime : null,
            largestContentfulPaint: perf.lcp,
            cumulativeLayoutShift: Number(perf.cls.toFixed(4)),
            resourceCount: performance.getEntriesByType('resource').length,
        };
    });
    return { view: viewName, url: page.url(), ...metrics };
}

async function capturePage(page, role) {
    const isAdmin = role === 'admin';
    const auth = isAdmin ? MOCK_AUTH_ADMIN : MOCK_AUTH_CHILD;
    const familyData = isAdmin ? FAMILY_DATA_ADMIN : FAMILY_DATA_CHILD;

    await page.unrouteAll({ behavior: 'ignoreErrors' }).catch(() => {});

    // Stub the Telegram SDK script so it does not overwrite our initData.
    await page.route('https://telegram.org/js/telegram-web-app.js', (route) =>
        route.fulfill({ status: 200, contentType: 'application/javascript', body: '' })
    );

    // Provide Telegram WebApp object before any app code runs.
    await page.addInitScript((initData) => {
        window.Telegram = {
            WebApp: {
                initData,
                initDataUnsafe: {},
                ready: () => {},
                expand: () => {},
                close: () => {},
                HapticFeedback: { notificationOccurred: () => {} },
            },
        };
    }, isAdmin ? 'signed-admin-screenshot-data' : 'signed-child-screenshot-data');

    // Collect Web Vitals / navigation metrics for this view.
    await page.addInitScript({ content: PERF_INIT_SCRIPT });

    // API stubs
    await page.route('**/api/telegram/account-connection/complete', (route) =>
        route.fulfill(json(MOCK_ACCOUNT_CONNECTION))
    );
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill(json(auth)));
    await page.route('**/api/base-data', (route) => route.fulfill(json(BASE_DATA)));
    await page.route('**/api/data/details**', (route) => route.fulfill(json(DETAILS_DATA)));
    await page.route('**/api/history?**', (route) => route.fulfill(json(HISTORY_PAGE)));
    await page.route('**/api/data**', (route) => {
        const url = route.request().url();
        const childId = new URL(url).searchParams.get('childId');
        if (isAdmin && childId && childId !== String(CHILD_ID)) {
            return route.fulfill(json({ ...FAMILY_DATA_ADMIN, activeChildId: Number(childId), balance: 12 }));
        }
        return route.fulfill(json(familyData));
    });

    await page.goto(`${SITE_URL}/telegram`);
}

async function captureScreenshot(page, name) {
    const path = join(OUT_DIR, `${name}.png`);
    await page.screenshot({ path, fullPage: false });
    return path;
}

async function main() {
    await mkdir(OUT_DIR, { recursive: true });
    const browser = await chromium.launch();
    const context = await browser.newContext({ viewport: VIEWPORT });
    const page = await context.newPage();
    const perfReport = [];

    // Parent home
    await capturePage(page, 'admin');
    await page.waitForSelector('[role="tabpanel"]', { timeout: 10000 });
    perfReport.push(await collectMetrics(page, 'parent-home'));
    await captureScreenshot(page, 'parent-home');

    // Parent tasks
    await page.getByRole('tab', { name: 'Задания' }).click();
    await page.waitForTimeout(250);
    perfReport.push(await collectMetrics(page, 'parent-tasks'));
    await captureScreenshot(page, 'parent-tasks');

    // Parent family
    await page.getByRole('tab', { name: 'Семья' }).click();
    await page.waitForTimeout(250);
    perfReport.push(await collectMetrics(page, 'parent-family'));
    await captureScreenshot(page, 'parent-family');

    // Child today
    await capturePage(page, 'child');
    await page.waitForSelector('[role="tabpanel"]', { timeout: 10000 });
    await page.waitForTimeout(600);
    perfReport.push(await collectMetrics(page, 'child-today'));
    await captureScreenshot(page, 'child-today');

    await browser.close();

    const reportPath = join(OUT_DIR, 'performance-report.json');
    await writeFile(
        reportPath,
        JSON.stringify({ generatedAt: new Date().toISOString(), environment: { siteUrl: SITE_URL, viewport: VIEWPORT }, views: perfReport }, null, 2)
    );

    console.log('Screenshots saved to', OUT_DIR);
    console.table(perfReport.map(({ view, ttfb, domContentLoaded, loadComplete, firstContentfulPaint, largestContentfulPaint, cumulativeLayoutShift, resourceCount }) => ({
        view,
        ttfb: ttfb ? `${ttfb.toFixed(1)} ms` : '-',
        dcl: domContentLoaded ? `${domContentLoaded.toFixed(1)} ms` : '-',
        load: loadComplete ? `${loadComplete.toFixed(1)} ms` : '-',
        fcp: firstContentfulPaint ? `${firstContentfulPaint.toFixed(1)} ms` : '-',
        lcp: largestContentfulPaint ? `${largestContentfulPaint.toFixed(1)} ms` : '-',
        cls: cumulativeLayoutShift,
        resources: resourceCount,
    })));
    console.log('Performance report saved to', reportPath);
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
