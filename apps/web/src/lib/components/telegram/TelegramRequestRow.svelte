<script lang="ts">
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import { stripLeadingEmoji } from './telegramEntityIcons';
    import type { TelegramRequestPresentation } from './telegramRequestPresentation';
    import { formatLastUsedTime } from './telegramLastUsed';

    interface Props {
        presentation: TelegramRequestPresentation;
        locale?: string;
        actionsInTrailing?: boolean;
        children?: import('svelte').Snippet;
    }

    let {
        presentation,
        locale = 'en',
        actionsInTrailing = false,
        children
    }: Props = $props();

    let title = $derived(stripLeadingEmoji(presentation.title));
</script>

<TelegramEntityRow hasTrailingActions={actionsInTrailing}>
    {#snippet icon()}
        <span  aria-hidden="true"><TelegramIcon name={presentation.entityIcon} size={20} label={presentation.kindLabel} /></span>
    {/snippet}
    {#snippet title()}
        <h3 >{title}</h3>
    {/snippet}
    {#snippet trailing()}
        <div class="request-status" >
            <span class="status-chip status-chip--{presentation.statusTone}">{presentation.statusLabel}</span>
            {#if actionsInTrailing && children}<div class="trailing-actions">{@render children?.()}</div>{/if}
        </div>
    {/snippet}
    {#snippet metadata()}
        <span >{presentation.metadata}</span>
    {/snippet}
    {#snippet actions()}
        <div  class="content-footer">
            <div class="request-summary">
                <p class="amount" class:spend={presentation.isReward}><TelegramCoin size={13} />{presentation.amountSign}{presentation.amount}</p>
                {#if presentation.createdAt}<time datetime={presentation.createdAt}>{formatLastUsedTime(presentation.createdAt, locale as 'en' | 'ru')}</time>{/if}
            </div>
            {#if !actionsInTrailing && presentation.statusTone === 'pending'}<div class="request-actions">{@render children?.()}</div>{/if}
        </div>
    {/snippet}
</TelegramEntityRow>

<style>
    .request-status { position:relative; display:flex; align-items:flex-end; gap:.35rem; min-width:0; flex-direction:column; }
    .status-chip { display:inline-flex; align-items:center; max-width:100%; padding:.2rem .55rem; border-radius:999px; font-size:.7rem; font-weight:800; white-space:nowrap; }
    .status-chip--pending { background:#fff5df; color:#98721d; }
    .status-chip--approved { background:#eaf7ef; color:#17884b; }
    .status-chip--rejected { background:#fff0f1; color:#c63c42; }
    .status-chip--cancelled { background:#eef0f5; color:#66718a; }
    .status-chip--neutral { background:#eef0f5; color:#66718a; }
    time { color:#7f899e; font-size:.7rem; white-space:nowrap; }
    .content-footer { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:.35rem .5rem; width:100%; margin-top:.1rem; }
    .request-summary { display:flex; align-items:center; flex-wrap:wrap; gap:.35rem .5rem; min-width:0; }
    .amount { display:flex; align-items:center; gap:.25rem; margin:0; color:#237b3c; font-weight:700; font-size:.8rem; }
    .amount.spend { color:#a33b3b; }
    .request-actions, .trailing-actions { display:flex; flex:0 1 auto; flex-direction:row; flex-wrap:wrap; justify-content:flex-end; gap:.35rem; min-width:0; }
    .trailing-actions { position:absolute; top:calc(100% + .15rem); right:0; width:max-content; }
    :global(.request-actions button) { min-width:2.75rem; min-height:2.75rem; }
    @media (max-width:370px) {
        .request-actions { flex-basis:100%; justify-content:flex-start; }
    }
</style>
