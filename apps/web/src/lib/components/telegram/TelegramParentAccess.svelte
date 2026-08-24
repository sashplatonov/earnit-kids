<script lang="ts">
    import ParentAccessPanel from '$lib/features/workspace/family/ParentAccessPanel.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';

    export let open = false;
    export let onClose: () => void = () => {};
    const i18n = useI18n();
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="parent-access-title" tabindex="-1">
        <h2 id="parent-access-title">{$i18n.t('app.telegram.parents.title')}</h2>
        <ParentAccessPanel />
        <button class="close" type="button" on:click={onClose}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.header.close')} />{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop{position:fixed;inset:0;z-index:40;background:rgb(15 24 45 / 35%)}.sheet{position:fixed;inset:auto 0 0;z-index:41;display:grid;gap:1rem;padding:1rem max(1rem,env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom));border-radius:1.1rem 1.1rem 0 0;background:#fff;box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%);max-height:88dvh;overflow:auto}h2{margin:0;color:#18243d;font-size:1.15rem}.close{display:flex;align-items:center;justify-content:center;gap:.5rem;width:100%;min-height:2.75rem;padding:.5rem .7rem;border:0;border-radius:.7rem;background:#fff7f7!important;border:1px solid #f1c7ca!important;color:#a84a50!important;font:inherit;font-weight:700}
    @media (min-width: 700px) {.sheet{inset:50% auto auto 50%;width:min(38rem,calc(100% - 3rem));max-height:min(82dvh,46rem);padding:1.4rem;border-radius:1.25rem;box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%);transform:translate(-50%,-50%)}}
</style>
