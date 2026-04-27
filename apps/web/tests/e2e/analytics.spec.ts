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
    selectChild,
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
    await page.locator('#tasks-section [data-task-action="request"]').first().click();
    await expect(page.getByText(/Request sent|Заявка отправлена/i)).toBeVisible();

    await logout(page);
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD);
    await approveFirstRequest(page);

    // Add second child with no completed tasks
    await addChild(page, CHILD_NAME_B);
    await createTask(page, TASK_TITLE_B, TASK_COINS, 'Почитать 30 минут');

    await page.close();
});

async function loginAsParent(page: import('@playwright/test').Page) {
    await loginParent(page, parentEmail, DEFAULT_PARENT_PASSWORD, {
        destination: /\/app\/analytics$/,
        heading: /EarnIt Kids/i,
    });
}

async function goToAnalytics(
    page: import('@playwright/test').Page,
    options: { expectQuests?: boolean } = {}
) {
    const { expectQuests = true } = options;

    await page.getByRole('link', { name: /Достижения|Аналитика|Achievements|Analytics|My achievements/i }).click();
    await expect(page.locator('#analytics-section')).toBeVisible();
    if (expectQuests) {
        await expect(page.locator('#daily-quest-list')).toBeVisible();
    }
}

async function openAnalyticsDetails(page: import('@playwright/test').Page) {
    const toggle = page.locator('#analytics-details-toggle');
    await expect(toggle).toBeVisible();
    await toggle.click();
    await expect(page.locator('#analytics-details-panel')).toBeVisible();
}

test.describe('Analytics section — parent view', () => {
    test('analytics section is visible for parent with children', async ({ page }) => {
        await loginAsParent(page);
        await goToAnalytics(page);

        await expect(page.locator('#analytics-section')).toBeVisible();
        // Should NOT show the "no children" empty state
        await expect(page.locator('#analytics-empty-state')).not.toBeVisible();
    });

    test('shows daily quests first and keeps details collapsed by default', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);
        await goToAnalytics(page);

        const quests = page.locator('#daily-quest-list [data-quest-id]');
        await expect(quests).toHaveCount(5);
        await expect(page.locator('#analytics-details-panel')).toHaveCount(0);
    });

    test('shows earned coins after approved task for child A', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);

        await goToAnalytics(page);

        // Stats should show earned coins
        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earnedText = await earnedStat.textContent();
        expect(Number(earnedText)).toBeGreaterThanOrEqual(TASK_COINS);
    });

    test('shows analytics balance equal to the main header balance', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);

        await goToAnalytics(page);

        const headerBalance = (await page.locator('#balance').textContent())?.trim() ?? '';
        await expect(page.locator('#stats-net')).toHaveText(headerBalance);
    });

    test('shows readable recommendation cards instead of blank placeholders', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);

        await goToAnalytics(page);
        await openAnalyticsDetails(page);

        const firstRecommendation = page.locator('#analytics-recommendations .recommendation-card').first();
        await expect(firstRecommendation).toBeVisible();
        await expect(firstRecommendation.locator('.card__title')).toContainText(TASK_TITLE_A);
        await expect(firstRecommendation.locator('.card__badge--group')).toBeVisible();
        await expect(firstRecommendation.locator('.card__coins')).toBeVisible();
        await expect(firstRecommendation.locator('.card__comment')).toContainText('Полить все цветы в комнате');
        await expect(firstRecommendation).not.toContainText(/мон\.?/);
    });

    test('shows zero earned for child B who has no completed tasks', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_B);

        await goToAnalytics(page);

        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earnedText = await earnedStat.textContent();
        expect(Number(earnedText)).toBe(0);
    });

    test('reloads analytics when switching between children', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);
        await goToAnalytics(page);

        const earnedA = Number(await page.locator('#stats-earned').textContent());
        const questMetricA = (await page.locator('[data-quest-id="earn-coins"] .analytics-quest-card__metric').textContent())?.trim() ?? '';
        expect(earnedA).toBeGreaterThanOrEqual(TASK_COINS);

        await selectChild(page, CHILD_NAME_B);
        await expect.poll(async () => (await page.locator('#stats-earned').textContent())?.trim() ?? '').toBe('0');
        const earnedB = Number(await page.locator('#stats-earned').textContent());
        const questMetricB = (await page.locator('[data-quest-id="earn-coins"] .analytics-quest-card__metric').textContent())?.trim() ?? '';
        expect(earnedB).toBe(0);
        expect(questMetricA).not.toBe(questMetricB);
    });

    test('timeframe filter buttons change the displayed timeframe', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);
        await goToAnalytics(page);
        await openAnalyticsDetails(page);

        const timeframeGroup = page.locator('#analytics-timeframe-group');
        await expect(timeframeGroup).toBeVisible();

        // Switch to week
        await page.locator('#analytics-timeframe-group [data-timeframe="week"]').click();
        await expect(page.locator('#analytics-timeframe-group [data-timeframe="week"]')).toHaveClass(/active/);
        await expect(page.locator('#analytics-details-panel')).toBeVisible();
        await expect(page.locator('#stats-earned')).toBeVisible();
        await expect(page.locator('#achievements-trend-chart')).toBeVisible();

        // Switch to year
        await page.locator('#analytics-timeframe-group [data-timeframe="year"]').click();
        await expect(page.locator('#analytics-timeframe-group [data-timeframe="year"]')).toHaveClass(/active/);
        await expect(page.locator('#analytics-details-panel')).toBeVisible();
        await expect(page.locator('#stats-earned')).toBeVisible();
        await expect(page.locator('#achievements-trend-chart')).toBeVisible();
    });

    test('quest CTA buttons open tasks, rewards, and analytics details', async ({ page }) => {
        await loginAsParent(page);
        await selectChild(page, CHILD_NAME_A);
        await goToAnalytics(page);

        await page.locator('[data-quest-id="complete-tasks"] button').click();
        await expect(page).toHaveURL(/\/app\/tasks$/);
        await expect(page.locator('#tasks-section')).toBeVisible();

        await goToAnalytics(page);
        await page.locator('[data-quest-id="reward-target"] button').click();
        await expect(page).toHaveURL(/\/app\/shop$/);
        await expect(page.locator('#shop-section')).toBeVisible();

        await goToAnalytics(page);
        await page.locator('[data-quest-id="keep-streak"] button[data-quest-action-target="details"]').click();
        await expect(page.locator('#analytics-details-panel')).toBeVisible();
        await expect(page.locator('#achievements-trend-chart')).toBeVisible();
    });

    test('shows empty state when parent has no children', async ({ page }) => {
        // Register a fresh parent with no children
        const freshEmail = uniqueEmail('analytics.empty');
        await registerParent(page, freshEmail, DEFAULT_PARENT_PASSWORD);
        await openFamilyApp(page);
        await goToAnalytics(page, { expectQuests: false });

        await expect(page.locator('#analytics-empty-state')).toBeVisible();
        await expect(page.locator('#analytics-add-child')).toBeVisible();
    });
});

test.describe('Analytics section — child view', () => {
    test('child sees own achievements after completing a task', async ({ page }) => {
        await loginChildByMagicLink(page, childLinkA, TASK_TITLE_A);

        await page.getByRole('link', { name: /Достижения|Аналитика|Achievements|Analytics|My achievements/i }).click();
        await expect(page.locator('#analytics-section')).toBeVisible();
        await expect(page.locator('#daily-quest-list [data-quest-id]')).toHaveCount(5);

        // Child should NOT see the "no children" empty state
        await expect(page.locator('#analytics-empty-state')).not.toBeVisible();

        // Earned stat should reflect completed task
        const earnedStat = page.locator('#stats-earned');
        await expect(earnedStat).toBeVisible();
        const earned = Number(await earnedStat.textContent());
        expect(earned).toBeGreaterThanOrEqual(TASK_COINS);
    });
});
