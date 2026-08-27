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

    interface Props {
        entries?: HistoryEntry[];
        loading?: boolean;
        hasMore?: boolean;
        onLoadMore?: () => void;
        onRetry?: () => void;
        error?: string;
    }

    let {
        entries = [],
        loading = false,
        hasMore = false,
        onLoadMore = () => {},
        onRetry = () => {},
        error = ''
    }: Props = $props();
    let asyncState = $derived((loading && !entries.length ? 'loading' : error ? 'error' : !entries.length ? 'empty' : 'success') as AsyncState);
    function historyTitle(entry: HistoryEntry): string {
        return entry.description || entry.title || entry.taskName || entry.itemName || $i18n.t('app.telegram.history.activity');
    }
    function historyKind(type: HistoryEntry['type']): EntityKind {
        if (type === 'purchase' || type === 'spend') return 'reward';
        if (type === 'task_completed' || type === 'earn') return 'task';
        return 'activity';
    }
</script>

<section aria-label={$i18n.t('app.telegram.history.history')}>
    {#if asyncState !== 'success'}<TelegramAsyncState state={asyncState} loadingLabel={$i18n.t('app.telegram.history.loadingActivity')} emptyLabel={$i18n.t('app.telegram.history.noActivity')} errorMessage={error} retryLabel={$i18n.t('app.telegram.history.retry')} onRetry={onRetry} />
    {:else}<TelegramListSurface label={$i18n.t('app.telegram.history.history')}>{#each entries as entry (entry.id)}<TelegramEntityRow>
        {#snippet icon()}
                                        <span ><TelegramIcon name={getTelegramEntityIcon({ kind: historyKind(entry.type), title: historyTitle(entry), group: entry.groupName })} size={20} label={$i18n.t('app.telegram.history.activity')} /></span>
                                    {/snippet}
        {#snippet title()}
                                        <h3 class="history-title" >{stripLeadingEmoji(historyTitle(entry))}</h3>
                                    {/snippet}
        {#snippet metadata()}
                                        <div ><time class="history-time" datetime={entry.createdAt ?? undefined}>{entry.createdAt ? formatLastUsedTime(entry.createdAt, $i18n.locale) : $i18n.t('app.telegram.history.recently')}</time>{#if entry.groupName}<span class="history-group">{entry.groupName}</span>{/if}</div>
                                    {/snippet}
        {#snippet trailing()}
                                        <strong class="history-amount"  class:spend={entry.amount < 0 || entry.type === 'purchase' || entry.type === 'spend'}><TelegramCoin size={13} />{entry.type === 'purchase' || entry.type === 'spend' ? '-' : (entry.amount > 0 ? '+' : '')}{Math.abs(entry.amount)}</strong>
                                    {/snippet}
    </TelegramEntityRow>{/each}</TelegramListSurface>{/if}
    {#if hasMore}<button class="load-more" type="button" onclick={onLoadMore} disabled={loading}><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.history.loadMore')} />{loading ? $i18n.t('app.telegram.history.loading') : $i18n.t('app.telegram.history.loadMore')}</button>{/if}
</section>

<style>
    .load-more { width:100%; min-height:2.75rem; margin-top:.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .load-more:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    :global(.history-title) { display:-webkit-box; overflow:hidden; min-width:0; max-block-size:2.4em; overflow-wrap:anywhere; -webkit-box-orient:vertical; -webkit-line-clamp:2; line-clamp:2; color:#18243d; font-size:.85rem; font-weight:700; line-height:1.2; }
    :global(.history-time) { display:block; }
    :global(.history-group) { display:flex; align-items:center; gap:.25rem; color:#66718a; }
    :global(.history-amount) { display:inline-flex; align-items:center; justify-content:flex-end; gap:.25rem; min-width:3.2rem; color:#237b3c; white-space:nowrap; }
    :global(.history-amount.spend) { color:#a33b3b; }
</style>
