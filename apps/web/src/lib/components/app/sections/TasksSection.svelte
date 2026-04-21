<script lang="ts">
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
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    let selectedGroup = '';
    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;

    function tTasks(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`tasks.${key}` as MessageKey, variables);
    }

    $: tasks = $appStore.tasks;
    $: isAdmin = $appStore.isAdmin;

    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = (($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null);
    $: rawGroups = [...new Set(tasks.map((task) => normalizeGroupLabel(task.groupName)))];
    $: groups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'tasks', isAdmin));
    $: hasStoredGroupOrder = hasSavedGroupOrder(currentChild, 'tasks', isAdmin);
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
        {#if isAdmin}
        <div class="section__buttons admin-only">
            <button class="btn btn--add" id="add-task-btn" on:click={openAddTask}>{tTasks('section.add')}</button>
        </div>
        {/if}
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
        on:save={handleGroupOrderSave}
        on:reset={handleGroupOrderReset}
    />
    {/if}

    {#if visibleTasks.length > 0}
    <div class="cards" id="tasks-list">
        {#each visibleTasks as task (task.id)}
        <div class="card card--task task-card">
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{task.groupName ?? tTasks('section.noGroup')}</span>
                {#if formatFrequency(task.frequency)}
                <span class="card__badge card__badge--type">{formatFrequency(task.frequency)}</span>
                {/if}
            </div>
            <div class="card__header">
                <h3 class="card__title">{task.title ?? task.name}</h3>
                <div class="card__coins task-coins">
                    <span class="gamified-icon icon-coin" aria-hidden="true"></span>
                    <span>{task.coins}</span>
                </div>
            </div>
            {#if task.comment}
            <p class="card__comment">{task.comment}</p>
            {:else}
            <p class="card__comment">{tTasks('section.defaultComment')}</p>
            {/if}
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
