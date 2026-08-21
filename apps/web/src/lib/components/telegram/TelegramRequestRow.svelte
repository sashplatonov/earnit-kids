<script lang="ts">
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import { stripLeadingEmoji } from './telegramEntityIcons';
    import type { TelegramRequestPresentation } from './telegramRequestPresentation';
    import { formatLastUsedTime } from './telegramLastUsed';

    export let presentation: TelegramRequestPresentation;
    export let locale: string = 'en';

    $: title = stripLeadingEmoji(presentation.title);
</script>

<TelegramEntityRow>
    <span slot="icon" aria-hidden="true"><TelegramIcon name={presentation.entityIcon} size={20} label={presentation.kindLabel} /></span>
    <h3 slot="title">{title}</h3>
    <div slot="trailing">
        <span class="status-chip status-chip--{presentation.statusTone}">{presentation.statusLabel}</span>
        {#if presentation.createdAt}<time datetime={presentation.createdAt}>{formatLastUsedTime(presentation.createdAt, locale as 'en' | 'ru')}</time>{/if}
    </div>
    <span slot="metadata">{presentation.metadata}</span>
    <div slot="actions" class="content-footer">
        <p class="amount" class:spend={presentation.isReward}><TelegramCoin size={13} />{presentation.amountSign}{presentation.amount}</p>
        {#if presentation.statusTone === 'pending'}<div class="request-actions"><slot /></div>{/if}
    </div>
</TelegramEntityRow>

<style>
    .status-chip { display:inline-flex; align-items:center; padding:.2rem .55rem; border-radius:999px; font-size:.7rem; font-weight:800; white-space:nowrap; }
    .status-chip--pending { background:#fff5df; color:#98721d; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#7f899e; font-size:.7rem; white-space:nowrap; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; gap:.5rem; margin-top:.1rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#237b3c; font-weight:700; font-size:.8rem; }
    .amount.spend { color:#a33b3b; }
    .request-actions { display:flex; flex-direction:row; gap:.35rem; flex:0 0 auto; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; width:100%; gap:.5rem; }
</style>
