import type { AppConfig } from '$lib/types/config';
import type { SessionSnapshot } from '$lib/types/session';

declare global {
    // Injected at build time by vite.config.ts define
    const __BUILD_TS__: string;
    namespace App {
        interface Locals {
            appConfig: AppConfig;
            session: SessionSnapshot;
        }

        interface PageData {
            appConfig: AppConfig;
            session: SessionSnapshot;
        }
    }
}

export {};
