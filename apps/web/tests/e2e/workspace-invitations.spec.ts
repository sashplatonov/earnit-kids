import { expect, test } from '@playwright/test';

test('parent invitation entry is usable on a compact browser viewport', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto('/invite/parent');
    await expect(page.getByTestId('parent-invitation')).toBeVisible();
    await expect(page.getByRole('heading', { name: /invited|приглаш/i })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('an expired invitation is announced without exposing personal data', async ({ page }) => {
    await page.goto('/invite/parent?error=expired');
    await expect(page.getByRole('alert')).toContainText('expired');
    await expect(page.getByTestId('parent-invitation')).not.toContainText(/@|family id/i);
});
