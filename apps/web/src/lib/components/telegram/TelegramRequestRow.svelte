<script lang="ts">
    import type { Request } from '$lib/stores/app';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { extractLeadingEmoji, getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { requestKind } from './telegramRequestKind';
    import { formatLastUsedTime } from './telegramLastUsed';

    export let request: Request;
    export let kindLabel: string;
    export let statusLabel: string;
    export let statusTone: 'pending' | 'approved' | 'rejected' | 'cancelled' | 'neutral' = 'neutral';
    export let meta: string;
    export let locale: string = 'en';

    $: title = stripLeadingEmoji(request.taskName || request.itemName || request.title || kindLabel);
    $: titleEmoji = extractLeadingEmoji(request.taskName || request.itemName || request.title || '');
    $: fallbackIcon = getTelegramEntityIcon({ kind: requestKind(request), title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName });
</script>

<article class="request-card">
    <span class="entity-graphic" aria-hidden="true">
        {#if titleEmoji}
            <span class="entity-emoji">{titleEmoji}</span>
        {:else}
            <span class="entity-icon"><TelegramIcon name={fallbackIcon} size={20} label={kindLabel} /></span>
        {/if}
    </span>
    <div class="content">
        <div class="content-header">
            <h3>{title}</h3>
            <div class="side">
                <span class="status-chip status-chip--{statusTone}">{statusLabel}</span>
                {#if request.createdAt}<time datetime={request.createdAt}>{formatLastUsedTime(request.createdAt, locale as 'en' | 'ru')}</time>{/if}
            </div>
        </div>
        <p class="meta">{meta}</p>
        <div class="content-footer">
            <p class="amount"><TelegramCoin size={13} />+{request.coins ?? request.amount ?? 0}</p>
            {#if statusTone === 'pending'}
                <div class="request-actions"><slot /></div>
            {/if}
        </div>
    </div>
</article>

<style>
    .request-card { display:flex; align-items:flex-start; gap:.65rem; padding:.6rem .65rem; border:1px solid #e5e9f1; border-radius:.85rem; background:#fff; }
    .entity-graphic { display:grid; place-items:center; width:2.5rem; height:2.5rem; flex:0 0 auto; border-radius:.75rem; background:#f5f6fa; }
    .entity-emoji { font-size:1.35rem; line-height:1; }
    .entity-icon { display:grid; place-items:center; color:#5b63e9; }
    .content { flex:1; min-width:0; display:flex; flex-direction:column; gap:.28rem; }
    .content-header { display:flex; align-items:flex-start; justify-content:space-between; gap:.5rem; }
    h3 { margin:0; font-size:.95rem; line-height:1.25; font-weight:700; color:#18243d; white-space:normal; overflow-wrap:anywhere; min-width:0; }
    .side { display:flex; flex-direction:column; align-items:flex-end; gap:.15rem; flex:0 0 auto; min-width:0; }
    .status-chip { display:inline-flex; align-items:center; padding:.2rem .55rem; border-radius:999px; font-size:.7rem; font-weight:800; white-space:nowrap; }
    .status-chip--pending { background:#fff5df; color:#98721d; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#7f899e; font-size:.7rem; white-space:nowrap; }
    .meta { margin:0; color:#7f899e; font-size:.75rem; line-height:1.3; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; gap:.5rem; margin-top:.1rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#20283d; font-weight:700; font-size:.81rem; }
    .request-actions { display:flex; flex-direction:row; gap:.35rem; flex:0 0 auto; }
    @media (max-width:370px) {
        .content-header { flex-direction:column; }
        .side { align-items:flex-start; flex-direction:row; flex-wrap:wrap; gap:.35rem .5rem; width:100%; }
        .content-footer { align-items:flex-start; }
    }
</style>
