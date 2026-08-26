<script lang="ts">
    import { appStore, type Task } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { useTaskActions } from '$lib/telegram/services/taskActions';
    import { confirmAction } from '$lib/services/confirm';
    import { orderGroups } from '$lib/telegram/services/groupOrder';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramTaskForm from './TelegramTaskForm.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramGroupManager from './TelegramGroupManager.svelte';
    import TelegramParentCatalog from './TelegramParentCatalog.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';
    import TelegramCatalogToolbar from './TelegramCatalogToolbar.svelte';
    import { sortCatalogItems, type CatalogSortMode } from '$lib/telegram/services/catalogSort';

    const i18n = useI18n();
    const taskActions = useTaskActions();

    $: rawGroups = [...new Set($appStore.tasks.map((task) => task.groupName).filter((group): group is string => Boolean(group)))];
    $: currentChild = $appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null;
    $: groups = orderGroups(rawGroups, currentChild?.taskGroupOrder);
    $: hiddenGroups = currentChild?.hiddenTaskGroupOrder ?? [];
    let selectedGroup = '';
    let sortMode: CatalogSortMode = 'group';
    let catalogOpen = false;
    $: filteredTasks = sortCatalogItems(selectedGroup
        ? $appStore.tasks.filter((task) => task.groupName === selectedGroup)
        : $appStore.tasks, sortMode, groups, (task) => task.groupName ?? '', (task) => task.coins);
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
    async function toggleArchive(task: Task) {
        closeMenu(true);
        await taskActions.archiveTask(task);
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
        await taskActions.deleteTask(task);
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
        const result = await taskActions.complete({ taskId: task.id, childId: $appStore.currentChildId }) as Record<string, unknown> | null;
        completingId = null;
        if (result) {
            taskActions.applySnapshot(result);
        } else {
            completeError = $i18n.t('app.telegram.tasks.completeError');
            await taskActions.refresh();
        }
    }
    async function saveGroups(event: CustomEvent<{ groups: string[]; hiddenGroups: string[] }>) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await taskActions.saveGroups($appStore.currentChildId, event.detail.groups, event.detail.hiddenGroups);
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
    async function handleDeleteGroup(event: CustomEvent<{ group: string; moveTo: string | null }>) {
        const { group, moveTo } = event.detail;
        await taskActions.deleteGroup(group, moveTo);
        const nextGroups = groups.filter((g) => g !== group);
        const nextHidden = hiddenGroups.filter((g) => g !== group);
        await saveGroups(new CustomEvent('save', { detail: { groups: nextGroups, hiddenGroups: nextHidden } }));
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
        <TelegramCatalogToolbar count={filteredTasks.length} countLabel={$i18n.t('app.telegram.sort.tasksShown')} mode={sortMode} onChange={(mode) => sortMode = mode} />
        {#if selectedGroup && !filteredTasks.length}
            <p class="muted empty-group">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>
        {:else}
        <TelegramListSurface label={$i18n.t('app.telegram.tasks.title')}>
            {#each filteredTasks as task (task.id)}
                <TelegramEntityRow interactive={canEdit} archived={task.isActive === false} compact>
                    <span slot="icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', title: task.name, group: task.groupName, semantic: task.icon ?? null })} size={20} label={$i18n.t('app.telegram.tasks.task')} /></span>
                    <button slot="title" class="row-main" type="button" aria-label={$i18n.t('app.telegram.tasks.editItem', { name: stripLeadingEmoji(task.name) })} on:click={() => edit(task)}>
                        <span class="title">{stripLeadingEmoji(task.name)}</span>
                        <span class="row-metadata"><span class="meta"><TelegramCoin size={13} />{task.coins} · {task.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span>{#if task.lastCompletedAt}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.lastCompleted', { when: formatLastUsedTime(task.lastCompletedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.neverCompleted')}</span>{/if}</span>
                    </button>
                    <svelte:fragment slot="interactive">
                    {#if canEdit}
                        <button class="row-action check" type="button" aria-label={$i18n.t('app.telegram.tasks.completeShort')} disabled={task.isActive === false || completingId != null} on:click|stopPropagation={() => confirmComplete = task}><TelegramIcon name="done" size={16} label={$i18n.t('app.telegram.tasks.completeShort')} /></button>
                        <div class="menu-wrap">
                            <button class="row-action more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(task.name) })} aria-haspopup="menu" aria-expanded={openMenuId === task.id} on:click|stopPropagation={(event) => toggleMenu(task.id, event.currentTarget as HTMLButtonElement)}><TelegramIcon name="more" size={20} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
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
                    </svelte:fragment>
                </TelegramEntityRow>
            {/each}
        </TelegramListSurface>
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
    <TelegramBottomSheet open labelledBy="task-complete-title" busy={completingId != null} onClose={() => confirmComplete = null}>
        <h2 id="task-complete-title">{$i18n.t('app.telegram.tasks.completeShort')}</h2>
        <div class="complete-row">
            <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', title: confirmComplete.name, group: confirmComplete.groupName, semantic: confirmComplete.icon ?? null })} size={20} label={$i18n.t('app.telegram.tasks.task')} /></span>
            <span class="grow"><span class="title">{stripLeadingEmoji(confirmComplete.name)}</span><span class="meta"><TelegramCoin size={13} />{confirmComplete.coins} · {confirmComplete.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span></span>
        </div>
        <div class="delta"><span>{$i18n.t('app.telegram.tasks.completeChild')}</span><b>{$appStore.childNickname || $i18n.t('app.telegram.header.child')}</b></div>
        <div class="delta"><span>{$i18n.t('app.telegram.tasks.completeAward')}</span><b class="award">+{confirmComplete.coins} <TelegramCoin size={13} /></b></div>
        <div class="actions">
            <button class="cancel" type="button" on:click={() => confirmComplete = null}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.tasks.cancel')} />{$i18n.t('app.telegram.tasks.cancel')}</button>
            <button class="primary" type="button" disabled={completingId != null} on:click={() => confirmComplete && void completeForChild(confirmComplete)}><TelegramIcon name="done" size={16} label={$i18n.t('app.telegram.tasks.completeShort')} />{$i18n.t('app.telegram.tasks.completeShort')}</button>
        </div>
    </TelegramBottomSheet>
{/if}

<style>
    .tasks { box-sizing:border-box; width:100%; max-width:100%; min-width:0; }
    .page-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { margin:0; color:#18243d; font-size:1.35rem; }
    .header-actions { display:flex; align-items:center; gap:.25rem; }
    .add { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .catalog { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .empty-group { padding:1rem 0; text-align:center; }

    .more { color:#66718a; }
    .check { border:1px solid #cbd3e2; border-radius:.5rem; background:#fff; color:#17884b; font-weight:900; }
    .check:disabled { opacity:.5; cursor:not-allowed; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:11rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .menu button:disabled { opacity:.5; cursor:not-allowed; }
    .menu-divider { height:1px; margin:.25rem 0; background:#edf0f5; }
    .complete-row { display:flex; align-items:center; gap:.6rem; padding:.4rem 0; }
    .complete-row .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .complete-row .grow { flex:1; min-width:0; }
    .complete-row .title { display:block; color:#18243d; font-weight:600; font-size:.95rem; line-height:1.3; }
    .complete-row .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .delta { display:flex; align-items:center; justify-content:space-between; gap:.6rem; margin-top:.5rem; padding:.6rem .7rem; border-radius:.6rem; background:#f4f6f9; color:#33415f; font-size:.9rem; }
    .delta b { font-weight:700; }
    .delta .award { display:inline-flex; align-items:center; gap:.25rem; color:#17884b; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.9rem; }
    .actions .cancel { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions .primary { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .actions .primary:disabled { cursor:wait; opacity:.6; }
    .muted { color:#66718a; }
    .error { margin:.75rem 0 0; padding:.6rem .75rem; border-radius:.75rem; background:#fff0f0; color:#a33b3b; font-size:.875rem; }
    button.groups { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; color:#18243d; font:inherit; font-weight:700; cursor:pointer; }
    button.groups span { display:inline-flex; align-items:center; }
    .group-message { display:block; margin-top:.4rem; text-align:center; color:#66718a; font-size:.85rem; }
</style>
