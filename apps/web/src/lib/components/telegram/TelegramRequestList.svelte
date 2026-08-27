<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { useRequestActions } from '$lib/telegram/services/requestActions';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramAsyncState from './ui/TelegramAsyncState.svelte';
    import type { TelegramAsyncState as AsyncState } from './ui/telegramUi';
    import TelegramRequestRow from './TelegramRequestRow.svelte';
    import { presentRequest } from './telegramRequestPresentation';

    const i18n = useI18n();
    const requestActions = useRequestActions();

    interface Props {
        requests?: Request[];
        canDecide?: boolean;
        childId?: string | number | null;
        loading?: boolean;
        error?: string;
        onRetry?: () => void;
        showHeading?: boolean;
        headingText?: string;
        emptyText?: string;
        onDecision?: () => void;
    }

    let {
        requests = [],
        canDecide = false,
        childId = null,
        loading = false,
        error = '',
        onRetry = () => {},
        showHeading = true,
        headingText = '',
        emptyText = '',
        onDecision = () => {}
    }: Props = $props();
    let busy: string | number | null = $state(null);
    let decisionError = $state('');

    let resolvedHeading = $derived(headingText || $i18n.t('app.telegram.requests.requests'));
    let resolvedEmpty = $derived(emptyText || $i18n.t('app.telegram.requests.noRequests'));
    let asyncState = $derived((loading ? 'loading' : error ? 'error' : !requests.length ? 'empty' : 'success') as AsyncState);

    async function decide(id: string | number, action: 'approve' | 'reject') {
        busy = id;
        decisionError = '';
        const result = action === 'approve' ? await requestActions.approve(id, childId) : await requestActions.reject(id, childId);
        if (result == null) decisionError = $i18n.t('app.telegram.requests.updateError');
        const refreshed = await requestActions.refresh();
        if (!refreshed && result != null) decisionError = $i18n.t('app.telegram.requests.savedButRefreshFailed');
        if (result != null) onDecision();
        busy = null;
    }
    let presentations = $derived(requests.map((request) => presentRequest(request, {
        kindLabel: (kind) => kind === 'reward' ? $i18n.t('app.telegram.requests.rewardRequest') : $i18n.t('app.telegram.requests.taskRequest'),
        statusLabel: (status) => status === 'approved'
            ? $i18n.t('app.telegram.childRequests.statusApproved')
            : status === 'rejected'
                ? $i18n.t('app.telegram.childRequests.statusRejected')
                : status === 'cancelled'
                    ? $i18n.t('app.telegram.childRequests.statusCancelled')
                    : $i18n.t('app.telegram.childRequests.statusPending'),
        metadata: (request) => request.taskGroup || request.itemGroup || request.groupName || '',
    })));
</script>

<section aria-labelledby="telegram-requests-title">
    {#if showHeading}<div class="heading"><h2 id="telegram-requests-title">{resolvedHeading}</h2></div>{/if}
    {#if asyncState !== 'success'}<TelegramAsyncState state={asyncState} loadingLabel={$i18n.t('app.telegram.requests.loading')} emptyLabel={resolvedEmpty} errorMessage={error} retryLabel={$i18n.t('app.telegram.shell.retry')} onRetry={onRetry} />
    {:else}<TelegramListSurface label={resolvedHeading}>{#each presentations as presentation (presentation.request.id)}
        <TelegramRequestRow {presentation} locale={$i18n.locale}>
            {#if canDecide && presentation.status === 'pending'}
                    <div class="attention-actions"><button class="approve" type="button" aria-label={$i18n.t('app.telegram.requests.approveRequest')} disabled={busy === presentation.request.id} onclick={() => decide(presentation.request.id, 'approve')}><TelegramIcon name="approve" size={16} label={$i18n.t('app.telegram.requests.approve')} /><span>{$i18n.t('app.telegram.requests.approve')}</span></button><button class="reject" type="button" aria-label={$i18n.t('app.telegram.requests.rejectRequest')} disabled={busy === presentation.request.id} onclick={() => decide(presentation.request.id, 'reject')}><TelegramIcon name="reject" size={16} label={$i18n.t('app.telegram.requests.reject')} /><span>{$i18n.t('app.telegram.requests.reject')}</span></button></div>
            {/if}
        </TelegramRequestRow>{/each}</TelegramListSurface>{/if}
    {#if decisionError}<p class="error" role="alert">{decisionError}</p>{/if}
</section>

<style>
    .heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; padding:0 .2rem; }
    h2 { margin:0 0 .65rem; color:#18243d; }
    .attention-actions { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:.35rem; width:100%; }
    .attention-actions button { display:inline-flex; align-items:center; justify-content:center; gap:.25rem; width:100%; min-width:0; min-height:2.75rem; padding:.3rem .45rem; border-radius:.55rem; font:inherit; font-size:.78rem; font-weight:700; cursor:pointer; }
    .attention-actions button:disabled { cursor:wait; opacity:.6; }
    .attention-actions button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .approve { border:1px solid #cce9d8; background:#eaf7ef; color:#17884b; }
    .reject { border:1px solid #f3cfd2; background:#fff0f1; color:#c63c42; }
    @media (max-width:370px) {
        .attention-actions { width:100%; }
    }
    .error { color:#a33b3b; }
</style>
