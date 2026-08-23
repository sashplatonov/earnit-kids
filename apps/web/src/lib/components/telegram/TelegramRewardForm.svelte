<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import type { ShopItem } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { scheduleSave } from '$lib/services/save';
    import { buildShopPayload } from '$lib/telegram/services/shopPayload';
    import { getSemanticGraphic } from './semanticGraphics';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGraphicsPicker from './TelegramGraphicsPicker.svelte';
    import TelegramGroupPicker from './TelegramGroupPicker.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';

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
    let groupPickerOpen = false;
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
            const nextItems = $shopItems.map((entry) => entry.id == item.id ? ({ ...entry, ...payload } as typeof entry) : entry);
            shopItems.set(nextItems);
        } else {
            const newItem = { ...payload, id: Date.now() };
            const nextItems = [...$shopItems, newItem as unknown as typeof $shopItems[number]];
            shopItems.set(nextItems);
        }
        void scheduleSave();
        onSaved();
        onClose();
    }
</script>

{#if open}
    <TelegramBottomSheet open={open} labelledBy="reward-form-title" {onClose}>
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

        <button class="field" id="reward-group" type="button" on:click={() => groupPickerOpen = true}>
            <span class="gico"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', group: groupName })} size={20} label={groupName || $i18n.t('app.telegram.rewardForm.groupPlaceholder')} /></span>
            <span class="grow">{groupName || $i18n.t('app.telegram.rewardForm.groupPlaceholder')}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        {#if error}<p class="error" role="alert">{error}</p>{/if}

        <button class="primary" type="button" on:click={save}><TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.rewardForm.save')} />{$i18n.t('app.telegram.rewardForm.save')}</button>
        <button class="close" type="button" on:click={onClose}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.rewardForm.cancel')} />{$i18n.t('app.telegram.rewardForm.cancel')}</button>
    </TelegramBottomSheet>
{/if}

<TelegramGroupPicker open={groupPickerOpen} groups={suggestions} selected={groupName} title={$i18n.t('app.telegram.groupPicker.title')} onSelect={(group) => groupName = group} onClose={() => groupPickerOpen = false} />
<TelegramGraphicsPicker open={graphicOpen} title={$i18n.t('app.telegram.rewardForm.graphicLabel')} initial={icon} onSelect={(key) => icon = key} onClose={() => graphicOpen = false} />

<style>
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .field { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.9rem; padding:.35rem .6rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; text-align:left; }
    .grow { flex:1; min-width:0; font-weight:600; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .primary { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #f1c7ca; border-radius:.7rem; background:#fff7f7; color:#a84a50; font:inherit; font-weight:600; cursor:pointer; }
</style>
