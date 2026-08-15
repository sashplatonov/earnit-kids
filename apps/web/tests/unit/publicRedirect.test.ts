import { describe, expect, it } from 'vitest';
import { resolvePublicRedirect } from '../../src/lib/i18n/config';

describe('resolvePublicRedirect', () => {
    it('maps legacy public paths to new bare public URLs', () => {
        expect(resolvePublicRedirect('/about')).toBe('/parents');
        expect(resolvePublicRedirect('/about.html')).toBe('/parents');
        expect(resolvePublicRedirect('/features')).toBe('/tasks');
        expect(resolvePublicRedirect('/features/tasks')).toBe('/tasks');
        expect(resolvePublicRedirect('/features/shop')).toBe('/rewards');
        expect(resolvePublicRedirect('/faq.html')).toBe('/faq');
        expect(resolvePublicRedirect('/index.html')).toBe('/');
    });

    it('normalizes trailing slashes before matching', () => {
        expect(resolvePublicRedirect('/about/')).toBe('/parents');
        expect(resolvePublicRedirect('/features/shop/')).toBe('/rewards');
    });

    it('returns null for non-legacy public paths', () => {
        expect(resolvePublicRedirect('/')).toBeNull();
        expect(resolvePublicRedirect('/how')).toBeNull();
        expect(resolvePublicRedirect('/tasks')).toBeNull();
        expect(resolvePublicRedirect('/login')).toBeNull();
        expect(resolvePublicRedirect('/nonexistent')).toBeNull();
    });

    it('does not redirect authenticated app routes', () => {
        expect(resolvePublicRedirect('/app/tasks')).toBeNull();
        expect(resolvePublicRedirect('/settings')).toBeNull();
        expect(resolvePublicRedirect('/api/foo')).toBeNull();
    });
});
