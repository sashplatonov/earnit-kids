<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import { approveRequest, rejectRequest } from '$lib/services/api';
    import { refreshData } from '$lib/services/bootstrap';
    export let requests: Request[] = [];
    export let canDecide = false;
    export let childId: string | number | null = null;
    export let loading = false;
    export let error = '';
    export let onRetry: () => void = () => {};
    let busy: string | number | null = null;
    async function decide(id: string | number, action: 'approve' | 'reject') {
        busy = id;
        const result = action === 'approve' ? await approveRequest(id, childId) : await rejectRequest(id, childId);
        await refreshData();
        busy = null;
    }
</script>

<section class="panel" aria-labelledby="telegram-requests-title">
    <div class="heading"><h2 id="telegram-requests-title">Requests</h2><button type="button" on:click={onRetry} disabled={loading}>Refresh</button></div>
    {#if loading}<p class="muted" role="status">Loading requests…</p>{:else if error}<p class="error" role="alert">{error}</p><button type="button" on:click={onRetry}>Retry</button>{:else if !requests.length}<p class="muted">No requests yet.</p>{:else}<div class="items">{#each requests as request (request.id)}<article><div><h3>{request.taskName || request.itemName || request.title || 'Request'}</h3><p>{request.coins ?? request.amount ?? 0} 🪙 · <span class:pending={request.status === 'pending'}>{request.status}</span></p></div>{#if canDecide && request.status === 'pending'}<div class="actions"><button class="approve" type="button" disabled={busy === request.id} on:click={() => decide(request.id, 'approve')}>Approve</button><button class="reject" type="button" disabled={busy === request.id} on:click={() => decide(request.id, 'reject')}>Reject</button></div>{/if}</article>{/each}</div>{/if}
</section>

<style>
    .panel { padding:1rem; border:1px solid #dfe4ee; border-radius:1rem; background:#fff; }.heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; } h2 { margin:0 0 1rem; color:#18243d; } .items { display:grid; gap:.55rem; } article { display:flex; align-items:center; justify-content:space-between; gap:.75rem; padding:.75rem; border:1px solid #e5e9f1; border-radius:.75rem; } h3 { margin:0; font-size:1rem; } p { margin:.25rem 0 0; color:#66718a; font-size:.875rem; } button { min-height:2.75rem; padding:.55rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; } .actions { display:flex; gap:.35rem; } .approve { background:#e9f8ed; border-color:#8ed09b; } .reject { background:#fff0f0; border-color:#e0a0a0; } .pending { color:#8a5a00; font-weight:700; } .muted { color:#66718a; } .error { color:#a33b3b; }
</style>
