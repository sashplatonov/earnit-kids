<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { showToast } from '$lib/stores/toasts';

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

    $: if (isOpen) {
        if (existingItem) {
            title = (existingItem.title as string) ?? '';
            groupName = (existingItem.groupName as string) ?? '';
            coins = (existingItem.coins as number) ?? 50;
            comment = (existingItem.comment as string) ?? '';
            moneyLimit = String((existingItem.moneyLimit as number) ?? '');
            itemType = (existingItem.itemType as typeof itemType) ?? 'small';
        } else {
            title = ''; groupName = ''; coins = 50; comment = ''; moneyLimit = ''; itemType = 'small';
        }
    }

    function close() { modalStore.close(); }

    async function save() {
        if (!title.trim()) { showToast('Введите название', 'error'); return; }
        const payload = {
            id: existingItem?.id,
            title: title.trim(),
            groupName: groupName.trim() || null,
            coins: Number(coins) || 50,
            comment: comment.trim() || null,
            moneyLimit: moneyLimit ? Number(moneyLimit) : null,
            itemType,
        };

        if (isEdit) {
            appStore.setState({
                shopItems: $appStore.shopItems.map(i => i.id == payload.id ? ({ ...i, ...payload } as typeof i) : i)
            });
        } else {
            const newItem = { ...payload, id: payload.id ?? Date.now() };
            appStore.setState({ shopItems: [...$appStore.shopItems, newItem as typeof $appStore.shopItems[number]] });
        }
        void scheduleSave();
        showToast(isEdit ? 'Товар сохранён' : 'Товар добавлен', 'success');
        close();
    }

    async function deleteItem() {
        if (!existingItem?.id) return;
        if (!confirm('Удалить товар?')) return;
        appStore.setState({ shopItems: $appStore.shopItems.filter(i => i.id != existingItem!.id) });
        void scheduleSave();
        showToast('Товар удалён', 'info');
        close();
    }
</script>

{#if isOpen}
<dialog class="modal" role="dialog" aria-modal="true" id="shop-modal" open>
    <div class="modal__content">
        <h3 id="shop-modal-title">{isEdit ? 'Редактировать товар' : 'Добавить товар'}</h3>

        <div class="form-group">
            <label for="shop-name">Название</label>
            <input type="text" class="input" id="shop-name" placeholder="Час игры на планшете" bind:value={title} />
        </div>
        <div class="form-group">
            <label for="shop-group">Группа</label>
            <input type="text" class="input" id="shop-group" placeholder="Напр: Развлечения, Мелочи..." bind:value={groupName} />
        </div>
        <div class="form-group">
            <label for="shop-price">Цена (монеты)</label>
            <input type="number" inputmode="numeric" class="input" id="shop-price" min="0" bind:value={coins} />
        </div>
        <div class="form-row">
            <div class="form-group">
                <label for="shop-money-limit">Лимит в деньгах</label>
                <input type="number" inputmode="numeric" class="input" id="shop-money-limit"
                    placeholder="Без лимита" min="0" bind:value={moneyLimit} />
            </div>
            <div class="form-group">
                <label for="shop-type">Тип</label>
                <select class="input" id="shop-type" bind:value={itemType}>
                    <option value="micro">Микро</option>
                    <option value="small">Малая</option>
                    <option value="large">Крупная (1/мес)</option>
                </select>
            </div>
        </div>
        <div class="form-group">
            <label for="shop-comment">Комментарий</label>
            <textarea class="input textarea" id="shop-comment" bind:value={comment}></textarea>
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="shop-cancel" on:click={close}>Отмена</button>
            {#if isEdit}
            <button class="btn btn--danger" id="shop-delete" on:click={deleteItem}>Удалить</button>
            {/if}
            <button class="btn btn--primary" id="shop-save" on:click={save}>Сохранить</button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" on:click={close} role="presentation"></div>
{/if}
