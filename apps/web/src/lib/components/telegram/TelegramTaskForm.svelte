<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type Task } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { buildTaskPayload } from '$lib/services/taskPayload';
    import { getSemanticGraphic } from './semanticGraphics';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGraphicsPicker from './TelegramGraphicsPicker.svelte';

    export let open = false;
    export let task: Task | null = null;
    export let groupSuggestions: string[] = [];
    export let onClose: () => void = () => {};
    export let onSaved: () => void = () => {};

    const i18n = useI18n();

    let title = '';
    let groupName = '';
    let coins = 10;
    let freqLimit = '';
    let freqPeriod: 'day' | 'week' | 'month' | 'year' = 'week';
    let icon: string | null = null;
    let graphicOpen = false;
    let error = '';

    $: isEdit = task != null;
    $: currentGraphic = getSemanticGraphic(icon);

    $: if (open && task) {
        title = task.name ?? '';
        groupName = task.groupName ?? '';
        coins = task.coins ?? 10;
        icon = task.icon ?? null;
        const frequency = task.frequency as { limit?: number; period?: string } | null | undefined;
        freqLimit = String(frequency?.limit ?? '');
        freqPeriod = (frequency?.period as typeof freqPeriod) ?? 'week';
        error = '';
    } else if (open && !task) {
        title = ''; groupName = ''; coins = 10; icon = null; freqLimit = ''; freqPeriod = 'week'; error = '';
    }

    $: suggestions = [...new Set(groupSuggestions.filter(Boolean))];

    function save() {
        if (!title.trim()) { error = $i18n.t('app.telegram.taskForm.nameRequired'); return; }
        error = '';
        const payload = buildTaskPayload({
            id: task?.id,
            title,
            groupName,
            coins,
            comment: '',
            cueWhen: undefined,
            cueAction: undefined,
            freqLimit,
            freqPeriod,
            icon,
        });

        if (task) {
            appStore.setState({
                tasks: $appStore.tasks.map((item) => item.id == task.id ? ({ ...item, ...payload } as typeof item) : item),
            });
        } else {
            const newTask = { ...payload, id: Date.now() };
            appStore.setState({ tasks: [...$appStore.tasks, newTask as unknown as typeof $appStore.tasks[number]] });
        }
        void scheduleSave();
        onSaved();
        onClose();
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="task-form-title" tabindex="-1">
        <h2 id="task-form-title">{isEdit ? $i18n.t('app.telegram.taskForm.editTitle') : $i18n.t('app.telegram.taskForm.addTitle')}</h2>

        <label for="task-name">{$i18n.t('app.telegram.taskForm.nameLabel')}</label>
        <input id="task-name" class="input" bind:value={title} placeholder={$i18n.t('app.telegram.taskForm.namePlaceholder')} />

        <label for="task-graphic">{$i18n.t('app.telegram.taskForm.graphicLabel')}</label>
        <button class="field" id="task-graphic" type="button" on:click={() => graphicOpen = true}>
            <span class="gico"><TelegramIcon name={currentGraphic.key} size={20} label={currentGraphic.label} /></span>
            <span class="grow">{currentGraphic.label}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        <label for="task-coins">{$i18n.t('app.telegram.taskForm.coinsLabel')}</label>
        <input id="task-coins" class="input" type="number" inputmode="numeric" bind:value={coins} min="0" />

        <label for="task-group">{$i18n.t('app.telegram.taskForm.groupLabel')}</label>
        <input id="task-group" class="input" list="task-group-suggestions" bind:value={groupName} placeholder={$i18n.t('app.telegram.taskForm.groupPlaceholder')} />
        <datalist id="task-group-suggestions">
            {#each suggestions as group (group)}<option value={group}></option>{/each}
        </datalist>

        <label for="task-schedule">{$i18n.t('app.telegram.taskForm.scheduleLabel')}</label>
        <select id="task-schedule" class="input" bind:value={freqPeriod}>
            <option value="day">{$i18n.t('app.telegram.taskForm.scheduleDay')}</option>
            <option value="week">{$i18n.t('app.telegram.taskForm.scheduleWeek')}</option>
            <option value="month">{$i18n.t('app.telegram.taskForm.scheduleMonth')}</option>
            <option value="year">{$i18n.t('app.telegram.taskForm.scheduleYear')}</option>
        </select>

        {#if error}<p class="error" role="alert">{error}</p>{/if}

        <button class="primary" type="button" on:click={save}>{$i18n.t('app.telegram.taskForm.save')}</button>
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.taskForm.cancel')}</button>
    </div>
{/if}

<TelegramGraphicsPicker open={graphicOpen} title={$i18n.t('app.telegram.taskForm.graphicLabel')} initial={icon} on:select={(event) => icon = event.detail} on:close={() => graphicOpen = false} />

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    select.input { appearance:none; }
    .field { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.9rem; padding:.35rem .6rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; text-align:left; }
    .grow { flex:1; min-width:0; font-weight:600; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
