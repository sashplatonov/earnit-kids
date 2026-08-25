import { expect, test } from '@playwright/test';

import {
    authenticateE2eSession,
    registerParent,
    uniqueEmail,
} from './helpers';
import { preserveTelegramFixture } from './telegramSdkFixture';

const PERIODS = [
    { wireValue: '7d', label: '7 дней' },
    { wireValue: '30d', label: '30 дней' },
    { wireValue: '90d', label: '90 дней' },
    { wireValue: 'all', label: 'Всё время' },
] as const;

async function registerAdmin(page: Parameters<typeof registerParent>[0], prefix: string) {
    await registerParent(page, uniqueEmail(prefix));
    await authenticateE2eSession(page);
}

test('admin Statistics stays usable at compact mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 667 });
    await registerAdmin(page, 'admin.statistics.mobile');

    await page.goto('/telegram/dashboard?period=30d');
    await expect(page).toHaveURL(/\/telegram\/dashboard\?period=30d$/);
    await expect(page.getByRole('heading', { name: 'Статистика' })).toBeVisible();
    await expect(page.getByText('Ключевые сигналы')).toHaveCount(0);
    await expect(page.getByRole('tab')).toHaveCount(5);
    await expect(page.locator('[role="tab"] svg')).toHaveCount(5);

    const primaryTabs = page.getByRole('tablist', { name: 'Разделы статистики' }).getByRole('tab');
    const primaryPanels = ['overview', 'coins', 'rewards', 'tasks', 'activity'];
    for (const [index, panel] of primaryPanels.entries()) {
        await primaryTabs.nth(index).click();
        await expect(primaryTabs.nth(index)).toHaveAttribute('aria-selected', 'true');
        await expect(page.locator(`#panel-${panel}`)).toBeVisible();
    }

    await primaryTabs.first().focus();
    await page.keyboard.press('ArrowRight');
    await expect(primaryTabs.nth(1)).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-coins')).toBeVisible();
    await page.keyboard.press('Home');
    await expect(primaryTabs.first()).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('End');
    await expect(primaryTabs.nth(4)).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-activity')).toBeVisible();

    const activityTabs = page.getByRole('tablist', { name: 'Подразделы активности' }).getByRole('tab');
    const activityPanels = ['activation', 'retention', 'needs'];
    for (const [index, panel] of activityPanels.entries()) {
        await activityTabs.nth(index).click();
        await expect(activityTabs.nth(index)).toHaveAttribute('aria-selected', 'true');
        await expect(page.locator(`#panel-activity-${panel}`)).toBeVisible();
    }

    const activityUrl = page.url();
    await activityTabs.first().focus();
    await page.keyboard.press('ArrowRight');
    await expect(activityTabs.nth(1)).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('End');
    await expect(activityTabs.nth(2)).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Home');
    await expect(activityTabs.first()).toHaveAttribute('aria-selected', 'true');
    await expect(page).toHaveURL(activityUrl);

    for (const period of PERIODS) {
        const control = page.getByRole('button', { name: period.label, exact: true });
        await control.click();
        await expect(page).toHaveURL(new RegExp(`/telegram/dashboard\\?period=${period.wireValue}$`));
        await expect(control).toHaveAttribute('aria-pressed', 'true');
        await expect(page.locator('.period-loading')).toHaveCount(0);
        await expect(control).toBeEnabled();
        expect((await control.boundingBox())?.height).toBeGreaterThanOrEqual(44);
    }

    for (const tab of await primaryTabs.all()) {
        expect((await tab.boundingBox())?.height).toBeGreaterThanOrEqual(44);
    }
    for (const tab of await activityTabs.all()) {
        expect((await tab.boundingBox())?.height).toBeGreaterThanOrEqual(44);
    }

    for (const control of await page.locator('.info:visible, .mini-info:visible').all()) {
        const box = await control.boundingBox();
        expect(box?.width).toBeGreaterThanOrEqual(44);
        expect(box?.height).toBeGreaterThanOrEqual(44);
    }

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
    await expect(page.locator('.tabs-wrap')).toHaveCSS('position', 'fixed');
});

test('admin Statistics matches the reviewed mobile visual baselines', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 667 });
    await registerAdmin(page, 'admin.statistics.visual');
    await page.goto('/telegram/dashboard?period=30d');
    await expect(page.getByRole('heading', { name: 'Статистика' })).toBeVisible();
    await page.evaluate(() => document.fonts.ready);

    const primaryTabs = page.getByRole('tablist', { name: 'Разделы статистики' }).getByRole('tab');
    const activityTabs = page.getByRole('tablist', { name: 'Подразделы активности' }).getByRole('tab');
    const updated = page.locator('.updated');

    async function expectVisualBaseline(tabIndex: number, snapshot: string, activityTabIndex?: number) {
        await primaryTabs.nth(tabIndex).click();
        await expect(primaryTabs.nth(tabIndex)).toHaveAttribute('aria-selected', 'true');
        if (activityTabIndex !== undefined) {
            await activityTabs.nth(activityTabIndex).click();
            await expect(activityTabs.nth(activityTabIndex)).toHaveAttribute('aria-selected', 'true');
        }
        await page.evaluate(() => document.fonts.ready);
        await expect(page).toHaveScreenshot(snapshot, {
            animations: 'disabled',
            fullPage: false,
            mask: [updated],
            maskColor: '#f5f6fa',
        });
    }

    await expectVisualBaseline(0, 'statistics-overview.png');
    await expectVisualBaseline(1, 'statistics-coins.png');
    await expectVisualBaseline(2, 'statistics-rewards.png');
    await expectVisualBaseline(3, 'statistics-tasks.png');
    await expectVisualBaseline(4, 'statistics-activity-activation.png', 0);
    await expectVisualBaseline(4, 'statistics-activity-retention.png', 1);
    await expectVisualBaseline(4, 'statistics-activity-needs.png', 2);
});

test('admin Statistics keeps localized tooltip and desktop navigation accessible', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await registerAdmin(page, 'admin.statistics.desktop');
    await page.goto('/telegram/dashboard?period=7d');

    await expect(page.getByRole('heading', { name: 'Статистика' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page.getByRole('tablist', { name: 'Разделы статистики' })).toBeVisible();
    await expect(page.getByText(/admin\.dashboard|tabs\.|kpis\.|tooltips\./)).toHaveCount(0);

    const tooltipTrigger = page.getByRole('button', { name: 'Активные семьи' }).first();
    const helpControls = page.locator('.info:visible, .mini-info:visible');
    for (const control of await helpControls.all()) {
        const box = await control.boundingBox();
        expect(box?.width).toBeGreaterThanOrEqual(44);
        expect(box?.height).toBeGreaterThanOrEqual(44);
    }

    await tooltipTrigger.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Активные семьи' })).toBeVisible();
    const close = page.getByRole('button', { name: 'Закрыть пояснение' });
    await expect(close).toBeVisible();
    await expect(close).toBeFocused();
    await close.press('Enter');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(tooltipTrigger).toBeFocused();

    await tooltipTrigger.click();
    await expect(close).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(tooltipTrigger).toBeFocused();

    const tabs = page.getByRole('tablist', { name: 'Разделы статистики' }).getByRole('tab');
    await tabs.first().focus();
    await page.keyboard.press('End');
    await expect(tabs.nth(4)).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-activity')).toBeVisible();

    for (const tab of await tabs.all()) {
        const box = await tab.boundingBox();
        expect(box?.width).toBeGreaterThanOrEqual(44);
        expect(box?.height).toBeGreaterThanOrEqual(44);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});

test('non-admin cannot access Statistics data', async ({ page }) => {
    await preserveTelegramFixture(page);
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-child-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'child', familyId: 'family-1' }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [] }),
    }));
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isAdmin: false, tasks: [], shop: [], requests: [] }),
    }));

    await page.goto('/telegram');
    await expect(page).toHaveURL(/\/telegram$/);
    await page.goto('/telegram/dashboard?period=30d');
    await expect(page).toHaveURL(/\/telegram$/);
    await expect(page.getByRole('heading', { name: 'Статистика' })).toHaveCount(0);
});
