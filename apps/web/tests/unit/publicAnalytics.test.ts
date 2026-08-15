import { describe, expect, it, vi, afterEach } from 'vitest';
import {
    trackPublicPageView,
    trackPublicCtaClick,
    trackPublicShare,
} from '../../src/lib/observability/publicAnalytics';

const logBrowserModule = vi.hoisted(() => ({
    logBrowser: vi.fn(),
}));

vi.mock('$lib/observability/newrelic', () => ({
    logBrowser: logBrowserModule.logBrowser,
}));

describe('publicAnalytics', () => {
    afterEach(() => {
        vi.clearAllMocks();
    });

    it('tracks a page view with a normalized page', () => {
        trackPublicPageView('/how');
        expect(logBrowserModule.logBrowser).toHaveBeenCalledWith(
            'info',
            'public_page_view',
            'Public page viewed',
            { page: '/how' }
        );
    });

    it('normalizes an empty page to the root path', () => {
        trackPublicPageView('');
        expect(logBrowserModule.logBrowser).toHaveBeenCalledWith(
            'info',
            'public_page_view',
            'Public page viewed',
            { page: '/' }
        );
    });

    it('tracks a CTA click with placement and page', () => {
        trackPublicCtaClick('hero', '/');
        expect(logBrowserModule.logBrowser).toHaveBeenCalledWith(
            'info',
            'public_cta_click',
            'Public CTA clicked',
            { placement: 'hero', page: '/' }
        );
    });

    it('tracks a share event with page', () => {
        trackPublicShare('/');
        expect(logBrowserModule.logBrowser).toHaveBeenCalledWith(
            'info',
            'public_share',
            'Public site shared',
            { page: '/' }
        );
    });
});
