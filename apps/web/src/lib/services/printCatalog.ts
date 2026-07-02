export function buildPrintCatalogUrl(basePath: string, childId?: string | number | null): string {
    if (childId == null) {
        return basePath;
    }

    const separator = basePath.includes('?') ? '&' : '?';
    return `${basePath}${separator}childId=${encodeURIComponent(String(childId))}`;
}
