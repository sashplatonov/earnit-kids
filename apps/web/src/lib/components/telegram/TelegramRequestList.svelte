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
</script>

<section class="panel" aria-labelledby="telegram-requests-title">
    <div class="heading"><h2 id="telegram-requests-title">Requests</h2></div>
    {#if loading}<p class="muted" role="status">Loading requests…</p>{:else if error}<p class="error" role="alert">{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label="Retry" />Retry</button>{:else if !requests.length}<p class="muted">No requests yet.</p>{:else}<div class="items">{#each requests as request (request.id)}<article><div class="entity-main"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: request.requestType === 'shop_purchase' ? 'reward' : 'task', title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName })} size={20} label="Request" /></span><div class="entity-text"><h3>{stripLeadingEmoji(request.taskName || request.itemName || request.title || 'Request')}</h3><p><TelegramCoin size={13} />{request.coins ?? request.amount ?? 0} · <span class:pending={request.status === 'pending'}>{request.status}</span></p></div></div>{#if canDecide && request.status === 'pending'}<div class="actions"><button class="approve" type="button" aria-label="Approve request" disabled={busy === request.id} on:click={() => decide(request.id, 'approve')}><TelegramIcon name="approve" size={18} label="Approve" /></button><button class="reject" type="button" aria-label="Reject request" disabled={busy === request.id} on:click={() => decide(request.id, 'reject')}><TelegramIcon name="reject" size={18} label="Reject" /></button></div>{/if}</article>{/each}</div>{/if}
    {#if decisionError}<p class="error" role="alert">{decisionError}</p>{/if}
</section>

<style>
    .panel { width:100%; }.heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; } h2 { margin:0 0 .65rem; color:#18243d; } .items { display:grid; gap:.55rem; } article { display:flex; align-items:center; justify-content:space-between; gap:.75rem; width:100%; padding:.75rem; border:1px solid #e5e9f1; border-radius:.75rem; background:#fff; } .entity-main { display:flex; align-items:center; gap:.6rem; min-width:0; } .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } .entity-text { min-width:0; } h3 { margin:0; font-size:1rem; } p { display:flex; align-items:center; gap:.3rem; margin:.25rem 0 0; color:#66718a; font-size:.875rem; } button { min-height:2.75rem; padding:.55rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; } .actions { display:flex; gap:.35rem; } .approve { background:#e9f8ed; border-color:#8ed09b; } .reject { background:#fff0f0; border-color:#e0a0a0; } .pending { color:#8a5a00; font-weight:700; } .muted { color:#66718a; } .error { color:#a33b3b; }
</style>
