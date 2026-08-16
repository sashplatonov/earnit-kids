<script lang="ts">
    import { appStore, type Task } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { earnCoins, saveChildGroupOrder } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { confirmAction } from '$lib/services/confirm';
    import { scheduleSave } from '$lib/services/save';
    import { orderGroups } from '$lib/services/groupOrder';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramTaskForm from './TelegramTaskForm.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramGroupManager from './TelegramGroupManager.svelte';
    import TelegramParentCatalog from './TelegramParentCatalog.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';

    const i18n = useI18n();

    $: rawGroups = [...new Set($appStore.tasks.map((task) => task.groupName).filter((group): group is string => Boolean(group)))];
    $: currentChild = $appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null;
    $: groups = orderGroups(rawGroups, currentChild?.taskGroupOrder);
    $: hiddenGroups = currentChild?.hiddenTaskGroupOrder ?? [];
    let selectedGroup = '';
    let catalogOpen = false;
    $: filteredTasks = selectedGroup
        ? $appStore.tasks.filter((task) => task.groupName === selectedGroup)
        : $appStore.tasks;
    $: canEdit = $appStore.permission !== 'viewer';
    let groupMessage = '';
    let groupEditorOpen = false;
    let groupSaving = false;
    let openMenuId: string | number | null = null;
    let menuTrigger: HTMLButtonElement | null = null;
    let formOpen = false;
    let editingTask: Task | null = null;
    function toggleMenu(id: string | number, button: HTMLButtonElement) {
        if (openMenuId === id) closeMenu(true);
        else { menuTrigger = button; openMenuId = id; }
    }
    function closeMenu(restoreFocus = false) {
        openMenuId = null;
        if (restoreFocus && menuTrigger?.isConnected) menuTrigger.focus();
        menuTrigger = null;
    }
    function handleWindowKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && openMenuId != null) {
            event.preventDefault();
            closeMenu(true);
        }
    }
    function add() { editingTask = null; formOpen = true; }
    function edit(task: unknown) { closeMenu(); editingTask = task as Task; formOpen = true; }
    function toggleArchive(task: Task) {
        closeMenu(true);
        const nextActive = task.isActive === false;
        appStore.setState({
            tasks: $appStore.tasks.map((item) => item.id == task.id ? ({ ...item, isActive: nextActive } as typeof item) : item),
        });
        void scheduleSave();
    }
    async function remove(task: Task) {
        closeMenu();
        const confirmed = await confirmAction({
            title: $i18n.t('app.telegram.tasks.deleteTitle'),
            description: $i18n.t('app.telegram.tasks.deleteDescription', { name: stripLeadingEmoji(task.name) }),
            confirmLabel: $i18n.t('app.telegram.tasks.delete'),
            cancelLabel: $i18n.t('app.telegram.tasks.cancel'),
            tone: 'danger',
        });
        if (!confirmed) return;
        appStore.setState({ tasks: $appStore.tasks.filter((item) => item.id != task.id) });
        void scheduleSave();
    }
    // EXPLAIN: Parent directly completes a task for the current child, awarding
    // EXPLAIN: coins without a child request. Reuses earnCoins (POST /complete).
    let completingId: string | number | null = null;
    let completeError = '';
    let confirmComplete: Task | null = null;
    async function completeForChild(task: Task) {
        if ($appStore.currentChildId == null || completingId != null) return;
        closeMenu();
        confirmComplete = null;
        completingId = task.id;
        completeError = '';
        const result = await earnCoins(task.id, $appStore.currentChildId) as Record<string, unknown> | null;
        completingId = null;
        if (result) {
            applyDataSnapshot(result);
        } else {
            completeError = $i18n.t('app.telegram.tasks.completeError');
            await refreshData();
        }
    }
    async function saveGroups(event: CustomEvent<{ groups: string[]; hiddenGroups: string[] }>) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await saveChildGroupOrder($appStore.currentChildId, 'tasks', event.detail.groups, event.detail.hiddenGroups);
        groupSaving = false;
        groupMessage = result.ok ? $i18n.t('app.telegram.tasks.groupsSaved') : $i18n.t('app.telegram.tasks.groupsSaveError');
        if (result.ok) {
            groupEditorOpen = false;
            appStore.setState({
                children: $appStore.children.map((child) =>
                    String(child.id) === String($appStore.currentChildId)
                        ? { ...child, taskGroupOrder: event.detail.groups, hiddenTaskGroupOrder: event.detail.hiddenGroups }
                        : child
                ),
            });
        }
    }
    function handleDeleteGroup(event: CustomEvent<{ group: string; moveTo: string | null }>) {
        const { group, moveTo } = event.detail;
        const nextTasks = $appStore.tasks.map((task) =>
            task.groupName === group ? { ...task, groupName: moveTo ?? null } as typeof task : task
        );
        appStore.setState({ tasks: nextTasks });
        void scheduleSave();
        const nextGroups = groups.filter((g) => g !== group);
        const nextHidden = hiddenGroups.filter((g) => g !== group);
        void saveGroups(new CustomEvent('save', { detail: { groups: nextGroups, hiddenGroups: nextHidden } }));
    }
</script>

<svelte:window on:click={() => openMenuId = null} on:keydown={handleWindowKeydown} />

<div class="tasks">
    <div class="page-header">
        <h1 id="tasks-title">{$i18n.t('app.telegram.tasks.title')}</h1>
        <div class="header-actions">
            {#if canEdit}<button class="add" type="button" aria-label={$i18n.t('app.telegram.tasks.addTask')} on:click={add}><TelegramIcon name="add" size={18} label={$i18n.t('app.telegram.tasks.addTask')} /></button>{/if}
            <button class="catalog" type="button" on:click={() => catalogOpen = true}><TelegramIcon name="book" size={18} label={$i18n.t('app.telegram.readyCatalog.catalogTasks')} /><span>{$i18n.t('app.telegram.tasks.catalogShort')}</span></button>
        </div>
    </div>

    {#if catalogOpen}
        <TelegramParentCatalog kind="task" onBack={() => catalogOpen = false} />
    {:else if !$appStore.tasks.length}
        <p class="muted">{$i18n.t('app.telegram.tasks.noTasks')}</p>
    {:else}
        <TelegramGroupSubnav
            {groups}
            selected={selectedGroup}
            kind="tasks"
            allLabel={$i18n.t('app.telegram.groupSubnav.all')}
            moreLabel={$i18n.t('app.telegram.groupSubnav.more')}
            allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')}
            onSelect={(group) => selectedGroup = group}
        />
        {#if selectedGroup && !filteredTasks.length}
            <p class="muted empty-group">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>
        {:else}
        <div class="list" aria-label={$i18n.t('app.telegram.tasks.title')}>
            {#each filteredTasks as task (task.id)}
                <div class:archived={task.isActive === false} class="row">
                    <button class="row-main" type="button" aria-label={$i18n.t('app.telegram.tasks.editItem', { name: stripLeadingEmoji(task.name) })} on:click={() => edit(task)}>
                        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', title: task.name, group: task.groupName, semantic: task.icon ?? null })} size={20} label={$i18n.t('app.telegram.tasks.task')} /></span>
                        <span class="entity-text">
                            <span class="title">{stripLeadingEmoji(task.name)}</span>
                            <span class="meta"><TelegramCoin size={13} />{task.coins} · {stripLeadingEmoji(task.groupName || $i18n.t('app.telegram.tasks.ungrouped'))}</span>
                            {#if task.lastCompletedAt}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.lastCompleted', { when: formatLastUsedTime(task.lastCompletedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.neverCompleted')}</span>{/if}
                        </span>
                    </button>
                    {#if canEdit}
                        <button class="check" type="button" aria-label={$i18n.t('app.telegram.tasks.completeShort')} disabled={task.isActive === false || completingId != null} on:click|stopPropagation={() => confirmComplete = task}><TelegramIcon name="done" size={18} label={$i18n.t('app.telegram.tasks.completeShort')} /></button>
                        <div class="menu-wrap">
                            <button class="more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(task.name) })} aria-haspopup="menu" aria-expanded={openMenuId === task.id} on:click|stopPropagation={(event) => toggleMenu(task.id, event.currentTarget as HTMLButtonElement)}><TelegramIcon name="more" size={20} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                            {#if openMenuId === task.id}
                                <div class="menu" role="menu" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(task.name) })}>
                                    <button role="menuitem" type="button" on:click={() => edit(task)}><TelegramIcon name="edit" size={16} label={$i18n.t('app.telegram.tasks.edit')} /><span>{$i18n.t('app.telegram.tasks.edit')}</span></button>
                                    <button role="menuitem" type="button" on:click={() => toggleArchive(task)}><TelegramIcon name="archive" size={16} label={task.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')} /><span>{task.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')}</span></button>
                                    <div class="menu-divider" role="presentation"></div>
                                    <button role="menuitem" class="danger" type="button" on:click={() => void remove(task)}><TelegramIcon name="delete" size={16} label={$i18n.t('app.telegram.tasks.delete')} /><span>{$i18n.t('app.telegram.tasks.delete')}</span></button>
                                </div>
                            {/if}
                        </div>
                    {/if}
                </div>
            {/each}
        </div>
        {/if}
    {/if}

    {#if !catalogOpen && canEdit}
        <button class="groups" type="button" on:click={() => groupEditorOpen = true}>
            <TelegramIcon name="filter" size={16} label={$i18n.t('app.telegram.tasks.manageGroups')} />
            <span>{$i18n.t('app.telegram.tasks.manageGroups')}</span>
        </button>
        {#if groupMessage}<span role="status" class="group-message">{groupMessage}</span>{/if}
    {/if}
    {#if completeError}<p class="error" role="alert">{completeError}</p>{/if}
</div>
<TelegramTaskForm open={formOpen} task={editingTask} groupSuggestions={groups} onClose={() => formOpen = false} />
<TelegramGroupManager open={groupEditorOpen} kind="tasks" onClose={() => groupEditorOpen = false} on:save={saveGroups} on:deleteGroup={handleDeleteGroup} />

{#if confirmComplete}
    <div class="sheet-backdrop" role="presentation" on:click={() => confirmComplete = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="task-complete-title" tabindex="-1">
        <h2 id="task-complete-title">{$i18n.t('app.telegram.tasks.completeShort')}</h2>
        <div class="complete-row">
            <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', title: confirmComplete.name, group: confirmComplete.groupName, semantic: confirmComplete.icon ?? null })} size={20} label={$i18n.t('app.telegram.tasks.task')} /></span>
            <span class="grow"><span class="title">{stripLeadingEmoji(confirmComplete.name)}</span><span class="meta"><TelegramCoin size={13} />{confirmComplete.coins} · {stripLeadingEmoji(confirmComplete.groupName || $i18n.t('app.telegram.tasks.ungrouped'))}</span></span>
        </div>
        <div class="delta"><span>{$i18n.t('app.telegram.tasks.completeChild')}</span><b>{$appStore.childNickname || $i18n.t('app.telegram.header.child')}</b></div>
        <div class="delta"><span>{$i18n.t('app.telegram.tasks.completeAward')}</span><b class="award">+{confirmComplete.coins} <TelegramCoin size={13} /></b></div>
        <div class="actions">
            <button class="cancel" type="button" on:click={() => confirmComplete = null}>{$i18n.t('app.telegram.tasks.cancel')}</button>
            <button class="primary" type="button" disabled={completingId != null} on:click={() => confirmComplete && void completeForChild(confirmComplete)}>{$i18n.t('app.telegram.tasks.completeShort')}</button>
        </div>
    </div>
{/if}

<style>
    .tasks { width:100%; }
    .page-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { margin:0; color:#18243d; font-size:1.35rem; }
    .header-actions { display:flex; align-items:center; gap:.25rem; }
    .add { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .catalog { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .empty-group { padding:1rem 0; text-align:center; }
    .row { display:flex; align-items:center; gap:.25rem; min-height:3.5rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row.archived { opacity:.6; }
    .row-main { display:flex; align-items:center; gap:.6rem; flex:1; min-width:0; min-height:3.5rem; padding:.3rem 0; border:0; background:transparent; text-align:left; cursor:pointer; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; }
    .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; }
    .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .meta--last { color:#8a93a8; font-size:.75rem; }
    .more { width:2.75rem; height:2.75rem; display:grid; place-items:center; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .check { width:2.75rem; height:2.75rem; flex:0 0 auto; display:grid; place-items:center; border:1px solid #cbd3e2; border-radius:.65rem; background:#fff; color:#17884b; font-weight:900; cursor:pointer; }
    .check:disabled { opacity:.5; cursor:not-allowed; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:11rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .menu button:disabled { opacity:.5; cursor:not-allowed; }
    .menu-divider { height:1px; margin:.25rem 0; background:#edf0f5; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    .sheet h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .complete-row { display:flex; align-items:center; gap:.6rem; padding:.4rem 0; }
    .complete-row .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .complete-row .grow { flex:1; min-width:0; }
    .complete-row .title { display:block; color:#18243d; font-weight:600; font-size:.95rem; line-height:1.3; }
    .complete-row .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .delta { display:flex; align-items:center; justify-content:space-between; gap:.6rem; margin-top:.5rem; padding:.6rem .7rem; border-radius:.6rem; background:#f4f6f9; color:#33415f; font-size:.9rem; }
    .delta b { font-weight:700; }
    .delta .award { display:inline-flex; align-items:center; gap:.25rem; color:#17884b; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.9rem; }
    .actions .cancel { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions .primary { min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .actions .primary:disabled { cursor:wait; opacity:.6; }
    .muted { color:#66718a; }
    .error { margin:.75rem 0 0; padding:.6rem .75rem; border-radius:.75rem; background:#fff0f0; color:#a33b3b; font-size:.875rem; }
    button.groups { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; color:#18243d; font:inherit; font-weight:700; cursor:pointer; }
    button.groups span { display:inline-flex; align-items:center; }
    .group-message { display:block; margin-top:.4rem; text-align:center; color:#66718a; font-size:.85rem; }
</style>
