<script lang="ts">
    import { onMount, onDestroy } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { approveRequest, rejectRequest, deleteRequest } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';
    import type { Request } from '$lib/stores/app';
    import { buildRequestCatalog, resolveRequestCard, type RequestDetailsI18n } from './requestDetails';

    const i18n = useI18n();

    $: requests = $appStore.requests;
    $: isAdmin = $appStore.isAdmin;

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

    // Polling every 8 s — both admin (new incoming) and child (status updates).
    // WS refresh already triggers refreshData on 'update' events; this is the fallback.
    let pollTimer: ReturnType<typeof setInterval> | null = null;

    onMount(() => {
        pollTimer = setInterval(() => { void refreshData(); }, 8_000);
        return () => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } };
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
        </div>

        {#if incomingRequests.length > 0}
        <div id="incoming-requests-list" class="history-list request-list">
            {#each incomingRequests as req (req.id)}
            <article
                class="history-item request-item"
                class:history-item--request-purchase={req.ui.isPurchase}
                class:history-item--request-task={!req.ui.isPurchase}>
                <div class="history-item__icon">
                    <span class={`gamified-icon ${req.ui.iconClass}`} aria-hidden="true"></span>
                </div>
                <div class="history-item__body history-item__content">
                    <p class="history-item__title history-item__desc">{req.ui.title}</p>
                    <div class="request-item__chips">
                        <span class={`request-chip ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                        {#if req.childNickname}
                        <span class="request-chip request-chip--child request-item__child">{req.childNickname}</span>
                        {/if}
                        <span class="request-chip request-chip--group request-item__group">{req.ui.group}</span>
                        {#if formatDate(requestCreatedAt(req))}
                        <span class="request-chip request-chip--muted request-item__date history-item__meta">{formatDate(requestCreatedAt(req))}</span>
                        {/if}
                    </div>
                    {#if req.ui.description}
                    <p class="history-item__note request-item__comment">{req.ui.description}</p>
                    {/if}
                </div>
                <div class="history-item__actions request-item__actions">
                    <span class={`history-item__amount ${req.ui.isPurchase ? 'history-item__amount--spend' : 'history-item__amount--earn'}`}>
                        <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width:1em;height:1em;"></span>
                        {req.ui.amountPrefix}{req.ui.coins}
                    </span>
                    {#if hasMoneyAmount(req.ui.moneyAmount)}
                    <span class="history-item__money request-item__money">{req.ui.moneyAmount} 💶</span>
                    {/if}
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
        </div>
        {#if myRequests.length > 0}
        <div class="history-list request-list" id="my-requests-list">
            {#each myRequests as req (req.id)}
            <article
                class="history-item request-item"
                class:history-item--request-purchase={req.ui.isPurchase}
                class:history-item--request-task={!req.ui.isPurchase}>
                <div class="history-item__icon">
                    <span class={`gamified-icon ${req.ui.iconClass}`} aria-hidden="true"></span>
                </div>
                <div class="history-item__body history-item__content">
                    <p class="history-item__title history-item__desc">{req.ui.title}</p>
                    <div class="request-item__chips">
                        <span class={`request-chip ${req.ui.typeChipClass}`}>{req.ui.typeLabel}</span>
                        <span class={`request-chip request-chip--status ${requestStatusClass(req.status)}`}>{requestStatusLabel(req.status)}</span>
                        <span class="request-chip request-chip--group request-item__group">{req.ui.group}</span>
                        {#if formatDate(requestCreatedAt(req))}
                        <span class="request-chip request-chip--muted request-item__date history-item__meta">{formatDate(requestCreatedAt(req))}</span>
                        {/if}
                    </div>
                    {#if req.ui.description}
                    <p class="history-item__note request-item__comment">{req.ui.description}</p>
                    {/if}
                </div>
                <div class="history-item__actions request-item__actions">
                    <span class={`history-item__amount ${req.ui.isPurchase ? 'history-item__amount--spend' : 'history-item__amount--earn'}`}>
                        <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width:1em;height:1em;"></span>
                        {req.ui.amountPrefix}{req.ui.coins}
                    </span>
                    {#if hasMoneyAmount(req.ui.moneyAmount)}
                    <span class="history-item__money request-item__money">{req.ui.moneyAmount} 💶</span>
                    {/if}
                    {#if req.status === 'rejected'}
                    <div class="request-item__buttons">
                        <button class="history-item__delete-btn" on:click={() => handleDelete(req.id)} aria-label={tHistory('requests.deleteAria')}>✕</button>
                    </div>
                    {/if}
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
