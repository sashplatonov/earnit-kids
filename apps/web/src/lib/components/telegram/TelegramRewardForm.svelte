<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type ShopItem } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { buildShopPayload } from '$lib/services/shopPayload';
    import { getSemanticGraphic } from './semanticGraphics';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGraphicsPicker from './TelegramGraphicsPicker.svelte';

    export let open = false;
    export let item: ShopItem | null = null;
    export let groupSuggestions: string[] = [];
    export let onClose: () => void = () => {};
    export let onSaved: () => void = () => {};

    const i18n = useI18n();

    let title = '';
    let groupName = '';
    let price = 50;
    let icon: string | null = null;
    let graphicOpen = false;
    let error = '';

    $: isEdit = item != null;
    $: currentGraphic = getSemanticGraphic(icon);

    $: if (open && item) {
        title = item.name ?? '';
        groupName = item.groupName ?? '';
        price = item.price ?? 50;
        icon = item.icon ?? null;
        error = '';
    } else if (open && !item) {
        title = ''; groupName = ''; price = 50; icon = null; error = '';
    }

    $: suggestions = [...new Set(groupSuggestions.filter(Boolean))];

    function save() {
        if (!title.trim()) { error = $i18n.t('app.telegram.rewardForm.nameRequired'); return; }
        error = '';
        const payload = buildShopPayload({
            id: item?.id,
            title,
            groupName,
            price,
            comment: '',
            freqLimit: '',
            freqPeriod: 'week',
            moneyLimit: '',
            itemType: 'micro',
            icon,
        });

        if (item) {
            appStore.setState({
                shopItems: $appStore.shopItems.map((entry) => entry.id == item.id ? ({ ...entry, ...payload } as typeof entry) : entry),
            });
        } else {
            const newItem = { ...payload, id: Date.now() };
            appStore.setState({ shopItems: [...$appStore.shopItems, newItem as unknown as typeof $appStore.shopItems[number]] });
        }
        void scheduleSave();
        onSaved();
        onClose();
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="reward-form-title" tabindex="-1">
        <h2 id="reward-form-title">{isEdit ? $i18n.t('app.telegram.rewardForm.editTitle') : $i18n.t('app.telegram.rewardForm.addTitle')}</h2>

        <label for="reward-name">{$i18n.t('app.telegram.rewardForm.nameLabel')}</label>
        <input id="reward-name" class="input" bind:value={title} placeholder={$i18n.t('app.telegram.rewardForm.namePlaceholder')} />

        <label for="reward-graphic">{$i18n.t('app.telegram.rewardForm.graphicLabel')}</label>
        <button class="field" id="reward-graphic" type="button" on:click={() => graphicOpen = true}>
            <span class="gico"><TelegramIcon name={currentGraphic.key} size={20} label={currentGraphic.label} /></span>
            <span class="grow">{currentGraphic.label}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        <label for="reward-price">{$i18n.t('app.telegram.rewardForm.priceLabel')}</label>
        <input id="reward-price" class="input" type="number" inputmode="numeric" bind:value={price} min="0" />

        <label for="reward-group">{$i18n.t('app.telegram.rewardForm.groupLabel')}</label>
        <input id="reward-group" class="input" list="reward-group-suggestions" bind:value={groupName} placeholder={$i18n.t('app.telegram.rewardForm.groupPlaceholder')} />
        <datalist id="reward-group-suggestions">
            {#each suggestions as group (group)}<option value={group}></option>{/each}
        </datalist>

        {#if error}<p class="error" role="alert">{error}</p>{/if}

        <button class="primary" type="button" on:click={save}>{$i18n.t('app.telegram.rewardForm.save')}</button>
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.rewardForm.cancel')}</button>
    </div>
{/if}

<TelegramGraphicsPicker open={graphicOpen} title={$i18n.t('app.telegram.rewardForm.graphicLabel')} initial={icon} onSelect={(key) => icon = key} onClose={() => graphicOpen = false} />

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .field { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.9rem; padding:.35rem .6rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; text-align:left; }
    .grow { flex:1; min-width:0; font-weight:600; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
