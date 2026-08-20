<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { approveRequest, rejectRequest } from '$lib/services/api';
    import { refreshData } from '$lib/services/bootstrap';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';

    const i18n = useI18n();

    export let requests: Request[] = [];
    export let canDecide = false;
    export let childId: string | number | null = null;
    export let loading = false;
    export let error = '';
    export let onRetry: () => void = () => {};
    export let showHeading = true;
    export let headingText = '';
    export let emptyText = '';
    let busy: string | number | null = null;
    let decisionError = '';

    $: resolvedHeading = headingText || $i18n.t('app.telegram.requests.requests');
    $: resolvedEmpty = emptyText || $i18n.t('app.telegram.requests.noRequests');

    async function decide(id: string | number, action: 'approve' | 'reject') {
        busy = id;
        decisionError = '';
        const result = action === 'approve' ? await approveRequest(id, childId) : await rejectRequest(id, childId);
        if (result == null) decisionError = $i18n.t('app.telegram.requests.updateError');
        const refreshed = await refreshData();
        if (!refreshed && result != null) decisionError = $i18n.t('app.telegram.requests.savedButRefreshFailed');
        busy = null;
    }
    function requestKind(request: Request): 'task' | 'reward' {
        return request.requestType === 'shop_purchase' ? 'reward' : 'task';
    }
    function requestMeta(request: Request): string {
        const who = request.childNickname || $i18n.t('app.telegram.requests.child');
        const what = request.requestType === 'shop_purchase' ? $i18n.t('app.telegram.requests.rewardRequest') : $i18n.t('app.telegram.requests.taskRequest');
        return `${who} · ${what}`;
    }
    function requestAmount(request: Request): number {
        return Math.abs(request.coins ?? request.amount ?? 0);
    }
</script>

<section class="panel" aria-labelledby="telegram-requests-title">
    {#if showHeading}<div class="heading"><h2 id="telegram-requests-title">{resolvedHeading}</h2></div>{/if}
    {#if loading}<p class="muted" role="status">{$i18n.t('app.telegram.requests.loading')}</p>
    {:else if error}<div class="state-error" role="alert"><TelegramIcon name="alert" size={18} label={$i18n.t('app.telegram.home.error')} /><p>{error}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label={$i18n.t('app.telegram.shell.retry')} />{$i18n.t('app.telegram.shell.retry')}</button></div>
    {:else if !requests.length}<div class="state-empty"><TelegramIcon name="checkCircle" size={18} label={$i18n.t('app.telegram.requests.allClear')} /><span>{resolvedEmpty}</span></div>
    {:else}<div class="items" aria-label={resolvedHeading}>{#each requests as request (request.id)}<article class="request-row">
        <span class="entity-graphic" aria-hidden="true"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: requestKind(request), title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName })} size={18} label={$i18n.t('app.telegram.requests.request')} /></span></span>
        <div class="content">
            <h3>{stripLeadingEmoji(request.taskName || request.itemName || request.title || $i18n.t('app.telegram.requests.request'))}</h3>
            <p class="meta">{requestMeta(request)}</p>
            <div class="content-footer">
                <p class="amount" class:spend={requestKind(request) === 'reward'}><TelegramCoin size={13} />{requestKind(request) === 'reward' ? '-' : '+'}{requestAmount(request)}</p>
                {#if canDecide && request.status === 'pending'}
                    <div class="attention-actions"><button class="approve" type="button" aria-label={$i18n.t('app.telegram.requests.approveRequest')} disabled={busy === request.id} on:click={() => decide(request.id, 'approve')}><TelegramIcon name="approve" size={16} label={$i18n.t('app.telegram.requests.approve')} /><span>{$i18n.t('app.telegram.requests.approve')}</span></button><button class="reject" type="button" aria-label={$i18n.t('app.telegram.requests.rejectRequest')} disabled={busy === request.id} on:click={() => decide(request.id, 'reject')}><TelegramIcon name="reject" size={16} label={$i18n.t('app.telegram.requests.reject')} /><span>{$i18n.t('app.telegram.requests.reject')}</span></button></div>
                {/if}
            </div>
        </div>
    </article>{/each}</div>{/if}
    {#if decisionError}<p class="error" role="alert">{decisionError}</p>{/if}
</section>

<style>
    .panel { width:100%; }
    .heading { display:flex; justify-content:space-between; align-items:center; gap:.5rem; padding:0 .2rem; }
    h2 { margin:0 0 .65rem; color:#18243d; }
    .items { display:flex; flex-direction:column; width:100%; box-sizing:border-box; }
    .request-row { display:flex; align-items:flex-start; gap:.6rem; width:100%; box-sizing:border-box; padding:.6rem 0; border-bottom:1px solid #edf0f5; background:transparent; }
    .request-row:last-child { border-bottom:0; }
    .entity-graphic { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; }
    .entity-icon { display:grid; place-items:center; color:#5b63e9; }
    .content { flex:1; min-width:0; display:flex; flex-direction:column; gap:.2rem; }
    h3 { margin:0; color:#18243d; font-size:.9rem; line-height:1.25; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .meta { margin:0; color:#7f899e; font-size:.75rem; line-height:1.3; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; gap:.5rem; margin-top:.1rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#237b3c; font-weight:700; font-size:.81rem; white-space:nowrap; }
    .amount.spend { color:#a33b3b; }
    .attention-actions { display:flex; gap:.35rem; flex-wrap:wrap; justify-content:flex-end; }
    .attention-actions button { display:inline-flex; align-items:center; justify-content:center; gap:.25rem; min-height:2.25rem; padding:.3rem .45rem; border-radius:.55rem; font:inherit; font-size:.78rem; font-weight:700; cursor:pointer; }
    .attention-actions button:disabled { cursor:wait; opacity:.6; }
    .attention-actions button:focus-visible, .state-error button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .approve { border:1px solid #cce9d8; background:#eaf7ef; color:#17884b; }
    .reject { border:1px solid #f3cfd2; background:#fff0f1; color:#c63c42; }
    @media (max-width:370px) {
        .content-footer { align-items:flex-start; flex-direction:column; }
        .attention-actions { justify-content:flex-start; width:100%; }
    }
    .state-empty { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#eaf7ef; color:#275d3b; font-size:.9rem; }
    .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .75rem; border-radius:.8rem; background:#fff0f0; color:#a33b3b; }
    .state-error p { margin:0; flex:1; }
    .state-error button { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
</style>
