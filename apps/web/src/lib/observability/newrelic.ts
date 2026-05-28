type NewRelicLogLevel = 'debug' | 'info' | 'warn' | 'error' | 'trace';

type NewRelicPrimitive = string | number | boolean | null;

type NewRelicInfo = {
    applicationID: string;
    licenseKey: string;
};

type NewRelicApi = {
    log?: (message: string, options?: { level?: NewRelicLogLevel; customAttributes?: Record<string, NewRelicPrimitive> }) => void;
    noticeError?: (error: Error | string, customAttributes?: Record<string, NewRelicPrimitive>) => void;
    setPageViewName?: (name: string, host?: string) => void;
    setCustomAttribute?: (name: string, value: NewRelicPrimitive, persist?: boolean) => void;
};

const DEFAULT_INIT = {
    ajax: { enabled: true },
    generic_events: { enabled: true },
    jserrors: { enabled: true },
    logging: { enabled: true },
    metrics: { enabled: true },
    page_view_timing: { enabled: true },
    session_trace: { enabled: true },
    soft_navigations: { enabled: true },
};

let initialized = false;

function isEnabled(): boolean {
    return (import.meta.env.VITE_NEW_RELIC_BROWSER_ENABLED as string | undefined) !== 'false';
}

function parseJsonEnv<T>(value: string | undefined): T | null {
    if (!value?.trim()) {
        return null;
    }

    try {
        return JSON.parse(value) as T;
    } catch {
        return null;
    }
}

function normalizeAttributeValue(value: unknown): NewRelicPrimitive {
    if (value === null || value === undefined) {
        return null;
    }
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        return value;
    }

    try {
        return JSON.stringify(value);
    } catch {
        return String(value);
    }
}

function normalizeAttributes(attributes?: Record<string, unknown>): Record<string, NewRelicPrimitive> | undefined {
    if (!attributes) {
        return undefined;
    }

    return Object.fromEntries(
        Object.entries(attributes).map(([key, value]) => [key, normalizeAttributeValue(value)])
    );
}

function getNewRelicApi(): NewRelicApi | null {
    if (typeof window === 'undefined') {
        return null;
    }

    const candidate = (window as Window & { newrelic?: NewRelicApi }).newrelic;
    return candidate ?? null;
}

export async function initNewRelicBrowser(): Promise<void> {
    if (typeof window === 'undefined' || initialized || !isEnabled()) {
        return;
    }

    const info = parseJsonEnv<NewRelicInfo>(import.meta.env.VITE_NEW_RELIC_BROWSER_INFO as string | undefined);
    if (!info?.applicationID || !info.licenseKey) {
        return;
    }

    const init = {
        ...DEFAULT_INIT,
        ...parseJsonEnv<Record<string, unknown>>(import.meta.env.VITE_NEW_RELIC_BROWSER_INIT as string | undefined),
    };
    const loaderConfig = parseJsonEnv<Record<string, unknown>>(
        import.meta.env.VITE_NEW_RELIC_BROWSER_LOADER_CONFIG as string | undefined
    );

    const { BrowserAgent } = await import('@newrelic/browser-agent/loaders/browser-agent');
    new BrowserAgent({
        info,
        init,
        ...(loaderConfig ? { loader_config: loaderConfig } : {}),
    });

    initialized = true;
    setBrowserPageViewName(window.location.pathname);

    const deploymentEnv = import.meta.env.MODE;
    if (deploymentEnv) {
        getNewRelicApi()?.setCustomAttribute?.('deployment.environment', deploymentEnv, true);
    }
}

export function setBrowserPageViewName(pathname: string): void {
    getNewRelicApi()?.setPageViewName?.(pathname);
}

export function logBrowser(
    level: NewRelicLogLevel,
    event: string,
    message: string,
    context?: Record<string, unknown>
): void {
    getNewRelicApi()?.log?.(message, {
        level,
        customAttributes: normalizeAttributes({
            event,
            path: typeof window === 'undefined' ? undefined : window.location.pathname,
            ...context,
        }),
    });
}

export function noticeBrowserError(
    error: unknown,
    attributes?: Record<string, unknown>
): void {
    const normalizedError = error instanceof Error ? error : new Error(String(error));
    getNewRelicApi()?.noticeError?.(normalizedError, normalizeAttributes(attributes));
}
