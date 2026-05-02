<script lang="ts">
    import { browser } from '$app/environment';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { earnCoins, requestCoinsWithNote, saveChildGroupOrder } from '$lib/services/api';
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

    let selectedGroup = '';
    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let viewMode: CardViewMode = 'list';
    let groupOrderEditor: { openEditor: () => void } | null = null;
    const loadedViewRole: { value: CardViewRole | null } = { value: null };

    function tTasks(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`tasks.${key}` as MessageKey, variables);
    }

    $: tasks = $appStore.tasks;
    $: isAdmin = $appStore.isAdmin;
    $: isLoading = $appStore.isLoading;
    $: viewRole = (isAdmin ? 'admin' : 'child') as CardViewRole;

    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = (($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null);
    $: rawGroups = [...new Set(tasks.map((task) => normalizeGroupLabel(task.groupName)))];
    $: groups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'tasks', isAdmin));
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
        isActive?: unknown;
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
        if (!isTaskActive(task)) {
            chips.push({ label: tTasks('section.blocked'), className: 'card__compact-chip--status card__compact-chip--status-locked' });
        }

        return chips;
    }

    function isTaskActive(task: { isActive?: unknown }) {
        return task.isActive !== false;
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
            const note = await requestNote({
                title: tTasks('requestNoteModal.title'),
                description: tTasks('requestNoteModal.description', { title: String(task.title ?? task.name ?? '') }),
                placeholder: tTasks('requestNoteModal.placeholder'),
                saveLabel: tTasks('requestNoteModal.save'),
                skipLabel: tTasks('requestNoteModal.skip'),
            });
            const result = await requestCoinsWithNote(taskId, note);
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

    function setViewMode(nextMode: CardViewMode) {
        viewMode = nextMode;
        saveCardViewMode('tasks', viewRole, nextMode);
    }

    function openGroupOrderEditor() {
        groupOrderEditor?.openEditor();
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
        <SectionHeaderControls
            {isAdmin}
            addLabel={tTasks('section.add')}
            addId="add-task-btn"
            {viewMode}
            viewAriaLabel={tTasks('section.viewAria')}
            gridLabel={tTasks('section.viewGrid')}
            listLabel={tTasks('section.viewList')}
            orderLabel={isAdmin ? $i18n.t('app.groupOrder.configureAdmin') : $i18n.t('app.groupOrder.configureChild')}
            hasGroups={groups.length > 1}
            {isEditingGroupOrder}
            {isSavingGroupOrder}
            on:add={openAddTask}
            on:editOrder={openGroupOrderEditor}
            on:viewMode={(event) => setViewMode(event.detail)}
        />
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
        {groups}
        title={tTasks('groupOrder.title')}
        descriptionAdmin={tTasks('groupOrder.descriptionAdmin')}
        descriptionChild={tTasks('groupOrder.descriptionChild')}
        on:save={handleGroupOrderSave}
    />
    {/if}

    {#if isLoading}
    <div class="cards cards--skeleton" id="tasks-skeleton">
        {#each { length: 3 } as _}
        <div class="card card--task card--skeleton">
            <div class="card__badge-row">
                <span class="skeleton skeleton--badge">&nbsp;</span>
                <span class="skeleton skeleton--badge skeleton--badge-sm">&nbsp;</span>
            </div>
            <div class="task-card__layout">
                <div class="task-card__main">
                    <div class="skeleton skeleton--title">&nbsp;</div>
                    <div class="skeleton skeleton--text">&nbsp;</div>
                    <div class="skeleton skeleton--text skeleton--text-short">&nbsp;</div>
                </div>
                <div class="task-card__side">
                    <div class="skeleton skeleton--button">&nbsp;</div>
                </div>
            </div>
        </div>
        {/each}
    </div>
    {:else if visibleTasks.length > 0}
    <div class="cards" class:cards--list={viewMode === 'list'} id="tasks-list">
        {#each visibleTasks as task (task.id)}
        <div class="card card--task task-card" class:task-card--list={viewMode === 'list'} class:task-card--inactive={!isTaskActive(task)} class:card--disabled={!isTaskActive(task)}>
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{task.groupName ?? tTasks('section.noGroup')}</span>
                {#if formatFrequency(task.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(task.frequency)}</span>
                {/if}
                {#if !isTaskActive(task)}
                <span class="card__status card__status--locked">{tTasks('section.blocked')}</span>
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
                        <button class="btn btn--primary btn--small" data-task-action="award" disabled={!isTaskActive(task)} on:click={() => handleEarn(task.id)}>
                            {tTasks('actions.award')}
                        </button>
                        <button class="btn btn--secondary btn--small admin-only" data-task-action="edit" on:click={() => openEditTask(task)}>
                            {tTasks('actions.edit')}
                        </button>
                        {:else}
                        <button class="btn btn--primary" data-task-action="request" disabled={!isTaskActive(task)} on:click={() => handleEarn(task.id)}>
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

    .task-card--inactive {
        opacity: 0.72;
    }

    @media (max-width: 640px) {
        .task-card--list {
            padding: 0.38rem 0.46rem 0.38rem 0.56rem;
        }

        .task-card--list .task-card__layout {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            align-items: stretch;
            gap: 0.48rem;
        }

        .task-card--list .task-card__side {
            width: auto;
            min-width: 0;
            justify-content: stretch;
            align-self: stretch;
        }

        .task-card--list .card__actions {
            width: auto;
            height: 100%;
            min-height: 3.15rem;
            display: flex;
            flex-direction: column;
            justify-content: stretch;
            gap: 0.24rem;
        }

        .task-card--list .card__actions .btn {
            flex: 1 1 0;
            min-width: 3.6rem;
            min-height: 0;
            padding: 0.2rem 0.42rem;
            font-size: 0.68rem;
            line-height: 1.05;
        }
    }

    /* ── Skeleton loader ── */
    .cards--skeleton {
        pointer-events: none;
        user-select: none;
    }

    .card--skeleton {
        background: var(--card-bg, #ffffff) !important;
        border-color: var(--card-border, rgba(0, 0, 0, 0.06)) !important;
    }

    .skeleton {
        display: block;
        background: linear-gradient(90deg, #e8e8e8 25%, #f5f5f5 50%, #e8e8e8 75%);
        background-size: 200% 100%;
        animation: skeleton-shimmer 1.5s ease-in-out infinite;
        border-radius: 6px;
        color: transparent !important;
    }

    .skeleton--badge {
        width: 5rem;
        height: 1.2rem;
        border-radius: 999px;
    }

    .skeleton--badge-sm {
        width: 3.5rem;
    }

    .skeleton--title {
        width: 70%;
        height: 1.4rem;
        margin-bottom: 0.5rem;
    }

    .skeleton--text {
        width: 100%;
        height: 0.85rem;
        margin-bottom: 0.35rem;
    }

    .skeleton--text-short {
        width: 55%;
    }

    .skeleton--button {
        width: 5rem;
        height: 2.2rem;
        border-radius: 8px;
    }

    @keyframes skeleton-shimmer {
        0% { background-position: 200% 0; }
        100% { background-position: -200% 0; }
    }
</style>
