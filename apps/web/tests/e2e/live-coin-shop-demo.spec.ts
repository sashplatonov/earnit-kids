import { expect, test } from '@playwright/test';
import { captureApiRequests, expectNoApiRequests, expectNoHorizontalOverflow, expectParentTabs, expectTargetSize } from './live-coin-shop-demo.helpers';

test.use({ locale: 'en-US' });

test('anonymous live demo submits a noted request and keeps it in memory', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const apiRequests = captureApiRequests(page);
    const response = await page.goto('/demo');

    expect(response?.status()).toBe(200);
    await expect(page).toHaveTitle('Live rewards shop demo · EarnIt Kids');
    await expectParentTabs(page);
    await expect(page.getByRole('main', { name: 'My family' })).toBeVisible();
    await expect(page.getByText('This is a temporary demo.', { exact: false })).toBeVisible();
    await expect(page.getByLabel('Balance: 75 coins', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: /View as child/ }).click();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    const iceCream = page.getByRole('listitem').filter({ hasText: 'Ice cream' });
    const requestButton = iceCream.getByRole('button', { name: 'Ask for reward', exact: true });
    await requestButton.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('textbox', { name: 'Optional note' }).fill('Saturday treat');
    await dialog.getByRole('button', { name: /Ask for reward/ }).click();

    await expect(page.getByRole('status')).toContainText('Reward request sent for approval.');
    await expectNoApiRequests(apiRequests);

    await page.getByRole('button', { name: 'Reset demo', exact: true }).click();
    await expect(page.locator('.announcement')).toContainText('The demo was reset to its starting state.');
    await page.getByRole('button', { name: /View as child/ }).click();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await expect(requestButton).toBeEnabled();
    await expectNoApiRequests(apiRequests);

    await page.reload();
    await page.getByRole('button', { name: /View as child/ }).click();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await expect(page.getByLabel('Balance: 75 coins', { exact: true })).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: 'Ice cream' }).getByRole('button', { name: 'Ask for reward', exact: true })).toBeEnabled();
    await expectNoApiRequests(apiRequests);
});

test('Russian live demo is directly reachable and preserves locale copy', async ({ page }) => {
    const apiRequests = captureApiRequests(page);
    const response = await page.goto('/ru/demo');

    expect(response?.status()).toBe(200);
    await expect(page.locator('html')).toHaveAttribute('lang', 'ru');
    await expect(page).toHaveTitle('Живое демо магазина наград · EarnIt Kids');
    await expect(page.getByRole('main', { name: 'Моя семья' })).toBeVisible();
    await expect(page.getByRole('tab', { name: /^Главная/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Начать демо заново', exact: true })).toBeVisible();
    await expect(page.getByText('Ваши действия остаются в этой вкладке браузера', { exact: false })).toBeVisible();
    await expectNoApiRequests(apiRequests);
});

test('live demo remains usable at 320px with keyboard-visible touch targets', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    const apiRequests = captureApiRequests(page);
    await page.goto('/demo');
    await expectParentTabs(page);

    const reset = page.getByRole('button', { name: 'Reset demo', exact: true });
    const request = page.getByRole('listitem').filter({ hasText: 'Ice cream' }).getByRole('button', { name: 'Ask for reward', exact: true });
    await page.getByRole('button', { name: /View as child/ }).click();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await expectTargetSize(reset);
    await expectTargetSize(request);

    await reset.focus();
    await page.keyboard.press('Tab');
    await page.keyboard.press('Shift+Tab');
    await expect(reset).toBeFocused();
    await expect(reset).toHaveCSS('outline-style', 'solid');
    await expectNoHorizontalOverflow(page);
    await expectNoApiRequests(apiRequests);
});

test('parent tabs support keyboard navigation and child selector stays isolated', async ({ page }) => {
    const apiRequests = captureApiRequests(page);
    await page.goto('/demo');
    await expectParentTabs(page);

    const home = page.getByRole('tab', { name: /^Home/ });
    await home.focus();
    await home.press('End');
    const family = page.getByRole('tab', { name: /^Family/ });
    await expect(family).toBeFocused();
    await family.press('Home');
    await expect(home).toBeFocused();
    await expect(home).toHaveCSS('outline-style', 'solid');

    const selector = page.getByRole('button', { name: 'Switch child' });
    await selector.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('option', { name: /Leo/ }).click();
    await expect(selector).toContainText('Leo');
    await expect(page.getByLabel('Balance: 145 coins', { exact: true })).toBeVisible();
    await expectNoApiRequests(apiRequests);
});
