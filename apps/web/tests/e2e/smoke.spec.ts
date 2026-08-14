import { expect, test } from '@playwright/test';

test('public marketing routes render the legacy parity content', async ({ page, request }) => {
    const healthResponse = await request.get('/healthz');
    expect(healthResponse.ok()).toBeTruthy();
    expect(healthResponse.headers()['x-content-type-options']).toBe('nosniff');
    expect(healthResponse.headers()['x-frame-options']).toBe('DENY');

    const healthPayload = await healthResponse.json();
    expect(healthPayload.status).toBe('ok');
    expect(healthPayload.service).toBe('web');

    await page.goto('/en/');
    await expect(page).toHaveTitle('EarnIt Kids | Family tasks and rewards');
    await expect(page.getByRole('heading', { name: 'Make useful habits clear and fun' })).toBeVisible();
    await expect(page.getByText('Calm and friendly design')).toBeVisible();

    await page.goto('/en/faq');
    await expect(page).toHaveTitle('EarnIt Kids | Frequently asked questions');
    await expect(page.getByText('How does it work?')).toBeVisible();
    await page.getByText('How does it work?').click();
    await expect(page.getByText('Parents create a task, the child completes it, and coins can later be exchanged for rewards.')).toBeVisible();

    await page.goto('/en/features/tasks');
    await expect(page).toHaveTitle('EarnIt Kids | Family-friendly tasks');
    await expect(page.getByRole('heading', { name: 'Tasks children enjoy completing' })).toBeVisible();

    await page.goto('/en/features/shop');
    await expect(page).toHaveTitle('EarnIt Kids | Family rewards shop');
    await expect(page.getByRole('heading', { name: 'Rewards shop that stays fair' })).toBeVisible();

    await page.goto('/en/blog');
    await expect(page).toHaveTitle('EarnIt Kids | Blog for parents and children');
    await expect(page.getByRole('heading', { name: 'Helpful ideas for parents and children' })).toBeVisible();
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
    await page.goto('/en/reset-password');
    await expect(page).toHaveTitle('EarnIt Kids | Reset password');
    await expect(page.getByRole('heading', { name: 'New password' })).toBeVisible();
    await expect(page.getByText('This password reset link is invalid.')).toBeVisible();

    await page.goto('/en/verify');
    await expect(page).toHaveTitle('EarnIt Kids | Email verification');
    await expect(page.getByRole('heading', { name: 'Email verification' })).toBeVisible();
    await expect(page.getByText('The verification link is invalid.')).toBeVisible();
});
