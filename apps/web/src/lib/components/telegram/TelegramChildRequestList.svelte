<script lang="ts">
    import { appStore, type Request } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestRow from './TelegramRequestRow.svelte';
    import { presentRequest, sortRequestPresentations } from './telegramRequestPresentation';

    const i18n = useI18n();

    export let loading = false;
    export let error = '';
    export let cancelError = '';
    export let onRetry: () => void = () => {};
    export let cancellingIds: Array<string | number> = [];
    export let onCancel: (request: Request) => void = () => {};

    const translator = {
        kindLabel: (kind: 'task' | 'reward') => kind === 'reward'
            ? $i18n.t('app.telegram.childRequests.rewardRequest')
            : $i18n.t('app.telegram.childRequests.taskRequest'),
        statusLabel: (status: 'pending' | 'approved' | 'rejected' | 'cancelled' | 'unknown') => status === 'approved'
            ? $i18n.t('app.telegram.childRequests.statusApproved')
            : status === 'rejected'
                ? $i18n.t('app.telegram.childRequests.statusRejected')
                : status === 'cancelled'
                    ? $i18n.t('app.telegram.childRequests.statusCancelled')
                    : $i18n.t('app.telegram.childRequests.statusPending'),
        metadata: (request: Request, _kind: 'task' | 'reward', kindLabel: string) => {
            const group = request.taskGroup || request.itemGroup || request.groupName;
            return group ? `${group} · ${kindLabel}` : kindLabel;
        },
    };

    function isCancelling(request: Request): boolean {
        return cancellingIds.some((id) => String(id) === String(request.id));
    }

    $: sortedRequests = sortRequestPresentations($appStore.requests.map((request) => presentRequest(request, translator)));

    const PAGE_SIZE = 10;
    let visibleCount = PAGE_SIZE;
    $: visibleRequests = sortedRequests.slice(0, visibleCount);
    $: hasMore = visibleCount < sortedRequests.length;
    function showMore() {
        visibleCount += PAGE_SIZE;
    }
</script>

<section class="panel" aria-label={$i18n.t('app.telegram.childRequests.title')}>
    {#if loading && !sortedRequests.length}
        <p class="muted" role="status">{$i18n.t('app.telegram.childRequests.loading')}</p>
    {:else if error}
        <div class="state-error" role="alert"><TelegramIcon name="alert" size={18} label={$i18n.t('app.telegram.home.error')} /><p>{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label={$i18n.t('app.telegram.childRequests.retry')} />{$i18n.t('app.telegram.childRequests.retry')}</button></div>
    {:else if !sortedRequests.length}
        <div class="state-empty"><TelegramIcon name="checkCircle" size={18} label={$i18n.t('app.telegram.childRequests.title')} /><span>{$i18n.t('app.telegram.childRequests.empty')}</span></div>
    {:else}
        <div class="items">{#each visibleRequests as presentation (presentation.request.id)}<TelegramRequestRow {presentation} locale={$i18n.locale}>
            <button class="cancel" type="button" aria-label={$i18n.t('app.telegram.childRequests.cancelAria')} disabled={isCancelling(presentation.request)} on:click={() => onCancel(presentation.request)}><TelegramIcon name="delete" size={18} label={$i18n.t('app.telegram.childRequests.cancel')} /><span>{$i18n.t('app.telegram.childRequests.cancel')}</span></button>
        </TelegramRequestRow>{/each}</div>
        {#if hasMore}<button class="load-more" type="button" on:click={showMore}><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.childRequests.showMore')} />{$i18n.t('app.telegram.childRequests.showMore')}</button>{/if}
    {/if}
    {#if cancelError}<p class="error" role="alert">{cancelError}</p>{/if}
</section>

<style>
    .panel { width:100%; box-sizing:border-box; border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .items { display:flex; flex-direction:column; width:100%; }
    .cancel { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.3rem .5rem; border:1px solid #f3cfd2; border-radius:.55rem; background:#fff0f1; color:#c63c42; font:inherit; font-size:.78rem; font-weight:700; cursor:pointer; }
    .cancel:disabled { cursor:wait; opacity:.6; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .state-empty { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#eaf7ef; color:#275d3b; font-size:.9rem; }
    .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#fff0f0; color:#a33b3b; }
    .state-error p { margin:0; flex:1; font-size:.9rem; }
    .state-error button { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; font-size:.9rem; margin:.6rem 0 0; }
    .load-more { width:100%; min-height:2.75rem; margin-top:.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
