<script lang="ts">
    import { run } from 'svelte/legacy';

    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';

    const i18n = useI18n();

    interface Props {
        open?: boolean;
        busy?: boolean;
        error?: string;
        onadjust?: (value: { amount: number; note: string | null }) => void;
        onclose?: () => void;
    }

    let { open = false, busy = false, error = '', onadjust = () => {}, onclose = () => {} }: Props = $props();
    const MAX_ADJUST = 1_000_000;
    let amount = $state('');
    let note = $state('');
    let localError = $state('');
    run(() => {
        if (!open) { amount = ''; note = ''; localError = ''; }
    });
    function close() {
        if (!busy) onclose();
    }
    function submit() {
        if (busy) return;
        const rawAmount = String(amount);
        const value = Number(amount);
        if (!rawAmount.trim()) { localError = $i18n.t('app.telegram.coinAdjust.enterAmount'); return; }
        if (!Number.isInteger(value) || value === 0) { localError = $i18n.t('app.telegram.coinAdjust.nonZeroWhole'); return; }
        if (Math.abs(value) > MAX_ADJUST) { localError = $i18n.t('app.telegram.coinAdjust.amountTooBig', { max: $i18n.formatNumber(MAX_ADJUST) }); return; }
        localError = '';
        onadjust({ amount: value, note: note.trim() || null });
    }
</script>

{#if open}
    <TelegramBottomSheet open={open} labelledBy="coin-adjust-title" {busy} onClose={close}>
        <h2 id="coin-adjust-title">{$i18n.t('app.telegram.coinAdjust.title')}</h2>
        <div class="amount-row">
            <TelegramCoin size={20} />
            <input id="coin-amount" type="number" inputmode="numeric" bind:value={amount} placeholder={$i18n.t('app.telegram.coinAdjust.amountPlaceholder')} aria-label={$i18n.t('app.telegram.coinAdjust.amountAria')} aria-invalid={localError ? 'true' : undefined} aria-describedby={localError ? 'coin-adjust-error' : undefined} onkeydown={(event) => { if (event.key === 'Enter') submit(); }} />
        </div>
        <label for="coin-note">{$i18n.t('app.telegram.coinAdjust.noteLabel')}</label>
        <input id="coin-note" type="text" maxlength="80" bind:value={note} placeholder={$i18n.t('app.telegram.coinAdjust.notePlaceholder')} />
        {#if localError || error}<p id="coin-adjust-error" class="error" role="alert">{localError || error}</p>{/if}
        <div class="actions">
            <button type="button" onclick={close} disabled={busy}><TelegramIcon name="back" size={18} label={$i18n.t('app.telegram.coinAdjust.cancel')} />{$i18n.t('app.telegram.coinAdjust.cancel')}</button>
            <button class="primary" type="button" onclick={submit} disabled={busy}><TelegramIcon name="coinAdjustment" size={18} label={busy ? $i18n.t('app.telegram.coinAdjust.saving') : $i18n.t('app.telegram.coinAdjust.save')} />{busy ? $i18n.t('app.telegram.coinAdjust.saving') : $i18n.t('app.telegram.coinAdjust.save')}</button>
        </div>
    </TelegramBottomSheet>
{/if}

<style>
    h2 { margin:0 0 .9rem; color:#18243d; font-size:1.15rem; }
    .amount-row { display:flex; align-items:center; gap:.5rem; margin-bottom:.8rem; }
    input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    label { display:block; margin-bottom:.35rem; color:#33415f; font-weight:600; font-size:.9rem; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .actions { display:flex; justify-content:flex-end; gap:.6rem; margin-top:1rem; }
    button { display:inline-flex; align-items:center; gap:.4rem; min-height:2.75rem; padding:.6rem .85rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button.primary { border-color:#3867d6; background:#3867d6; color:#fff; }
    button:disabled { cursor:wait; opacity:.6; }
</style>
