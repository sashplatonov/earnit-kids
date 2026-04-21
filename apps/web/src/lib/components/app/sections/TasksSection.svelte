<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import type { Child } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { earnCoins, requestCoins, saveChildGroupOrder } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import {
        applyGroupOrderToChildren,
        getEffectiveGroupOrder,
        hasSavedGroupOrder,
        moveGroup,
        normalizeGroupLabel,
        orderGroups,
        sortItemsByGroup,
    } from '$lib/services/groupOrder';
    import { showToast } from '$lib/stores/toasts';

    let selectedGroup = '';
    let isEditingGroupOrder = false;
    let isSavingGroupOrder = false;
    let groupOrderDraft: string[] = [];

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
    $: if (!isEditingGroupOrder) {
        groupOrderDraft = [...groups];
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

        const periodLabels: Record<string, string> = {
            day: 'день',
            week: 'неделю',
            month: 'месяц',
            year: 'год',
        };

        return `${limit} раз(а) в ${periodLabels[period] ?? period}`;
    }

    async function handleEarn(taskId: unknown) {
        const childId = resolvedChildId;
        const task = tasks.find((entry) => entry.id == taskId);
        if (!task) return;
        if (isAdmin) {
            const res = await earnCoins(taskId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(`+${task.coins} монет — ${String(task.title ?? task.name)}`, 'success');
            }
        } else {
            const result = await requestCoins(taskId);
            if (result.ok) {
                if (result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }
                showToast('Заявка отправлена!', 'success');
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

    function openGroupOrderEditor() {
        groupOrderDraft = [...groups];
        isEditingGroupOrder = true;
    }

    function cancelGroupOrderEditor() {
        isEditingGroupOrder = false;
    }

    function shiftGroup(index: number, direction: -1 | 1) {
        groupOrderDraft = moveGroup(groupOrderDraft, index, direction);
    }

    async function persistGroupOrder(nextOrder: string[]) {
        if (resolvedChildId == null) {
            showToast('Сначала выберите ребенка', 'error');
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
            showToast(
                isAdmin ? 'Порядок групп задач сохранен' : 'Твой порядок групп задач сохранен',
                'success'
            );
        } else {
            showToast(result.error, 'error');
        }
        isSavingGroupOrder = false;
    }

    async function saveGroupOrder() {
        await persistGroupOrder(groupOrderDraft);
    }

    async function resetGroupOrder() {
        await persistGroupOrder([]);
    }
</script>

<section class="section" id="tasks-section">
    <div class="section__header">
        <div class="section__header-titles">
            <h2>
                <span class="gamified-icon icon-tasks" aria-hidden="true"
                    style="width: 1.5rem; height: 1.5rem; margin-right: 0.5rem; vertical-align: middle;"></span>
                За что можно заработать
            </h2>
            <p class="section__subtitle">Выполняйте задания, чтобы получать монетки и опыт</p>
        </div>
        {#if isAdmin}
        <div class="section__buttons admin-only">
            <button class="btn btn--add" id="add-task-btn" on:click={openAddTask}>+ Добавить</button>
        </div>
        {/if}
    </div>

    {#if groups.length > 1}
    <nav class="group-nav" id="tasks-group-nav">
        <div class="group-nav__scroll">
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === ''} on:click={() => selectedGroup = ''}>
                Все
            </button>
            {#each groups as group (group)}
            <button class="group-nav__tab" class:group-nav__tab--active={selectedGroup === group}
                on:click={() => selectedGroup = group}>
                {group}
            </button>
            {/each}
        </div>
    </nav>

    <div class="group-order-toolbar">
        <p class="group-order-toolbar__hint">
            {#if isAdmin}
                Родитель задает порядок групп по умолчанию для этого ребенка.
            {:else}
                Можно переставить группы под себя, не меняя родительский порядок.
            {/if}
        </p>
        {#if !isEditingGroupOrder}
        <div class="group-order-toolbar__actions">
            <button class="btn btn--secondary btn--small" type="button" on:click={openGroupOrderEditor}>
                {isAdmin ? 'Настроить порядок' : 'Настроить под себя'}
            </button>
            {#if hasStoredGroupOrder}
            <button class="btn btn--secondary btn--small" type="button" on:click={resetGroupOrder} disabled={isSavingGroupOrder}>
                {isAdmin ? 'Сбросить' : 'К родительскому'}
            </button>
            {/if}
        </div>
        {/if}
    </div>

    {#if isEditingGroupOrder}
    <div class="group-order-panel" aria-live="polite">
        <div class="group-order-panel__header">
            <h3 class="group-order-panel__title">Порядок групп задач</h3>
            <p class="group-order-panel__description">
                {#if isAdmin}
                    Новый порядок станет основным для задач этого ребенка.
                {:else}
                    Этот порядок увидишь только ты. Родительский вариант останется отдельно.
                {/if}
            </p>
        </div>

        <div class="group-order-list" role="list">
            {#each groupOrderDraft as group, index (group)}
            <div class="group-order-row" role="listitem">
                <span class="group-order-row__index">{index + 1}</span>
                <span class="group-order-row__name">{group}</span>
                <div class="group-order-row__actions">
                    <button
                        class="group-order-row__btn"
                        type="button"
                        aria-label={`Поднять группу ${group}`}
                        on:click={() => shiftGroup(index, -1)}
                        disabled={index === 0 || isSavingGroupOrder}
                    >↑</button>
                    <button
                        class="group-order-row__btn"
                        type="button"
                        aria-label={`Опустить группу ${group}`}
                        on:click={() => shiftGroup(index, 1)}
                        disabled={index === groupOrderDraft.length - 1 || isSavingGroupOrder}
                    >↓</button>
                </div>
            </div>
            {/each}
        </div>

        <div class="group-order-panel__actions">
            <button class="btn btn--secondary btn--small" type="button" on:click={cancelGroupOrderEditor} disabled={isSavingGroupOrder}>
                Отмена
            </button>
            {#if hasStoredGroupOrder}
            <button class="btn btn--secondary btn--small" type="button" on:click={resetGroupOrder} disabled={isSavingGroupOrder}>
                {isAdmin ? 'Сбросить' : 'К родительскому'}
            </button>
            {/if}
            <button class="btn btn--primary btn--small" type="button" on:click={saveGroupOrder} disabled={isSavingGroupOrder}>
                {isSavingGroupOrder ? 'Сохраняю...' : 'Сохранить'}
            </button>
        </div>
    </div>
    {/if}
    {/if}

    {#if visibleTasks.length > 0}
    <div class="cards" id="tasks-list">
        {#each visibleTasks as task (task.id)}
        <div class="card card--task task-card">
            <div class="card__badge-row">
                <span class="card__badge card__badge--group">{task.groupName ?? 'Без группы'}</span>
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
            <p class="card__comment">Короткий шаг, который помогает заработать монетки и закрепить привычку.</p>
            {/if}
            <div class="card__meta">
                {#if task.moneyLimit != null}
                <span class="card__meta-item">До {task.moneyLimit} 💶</span>
                {/if}
                {#if task.ageMin != null || task.ageMax != null}
                <span class="card__meta-item">Возраст {task.ageMin ?? 0}-{task.ageMax ?? 18}</span>
                {/if}
            </div>
            <div class="card__actions">
                {#if isAdmin}
                <button class="btn btn--primary btn--small" on:click={() => handleEarn(task.id)}>
                    Начислить
                </button>
                <button class="btn btn--secondary btn--small admin-only" on:click={() => openEditTask(task)}>
                    Изменить
                </button>
                {:else}
                <button class="btn btn--primary" on:click={() => handleEarn(task.id)}>
                    Выполнил!
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
        <p class="empty-state__title">Пока нет заданий</p>
        <p class="empty-state__hint">
            {#if isAdmin}Создайте первую задачу — она сразу появится в списке ребенка.{:else}Попроси родителя добавить задания.{/if}
        </p>
        {#if isAdmin}
        <div class="empty-state__actions">
            <button class="btn btn--add" type="button" on:click={openAddTask}>Добавить задачу</button>
        </div>
        {/if}
    </div>
    {/if}
</section>
