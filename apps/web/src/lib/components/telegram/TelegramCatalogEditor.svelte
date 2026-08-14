<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { approveRequest, rejectRequest } from '$lib/services/api';
    import { refreshData } from '$lib/services/bootstrap';
    let busy: string | number | null = null;
    let error = '';
    async function decide(id: string | number, action: 'approve' | 'reject') {
        busy = id; error = '';
        const result = action === 'approve' ? await approveRequest(id, $appStore.currentChildId) : await rejectRequest(id, $appStore.currentChildId);
        if (result == null) error = 'This request is stale or could not be updated. Refresh and try again.';
        await refreshData(); busy = null;
    }
</script>

<section class="panel" aria-labelledby="requests-title">
    <div class="section-heading"><div><p class="eyebrow">One decision at a time</p><h2 id="requests-title">Requests</h2></div><button type="button" on:click={() => refreshData()}>Refresh</button></div>
    {#if !$appStore.requests.length}<p class="muted">No requests yet.</p>{:else}<div class="items">{#each $appStore.requests as request (request.id)}<article><div><h3>{request.taskName || 'Request'}</h3><p>{request.coins} 🪙 · {request.status}</p></div>{#if request.status === 'pending'}<div class="actions"><button class="approve" disabled={busy === request.id} type="button" on:click={() => decide(request.id, 'approve')}>Approve</button><button class="reject" disabled={busy === request.id} type="button" on:click={() => decide(request.id, 'reject')}>Reject</button></div>{/if}</article>{/each}</div>{/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
</section>

<style>
    .panel { padding:1rem; border:1px solid #dfe4ee; border-radius:1rem; background:#fff; }.section-heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; }.eyebrow { margin:0; color:#66718a; font-size:.75rem; text-transform:uppercase; letter-spacing:.08em; } h2 { margin:.2rem 0 1rem; color:#18243d; }.items { display:grid; gap:.5rem; } article { display:flex; justify-content:space-between; gap:.75rem; align-items:center; padding:.75rem; border:1px solid #e5e9f1; border-radius:.75rem; } h3 { margin:0; font-size:1rem; } p { margin:.25rem 0 0; color:#66718a; font-size:.875rem; } button { min-height:2.75rem; padding:.6rem .75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }.actions { display:flex; gap:.35rem; }.approve { background:#e9f8ed; border-color:#8ed09b; }.reject { background:#fff0f0; border-color:#e0a0a0; }.error { color:#a33b3b; }.muted { color:#66718a; }
</style>
