export type CatalogView = 'grid' | 'list';

export interface CatalogViewState {
    group: string;
    view: CatalogView;
}

export function readCatalogViewState(url: URL, fallbackView: CatalogView): CatalogViewState {
    const view = url.searchParams.get('view');
    return {
        group: url.searchParams.get('group') ?? '',
        view: view === 'grid' || view === 'list' ? view : fallbackView,
    };
}

export function writeCatalogViewState(url: URL, patch: Partial<CatalogViewState>): URL {
    const next = new URL(url);
    if (patch.group !== undefined) {
        if (patch.group) next.searchParams.set('group', patch.group);
        else next.searchParams.delete('group');
    }
    if (patch.view !== undefined) next.searchParams.set('view', patch.view);
    return next;
}
