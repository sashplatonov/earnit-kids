import { expect, test } from '@playwright/test';

import { registerParent, uniqueEmail } from './helpers';

test('admin Statistics stays usable at compact mobile width', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 667 });
    await registerParent(page, uniqueEmail('admin.statistics'), undefined, { heading: null });

    await page.goto('/telegram/dashboard?period=30d');
    await expect(page).toHaveURL(/\/telegram\/dashboard\?period=30d$/);
    await expect(page.getByRole('heading', { name: 'Статистика' })).toBeVisible();
    await expect(page.getByText('Ключевые сигналы')).toHaveCount(0);
    await expect(page.getByRole('tab')).toHaveCount(5);
    await expect(page.locator('[role="tab"] svg')).toHaveCount(5);

    const periods = [
        { wireValue: '7d', label: '7 дней' },
        { wireValue: '30d', label: '30 дней' },
        { wireValue: '90d', label: '90 дней' },
        { wireValue: 'all', label: 'Всё время' },
    ];

    for (const period of periods) {
        const control = page.getByRole('button', { name: period.label, exact: true });
        await control.click();
        await expect(page).toHaveURL(new RegExp(`/telegram/dashboard\\?period=${period.wireValue}$`));
        await expect(control).toHaveAttribute('aria-pressed', 'true');
        expect((await control.boundingBox())?.height).toBeGreaterThanOrEqual(44);
    }

    const tabs = page.getByRole('tab');
    await tabs.first().focus();
    await page.keyboard.press('ArrowRight');
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-coins')).toBeVisible();

    for (const tab of await tabs.all()) {
        expect((await tab.boundingBox())?.height).toBeGreaterThanOrEqual(44);
    }

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
    await expect(page.locator('.tabs-wrap')).toHaveCSS('position', 'fixed');
});
