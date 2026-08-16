<script lang="ts">
    import { browser } from '$app/environment';
    import { onMount, onDestroy, tick } from 'svelte';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
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

    function requestCompactChips(req: Request & {
        ui: {
            group: string;
            typeLabel: string;
            isPurchase: boolean;
        };
    }) {
        // NOTE: In list (row) view on mobile we only show the 1st chip (group) and the 2nd chip.
        // We want the child's name to always be visible and placed near the beginning, so we
        // inject it as the 2nd chip when available.
        const chips: Array<{ label: string; className?: string }> = [
            { label: req.ui.group, className: 'card__compact-chip--group' },
        ];

        const childName = resolveChildName(req);
        if (childName) chips.push({ label: childName, className: 'card__compact-chip--child' });

        chips.push({ label: req.ui.typeLabel });

        if (!isAdmin) {
            chips.push({
                label: requestStatusLabel(req.status),
                className: req.status === 'approved'
                    ? 'card__compact-chip--status-available'
                    : req.status === 'rejected'
                        ? 'card__compact-chip--status-locked'
                        : '',
            });
        }

        return chips;
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
            <article
                class="card request-card"
                class:request-card--list={viewMode === 'list'}
                class:request-card--purchase={req.ui.isPurchase}
                class:request-card--task={!req.ui.isPurchase}>
                <div class="card__badge-row">
                    {#if childName}
                    <span class="card__badge request-chip--child">{childName}</span>
                    {/if}
                    <span class={`card__badge ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                    <span class="card__badge card__badge--group">{req.ui.group}</span>
                </div>
                <div class="request-card__layout">
                    <div class="request-card__main">
                        <CardHeader
                            title={req.ui.title}
                            amount={formatRequestAmount(req)}
                            amountClass={req.ui.isPurchase ? 'item-coins' : 'task-coins'}
                            amountNote={requestMoneyLabel(req.ui.moneyAmount)}
                            compactChips={requestCompactChips(req)}
                            titleActionAria={req.ui.note ? tHistory('requests.noteButtonAria') : ''}
                            titleActionExpanded={openNoteRequestId === String(req.id)}
                            titleActionControls={req.ui.note ? requestNoteId(req.id) : ''}
                            onTitleAction={req.ui.note ? ((event?: MouseEvent) => toggleNote(req.id, event)) : null}
                        />
                        {#if req.ui.description}
                        <p class="card__comment">{req.ui.description}</p>
                        {/if}
                    </div>
                    <div class="request-card__side">
                        <div class="card__meta">
                        {#if childName}
                            <span class="card__meta-item">{childName}</span>
                        {/if}
                        {#if formatDate(requestCreatedAt(req))}
                            <span class="card__meta-item">{formatDate(requestCreatedAt(req))}</span>
                        {/if}
                        </div>
                        {#if requestMoneyLabel(req.ui.moneyAmount)}
                        <span class="request-card__money-price">{requestMoneyLabel(req.ui.moneyAmount)}</span>
                        {/if}
                        <div class="card__actions request-card__actions">
                            <button class="btn btn--success btn--small" aria-label={tHistory('requests.approveAria')} on:click={() => handleApprove(req)}>✓</button>
                            <button class="btn btn--danger btn--small" aria-label={tHistory('requests.rejectAria')} on:click={() => handleReject(req)}>✗</button>
                        </div>
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
            {@const childName = resolveChildName(req)}
            <article
                class="card request-card"
                class:request-card--list={viewMode === 'list'}
                class:request-card--purchase={req.ui.isPurchase}
                class:request-card--task={!req.ui.isPurchase}>
                <div class="card__badge-row">
                    <span class={`card__badge request-chip--status ${requestStatusClass(req.status)}`}>
                        <span class="request-status-dot" aria-hidden="true"></span>
                        {requestStatusLabel(req.status)}
                    </span>
                    <span class={`card__badge ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                    <span class="card__badge card__badge--group">{req.ui.group}</span>
                </div>
                <div class="request-card__layout">
                    <div class="request-card__main">
                        <CardHeader
                            title={req.ui.title}
                            amount={formatRequestAmount(req)}
                            amountClass={req.ui.isPurchase ? 'item-coins' : 'task-coins'}
                            amountNote={requestMoneyLabel(req.ui.moneyAmount)}
                            compactChips={requestCompactChips(req)}
                            titleActionAria={req.ui.note ? tHistory('requests.noteButtonAria') : ''}
                            titleActionExpanded={openNoteRequestId === String(req.id)}
                            titleActionControls={req.ui.note ? requestNoteId(req.id) : ''}
                            onTitleAction={req.ui.note ? ((event?: MouseEvent) => toggleNote(req.id, event)) : null}
                        />
                        {#if req.ui.description}
                        <p class="card__comment">{req.ui.description}</p>
                        {/if}
                    </div>
                    <div class="request-card__side">
                        {#if formatDate(requestCreatedAt(req))}
                            <span class="request-card__date">{formatDate(requestCreatedAt(req))}</span>
                        {/if}
                        {#if req.status !== 'approved'}
                        <div class="card__actions request-card__actions">
                            <button class="history-item__delete-btn" on:click={() => handleDelete(req.id)} aria-label={tHistory('requests.deleteAria')}>✕</button>
                        </div>
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
    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
    }

    .request-card {
        min-height: 0;
        height: auto;
        overflow: visible;
        padding: 0.75rem 0.85rem;
    }

    .request-card--purchase .card__coins {
        background: linear-gradient(135deg, rgba(239, 68, 68, 0.9), rgba(190, 70, 52, 0.9));
        color: white;
    }

    .request-card--task .card__coins {
        background: var(--gradient-success);
        color: white;
    }

    .request-card__layout {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 0.65rem;
        align-items: start;
    }

    .request-card__main {
        display: flex;
        flex-direction: column;
        gap: 0.45rem;
        min-width: 0;
    }

    .request-card__side {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.4rem;
        flex-shrink: 0;
    }

    .request-card__date {
        font-size: 0.72rem;
        color: #64748b;
        font-weight: 600;
        white-space: nowrap;
    }

    .request-card__money-price {
        display: inline-flex;
        align-items: center;
        width: fit-content;
        padding: 0.18rem 0.46rem;
        border-radius: 999px;
        background: rgba(245, 158, 11, 0.12);
        color: #8a6118;
        font-size: 0.76rem;
        font-weight: 800;
        line-height: 1;
        white-space: nowrap;
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

    .request-card--list {
        min-height: 0;
        height: auto;
        padding: 0.35rem 0.5rem;
    }

    .request-card--list .card__badge-row,
    .request-card--list .card__comment,
    .request-card--list .card__meta {
        display: none;
    }

    .request-card--list .request-card__layout {
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 0.4rem;
        align-items: center;
    }

    .request-card--list .request-card__main {
        min-width: 0;
    }

    .request-card--list .request-card__side {
        flex-direction: column;
        align-items: flex-end;
        gap: 0.3rem;
        margin-top: 0;
    }

    .request-card--list .request-card__date {
        font-size: 0.68rem;
    }

    .request-card--list .request-card__money-price {
        display: none;
    }

    .request-card--list .request-card__actions {
        display: flex;
        flex-direction: row;
        gap: 0.3rem;
        justify-content: flex-end;
        margin-top: 0;
        align-items: center;
    }

    .request-card--list .request-card__actions .btn {
        flex: none;
        width: 2rem;
        height: 2rem;
        padding: 0;
        font-size: 0.88rem;
        display: grid;
        place-items: center;
    }

    /* Bright child name badge for requests */
    .request-chip--child {
        background: linear-gradient(135deg, rgba(99, 102, 241, 0.22), rgba(168, 85, 247, 0.22));
        color: #2d1b5a;
        border: 1px solid rgba(99, 102, 241, 0.18);
        font-weight: 900;
    }

    /* Status dot indicator for child request status chips */
    .request-status-dot {
        display: inline-block;
        width: 0.5rem;
        height: 0.5rem;
        border-radius: 50%;
        margin-right: 0.3rem;
        vertical-align: middle;
        background: currentColor;
        box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.55);
    }

    .request-chip--success .request-status-dot {
        background: #22c55e;
    }

    .request-chip--danger .request-status-dot {
        background: #ef4444;
    }

    .request-chip--warning .request-status-dot {
        background: #f59e0b;
    }

    .request-card--list .history-item__delete-btn {
        width: 2.2rem;
        height: 2.2rem;
    }

    @media (max-width: 640px) {
        .request-card {
            min-height: 0;
            padding: 0.6rem 0.65rem;
        }

        .request-card--list {
            padding: 0.35rem 0.45rem;
        }

        .request-card--list .request-card__layout {
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 0.4rem;
            align-items: center;
        }

        .request-card--list .request-card__side {
            gap: 0.25rem;
        }

        .request-card--list .request-card__date {
            font-size: 0.65rem;
        }

        .request-card--list .request-card__actions .btn {
            width: 1.95rem;
            height: 1.95rem;
            font-size: 0.82rem;
        }

        .request-note-popover {
            width: min(16rem, calc(100vw - 1rem));
        }

    }
</style>
