<script lang="ts">
    import { stopPropagation } from 'svelte/legacy';

    import { createEventDispatcher } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import type { MessageKey } from '$lib/i18n';
    import { appStore, type Task } from '$lib/stores/app';
    import type { ShopItem } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { confirmAction } from '$lib/services/confirm';
    import { moveGroup } from '$lib/telegram/services/groupOrder';
    import { useTaskActions } from '$lib/telegram/services/taskActions';
    import { useRewardActions } from '$lib/telegram/services/rewardActions';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import { getSemanticGraphic } from './semanticGraphics';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGraphicsPicker from './TelegramGraphicsPicker.svelte';

    interface Props {
        open?: boolean;
        kind?: 'tasks' | 'shop';
        onClose?: () => void;
    }

    let { open = false, kind = 'tasks', onClose = () => {} }: Props = $props();

    const i18n = useI18n();
    const taskActions = useTaskActions();
    const rewardActions = useRewardActions();
    const dispatch = createEventDispatcher<{
        save: { groups: string[]; hiddenGroups: string[] };
        deleteGroup: { group: string; moveTo: string | null };
    }>();

    let items = $derived(kind === 'tasks' ? ($appStore.tasks as Task[]) : ($shopItems as ShopItem[]));
    let currentChild = $derived($appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null);
    let groups = $derived([...new Set(items.map((item) => item.groupName).filter((group): group is string => Boolean(group)))]);
    let hiddenGroups = $derived(kind === 'tasks'
        ? (currentChild?.hiddenTaskGroupOrder ?? [])
        : (currentChild?.hiddenShopGroupOrder ?? []));
    let orderedGroups = $derived([...groups]);

    let formOpen = $state(false);
    let editingGroup: string | null = $state(null);
    let groupName = $state('');
    let groupIcon: string | null = $state(null);
    let graphicOpen = $state(false);
    let openMenuId: string | null = $state(null);
    let menuTrigger: HTMLButtonElement | null = null;
    let error = $state('');
    let moveTargetOpen = $state(false);
    let pendingDeleteGroup: string | null = $state(null);
    let reorderOpen = $state(false);
    let reorderDraft: string[] = $state([]);

    let title = $derived(kind === 'tasks'
        ? $i18n.t('app.telegram.groupManager.taskTitle')
        : $i18n.t('app.telegram.groupManager.rewardTitle'));

    function countFor(group: string): number {
        return items.filter((item) => item.groupName === group).length;
    }

    function plural(count: number): string {
        const gen = kind === 'tasks'
            ? $i18n.t('app.telegram.groupManager.itemsTaskGen')
            : $i18n.t('app.telegram.groupManager.itemsRewardGen');
        return $i18n.t('app.telegram.groupManager.count', { count, items: gen });
    }

    function toggleMenu(group: string, button: HTMLButtonElement) {
        if (openMenuId === group) closeMenu(true);
        else { menuTrigger = button; openMenuId = group; }
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

    function openCreate() {
        editingGroup = null;
        groupName = '';
        groupIcon = null;
        error = '';
        formOpen = true;
    }
    function openEdit(group: string) {
        closeMenu(true);
        editingGroup = group;
        groupName = group;
        groupIcon = loadGroupIcon(group);
        error = '';
        formOpen = true;
    }

    function saveGroup() {
        const name = groupName.trim();
        if (!name) { error = $i18n.t('app.telegram.taskForm.nameRequired'); return; }
        error = '';
        if (editingGroup != null && editingGroup !== name) {
            // Rename: update items + group order + hidden list.
            const nextGroups = orderedGroups.map((g) => g === editingGroup ? name : g);
            const nextHidden = hiddenGroups.map((g) => g === editingGroup ? name : g);
            if (kind === 'tasks') void taskActions.deleteGroup(editingGroup, name);
            else void rewardActions.deleteGroup(editingGroup, name);
            saveGroupOrder(nextGroups, nextHidden);
            saveGroupIcon(name, groupIcon);
        } else if (editingGroup == null) {
            // Create: append to order.
            const nextGroups = orderedGroups.includes(name) ? orderedGroups : [...orderedGroups, name];
            saveGroupOrder(nextGroups, hiddenGroups);
            saveGroupIcon(name, groupIcon);
        }
        formOpen = false;
    }

    function saveGroupOrder(groups: string[], hidden: string[]) {
        dispatch('save', { groups, hiddenGroups: hidden });
    }

    function saveGroupIcon(name: string, icon: string | null) {
        const key = `earnit:group-icon:${kind}:${name}`;
        void key;
        void icon;
    }
    function loadGroupIcon(name: string): string | null {
        void name;
        return null;
    }

    function moveUp(group: string) {
        closeMenu(true);
        const index = orderedGroups.indexOf(group);
        const next = moveGroup(orderedGroups, index, -1);
        saveGroupOrder(next, hiddenGroups);
    }
    function moveDown(group: string) {
        closeMenu(true);
        const index = orderedGroups.indexOf(group);
        const next = moveGroup(orderedGroups, index, 1);
        saveGroupOrder(next, hiddenGroups);
    }

    function toggleArchive(group: string) {
        closeMenu(true);
        const nextHidden = hiddenGroups.includes(group)
            ? hiddenGroups.filter((g) => g !== group)
            : [...hiddenGroups, group];
        saveGroupOrder(orderedGroups, nextHidden);
    }

    async function removeGroup(group: string) {
        closeMenu();
        const count = countFor(group);
        const confirmed = await confirmAction({
            title: $i18n.t('app.telegram.groupManager.deleteTitle'),
            description: $i18n.t('app.telegram.groupManager.deleteDescription', {
                count,
                items: kind === 'tasks'
                    ? $i18n.t('app.telegram.groupManager.itemsTaskGen')
                    : $i18n.t('app.telegram.groupManager.itemsRewardGen'),
            }),
            confirmLabel: $i18n.t('app.telegram.groupManager.delete'),
            cancelLabel: $i18n.t('app.telegram.groupManager.cancel'),
            tone: 'danger',
        });
        if (!confirmed) return;

        if (count === 0) {
            // Empty group: just remove from order + hidden.
            const nextGroups = orderedGroups.filter((g) => g !== group);
            const nextHidden = hiddenGroups.filter((g) => g !== group);
            saveGroupOrder(nextGroups, nextHidden);
            return;
        }

        // Non-empty group: ask where to move items.
        pendingDeleteGroup = group;
        moveTargetOpen = true;
    }

    function confirmMoveTarget(moveTo: string | null) {
        const group = pendingDeleteGroup;
        pendingDeleteGroup = null;
        moveTargetOpen = false;
        if (group == null) return;
        dispatch('deleteGroup', { group, moveTo });
    }

    function openReorder() {
        closeMenu(true);
        reorderDraft = [...orderedGroups];
        reorderOpen = true;
    }
    function moveReorder(index: number, delta: number) {
        const next = [...reorderDraft];
        const target = index + delta;
        if (target < 0 || target >= next.length) return;
        const [item] = next.splice(index, 1);
        next.splice(target, 0, item);
        reorderDraft = next;
    }
    function saveReorder() {
        saveGroupOrder(reorderDraft, hiddenGroups);
        reorderOpen = false;
    }

    let currentGraphic = $derived(getSemanticGraphic(groupIcon));
    let currentGraphicLabel = $derived($i18n.t(`app.telegram.graphics.labels.${currentGraphic.key}` as MessageKey));
</script>

<svelte:window onclick={() => openMenuId = null} onkeydown={handleWindowKeydown} />

{#if open}
    <div class="sheet-backdrop" role="presentation" onclick={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="group-manager-title" tabindex="-1">
        <div class="sheet-header">
            <h2 id="group-manager-title">{title}</h2>
            <button class="add" type="button" onclick={openCreate}><TelegramIcon name="add" size={18} label={$i18n.t('app.telegram.groupManager.addGroup')} /><span>{$i18n.t('app.telegram.groupManager.newGroup')}</span></button>
        </div>

        {#if !orderedGroups.length}
            <p class="muted">{$i18n.t('app.telegram.groupManager.empty')}</p>
        {:else}
            <div class="list" role="list">
                {#each orderedGroups as group (group)}
                    <div class="row" class:archived={hiddenGroups.includes(group)} role="listitem">
                        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: kind === 'tasks' ? 'task' : 'reward', group })} size={20} label={$i18n.t('app.telegram.groupManager.groupIcon', { name: group })} /></span>
                        <span class="entity-text">
                            <span class="title">{group}</span>
                            <span class="meta">{plural(countFor(group))}</span>
                        </span>
                        <div class="menu-wrap">
                            <button class="more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: group })} aria-haspopup="menu" aria-expanded={openMenuId === group} onclick={stopPropagation((event) => toggleMenu(group, event.currentTarget as HTMLButtonElement))}><TelegramIcon name="more" size={20} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                            {#if openMenuId === group}
                                <div class="menu" role="menu" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: group })}>
                                    <button role="menuitem" type="button" onclick={() => openEdit(group)}><TelegramIcon name="edit" size={16} label={$i18n.t('app.telegram.groupManager.edit')} /><span>{$i18n.t('app.telegram.groupManager.edit')}</span></button>
                                    <button role="menuitem" type="button" onclick={() => { closeMenu(true); reorderOpen = true; }}><TelegramIcon name="reorder" size={16} label={$i18n.t('app.telegram.groupManager.reorder')} /><span>{$i18n.t('app.telegram.groupManager.reorder')}</span></button>
                                    <button role="menuitem" type="button" onclick={() => toggleArchive(group)}><TelegramIcon name="archive" size={16} label={hiddenGroups.includes(group) ? $i18n.t('app.telegram.groupManager.unarchive') : $i18n.t('app.telegram.groupManager.archive')} /><span>{hiddenGroups.includes(group) ? $i18n.t('app.telegram.groupManager.unarchive') : $i18n.t('app.telegram.groupManager.archive')}</span></button>
                                    <div class="menu-divider" role="presentation"></div>
                                    <button role="menuitem" class="danger" type="button" onclick={() => void removeGroup(group)}><TelegramIcon name="delete" size={16} label={$i18n.t('app.telegram.groupManager.delete')} /><span>{$i18n.t('app.telegram.groupManager.delete')}</span></button>
                                </div>
                            {/if}
                        </div>
                    </div>
                {/each}
            </div>
        {/if}

        <button class="close" type="button" onclick={onClose}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.groupManager.close')} />{$i18n.t('app.telegram.groupManager.close')}</button>
    </div>
{/if}

{#if formOpen}
    <div class="sheet-backdrop" role="presentation" onclick={() => formOpen = false}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="group-form-title" tabindex="-1">
        <h2 id="group-form-title">{editingGroup != null ? $i18n.t('app.telegram.groupManager.editGroup') : $i18n.t('app.telegram.groupManager.newGroup')}</h2>

        <label for="group-name">{$i18n.t('app.telegram.groupManager.groupNameLabel')}</label>
        <input id="group-name" class="input" bind:value={groupName} placeholder={$i18n.t('app.telegram.groupManager.groupNamePlaceholder')} />

        <label for="group-graphic">{$i18n.t('app.telegram.taskForm.graphicLabel')}</label>
        <button class="field" id="group-graphic" type="button" onclick={() => graphicOpen = true}>
            <span class="gico"><TelegramIcon name={currentGraphic.key} size={20} label={currentGraphicLabel} /></span>
            <span class="grow">{currentGraphicLabel}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        {#if error}<p class="error" role="alert">{error}</p>{/if}

        <button class="primary" type="button" onclick={saveGroup}><TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.groupManager.save')} />{$i18n.t('app.telegram.groupManager.save')}</button>
        <button class="close" type="button" onclick={() => formOpen = false}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.groupManager.cancel')} />{$i18n.t('app.telegram.groupManager.cancel')}</button>
    </div>
{/if}

<TelegramGraphicsPicker open={graphicOpen} title={$i18n.t('app.telegram.taskForm.graphicLabel')} initial={groupIcon} onSelect={(key) => groupIcon = key} onClose={() => graphicOpen = false} />

{#if moveTargetOpen}
    <div class="sheet-backdrop" role="presentation" onclick={() => moveTargetOpen = false}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="move-target-title" tabindex="-1">
        <h2 id="move-target-title">{$i18n.t('app.telegram.groupManager.moveToTitle')}</h2>
        <div class="list" role="list">
            {#each orderedGroups.filter((g) => g !== pendingDeleteGroup) as group (group)}
                <button class="sheet-item" type="button" onclick={() => confirmMoveTarget(group)}>{group}</button>
            {/each}
            <button class="sheet-item" type="button" onclick={() => confirmMoveTarget(null)}>{$i18n.t('app.telegram.groupManager.leaveUngrouped')}</button>
        </div>
        <button class="close" type="button" onclick={() => moveTargetOpen = false}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.groupManager.cancel')} />{$i18n.t('app.telegram.groupManager.cancel')}</button>
    </div>
{/if}

{#if reorderOpen}
    <div class="sheet-backdrop" role="presentation" onclick={() => reorderOpen = false}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="reorder-title" tabindex="-1">
        <h2 id="reorder-title">{$i18n.t('app.telegram.groupManager.reorderTitle')}</h2>
        <div class="list" role="list">
            {#each reorderDraft as group, index (group)}
                <div class="row" role="listitem">
                    <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: kind === 'tasks' ? 'task' : 'reward', group })} size={20} label={$i18n.t('app.telegram.groupManager.groupIcon', { name: group })} /></span>
                    <span class="entity-text"><span class="title">{group}</span></span>
                    <div class="reorder-actions">
                        <button class="reorder-btn" type="button" aria-label={$i18n.t('app.telegram.groupManager.moveUp')} disabled={index === 0} onclick={() => moveReorder(index, -1)}><TelegramIcon name="arrowUp" size={18} label={$i18n.t('app.telegram.groupManager.moveUp')} /></button>
                        <button class="reorder-btn" type="button" aria-label={$i18n.t('app.telegram.groupManager.moveDown')} disabled={index === reorderDraft.length - 1} onclick={() => moveReorder(index, 1)}><TelegramIcon name="arrowDown" size={18} label={$i18n.t('app.telegram.groupManager.moveDown')} /></button>
                    </div>
                </div>
            {/each}
        </div>
        <button class="primary" type="button" onclick={saveReorder}><TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.groupManager.done')} />{$i18n.t('app.telegram.groupManager.done')}</button>
        <button class="close" type="button" onclick={() => reorderOpen = false}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.groupManager.cancel')} />{$i18n.t('app.telegram.groupManager.cancel')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    .sheet-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.5rem; }
    h2 { margin:0; color:#18243d; font-size:1.15rem; }
    .add { display:inline-flex; align-items:center; gap:.3rem; min-height:2.75rem; padding:.35rem .6rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; max-height:50vh; overflow-y:auto; }
    .row { display:flex; align-items:center; gap:.6rem; min-height:3.5rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row.archived { opacity:.6; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; flex:1; }
    .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .meta { display:block; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .more { width:2.75rem; height:2.75rem; display:grid; place-items:center; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:12rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .menu-divider { height:1px; margin:.25rem 0; background:#edf0f5; }
    .reorder-actions { display:flex; align-items:center; gap:.25rem; flex:0 0 auto; }
    .reorder-btn { width:2.75rem; height:2.75rem; display:grid; place-items:center; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#66718a; cursor:pointer; }
    .reorder-btn:disabled { opacity:.4; cursor:not-allowed; }
    .muted { color:#66718a; }
    .close { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #f1c7ca; border-radius:.7rem; background:#fff7f7; color:#a84a50; font:inherit; font-weight:600; cursor:pointer; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { width:100%; min-height:2.75rem; padding:.5rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#18243d; font:inherit; }
    .field { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#18243d; font:inherit; cursor:pointer; }
    .gico { display:grid; place-items:center; width:2rem; height:2rem; flex:0 0 auto; border-radius:.55rem; background:#eef0ff; color:#5b63e9; }
    .grow { flex:1; min-width:0; text-align:left; }
    .primary { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .error { color:#c63c42; font-size:.85rem; margin:.4rem 0 0; }
    .sheet-item { display:flex; align-items:center; width:100%; min-height:2.75rem; padding:0 .6rem; border:0; border-bottom:1px solid #edf0f5; border-radius:0; background:#fff; color:#18243d; font:inherit; font-weight:600; text-align:left; cursor:pointer; }
    .sheet-item:last-child { border-bottom:0; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
