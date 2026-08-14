import type { Page } from '@playwright/test';

export async function preserveTelegramFixture(page: Page): Promise<void> {
    await page.route('https://telegram.org/js/telegram-web-app.js', (route) => route.fulfill({
        status: 200,
        contentType: 'application/javascript',
        body: '',
    }));
}
