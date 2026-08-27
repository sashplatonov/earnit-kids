<script lang="ts">
    import { useTaskActions } from '$lib/telegram/services/taskActions';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { buildTodayTaskSummary } from '$lib/services/todayTaskViewModel';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import TelegramCatalogToolbar from './TelegramCatalogToolbar.svelte';
    import { sortCatalogItems, type CatalogSortMode } from '$lib/telegram/services/catalogSort';
    import { getEffectiveGroupOrder, orderGroups } from '$lib/telegram/services/groupOrder';

    const i18n = useI18n();
    const taskActions = useTaskActions();

    let selectedGroup = $state('');
    let sortMode: CatalogSortMode = $state('group');
    let selected: { id: number | string; title: string } | null = $state(null);
    let busy = $state(false);
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = $state('idle');
    let message = $state('');
    let currentChild = $derived($appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null);
    let hiddenGroups = $derived(currentChild?.hiddenTaskGroupOrder ?? []);
    let visibleTasks = $derived($appStore.tasks.filter((task) => !hiddenGroups.includes(task.groupName?.trim() ?? '')));
    let summary = $derived(buildTodayTaskSummary(visibleTasks));
    let progressPercent = $derived(summary.limitCount > 0 ? Math.min(100, Math.round((summary.completedCount / summary.limitCount) * 100)) : 0);
    let pendingIds = $derived($appStore.requests.filter((request) => request.requestType !== 'shop_purchase' && request.status === 'pending').map((request) => request.taskId).filter((id): id is string | number => id != null));
    let rawGroups = $derived([...new Set(visibleTasks.filter((task) => task.isActive !== false).map((task) => task.groupName?.trim()).filter((group): group is string => Boolean(group)))]);
    let orderedGroups = $derived(orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'tasks', false)));
    let items = $derived(sortCatalogItems(selectedGroup ? visibleTasks.filter((task) => task.isActive !== false && task.groupName?.trim() === selectedGroup) : visibleTasks.filter((task) => task.isActive !== false), sortMode, orderedGroups, (task) => task.groupName?.trim() ?? '', (task) => task.coins));
    const isPending = (id: number | string) => pendingIds.some((pendingId) => String(pendingId) === String(id));
    const isLimitReached = (task: { periodProgress?: { available?: boolean } | null }) => task.periodProgress?.available === false;
    async function submit(note: string | null) {
        if (!selected || busy) return;
        busy = true; status = 'pending'; message = $i18n.t('app.telegram.childTasks.sendingRequest');
        const result = await taskActions.request({ taskId: selected.id, note, childId: $appStore.currentChildId ?? undefined });
        busy = false;
        if (result.ok) { if (result.data && typeof result.data === 'object') taskActions.applySnapshot(result.data as Record<string, unknown>); status = 'success'; message = $i18n.t('app.telegram.childTasks.requestSent'); selected = null; }
        else if (result.errorCode === 'STALE_STATE') { await taskActions.refresh(); status = 'stale'; message = $i18n.t('app.telegram.childTasks.taskChanged'); selected = null; }
        else { status = 'error'; message = result.error; selected = null; }
    }
</script>

<section aria-labelledby="child-tasks-title"><div class="heading"><h2 id="child-tasks-title">{$i18n.t('app.telegram.childTasks.tasksToday')}</h2><span>{summary.trackedCount > 0 ? $i18n.t('app.telegram.childTasks.done', { completed: summary.completedCount, limit: summary.limitCount }) : $i18n.t('app.telegram.childTasks.available', { count: visibleTasks.length })}</span></div>{#if summary.trackedCount > 0}<div class="today-progress" role="progressbar" aria-valuemin="0" aria-valuemax={summary.limitCount} aria-valuenow={summary.completedCount} aria-label={$i18n.t('app.telegram.childTasks.todayProgress')}><span style={`width: ${progressPercent}%`}></span></div>{/if}<TelegramGroupSubnav groups={rawGroups} selected={selectedGroup} kind="tasks" allLabel={$i18n.t('app.telegram.groupSubnav.all')} moreLabel={$i18n.t('app.telegram.groupSubnav.more')} allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')} onSelect={(group) => selectedGroup = group} />{#if visibleTasks.length}<TelegramCatalogToolbar count={items.length} countLabel={$i18n.t('app.telegram.sort.tasksShown')} mode={sortMode} onChange={(mode) => sortMode = mode} />{/if}{#if selectedGroup && !items.length}<p class="empty">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>{:else if !items.length}<p class="empty">{$i18n.t('app.telegram.childTasks.noTasks')}</p>{:else}<TelegramListSurface label={$i18n.t('app.telegram.childTasks.tasksToday')}>{#each items as task (task.id)}<TelegramEntityRow isInteractive>
    {#snippet icon()}
        <span><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', title: task.name, group: task.groupName, semantic: task.icon ?? null })} size={20} label={$i18n.t('app.telegram.childTasks.tasksToday')} /></span>
    {/snippet}
    {#snippet title()}
        <button class="row-main" type="button" aria-label={stripLeadingEmoji(task.name)} onclick={() => { selected = { id: task.id, title: task.name }; status = 'idle'; }}><span class="title">{stripLeadingEmoji(task.name)}</span><span class="row-metadata"><span class="meta"><TelegramCoin size={13} />+{task.coins} · {task.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span>{#if task.lastCompletedAt}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.lastCompleted', { when: formatLastUsedTime(task.lastCompletedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.tasks.neverCompleted')}</span>{/if}</span></button>
    {/snippet}
    {#snippet interactive()}
        <button class="row-action check" type="button" aria-label={$i18n.t('app.telegram.childTasks.request')} disabled={isPending(task.id) || isLimitReached(task)} onclick={() => { selected = { id: task.id, title: task.name }; status = 'idle'; }}><TelegramIcon name={isPending(task.id) ? 'refresh' : 'done'} size={16} label={isPending(task.id) ? $i18n.t('app.telegram.childTasks.pending') : $i18n.t('app.telegram.childTasks.request')} /></button>
    {/snippet}
</TelegramEntityRow>{/each}</TelegramListSurface>{/if}<TelegramActionStatus state={status} message={message} /></section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { margin:0; color:#18243d; font-size:1.2rem; } .heading > span { color:#66718a; font-size:.8rem; } .today-progress { height:.4rem; margin-bottom:.75rem; overflow:hidden; border-radius:999px; background:#e8e9f4; } .today-progress span { display:block; height:100%; border-radius:inherit; background:#5b63e9; transition:width .3s ease; } .check { background:#3867d6; color:#fff; } .check:disabled { opacity:.5; cursor:not-allowed; } .empty { padding:1rem 0; color:#66718a; text-align:center; }
</style>
