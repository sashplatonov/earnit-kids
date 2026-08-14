<script lang="ts">
    import { createEventDispatcher, tick } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';

    const i18n = useI18n();

    export let open = false;
    export let title = '';
    export let actionLabel = '';
    export let busy = false;
    const dispatch = createEventDispatcher<{ submit: string | null; close: void }>();
    let note = '';
    $: if (!open) note = '';
    $: resolvedActionLabel = actionLabel || $i18n.t('app.telegram.requestSheet.sendRequest');
    function manageFocus(node: HTMLTextAreaElement) {
        const target = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        void tick().then(() => node.focus());
        return {
            destroy() {
                if (target?.isConnected) target.focus();
            },
        };
    }
    function close() {
        if (!busy) dispatch('close');
    }
    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            event.preventDefault();
            close();
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={close}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="request-sheet-title" tabindex="-1" on:keydown={handleKeydown}>
        <h2 id="request-sheet-title">{$i18n.t('app.telegram.requestSheet.requestTitle', { title })}</h2>
        <label for="request-note">{$i18n.t('app.telegram.requestSheet.optionalNote')}</label>
        <textarea id="request-note" maxlength="240" bind:value={note} placeholder={$i18n.t('app.telegram.requestSheet.notePlaceholder')} use:manageFocus></textarea>
        <div class="actions">
            <button type="button" on:click={close} disabled={busy}><TelegramIcon name="back" size={18} label={$i18n.t('app.telegram.requestSheet.cancel')} />{$i18n.t('app.telegram.requestSheet.cancel')}</button>
            <button class="primary" type="button" on:click={() => dispatch('submit', note.trim() || null)} disabled={busy}><TelegramIcon name="request" size={18} label={busy ? $i18n.t('app.telegram.requestSheet.sendingRequest') : resolvedActionLabel} />{busy ? $i18n.t('app.telegram.requestSheet.sending') : resolvedActionLabel}</button>
        </div>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 1rem; color:#18243d; font-size:1.2rem; } label { display:block; margin-bottom:.4rem; color:#33415f; font-weight:600; } textarea { box-sizing:border-box; width:100%; min-height:5rem; padding:.7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; resize:vertical; } .actions { display:flex; justify-content:flex-end; gap:.6rem; margin-top:1rem; } button { min-height:2.75rem; padding:.6rem .85rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; } button.primary { border-color:#3867d6; background:#3867d6; color:#fff; }
</style>
