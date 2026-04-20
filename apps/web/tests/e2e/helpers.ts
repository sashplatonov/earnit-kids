import { expect, type Locator, type Page } from '@playwright/test';

export const DEFAULT_PARENT_PASSWORD = 'TestPass123!';

type LoginOptions = {
    destination?: string | RegExp;
    heading?: string | RegExp;
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
    const destination = options.destination ?? /\/$/;
    const heading = options.heading ?? /EarnIt Kids/i;

    await page.goto('/login.html');
    await page.getByRole('textbox', { name: 'Email' }).fill(email);
    await page.getByRole('textbox', { name: 'Пароль' }).fill(password);
    await page.getByRole('button', { name: 'Войти' }).click();
    await expect(page).toHaveURL(destination);
    await expect(page.getByRole('heading', { name: heading })).toBeVisible();
}

export async function openFamilyApp(page: Page) {
    await page.goto('/');
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: /EarnIt Kids/i })).toBeVisible();
}

export async function logout(page: Page) {
    await page.getByRole('button', { name: 'Дополнительные разделы' }).click();
    await page.getByRole('menuitem', { name: 'Выйти' }).click();
    await expect(page).toHaveURL(/\/login\.html/);
}

export async function registerParent(page: Page, email: string, password = DEFAULT_PARENT_PASSWORD) {
    await page.goto('/login.html');
    await page.getByRole('button', { name: 'Регистрация' }).click();
    await page.getByPlaceholder('Email родителя').fill(email);
    await page.getByPlaceholder('Пароль (мин. 6)').fill(password);
    await page.getByRole('button', { name: 'Зарегистрировать' }).click();
    await expect(page.getByText('Семья зарегистрирована')).toBeVisible();
}

export async function addChild(page: Page, childName: string) {
    const firstChildButton = page.locator('#child-switcher-add-child');
    const childMenuButton = page.locator('.child-menu-btn');

    if (await firstChildButton.isVisible().catch(() => false)) {
        await firstChildButton.click();
    } else if (await childMenuButton.isVisible().catch(() => false)) {
        await childMenuButton.click();
        await page.getByRole('option', { name: 'Добавить ребенка' }).click();
    } else {
        await page.getByRole('tab', { name: /Достижения|Аналитика/ }).click();
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
    await option.click();
    await expect(activeName).toHaveText(childName);
}

export async function createTask(page: Page, title: string, reward: number, comment: string) {
    await page.getByRole('tab', { name: 'Задания' }).click();
    await page.getByRole('button', { name: '+ Добавить' }).click();
    await page.getByRole('textbox', { name: 'Название' }).fill(title);
    await page.getByRole('textbox', { name: 'Группа' }).fill('Дом');
    await page.getByRole('spinbutton', { name: 'Монеты' }).fill(String(reward));
    await page.getByRole('textbox', { name: 'Комментарий' }).fill(comment);
    await page.getByRole('button', { name: 'Сохранить' }).click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

export async function createReward(page: Page, title: string, price: number, comment: string) {
    await page.getByRole('tab', { name: 'Награды' }).click();
    await page.getByRole('button', { name: '+ Добавить' }).click();
    await page.getByRole('textbox', { name: 'Название' }).fill(title);
    await page.getByRole('textbox', { name: 'Группа' }).fill('Игры');
    await page.getByRole('spinbutton', { name: 'Цена (монеты)' }).fill(String(price));
    await page.getByRole('textbox', { name: 'Комментарий' }).fill(comment);
    await page.getByRole('button', { name: 'Сохранить' }).click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

export async function openSettings(page: Page) {
    await page.getByRole('button', { name: 'Дополнительные разделы' }).click();
    await page.getByRole('menuitem', { name: 'Настройки' }).click();
    await expect(page.locator('#settings-section h2')).toHaveText('Настройки');
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
            const tasksTab = page.getByRole('tab', { name: 'Задания' });
            if (await tasksTab.isVisible().catch(() => false)) {
                await tasksTab.click();
            }
        }
        if (!(await taskHeading.isVisible().catch(() => false))) {
            await page.reload();
            const tasksTab = page.getByRole('tab', { name: 'Задания' });
            if (await tasksTab.isVisible().catch(() => false)) {
                await tasksTab.click();
            }
        }
        await expect(taskHeading).toBeVisible();
    }
}

export async function approveFirstRequest(page: Page) {
    await page.getByRole('tab', { name: /Заявки/ }).click();
    const requestList = page.locator('#incoming-requests-list');
    await expect(requestList).toBeVisible();

    const approveButton = requestList.getByRole('button', { name: 'Одобрить заявку' }).first();
    await expect(approveButton).toBeVisible();
    await approveButton.click();
}

export async function expectHeaderBalance(page: Page, amount: number) {
    const historyButton: Locator = page.getByRole('button', { name: /Открыть историю/ });
    await expect(historyButton).toContainText(String(amount));
}