<script lang="ts">
    import { run } from 'svelte/legacy';

    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';

    const i18n = useI18n();

    interface Props {
        open?: boolean;
        title?: string;
        actionLabel?: string;
        busy?: boolean;
        onsubmit?: (note: string | null) => void;
        onclose?: () => void;
    }

    let {
        open = false,
        title = '',
        actionLabel = '',
        busy = $bindable(false),
        onsubmit = () => {},
        onclose = () => {}
    }: Props = $props();
    let note = $state('');
    run(() => {
        if (!open) note = '';
    });
    let resolvedActionLabel = $derived(actionLabel || $i18n.t('app.telegram.requestSheet.sendRequest'));
    function close() {
        if (!busy) onclose();
    }
</script>

{#if open}
    <TelegramBottomSheet open={open} labelledBy="request-sheet-title" {busy} onClose={close}>
        <h2 id="request-sheet-title">{$i18n.t('app.telegram.requestSheet.requestTitle', { title })}</h2>
        <label for="request-note">{$i18n.t('app.telegram.requestSheet.optionalNote')}</label>
        <textarea id="request-note" maxlength="240" bind:value={note} placeholder={$i18n.t('app.telegram.requestSheet.notePlaceholder')}></textarea>
        <div class="actions">
            <button type="button" onclick={close} disabled={busy}><TelegramIcon name="back" size={18} label={$i18n.t('app.telegram.requestSheet.cancel')} />{$i18n.t('app.telegram.requestSheet.cancel')}</button>
            <button class="primary" type="button" onclick={() => onsubmit(note.trim() || null)} disabled={busy}><TelegramIcon name="request" size={18} label={busy ? $i18n.t('app.telegram.requestSheet.sendingRequest') : resolvedActionLabel} />{busy ? $i18n.t('app.telegram.requestSheet.sending') : resolvedActionLabel}</button>
        </div>
    </TelegramBottomSheet>
{/if}

<style>
    h2 { margin:0 0 1rem; color:#18243d; font-size:1.2rem; } label { display:block; margin-bottom:.4rem; color:#33415f; font-weight:600; } textarea { box-sizing:border-box; width:100%; min-height:5rem; padding:.7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; resize:vertical; } .actions { display:flex; justify-content:flex-end; gap:.6rem; margin-top:1rem; } button { min-height:2.75rem; padding:.6rem .85rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; } button.primary { border-color:#3867d6; background:#3867d6; color:#fff; }
</style>
