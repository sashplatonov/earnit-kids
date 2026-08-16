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

<article class="request-row">
    <div class="entity-main">
        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: requestKind(request), title: request.taskName || request.itemName || request.title || '', group: request.taskGroup || request.itemGroup || request.groupName })} size={20} label={kindLabel} /></span>
        <div class="entity-text">
            <h3>{stripLeadingEmoji(request.taskName || request.itemName || request.title || kindLabel)}</h3>
            <p class="meta">{meta}</p>
            <p class="amount"><TelegramCoin size={13} />+{request.coins ?? request.amount ?? 0}</p>
        </div>
    </div>
    <div class="row-side">
        <span class="status-chip status-chip--{statusTone}">{statusLabel}</span>
        {#if request.createdAt}<time datetime={request.createdAt}>{formatLastUsedTime(request.createdAt, locale as 'en' | 'ru')}</time>{/if}
    </div>
    <slot />
</article>

<style>
    .request-row { display:flex; align-items:center; justify-content:space-between; gap:.75rem; width:100%; padding:.75rem 0; border-bottom:1px solid #edf0f5; }
    .request-row:last-child { border-bottom:0; }
    .entity-main { display:flex; align-items:center; gap:.6rem; min-width:0; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; }
    h3 { margin:0; font-size:.95rem; line-height:1.3; }
    .meta { margin:.15rem 0 0; color:#66718a; font-size:.8rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:.25rem 0 0; color:#18243d; font-weight:750; font-size:.85rem; }
    .row-side { display:flex; flex-direction:column; align-items:flex-end; gap:.3rem; flex:0 0 auto; }
    .status-chip { display:inline-flex; align-items:center; padding:.15rem .5rem; border-radius:999px; font-size:.72rem; font-weight:700; white-space:nowrap; }
    .status-chip--pending { background:#fff4e0; color:#8a6118; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#8a93a8; font-size:.75rem; }
</style>
