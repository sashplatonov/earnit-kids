export type DiagnosticSeverity = 'warn' | 'error';

export type DiagnosticEventCode =
    | 'web.server_error'
    | 'web.proxy_failure'
    | 'web.session_failure';

export type DiagnosticEvent = {
    severity: DiagnosticSeverity;
    code: DiagnosticEventCode;
    route: string;
    status?: number;
    category: string;
    traceId: string;
    durationMs?: number;
    errorClass?: string;
};

const SAFE_ERROR_CLASSES = new Set(['AbortError', 'TypeError', 'SyntaxError', 'Error']);

export function safeErrorClass(error: unknown): string {
    const name = error instanceof Error ? error.name : '';
    return SAFE_ERROR_CLASSES.has(name) ? name : 'UnknownError';
}

function normalizeErrorClass(errorClass: string): string {
    return SAFE_ERROR_CLASSES.has(errorClass) ? errorClass : 'UnknownError';
}

export function emitDiagnostic(event: DiagnosticEvent): void {
    // EXPLAIN: Keep operational logs bounded and machine-readable. Never pass
    // request URLs, headers, exception messages, or arbitrary user data here.
    const safeEvent = {
        severity: event.severity,
        eventCode: event.code,
        route: event.route.slice(0, 160),
        status: typeof event.status === 'number' ? event.status : undefined,
        category: event.category.slice(0, 80),
        traceId: event.traceId.slice(0, 128),
        durationMs: typeof event.durationMs === 'number' ? Math.max(0, Math.round(event.durationMs)) : undefined,
        errorClass: event.errorClass ? normalizeErrorClass(event.errorClass) : undefined,
    };
    console.error(JSON.stringify(safeEvent));
}

export function requestTraceId(request: Request): string {
    const existing = request.headers.get('x-trace-id');
    return existing?.trim().slice(0, 128) || crypto.randomUUID();
}