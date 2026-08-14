<script context="module" lang="ts">
    export type CatalogItem = { id: number | string; title: string; amount: number; group?: string | null; available?: boolean; disabledReason?: string };
</script>
<script lang="ts">
    import { createEventDispatcher } from 'svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    export let items: CatalogItem[] = [];
    export let kind: 'task' | 'reward' = 'task';
    export let pendingIds: Array<number | string> = [];
    const dispatch = createEventDispatcher<{ request: CatalogItem }>();
    $: groups = [...new Set(items.map((item) => item.group?.trim()).filter((group): group is string => Boolean(group)))];
    $: grouped = groups.length > 1;
    const isPending = (id: number | string) => pendingIds.some((pendingId) => String(pendingId) === String(id));
</script>

{#if !items.length}
    <p class="empty">No {kind === 'task' ? 'tasks' : 'rewards'} available right now.</p>
{:else if !grouped}
    <div class="catalog" aria-label={kind === 'task' ? 'Available tasks' : 'Available rewards'}>
        {#each items as item (item.id)}
            <article><div><h3>{item.title}</h3><p>{kind === 'task' ? '+' : '−'}{item.amount} 🪙</p></div><button type="button" aria-label={isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'} disabled={!item.available || isPending(item.id)} on:click={() => dispatch('request', item)}><TelegramIcon name={isPending(item.id) ? 'refresh' : kind === 'task' ? 'done' : 'requestReward'} size={18} label={isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'} /><span>{isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'}</span></button></article>
        {/each}
    </div>
{:else}
    <div class="groups" aria-label={kind === 'task' ? 'Grouped tasks' : 'Grouped rewards'}>
        {#each groups as group (group)}
            <details open><summary>{group}</summary><div class="catalog">{#each items.filter((item) => item.group?.trim() === group) as item (item.id)}<article><div><h3>{item.title}</h3><p>{kind === 'task' ? '+' : '−'}{item.amount} 🪙</p></div><button type="button" aria-label={isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'} disabled={!item.available || isPending(item.id)} on:click={() => dispatch('request', item)}><TelegramIcon name={isPending(item.id) ? 'refresh' : kind === 'task' ? 'done' : 'requestReward'} size={18} label={isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'} /><span>{isPending(item.id) ? 'Pending' : item.available ? kind === 'task' ? 'Done' : 'Get reward' : item.disabledReason ?? 'Unavailable'}</span></button></article>{/each}</div></details>
        {/each}
    </div>
{/if}

<style>
    .catalog { display:grid; gap:.6rem; } article { display:flex; align-items:center; justify-content:space-between; gap:.75rem; width:100%; padding:.8rem; border:1px solid #e1e6ef; border-radius:.8rem; background:#fff; } h3 { margin:0; color:#18243d; font-size:1rem; } p { margin:.25rem 0 0; color:#66718a; font-size:.875rem; } button { min-width:5.5rem; min-height:2.75rem; padding:.55rem .65rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; } button:disabled { border-color:#cfd6e4; background:#f1f3f7; color:#66718a; } .empty { padding:1rem 0; color:#66718a; text-align:center; } details { margin-bottom:.85rem; border:0; border-radius:0; background:transparent; overflow:visible; } summary { min-height:2.5rem; box-sizing:border-box; padding:.65rem 0; border-bottom:1px solid #dfe4ee; color:#18243d; font-weight:700; cursor:pointer; } details .catalog { padding:0; }
</style>
