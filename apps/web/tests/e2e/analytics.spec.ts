/**
 * E2E tests for the Analytics (Achievements) section.
 * Verifies that charts and stats load, react to timeframe changes,
 * and reload correctly when the active child is switched.
 */
import { expect, test } from '@playwright/test';

import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    approveFirstRequest,
    createTask,
    getChildMagicLink,
    loginChildByMagicLink,
    loginParent,
    logout,
    openFamilyApp,
    registerParent,
    uniqueEmail,
} from './helpers';

const CHILD_NAME_A = 'Аналит Ребёнок А';
const CHILD_NAME_B = 'Аналит Ребёнок Б';
const TASK_TITLE_A = 'Полить цветы (тест аналитика)';
const TASK_TITLE_B = 'Почитать книгу (тест аналитика)';
const TASK_COINS = 30;

let parentEmail = '';
let childLinkA = '';

test.beforeAll(async ({ browser }) => {
    parentEmail = uniqueEmail('analytics');

    const page = await browser.newPage();

    await registerParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);

    // Add first child, create task, complete and approve
    await addChild(page, CHILD_NAME_A);
    await createTask(page, TASK_TITLE_A, TASK_COINS, 'Полить все цветы в комнате');
    childLinkA = await getChildMagicLink(page);

    await loginChildByMagicLink(page, childLinkA, TASK_TITLE_A);
    await page.getByRole('button', { name: 'Выполнил!' }).click();
    await expect(page.getByText('Заявка отправлена')).toBeVisible();

    await logout(page);
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    // Add second child with no completed tasks
    await page.getByRole('button', { name: /Дополнительные разделы/ }).click();
    await page.getByRole('menuitem', { name: 'Настройки' }).click();
    // Use add child via switcher instead of analytics empty state
    await page.locator('#child-switcher-add-child').click().catch(async () => {
        // Fallback: go to analytics tab and use the button there
        await page.getByRole('tab', { name: /Достижения|Аналитика/ }).click();
        await page.locator('#analytics-add-child').click();
    });
    await page.locator('#new-child-name').fill(CHILD_NAME_B);
    await page.locator('#add-child-save').click();
    await expect(page.locator('.child-menu-btn__name')).toHaveText(CHILD_NAME_B);
    await createTask(page, TASK_TITLE_B, TASK_COINS, 'Почитать 30 минут');

    await page.close();
});

async function loginAsParent(page: import('@playwright/test').Page) {
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD, {
        destination: /\/$/,
        heading: /EarnIt Kids/i,
    });
}

async function goToAnalytics(page: import('@playwright/test').Page) {
    await page.getByRole('tab', { name: /Достижения|Аналитика/ }).click();
    await expect(page.locator('#analytics-section')).toBeVisible();
}

test.describe('Analytics section — parent view', () => {
    test('analytics section is visible for parent with children', async ({ page }) => {
        await loginAsParent(page);
        await goToAnalytics(page);

        await expect(page.locator('#analytics-section')).toBeVisible();
        // Should NOT show the "no children" empty state
        await expect(page.locator('#analytics-empty-state')).not.toBeVisible();
    });

    test('shows earned coins after approved task for child A', async ({ page }) => {
        await loginAsParent(page);

        // Make sure child A is selected
        const childBtn = page.locator('.child-menu-btn__name');
        const currentChild = await childBtn.textContent();
        if (currentChild !== CHILD_NAME_A) {
            await page.locator('.child-menu-btn').click();
            await page.getByRole('menuitem', { name: CHILD_NAME_A }).click();
        }

        await goToAnalytics(page);

        // Stats should show earned coins
        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earnedText = await earnedStat.textContent();
        expect(Number(earnedText)).toBeGreaterThanOrEqual(TASK_COINS);
    });

    test('shows zero earned for child B who has no completed tasks', async ({ page }) => {
        await loginAsParent(page);

        // Switch to child B
        await page.locator('.child-menu-btn').click();
        await page.getByRole('menuitem', { name: CHILD_NAME_B }).click();

        await goToAnalytics(page);

        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earnedText = await earnedStat.textContent();
        expect(Number(earnedText)).toBe(0);
    });

    test('reloads analytics when switching between children', async ({ page }) => {
        await loginAsParent(page);

        // Start with child A — should have earned coins
        await page.locator('.child-menu-btn').click();
        await page.getByRole('menuitem', { name: CHILD_NAME_A }).click();
        await goToAnalytics(page);

        const earnedA = Number(await page.locator('#stats-earned').textContent());
        expect(earnedA).toBeGreaterThanOrEqual(TASK_COINS);

        // Switch to child B — should reload and show 0
        await page.locator('.child-menu-btn').click();
        await page.getByRole('menuitem', { name: CHILD_NAME_B }).click();
        // Wait for stats to reload
        await page.waitForTimeout(800);
        const earnedB = Number(await page.locator('#stats-earned').textContent());
        expect(earnedB).toBe(0);
    });

    test('timeframe filter buttons change the displayed timeframe', async ({ page }) => {
        await loginAsParent(page);

        // Make sure child A is selected (has data)
        await page.locator('.child-menu-btn').click();
        await page.getByRole('menuitem', { name: CHILD_NAME_A }).click();
        await goToAnalytics(page);

        const timeframeGroup = page.locator('#analytics-timeframe-group');
        await expect(timeframeGroup).toBeVisible();

        // Switch to week
        await page.getByRole('button', { name: 'Неделя' }).click();
        await expect(page.getByRole('button', { name: 'Неделя' })).toHaveClass(/active/);
        await page.waitForTimeout(500);
        await expect(page.locator('#stats-earned')).toBeVisible();

        // Switch to year
        await page.getByRole('button', { name: 'Год' }).click();
        await expect(page.getByRole('button', { name: 'Год' })).toHaveClass(/active/);
        await page.waitForTimeout(500);
        await expect(page.locator('#stats-earned')).toBeVisible();
    });

    test('shows empty state when parent has no children', async ({ page }) => {
        // Register a fresh parent with no children
        const freshEmail = uniqueEmail('analytics.empty');
        await registerParent(page, freshEmail, DEFAULT_PARENT_PASSWORD);
        await openFamilyApp(page);
        await goToAnalytics(page);

        await expect(page.locator('#analytics-empty-state')).toBeVisible();
        await expect(page.locator('#analytics-add-child')).toBeVisible();
    });
});

test.describe('Analytics section — child view', () => {
    test('child sees own achievements after completing a task', async ({ page }) => {
        await loginChildByMagicLink(page, childLinkA, TASK_TITLE_A);

        await page.getByRole('tab', { name: /Достижения|Аналитика/ }).click();
        await expect(page.locator('#analytics-section')).toBeVisible();

        // Child should NOT see the "no children" empty state
        await expect(page.locator('#analytics-empty-state')).not.toBeVisible();

        // Earned stat should reflect completed task
        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earned = Number(await earnedStat.textContent());
        expect(earned).toBeGreaterThanOrEqual(TASK_COINS);
    });
});
