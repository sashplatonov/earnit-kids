<script lang="ts">
    import type { HistoryEntry } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji, type EntityKind } from './telegramEntityIcons';

    const i18n = useI18n();

    export let entries: HistoryEntry[] = [];
    export let loading = false;
    export let hasMore = false;
    export let onLoadMore: () => void = () => {};
    export let onRetry: () => void = () => {};
    export let error = '';
    function historyKind(type: HistoryEntry['type']): EntityKind {
        if (type === 'purchase' || type === 'spend') return 'reward';
        if (type === 'task_completed' || type === 'earn') return 'task';
        return 'activity';
    }
</script>

<section class="panel" aria-labelledby="telegram-history-title"><div class="heading"><h2 id="telegram-history-title">{$i18n.t('app.telegram.history.recentActivity')}</h2></div>{#if loading && !entries.length}<p class="muted" role="status">{$i18n.t('app.telegram.history.loadingActivity')}</p>{:else if error}<div class="state-error" role="alert"><TelegramIcon name="alert" size={18} label={$i18n.t('app.telegram.home.error')} /><p>{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label={$i18n.t('app.telegram.history.retry')} />{$i18n.t('app.telegram.history.retry')}</button></div>{:else if !entries.length}<p class="muted">{$i18n.t('app.telegram.history.noActivity')}</p>{:else}<div class="items">{#each entries as entry (entry.id)}<article><div class="entity-main"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: historyKind(entry.type), title: entry.description || entry.title || entry.taskName || entry.itemName || '', group: entry.groupName })} size={20} label={$i18n.t('app.telegram.history.activity')} /></span><div class="entity-text"><h3>{stripLeadingEmoji(entry.description || entry.title || entry.taskName || entry.itemName || $i18n.t('app.telegram.history.activity'))}</h3><time datetime={entry.createdAt ?? undefined}>{entry.createdAt ? new Date(entry.createdAt).toLocaleDateString() : $i18n.t('app.telegram.history.recently')}</time></div></div><strong class:spend={entry.amount < 0}><TelegramCoin size={13} />{entry.amount > 0 ? '+' : ''}{entry.amount}</strong></article>{/each}</div>{/if}{#if hasMore}<button class="load-more" type="button" on:click={onLoadMore} disabled={loading}><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.history.loadMore')} />{loading ? $i18n.t('app.telegram.history.loading') : $i18n.t('app.telegram.history.loadMore')}</button>{/if}</section>

<style>
    .panel { width:100%; }.heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; } h2 { margin:0 0 .65rem; color:#18243d; } .items { display:grid; gap:.55rem; } article { display:flex; align-items:center; justify-content:space-between; gap:.75rem; width:100%; padding:.75rem 0; border-bottom:1px solid #edf0f5; } article:last-child { border-bottom:0; } .entity-main { display:flex; align-items:center; gap:.6rem; min-width:0; } .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } .entity-text { min-width:0; } h3 { margin:0; font-size:.95rem; } time { color:#66718a; font-size:.8rem; } strong { display:inline-flex; align-items:center; gap:.25rem; color:#237b3c; white-space:nowrap; } strong.spend { color:#a33b3b; } .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#fff0f0; color:#a33b3b; } .state-error p { margin:0; flex:1; font-size:.9rem; } .state-error button { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; } .load-more { width:100%; min-height:2.75rem; margin-top:.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; } .muted { color:#66718a; }
</style>
