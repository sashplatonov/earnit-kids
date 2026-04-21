<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tShop(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`shop.${key}` as MessageKey, variables);
    }

    $: isOpen = $modalStore.open === 'shop-modal';
    $: modalData = $modalStore.data;
    $: isEdit = modalData?.mode === 'edit';
    $: existingItem = isEdit ? (modalData?.item as Record<string, unknown>) : null;

    let title = '';
    let groupName = '';
    let coins = 50;
    let comment = '';
    let moneyLimit = '';
    let itemType: 'micro' | 'small' | 'large' = 'small';

    $: if ($modalStore.open === 'shop-modal') {
        const _item = $modalStore.data?.mode === 'edit'
            ? ($modalStore.data?.item as Record<string, unknown>)
            : null;
        if (_item) {
            title = ((_item.name ?? _item.title) as string) ?? '';
            groupName = (_item.groupName as string) ?? '';
            coins = ((_item.price ?? _item.coins) as number) ?? 50;
            comment = (_item.comment as string) ?? '';
            moneyLimit = String((_item.moneyLimit as number) ?? '');
            itemType = ((_item.type ?? _item.itemType) as typeof itemType) ?? 'small';
        } else {
            title = ''; groupName = ''; coins = 50; comment = ''; moneyLimit = ''; itemType = 'small';
        }
    }

    function close() { modalStore.close(); }

    async function save() {
        if (!title.trim()) { showToast(tShop('modal.enterTitle'), 'error'); return; }
        const payload = {
            id: existingItem?.id,
            name: title.trim(),
            title: title.trim(),
            groupName: groupName.trim() || null,
            price: Number(coins) || 50,
            coins: Number(coins) || 50,
            comment: comment.trim() || null,
            moneyLimit: moneyLimit ? Number(moneyLimit) : null,
            type: itemType,
            itemType,
        };

        if (isEdit) {
            appStore.setState({
                shopItems: $appStore.shopItems.map(i => i.id == payload.id ? ({ ...i, ...payload } as typeof i) : i)
            });
        } else {
            const newItem = { ...payload, id: (payload.id as number | string | undefined) ?? Date.now() };
            appStore.setState({ shopItems: [...$appStore.shopItems, newItem as typeof $appStore.shopItems[number]] });
        }
        void scheduleSave();
        showToast(isEdit ? tShop('modal.saved') : tShop('modal.added'), 'success');
        close();
    }

    async function deleteItem() {
        if (!existingItem?.id) return;
        if (!confirm(tShop('modal.confirmDelete'))) return;
        appStore.setState({ shopItems: $appStore.shopItems.filter(i => i.id != existingItem!.id) });
        void scheduleSave();
        showToast(tShop('modal.deleted'), 'info');
        close();
    }
</script>

{#if isOpen}
<dialog class="modal" aria-modal="true" id="shop-modal" open>
    <div class="modal__content">
        <h3 id="shop-modal-title">{isEdit ? tShop('modal.titleEdit') : tShop('modal.titleAdd')}</h3>

        <div class="form-group">
            <label for="shop-name">{tShop('modal.nameLabel')}</label>
            <input type="text" class="input" id="shop-name" placeholder={tShop('modal.namePlaceholder')} bind:value={title} />
        </div>
        <div class="form-group">
            <label for="shop-group">{tShop('modal.groupLabel')}</label>
            <input type="text" class="input" id="shop-group" placeholder={tShop('modal.groupPlaceholder')} bind:value={groupName} />
        </div>
        <div class="form-group">
            <label for="shop-price">{tShop('modal.priceLabel')}</label>
            <input type="number" inputmode="numeric" class="input" id="shop-price" min="0" bind:value={coins} />
        </div>
        <div class="form-row">
            <div class="form-group">
                <label for="shop-money-limit">{tShop('modal.moneyLimitLabel')}</label>
                <input type="number" inputmode="numeric" class="input" id="shop-money-limit"
                    placeholder={tShop('modal.noLimitPlaceholder')} min="0" bind:value={moneyLimit} />
            </div>
            <div class="form-group">
                <label for="shop-type">{tShop('modal.typeLabel')}</label>
                <select class="input" id="shop-type" bind:value={itemType}>
                    <option value="micro">{tShop('modal.typeMicro')}</option>
                    <option value="small">{tShop('modal.typeSmall')}</option>
                    <option value="large">{tShop('modal.typeLarge')}</option>
                </select>
            </div>
        </div>
        <div class="form-group">
            <label for="shop-comment">{tShop('modal.commentLabel')}</label>
            <textarea class="input textarea" id="shop-comment" bind:value={comment}></textarea>
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="shop-cancel" on:click={close}>{tShop('modal.cancel')}</button>
            {#if isEdit}
            <button class="btn btn--danger" id="shop-delete" on:click={deleteItem}>{tShop('modal.delete')}</button>
            {/if}
            <button class="btn btn--primary" id="shop-save" on:click={save}>{tShop('modal.save')}</button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" on:click={close} role="presentation"></div>
{/if}
