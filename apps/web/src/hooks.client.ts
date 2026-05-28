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
        path: event.url.pathname,
        status,
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
