import type { AppConfig } from '$lib/types/config';
import type { I18nPayload, Locale } from '$lib/i18n';
import type { SessionSnapshot } from '$lib/types/session';

declare global {
    // Injected at build time by vite.config.ts define
    const __BUILD_TS__: string;
    namespace App {
        interface Locals {
            appConfig: AppConfig;
            locale: Locale;
            session: SessionSnapshot;
        }

        interface PageData {
            appConfig: AppConfig;
            i18n: I18nPayload;
            locale: Locale;
            session: SessionSnapshot;
        }
    }
}

export {};
