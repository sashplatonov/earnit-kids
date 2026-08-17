<script lang="ts">
    import { browser } from '$app/environment';
    import { onMount, onDestroy, tick } from 'svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { approveRequest, rejectRequest, deleteRequest, fetchRequestsFromServer } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { showToast } from '$lib/stores/toasts';
    import type { Request } from '$lib/stores/app';
    import { buildRequestCatalog, resolveRequestCard, type RequestDetailsI18n } from './requestDetails';
    import { normalizeRequest } from '$lib/services/serverContract';

    const i18n = useI18n();
    let viewMode: CardViewMode = 'list';
    const loadedViewRole: { value: CardViewRole | null } = { value: null };
    let openNoteRequestId: string | null = null;
    let notePopoverStyle = '';
    let notePopoverElement: HTMLDivElement | null = null;
    let noteAnchorRect: DOMRect | null = null;

    $: requests = $appStore.requests;
    $: isAdmin = $appStore.isAdmin;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;
    $: if (browser && loadedViewRole.value !== viewRole) {
        viewMode = loadCardViewMode('requests', viewRole);
        loadedViewRole.value = viewRole;
    }

    function tHistory(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`history.${key}` as MessageKey, variables);
    }

    function createRequestDetailsI18n(): RequestDetailsI18n {
        return {
            t(key) {
                return tHistory(`model.${key}`);
            },
        };
    }

    $: requestDetailsI18n = ($i18n.locale, createRequestDetailsI18n());

    $: requestCatalog = buildRequestCatalog({
        tasks: $appStore.tasks,
        shopItems: $appStore.shopItems,
        baseTasks: $appStore.baseData.tasks,
        baseProducts: $appStore.baseData.products,
    });

    $: incomingRequests = requests
        .filter(r => r.status === 'pending')
        .map(r => ({ ...r, ui: resolveRequestCard(r, requestCatalog, requestDetailsI18n) }));
    $: myRequests = requests.map(r => ({ ...r, ui: resolveRequestCard(r, requestCatalog, requestDetailsI18n) }));

    // Polling every 8 s — lightweight requests-only endpoint instead of full `/api/data`.
    // WS refresh already triggers snapshot refresh on 'update' events; this is the fallback.
    let pollTimer: ReturnType<typeof setInterval> | null = null;

    async function pollRequests(): Promise<void> {
        const res = await fetchRequestsFromServer(1, 50);
        if (res?.items && Array.isArray(res.items)) {
            const normalized = (res.items as Record<string, unknown>[]).map(normalizeRequest);
            appStore.setState({ requests: normalized as unknown as Request[] });
        }
    }

    onMount(() => {
        pollTimer = setInterval(() => { void pollRequests(); }, 8_000);
        const handleDocumentClick = (event: MouseEvent) => {
            const target = event.target;
            if (!(target instanceof Element) || !target.closest('.request-note-tooltip')) {
                openNoteRequestId = null;
            }
        };

        const handleDocumentKeydown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                openNoteRequestId = null;
            }
        };

        document.addEventListener('click', handleDocumentClick);
        document.addEventListener('keydown', handleDocumentKeydown);

        return () => {
            if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
            document.removeEventListener('click', handleDocumentClick);
            document.removeEventListener('keydown', handleDocumentKeydown);
        };
    });

    onDestroy(() => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } });

    async function handleApprove(req: Request) {
        const res = await approveRequest(req.id, req.childId) as Record<string, unknown> | null;
        if (res) {
            applyDataSnapshot(res);
            showToast(tHistory('requests.approvedToast'), 'success');
        }
    }

    async function handleReject(req: Request) {
        const res = await rejectRequest(req.id, req.childId) as Record<string, unknown> | null;
        if (res) {
            applyDataSnapshot(res);
            showToast(tHistory('requests.rejectedToast'), 'info');
        }
    }

    async function handleDelete(reqId: unknown) {
        const ok = await deleteRequest(reqId, $appStore.currentChildId);
        if (ok) {
            appStore.setState({ requests: requests.filter(r => r.id !== reqId) });
            showToast(tHistory('requests.deletedToast'), 'info');
        } else {
            showToast(tHistory('requests.deleteFailedToast'), 'error');
        }
    }

    function formatDate(dateStr: string | null | undefined): string {
        if (!dateStr) return '';
        try {
            return $i18n.formatDate(new Date(dateStr), {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
            });
        } catch {
            return '';
        }
    }

    function requestCreatedAt(req: Request): string {
        return typeof req['createdAt'] === 'string' ? (req['createdAt'] as string) : '';
    }

    function requestStatusLabel(status: string | null | undefined): string {
        if (status === 'approved') return tHistory('requests.statusApproved');
        if (status === 'rejected') return tHistory('requests.statusRejected');
        return tHistory('requests.statusPending');
    }

    function requestStatusClass(status: string | null | undefined): string {
        if (status === 'approved') return 'request-chip--success';
        if (status === 'rejected') return 'request-chip--danger';
        return 'request-chip--warning';
    }

    function hasMoneyAmount(value: number): boolean {
        return Number(value ?? 0) > 0;
    }

    function formatRequestAmount(req: { ui: { amountPrefix: string; coins: number } }): string {
        return `${req.ui.amountPrefix}${$i18n.formatNumber(req.ui.coins)}`;
    }

    function requestMoneyLabel(value: number): string {
        return hasMoneyAmount(value) ? `${$i18n.formatNumber(value)} 💶` : '';
    }

    function resolveChildName(req: Request): string {
        // Prefer backend-provided nickname if present
        if (req.childNickname) return req.childNickname;

        // Fallback: lookup in loaded children list by childId
        const childId = req.childId;
        if (childId != null) {
            const fromChildren = $appStore.children?.find(c => String(c.id) === String(childId))?.nickname;
            if (fromChildren) return fromChildren;
        }

        // Fallback: for child role, use current child's nickname if store has it
        if (!isAdmin && $appStore.childNickname) return $appStore.childNickname;

        return '';
    }

    function setViewMode(nextMode: CardViewMode) {
        viewMode = nextMode;
        saveCardViewMode('requests', viewRole, nextMode);
    }

    function requestNoteId(reqId: unknown): string {
        return `request-note-${String(reqId)}`;
    }

    async function toggleNote(reqId: unknown, event?: MouseEvent) {
        const nextId = String(reqId);
        if (openNoteRequestId === nextId) {
            openNoteRequestId = null;
            noteAnchorRect = null;
            return;
        }

        openNoteRequestId = nextId;

        const button = event?.currentTarget instanceof HTMLElement
            ? event.currentTarget
            : null;

        if (!button) {
            notePopoverStyle = '';
            noteAnchorRect = null;
            return;
        }

        noteAnchorRect = button.getBoundingClientRect();

        await tick();

        const rect = noteAnchorRect;
        const popover = notePopoverElement;
        if (!rect || !popover) {
            notePopoverStyle = '';
            return;
        }

        const viewportWidth = window.innerWidth;
        const viewportHeight = window.innerHeight;
        const tooltipWidth = Math.min(popover.offsetWidth || 288, viewportWidth - 16);
        const tooltipHeight = popover.offsetHeight || 0;
        const left = Math.min(
            Math.max(8, rect.right - tooltipWidth),
            viewportWidth - tooltipWidth - 8
        );
        const preferredTop = rect.bottom + 8;
        const top = preferredTop + tooltipHeight > viewportHeight - 8
            ? Math.max(8, rect.top - tooltipHeight - 8)
            : preferredTop;

        notePopoverStyle = `left:${left}px; top:${top}px;`;
    }
</script>

<section class="section" id="requests-section">
    {#if isAdmin}
    <!-- Admin view: incoming requests -->
    <div class="admin-only">
        <div class="section__header">
            <div class="section__header-titles">
                <h2>{tHistory('requests.adminTitle')}</h2>
                <p class="section__subtitle">{tHistory('requests.adminSubtitle')}</p>
            </div>
            <SectionHeaderControls
                isAdmin={false}
                {viewMode}
                viewAriaLabel={tHistory('requests.viewAria')}
                gridLabel={tHistory('requests.viewGrid')}
                listLabel={tHistory('requests.viewList')}
                on:viewMode={(event) => setViewMode(event.detail)}
            />
        </div>

        {#if incomingRequests.length > 0}
        <div id="incoming-requests-list" class="cards request-list" class:cards--list={viewMode === 'list'}>
            {#each incomingRequests as req (req.id)}
            {@const childName = resolveChildName(req)}
            {@const createdAt = formatDate(requestCreatedAt(req))}
            <article
                class="card request-card"
                class:request-card--list={viewMode === 'list'}
                class:request-card--purchase={req.ui.isPurchase}
                class:request-card--task={!req.ui.isPurchase}>
                <div class="request-item__icon">
                    <span class={`gamified-icon ${req.ui.iconClass}`} aria-hidden="true"></span>
                </div>
                <div class="request-item__content">
                    <h3 class="request-item__title">
                        <span>{req.ui.title}</span>
                        {#if req.ui.note}
                        <span class="request-note-tooltip request-item__note">
                            <button
                                type="button"
                                class="request-item__note-btn"
                                aria-label={tHistory('requests.noteButtonAria')}
                                aria-expanded={openNoteRequestId === String(req.id)}
                                aria-controls={requestNoteId(req.id)}
                                on:click|stopPropagation={(event) => toggleNote(req.id, event)}
                            >
                                <span aria-hidden="true">📝</span>
                            </button>
                        </span>
                        {/if}
                    </h3>
                    <div class="request-item__chips">
                        {#if childName}<span class="request-chip request-chip--child">{childName}</span>{/if}
                        <span class={`request-chip ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                        <span class="request-chip request-chip--group">{req.ui.group}</span>
                        {#if createdAt}<span class="request-chip request-chip--muted">{createdAt}</span>{/if}
                    </div>
                    {#if req.ui.description}
                    <p class="request-item__comment">{req.ui.description}</p>
                    {/if}
                </div>
                <div class="request-item__actions">
                    <div class="request-item__amounts">
                        <span class="request-item__coins {req.ui.isPurchase ? 'item-coins' : 'task-coins'}">
                            <span class="gamified-icon icon-coin" aria-hidden="true"></span>
                            {formatRequestAmount(req)}
                        </span>
                        {#if requestMoneyLabel(req.ui.moneyAmount)}
                        <span class="request-item__money">{requestMoneyLabel(req.ui.moneyAmount)}</span>
                        {/if}
                    </div>
                    <div class="request-item__buttons">
                        <button class="btn btn--success btn--small" aria-label={tHistory('requests.approveAria')} on:click={() => handleApprove(req)}>✓</button>
                        <button class="btn btn--danger btn--small" aria-label={tHistory('requests.rejectAria')} on:click={() => handleReject(req)}>✗</button>
                    </div>
                </div>
            </article>
            {/each}
        </div>
        {:else}
        <article class="empty-state" id="incoming-requests-empty">
            <span class="empty-state__icon">
                <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
            </span>
            <p class="empty-state__title">{tHistory('requests.emptyIncomingTitle')}</p>
            <p class="empty-state__hint">{tHistory('requests.emptyIncomingHint')}</p>
        </article>
        {/if}
    </div>
    {:else}
    <!-- Child view: my requests -->
    <div class="child-only">
        <div class="section__header">
            <div class="section__header-titles">
                <h2>{tHistory('requests.childTitle')}</h2>
                <p class="section__subtitle">{tHistory('requests.childSubtitle')}</p>
            </div>
            <SectionHeaderControls
                isAdmin={false}
                {viewMode}
                viewAriaLabel={tHistory('requests.viewAria')}
                gridLabel={tHistory('requests.viewGrid')}
                listLabel={tHistory('requests.viewList')}
                on:viewMode={(event) => setViewMode(event.detail)}
            />
        </div>
        {#if myRequests.length > 0}
        <div class="cards request-list" class:cards--list={viewMode === 'list'} id="my-requests-list">
            {#each myRequests as req (req.id)}
            <article
                class="card request-card request-card--mine"
                class:request-card--list={viewMode === 'list'}
                class:request-card--purchase={req.ui.isPurchase}
                class:request-card--task={!req.ui.isPurchase}>
                <div class="request-item__icon">
                    <span class={`gamified-icon ${req.ui.iconClass}`} aria-hidden="true"></span>
                </div>
                <div class="request-item__content">
                    <h3 class="request-item__title">
                        <span>{req.ui.title}</span>
                        {#if req.ui.note}
                        <span class="request-note-tooltip request-item__note">
                            <button
                                type="button"
                                class="request-item__note-btn"
                                aria-label={tHistory('requests.noteButtonAria')}
                                aria-expanded={openNoteRequestId === String(req.id)}
                                aria-controls={requestNoteId(req.id)}
                                on:click|stopPropagation={(event) => toggleNote(req.id, event)}
                            >
                                <span aria-hidden="true">📝</span>
                            </button>
                        </span>
                        {/if}
                    </h3>
                    <div class="request-item__chips">
                        <span class={`request-chip ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                        <span class="request-chip request-chip--group">{req.ui.group}</span>
                        <span class={`request-chip ${requestStatusClass(req.status)}`}>
                            <span class="request-status-dot" aria-hidden="true"></span>
                            {requestStatusLabel(req.status)}
                        </span>
                        {#if formatDate(requestCreatedAt(req))}
                        <span class="request-chip request-chip--muted">{formatDate(requestCreatedAt(req))}</span>
                        {/if}
                    </div>
                    {#if req.ui.description}
                    <p class="request-item__comment">{req.ui.description}</p>
                    {/if}
                </div>
                <div class="request-item__actions">
                    <div class="request-item__amounts">
                        <span class="request-item__coins {req.ui.isPurchase ? 'item-coins' : 'task-coins'}">
                            <span class="gamified-icon icon-coin" aria-hidden="true"></span>
                            {formatRequestAmount(req)}
                        </span>
                        {#if requestMoneyLabel(req.ui.moneyAmount)}
                        <span class="request-item__money">{requestMoneyLabel(req.ui.moneyAmount)}</span>
                        {/if}
                    </div>
                    <div class="request-item__buttons">
                        {#if req.status !== 'approved'}
                        <button class="history-item__delete-btn" on:click={() => handleDelete(req.id)} aria-label={tHistory('requests.deleteAria')}>✕</button>
                        {/if}
                    </div>
                </div>
            </article>
            {/each}
        </div>
        {:else}
        <article class="empty-state" id="my-requests-empty">
            <span class="empty-state__icon">
                <span class="gamified-icon icon-envelope" aria-hidden="true"></span>
            </span>
            <p class="empty-state__title">{tHistory('requests.emptyMineTitle')}</p>
            <p class="empty-state__hint">{tHistory('requests.emptyMineHint')}</p>
        </article>
        {/if}
    </div>
    {/if}
</section>

{#if openNoteRequestId}
    {@const activeRequest = [...incomingRequests, ...myRequests].find((req) => String(req.id) === openNoteRequestId)}
    {#if activeRequest?.ui.note}
    <div class="request-note-popover" id={requestNoteId(openNoteRequestId)} role="tooltip" style={notePopoverStyle} bind:this={notePopoverElement}>
        <span class="request-note-popover__label">{tHistory('requests.noteLabel')}</span>
        <span>{activeRequest.ui.note}</span>
    </div>
    {/if}
{/if}

<style>
    .request-card {
        min-height: 0;
        height: auto;
        overflow: visible;
        padding: 0.6rem 0.75rem;
        display: grid;
        grid-template-columns: 2.25rem minmax(0, 1fr) auto;
        grid-template-areas: 'icon content actions';
        gap: 0.6rem;
        align-items: center;
    }

    .request-card--purchase .request-item__coins {
        background: linear-gradient(135deg, rgba(239, 68, 68, 0.9), rgba(190, 70, 52, 0.9));
        color: white;
    }

    .request-card--task .request-item__coins {
        background: var(--gradient-success);
        color: white;
    }

    .request-item__icon {
        grid-area: icon;
        display: grid;
        place-items: center;
        width: 2.25rem;
        height: 2.25rem;
        border-radius: 0.65rem;
        background: rgba(99, 102, 241, 0.1);
        color: #5b63e9;
        flex-shrink: 0;
    }

    .request-item__icon .gamified-icon {
        width: 1.15rem;
        height: 1.15rem;
    }

    .request-item__content {
        grid-area: content;
        display: flex;
        flex-direction: column;
        gap: 0.22rem;
        min-width: 0;
    }

    .request-item__title {
        margin: 0;
        font-size: clamp(0.82rem, 3.8cqi, 0.98rem);
        font-weight: var(--font-heading-weight);
        color: var(--color-text-high-contrast);
        line-height: 1.25;
        display: flex;
        align-items: center;
        gap: 0.35rem;
        min-width: 0;
    }

    .request-item__title > span:first-child {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .request-item__chips {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.25rem;
    }

    .request-item__comment {
        margin: 0;
        color: var(--color-text-muted);
        font-size: 0.78rem;
        line-height: 1.35;
        overflow: hidden;
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 1;
        line-clamp: 1;
    }

    .request-item__actions {
        grid-area: actions;
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.35rem;
        flex-shrink: 0;
        min-width: 0;
    }

    .request-item__amounts {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.22rem;
    }

    .request-item__coins {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.25rem;
        background: var(--gradient-gold);
        color: var(--color-text-high-contrast);
        padding: 0.25rem 0.6rem;
        border-radius: var(--radius-xl);
        font-weight: 700;
        font-size: 0.82rem;
        white-space: nowrap;
        flex-shrink: 0;
    }

    .request-item__coins .gamified-icon {
        width: 0.85rem;
        height: 0.85rem;
    }

    .request-item__money {
        display: inline-flex;
        align-items: center;
        width: fit-content;
        padding: 0.15rem 0.4rem;
        border-radius: 999px;
        background: rgba(245, 158, 11, 0.12);
        color: #8a6118;
        font-size: 0.72rem;
        font-weight: 800;
        line-height: 1;
        white-space: nowrap;
    }

    .request-item__buttons {
        display: flex;
        align-items: center;
        gap: 0.28rem;
        margin-left: auto;
    }

    .request-item__buttons .btn {
        min-width: 1.9rem;
        min-height: 1.9rem;
        padding-inline: 0.45rem;
    }

    .request-item__note-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.45rem;
        height: 1.45rem;
        padding: 0;
        border: 1px solid rgba(99, 102, 241, 0.22);
        border-radius: 9999px;
        background: rgba(99, 102, 241, 0.1);
        color: #4338ca;
        cursor: pointer;
        line-height: 1;
        font-size: 0.82rem;
        transition: background-color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
        flex: 0 0 auto;
    }

    .request-item__note-btn:hover,
    .request-item__note-btn:focus-visible {
        background: rgba(99, 102, 241, 0.16);
        border-color: rgba(99, 102, 241, 0.34);
        transform: translateY(-1px);
        outline: none;
    }

    .request-note-popover {
        position: fixed;
        z-index: 1200;
        display: grid;
        gap: 0.22rem;
        width: min(18rem, calc(100vw - 1rem));
        max-width: calc(100vw - 1rem);
        padding: 0.6rem 0.7rem;
        border: 1px solid rgba(148, 163, 184, 0.28);
        border-radius: 0.85rem;
        background: rgba(255, 255, 255, 0.98);
        box-shadow: 0 16px 40px rgba(15, 23, 42, 0.14);
        color: #334155;
        font-size: 0.76rem;
        line-height: 1.35;
        white-space: normal;
    }

    .request-note-popover__label {
        font-weight: 800;
        color: #1e293b;
    }

    /* Status chip with colored dot */
    .request-status-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        padding: 0.18rem 0.5rem;
        border-radius: 999px;
        font-size: 0.68rem;
        font-weight: 700;
        line-height: 1;
        white-space: nowrap;
        border: 1px solid transparent;
    }

    .request-status-chip.request-chip--success {
        background: color-mix(in srgb, var(--color-success) 16%, white);
        border-color: color-mix(in srgb, var(--color-success) 38%, white);
        color: var(--color-success);
    }

    .request-status-chip.request-chip--danger {
        background: color-mix(in srgb, var(--color-danger) 14%, white);
        border-color: color-mix(in srgb, var(--color-danger) 32%, white);
        color: var(--color-danger);
    }

    .request-status-chip.request-chip--warning {
        background: color-mix(in srgb, var(--color-warning) 22%, white);
        border-color: color-mix(in srgb, var(--color-warning) 40%, white);
        color: #8a6118;
    }

    /* Status dot indicator */
    .request-status-dot {
        display: inline-block;
        width: 0.5rem;
        height: 0.5rem;
        border-radius: 50%;
        flex: none;
        background: currentColor;
        box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.55);
    }

    .request-status-chip.request-chip--success .request-status-dot {
        background: var(--color-success);
    }

    .request-status-chip.request-chip--danger .request-status-dot {
        background: var(--color-danger);
    }

    .request-status-chip.request-chip--warning .request-status-dot {
        background: var(--color-warning);
    }

    /* ---- List (row) view overrides ---- */
    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
    }

    .request-card--list {
        min-height: 0;
        height: auto;
        padding: 0.35rem 0.5rem;
        grid-template-columns: 2rem minmax(0, 1fr) auto;
        gap: 0.45rem;
        align-items: center;
    }

    .request-card--list .request-item__icon {
        width: 2rem;
        height: 2rem;
        border-radius: 0.55rem;
    }

    .request-card--list .request-item__icon .gamified-icon {
        width: 1rem;
        height: 1rem;
    }

    .request-card--list .request-item__content {
        gap: 0.18rem;
    }

    .request-card--list .request-item__title {
        font-size: 0.86rem;
    }

    .request-card--list .request-item__comment {
        display: none;
    }

    .request-card--list .request-item__actions {
        flex-direction: row;
        align-items: center;
        gap: 0.4rem;
    }

    .request-card--list .request-item__amounts {
        flex-direction: row;
        align-items: center;
        gap: 0.35rem;
    }

    .request-card--list .request-item__money {
        display: none;
    }

    .request-card--list .request-item__buttons .btn {
        flex: none;
        width: 2rem;
        height: 2rem;
        padding: 0;
        font-size: 0.88rem;
        display: grid;
        place-items: center;
    }

    .request-card--list .request-item__buttons .history-item__delete-btn {
        width: 2.2rem;
        height: 2.2rem;
    }

    @media (max-width: 640px) {
        .request-card {
            padding: 0.5rem 0.6rem;
            grid-template-columns: 2rem minmax(0, 1fr) auto;
            gap: 0.45rem;
        }

        .request-item__icon {
            width: 2rem;
            height: 2rem;
        }

        .request-item__icon .gamified-icon {
            width: 1rem;
            height: 1rem;
        }

        .request-item__title {
            font-size: 0.86rem;
        }

        .request-item__comment {
            font-size: 0.74rem;
        }

        .request-card--list {
            padding: 0.35rem 0.45rem;
        }

        .request-card--list .request-item__actions {
            gap: 0.25rem;
        }

        .request-card--list .request-item__buttons .btn {
            width: 1.95rem;
            height: 1.95rem;
            font-size: 0.82rem;
        }

        .request-note-popover {
            width: min(16rem, calc(100vw - 1rem));
        }
    }
</style>
