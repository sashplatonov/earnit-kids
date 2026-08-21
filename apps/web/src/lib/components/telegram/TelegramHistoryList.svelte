<script lang="ts">
    import type { HistoryEntry } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
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
    let selectedGroup = '';
    $: asyncState = (loading && !entries.length ? 'loading' : error ? 'error' : !entries.length ? 'empty' : 'success') as AsyncState;
    $: groups = [...new Set(entries.map((entry) => entry.groupName?.trim()).filter((group): group is string => Boolean(group)))];
    $: filteredEntries = selectedGroup ? entries.filter((entry) => entry.groupName?.trim() === selectedGroup) : entries;
    function historyKind(type: HistoryEntry['type']): EntityKind {
        if (type === 'purchase' || type === 'spend') return 'reward';
        if (type === 'task_completed' || type === 'earn') return 'task';
        return 'activity';
    }
</script>

<section aria-label={$i18n.t('app.telegram.history.history')}>
    {#if asyncState !== 'success'}<TelegramAsyncState state={asyncState} loadingLabel={$i18n.t('app.telegram.history.loadingActivity')} emptyLabel={$i18n.t('app.telegram.history.noActivity')} errorMessage={error} retryLabel={$i18n.t('app.telegram.history.retry')} onRetry={onRetry} />
    {:else}<TelegramGroupSubnav groups={groups} selected={selectedGroup} kind="history" allLabel={$i18n.t('app.telegram.groupSubnav.all')} moreLabel={$i18n.t('app.telegram.groupSubnav.more')} allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')} onSelect={(group) => selectedGroup = group} />
        {#if selectedGroup && !filteredEntries.length}<p class="empty">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>
        {:else}<TelegramListSurface label={$i18n.t('app.telegram.history.history')}>{#each filteredEntries as entry (entry.id)}<TelegramEntityRow>
        <span slot="icon"><TelegramIcon name={getTelegramEntityIcon({ kind: historyKind(entry.type), title: entry.description || entry.title || entry.taskName || entry.itemName || '', group: entry.groupName })} size={20} label={$i18n.t('app.telegram.history.activity')} /></span>
        <h3 class="history-title" slot="title">{stripLeadingEmoji(entry.description || entry.title || entry.taskName || entry.itemName || $i18n.t('app.telegram.history.activity'))}</h3>
        <div slot="metadata"><time class="history-time" datetime={entry.createdAt ?? undefined}>{entry.createdAt ? formatLastUsedTime(entry.createdAt, $i18n.locale) : $i18n.t('app.telegram.history.recently')}</time>{#if entry.groupName}<span class="history-group">{stripLeadingEmoji(entry.groupName)}</span>{/if}</div>
        <strong class="history-amount" slot="trailing" class:spend={entry.amount < 0 || entry.type === 'purchase' || entry.type === 'spend'}><TelegramCoin size={13} />{entry.type === 'purchase' || entry.type === 'spend' ? '-' : (entry.amount > 0 ? '+' : '')}{Math.abs(entry.amount)}</strong>
    </TelegramEntityRow>{/each}</TelegramListSurface>{/if}{/if}
    {#if hasMore}<button class="load-more" type="button" on:click={onLoadMore} disabled={loading}><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.history.loadMore')} />{loading ? $i18n.t('app.telegram.history.loading') : $i18n.t('app.telegram.history.loadMore')}</button>{/if}
</section>

<style>
    .empty { padding:1rem 0; color:#66718a; text-align:center; }
    .load-more { width:100%; min-height:2.75rem; margin-top:.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .load-more:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    :global(.history-title) { display:-webkit-box; overflow:hidden; min-width:0; max-block-size:2.4em; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:2; line-clamp:2; color:#18243d; font-size:.85rem; font-weight:700; line-height:1.2; }
    :global(.history-time) { display:block; }
    :global(.history-group) { display:block; color:#66718a; }
    :global(.history-amount) { display:inline-flex; align-items:center; justify-content:flex-end; gap:.25rem; min-width:3.2rem; color:#237b3c; white-space:nowrap; }
    :global(.history-amount.spend) { color:#a33b3b; }
</style>
