<script lang="ts">
    import type { HistoryEntry } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import TelegramAsyncState from './ui/TelegramAsyncState.svelte';
    import type { TelegramAsyncState as AsyncState } from './ui/telegramUi';
    import { getTelegramEntityIcon, stripLeadingEmoji, type EntityKind } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';

    const i18n = useI18n();

    export let entries: HistoryEntry[] = [];
    export let loading = false;
    export let hasMore = false;
    export let onLoadMore: () => void = () => {};
    export let onRetry: () => void = () => {};
    export let error = '';
    $: asyncState = (loading && !entries.length ? 'loading' : error ? 'error' : !entries.length ? 'empty' : 'success') as AsyncState;
    function historyKind(type: HistoryEntry['type']): EntityKind {
        if (type === 'purchase' || type === 'spend') return 'reward';
        if (type === 'task_completed' || type === 'earn') return 'task';
        return 'activity';
    }
</script>

<section aria-label={$i18n.t('app.telegram.history.history')}>
    {#if asyncState !== 'success'}<TelegramAsyncState state={asyncState} loadingLabel={$i18n.t('app.telegram.history.loadingActivity')} emptyLabel={$i18n.t('app.telegram.history.noActivity')} errorMessage={error} retryLabel={$i18n.t('app.telegram.history.retry')} onRetry={onRetry} />
    {:else}<TelegramListSurface label={$i18n.t('app.telegram.history.history')}>{#each entries as entry (entry.id)}<TelegramEntityRow>
        <span slot="icon"><TelegramIcon name={getTelegramEntityIcon({ kind: historyKind(entry.type), title: entry.description || entry.title || entry.taskName || entry.itemName || '', group: entry.groupName })} size={20} label={$i18n.t('app.telegram.history.activity')} /></span>
        <h3 slot="title">{stripLeadingEmoji(entry.description || entry.title || entry.taskName || entry.itemName || $i18n.t('app.telegram.history.activity'))}</h3>
        <time slot="metadata" datetime={entry.createdAt ?? undefined}>{entry.createdAt ? formatLastUsedTime(entry.createdAt, $i18n.locale) : $i18n.t('app.telegram.history.recently')}</time>
        <strong slot="trailing" class:spend={entry.amount < 0 || entry.type === 'purchase' || entry.type === 'spend'}><TelegramCoin size={13} />{entry.type === 'purchase' || entry.type === 'spend' ? '-' : (entry.amount > 0 ? '+' : '')}{Math.abs(entry.amount)}</strong>
    </TelegramEntityRow>{/each}</TelegramListSurface>{/if}
    {#if hasMore}<button class="load-more" type="button" on:click={onLoadMore} disabled={loading}><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.history.loadMore')} />{loading ? $i18n.t('app.telegram.history.loading') : $i18n.t('app.telegram.history.loadMore')}</button>{/if}
</section>

<style>
    .load-more { width:100%; min-height:2.75rem; margin-top:.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .load-more:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    strong { display:inline-flex; align-items:center; gap:.25rem; color:#237b3c; white-space:nowrap; }
    strong.spend { color:#a33b3b; }
</style>
