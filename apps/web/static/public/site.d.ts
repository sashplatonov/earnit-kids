export const GOOGLE_WORKSPACE_FALLBACK: string;
export function requestBrowserWorkspaceUrl(
    fetchImpl: typeof fetch,
    config?: { redirectTo?: string },
): Promise<string>;
