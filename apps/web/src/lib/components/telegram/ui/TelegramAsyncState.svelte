<script lang="ts">
    import TelegramIcon from '../TelegramIcon.svelte';
    import { telegramUi, type TelegramAsyncState as AsyncState } from './telegramUi';

    export let state: AsyncState;
    export let loadingLabel = '';
    export let emptyLabel = '';
    export let errorMessage = '';
    export let retryLabel = '';
    export let onRetry: () => void = () => {};
</script>

{#if state === 'loading'}
    <p class="async-state muted" role="status" aria-live="polite">{loadingLabel}</p>
{:else if state === 'empty'}
    <div class="async-state state-empty" role="status"><TelegramIcon name="checkCircle" size={18} label={emptyLabel} /><span>{emptyLabel}</span></div>
{:else if state === 'error'}
    <div class="async-state state-error" role="alert" style={`--telegram-focus:${telegramUi.colors.focus};`}><TelegramIcon name="alert" size={18} label={errorMessage} /><p>{errorMessage}</p><button type="button" on:click={onRetry}><TelegramIcon name="refresh" size={18} label={retryLabel} />{retryLabel}</button></div>
{/if}

<style>
    .async-state { box-sizing:border-box; }
    .muted { padding:.6rem 0; color:#66718a; }
    .state-empty, .state-error { display:flex; align-items:center; gap:.55rem; padding:.6rem .15rem; }
    .state-empty { color:#275d3b; font-size:.9rem; }
    .state-error { color:#a33b3b; }
    .state-error p { margin:0; flex:1; font-size:.9rem; overflow-wrap:anywhere; }
    button { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #f3cfd2; border-radius:.6rem; background:#fff; color:#a33b3b; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid var(--telegram-focus, #80aaff); outline-offset:2px; }
</style>
