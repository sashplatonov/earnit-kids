<script lang="ts">
    import { createEventDispatcher, tick } from 'svelte';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    export let open = false;
    export let busy = false;
    export let error = '';
    const dispatch = createEventDispatcher<{ adjust: { amount: number; note: string | null }; close: void }>();
    let amount = '';
    let note = '';
    $: if (!open) { amount = ''; note = ''; }
    function manageFocus(node: HTMLInputElement) {
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
    function submit() {
        if (busy) return;
        const value = Number.parseInt(amount, 10);
        if (!Number.isFinite(value) || value === 0) return;
        dispatch('adjust', { amount: value, note: note.trim() || null });
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
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="coin-adjust-title" tabindex="-1" on:keydown={handleKeydown}>
        <h2 id="coin-adjust-title">Adjust coins</h2>
        <div class="amount-row">
            <TelegramCoin size={20} />
            <input id="coin-amount" type="number" inputmode="numeric" bind:value={amount} placeholder="e.g. +10 or -5" aria-label="Coin amount" use:manageFocus on:keydown={(event) => { if (event.key === 'Enter') submit(); }} />
        </div>
        <label for="coin-note">Note (optional)</label>
        <input id="coin-note" type="text" maxlength="80" bind:value={note} placeholder="Why?" />
        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <div class="actions">
            <button type="button" on:click={close} disabled={busy}><TelegramIcon name="back" size={18} label="Cancel" />Cancel</button>
            <button class="primary" type="button" on:click={submit} disabled={busy}><TelegramIcon name="coinAdjustment" size={18} label={busy ? 'Saving…' : 'Save'} />{busy ? 'Saving…' : 'Save'}</button>
        </div>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
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
