<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { requestKind } from './telegramRequestKind';
    import { formatLastUsedTime } from './telegramLastUsed';

    export let request: Request;
    export let kindLabel: string;
    export let statusLabel: string;
    export let statusTone: 'pending' | 'approved' | 'rejected' | 'cancelled' | 'neutral' = 'neutral';
    export let meta: string;
    export let locale: string = 'en';
</script>

<article class="request-card">
    <span class="entity-icon">
        <TelegramIcon name={getTelegramEntityIcon({ kind: requestKind(request), title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName })} size={20} label={kindLabel} />
    </span>
    <div class="entity-text">
        <h3>{stripLeadingEmoji(request.taskName || request.itemName || request.title || kindLabel)}</h3>
        <p class="meta">{meta}</p>
        <p class="amount"><TelegramCoin size={13} />+{request.coins ?? request.amount ?? 0}</p>
    </div>
    <div class="row-side">
        <span class="status-chip status-chip--{statusTone}">{statusLabel}</span>
        {#if request.createdAt}<time datetime={request.createdAt}>{formatLastUsedTime(request.createdAt, locale as 'en' | 'ru')}</time>{/if}
        {#if statusTone === 'pending'}
        <div class="request-actions"><slot /></div>
        {/if}
    </div>
</article>

<style>
    .request-card { display:grid; grid-template-columns:2.75rem minmax(0,1fr) auto; gap:.6rem; align-items:start; padding:.75rem; border:1px solid #e4e8f1; border-radius:1rem; background:#fff; }
    .entity-icon { display:grid; place-items:center; width:2.75rem; height:2.75rem; flex:0 0 auto; border-radius:.8rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; display:flex; flex-direction:column; gap:.25rem; }
    h3 { margin:0; font-size:.94rem; line-height:1.3; font-weight:700; white-space:normal; overflow-wrap:anywhere; min-width:0; }
    .meta { margin:0; color:#7f899e; font-size:.75rem; line-height:1.3; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#20283d; font-weight:700; font-size:.81rem; }
    .row-side { display:flex; flex-direction:column; align-items:flex-end; gap:.5rem; flex:0 0 auto; min-width:0; }
    .status-chip { display:inline-flex; align-items:center; padding:.2rem .6rem; border-radius:999px; font-size:.7rem; font-weight:800; white-space:nowrap; }
    .status-chip--pending { background:#fff5df; color:#98721d; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#7f899e; font-size:.7rem; }
    .request-actions { display:flex; flex-direction:row; gap:.35rem; }
    @media (max-width:370px) {
        .request-card { grid-template-columns:2.75rem 1fr; }
        .row-side { grid-column:2; align-items:flex-start; flex-direction:row; flex-wrap:wrap; gap:.6rem; margin-top:.6rem; }
        .status-chip { margin:0; }
    }
</style>
