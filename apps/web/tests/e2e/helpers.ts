import { type Page } from '@playwright/test';

export const DEFAULT_PARENT_PASSWORD = 'TestPass123!';

export function uniqueEmail(prefix: string) {
    return `${prefix}.${Date.now()}@example.com`;
}

export async function authenticateE2eSession(page: Page, role: 'parent' | 'child' = 'parent') {
    await page.goto('/');
    const host = new URL(page.url()).hostname;
    await page.context().addCookies([
        { name: 'e2e_session', value: role, domain: host, path: '/' },
        { name: 'csrf_token', value: 'e2e-csrf', domain: host, path: '/' },
    ]);
}

export async function registerParent(
    page: Page,
    email: string,
    password = DEFAULT_PARENT_PASSWORD
) {
    const response = await page.request.post('/api/register', { data: { email, password } });
    if (!response.ok()) {
        throw new Error(`Register failed (${response.status()}): ${await response.text()}`);
    }

}
