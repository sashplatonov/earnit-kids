<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type CatalogRewardTemplate, type CatalogTaskTemplate } from '$lib/stores/app';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { formatFrequency, isAlreadyAdded, stripEmoji } from '$lib/telegram/services/catalogFilter';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let kind: 'task' | 'reward' = 'task';
    export let template: CatalogTaskTemplate | CatalogRewardTemplate | null = null;
    export let onAdd: (template: CatalogTaskTemplate | CatalogRewardTemplate) => void = () => {};
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    $: familyItems = kind === 'task' ? $appStore.tasks : $shopItems;
    $: added = template != null && isAlreadyAdded(template, familyItems);
    $: amount = template != null
        ? (kind === 'task' ? (template as CatalogTaskTemplate).coins : (template as CatalogRewardTemplate).price)
        : 0;
    $: freq = template != null ? formatFrequency(template.frequencyLimit, template.frequencyPeriod) : '';
    $: ageRange = template != null
        ? $i18n.t('app.telegram.readyCatalog.ageRange', { min: template.minAge ?? 6, max: template.maxAge ?? 14 })
        : '';
</script>

{#if open && template}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="catalog-details-title" tabindex="-1">
        <h2 id="catalog-details-title">{template.title}</h2>

        <div class="groupbox">
            <div class="row"><div class="grow"><div class="meta">{kind === 'task' ? $i18n.t('app.telegram.readyCatalog.coins', { count: amount }) : $i18n.t('app.telegram.readyCatalog.price', { count: amount })}</div></div></div>
            <div class="row"><div class="grow"><div class="meta">{$i18n.t('app.telegram.readyCatalog.frequencyLabel')}</div><div class="title">{freq}</div></div></div>
            <div class="row"><div class="grow"><div class="meta">{$i18n.t('app.telegram.readyCatalog.age')}</div><div class="title">{ageRange}</div></div></div>
        </div>

        {#if template.comment}
            <h3>{$i18n.t('app.telegram.readyCatalog.whenCounts')}</h3>
            <div class="note">{template.comment}</div>
        {/if}

        {#if added}
            <button class="added" type="button" disabled><TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.added')} />{$i18n.t('app.telegram.readyCatalog.added')}</button>
        {:else}
            <button class="primary" type="button" on:click={() => onAdd(template)}>
                <TelegramIcon name="add" size={18} label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.addToMyTasks') : $i18n.t('app.telegram.readyCatalog.addToMyRewards')} />
                {kind === 'task' ? $i18n.t('app.telegram.readyCatalog.addToMyTasks') : $i18n.t('app.telegram.readyCatalog.addToMyRewards')}
            </button>
        {/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.readyCatalog.done')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    h3 { margin:1rem 0 .4rem; color:#33415f; font-size:.85rem; font-weight:700; }
    .groupbox { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; min-height:2.75rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .grow { flex:1; min-width:0; }
    .meta { color:#66718a; font-size:.8rem; }
    .title { color:#18243d; font-weight:600; }
    .note { background:#f1f4f8; border-radius:.7rem; padding:.6rem .7rem; color:#5b6679; font-size:.85rem; line-height:1.45; }
    .primary { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .added { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #cbe8d7; border-radius:.7rem; background:#eaf7ef; color:#168552; font:inherit; font-weight:750; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
