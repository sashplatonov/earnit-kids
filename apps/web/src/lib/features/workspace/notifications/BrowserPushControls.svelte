<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { disableBrowserPush, enableBrowserPush, readBrowserPushState, type BrowserPushState } from './browserPush';
    export let enabled = true;
    const i18n = useI18n();
    let state: BrowserPushState = 'default';
    let busy = false;
    onMount(() => { if (enabled) void refresh(); else state = 'unsupported'; });
    async function refresh() { state = await readBrowserPushState(); }
    async function toggle() {
        if (busy || !enabled) return;
        busy = true;
        const current = await readBrowserPushState();
        state = 'pending';
        state = current === 'subscribed' ? await disableBrowserPush() : await enableBrowserPush();
        busy = false;
    }
</script>

<section class="browser-push" aria-labelledby="browser-push-title">
    <div class="copy"><h3 id="browser-push-title">{$i18n.t('app.telegram.notifications.browserPush.title')}</h3><p>{$i18n.t(`app.telegram.notifications.browserPush.${state}`)}</p></div>
    {#if state === 'subscribed'}
        <button type="button" class="control" on:click={() => void toggle()} disabled={busy}>{$i18n.t('app.telegram.notifications.browserPush.disable')}</button>
    {:else if enabled && (state === 'default' || state === 'unsubscribed' || state === 'error')}
        <button type="button" class="control" on:click={() => void toggle()} disabled={busy}>{$i18n.t('app.telegram.notifications.browserPush.enable')}</button>
    {/if}
</section>

<style>
    .browser-push { display:flex; align-items:center; gap:.75rem; padding:.85rem 0; border-bottom:1px solid #edf0f5; }
    .copy { flex:1; min-width:0; } h3 { margin:0; color:#18243d; font-size:1rem; } p { margin:.25rem 0 0; color:#66718a; font-size:.85rem; line-height:1.35; }
    .control { min-width:7rem; min-height:2.75rem; padding:.55rem .8rem; border:0; border-radius:.65rem; background:#3867d6; color:#fff; font:inherit; cursor:pointer; }
    .control:disabled { opacity:.6; cursor:wait; } @media(max-width:360px) { .browser-push { align-items:flex-start; flex-direction:column; } .control { width:100%; } }
</style>
