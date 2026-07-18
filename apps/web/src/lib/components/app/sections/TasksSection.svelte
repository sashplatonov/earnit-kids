<script lang="ts">
    import { browser } from '$app/environment';
    import { onMount } from 'svelte';
    import CardHeader from '$lib/components/app/CardHeader.svelte';
    import TodaySummary from '$lib/components/app/catalog/TodaySummary.svelte';
    import CatalogActionFeedback from '$lib/components/app/catalog/CatalogActionFeedback.svelte';
    import CatalogGroupNav from '$lib/components/app/catalog/CatalogGroupNav.svelte';
    import CatalogSectionHeader from '$lib/components/app/catalog/CatalogSectionHeader.svelte';
    import CatalogCard from '$lib/components/app/catalog/CatalogCard.svelte';
    import BulkActionToolbar from '$lib/components/app/BulkActionToolbar.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import SectionHeaderControls from '$lib/components/app/SectionHeaderControls.svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { bulkTaskAction, earnCoins, requestCoinsWithNote, saveChildGroupOrder } from '$lib/services/api';
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
    import { buildTodayTaskSummary } from '$lib/services/todayTaskViewModel';
    import { recordCatalogEvent } from '$lib/services/catalogTelemetry';
    import { readCatalogViewState, writeCatalogViewState } from '$lib/services/catalogViewState';
    import { buildTaskCatalogItemViewModel } from '$lib/services/catalogItemViewModel';

    const i18n = useI18n();

    type CardHeaderChip = {
        label: string;
        className?: string;
    };

    let selectedGroup = '';
    let isBulkMode = false;
    let selectedTaskIds: Array<number | string> = [];
    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let viewMode: CardViewMode = 'list';
    let groupOrderEditor: { openEditor: () => void } | null = null;
    let pendingTaskIds: string[] = [];
    let taskFeedback: Record<string, { status: 'pending' | 'success' | 'error'; message: string }> = {};
    const loadedViewRole: { value: CardViewRole | null } = { value: null };
    const loadedChildScope: { value: string } = { value: '' };

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
        isBulkMode = false;
        selectedTaskIds = [];
    }
    $: if (selectedGroup && groups.length > 0 && !groups.includes(selectedGroup)) {
        setSelectedGroup('', { replace: true });
    }
    $: {
        const nextChildScope = String(resolvedChildId ?? '');
        if (loadedChildScope.value !== nextChildScope) {
            isBulkMode = false;
            selectedTaskIds = [];
            taskFeedback = {};
            loadedChildScope.value = nextChildScope;
        }
    }

    $: visibleTasks = selectedGroup
        ? tasks
              .filter((task) => normalizeGroupLabel(task.groupName) === selectedGroup)
              .sort((a, b) => {
                  // Blocked tasks go to the bottom
                  const aActive = isTaskActive(a);
                  const bActive = isTaskActive(b);
                  if (aActive && !bActive) return -1;
                  if (!aActive && bActive) return 1;
                  if (!aActive && !bActive) return 0;
                  // Both active: sort by frequency limit (descending)
                  const aLimit = a.frequency?.limit ?? 0;
                  const bLimit = b.frequency?.limit ?? 0;
                  if (bLimit !== aLimit) return bLimit - aLimit;
                  return 0;
              })
        : sortItemsByGroup(tasks, groups, (task) => normalizeGroupLabel(task.groupName));
    $: todaySummary = buildTodayTaskSummary(tasks);
    $: rewardGoal = $appStore.shopItems.find((item) => String(item.id) === String(currentChild?.rewardGoalItemId)) ?? null;

    onMount(() => {
        const applyLocation = () => {
            const state = readCatalogViewState(new URL(window.location.href), viewMode);
            const resolvedGroup = groups.length === 0 || groups.includes(state.group) ? state.group : '';
            selectedGroup = resolvedGroup;
            viewMode = state.view;
            if (state.group !== resolvedGroup) {
                history.replaceState(history.state, '', writeCatalogViewState(new URL(window.location.href), { group: resolvedGroup }));
            }
        };
        applyLocation();
        window.addEventListener('popstate', applyLocation);
        return () => window.removeEventListener('popstate', applyLocation);
    });

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

    function formatLastCompletedAt(task: { lastCompletedAt?: unknown }) {
        if (typeof task.lastCompletedAt !== 'string' || !task.lastCompletedAt) {
            return '';
        }

        const parsed = new Date(task.lastCompletedAt);
        return Number.isNaN(parsed.getTime()) ? '' : $i18n.formatShortDate(parsed);
    }

    function taskLastCompletedLabel(task: { lastCompletedAt?: unknown }) {
        const date = formatLastCompletedAt(task);
        return date ? tTasks('section.lastCompleted', { date }) : '';
    }

    function taskCompactChips(task: {
        isActive?: unknown;
        groupName?: string | null;
        frequency?: { limit?: number; period?: string } | null;
        moneyLimit?: number | null;
        ageMin?: number | null;
        ageMax?: number | null;
        lastCompletedAt?: unknown;
    }): CardHeaderChip[] {
        const chips: CardHeaderChip[] = [
            { label: task.groupName ?? tTasks('section.noGroup'), className: 'card__compact-chip--group' },
        ];
        const frequency = formatFrequency(task.frequency);
        const moneyLimit = taskMoneyLimitLabel(task);
        const ageRange = taskAgeRangeLabel(task);
        const lastCompleted = taskLastCompletedLabel(task);

        if (frequency) chips.push({ label: frequency });
        if (moneyLimit) chips.push({ label: moneyLimit });
        if (ageRange) chips.push({ label: ageRange });
        if (lastCompleted) {
            chips.push({ label: lastCompleted, className: 'card__compact-chip--task-history' });
        }
        if (!isTaskActive(task)) {
            chips.push({ label: tTasks('section.blocked'), className: 'card__compact-chip--status card__compact-chip--status-locked' });
        }

        return chips;
    }

    function isTaskActive(task: { isActive?: unknown }) {
        return task.isActive !== false;
    }

    function taskActionKey(taskId: unknown) {
        return `${String(resolvedChildId ?? '')}:${String(taskId)}`;
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
        const taskKey = taskActionKey(taskId);
        const feedbackKey = String(taskId);
        if (pendingTaskIds.includes(taskKey)) return;
        const childId = resolvedChildId;
        const actionScope = String(childId ?? '');
        const task = tasks.find((entry) => entry.id == taskId);
        if (!task) return;
        pendingTaskIds = [...pendingTaskIds, taskKey];
        recordCatalogEvent({ name: 'task_action', surface: 'tasks', result: 'started' });
        taskFeedback = { ...taskFeedback, [feedbackKey]: { status: 'pending', message: tTasks('feedback.pending') } };
        const finish = (status: 'success' | 'error', message: string) => {
            pendingTaskIds = pendingTaskIds.filter((id) => id !== taskKey);
            if (String(resolvedChildId ?? '') !== actionScope) return;
            taskFeedback = { ...taskFeedback, [feedbackKey]: { status, message } };
        };
        if (isAdmin) {
            const res = await earnCoins(taskId, childId) as Record<string, unknown> | null;
            if (res) {
                if (String(resolvedChildId ?? '') === actionScope) applyDataSnapshot(res);
                showToast(
                    tTasks('toasts.awarded', {
                        amount: $i18n.formatNumber(Number(task.coins ?? 0)),
                        title: String(task.title ?? task.name),
                    }),
                    'success'
                );
                finish('success', tTasks('feedback.success'));
                recordCatalogEvent({ name: 'task_action', surface: 'tasks', result: 'success' });
            } else {
                finish('error', tTasks('feedback.error'));
                showToast(tTasks('feedback.error'), 'error');
                recordCatalogEvent({ name: 'task_action', surface: 'tasks', result: 'error' });
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
                if (String(resolvedChildId ?? '') === actionScope && result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tTasks('toasts.requestSent'), 'success');
                finish('success', tTasks('feedback.success'));
                recordCatalogEvent({ name: 'task_action', surface: 'tasks', result: 'success' });
                return;
            }

            showToast(result.error, 'error');
            finish('error', tTasks('feedback.error'));
            recordCatalogEvent({ name: 'task_action', surface: 'tasks', result: 'error' });
        }
    }

    function openAddTask() {
        modalStore.open('task-modal', { mode: 'add', groupSuggestions: groups });
    }

    function openTaskFromToday(taskId: number | string) {
        const card = document.querySelector<HTMLElement>(`[data-task-id="${String(taskId)}"]`);
        const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        card?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' });
        card?.focus({ preventScroll: true });
    }

    function openEditTask(task: unknown) {
        modalStore.open('task-modal', { mode: 'edit', task, groupSuggestions: groups });
    }

    function isTaskSelected(task: { id: number | string }) {
        return selectedTaskIds.some((id) => String(id) === String(task.id));
    }

    function clearBulkSelection() {
        isBulkMode = false;
        selectedTaskIds = [];
    }

    function toggleBulkMode() {
        if (isBulkMode) {
            clearBulkSelection();
            return;
        }

        isBulkMode = true;
        selectedTaskIds = [];
    }

    function setSelectedGroup(nextGroup: string, options?: { replace?: boolean }) {
        const resolvedGroup = groups.includes(nextGroup) ? nextGroup : '';
        const currentGroup = browser ? readCatalogViewState(new URL(window.location.href), viewMode).group : selectedGroup;
        if (selectedGroup === resolvedGroup && currentGroup === resolvedGroup) {
            return;
        }

        clearBulkSelection();
        selectedGroup = resolvedGroup;
        if (browser) {
            const url = writeCatalogViewState(new URL(window.location.href), { group: resolvedGroup });
            if (options?.replace) history.replaceState(history.state, '', url);
            else history.pushState(history.state, '', url);
        }
    }

    function selectAllVisibleTasks() {
        if (!isBulkMode) {
            isBulkMode = true;
        }
        selectedTaskIds = visibleTasks.map((task) => task.id);
    }

    async function runTaskBulkAction(action: 'delete' | 'block' | 'unblock' | 'change_group') {
        if (resolvedChildId == null) {
            showToast(tTasks('toasts.selectChildFirst'), 'error');
            return;
        }
        if (selectedTaskIds.length === 0) {
            return;
        }

        if (action === 'delete') {
            const confirmed = await confirmAction({
                title: tTasks('modal.confirmDeleteTitle'),
                description: tTasks('modal.confirmDeleteDescription'),
                confirmLabel: tTasks('modal.delete'),
                cancelLabel: tTasks('modal.cancel'),
                tone: 'danger',
            });
            if (!confirmed) {
                return;
            }
        }

        if (action === 'change_group') {
            const groupName = await requestGroupName({
                title: tTasks('bulk.changeGroupTitle'),
                description: tTasks('bulk.changeGroupDescription'),
                placeholder: tTasks('bulk.changeGroupPlaceholder'),
                confirmLabel: tTasks('bulk.changeGroupConfirm'),
                cancelLabel: tTasks('modal.cancel'),
                suggestions: groups,
            });
            if (groupName == null) {
                return;
            }

            const result = await bulkTaskAction({
                childId: resolvedChildId,
                action,
                taskIds: [...selectedTaskIds],
                groupName,
            });
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast(tTasks('toasts.bulkChangedGroup'), 'success');
                clearBulkSelection();
            } else {
                showToast(result.error, 'error');
            }
            return;
        }

        const result = await bulkTaskAction({
            childId: resolvedChildId,
            action,
            taskIds: [...selectedTaskIds],
        });
        if (!result.ok) {
            showToast(result.error, 'error');
            return;
        }

        if (result.data && typeof result.data === 'object') {
            applyDataSnapshot(result.data as Record<string, unknown>);
        }

        if (action === 'delete') {
            showToast(tTasks('toasts.bulkDeleted', { count: selectedTaskIds.length }), 'success');
        } else if (action === 'block') {
            showToast(tTasks('toasts.bulkBlocked', { count: selectedTaskIds.length }), 'success');
        } else {
            showToast(tTasks('toasts.bulkUnblocked', { count: selectedTaskIds.length }), 'success');
        }

        clearBulkSelection();
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
        if (browser) history.replaceState(history.state, '', writeCatalogViewState(new URL(window.location.href), { view: nextMode }));
    }

    function openGroupOrderEditor() {
        groupOrderEditor?.openEditor();
    }

</script>

<section class="section" id="tasks-section">
    <CatalogSectionHeader title={tTasks('section.title')} subtitle={tTasks('section.subtitle')} iconClass="icon-tasks">
        <SectionHeaderControls
            {isAdmin}
            addLabel={tTasks('section.add')}
            addId="add-task-btn"
            {viewMode}
            viewAriaLabel={tTasks('section.viewAria')}
            gridLabel={tTasks('section.viewGrid')}
            listLabel={tTasks('section.viewList')}
            orderLabel={isAdmin ? $i18n.t('app.groupOrder.configureAdmin') : $i18n.t('app.groupOrder.configureChild')}
            bulkLabel={isBulkMode ? tTasks('bulk.clear') : tTasks('bulk.toggle')}
            isBulkMode={isBulkMode}
            hasGroups={groups.length > 1}
            {isEditingGroupOrder}
            {isSavingGroupOrder}
            on:add={openAddTask}
            on:editOrder={openGroupOrderEditor}
            on:toggleBulkMode={toggleBulkMode}
            on:viewMode={(event) => setViewMode(event.detail)}
        />
    </CatalogSectionHeader>

    <BulkActionToolbar
        show={isBulkMode}
        selectedCount={selectedTaskIds.length}
        selectionLabel={tTasks('bulk.selected', { count: selectedTaskIds.length })}
        selectAllLabel={tTasks('bulk.selectAll')}
        deleteLabel={tTasks('bulk.delete')}
        blockLabel={tTasks('bulk.block')}
        unblockLabel={tTasks('bulk.unblock')}
        changeGroupLabel={tTasks('bulk.changeGroup')}
        clearLabel={tTasks('bulk.clear')}
        on:selectAll={selectAllVisibleTasks}
        on:delete={() => void runTaskBulkAction('delete')}
        on:block={() => void runTaskBulkAction('block')}
        on:unblock={() => void runTaskBulkAction('unblock')}
        on:changeGroup={() => void runTaskBulkAction('change_group')}
        on:clear={clearBulkSelection}
    />

    <TodaySummary
        summary={todaySummary}
        title={tTasks('today.title')}
        progressLabel={tTasks('today.progress', { completed: todaySummary.completedCount, available: todaySummary.limitCount })}
        emptyProgressLabel={tTasks('today.emptyProgress')}
        availableLabel={tTasks('today.available')}
        nextLabel={tTasks('today.next')}
        nextActionLabel={tTasks('today.open')}
        onNext={(task) => openTaskFromToday(task.id)}
        rewardGoal={rewardGoal}
        rewardGoalSelected={currentChild?.rewardGoalItemId != null}
        balance={$appStore.balance}
        goalLabel={tTasks('today.goal')}
        goalReadyLabel={tTasks('today.goalReady')}
        goalMissingLabel={(amount) => tTasks('today.goalMissing', { amount: $i18n.formatNumber(amount) })}
        goalEmptyLabel={tTasks(isAdmin ? 'today.goalEmptyAdmin' : 'today.goalEmpty')}
        goalStaleLabel={tTasks('today.goalStale')}
        formatNumber={(value) => $i18n.formatNumber(value)}
    />

    <CatalogGroupNav id="tasks-group-nav" {groups} selected={selectedGroup} allLabel={tTasks('section.all')}
        ariaLabel={tTasks('section.groupAria')} onSelect={setSelectedGroup} />

    {#if groups.length > 1}
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
    <div class="cards cards--skeleton-task" id="tasks-skeleton">
        {#each { length: 3 } as _, i (i)}
        <div class="card card--task card--skeleton-task">
            <div class="card__badge-row">
                <span class="skel-task skel-task--badge">&nbsp;</span>
                <span class="skel-task skel-task--badge skel-task--badge-sm">&nbsp;</span>
            </div>
            <div class="task-card__layout">
                <div class="task-card__main">
                    <div class="skel-task skel-task--title">&nbsp;</div>
                    <div class="skel-task skel-task--text">&nbsp;</div>
                    <div class="skel-task skel-task--text skel-task--text-short">&nbsp;</div>
                </div>
                <div class="task-card__side">
                    <div class="skel-task skel-task--button">&nbsp;</div>
                </div>
            </div>
        </div>
        {/each}
    </div>
    {:else if visibleTasks.length > 0}
    <div class="cards" class:cards--list={viewMode === 'list'} id="tasks-list">
        {#each visibleTasks as task (task.id)}
        {@const catalogItem = buildTaskCatalogItemViewModel(task)}
        <CatalogCard kind="task" view={viewMode} disabled={!catalogItem.active} selected={isTaskSelected(task)}
            additionalClass={!catalogItem.active ? 'task-card--inactive' : ''} let:classes>
        <article class={classes} data-task-id={String(task.id)} tabindex="-1">
            <div class="card__badge-row">
                {#if isAdmin && isBulkMode && viewMode !== 'list'}
                <label class="bulk-select">
                    <input
                        type="checkbox"
                        bind:group={selectedTaskIds}
                        value={task.id}
                        aria-label={tTasks('section.selectAria', { title: String(task.title ?? task.name ?? '') })}
                    />
                </label>
                {/if}
                <span class="card__badge card__badge--group">{task.groupName ?? tTasks('section.noGroup')}</span>
                {#if formatFrequency(task.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(task.frequency)}</span>
                {/if}
                {#if taskLastCompletedLabel(task)}
                <span class="card__badge card__badge--history">{taskLastCompletedLabel(task)}</span>
                {/if}
                {#if !isTaskActive(task)}
                <span class="card__status card__status--locked">{tTasks('section.blocked')}</span>
                {/if}
            </div>
            <div class="task-card__layout">
                {#if isAdmin && isBulkMode && viewMode === 'list'}
                <label class="task-card__select-cell bulk-select">
                    <input
                        type="checkbox"
                        bind:group={selectedTaskIds}
                        value={task.id}
                        aria-label={tTasks('section.selectAria', { title: String(task.title ?? task.name ?? '') })}
                    />
                </label>
                {/if}
                <div class="task-card__main">
                    <CardHeader
                        title={catalogItem.title}
                        amount={String(catalogItem.amount)}
                        amountClass="task-coins"
                        compactChips={taskCompactChips(task)}
                    />
                    {#if task.comment}
                    <p class="card__comment">{task.comment}</p>
                    {:else}
                    <p class="card__comment">{tTasks('section.defaultComment')}</p>
                    {/if}
                    {#if task.cueWhen && task.cueAction}
                    <p class="task-card__cue">{tTasks('section.cueSentence', { when: task.cueWhen, action: task.cueAction })}</p>
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
                    <div class="card__action-area">
                        <div class="card__actions">
                            {#if isAdmin}
                            <button class="btn btn--primary btn--small" data-task-action="award" disabled={!isTaskActive(task) || pendingTaskIds.includes(taskActionKey(task.id))} on:click={() => handleEarn(task.id)}>
                                {tTasks('actions.award')}
                            </button>
                            <button class="btn btn--secondary btn--small admin-only" data-task-action="edit" on:click={() => openEditTask(task)}>
                                {tTasks('actions.edit')}
                            </button>
                            {:else}
                            <button class="btn btn--primary" data-task-action="request" disabled={!isTaskActive(task) || pendingTaskIds.includes(taskActionKey(task.id))} on:click={() => handleEarn(task.id)}>
                                {tTasks('actions.complete')}
                            </button>
                            {/if}
                        </div>
                        <CatalogActionFeedback status={pendingTaskIds.includes(taskActionKey(task.id)) ? 'pending' : taskFeedback[String(task.id)]?.status ?? 'idle'}
                            message={pendingTaskIds.includes(taskActionKey(task.id)) ? tTasks('feedback.pending') : taskFeedback[String(task.id)]?.message ?? ''}
                            retryLabel={tTasks('feedback.retry')} onRetry={() => void handleEarn(task.id)} />
                    </div>
                </div>
            </div>
        </article>
        </CatalogCard>
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

    .card__action-area {
        display: grid;
        min-width: 0;
    }

    .task-card__cue {
        margin: 0.55rem 0 0;
        overflow-wrap: anywhere;
        color: var(--color-text-muted);
        font-size: var(--text-sm);
        line-height: var(--line-height-normal);
    }

    .task-card--selected {
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

    .task-card__side {
        display: flex;
        flex-direction: column;
        gap: 0.8rem;
    }

    .task-card--list {
        min-height: 0;
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
        flex-wrap: nowrap;
        align-items: center;
        gap: 0.5rem 0.75rem;
    }

    .task-card--list .task-card__main {
        flex: 1 1 0;
        min-width: 0;
    }

    .task-card__select-cell {
        display: inline-flex;
        align-self: center;
        flex: 0 0 auto;
        padding-right: 0.1rem;
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
        min-width: 0;
        min-height: var(--catalog-control-height);
        padding: 0.38rem 0.7rem;
        font-size: 0.82rem;
    }

    .card__badge--history {
        background: rgba(56, 189, 248, 0.14);
        color: #0f4c81;
    }

    :global(.card__compact-chip--task-history) {
        background: rgba(56, 189, 248, 0.14);
        color: #0f4c81;
    }

    .task-card--inactive {
        opacity: 0.72;
    }

    @media (max-width: 640px) {
        .task-card--list {
            padding: 0.38rem 0.46rem 0.38rem 0.56rem;
        }

        .task-card--list .task-card__layout {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 0.48rem;
        }

        .task-card__select-cell {
            align-self: center;
            padding-right: 0;
        }

        .task-card--list .task-card__side {
            width: 100%;
            min-width: 0;
            display: block;
            flex: 1 0 100%;
        }

        .task-card--list .card__actions {
            width: 100%;
            min-height: var(--catalog-control-height);
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(min(7.5rem, 100%), 1fr));
            gap: 0.4rem;
        }

        .task-card--list .card__actions .btn {
            min-width: 0;
            min-height: var(--catalog-control-height);
            padding: 0.45rem 0.55rem;
            font-size: var(--text-xs);
            line-height: 1.15;
            white-space: normal;
        }
    }

    /* ── Skeleton loader (scoped names to avoid global .skeleton conflicts) ── */
    .cards--skeleton-task {
        pointer-events: none;
        user-select: none;
    }

    .card--skeleton-task {
        background: var(--card-bg, #ffffff) !important;
        border-color: var(--card-border, rgba(0, 0, 0, 0.06)) !important;
    }

    .skel-task {
        display: block;
        background: linear-gradient(90deg, #e8e8e8 25%, #f5f5f5 50%, #e8e8e8 75%);
        background-size: 200% 100%;
        animation: skel-task-shimmer 1.5s ease-in-out infinite;
        border-radius: 6px;
        color: transparent !important;
    }

    .skel-task--badge {
        width: 5rem;
        height: 1.2rem;
        border-radius: 999px;
    }

    .skel-task--badge-sm {
        width: 3.5rem;
    }

    .skel-task--title {
        width: 70%;
        height: 1.4rem;
        margin-bottom: 0.5rem;
    }

    .skel-task--text {
        width: 100%;
        height: 0.85rem;
        margin-bottom: 0.35rem;
    }

    .skel-task--text-short {
        width: 55%;
    }

    .skel-task--button {
        width: 5rem;
        height: 2.2rem;
        border-radius: 8px;
    }

    @keyframes skel-task-shimmer {
        0% { background-position: 200% 0; }
        100% { background-position: -200% 0; }
    }
</style>
