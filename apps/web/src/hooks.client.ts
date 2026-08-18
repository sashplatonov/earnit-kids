import { initNewRelicBrowser, logBrowser, noticeBrowserError } from '$lib/observability/newrelic';
import { installGlobalClientLogging, logClientError } from '$lib/logging/clientLogger';
import { recordCatalogEvent } from '$lib/services/catalogTelemetry';
import { recordReadyCatalogEvent } from '$lib/telegram/services/readyCatalogTelemetry';

let initialized = false;

export async function init(): Promise<void> {
    if (initialized) {
        return;
    }

    initialized = true;
    await initNewRelicBrowser();
    installGlobalClientLogging();

    if (typeof window !== 'undefined') {
        window.addEventListener('catalog:telemetry', (event: Event) => {
            const detail = (event as CustomEvent).detail;
            if (detail == null) return;
            logBrowser('info', 'catalog_telemetry', JSON.stringify(detail), detail);
        });
        // Force-load both telemetry modules so their event dispatchers are bound.
        recordCatalogEvent({ name: 'noop' });
        recordReadyCatalogEvent({ name: 'noop', type: 'TASK' });
    }
}

type ClientErrorInput = {
    error: unknown;
    event: {
        url: URL;
    };
    message: string;
    status: number;
};

export function handleError({ error, event, message, status }: ClientErrorInput) {
    const context = {
        event: 'sveltekit.client_error',
        href: event.url.toString(),
        path: event.url.pathname,
        search: event.url.search,
        status,
        userAgent: typeof navigator === 'undefined' ? undefined : navigator.userAgent,
        traceId: typeof document === 'undefined'
            ? undefined
            : document.documentElement.getAttribute('data-trace-id') ?? undefined,
    };

    logClientError(context.event, message, {
        ...context,
        error: error instanceof Error
            ? {
                message: error.message,
                name: error.name,
                stack: error.stack,
            }
            : String(error),
    });
    noticeBrowserError(error, context);

    return { message };
}
