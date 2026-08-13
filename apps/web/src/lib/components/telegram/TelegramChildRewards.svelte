<script lang="ts">
    import { requestItem, requestItemWithNote } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { appStore } from '$lib/stores/app';
    import TelegramGroupedCatalog, { type CatalogItem } from './TelegramGroupedCatalog.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    let selected: CatalogItem | null = null;
    let busy = false;
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = 'idle';
    let message = '';
    $: pendingIds = $appStore.requests.filter((request) => (request.requestType === 'shop_purchase' || request.itemId != null) && request.status === 'pending').map((request) => request.itemId).filter((id): id is string | number => id != null);
    $: items = $appStore.shopItems.filter((item) => item.isActive !== false).map((item): CatalogItem => ({ id: item.id, title: item.name, amount: item.price, group: item.groupName, available: $appStore.balance >= item.price, disabledReason: $appStore.balance < item.price ? 'Not enough coins' : undefined }));
    async function submit(note: string | null) {
        if (!selected || busy) return;
        busy = true; status = 'pending'; message = 'Sending request…';
        const result = note ? await requestItemWithNote(selected.id, note) : await requestItem(selected.id);
        busy = false;
        if (result.ok) { if (result.data && typeof result.data === 'object') applyDataSnapshot(result.data as Record<string, unknown>); status = 'success'; message = 'Reward request sent for approval.'; selected = null; }
        else if (result.errorCode === 'STALE_STATE') { await refreshData(); status = 'stale'; message = 'This reward changed. Your list was refreshed.'; selected = null; }
        else { status = 'error'; message = result.error; selected = null; }
    }
</script>

<section aria-labelledby="child-rewards-title"><div class="heading"><h2 id="child-rewards-title">Rewards</h2><span>Spend your coins</span></div><TelegramGroupedCatalog kind="reward" items={items} {pendingIds} on:request={(event) => { selected = event.detail; status = 'idle'; }} /><TelegramActionStatus state={status} message={message} /></section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} actionLabel="Ask for reward" bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { margin:0; color:#18243d; font-size:1.2rem; } span { color:#66718a; font-size:.8rem; }
</style>
