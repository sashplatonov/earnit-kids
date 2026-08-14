<script lang="ts">
    import type { HistoryEntry } from '$lib/stores/app';
    import TelegramIcon from './TelegramIcon.svelte';
    export let entries: HistoryEntry[] = [];
    export let loading = false;
    export let hasMore = false;
    export let onLoadMore: () => void = () => {};
    export let onRetry: () => void = () => {};
    export let error = '';
</script>

<section class="panel" aria-labelledby="telegram-history-title"><div class="heading"><h2 id="telegram-history-title">Recent activity</h2><button type="button" aria-label="Refresh activity" on:click={onRetry} disabled={loading}><TelegramIcon name="refresh" size={18} label="Refresh activity" /></button></div>{#if loading && !entries.length}<p class="muted" role="status">Loading activity…</p>{:else if error}<p class="error" role="alert">{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label="Retry" />Retry</button>{:else if !entries.length}<p class="muted">No activity yet.</p>{:else}<div class="items">{#each entries as entry (entry.id)}<article><div><h3>{entry.description || entry.title || entry.taskName || entry.itemName || 'Activity'}</h3><time datetime={entry.createdAt ?? undefined}>{entry.createdAt ? new Date(entry.createdAt).toLocaleDateString() : 'Recently'}</time></div><strong class:spend={entry.amount < 0}>{entry.amount > 0 ? '+' : ''}{entry.amount} 🪙</strong></article>{/each}</div>{/if}{#if hasMore}<button class="load-more" type="button" on:click={onLoadMore} disabled={loading}><TelegramIcon name="arrowRight" size={18} label="Load more" />{loading ? 'Loading…' : 'Load more'}</button>{/if}</section>

<style>
    .panel { width:100%; }.heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; } h2 { margin:0 0 .65rem; color:#18243d; } .items { display:grid; gap:.55rem; } article { display:flex; align-items:center; justify-content:space-between; gap:.75rem; width:100%; padding:.75rem 0; border-bottom:1px solid #edf0f5; } article:last-child { border-bottom:0; } h3 { margin:0; font-size:.95rem; } time { color:#66718a; font-size:.8rem; } strong { color:#237b3c; white-space:nowrap; } strong.spend { color:#a33b3b; } button { min-height:2.75rem; padding:.55rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; } .load-more { width:100%; margin-top:.75rem; } .muted { color:#66718a; } .error { color:#a33b3b; }
</style>
