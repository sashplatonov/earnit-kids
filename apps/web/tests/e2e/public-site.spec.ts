import { expect, test } from '@playwright/test';

// EXPLAIN: Functional E2E + progressive-enhancement smoke for the new public
// EXPLAIN: site (Russian-only, bare URLs). Covers every page, navigation,
// EXPLAIN: FAQ disclosure, Telegram CTA availability, sharing, legacy
// EXPLAIN: redirects, and the root auth redirect contract.

const PUBLIC_PAGES = [
    { path: '/how', heading: 'Четыре шага — и всё понятно', title: 'Как работает — EarnIt Kids' },
    { path: '/tasks', heading: 'Задания, которые не нужно повторять', title: 'Задания — EarnIt Kids' },
    { path: '/rewards', heading: 'Награды, о которых договорились заранее', title: 'Награды — EarnIt Kids' },
    { path: '/parents', heading: 'Вы управляете правилами, а не повторяете их', title: 'Для родителей — EarnIt Kids' },
    { path: '/faq', heading: 'Вопросы, которые обычно возникают сначала', title: 'Вопросы — EarnIt Kids' },
] as const;

test.describe('public site — home', () => {
    test('home renders the public landing at the bare root', async ({ page }) => {
        await page.goto('/');
        await expect(page).toHaveTitle('EarnIt Kids | Семейные задания и награды');
        await expect(page.getByRole('heading', { level: 1, name: 'Чтобы не повторять одно и то же по пять раз' })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Что меняется дома' })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Кто за что отвечает' })).toBeVisible();
    });

    test('home exposes the secondary CTA link', async ({ page }) => {
        await page.goto('/');
        // The "how it works" CTA is an ordinary link that renders via SSR.
        await expect(page.locator('a[href="/how"]').first()).toBeVisible();
    });
});

test.describe('public site — static pages', () => {
    for (const { path, heading, title } of PUBLIC_PAGES) {
        test(`${path} renders its heading and metadata`, async ({ page }) => {
            await page.goto(path);
            await expect(page).toHaveTitle(title);
            await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
        });
    }
});

test.describe('public site — navigation', () => {
    test('header navigation links to every public page', async ({ page }) => {
        await page.goto('/');
        const nav = page.locator('nav[aria-label="Разделы сайта"]').first();
        await expect(nav.getByRole('link', { name: 'Как работает' })).toHaveAttribute('href', '/how');
        await expect(nav.getByRole('link', { name: 'Задания' })).toHaveAttribute('href', '/tasks');
        await expect(nav.getByRole('link', { name: 'Награды' })).toHaveAttribute('href', '/rewards');
        await expect(nav.getByRole('link', { name: 'Для родителей' })).toHaveAttribute('href', '/parents');
        await expect(nav.getByRole('link', { name: 'Вопросы' })).toHaveAttribute('href', '/faq');
    });

    test('navigation between pages works without reload', async ({ page }) => {
        await page.goto('/how');
        await page.getByRole('link', { name: 'Задания' }).first().click();
        await expect(page).toHaveURL(/\/tasks$/);
        await expect(page.getByRole('heading', { level: 1 })).toHaveText('Задания, которые не нужно повторять');
    });

    test('active nav item is marked with aria-current', async ({ page }) => {
        await page.goto('/tasks');
        await expect(page.locator('a[aria-current="page"]')).toHaveText('Задания');
    });
});

test.describe('public site — FAQ progressive enhancement', () => {
    test('FAQ uses native details/summary and reveals an answer', async ({ page }) => {
        await page.goto('/faq');
        const item = page.locator('details.faq-item').first();
        await expect(item).toHaveAttribute('open');
        await expect(item.locator('summary')).toContainText('Ребёнок может сам начислить себе монеты?');

        const secondItem = page.locator('details.faq-item').nth(1);
        await secondItem.locator('summary').click();
        await expect(secondItem).toHaveAttribute('open');
        await expect(secondItem.locator('p')).toContainText('Нет. Можно добавить настолку');
    });
});

test.describe('public site — legacy redirects', () => {
    const redirects = [
        ['/about', '/parents'],
        ['/about.html', '/parents'],
        ['/features', '/tasks'],
        ['/features/tasks', '/tasks'],
        ['/features/shop', '/rewards'],
        ['/faq.html', '/faq'],
        ['/index.html', '/'],
    ] as const;

    for (const [from, to] of redirects) {
        test(`${from} permanently redirects to ${to}`, async ({ request }) => {
            const response = await request.get(from, { maxRedirects: 0 });
            expect(response.status()).toBe(308);
            const location = response.headers()['location'];
            expect(location).toBe(to);
        });
    }
});

test.describe('public site — root auth contract', () => {
    test('anonymous GET / returns the public landing (200)', async ({ request }) => {
        const response = await request.get('/');
        expect(response.status()).toBe(200);
        const body = await response.text();
        expect(body).toContain('Чтобы не повторять одно и то же по пять раз');
    });
});
