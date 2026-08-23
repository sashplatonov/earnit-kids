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
    <label class="switch">
        <input type="checkbox" checked={state === 'subscribed'} aria-labelledby="browser-push-title"
            disabled={busy || !enabled || !['subscribed', 'default', 'unsubscribed', 'error'].includes(state)}
            on:change={() => void toggle()} />
        <span class="track"></span>
    </label>
</section>

<style>
    .browser-push { display:flex; align-items:center; gap:.6rem; min-height:2.9rem; padding:.3rem 0 1rem; border-bottom:1px solid #edf0f5; }
    .copy { flex:1; min-width:0; } h3 { margin:0; color:#18243d; font-size:1rem; } p { margin:.25rem 0 0; color:#66718a; font-size:.85rem; line-height:1.35; }
    .switch { position:relative; display:inline-flex; align-items:center; width:2.75rem; height:1.625rem; flex:0 0 auto; cursor:pointer; }
    .switch input { position:absolute; opacity:0; pointer-events:none; }
    .track { width:2.75rem; height:1.625rem; border-radius:999px; background:#cfd5e2; position:relative; transition:background .18s ease; }
    .track::after { content:""; position:absolute; top:.1875rem; left:.1875rem; width:1.25rem; height:1.25rem; border-radius:50%; background:#fff; box-shadow:0 1px 3px rgb(0 0 0 / 18%); transition:transform .18s ease; }
    .switch input:checked + .track { background:#3867d6; }
    .switch input:checked + .track::after { transform:translateX(1.125rem); }
    .switch input:focus-visible + .track { outline:3px solid rgb(56 103 214 / 22%); outline-offset:2px; }
    .switch input:disabled + .track { opacity:.45; cursor:not-allowed; }
</style>
