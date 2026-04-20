export interface ProxyContext {
    backendOrigin: string;
    backendUrl: URL;
    publicOrigin: string;
    publicUrl: URL;
}

export function resolveProxyContext(env?: NodeJS.ProcessEnv): ProxyContext;
export function buildProxyReferer(referer: string | null | undefined, publicOrigin: string): string | null;