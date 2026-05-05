<script lang="ts">
    import { browser } from '$app/environment';
    import { onMount } from 'svelte';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { buyItem, requestItemWithNote, saveChildGroupOrder } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import {
        applyGroupOrderToChildren,
        getEffectiveGroupOrder,
        normalizeGroupLabel,
        orderGroups,
        sortItemsByGroup,
    } from '$lib/services/groupOrder';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let selectedGroup = '';
    let viewMode: CardViewMode = 'list';
    let groupOrderEditor: { openEditor: () => void } | null = null;
    const loadedViewRole: { value: CardViewRole | null } = { value: null };

    function tShop(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`shop.${key}` as MessageKey, variables);
    }

    $: shopItems = $appStore.shopItems;
    $: isAdmin = $appStore.isAdmin;
    $: balance = $appStore.balance;
    $: isLoading = $appStore.isLoading;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;

    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = (($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null);
    $: rawGroups = [...new Set(shopItems.map((item) => normalizeGroupLabel(item.groupName)))];
    $: groups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'shop', isAdmin));
    $: if (browser && loadedViewRole.value !== viewRole) {
        viewMode = loadCardViewMode('shop', viewRole);
        loadedViewRole.value = viewRole;
    }

    $: if (selectedGroup && groups.length > 0 && !groups.includes(selectedGroup)) {
        setSelectedGroup('', { replace: true });
    }

    $: visibleItems = selectedGroup
        ? shopItems
              .filter((item) => normalizeGroupLabel(item.groupName) === selectedGroup)
              .sort((a, b) => {
                  // Blocked items go to the bottom
                  const aActive = isItemActive(a);
                  const bActive = isItemActive(b);
                  if (aActive && !bActive) return -1;
                  if (!aActive && bActive) return 1;
                  if (!aActive && !bActive) return 0;
                  // Both active: sort by price (descending) as a proxy for value
                  const aPrice = itemPrice(a);
                  const bPrice = itemPrice(b);
                  if (bPrice !== aPrice) return bPrice - aPrice;
                  return 0;
              })
        : sortItemsByGroup(shopItems, groups, (item) => normalizeGroupLabel(item.groupName));

    onMount(() => {
        selectedGroup = readSelectedGroupFromLocation();

        const handlePopState = () => {
            selectedGroup = readSelectedGroupFromLocation();
        };

        window.addEventListener('popstate', handlePopState);

        return () => {
            window.removeEventListener('popstate', handlePopState);
        };
    });

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
            const note = await requestNote({
                title: tShop('requestNoteModal.title'),
                description: tShop('requestNoteModal.description', { title: String(item.name ?? '') }),
                placeholder: tShop('requestNoteModal.placeholder'),
                saveLabel: tShop('requestNoteModal.save'),
                skipLabel: tShop('requestNoteModal.skip'),
            });
            const result = await requestItemWithNote(itemId, note);
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

    function isItemActive(item: { isActive?: unknown }) {
        return item.isActive !== false;
    }

    function isItemAffordable(item: { price?: unknown }) {
        return balance >= itemPrice(item);
    }

    function availabilityLabel(item: { price?: unknown; isActive?: unknown }) {
        if (!isItemActive(item)) {
            return tShop('section.blocked');
        }
        return isItemAffordable(item)
            ? (isAdmin ? tShop('section.availableAdmin') : tShop('section.availableChild'))
            : tShop('section.missingCoins', { amount: $i18n.formatNumber(missingCoins(itemPrice(item))) });
    }

    function requestNote(options: { title: string; description: string; placeholder: string; saveLabel: string; skipLabel: string }) {
        return new Promise<string>((resolve) => {
            modalStore.open('request-note-modal', {
                ...options,
                onSubmit: (note: string) => resolve(note),
                onSkip: () => resolve(''),
            });
        });
    }

    function shopMoneyPriceLabel(item: { moneyLimit?: number | null }) {
        return item.moneyLimit != null
            ? `${$i18n.formatNumber(item.moneyLimit)} 💶`
            : '';
    }

    function shopCompactChips(item: {
        isActive?: unknown;
        groupName?: string | null;
        frequency?: { limit?: number; period?: string } | null;
        moneyLimit?: number | null;
        price?: unknown;
    }): CardHeaderChip[] {
        const chips: CardHeaderChip[] = [
            { label: item.groupName ?? tShop('section.noGroup'), className: 'card__compact-chip--group' },
        ];
        const frequency = formatFrequency(item.frequency);

        if (frequency) chips.push({ label: frequency });
        chips.push({
            label: availabilityLabel(item),
            className: isItemActive(item) && isItemAffordable(item)
                ? 'card__compact-chip--status card__compact-chip--status-available'
                : 'card__compact-chip--status card__compact-chip--status-locked',
        });

        return chips;
    }

    function readSelectedGroupFromLocation(): string {
        if (!browser) {
            return '';
        }

        return new URL(window.location.href).searchParams.get('group') ?? '';
    }

    function syncSelectedGroupUrl(nextGroup: string, replace = false) {
        if (!browser) {
            return;
        }

        const url = new URL(window.location.href);
        if (nextGroup) {
            url.searchParams.set('group', nextGroup);
        } else {
            url.searchParams.delete('group');
        }

        if (replace) {
            history.replaceState(history.state, '', url);
        } else {
            history.pushState(history.state, '', url);
        }
    }

    function setSelectedGroup(nextGroup: string, options?: { replace?: boolean }) {
        const resolvedGroup = groups.includes(nextGroup) ? nextGroup : '';
        const currentGroup = readSelectedGroupFromLocation();

        selectedGroup = resolvedGroup;
        if (currentGroup === resolvedGroup) {
            return;
        }

        syncSelectedGroupUrl(resolvedGroup, options?.replace ?? false);
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

    function setViewMode(nextMode: CardViewMode) {
        viewMode = nextMode;
        saveCardViewMode('shop', viewRole, nextMode);
    }

    function openGroupOrderEditor() {
        groupOrderEditor?.openEditor();
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
        <SectionHeaderControls
            {isAdmin}
            addLabel={tShop('section.add')}
            addId="add-shop-btn"
            {viewMode}
            viewAriaLabel={tShop('section.viewAria')}
            gridLabel={tShop('section.viewGrid')}
            listLabel={tShop('section.viewList')}
            orderLabel={isAdmin ? $i18n.t('app.groupOrder.configureAdmin') : $i18n.t('app.groupOrder.configureChild')}
            hasGroups={groups.length > 1}
            {isEditingGroupOrder}
            {isSavingGroupOrder}
            on:add={openAddShopItem}
            on:editOrder={openGroupOrderEditor}
            on:viewMode={(event) => setViewMode(event.detail)}
        />
    </div>

    {#if groups.length > 1}
    <nav class="group-nav" id="shop-group-nav">
        <div class="group-nav__scroll">
            <button type="button" class="group-nav__tab" class:group-nav__tab--active={selectedGroup === ''} on:click={() => setSelectedGroup('')}>
                {tShop('section.all')}
            </button>
            {#each groups as group (group)}
            <button type="button" class="group-nav__tab" class:group-nav__tab--active={selectedGroup === group}
                on:click={() => setSelectedGroup(group)}>{group}</button>
            {/each}
        </div>
    </nav>

    <GroupOrderEditor
        bind:this={groupOrderEditor}
        bind:isOpen={isEditingGroupOrder}
        {isAdmin}
        isSaving={isSavingGroupOrder}
        {groups}
        title={tShop('groupOrder.title')}
        descriptionAdmin={tShop('groupOrder.descriptionAdmin')}
        descriptionChild={tShop('groupOrder.descriptionChild')}
        on:save={handleGroupOrderSave}
    />
    {/if}

    {#if isLoading}
    <div class="cards cards--skeleton-shop" id="shop-skeleton">
        {#each { length: 3 } as _, i (i)}
        <div class="card card--shop card--skeleton-shop">
            <div class="card__badge-row">
                <span class="skel-shop skel-shop--badge">&nbsp;</span>
                <span class="skel-shop skel-shop--badge skel-shop--badge-sm">&nbsp;</span>
            </div>
            <div class="shop-card__layout">
                <div class="shop-card__main">
                    <div class="skel-shop skel-shop--title">&nbsp;</div>
                    <div class="skel-shop skel-shop--text">&nbsp;</div>
                    <div class="skel-shop skel-shop--text skel-shop--text-short">&nbsp;</div>
                </div>
                <div class="shop-card__side">
                    <div class="skel-shop skel-shop--button">&nbsp;</div>
                </div>
            </div>
        </div>
        {/each}
    </div>
    {:else if visibleItems.length > 0}
    <div class="cards" class:cards--list={viewMode === 'list'} id="shop-list">
        {#each visibleItems as item (item.id)}
        <div class="card card--shop shop-card" class:card--affordable={isItemActive(item) && isItemAffordable(item)} class:card--disabled={!isItemActive(item) || !isItemAffordable(item)} class:shop-card--list={viewMode === 'list'}>
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{item.groupName ?? tShop('section.noGroup')}</span>
                {#if formatFrequency(item.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(item.frequency)}</span>
                {/if}
                <span class:card__status--available={isItemActive(item) && isItemAffordable(item)} class:card__status--locked={!isItemActive(item) || !isItemAffordable(item)} class="card__status">
                    {availabilityLabel(item)}
                </span>
            </div>
            <div class="shop-card__layout">
                <div class="shop-card__main">
                    <CardHeader
                        title={String(item.name ?? '')}
                        amount={String(item.price ?? 0)}
                        amountClass="item-coins"
                        amountNote={shopMoneyPriceLabel(item)}
                        compactChips={shopCompactChips(item)}
                    />
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
                    {#if shopMoneyPriceLabel(item)}
                    <span class="shop-card__money-price">{shopMoneyPriceLabel(item)}</span>
                    {/if}
                    <div class="card__actions">
                        {#if isAdmin}
                        <button class="btn btn--primary btn--small admin-only" data-shop-action="buy" disabled={balance < itemPrice(item) || !isItemActive(item)}
                            on:click={() => handleBuy(item.id)}>
                            {tShop('actions.buy')}
                        </button>
                        <button class="btn btn--secondary btn--small admin-only" data-shop-action="edit" on:click={() => openEditShopItem(item)}>
                            {tShop('actions.edit')}
                        </button>
                        {:else}
                        <button class="btn btn--primary" data-shop-action="request" disabled={balance < itemPrice(item) || !isItemActive(item)}
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
    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
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

    .shop-card__money-price {
        display: none;
    }

    .shop-card--list {
        height: auto;
        padding: 0.4rem 0.75rem;
    }

    .shop-card--list .card__badge-row,
    .shop-card--list .card__comment,
    .shop-card--list .card__meta {
        display: none;
    }

    .shop-card--list .shop-card__layout {
        flex-direction: row;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.5rem 0.75rem;
    }

    .shop-card--list .shop-card__main {
        flex: 1 1 0;
        min-width: 0;
    }

    .shop-card--list .shop-card__side {
        flex-direction: row;
        align-items: center;
        gap: 0.4rem;
        flex-shrink: 0;
    }

    .shop-card--list .shop-card__money-price {
        display: inline-flex;
        align-items: center;
        flex: none;
        padding: 0.18rem 0.46rem;
        border-radius: 999px;
        background: rgba(245, 158, 11, 0.12);
        color: #8a6118;
        font-size: 0.76rem;
        font-weight: 800;
        line-height: 1;
        white-space: nowrap;
    }

    .shop-card--list .card__actions {
        flex-wrap: nowrap;
        gap: 0.4rem;
        justify-content: flex-end;
    }

    .shop-card--list .card__actions .btn {
        flex: none;
        padding: 0.38rem 0.7rem;
        font-size: 0.82rem;
    }

    @media (max-width: 640px) {
        .shop-card--list {
            padding: 0.38rem 0.46rem 0.38rem 0.56rem;
        }

        .shop-card--list .shop-card__layout {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: stretch;
            gap: 0.48rem;
        }

        .shop-card--list .shop-card__main {
            min-width: 0;
        }

        .shop-card--list .shop-card__side {
            width: auto;
            min-width: 0;
            display: flex;
            flex-direction: row;
            align-items: stretch;
            align-self: stretch;
            justify-content: stretch;
            flex-wrap: nowrap;
            gap: 0;
        }

        .shop-card--list .shop-card__money-price {
            display: none;
        }

        .shop-card--list .card__actions {
            width: auto;
            height: 100%;
            min-height: 3.15rem;
            display: flex;
            flex: 0 0 auto;
            flex-direction: column;
            justify-content: stretch;
            gap: 0.24rem;
        }

        .shop-card--list .card__actions .btn {
            flex: 1 1 0;
            min-width: 3.6rem;
            min-height: 0;
            padding: 0.2rem 0.42rem;
            font-size: 0.68rem;
            line-height: 1.05;
            white-space: nowrap;
        }
    }

    /* ── Skeleton loader (scoped names to avoid global .skeleton conflicts) ── */
    .cards--skeleton-shop {
        pointer-events: none;
        user-select: none;
    }

    .card--skeleton-shop {
        background: var(--card-bg, #ffffff) !important;
        border-color: var(--card-border, rgba(0, 0, 0, 0.06)) !important;
    }

    .skel-shop {
        display: block;
        background: linear-gradient(90deg, #e8e8e8 25%, #f5f5f5 50%, #e8e8e8 75%);
        background-size: 200% 100%;
        animation: skel-shimmer 1.5s ease-in-out infinite;
        border-radius: 6px;
        color: transparent !important;
    }

    .skel-shop--badge {
        width: 5rem;
        height: 1.2rem;
        border-radius: 999px;
    }

    .skel-shop--badge-sm {
        width: 3.5rem;
    }

    .skel-shop--title {
        width: 70%;
        height: 1.4rem;
        margin-bottom: 0.5rem;
    }

    .skel-shop--text {
        width: 100%;
        height: 0.85rem;
        margin-bottom: 0.35rem;
    }

    .skel-shop--text-short {
        width: 55%;
    }

    .skel-shop--button {
        width: 5rem;
        height: 2.2rem;
        border-radius: 8px;
    }

    @keyframes skel-shimmer {
        0% { background-position: 200% 0; }
        100% { background-position: -200% 0; }
    }
</style>
