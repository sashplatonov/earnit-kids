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
    moneyLimit?: number,
) {
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await page.locator('#add-shop-btn').click();
    await page.locator('#shop-name').fill(title);
    await page.locator('#shop-group').fill(group);
    await page.locator('#shop-price').fill(String(price));
    if (moneyLimit != null) {
        await page.locator('#shop-money-limit').fill(String(moneyLimit));
    }
    await page.locator('#shop-comment').fill(comment);
    await page.locator('#shop-save').click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

test('shop group filter works, persists after reload, and keeps compact list badge', async ({ page }) => {
    const email = uniqueEmail('shop.filters');
    const rewardGames = `Игра ${Date.now()}`;
    const rewardBooks = `Книга ${Date.now()}`;

    await registerParent(page, email, DEFAULT_PARENT_PASSWORD);
    await openFamilyApp(page);
    await addChild(page, 'Фильтр Ребёнок');

    await createRewardWithGroup(page, rewardGames, 'Игры', 20, 'Настольная игра');
    await createRewardWithGroup(page, rewardBooks, 'Книги', 25, 'Новая книга', 15);

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
    await expect(listCard.locator('.shop-card__list-group-badge')).toHaveText('Книги');
    await expect(listCard.locator('.shop-card__list-money-badge')).toHaveText('15 💶');

    const metrics = await listCard.evaluate((card) => {
        const rect = card.getBoundingClientRect();
        const badgeRow = card.querySelector('.card__badge-row');

        return {
            cardHeight: rect.height,
            badgeRowDisplay: badgeRow ? getComputedStyle(badgeRow).display : 'missing',
        };
    });

    expect(metrics.cardHeight).toBeLessThan(80);
    expect(metrics.badgeRowDisplay).toBe('none');
});