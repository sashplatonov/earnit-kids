<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import { approveRequest, rejectRequest } from '$lib/services/api';
    import { refreshData } from '$lib/services/bootstrap';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    export let requests: Request[] = [];
    export let canDecide = false;
    export let childId: string | number | null = null;
    export let loading = false;
    export let error = '';
    export let onRetry: () => void = () => {};
    export let showHeading = true;
    export let headingText = 'Requests';
    export let emptyText = 'No requests yet.';
    let busy: string | number | null = null;
    let decisionError = '';
    async function decide(id: string | number, action: 'approve' | 'reject') {
        busy = id;
        decisionError = '';
        const result = action === 'approve' ? await approveRequest(id, childId) : await rejectRequest(id, childId);
        if (result == null) decisionError = 'This request could not be updated. Try again.';
        const refreshed = await refreshData();
        if (!refreshed && result != null) decisionError = 'The decision was saved, but the latest requests could not be loaded.';
        busy = null;
    }
    function requestKind(request: Request): 'task' | 'reward' {
        return request.requestType === 'shop_purchase' ? 'reward' : 'task';
    }
    function requestMeta(request: Request): string {
        const who = request.childNickname || 'Child';
        const what = request.requestType === 'shop_purchase' ? 'Reward request' : 'Task request';
        return `${who} · ${what}`;
    }
</script>

<section class="panel" aria-labelledby="telegram-requests-title">
    {#if showHeading}<div class="heading"><h2 id="telegram-requests-title">{headingText}</h2></div>{/if}
    {#if loading}<p class="muted" role="status">Loading requests…</p>
    {:else if error}<div class="state-error" role="alert"><TelegramIcon name="alert" size={18} label="Error" /><p>{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label="Retry" />Retry</button></div>
    {:else if !requests.length}<div class="state-empty"><TelegramIcon name="checkCircle" size={18} label="All clear" /><span>{emptyText}</span></div>
    {:else}<div class="items">{#each requests as request (request.id)}<article class:decision={canDecide && request.status === 'pending'}><div class="request-top"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: requestKind(request), title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName })} size={20} label="Request" /></span><div class="entity-text"><h3>{stripLeadingEmoji(request.taskName || request.itemName || request.title || 'Request')}</h3><p class="meta">{requestMeta(request)}</p><p class="amount"><TelegramCoin size={13} />+{request.coins ?? request.amount ?? 0}</p></div></div>{#if canDecide && request.status === 'pending'}<div class="decision-actions"><button class="approve" type="button" aria-label="Approve request" disabled={busy === request.id} on:click={() => decide(request.id, 'approve')}><TelegramIcon name="approve" size={18} label="Approve" /><span>Approve</span></button><button class="reject" type="button" aria-label="Reject request" disabled={busy === request.id} on:click={() => decide(request.id, 'reject')}><TelegramIcon name="reject" size={18} label="Reject" /><span>Reject</span></button></div>{/if}</article>{/each}</div>{/if}
    {#if decisionError}<p class="error" role="alert">{decisionError}</p>{/if}
</section>

<style>
    .panel { width:100%; }
    .heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; }
    h2 { margin:0 0 .65rem; color:#18243d; }
    .items { display:grid; gap:.6rem; }
    article { padding:.75rem; border:1px solid #e5e9f1; border-radius:.9rem; background:#fff; }
    article.decision { border-color:#dfe4ee; box-shadow:0 1px 3px rgb(24 36 61 / 6%); }
    .request-top { display:flex; align-items:flex-start; gap:.6rem; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; }
    h3 { margin:0; font-size:.95rem; line-height:1.3; }
    .meta { margin:.15rem 0 0; color:#66718a; font-size:.8rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:.25rem 0 0; color:#18243d; font-weight:750; font-size:.85rem; }
    .decision-actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.6rem; }
    .decision-actions button { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; min-height:2.75rem; border-radius:.7rem; font:inherit; font-weight:700; cursor:pointer; }
    .decision-actions button:disabled { cursor:wait; opacity:.6; }
    .approve { border:1px solid #cce9d8; background:#eaf7ef; color:#17884b; }
    .reject { border:1px solid #f3cfd2; background:#fff0f1; color:#c63c42; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .state-empty { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#eaf7ef; color:#275d3b; font-size:.9rem; }
    .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#fff0f0; color:#a33b3b; }
    .state-error p { margin:0; flex:1; }
    .state-error button { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
</style>
