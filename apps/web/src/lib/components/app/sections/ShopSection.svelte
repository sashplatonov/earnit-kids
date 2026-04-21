<script lang="ts">
    import { browser } from '$app/environment';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { buyItem, requestItem, saveChildGroupOrder } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import {
        applyGroupOrderToChildren,
        getEffectiveGroupOrder,
        hasSavedGroupOrder,
        normalizeGroupLabel,
        orderGroups,
        sortItemsByGroup,
    } from '$lib/services/groupOrder';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { showToast } from '$lib/stores/toasts';
    import { page } from '$app/stores';

    const i18n = useI18n();

    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let viewMode: CardViewMode = 'grid';
    let loadedViewRole: CardViewRole | null = null;

    function tShop(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`shop.${key}` as MessageKey, variables);
    }

    $: shopItems = $appStore.shopItems;
    $: isAdmin = $appStore.isAdmin;
    $: balance = $appStore.balance;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;

    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = (($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null);
    $: rawGroups = [...new Set(shopItems.map((item) => normalizeGroupLabel(item.groupName)))];
    $: groups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'shop', isAdmin));
    $: hasStoredGroupOrder = hasSavedGroupOrder(currentChild, 'shop', isAdmin);
    $: if (browser && loadedViewRole !== viewRole) {
        viewMode = loadCardViewMode('shop', viewRole);
        loadedViewRole = viewRole;
    }
    
    // Read selected group from query parameter
    $: selectedGroup = ($page.url.searchParams.get('group') ?? '');
    $: if (selectedGroup && !groups.includes(selectedGroup)) {
        const url = new URL($page.url);
        url.searchParams.delete('group');
        history.replaceState(null, '', url);
    }

    $: visibleItems = selectedGroup
        ? shopItems.filter((item) => normalizeGroupLabel(item.groupName) === selectedGroup)
        : sortItemsByGroup(shopItems, groups, (item) => normalizeGroupLabel(item.groupName));

    function missingCoins(price: number) {
        return Math.max(price - balance, 0);
    }

    function formatFrequency(frequency: { limit?: number; period?: string } | null | undefined) {
        const limit = frequency?.limit;
        const period = frequency?.period;

        if (!limit || !period) {
            return '';
        }

        const periodMap: Record<string, string> = {
            day: 'frequencyDay',
            week: 'frequencyWeek',
            month: 'frequencyMonth',
            year: 'frequencyYear',
        };
        const numericLimit = Number(limit);
        const pluralCategory = new Intl.PluralRules($i18n.locale).select(numericLimit);
        const periodKey = periodMap[period];

        if (!periodKey) {
            return tShop('frequencyFallback', { limit: $i18n.formatNumber(numericLimit) });
        }

        return tShop(`${periodKey}.${pluralCategory}`, { limit: $i18n.formatNumber(numericLimit) });
    }

    async function handleBuy(itemId: unknown) {
        const childId = resolvedChildId;
        const item = shopItems.find((entry) => entry.id == itemId);
        if (!item) return;
        if (isAdmin) {
            if (balance < (item.price as number)) {
                showToast(tShop('toasts.notEnoughCoins'), 'error');
                return;
            }
            const res = await buyItem(itemId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(tShop('toasts.bought', { name: item.name }), 'success');
            }
        } else {
            const result = await requestItem(itemId);
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tShop('toasts.requestSent'), 'success');
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

    async function persistGroupOrder(nextOrder: string[]) {
        if (resolvedChildId == null) {
            showToast(tShop('toasts.selectChildFirst'), 'error');
            return;
        }

        isSavingGroupOrder = true;
        const result = await saveChildGroupOrder(resolvedChildId, 'shop', nextOrder);
        if (result.ok) {
            appStore.update((state) => ({
                ...state,
                children: applyGroupOrderToChildren(state.children, resolvedChildId, 'shop', isAdmin, nextOrder),
            }));
            isEditingGroupOrder = false;
            showToast(isAdmin ? tShop('toasts.groupOrderSavedAdmin') : tShop('toasts.groupOrderSavedChild'), 'success');
        } else {
            showToast(result.error, 'error');
        }
        isSavingGroupOrder = false;
    }

    async function handleGroupOrderSave(event: CustomEvent<string[]>) {
        await persistGroupOrder(event.detail);
    }

    async function handleGroupOrderReset() {
        await persistGroupOrder([]);
    }

    function setViewMode(nextMode: CardViewMode) {
        viewMode = nextMode;
        saveCardViewMode('shop', viewRole, nextMode);
    }
</script>

<section class="section" id="shop-section">
    <div class="section__header">
        <div class="section__header-titles">
            <h2>
                <span class="gamified-icon icon-shop" aria-hidden="true"
                    style="width: 1.5rem; height: 1.5rem; margin-right: 0.5rem; vertical-align: middle;"></span>
                {tShop('section.title')}
            </h2>
            <p class="section__subtitle">{tShop('section.subtitle')}</p>
        </div>
        <div class="section__header-actions">
            <div class="view-toggle" role="group" aria-label={tShop('section.viewAria')}>
                <button
                    type="button"
                    class="view-toggle__button"
                    class:view-toggle__button--active={viewMode === 'grid'}
                    aria-pressed={viewMode === 'grid'}
                    on:click={() => setViewMode('grid')}
                >
                    {tShop('section.viewGrid')}
                </button>
                <button
                    type="button"
                    class="view-toggle__button"
                    class:view-toggle__button--active={viewMode === 'list'}
                    aria-pressed={viewMode === 'list'}
                    on:click={() => setViewMode('list')}
                >
                    {tShop('section.viewList')}
                </button>
            </div>
            {#if isAdmin}
            <button class="btn btn--add" id="add-shop-btn" on:click={openAddShopItem}>{tShop('section.add')}</button>
            {/if}
        </div>
    </div>

    {#if groups.length > 1}
    <nav class="group-nav" id="shop-group-nav">
        <div class="group-nav__scroll">
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === ''} on:click={() => {
                const url = new URL($page.url);
                url.searchParams.delete('group');
                history.pushState(null, '', url);
            }}>
                {tShop('section.all')}
            </button>
            {#each groups as group (group)}
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === group}
                on:click={() => {
                    const url = new URL($page.url);
                    url.searchParams.set('group', group);
                    history.pushState(null, '', url);
                }}>{group}</button>
            {/each}
        </div>
    </nav>

    <GroupOrderEditor
        bind:isOpen={isEditingGroupOrder}
        {isAdmin}
        isSaving={isSavingGroupOrder}
        hasStoredOrder={hasStoredGroupOrder}
        {groups}
        title={tShop('groupOrder.title')}
        hintAdmin={tShop('groupOrder.hintAdmin')}
        hintChild={tShop('groupOrder.hintChild')}
        descriptionAdmin={tShop('groupOrder.descriptionAdmin')}
        descriptionChild={tShop('groupOrder.descriptionChild')}
        on:save={handleGroupOrderSave}
        on:reset={handleGroupOrderReset}
    />
    {/if}

    {#if visibleItems.length > 0}
    <div class="cards" class:cards--list={viewMode === 'list'} id="shop-list">
        {#each visibleItems as item (item.id)}
        <div class="card card--shop shop-card" class:card--affordable={balance >= itemPrice(item)} class:card--disabled={balance < itemPrice(item)} class:shop-card--list={viewMode === 'list'}>
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{item.groupName ?? tShop('section.noGroup')}</span>
                {#if formatFrequency(item.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(item.frequency)}</span>
                {/if}
                <span class:card__status--available={balance >= itemPrice(item)} class:card__status--locked={balance < itemPrice(item)} class="card__status">
                    {#if balance >= itemPrice(item)}
                        {#if isAdmin}
                        {tShop('section.availableAdmin')}
                        {:else}
                        {tShop('section.availableChild')}
                        {/if}
                    {:else}
                        {tShop('section.missingCoins', { amount: $i18n.formatNumber(missingCoins(itemPrice(item))) })}
                    {/if}
                </span>
            </div>
            <div class="shop-card__layout">
                <div class="shop-card__main">
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
                    <p class="card__comment">{tShop('section.defaultComment')}</p>
                    {/if}
                </div>
                <div class="shop-card__side">
                    <div class="card__meta">
                        {#if item.moneyLimit != null}
                        <span class="card__meta-item">{tShop('section.moneyLimit', { amount: $i18n.formatNumber(item.moneyLimit) })}</span>
                        {/if}
                    </div>
                    <div class="card__actions">
                        {#if isAdmin}
                        <button class="btn btn--primary btn--small admin-only" data-shop-action="buy" disabled={balance < itemPrice(item)}
                            on:click={() => handleBuy(item.id)}>
                            {tShop('actions.buy')}
                        </button>
                        <button class="btn btn--secondary btn--small admin-only" data-shop-action="edit" on:click={() => openEditShopItem(item)}>
                            {tShop('actions.edit')}
                        </button>
                        {:else}
                        <button class="btn btn--primary" data-shop-action="request" disabled={balance < itemPrice(item)}
                            on:click={() => handleBuy(item.id)}>
                            {tShop('actions.request')}
                        </button>
                        {/if}
                    </div>
                </div>
            </div>
        </div>
        {/each}
    </div>
    {:else}
    <div class="empty-state" id="shop-empty">
        <span class="empty-state__icon">
            <span class="gamified-icon icon-shop" aria-hidden="true"></span>
        </span>
        <p class="empty-state__title">{tShop('section.emptyTitle')}</p>
        <p class="empty-state__hint">
            {#if isAdmin}{tShop('section.emptyAdminHint')}{:else}{tShop('section.emptyChildHint')}{/if}
        </p>
        {#if isAdmin}
        <div class="empty-state__actions">
            <button class="btn btn--add" type="button" on:click={openAddShopItem}>{tShop('section.addReward')}</button>
        </div>
        {/if}
    </div>
    {/if}
</section>

<style>
    .section__header-actions {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        flex-wrap: wrap;
    }

    .view-toggle {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem;
        border-radius: 999px;
        border: 1px solid rgba(120, 140, 175, 0.18);
        background: rgba(246, 248, 252, 0.92);
    }

    .view-toggle__button {
        border: 0;
        background: transparent;
        color: rgba(54, 68, 96, 0.72);
        font: inherit;
        font-size: 0.84rem;
        font-weight: 700;
        line-height: 1;
        padding: 0.62rem 0.9rem;
        border-radius: 999px;
        cursor: pointer;
        transition: background-color 120ms ease, color 120ms ease, box-shadow 120ms ease;
    }

    .view-toggle__button--active {
        background: linear-gradient(135deg, rgba(87, 121, 206, 0.18), rgba(84, 179, 160, 0.2));
        color: #20304e;
        box-shadow: inset 0 0 0 1px rgba(87, 121, 206, 0.14);
    }

    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.9rem;
    }

    .shop-card__layout {
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
    }

    .shop-card__side {
        display: flex;
        flex-direction: column;
        gap: 0.8rem;
    }

    .shop-card--list {
        padding: 1rem 1.05rem;
    }

    .shop-card--list .card__comment {
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        overflow: hidden;
    }

    .shop-card--list .card__actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.55rem;
    }

    .shop-card--list .card__actions .btn {
        flex: 1 1 10rem;
    }

    @media (min-width: 720px) {
        .shop-card--list .shop-card__layout {
            display: grid;
            grid-template-columns: minmax(0, 1fr) minmax(14rem, auto);
            align-items: center;
            gap: 1rem 1.25rem;
        }

        .shop-card--list .shop-card__side {
            align-items: flex-end;
            text-align: right;
        }

        .shop-card--list .card__meta {
            justify-content: flex-end;
        }

        .shop-card--list .card__actions {
            justify-content: flex-end;
        }
    }

    @media (max-width: 640px) {
        .section__header-actions {
            width: 100%;
            justify-content: space-between;
        }

        .view-toggle {
            width: 100%;
            justify-content: stretch;
        }

        .view-toggle__button {
            flex: 1 1 0;
            text-align: center;
        }
    }
</style>
