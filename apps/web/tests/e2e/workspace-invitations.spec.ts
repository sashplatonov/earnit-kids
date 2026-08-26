import { expect, test } from '@playwright/test';
import { authenticateE2eSession } from './helpers';

test('parent invitation entry is usable on a compact browser viewport', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.goto('/invite/parent');
    await expect(page.getByTestId('parent-invitation')).toBeVisible();
    await expect(page.getByTestId('parent-invitation').locator('h1')).toContainText(/invited|приглаш|семь/i);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('an expired invitation is announced without exposing personal data', async ({ page }) => {
    await page.goto('/invite/parent?error=expired');
    await expect(page.getByRole('alert')).toContainText(/expired|истекло|истёк/i);
    await expect(page.getByTestId('parent-invitation')).not.toContainText(/@|family id/i);
});

test('a signed-in parent accepting an invitation returns to the canonical app route', async ({ page }) => {
    await authenticateE2eSession(page, 'parent');
    await page.route('**/invite/parent/accept', (route) => route.fulfill({ status: 204 }));
    await page.goto('/invite/parent');
    await page.getByRole('button', { name: /accept|принять/i }).click();
    await expect(page).toHaveURL('/app');
});
