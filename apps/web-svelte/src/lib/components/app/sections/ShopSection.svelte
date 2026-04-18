<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { buyItem, requestItem } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    $: shopItems = $appStore.shopItems;
    $: isAdmin = $appStore.isAdmin;
    $: balance = $appStore.balance;

    $: groups = [...new Set(shopItems.map(i => i.groupName ?? 'Без группы'))];
    let selectedGroup = '';

    $: visibleItems = selectedGroup
        ? shopItems.filter(i => (i.groupName ?? 'Без группы') === selectedGroup)
        : shopItems;

    async function handleBuy(itemId: unknown) {
        const childId = $appStore.currentChildId;
        const item = shopItems.find(i => i.id == itemId);
        if (!item) return;
        if (isAdmin) {
            if (balance < item.coins) {
                showToast('Не хватает монет!', 'error');
                return;
            }
            const res = await buyItem(itemId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(`Куплено: ${item.title}`, 'success');
            }
        } else {
            const res = await requestItem(itemId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast('Заявка на покупку отправлена!', 'success');
            }
        }
    }

    function openAddShopItem() {
        modalStore.open('shop-modal', { mode: 'add' });
    }

    function openEditShopItem(item: unknown) {
        modalStore.open('shop-modal', { mode: 'edit', item });
    }
</script>

<section class="section" id="shop-section">
    <div class="section__header">
        <div class="section__header-titles">
            <h2>
                <span class="gamified-icon icon-shop" aria-hidden="true"
                    style="width: 1.5rem; height: 1.5rem; margin-right: 0.5rem; vertical-align: middle;"></span>
                Магазин наград
            </h2>
            <p class="section__subtitle">Обменивайте честно заработанные монетки на призы</p>
        </div>
        {#if isAdmin}
        <div class="section__buttons admin-only">
            <button class="btn btn--add" id="add-shop-btn" on:click={openAddShopItem}>+ Добавить</button>
        </div>
        {/if}
    </div>

    {#if groups.length > 1}
    <nav class="group-nav" id="shop-group-nav">
        <button class="group-nav__btn" class:active={selectedGroup === ''} on:click={() => selectedGroup = ''}>
            Все
        </button>
        {#each groups as group (group)}
        <button class="group-nav__btn" class:active={selectedGroup === group}
            on:click={() => selectedGroup = group}>{group}</button>
        {/each}
    </nav>
    {/if}

    {#if visibleItems.length > 0}
    <div class="cards" id="shop-list">
        {#each visibleItems as item (item.id)}
        <div class="card shop-card">
            <div class="card__header">
                <h3 class="card__title">{item.title}</h3>
                <div class="card__icon">
                    <span class="item-coins">{item.coins} <span class="gamified-icon icon-coin" aria-hidden="true"></span></span>
                </div>
            </div>
            {#if item.comment}
            <p class="card__comment">{item.comment}</p>
            {/if}
            <div class="card__actions">
                {#if isAdmin}
                <button class="btn btn--secondary btn--small admin-only" on:click={() => openEditShopItem(item)}>
                    Изменить
                </button>
                {:else}
                <button class="btn btn--primary" disabled={balance < item.coins}
                    on:click={() => handleBuy(item.id)}>
                    Запросить
                </button>
                {/if}
            </div>
        </div>
        {/each}
    </div>
    {:else}
    <div class="empty-state" id="shop-empty">
        <span class="empty-state__icon">
            <span class="gamified-icon icon-shop" aria-hidden="true"></span>
        </span>
        <p class="empty-state__title">Магазин пока пуст</p>
        <p class="empty-state__hint">
            {#if isAdmin}Добавьте первую награду, и магазин сразу станет понятной целью для ребенка.{:else}Скоро появятся призы!{/if}
        </p>
    </div>
    {/if}
</section>
