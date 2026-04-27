import { expect, type Locator, type Page } from '@playwright/test';

export const DEFAULT_PARENT_PASSWORD = 'TestPass123!';

type LoginOptions = {
    destination?: string | RegExp;
    heading?: string | RegExp | null;
};

export function uniqueEmail(prefix: string) {
    return `${prefix}.${Date.now()}@example.com`;
}

export async function loginParent(
    page: Page,
    email: string,
    password = DEFAULT_PARENT_PASSWORD,
    options: LoginOptions = {}
) {
    const destination = options.destination ?? /\/app\/analytics$/;
    const heading = options.heading === undefined ? /EarnIt Kids/i : options.heading;

    await page.goto('/login.html');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: /Password|Пароль/i }).fill(password);
    await page.locator('.auth-forms .btn-login').click();
    await expect(page).toHaveURL(destination);
    if (heading !== null) {
        await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    }
}

export async function openFamilyApp(page: Page) {
    await page.goto('/app');
    await expect(page).toHaveURL(/\/app\/analytics$/);
    await expect(page.getByRole('heading', { name: /EarnIt Kids/i })).toBeVisible();
}

export async function logout(page: Page) {
    await page.getByRole('button', { name: /Additional sections|Дополнительные разделы/i }).click();
    await page.getByRole('menuitem', { name: /Sign out|Logout|Выйти/i }).click();
    await expect(page).toHaveURL(/\/(?:[a-z]{2}\/)?login(?:\.html)?$/);
}

export async function registerParent(page: Page, email: string, password = DEFAULT_PARENT_PASSWORD) {
    await page.goto('/login.html');
    await page.getByRole('button', { name: /Register|Регистрация/i }).click();
    await page.getByPlaceholder(/Parent email|Email родителя/i).fill(email);
    await page.getByPlaceholder(/Password \(min\. 6\)|Пароль \(мин\. 6\)/i).fill(password);
    await page.locator('.auth-forms .btn-login').click();
    await expect(page.getByText(/Family account created|Семья зарегистрирована/i)).toBeVisible();
}

export async function addChild(page: Page, childName: string) {
    const firstChildButton = page.locator('#child-switcher-add-child');
    const childMenuButton = page.locator('.child-menu-btn');

    if (await firstChildButton.isVisible().catch(() => false)) {
        await firstChildButton.click();
    } else if (await childMenuButton.isVisible().catch(() => false)) {
        await childMenuButton.click();
        await page.locator('#child-menu-add-child').click();
    } else {
        await page.getByRole('link', { name: /Achievements|Analytics|Достижения|Аналитика/i }).click();
        await page.locator('#analytics-add-child').click();
    }

    await page.locator('#new-child-name').fill(childName);
    await page.locator('#add-child-save').click();
    await expect(page.locator('.child-menu-btn__name')).toHaveText(childName);
}

export async function selectChild(page: Page, childName: string) {
    const activeName = page.locator('.child-menu-btn__name');
    if ((await activeName.textContent())?.trim() === childName) {
        return;
    }

    await page.locator('.child-menu-btn').click();
    const option = page.getByRole('option', { name: childName });
    await expect(option).toBeVisible();
    const childDataResponse = page.waitForResponse(
        (response) => response.request().method() === 'GET' && /\/api\/data\?childId=/.test(response.url())
    );
    await option.click();
    await childDataResponse;
    await expect(activeName).toHaveText(childName);
}

export async function createTask(page: Page, title: string, reward: number, comment: string) {
    await page.getByRole('link', { name: /Tasks|Задания/i }).click();
    await page.locator('#add-task-btn').click();
    await page.locator('#task-name').fill(title);
    await page.locator('#task-group').fill('Дом');
    await page.locator('#task-coins').fill(String(reward));
    await page.locator('#task-comment').fill(comment);
    await page.locator('#task-save').click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

export async function createReward(page: Page, title: string, price: number, comment: string) {
    await page.getByRole('link', { name: /Rewards|Награды/i }).click();
    await page.locator('#add-shop-btn').click();
    await page.locator('#shop-name').fill(title);
    await page.locator('#shop-group').fill('Игры');
    await page.locator('#shop-price').fill(String(price));
    await page.locator('#shop-comment').fill(comment);
    await page.locator('#shop-save').click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

export async function openSettings(page: Page) {
    await page.getByRole('button', { name: /Additional sections|Дополнительные разделы/i }).click();
    await page.getByRole('menuitem', { name: /Settings|Настройки/i }).click();
    await expect(page.locator('#settings-section')).toBeVisible();
}

export async function getChildMagicLink(page: Page) {
    await openSettings(page);
    const input = page.locator('#settings-child-link-input-inline');
    await expect(input).toHaveValue(/\/login-child\//);
    return input.inputValue();
}

export async function loginChildByMagicLink(page: Page, childLink: string, taskTitle?: string) {
    await page.goto(childLink);
    if (taskTitle) {
        const taskHeading = page.getByRole('heading', { name: taskTitle });
        if (!(await taskHeading.isVisible().catch(() => false))) {
            const tasksLink = page.getByRole('link', { name: /Tasks|Задания/i });
            if (await tasksLink.isVisible().catch(() => false)) {
                await tasksLink.click();
            }
        }
        if (!(await taskHeading.isVisible().catch(() => false))) {
            await page.reload();
            const tasksLink = page.getByRole('link', { name: /Tasks|Задания/i });
            if (await tasksLink.isVisible().catch(() => false)) {
                await tasksLink.click();
            }
        }
        await expect(taskHeading).toBeVisible();
    }
}

export async function approveFirstRequest(page: Page) {
    await page.getByRole('link', { name: /Requests|Заявки/i }).click();
    const requestList = page.locator('#incoming-requests-list');
    await expect(requestList).toBeVisible();

    const approveButton = requestList.getByRole('button', { name: /Approve request|Одобрить заявку/i }).first();
    await expect(approveButton).toBeVisible();
    await approveButton.click();
}

export async function expectHeaderBalance(page: Page, amount: number) {
    const historyButton: Locator = page.getByRole('button', { name: /Open history|Открыть историю/i });
    await expect(historyButton).toContainText(String(amount));
}