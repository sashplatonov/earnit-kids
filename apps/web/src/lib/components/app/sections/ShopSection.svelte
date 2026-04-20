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

    function missingCoins(price: number) {
        return Math.max(price - balance, 0);
    }

    function formatFrequency(frequency: { limit?: number; period?: string } | null | undefined) {
        const limit = frequency?.limit;
        const period = frequency?.period;

        if (!limit || !period) {
            return '';
        }

        const periodLabels: Record<string, string> = {
            day: 'день',
            week: 'неделю',
            month: 'месяц',
            year: 'год',
        };

        return `${limit} раз(а) в ${periodLabels[period] ?? period}`;
    }

    async function handleBuy(itemId: unknown) {
        const childId = $appStore.currentChildId;
        const item = shopItems.find(i => i.id == itemId);
        if (!item) return;
        if (isAdmin) {
            if (balance < (item.price as number)) {
                showToast('Не хватает монет!', 'error');
                return;
            }
            const res = await buyItem(itemId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(`Куплено: ${item.name}`, 'success');
            }
        } else {
            const result = await requestItem(itemId);
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast('Заявка на покупку отправлена!', 'success');
                return;
            }

            showToast(result.error, 'error');
        }
    }

    function openAddShopItem() {
        modalStore.open('shop-modal', { mode: 'add' });
    }

    function openEditShopItem(item: unknown) {
        modalStore.open('shop-modal', { mode: 'edit', item });
    }

    function itemPrice(item: { price?: unknown }) {
        return Number(item.price ?? 0);
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
        <div class="group-nav__scroll">
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === ''} on:click={() => selectedGroup = ''}>
                Все
            </button>
            {#each groups as group (group)}
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === group}
                on:click={() => selectedGroup = group}>{group}</button>
            {/each}
        </div>
    </nav>
    {/if}

    {#if visibleItems.length > 0}
    <div class="cards" id="shop-list">
        {#each visibleItems as item (item.id)}
        <div class="card card--shop shop-card" class:card--affordable={balance >= itemPrice(item)} class:card--disabled={balance < itemPrice(item)}>
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{item.groupName ?? 'Без группы'}</span>
                {#if formatFrequency(item.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(item.frequency)}</span>
                {/if}
                <span class:card__status--available={balance >= itemPrice(item)} class:card__status--locked={balance < itemPrice(item)} class="card__status">
                    {#if balance >= itemPrice(item)}
                        {#if isAdmin}
                        Можно купить
                        {:else}
                        Можно запросить
                        {/if}
                    {:else}
                        Еще {missingCoins(itemPrice(item))}
                    {/if}
                </span>
            </div>
            <div class="card__header">
                <h3 class="card__title">{item.name}</h3>
                <div class="card__coins item-coins">
                    <span class="gamified-icon icon-coin" aria-hidden="true"></span>
                    <span>{item.price}</span>
                </div>
            </div>
            {#if item.comment}
            <p class="card__comment">{item.comment}</p>
            {:else}
            <p class="card__comment">Награда, которую можно честно заработать и обсудить вместе с родителями.</p>
            {/if}
            {#if item.moneyLimit != null}
            <div class="card__meta">
                <span class="card__meta-item">Лимит: {item.moneyLimit} 💶</span>
            </div>
            {/if}
            <div class="card__actions">
                {#if isAdmin}
                <button class="btn btn--primary btn--small admin-only" disabled={balance < itemPrice(item)}
                    on:click={() => handleBuy(item.id)}>
                    Купить
                </button>
                <button class="btn btn--secondary btn--small admin-only" on:click={() => openEditShopItem(item)}>
                    Изменить
                </button>
                {:else}
                <button class="btn btn--primary" disabled={balance < itemPrice(item)}
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
        {#if isAdmin}
        <div class="empty-state__actions">
            <button class="btn btn--add" type="button" on:click={openAddShopItem}>Добавить награду</button>
        </div>
        {/if}
    </div>
    {/if}
</section>
