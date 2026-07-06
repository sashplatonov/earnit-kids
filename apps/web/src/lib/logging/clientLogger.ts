import { logBrowser, noticeBrowserError } from '$lib/observability/newrelic';

type LogLevel = 'info' | 'warn' | 'error';

function normalizeError(error: unknown): Record<string, unknown> {
    if (error instanceof Error) {
        return {
            message: error.message,
            name: error.name,
            stack: error.stack,
        };
    }

    return {
        value: String(error),
    };
}

function write(level: LogLevel, event: string, message: string, context?: Record<string, unknown>): void {
    logBrowser(level, event, message, context);
}

function currentBrowserContext(): Record<string, unknown> | undefined {
    if (typeof window === 'undefined' || typeof navigator === 'undefined') {
        return undefined;
    }

    const location = typeof window.location === 'object' && window.location !== null
        ? window.location
        : undefined;

    return {
        href: location?.href,
        path: location?.pathname,
        search: location?.search,
        userAgent: navigator.userAgent,
    };
}

export function logClientInfo(event: string, message: string, context?: Record<string, unknown>): void {
    write('info', event, message, context);
}

export function logClientWarn(event: string, message: string, context?: Record<string, unknown>): void {
    write('warn', event, message, context);
}

export function logClientError(event: string, message: string, context?: Record<string, unknown>): void {
    write('error', event, message, {
        ...currentBrowserContext(),
        ...context,
    });
}

export function installGlobalClientLogging(): () => void {
    if (typeof window === 'undefined') {
        return () => undefined;
    }

    const onUnhandledError = (event: ErrorEvent) => {
        const context = {
            event: 'ui.window_error',
            source: event.filename,
            line: event.lineno,
            column: event.colno,
            ...currentBrowserContext(),
        };
        logClientError(context.event, event.message || 'Unhandled window error', context);
        if (event.error) {
            noticeBrowserError(event.error, context);
        }
    };

    const onUnhandledRejection = (event: PromiseRejectionEvent) => {
        const context = {
            event: 'ui.unhandled_rejection',
            reason: normalizeError(event.reason),
            ...currentBrowserContext(),
        };
        logClientError(context.event, 'Unhandled promise rejection', context);
        noticeBrowserError(event.reason, context);
    };

    window.addEventListener('error', onUnhandledError);
    window.addEventListener('unhandledrejection', onUnhandledRejection);

    return () => {
        window.removeEventListener('error', onUnhandledError);
        window.removeEventListener('unhandledrejection', onUnhandledRejection);
    };
}
