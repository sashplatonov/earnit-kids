import { expect, test } from '@playwright/test';

test('public marketing routes render the legacy parity content', async ({ page, request }) => {
    const healthResponse = await request.get('/healthz');
    expect(healthResponse.ok()).toBeTruthy();
    expect(healthResponse.headers()['x-content-type-options']).toBe('nosniff');
    expect(healthResponse.headers()['x-frame-options']).toBe('DENY');

    const healthPayload = await healthResponse.json();
    expect(healthPayload.status).toBe('ok');
    expect(healthPayload.service).toBe('web');

    await page.goto('/');
    await expect(page).toHaveTitle('EarnIt Kids - Family tasks and rewards');
    await expect(page.getByRole('heading', { name: 'Делаем полезные привычки понятными и веселыми' })).toBeVisible();
    await expect(page.getByText('Спокойная яркая палитра')).toBeVisible();

    await page.goto('/faq');
    await expect(page).toHaveTitle('EarnIt Kids - Frequently Asked Questions');
    await expect(page.getByText('How does it work?')).toBeVisible();
    await page.getByText('How does it work?').click();
    await expect(page.getByText('Parents assign a task, the child completes it and earns coins. Coins can then be exchanged for rewards.')).toBeVisible();

    await page.goto('/features/tasks');
    await expect(page).toHaveTitle('EarnIt Kids - Family-friendly tasks');
    await expect(page.getByRole('heading', { name: 'Tasks kids enjoy completing' })).toBeVisible();

    await page.goto('/features/shop');
    await expect(page).toHaveTitle('EarnIt Kids - Family rewards shop');
    await expect(page.getByRole('heading', { name: 'Rewards shop' })).toBeVisible();

    await page.goto('/blog');
    await expect(page).toHaveTitle('EarnIt Kids - Блог для родителей и детей');
    await expect(page.getByRole('heading', { name: 'Полезные советы для родителей и детей' })).toBeVisible();
    await expect(page.locator('.blog-card').first()).toContainText(/26\.02\.2026\s*·\s*безопасность, цифровая гигиена, родителям/);
});

test('login page switches between legacy auth panels', async ({ page }) => {
    await page.goto('/login.html');

    await expect(page.getByRole('heading', { name: 'Вход для родителей и детей' })).toBeVisible();

    await page.getByRole('button', { name: 'Регистрация' }).click();
    await expect(page.getByPlaceholder('Email родителя')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Зарегистрировать' })).toBeVisible();

    await page.getByRole('button', { name: 'Уже есть аккаунт? Войти' }).click();
    await page.getByRole('link', { name: 'Восстановить пароль' }).click();
    await expect(page.getByPlaceholder('Email для восстановления')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Отправить' })).toBeVisible();
});

test('token routes show legacy invalid-link states without query params', async ({ page }) => {
    await page.goto('/reset-password');
    await expect(page).toHaveTitle('Сброс пароля - EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Новый пароль' })).toBeVisible();
    await expect(page.getByText('Неверная ссылка для сброса пароля.')).toBeVisible();

    await page.goto('/verify');
    await expect(page).toHaveTitle('Подтверждение Email - EarnIt Kids');
    await expect(page.getByRole('heading', { name: 'Подтверждение Email' })).toBeVisible();
    await expect(page.getByText('Неверная ссылка подтверждения.')).toBeVisible();
});
