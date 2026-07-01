import { expect, test } from '@playwright/test';

import {
    DEFAULT_PARENT_PASSWORD,
    addChild,
    openFamilyApp,
    registerParent,
    uniqueEmail,
} from './helpers';

async function createRewardWithGroup(
    page: import('@playwright/test').Page,
    title: string,
    group: string,
    price: number,
    comment: string,
    frequencyLimit?: number,
    frequencyPeriod?: 'day' | 'week' | 'month' | 'year',
    moneyLimit?: number,
) {
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await page.locator('#add-shop-btn').click();
    await page.locator('#shop-name').fill(title);
    await page.locator('#shop-group').fill(group);
    await page.locator('#shop-price').fill(String(price));
    if (frequencyLimit != null) {
        await page.locator('#shop-freq-limit').fill(String(frequencyLimit));
        if (frequencyPeriod) {
            await page.locator('#shop-freq-period').selectOption(frequencyPeriod);
        }
    }
    if (moneyLimit != null) {
        await page.locator('#shop-money-limit').fill(String(moneyLimit));
    }
    await page.locator('#shop-comment').fill(comment);
    await page.locator('#shop-save').click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

test('shop group filter works, persists after reload, and keeps compact list readable on mobile', async ({ page }) => {
    const email = uniqueEmail('shop.filters');
    const rewardGames = `Игра ${Date.now()}`;
    const rewardBooks = `Книга ${Date.now()}`;

    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await addChild(page, 'Фильтр Ребёнок');

    await createRewardWithGroup(page, rewardGames, 'Игры', 20, 'Настольная игра');
    await createRewardWithGroup(page, rewardBooks, 'Книги', 25, 'Новая книга', 2, 'week', 15);

    const booksTab = page.locator('#shop-group-nav .group-nav__tab').filter({ hasText: 'Книги' });
    await expect(booksTab).toBeVisible();
    await booksTab.click();

    await expect(page).toHaveURL(/group=/);
    await expect(booksTab).toHaveClass(/group-nav__tab--active/);

    const visibleTitles = page.locator('#shop-list .card__title');
    await expect(visibleTitles).toHaveCount(1);
    await expect(visibleTitles.first()).toHaveText(rewardBooks);

    await expect(page).toHaveURL(/group=/);
    await page.reload();

    await expect(page.locator('#shop-list .card__title')).toHaveCount(1);
    await expect(page.locator('#shop-list .card__title').first()).toHaveText(rewardBooks);

    await page.getByRole('button', { name: /Rows|Строки/i }).click();

    const listCard = page.locator('#shop-list .shop-card--list').first();
    await expect(listCard).toBeVisible();
    await expect(listCard.locator('.card__title')).toHaveText(rewardBooks);
    await expect(listCard.locator('.card__compact-meta')).toContainText('Книги');
    await expect(listCard.locator('.card__compact-meta')).toContainText('15');
    await expect(listCard.locator('.card__meta')).toContainText(/2.*(недел|week)/i);

    const metrics = await listCard.evaluate((card) => {
        const rect = card.getBoundingClientRect();
        const badgeRow = card.querySelector('.card__badge-row');

        return {
            cardHeight: rect.height,
            badgeRowDisplay: badgeRow ? getComputedStyle(badgeRow).display : 'missing',
        };
    });

    expect(metrics.cardHeight).toBeLessThan(120);
    expect(metrics.badgeRowDisplay).toBe('none');

    await page.setViewportSize({ width: 390, height: 844 });
    await expect(listCard.locator('.card__title')).toBeVisible();
    await expect(listCard.locator('.card__compact-meta')).toContainText('Книги');

    const mobileMetrics = await listCard.evaluate((card) => {
        const title = card.querySelector('.card__title');
        const side = card.querySelector('.shop-card__side');
        const rect = card.getBoundingClientRect();
        const titleRect = title?.getBoundingClientRect();
        const sideRect = side?.getBoundingClientRect();

        return {
            cardHeight: rect.height,
            titleHeight: titleRect?.height ?? 0,
            titleWidth: titleRect?.width ?? 0,
            sideTop: sideRect?.top ?? 0,
            titleBottom: titleRect?.bottom ?? 0,
        };
    });

    expect(mobileMetrics.cardHeight).toBeLessThan(180);
    expect(mobileMetrics.titleHeight).toBeGreaterThan(16);
    expect(mobileMetrics.titleWidth).toBeGreaterThan(120);
    expect(mobileMetrics.sideTop).toBeGreaterThanOrEqual(mobileMetrics.titleBottom);
});
