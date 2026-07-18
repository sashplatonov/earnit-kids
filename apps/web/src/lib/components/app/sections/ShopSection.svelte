<script lang="ts">
    import { browser } from '$app/environment';
    import { onMount } from 'svelte';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import BulkActionToolbar from '$lib/components/app/BulkActionToolbar.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import CatalogActionFeedback from '$lib/components/app/catalog/CatalogActionFeedback.svelte';
    import CatalogGroupNav from '$lib/components/app/catalog/CatalogGroupNav.svelte';
    import CatalogSectionHeader from '$lib/components/app/catalog/CatalogSectionHeader.svelte';
    import CatalogCard from '$lib/components/app/catalog/CatalogCard.svelte';
    import RewardGoalProgress from '$lib/components/app/catalog/RewardGoalProgress.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { bulkShopAction, buyItem, requestItemWithNote, saveChildGroupOrder, setRewardGoal } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { confirmAction } from '$lib/services/confirm';
    import {
        applyGroupOrderToChildren,
        getEffectiveGroupOrder,
        normalizeGroupLabel,
        orderGroups,
        sortItemsByGroup,
    } from '$lib/services/groupOrder';
    import { requestGroupName } from '$lib/services/groupPrompt';
    import { loadCardViewMode, saveCardViewMode, type CardViewMode, type CardViewRole } from '$lib/services/cardViewMode';
    import { showToast } from '$lib/stores/toasts';
    import { recordCatalogEvent } from '$lib/services/catalogTelemetry';
    import { readCatalogViewState, writeCatalogViewState } from '$lib/services/catalogViewState';
    import { buildShopCatalogItemViewModel } from '$lib/services/catalogItemViewModel';

    const i18n = useI18n();

    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let isSavingRewardGoal = false;
    let pendingItemIds: string[] = [];
    let itemFeedback: Record<string, { status: 'pending' | 'success' | 'error'; message: string }> = {};
    let selectedGroup = '';
    let isBulkMode = false;
    let selectedItemIds: Array<number | string> = [];
    let viewMode: CardViewMode = 'list';
    let groupOrderEditor: { openEditor: () => void } | null = null;
    const loadedViewRole: { value: CardViewRole | null } = { value: null };
    const loadedChildScope: { value: string } = { value: '' };

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
        isBulkMode = false;
        selectedItemIds = [];
    }

    $: if (selectedGroup && groups.length > 0 && !groups.includes(selectedGroup)) {
        clearBulkSelection();
        setSelectedGroup('', { replace: true });
    }
    $: {
        const nextChildScope = String(resolvedChildId ?? '');
        if (loadedChildScope.value !== nextChildScope) {
            isBulkMode = false;
            selectedItemIds = [];
            itemFeedback = {};
            loadedChildScope.value = nextChildScope;
        }
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
    $: rewardGoal = shopItems.find((item) => String(item.id) === String(currentChild?.rewardGoalItemId)) ?? null;

    onMount(() => {
        const initialState = readCatalogViewState(new URL(window.location.href), viewMode);
        selectedGroup = initialState.group;
        viewMode = initialState.view;

        const handlePopState = () => {
            const nextState = readCatalogViewState(new URL(window.location.href), viewMode);
            selectedGroup = nextState.group;
            viewMode = nextState.view;
        };

        window.addEventListener('popstate', handlePopState);

        return () => {
            window.removeEventListener('popstate', handlePopState);
        };
    });

    function missingCoins(price: number) {
        return Math.max(price - balance, 0);
    }

    function itemActionKey(itemId: unknown) {
        return `${String(resolvedChildId ?? '')}:${String(itemId)}`;
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
            season: 'frequencySeason',
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
        const itemKey = itemActionKey(itemId);
        const feedbackKey = String(itemId);
        if (pendingItemIds.includes(itemKey)) return;
        const childId = resolvedChildId;
        const actionScope = String(childId ?? '');
        const item = shopItems.find((entry) => entry.id == itemId);
        if (!item) return;
        pendingItemIds = [...pendingItemIds, itemKey];
        recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'started' });
        itemFeedback = { ...itemFeedback, [feedbackKey]: { status: 'pending', message: tShop('feedback.pending') } };
        const finish = (status: 'success' | 'error', message: string) => {
            pendingItemIds = pendingItemIds.filter((id) => id !== itemKey);
            if (String(resolvedChildId ?? '') !== actionScope) return;
            itemFeedback = { ...itemFeedback, [feedbackKey]: { status, message } };
        };
        if (isAdmin) {
            if (balance < (item.price as number)) {
                showToast(tShop('toasts.notEnoughCoins'), 'error');
                finish('error', tShop('feedback.error'));
                recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'error' });
                return;
            }
            const res = await buyItem(itemId, childId) as Record<string, unknown> | null;
            if (res) {
                if (String(resolvedChildId ?? '') === actionScope) applyDataSnapshot(res);
                showToast(tShop('toasts.bought', { name: item.name }), 'success');
                finish('success', tShop('feedback.success'));
                recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'success' });
            } else {
                finish('error', tShop('feedback.error'));
                showToast(tShop('feedback.error'), 'error');
                recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'error' });
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
                if (String(resolvedChildId ?? '') === actionScope && result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tShop('toasts.requestSent'), 'success');
                finish('success', tShop('feedback.success'));
                recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'success' });
                return;
            }

            showToast(result.error, 'error');
            finish('error', tShop('feedback.error'));
            recordCatalogEvent({ name: 'shop_action', surface: 'shop', result: 'error' });
        }
    }

    function openAddShopItem() {
        modalStore.open('shop-modal', { mode: 'add', groupSuggestions: groups });
    }

    function openEditShopItem(item: unknown) {
        modalStore.open('shop-modal', { mode: 'edit', item, groupSuggestions: groups });
    }

    function isItemSelected(item: { id: number | string }) {
        return selectedItemIds.some((id) => String(id) === String(item.id));
    }

    function clearBulkSelection() {
        isBulkMode = false;
        selectedItemIds = [];
    }

    function toggleBulkMode() {
        if (isBulkMode) {
            clearBulkSelection();
            return;
        }

        isBulkMode = true;
        selectedItemIds = [];
    }

    function selectAllVisibleItems() {
        if (!isBulkMode) {
            isBulkMode = true;
        }
        selectedItemIds = visibleItems.map((item) => item.id);
    }

    async function runItemBulkAction(action: 'delete' | 'block' | 'unblock' | 'change_group') {
        if (resolvedChildId == null) {
            showToast(tShop('toasts.selectChildFirst'), 'error');
            return;
        }
        if (selectedItemIds.length === 0) {
            return;
        }

        if (action === 'delete') {
            const confirmed = await confirmAction({
                title: tShop('modal.confirmDeleteTitle'),
                description: tShop('modal.confirmDeleteDescription'),
                confirmLabel: tShop('modal.delete'),
                cancelLabel: tShop('modal.cancel'),
                tone: 'danger',
            });
            if (!confirmed) {
                return;
            }
        }

        if (action === 'change_group') {
            const groupName = await requestGroupName({
                title: tShop('bulk.changeGroupTitle'),
                description: tShop('bulk.changeGroupDescription'),
                placeholder: tShop('bulk.changeGroupPlaceholder'),
                confirmLabel: tShop('bulk.changeGroupConfirm'),
                cancelLabel: tShop('modal.cancel'),
                suggestions: groups,
            });
            if (groupName == null) {
                return;
            }

            const result = await bulkShopAction({
                childId: resolvedChildId,
                action,
                itemIds: [...selectedItemIds],
                groupName,
            });
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tShop('toasts.bulkChangedGroup'), 'success');
                clearBulkSelection();
            } else {
                showToast(result.error, 'error');
            }
            return;
        }

        const result = await bulkShopAction({
            childId: resolvedChildId,
            action,
            itemIds: [...selectedItemIds],
        });
        if (!result.ok) {
            showToast(result.error, 'error');
            return;
        }

        if (result.data && typeof result.data === 'object') {
            applyDataSnapshot(result.data as Record<string, unknown>);
        }

        if (action === 'delete') {
            showToast(tShop('toasts.bulkDeleted', { count: selectedItemIds.length }), 'success');
        } else if (action === 'block') {
            showToast(tShop('toasts.bulkBlocked', { count: selectedItemIds.length }), 'success');
        } else {
            showToast(tShop('toasts.bulkUnblocked', { count: selectedItemIds.length }), 'success');
        }

        clearBulkSelection();
    }

    function itemPrice(item: { price?: unknown }) {
        return Number(item.price ?? 0);
    }

    function isRewardGoal(item: { id: number | string }) {
        return currentChild?.rewardGoalItemId != null && String(currentChild.rewardGoalItemId) === String(item.id);
    }

    async function toggleRewardGoal(item: { id: number | string }) {
        if (resolvedChildId == null || isSavingRewardGoal) return;
        const goalScope = String(resolvedChildId);
        const previousId = currentChild?.rewardGoalItemId ?? null;
        const nextId = isRewardGoal(item) ? null : item.id;
        isSavingRewardGoal = true;
        appStore.update((state) => ({
            ...state,
            children: state.children.map((child) => String(child.id) === String(resolvedChildId)
                ? { ...child, rewardGoalItemId: nextId }
                : child),
        }));
        recordCatalogEvent({ name: 'reward_goal_action', surface: 'shop', result: 'started' });
        const result = await setRewardGoal(nextId);
        if (result.ok) {
            if (String(resolvedChildId ?? '') === goalScope && result.data && typeof result.data === 'object') {
                applyDataSnapshot(result.data as Record<string, unknown>);
            }
            recordCatalogEvent({ name: 'reward_goal_action', surface: 'shop', result: 'success' });
        } else {
            appStore.update((state) => ({
                ...state,
                children: state.children.map((child) => String(child.id) === goalScope
                    ? { ...child, rewardGoalItemId: previousId }
                    : child),
            }));
            showToast(result.error || tShop('toasts.goalSaveFailed'), 'error');
            recordCatalogEvent({ name: 'reward_goal_action', surface: 'shop', result: 'error' });
        }
        isSavingRewardGoal = false;
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

    function formatLastPurchasedAt(item: { lastPurchasedAt?: unknown }) {
        if (typeof item.lastPurchasedAt !== 'string' || !item.lastPurchasedAt) {
            return '';
        }

        const parsed = new Date(item.lastPurchasedAt);
        return Number.isNaN(parsed.getTime()) ? '' : $i18n.formatShortDate(parsed);
    }

    function itemLastPurchasedLabel(item: { lastPurchasedAt?: unknown }) {
        const date = formatLastPurchasedAt(item);
        return date ? tShop('section.lastPurchased', { date }) : '';
    }

    function shopCompactChips(item: {
        isActive?: unknown;
        groupName?: string | null;
        frequency?: { limit?: number; period?: string } | null;
        moneyLimit?: number | null;
        price?: unknown;
        lastPurchasedAt?: unknown;
    }): CardHeaderChip[] {
        const chips: CardHeaderChip[] = [
            { label: item.groupName ?? tShop('section.noGroup'), className: 'card__compact-chip--group' },
        ];
        const frequency = formatFrequency(item.frequency);
        const lastPurchased = itemLastPurchasedLabel(item);

        if (frequency) chips.push({ label: frequency });
        if (lastPurchased) {
            chips.push({ label: lastPurchased, className: 'card__compact-chip--shop-history' });
        }
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

        return readCatalogViewState(new URL(window.location.href), viewMode).group;
    }

    function syncSelectedGroupUrl(nextGroup: string, replace = false) {
        if (!browser) {
            return;
        }

        const url = writeCatalogViewState(new URL(window.location.href), { group: nextGroup });

        if (replace) {
            history.replaceState(history.state, '', url);
        } else {
            history.pushState(history.state, '', url);
        }
    }

    function setSelectedGroup(nextGroup: string, options?: { replace?: boolean }) {
        const resolvedGroup = groups.includes(nextGroup) ? nextGroup : '';
        const currentGroup = readSelectedGroupFromLocation();

        clearBulkSelection();
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
        if (browser) history.replaceState(history.state, '', writeCatalogViewState(new URL(window.location.href), { view: nextMode }));
    }

    function openGroupOrderEditor() {
        groupOrderEditor?.openEditor();
    }

    function clearRewardGoal() {
        if (currentChild?.rewardGoalItemId != null) void toggleRewardGoal({ id: currentChild.rewardGoalItemId });
    }

</script>

<section class="section" id="shop-section">
    <CatalogSectionHeader title={tShop('section.title')} subtitle={tShop('section.subtitle')} iconClass="icon-shop">
        <SectionHeaderControls
            {isAdmin}
            addLabel={tShop('section.add')}
            addId="add-shop-btn"
            {viewMode}
            viewAriaLabel={tShop('section.viewAria')}
            gridLabel={tShop('section.viewGrid')}
            listLabel={tShop('section.viewList')}
            orderLabel={isAdmin ? $i18n.t('app.groupOrder.configureAdmin') : $i18n.t('app.groupOrder.configureChild')}
            bulkLabel={isBulkMode ? tShop('bulk.clear') : tShop('bulk.toggle')}
            isBulkMode={isBulkMode}
            hasGroups={groups.length > 1}
            {isEditingGroupOrder}
            {isSavingGroupOrder}
            on:add={openAddShopItem}
            on:editOrder={openGroupOrderEditor}
            on:toggleBulkMode={toggleBulkMode}
            on:viewMode={(event) => setViewMode(event.detail)}
        />
    </CatalogSectionHeader>

    <RewardGoalProgress item={rewardGoal} {balance} label={tShop('goal.label')} clearLabel={tShop('goal.clear')}
        staleLabel={tShop('goal.stale')} onClear={isAdmin ? null : clearRewardGoal} disabled={isSavingRewardGoal}
        stale={currentChild?.rewardGoalItemId != null && !rewardGoal} emptyLabel={tShop('goal.empty')}
        missingLabel={(amount) => tShop('goal.missing', { amount: $i18n.formatNumber(amount) })}
        readyLabel={tShop('goal.ready')}
        formatNumber={(value) => $i18n.formatNumber(value)} />

    <BulkActionToolbar
        show={isBulkMode}
        selectedCount={selectedItemIds.length}
        selectionLabel={tShop('bulk.selected', { count: selectedItemIds.length })}
        selectAllLabel={tShop('bulk.selectAll')}
        deleteLabel={tShop('bulk.delete')}
        blockLabel={tShop('bulk.block')}
        unblockLabel={tShop('bulk.unblock')}
        changeGroupLabel={tShop('bulk.changeGroup')}
        clearLabel={tShop('bulk.clear')}
        on:selectAll={selectAllVisibleItems}
        on:delete={() => void runItemBulkAction('delete')}
        on:block={() => void runItemBulkAction('block')}
        on:unblock={() => void runItemBulkAction('unblock')}
        on:changeGroup={() => void runItemBulkAction('change_group')}
        on:clear={clearBulkSelection}
    />

    <CatalogGroupNav id="shop-group-nav" {groups} selected={selectedGroup} allLabel={tShop('section.all')}
        ariaLabel={tShop('section.groupAria')} onSelect={setSelectedGroup} />

    {#if groups.length > 1}
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
        {@const catalogItem = buildShopCatalogItemViewModel(item, balance)}
        <CatalogCard kind="shop" view={viewMode} disabled={!catalogItem.active || !catalogItem.affordable} selected={isItemSelected(item)}
            additionalClass={catalogItem.active && catalogItem.affordable ? 'card--affordable' : ''} let:classes>
        <article class={classes}>
            <div class="card__badge-row">
                {#if isAdmin && isBulkMode && viewMode !== 'list'}
                <label class="bulk-select">
                    <input
                        type="checkbox"
                        bind:group={selectedItemIds}
                        value={item.id}
                        aria-label={tShop('section.selectAria', { title: String(item.name ?? '') })}
                    />
                </label>
                {/if}
                <span class="card__badge card__badge--group">{item.groupName ?? tShop('section.noGroup')}</span>
                {#if formatFrequency(item.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(item.frequency)}</span>
                {/if}
                {#if itemLastPurchasedLabel(item)}
                <span class="card__badge card__badge--history">{itemLastPurchasedLabel(item)}</span>
                {/if}
                <span class:card__status--available={isItemActive(item) && isItemAffordable(item)} class:card__status--locked={!isItemActive(item) || !isItemAffordable(item)} class="card__status">
                    {availabilityLabel(item)}
                </span>
            </div>
            <div class="shop-card__layout">
                {#if isAdmin && isBulkMode && viewMode === 'list'}
                <label class="shop-card__select-cell bulk-select">
                    <input
                        type="checkbox"
                        bind:group={selectedItemIds}
                        value={item.id}
                        aria-label={tShop('section.selectAria', { title: String(item.name ?? '') })}
                    />
                </label>
                {/if}
                <div class="shop-card__main">
                    <CardHeader
                        title={catalogItem.title}
                        amount={String(catalogItem.amount)}
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
                    <div class="card__action-area">
                        <div class="card__actions">
                            {#if !isAdmin}
                            <button class="btn btn--secondary btn--small card__goal-toggle" type="button"
                                aria-pressed={isRewardGoal(item)}
                                disabled={isSavingRewardGoal || !isItemActive(item)}
                                data-shop-action="goal"
                                on:click={() => void toggleRewardGoal(item)}>
                                {isRewardGoal(item) ? tShop('actions.clearGoal') : tShop('actions.setGoal')}
                            </button>
                            {/if}
                            {#if isAdmin}
                            <button class="btn btn--primary btn--small admin-only" data-shop-action="buy" disabled={balance < itemPrice(item) || !isItemActive(item) || pendingItemIds.includes(itemActionKey(item.id))}
                                on:click={() => handleBuy(item.id)}>
                                {tShop('actions.buy')}
                            </button>
                            <button class="btn btn--secondary btn--small admin-only" data-shop-action="edit" on:click={() => openEditShopItem(item)}>
                                {tShop('actions.edit')}
                            </button>
                            {:else}
                            <button class="btn btn--primary" data-shop-action="request" disabled={balance < itemPrice(item) || !isItemActive(item) || pendingItemIds.includes(itemActionKey(item.id))}
                                on:click={() => handleBuy(item.id)}>
                                {tShop('actions.request')}
                            </button>
                            {/if}
                        </div>
                        <CatalogActionFeedback status={pendingItemIds.includes(itemActionKey(item.id)) ? 'pending' : itemFeedback[String(item.id)]?.status ?? 'idle'}
                            message={pendingItemIds.includes(itemActionKey(item.id)) ? tShop('feedback.pending') : itemFeedback[String(item.id)]?.message ?? ''}
                            retryLabel={tShop('feedback.retry')} onRetry={() => void handleBuy(item.id)} />
                    </div>
                </div>
            </div>
        </article>
        </CatalogCard>
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

    .card__action-area {
        display: grid;
        min-width: 0;
    }

    .shop-card--selected {
        outline: 2px solid rgba(37, 99, 235, 0.28);
        box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.12);
    }

    .bulk-select {
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }

    .bulk-select input {
        width: 1rem;
        height: 1rem;
        accent-color: #2563eb;
        cursor: pointer;
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
        min-height: 0;
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
        flex-wrap: nowrap;
        align-items: center;
        gap: 0.5rem 0.75rem;
    }

    .shop-card--list .shop-card__main {
        flex: 1 1 0;
        min-width: 0;
    }

    .shop-card__select-cell {
        display: inline-flex;
        align-self: center;
        flex: 0 0 auto;
        padding-right: 0.1rem;
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
        min-width: 0;
        min-height: var(--catalog-control-height);
        padding: 0.38rem 0.7rem;
        font-size: 0.82rem;
    }

    .card__badge--history {
        background: rgba(251, 191, 36, 0.16);
        color: #8a6118;
    }

    :global(.card__compact-chip--shop-history) {
        background: rgba(251, 191, 36, 0.16);
        color: #8a6118;
    }

    @media (max-width: 640px) {
        .shop-card--list {
            padding: 0.38rem 0.46rem 0.38rem 0.56rem;
        }

        .shop-card--list .shop-card__layout {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 0.48rem;
        }

        .shop-card--list .shop-card__main {
            min-width: 0;
        }

        .shop-card__select-cell {
            align-self: center;
            padding-right: 0;
        }

        .shop-card--list .shop-card__side {
            width: 100%;
            min-width: 0;
            display: block;
            flex: 1 0 100%;
        }

        .shop-card--list .shop-card__money-price {
            display: none;
        }

        .shop-card--list .card__actions {
            width: 100%;
            min-height: var(--catalog-control-height);
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(min(7.5rem, 100%), 1fr));
            gap: 0.4rem;
        }

        .shop-card--list .card__actions .btn {
            min-width: 0;
            min-height: var(--catalog-control-height);
            padding: 0.45rem 0.55rem;
            font-size: var(--text-xs);
            line-height: 1.15;
            white-space: normal;
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
