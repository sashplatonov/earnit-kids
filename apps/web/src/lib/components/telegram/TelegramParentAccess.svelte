<script lang="ts">
    import ParentAccessPanel from '$lib/features/workspace/family/ParentAccessPanel.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';

    export let open = false;
    export let onClose: () => void = () => {};
    export let demoMode = false;
    let demoEmail = '';
    let demoMessage = '';
    const i18n = useI18n();

    function saveDemoAccess() {
        demoMessage = demoEmail.trim() ? $i18n.t('app.liveDemo.mockSaved') : $i18n.t('app.telegram.family.addChildNameRequired');
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-label={$i18n.t('app.telegram.parents.title')} tabindex="-1">
        {#if demoMode}
            <h2>{$i18n.t('app.telegram.parents.title')}</h2>
            <p class="demo-notice" role="note">{$i18n.t('app.liveDemo.demoActionNotice')}</p>
            <label for="demo-parent-email">Email</label>
            <input id="demo-parent-email" type="email" bind:value={demoEmail} placeholder="parent@example.com" />
            <button class="primary" type="button" on:click={saveDemoAccess}>{$i18n.t('common.actions.save')}</button>
            {#if demoMessage}<p class="demo-message" role="status">{demoMessage}</p>{/if}
        {:else}
            <ParentAccessPanel embedded compact hideTitle={false} />
        {/if}
        <button class="close" type="button" on:click={onClose}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.header.close')} />{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop{position:fixed;inset:0;z-index:40;background:rgb(15 24 45 / 35%)}.sheet{position:fixed;inset:auto 0 0;z-index:41;display:grid;gap:.65rem;padding:.75rem max(.75rem,env(safe-area-inset-left)) calc(.75rem + env(safe-area-inset-bottom));border-radius:1.1rem 1.1rem 0 0;background:#fff;box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%);max-height:88dvh;overflow:auto}.close{display:flex;align-items:center;justify-content:center;gap:.5rem;width:100%;min-height:2.75rem;padding:.5rem .7rem;border:0;border-radius:.7rem;background:#fff7f7!important;border:1px solid #f1c7ca!important;color:#a84a50!important;font:inherit;font-weight:700}.demo-notice{margin:.25rem 0 .8rem;padding:.55rem .65rem;border:1px solid #ead9a4;border-radius:.65rem;background:#fff9e8;color:#705719;font-size:.82rem;line-height:1.4}label{display:block;margin:.6rem 0 .3rem;color:#33415f;font-size:.85rem;font-weight:600}input{box-sizing:border-box;width:100%;min-height:2.75rem;padding:.6rem .7rem;border:1px solid #cfd6e4;border-radius:.7rem;font:inherit}.primary{width:100%;min-height:2.75rem;margin-top:.8rem;border:0;border-radius:.7rem;background:#3867d6;color:#fff;font:inherit;font-weight:700;cursor:pointer}.demo-message{color:#17884b;font-size:.85rem}
    @media (min-width: 700px) {.sheet{inset:50% auto auto 50%;width:min(38rem,calc(100% - 3rem));max-height:min(82dvh,46rem);padding:1.4rem;border-radius:1.25rem;box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%);transform:translate(-50%,-50%)}}
</style>
