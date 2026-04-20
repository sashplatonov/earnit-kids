<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { earnCoins, requestCoins } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    $: tasks = $appStore.tasks;
    $: isAdmin = $appStore.isAdmin;

    // Group tasks by groupName
    $: groups = [...new Set(tasks.map(t => t.groupName ?? 'Без группы'))];
    let selectedGroup = '';

    $: visibleTasks = selectedGroup
        ? tasks.filter(t => (t.groupName ?? 'Без группы') === selectedGroup)
        : tasks;

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
        const childId = $appStore.currentChildId;
        const task = tasks.find(t => t.id == taskId);
        if (!task) return;
        if (isAdmin) {
            const res = await earnCoins(taskId, childId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast(`+${task.coins} монет — ${task.title}`, 'success');
            }
        } else {
            const res = await requestCoins(taskId) as Record<string, unknown> | null;
            if (res) {
                applyDataSnapshot(res);
                showToast('Заявка отправлена!', 'success');
            }
        }
    }

    function openAddTask() {
        modalStore.open('task-modal', { mode: 'add' });
    }

    function openEditTask(task: unknown) {
        modalStore.open('task-modal', { mode: 'edit', task });
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
        <button class="group-nav__btn" class:active={selectedGroup === ''} on:click={() => selectedGroup = ''}>
            Все
        </button>
        {#each groups as group (group)}
        <button class="group-nav__btn" class:active={selectedGroup === group}
            on:click={() => selectedGroup = group}>
            {group}
        </button>
        {/each}
    </nav>
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
                <h3 class="card__title">{task.title}</h3>
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
            {#if task.moneyLimit != null || task.ageMin != null || task.ageMax != null}
            <div class="card__meta">
                {#if task.moneyLimit != null}
                <span class="card__meta-item">До {task.moneyLimit} €</span>
                {/if}
                {#if task.ageMin != null || task.ageMax != null}
                <span class="card__meta-item">Возраст {task.ageMin ?? 0}-{task.ageMax ?? 18}</span>
                {/if}
            </div>
            {/if}
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
