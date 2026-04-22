<script lang="ts">
    import { browser } from '$app/environment';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { earnCoins, requestCoins, saveChildGroupOrder } from '$lib/services/api';
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

    const i18n = useI18n();

    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    let selectedGroup = '';
    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let viewMode: CardViewMode = 'grid';
    let groupOrderEditor: { openEditor: () => void; resetOrder: () => void } | null = null;
    const loadedViewRole: { value: CardViewRole | null } = { value: null };

    function tTasks(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`tasks.${key}` as MessageKey, variables);
    }

    $: tasks = $appStore.tasks;
    $: isAdmin = $appStore.isAdmin;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;

    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = (($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null);
    $: rawGroups = [...new Set(tasks.map((task) => normalizeGroupLabel(task.groupName)))];
    $: groups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'tasks', isAdmin));
    $: hasStoredGroupOrder = hasSavedGroupOrder(currentChild, 'tasks', isAdmin);
    $: if (browser && loadedViewRole.value !== viewRole) {
        viewMode = loadCardViewMode('tasks', viewRole);
        loadedViewRole.value = viewRole;
    }
    $: if (selectedGroup && !groups.includes(selectedGroup)) {
        selectedGroup = '';
    }

    $: visibleTasks = selectedGroup
        ? tasks.filter((task) => normalizeGroupLabel(task.groupName) === selectedGroup)
        : sortItemsByGroup(tasks, groups, (task) => normalizeGroupLabel(task.groupName));

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
            return tTasks('frequencyFallback', { limit: $i18n.formatNumber(numericLimit) });
        }

        return tTasks(`${periodKey}.${pluralCategory}`, { limit: $i18n.formatNumber(numericLimit) });
    }

    function taskMoneyLimitLabel(task: { moneyLimit?: number | null }) {
        return task.moneyLimit != null
            ? tTasks('section.moneyLimit', { amount: $i18n.formatNumber(task.moneyLimit) })
            : '';
    }

    function taskAgeRangeLabel(task: { ageMin?: number | null; ageMax?: number | null }) {
        return task.ageMin != null || task.ageMax != null
            ? tTasks('section.ageRange', { min: task.ageMin ?? 0, max: task.ageMax ?? 18 })
            : '';
    }

    function taskCompactChips(task: {
        groupName?: string | null;
        frequency?: { limit?: number; period?: string } | null;
        moneyLimit?: number | null;
        ageMin?: number | null;
        ageMax?: number | null;
    }): CardHeaderChip[] {
        const chips: CardHeaderChip[] = [
            { label: task.groupName ?? tTasks('section.noGroup'), className: 'card__compact-chip--group' },
        ];
        const frequency = formatFrequency(task.frequency);
        const moneyLimit = taskMoneyLimitLabel(task);
        const ageRange = taskAgeRangeLabel(task);

        if (frequency) chips.push({ label: frequency });
        if (moneyLimit) chips.push({ label: moneyLimit });
        if (ageRange) chips.push({ label: ageRange });

        return chips;
    }

    async function handleEarn(taskId: unknown) {
        const childId = resolvedChildId;
        const task = tasks.find((entry) => entry.id == taskId);
        if (!task) return;
        if (isAdmin) {
            const res = await earnCoins(taskId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(
                    tTasks('toasts.awarded', {
                        amount: $i18n.formatNumber(Number(task.coins ?? 0)),
                        title: String(task.title ?? task.name),
                    }),
                    'success'
                );
            }
        } else {
            const result = await requestCoins(taskId);
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tTasks('toasts.requestSent'), 'success');
                return;
            }

            showToast(result.error, 'error');
        }
    }

    function openAddTask() {
        modalStore.open('task-modal', { mode: 'add' });
    }

    function openEditTask(task: unknown) {
        modalStore.open('task-modal', { mode: 'edit', task });
    }

    async function persistGroupOrder(nextOrder: string[]) {
        if (resolvedChildId == null) {
            showToast(tTasks('toasts.selectChildFirst'), 'error');
            return;
        }

        isSavingGroupOrder = true;
        const result = await saveChildGroupOrder(resolvedChildId, 'tasks', nextOrder);
        if (result.ok) {
            appStore.update((state) => ({
                ...state,
                children: applyGroupOrderToChildren(state.children, resolvedChildId, 'tasks', isAdmin, nextOrder),
            }));
            isEditingGroupOrder = false;
            showToast(isAdmin ? tTasks('toasts.groupOrderSavedAdmin') : tTasks('toasts.groupOrderSavedChild'), 'success');
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
        saveCardViewMode('tasks', viewRole, nextMode);
    }

    function openGroupOrderEditor() {
        groupOrderEditor?.openEditor();
    }

    function resetGroupOrderFromHeader() {
        groupOrderEditor?.resetOrder();
    }
</script>

<section class="section" id="tasks-section">
    <div class="section__header">
        <div class="section__header-titles">
            <h2>
                <span class="gamified-icon icon-tasks" aria-hidden="true"
                    style="width: 1.5rem; height: 1.5rem; margin-right: 0.5rem; vertical-align: middle;"></span>
                {tTasks('section.title')}
            </h2>
            <p class="section__subtitle">{tTasks('section.subtitle')}</p>
        </div>
        <div class="section__header-actions" class:section__header-actions--without-add={!isAdmin}>
            {#if isAdmin}
            <button class="btn btn--add section__add-btn" id="add-task-btn" on:click={openAddTask}>{tTasks('section.add')}</button>
            {/if}
            <div class="mobile-control-strip">
                <div class="view-toggle" role="group" aria-label={tTasks('section.viewAria')}>
                    <button
                        type="button"
                        class="view-toggle__button"
                        class:view-toggle__button--active={viewMode === 'grid'}
                        aria-label={tTasks('section.viewGrid')}
                        aria-pressed={viewMode === 'grid'}
                        on:click={() => setViewMode('grid')}
                    >
                        <span class="view-toggle__icon view-toggle__icon--grid" aria-hidden="true"></span>
                        <span class="view-toggle__label">{tTasks('section.viewGrid')}</span>
                    </button>
                    <button
                        type="button"
                        class="view-toggle__button"
                        class:view-toggle__button--active={viewMode === 'list'}
                        aria-label={tTasks('section.viewList')}
                        aria-pressed={viewMode === 'list'}
                        on:click={() => setViewMode('list')}
                    >
                        <span class="view-toggle__icon view-toggle__icon--rows" aria-hidden="true"></span>
                        <span class="view-toggle__label">{tTasks('section.viewList')}</span>
                    </button>
                </div>
                {#if groups.length > 1 && !isEditingGroupOrder}
                <div class="mobile-order-actions" role="group" aria-label={tTasks('groupOrder.title')}>
                    <button
                        class="mobile-order-actions__button"
                        type="button"
                        aria-label={isAdmin ? $i18n.t('app.groupOrder.configureAdmin') : $i18n.t('app.groupOrder.configureChild')}
                        on:click={openGroupOrderEditor}
                        disabled={isSavingGroupOrder}
                    >
                        <span class="mobile-order-actions__icon mobile-order-actions__icon--order" aria-hidden="true"></span>
                    </button>
                    {#if hasStoredGroupOrder}
                    <button
                        class="mobile-order-actions__button"
                        type="button"
                        aria-label={isAdmin ? $i18n.t('app.groupOrder.resetAdmin') : $i18n.t('app.groupOrder.resetChild')}
                        on:click={resetGroupOrderFromHeader}
                        disabled={isSavingGroupOrder}
                    >
                        <span class="mobile-order-actions__icon mobile-order-actions__icon--reset" aria-hidden="true"></span>
                    </button>
                    {/if}
                </div>
                {/if}
            </div>
        </div>
    </div>

    {#if groups.length > 1}
    <nav class="group-nav" id="tasks-group-nav">
        <div class="group-nav__scroll">
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === ''} on:click={() => selectedGroup = ''}>
                {tTasks('section.all')}
            </button>
            {#each groups as group (group)}
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === group}
                on:click={() => selectedGroup = group}>
                {group}
            </button>
            {/each}
        </div>
    </nav>

    <GroupOrderEditor
        bind:this={groupOrderEditor}
        bind:isOpen={isEditingGroupOrder}
        {isAdmin}
        isSaving={isSavingGroupOrder}
        hasStoredOrder={hasStoredGroupOrder}
        {groups}
        title={tTasks('groupOrder.title')}
        hintAdmin={tTasks('groupOrder.hintAdmin')}
        hintChild={tTasks('groupOrder.hintChild')}
        descriptionAdmin={tTasks('groupOrder.descriptionAdmin')}
        descriptionChild={tTasks('groupOrder.descriptionChild')}
        hideToolbarOnMobile
        on:save={handleGroupOrderSave}
        on:reset={handleGroupOrderReset}
    />
    {/if}

    {#if visibleTasks.length > 0}
    <div class="cards" class:cards--list={viewMode === 'list'} id="tasks-list">
        {#each visibleTasks as task (task.id)}
        <div class="card card--task task-card" class:task-card--list={viewMode === 'list'}>
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{task.groupName ?? tTasks('section.noGroup')}</span>
                {#if formatFrequency(task.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(task.frequency)}</span>
                {/if}
            </div>
            <div class="task-card__layout">
                <div class="task-card__main">
                    <CardHeader
                        title={String(task.title ?? task.name ?? '')}
                        amount={String(task.coins ?? 0)}
                        amountClass="task-coins"
                        compactChips={taskCompactChips(task)}
                    />
                    {#if task.comment}
                    <p class="card__comment">{task.comment}</p>
                    {:else}
                    <p class="card__comment">{tTasks('section.defaultComment')}</p>
                    {/if}
                </div>
                <div class="task-card__side">
                    <div class="card__meta">
                        {#if task.moneyLimit != null}
                        <span class="card__meta-item">{tTasks('section.moneyLimit', { amount: $i18n.formatNumber(task.moneyLimit) })}</span>
                        {/if}
                        {#if task.ageMin != null || task.ageMax != null}
                        <span class="card__meta-item">{tTasks('section.ageRange', { min: task.ageMin ?? 0, max: task.ageMax ?? 18 })}</span>
                        {/if}
                    </div>
                    <div class="card__actions">
                        {#if isAdmin}
                        <button class="btn btn--primary btn--small" data-task-action="award" on:click={() => handleEarn(task.id)}>
                            {tTasks('actions.award')}
                        </button>
                        <button class="btn btn--secondary btn--small admin-only" data-task-action="edit" on:click={() => openEditTask(task)}>
                            {tTasks('actions.edit')}
                        </button>
                        {:else}
                        <button class="btn btn--primary" data-task-action="request" on:click={() => handleEarn(task.id)}>
                            {tTasks('actions.complete')}
                        </button>
                        {/if}
                    </div>
                </div>
            </div>
        </div>
        {/each}
    </div>
    {:else}
    <div class="empty-state" id="tasks-empty">
        <span class="empty-state__icon">
            <span class="gamified-icon icon-empty" aria-hidden="true"></span>
        </span>
        <p class="empty-state__title">{tTasks('section.emptyTitle')}</p>
        <p class="empty-state__hint">
            {#if isAdmin}{tTasks('section.emptyAdminHint')}{:else}{tTasks('section.emptyChildHint')}{/if}
        </p>
        {#if isAdmin}
        <div class="empty-state__actions">
            <button class="btn btn--add" type="button" on:click={openAddTask}>{tTasks('section.addTask')}</button>
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

    .mobile-control-strip {
        order: 1;
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
    }

    .section__add-btn {
        order: 2;
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
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.38rem;
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

    .view-toggle__icon,
    .mobile-order-actions__icon {
        position: relative;
        display: none;
        width: 1rem;
        height: 1rem;
        flex: none;
    }

    .view-toggle__icon--grid {
        background:
            linear-gradient(currentColor 0 0) 0 0 / 0.38rem 0.38rem no-repeat,
            linear-gradient(currentColor 0 0) 100% 0 / 0.38rem 0.38rem no-repeat,
            linear-gradient(currentColor 0 0) 0 100% / 0.38rem 0.38rem no-repeat,
            linear-gradient(currentColor 0 0) 100% 100% / 0.38rem 0.38rem no-repeat;
    }

    .view-toggle__icon--rows {
        background:
            linear-gradient(currentColor 0 0) 0 0.08rem / 100% 0.18rem no-repeat,
            linear-gradient(currentColor 0 0) 0 0.41rem / 100% 0.18rem no-repeat,
            linear-gradient(currentColor 0 0) 0 0.74rem / 100% 0.18rem no-repeat;
    }

    .mobile-order-actions {
        display: none;
    }

    .mobile-order-actions__button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 2.1rem;
        height: 2.1rem;
        border: 0;
        border-radius: 999px;
        background: transparent;
        color: rgba(54, 68, 96, 0.72);
        cursor: pointer;
    }

    .mobile-order-actions__button:disabled {
        cursor: not-allowed;
        opacity: 0.55;
    }

    .mobile-order-actions__icon--order {
        background:
            radial-gradient(circle, currentColor 0.12rem, transparent 0.13rem) 0 0.02rem / 100% 0.32rem no-repeat,
            radial-gradient(circle, currentColor 0.12rem, transparent 0.13rem) 0 0.36rem / 100% 0.32rem no-repeat,
            radial-gradient(circle, currentColor 0.12rem, transparent 0.13rem) 0 0.7rem / 100% 0.32rem no-repeat,
            linear-gradient(currentColor 0 0) 0.35rem 0.09rem / 0.62rem 0.14rem no-repeat,
            linear-gradient(currentColor 0 0) 0.35rem 0.43rem / 0.62rem 0.14rem no-repeat,
            linear-gradient(currentColor 0 0) 0.35rem 0.77rem / 0.62rem 0.14rem no-repeat;
    }

    .mobile-order-actions__icon--reset::before {
        content: '';
        position: absolute;
        inset: 0.16rem;
        border: 0.17rem solid currentColor;
        border-left-color: transparent;
        border-radius: 999px;
    }

    .mobile-order-actions__icon--reset::after {
        content: '';
        position: absolute;
        top: 0.02rem;
        left: 0.11rem;
        width: 0;
        height: 0;
        border-top: 0.27rem solid transparent;
        border-bottom: 0.27rem solid transparent;
        border-right: 0.37rem solid currentColor;
        transform: rotate(-18deg);
    }

    .view-toggle__button--active {
        background: linear-gradient(135deg, rgba(87, 121, 206, 0.18), rgba(84, 179, 160, 0.2));
        color: #20304e;
        box-shadow: inset 0 0 0 1px rgba(87, 121, 206, 0.14);
    }

    .cards--list {
        grid-template-columns: minmax(0, 1fr);
        gap: 0.35rem;
    }

    .task-card__layout {
        display: flex;
        flex-direction: column;
        gap: 0.9rem;
    }

    .task-card__side {
        display: flex;
        flex-direction: column;
        gap: 0.8rem;
    }

    /* Compact list row */
    .task-card--list {
        height: auto;
        padding: 0.4rem 0.75rem;
    }

    .task-card--list .card__badge-row,
    .task-card--list .card__comment,
    .task-card--list .card__meta {
        display: none;
    }

    .task-card--list .task-card__layout {
        flex-direction: row;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.5rem 0.75rem;
    }

    .task-card--list .task-card__main {
        flex: 1 1 0;
        min-width: 0;
    }

    .task-card--list .task-card__side {
        flex-direction: row;
        align-items: center;
        gap: 0.4rem;
        flex-shrink: 0;
    }

    .task-card--list .card__actions {
        flex-wrap: nowrap;
        gap: 0.4rem;
        justify-content: flex-end;
    }

    .task-card--list .card__actions .btn {
        flex: none;
        padding: 0.38rem 0.7rem;
        font-size: 0.82rem;
    }

    @media (max-width: 640px) {
        .section__header-actions {
            width: 100%;
            display: grid;
            grid-template-columns: minmax(4.25rem, auto) minmax(0, 1fr);
            align-items: center;
            gap: 0.5rem;
        }

        .mobile-control-strip {
            order: 2;
            justify-self: end;
            max-width: 100%;
            gap: 0.35rem;
            min-width: 0;
        }

        .section__add-btn {
            order: 1;
            min-height: 2.55rem;
            padding-inline: 0.72rem;
            white-space: nowrap;
        }

        .section__header-actions--without-add {
            grid-template-columns: minmax(0, 1fr);
        }

        .section__header-actions--without-add .mobile-control-strip {
            justify-self: start;
        }

        .view-toggle {
            width: auto;
            flex: none;
            justify-content: center;
        }

        .view-toggle__button {
            width: 2.1rem;
            height: 2.1rem;
            flex: none;
            padding: 0;
        }

        .view-toggle__label {
            position: absolute;
            width: 1px;
            height: 1px;
            overflow: hidden;
            clip: rect(0 0 0 0);
            white-space: nowrap;
        }

        .view-toggle__icon,
        .mobile-order-actions__icon {
            display: block;
        }

        .mobile-order-actions {
            display: inline-flex;
            align-items: center;
            gap: 0.25rem;
            padding: 0.25rem;
            border-radius: 999px;
            border: 1px solid rgba(120, 140, 175, 0.18);
            background: rgba(246, 248, 252, 0.92);
        }

        .task-card--list .task-card__layout {
            align-items: stretch;
        }

        .task-card--list .task-card__side {
            width: 100%;
            justify-content: space-between;
            flex-wrap: wrap;
        }

        .task-card--list .card__actions {
            width: 100%;
            justify-content: flex-start;
        }
    }
</style>
