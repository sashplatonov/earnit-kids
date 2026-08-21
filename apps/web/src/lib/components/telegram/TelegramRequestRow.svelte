<script lang="ts">
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { stripLeadingEmoji } from './telegramEntityIcons';
    import type { TelegramRequestPresentation } from './telegramRequestPresentation';
    import { formatLastUsedTime } from './telegramLastUsed';

    export let presentation: TelegramRequestPresentation;
    export let locale: string = 'en';

    $: title = stripLeadingEmoji(presentation.title);
</script>

<article class="request-row row">
    <span class="entity-graphic" aria-hidden="true">
        <span class="entity-icon"><TelegramIcon name={presentation.entityIcon} size={20} label={presentation.kindLabel} /></span>
    </span>
    <div class="content">
        <div class="content-header">
            <h3>{title}</h3>
            <div class="side">
                <span class="status-chip status-chip--{presentation.statusTone}">{presentation.statusLabel}</span>
                {#if presentation.createdAt}<time datetime={presentation.createdAt}>{formatLastUsedTime(presentation.createdAt, locale as 'en' | 'ru')}</time>{/if}
            </div>
        </div>
        <p class="meta">{presentation.metadata}</p>
        <div class="content-footer">
            <p class="amount" class:spend={presentation.isReward}><TelegramCoin size={13} />{presentation.amountSign}{presentation.amount}</p>
            {#if presentation.statusTone === 'pending'}
                <div class="request-actions"><slot /></div>
            {/if}
        </div>
    </div>
</article>

<style>
    .request-row { display:flex; align-items:stretch; gap:.25rem; width:100%; box-sizing:border-box; min-height:4rem; padding:.5rem 0; border-bottom:1px solid #edf0f5; background:transparent; }
    .request-row:last-child { border-bottom:0; }
    .entity-graphic { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; margin-top:.2rem; border-radius:.65rem; background:#eef0ff; }
    .entity-icon { display:grid; place-items:center; color:#5b63e9; }
    .content { flex:1; min-width:0; display:flex; flex-direction:column; gap:.25rem; }
    .content-header { display:flex; align-items:flex-start; justify-content:space-between; gap:.5rem; }
    h3 { display:block; margin:0; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; overflow-wrap:anywhere; min-width:0; }
    .side { display:flex; flex-direction:column; align-items:flex-end; gap:.15rem; flex:0 0 auto; min-width:0; }
    .status-chip { display:inline-flex; align-items:center; padding:.2rem .55rem; border-radius:999px; font-size:.7rem; font-weight:800; white-space:nowrap; }
    .status-chip--pending { background:#fff5df; color:#98721d; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#7f899e; font-size:.7rem; white-space:nowrap; }
    .meta { margin:0; color:#8a93a8; font-size:.75rem; line-height:1.3; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; gap:.5rem; margin-top:.1rem; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#237b3c; font-weight:700; font-size:.8rem; }
    .amount.spend { color:#a33b3b; }
    .request-actions { display:flex; flex-direction:row; gap:.35rem; flex:0 0 auto; }
    @media (max-width:370px) {
        .content-header { flex-direction:column; }
        .side { align-items:flex-start; flex-direction:row; flex-wrap:wrap; gap:.35rem .5rem; width:100%; }
        .content-footer { align-items:flex-start; }
    }
</style>
