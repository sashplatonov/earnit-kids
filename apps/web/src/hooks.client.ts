import { initNewRelicBrowser, noticeBrowserError } from '$lib/observability/newrelic';
import { installGlobalClientLogging, logClientError } from '$lib/logging/clientLogger';

let initialized = false;

export async function init(): Promise<void> {
    if (initialized) {
        return;
    }

    initialized = true;
    await initNewRelicBrowser();
    installGlobalClientLogging();
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
