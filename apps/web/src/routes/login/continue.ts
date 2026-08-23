import { localizePath } from '$lib/i18n';

export function resolveContinuePath(value: string | null, locale: Parameters<typeof localizePath>[1]): string | null {
    if (!value || value.startsWith('//')) {
        return null;
    }

    try {
        const target = new URL(value, 'https://earnit.invalid');
        if (target.origin !== 'https://earnit.invalid' || !target.pathname.startsWith('/') || target.pathname.startsWith('//')) {
            return null;
        }

        return `${localizePath(target.pathname, locale)}${target.search}${target.hash}`;
    } catch {
        return null;
    }
}
