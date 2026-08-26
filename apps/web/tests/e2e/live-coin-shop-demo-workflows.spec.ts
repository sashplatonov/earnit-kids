import { expect, test } from '@playwright/test';
import { captureApiRequests, expectNoApiRequests, expectParentTabs } from './live-coin-shop-demo.helpers';

test.use({ locale: 'en-US' });

test('parent and child complete a noted request lifecycle without API calls', async ({ page }) => {
    const apiRequests = captureApiRequests(page);

    await page.goto('/demo');
    await expectParentTabs(page);
    await page.getByRole('tab', { name: 'Rewards' }).click();
    const iceCream = page.getByRole('listitem').filter({ hasText: 'Ice cream' });
    await iceCream.getByRole('button', { name: 'Grant' }).click();
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();

    await page.getByRole('button', { name: /View as child/ }).click();
    await expect(page.getByRole('tab', { name: 'Tasks' })).toBeVisible();
    const task = page.getByRole('listitem').filter({ hasText: 'Make the bed' });
    await task.getByRole('button', { name: 'Complete' }).click();
    const requestDialog = page.getByRole('dialog');
    await requestDialog.getByRole('textbox', { name: 'Optional note' }).fill('I finished it');
    await requestDialog.getByRole('button', { name: /request/i }).click();
    await expect(page.getByRole('status')).toContainText('sent');

    await page.getByRole('button', { name: /Back to parent/ }).click();
    await page.getByRole('tab', { name: 'Home' }).click();
    const requestRow = page.getByRole('listitem').filter({ hasText: 'Make the bed' });
    await requestRow.getByRole('button', { name: 'Approve' }).click();
    await expect(page.getByRole('listitem').filter({ hasText: 'Make the bed' })).toBeVisible();
    await expect(page.getByText('85', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Reset demo', exact: true }).click();
    await expect(page.getByText('75', { exact: true })).toBeVisible();
    expectNoApiRequests(apiRequests);
});

test('parent reward boundary reports an error and leaves the balance unchanged', async ({ page }) => {
    await page.goto('/demo');
    await page.getByRole('tab', { name: 'Rewards' }).click();
    const cinema = page.getByRole('listitem').filter({ hasText: 'Trip to the cinema' });
    await cinema.getByRole('button', { name: 'Grant' }).click();
    await page.getByRole('button', { name: /Grant/ }).last().click();
    await expect(page.getByRole('alert')).toContainText('Not enough coins');
    await expect(page.getByText('75', { exact: true })).toBeVisible();
});

test('all parent panels expose real content and reset restores a fresh fixture', async ({ page }) => {
    const apiRequests = captureApiRequests(page);
    await page.goto('/demo');
    await expectParentTabs(page);
    await page.getByRole('tab', { name: 'Tasks' }).click();
    await expect(page.getByText('Make the bed', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Rewards' }).click();
    await expect(page.getByText('Ice cream', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Family' }).click();
    await expect(page.getByRole('heading', { name: 'Family', level: 1 })).toBeVisible();
    await expect(page.getByText('Family language', { exact: true })).toBeVisible();

    await page.getByRole('tab', { name: /^Home/ }).click();
    await page.getByRole('button', { name: /View as child/ }).click();
    await expect(page.getByRole('tab', { name: 'Tasks' })).toBeVisible();
    await expect(page.getByText('Make the bed', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: /Back to parent/ }).click();
    await page.getByRole('button', { name: 'Reset demo', exact: true }).click();
    await expect(page.getByRole('tab', { name: /^Home/ })).toHaveAttribute('aria-selected', 'true');
    await page.reload();
    await expect(page.getByText('75', { exact: true }).first()).toBeVisible();
    expectNoApiRequests(apiRequests);
});
