import { expect, type Locator, type Page } from '@playwright/test';

export function captureApiRequests(page: Page): string[] {
    const apiRequests: string[] = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests.push(request.url());
    });
    return apiRequests;
}

export function expectNoApiRequests(apiRequests: string[]): void {
    expect(apiRequests, `live demo must stay isolated from the API; unexpected requests: ${apiRequests.join(', ')}`).toEqual([]);
}

export async function expectTargetSize(locator: Locator): Promise<void> {
    const box = await locator.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.width).toBeGreaterThanOrEqual(44);
    expect(box!.height).toBeGreaterThanOrEqual(44);
}

export async function expectNoHorizontalOverflow(page: Page): Promise<void> {
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
}

export async function expectParentTabs(page: Page): Promise<void> {
    for (const name of [/^Home/, 'Tasks', 'Rewards', 'Family'] as const) {
        await expect(page.getByRole('tab', { name })).toBeVisible();
    }
}
