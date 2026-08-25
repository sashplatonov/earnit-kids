import { expect, test } from '@playwright/test';
import { preserveTelegramFixture } from './telegramSdkFixture';

test.beforeEach(async ({ page }) => {
    await preserveTelegramFixture(page);
});

test('localized coin adjustment keeps the family number format on a narrow viewport', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        Object.defineProperty(navigator, 'language', { configurable: true, value: 'en-US' });
        Object.defineProperty(navigator, 'languages', { configurable: true, value: ['en-US'] });
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} },
        };
    });

    let familyLocale: 'en' | 'ru' = 'en';
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'parent', familyId: 'family-1', locale: familyLocale }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [] }),
    }));
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ isAdmin: true, balance: 42, childId: 10, children: [{ id: 10, nickname: 'Alex', balance: 42 }], tasks: [], shop: [], requests: [] }),
    }));
    await page.route('**/api/data/details**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requests: [], history: [], friends: [] }),
    }));
    await page.route('**/api/history?**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0, page: 1, limit: 10 }),
    }));

    for (const locale of ['en', 'ru'] as const) {
        familyLocale = locale;
        await page.goto('/telegram');
        const copy = locale === 'en'
            ? { addCoins: 'Add coins', alert: 'Amount cannot exceed', cancel: 'Cancel', save: 'Save' }
            : { addCoins: 'Начислить', alert: 'Сумма не может превышать', cancel: 'Отмена', save: 'Сохранить' };
        await page.getByRole('button', { name: new RegExp(copy.addCoins) }).click();
        await expect(page.getByRole('dialog')).toBeVisible();
        const amountInput = page.locator('#coin-amount');
        await amountInput.fill('1000001');
        await expect(amountInput).toHaveValue('1000001');
        await amountInput.press('Enter');
        const alert = page.locator('#coin-adjust-error');
        await expect(alert).toContainText(copy.alert);
        await expect(alert).toContainText(/1(?:[,\.\s\u00a0\u202f])000(?:[,\.\s\u00a0\u202f])000/);
        await expect(page.getByRole('button', { name: new RegExp(copy.cancel) })).toBeVisible();
        await expect(page.getByRole('button', { name: new RegExp(copy.save) })).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
        for (const button of await page.locator('.actions button').all()) {
            const rect = await button.boundingBox();
            expect(rect?.width).toBeGreaterThanOrEqual(44);
            expect(rect?.height).toBeGreaterThanOrEqual(44);
        }
    }
});

async function expectCompactList(list: import('@playwright/test').Locator, count: number) {
    await expect(list).toBeVisible();
    await expect(list.locator(':scope > .entity-row')).toHaveCount(count);
    expect(await list.evaluate((node) => {
        const rows = [...node.children];
        return rows.every((row) => row.classList.contains('entity-row'))
            && rows.slice(0, -1).every((row) => getComputedStyle(row).borderBottomWidth === '1px')
            && rows.every((row) => getComputedStyle(row).backgroundColor === 'rgba(0, 0, 0, 0)')
            && rows.every((row) => [...row.querySelectorAll('*')].every((child) => {
                if (child.matches('button, a')) return true;
                const style = getComputedStyle(child);
                return !(style.borderStyle !== 'none' && style.borderRadius !== '0px' && style.backgroundColor === 'rgb(255, 255, 255)');
            }));
    })).toBeTruthy();
}

async function expectCatalogRow(
    list: import('@playwright/test').Locator,
    title: RegExp,
    amount: string,
    group: string,
    frequency: RegExp,
    actionName: RegExp,
) {
    const row = list.locator(':scope > .entity-row').first();
    await expect(row).toHaveAttribute('role', 'listitem');
    await expect(row.locator('.entity-icon')).toHaveCount(1);
    await expect(row.getByRole('button', { name: title })).toBeVisible();
    await expect(row.getByText(amount, { exact: true })).toBeVisible();
    await expect(row.getByText(group, { exact: true })).toBeVisible();
    await expect(row.locator('.row-main .row-metadata')).toContainText(frequency);
    const action = row.getByRole('button', { name: actionName });
    await expect(action).toBeVisible();
    expect(await action.evaluate((node) => {
        const rect = node.getBoundingClientRect();
        return rect.width >= 44 && rect.height >= 44;
    })).toBeTruthy();
    return row;
}

test('parent Mini App is server-role scoped and mobile-safe', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.addInitScript(() => {
        (window as Window & { Telegram?: unknown }).Telegram = {
            WebApp: { initData: 'signed-init-data', ready: () => {}, expand: () => {} },
        };
    });
    await page.route('**/api/telegram/auth/exchange', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ role: 'parent', familyId: 'family-1' }),
    }));
    await page.route('**/api/base-data', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ tasks: [], products: [], catalog: { tasks: [{ id: 'catalog-task', title: 'Очень длинное каталожное задание для узкого экрана', coins: 25, groupName: 'Дом', groupKey: 'home', frequencyPeriod: 'daily' }], rewards: [{ id: 'catalog-reward', title: 'Каталожная награда с длинным названием', price: 40, groupName: 'Отдых', groupKey: 'fun' }] } }),
    }));
    await page.route('**/api/data**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            isAdmin: true,
            balance: 42,
            childId: 10,
            children: [{ id: 10, nickname: 'Alex', balance: 42 }, { id: 11, nickname: 'Sam', balance: 8 }],
            tasks: [{ id: 1, name: 'A very long parent task title that remains readable on a narrow viewport', coins: 20, groupName: 'Home', lastCompletedAt: '2026-08-16T09:00:00Z', isActive: false }],
            shop: [{ id: 2, name: 'A very long parent reward title that remains readable', price: 50, groupName: 'Fun', lastPurchasedAt: '2026-08-15T09:00:00Z', isActive: false }],
            requests: [],
        }),
    }));
    await page.route('**/api/requests/15/approve**', (route) => route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ detail: 'Already resolved' }),
    }));
    await page.route('**/api/requests/16/reject**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({}),
    }));
    await page.route('**/api/data/details**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ requests: [
            { id: 15, taskName: 'Read this very long task title without clipping', coins: 20, status: 'pending', childNickname: 'Alex', createdAt: '2026-08-16T09:00:00Z' },
            { id: 16, itemName: 'Game reward', amount: 50, requestType: 'shop_purchase', status: 'pending', childNickname: 'Sam', createdAt: '2026-08-15T09:00:00Z' },
        ], history: [], friends: [] }),
    }));
    await page.route('**/api/history?**', (route) => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [{ id: 21, type: 'earn', description: 'Read completed', amount: 20, createdAt: '2026-08-16T09:00:00Z' }], total: 1, page: 1, limit: 10 }),
    }));

    await page.goto('/telegram');

    await expect(page.getByRole('button', { name: /Switch child|Выбрать ребёнка/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Rewards|Награды/ })).toBeVisible();
    await expect(page.getByRole('tab', { name: /Family|Семья/ })).toBeVisible();
    await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
    const taskList = page.locator('.tasks .list-surface');
    await expectCompactList(taskList, 1);
    await expect(taskList.getByText(/A very long parent task title/)).toBeVisible();
    await expect(taskList.getByText('Home')).toBeVisible();
    await expect(taskList.getByRole('button', { name: /Complete|Выполнить/ })).toBeDisabled();
    await page.getByRole('tab', { name: /Rewards|Награды/ }).click();
    const rewardList = page.locator('.rewards .list-surface');
    await expectCompactList(rewardList, 1);
    await expect(rewardList.getByText(/A very long parent reward title/)).toBeVisible();
    await expect(rewardList.getByText('Fun')).toBeVisible();
    await expect(rewardList.getByRole('button', { name: /Grant|Выдать/ })).toBeDisabled();
    expect(await rewardList.locator(':scope > .entity-row').evaluate((row) => {
        const list = row.parentElement?.getBoundingClientRect();
        const rowRect = row.getBoundingClientRect();
        const actionRect = row.querySelector('.entity-interactive')?.getBoundingClientRect();
        return row.classList.contains('compact')
            && list != null
            && actionRect != null
            && rowRect.right <= list.right
            && actionRect.right <= list.right;
    })).toBeTruthy();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
    await page.locator('button.catalog').first().click();
    const taskCatalog = page.getByRole('list', { name: /Task catalog|Каталог заданий/ });
    await expect(taskCatalog).toBeVisible();
    await expect(taskCatalog.locator(':scope > .entity-row')).toHaveCount(1);
    const taskRow = await expectCatalogRow(
        taskCatalog,
        /Очень длинное каталожное задание для узкого экрана/,
        '25',
        'Дом',
        /(?:раз в неделю|times? per week)/,
        /Add|Добавить/,
    );
    await expect(taskRow).not.toHaveClass(/has-selection/);
    await expect(taskRow.locator('.entity-selection')).toHaveCount(0);
    await expect(taskRow.locator('.row-main .row-metadata')).toHaveCount(1);
    expect(await taskRow.evaluate((row) => {
        const title = row.querySelector('.title')?.getBoundingClientRect();
        const metadata = row.querySelector('.row-main .row-metadata')?.getBoundingClientRect();
        return title != null && metadata != null && metadata.top - title.bottom <= 2;
    })).toBeTruthy();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
    await taskRow.getByRole('button', { name: /Очень длинное каталожное задание/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('dialog').getByRole('heading', { name: /Очень длинное каталожное задание/ })).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: /Done|Готово/ }).click();
    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(taskRow.getByRole('button', { name: /Add|Добавить/ })).toBeVisible();

    const bulkToggle = page.locator('button.bulk-toggle');
    await bulkToggle.focus();
    await expect(bulkToggle).toBeFocused();
    await page.keyboard.press('Enter');
    const taskCheckbox = taskRow.getByRole('checkbox', { name: /Очень длинное каталожное задание/ });
    await expect(taskCheckbox).toBeVisible();
    await taskCheckbox.focus();
    await expect(taskCheckbox).toBeFocused();
    await taskCheckbox.check();
    await expect(page.getByText(/Selected: 1|Выбрано: 1/)).toBeVisible();
    await bulkToggle.focus();
    await page.keyboard.press('Enter');
    await expect(taskRow.getByRole('button', { name: /Add|Добавить/ })).toBeVisible();
    await taskRow.getByRole('button', { name: /Очень длинное каталожное задание/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: /Done|Готово/ }).click();
    await page.getByRole('button', { name: /Back|Назад/ }).click();

    await page.getByRole('tab', { name: /Rewards|Награды/ }).click();
    await page.locator('button.catalog').first().click();
    const rewardCatalog = page.getByRole('list', { name: /Reward catalog|Каталог наград/ });
    await expect(rewardCatalog).toBeVisible();
    await expect(rewardCatalog.locator(':scope > .entity-row')).toHaveCount(1);
    const rewardRow = await expectCatalogRow(
        rewardCatalog,
        /Каталожная награда с длинным названием/,
        '40',
        'Отдых',
        /(?:раз в неделю|times? per week)/,
        /Add|Добавить/,
    );
    await rewardRow.getByRole('button', { name: /Каталожная награда с длинным названием/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('dialog').getByRole('heading', { name: /Каталожная награда/ })).toBeVisible();
    await page.getByRole('dialog').getByRole('button', { name: /Done|Готово/ }).click();
    await expect(rewardRow.getByRole('button', { name: /Add|Добавить/ })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();

    await page.getByRole('button', { name: /Back|Назад/ }).click();
    await page.getByRole('tab', { name: /Tasks|Задания/ }).click();
    const addTask = page.getByRole('button', { name: /Add task|Добавить задание/ });
    await addTask.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.locator('#task-name')).toBeFocused();
    expect(await page.locator('.sheet').evaluate((node) => {
        const style = getComputedStyle(node);
        const rect = node.getBoundingClientRect();
        return style.overflowY === 'auto' && rect.bottom <= window.innerHeight;
    })).toBeTruthy();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(addTask).toBeFocused();
    await page.getByRole('tab', { name: /Home|Главная/ }).click();
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toHaveAccessibleName(/Home|Главная.*\(2\)/);
    await page.getByRole('tab', { name: /Home|Главная/ }).focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toBeFocused();
    await expect(page.getByRole('tab', { name: /Tasks|Задания/ })).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Home');
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toBeFocused();
    await expect(page.getByRole('tab', { name: /Home|Главная/ })).toHaveAttribute('aria-selected', 'true');
    await page.getByRole('tab', { name: /Tasks|Задания/ }).press('End');
    await expect(page.getByRole('tab', { name: /Family|Семья/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Family', exact: true }).or(page.getByRole('heading', { name: 'Семья', exact: true }))).toBeVisible();
    await expect(page.getByRole('button', { name: /Add child|Добавить ребёнка/ })).toBeVisible();
    await page.getByRole('tab', { name: /Home|Главная/ }).click();
    const requestList = page.locator('.home .list-surface').first();
    await expectCompactList(requestList, 2);
    const requestRows = requestList.locator('.entity-row');
    await expect(page.locator('.home .list-surface').first()).toHaveCSS('border-style', 'solid');
    await expect(requestRows.nth(0)).toHaveCSS('border-bottom-width', '1px');
    await expect(requestRows.nth(1)).toHaveCSS('border-bottom-width', '0px');
    const activityList = page.locator('.home .list-surface').nth(1);
    await expect(activityList).toBeVisible();
    await expect(activityList.locator('.entity-row')).toHaveCount(1);
    await expect(activityList.getByRole('heading', { name: 'Read completed' })).toBeVisible();
    const approveButton = requestRows.nth(0).getByRole('button', { name: /Approve request|Одобрить заявку/ });
    const rejectButton = requestRows.nth(0).getByRole('button', { name: /Reject request|Отклонить заявку/ });
    for (const button of [approveButton, rejectButton]) {
        expect(await button.evaluate((node) => {
            const rect = node.getBoundingClientRect();
            return rect.width >= 44 && rect.height >= 44;
        })).toBeTruthy();
    }
    await expect(requestRows.nth(0).getByText('+20')).toBeVisible();
    await expect(requestRows.nth(0).locator('time')).toHaveCount(1);
    await page.locator('body').click({ position: { x: 1, y: 1 } });
    for (let tab = 0; tab < 40; tab += 1) {
        if (await page.evaluate(() => document.activeElement?.getAttribute('aria-label')?.match(/Approve request|Одобрить заявку/))) break;
        await page.keyboard.press('Tab');
    }
    await expect(approveButton).toBeFocused();
    await expect(approveButton).toHaveCSS('outline-width', '3px');
    await expect(approveButton).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await approveButton.press('Tab');
    await expect(rejectButton).toBeFocused();
    await expect(rejectButton).toHaveCSS('outline-width', '3px');
    await expect(rejectButton).toHaveCSS('outline-color', 'rgb(128, 170, 255)');
    await approveButton.click();
    await expect(page.getByRole('alert').filter({ hasText: /This request could not be updated|Не удалось обновить заявку/ })).toBeVisible();
    await requestRows.nth(1).getByRole('button', { name: /Reject request|Отклонить заявку/ }).click();
    const mobileNav = await page.getByRole('tablist').evaluate((node) => {
        const style = getComputedStyle(node);
        const rect = node.getBoundingClientRect();
        return { position: style.position, bottom: Math.round(window.innerHeight - rect.bottom), width: Math.round(rect.width) };
    });
    expect(mobileNav).toEqual({ position: 'fixed', bottom: 0, width: 320 });
    expect(await page.locator('.parent-workspace').evaluate((node) => node.getBoundingClientRect().width)).toBeGreaterThan(300);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
