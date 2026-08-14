<script lang="ts">
    import { requestCoins, requestCoinsWithNote } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { buildTodayTaskSummary } from '$lib/services/todayTaskViewModel';
    import TelegramGroupedCatalog, { type CatalogItem } from './TelegramGroupedCatalog.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';

    const i18n = useI18n();

    let selected: CatalogItem | null = null;
    let busy = false;
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = 'idle';
    let message = '';
    $: summary = buildTodayTaskSummary($appStore.tasks);
    $: progressPercent = summary.limitCount > 0 ? Math.min(100, Math.round((summary.completedCount / summary.limitCount) * 100)) : 0;
    $: pendingIds = $appStore.requests.filter((request) => request.requestType !== 'shop_purchase' && request.status === 'pending').map((request) => request.taskId).filter((id): id is string | number => id != null);
    $: items = $appStore.tasks.filter((task) => task.isActive !== false).map((task): CatalogItem => ({ id: task.id, title: task.name, amount: task.coins, group: task.groupName, available: task.periodProgress?.available !== false, disabledReason: task.periodProgress?.available === false ? $i18n.t('app.telegram.childTasks.limitReached') : undefined }));
    async function submit(note: string | null) {
        if (!selected || busy) return;
        busy = true; status = 'pending'; message = $i18n.t('app.telegram.childTasks.sendingRequest');
        const result = note ? await requestCoinsWithNote(selected.id, note) : await requestCoins(selected.id);
        busy = false;
        if (result.ok) { if (result.data && typeof result.data === 'object') applyDataSnapshot(result.data as Record<string, unknown>); status = 'success'; message = $i18n.t('app.telegram.childTasks.requestSent'); selected = null; }
        else if (result.errorCode === 'STALE_STATE') { await refreshData(); status = 'stale'; message = $i18n.t('app.telegram.childTasks.taskChanged'); selected = null; }
        else { status = 'error'; message = result.error; selected = null; }
    }
</script>

<section aria-labelledby="child-tasks-title"><div class="heading"><h2 id="child-tasks-title">{$i18n.t('app.telegram.childTasks.tasksToday')}</h2><span>{summary.trackedCount > 0 ? $i18n.t('app.telegram.childTasks.done', { completed: summary.completedCount, limit: summary.limitCount }) : $i18n.t('app.telegram.childTasks.available', { count: $appStore.tasks.length })}</span></div>{#if summary.trackedCount > 0}<div class="today-progress" role="progressbar" aria-valuemin="0" aria-valuemax={summary.limitCount} aria-valuenow={summary.completedCount} aria-label={$i18n.t('app.telegram.childTasks.todayProgress')}><span style={`width: ${progressPercent}%`}></span></div>{/if}<TelegramGroupedCatalog kind="task" {items} {pendingIds} on:request={(event) => { selected = event.detail; status = 'idle'; }} /><TelegramActionStatus state={status} message={message} /></section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { margin:0; color:#18243d; font-size:1.2rem; } span { color:#66718a; font-size:.8rem; } .today-progress { height:.4rem; margin-bottom:.75rem; overflow:hidden; border-radius:999px; background:#e8e9f4; } .today-progress span { display:block; height:100%; border-radius:inherit; background:#5b63e9; transition:width .3s ease; }
</style>
