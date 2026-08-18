/**
 * Utility that forwards console messages from the browser to the backend
 * endpoint `/ui-log`. This is useful when the mini‑app runs inside a native
 * WebView where the developer cannot open the browser devtools.
 */
export function initUiLogForwarder() {
    const originalLog = console.log;
    const originalInfo = console.info;
    const originalWarn = console.warn;
    const originalError = console.error;

    async function send(level: string, args: unknown[]) {
        try {
            await fetch('/api/ui-log', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ level, message: args.map(String).join(' ') })
            });
        } catch {
            // ignore network errors – we don't want to break the UI
        }
    }

    console.log = (...args: unknown[]) => { originalLog(...args); send('info', args); };
    console.info = (...args: unknown[]) => { originalInfo(...args); send('info', args); };
    console.warn = (...args: unknown[]) => { originalWarn(...args); send('warn', args); };
    console.error = (...args: unknown[]) => { originalError(...args); send('error', args); };
}
