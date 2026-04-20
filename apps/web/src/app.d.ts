import type { AppConfig } from '$lib/types/config';
import type { SessionSnapshot } from '$lib/types/session';

declare global {
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
