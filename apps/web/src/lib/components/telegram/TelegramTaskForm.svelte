<script lang="ts">
    import { run } from 'svelte/legacy';

    import { useI18n } from '$lib/i18n/context';
    import type { MessageKey } from '$lib/i18n';
    import { appStore, type Task } from '$lib/stores/app';
    import { useTaskActions } from '$lib/telegram/services/taskActions';
    import { buildTaskPayload } from '$lib/services/taskPayload';
    import { getSemanticGraphic } from './semanticGraphics';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGraphicsPicker from './TelegramGraphicsPicker.svelte';
    import TelegramGroupPicker from './TelegramGroupPicker.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';

    interface Props {
        open?: boolean;
        task?: Task | null;
        groupSuggestions?: string[];
        onClose?: () => void;
        onSaved?: () => void;
    }

    let {
        open = false,
        task = null,
        groupSuggestions = [],
        onClose = () => {},
        onSaved = () => {}
    }: Props = $props();

    const i18n = useI18n();
    const taskActions = useTaskActions();

    let title = $state('');
    let groupName = $state('');
    let coins = $state(10);
    let freqLimit = $state('');
    let freqPeriod: 'day' | 'week' | 'month' | 'year' = $state('week');
    let icon: string | null = $state(null);
    let graphicOpen = $state(false);
    let groupPickerOpen = $state(false);
    let error = $state('');

    let isEdit = $derived(task != null);
    let currentGraphic = $derived(getSemanticGraphic(icon));
    let currentGraphicLabel = $derived($i18n.t(`app.telegram.graphics.labels.${currentGraphic.key}` as MessageKey));
    let suggestions = $derived([...new Set(groupSuggestions.filter(Boolean))]);

    run(() => {
        if (open && task) {
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
    });

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

        void taskActions.saveTask(task, payload as unknown as Partial<Task>).then(() => { onSaved(); onClose(); });
    }
</script>

{#if open}
    <TelegramBottomSheet open={open} labelledBy="task-form-title" {onClose}>
        <h2 id="task-form-title">{isEdit ? $i18n.t('app.telegram.taskForm.editTitle') : $i18n.t('app.telegram.taskForm.addTitle')}</h2>

        <label for="task-name">{$i18n.t('app.telegram.taskForm.nameLabel')}</label>
        <input id="task-name" class="input" bind:value={title} placeholder={$i18n.t('app.telegram.taskForm.namePlaceholder')} />

        <label for="task-graphic">{$i18n.t('app.telegram.taskForm.graphicLabel')}</label>
        <button class="field" id="task-graphic" type="button" onclick={() => graphicOpen = true}>
            <span class="gico"><TelegramIcon name={currentGraphic.key} size={20} label={currentGraphicLabel} /></span>
            <span class="grow">{currentGraphicLabel}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        <label for="task-coins">{$i18n.t('app.telegram.taskForm.coinsLabel')}</label>
        <input id="task-coins" class="input" type="number" inputmode="numeric" bind:value={coins} min="0" />

        <button class="field" id="task-group" type="button" onclick={() => groupPickerOpen = true}>
            <span class="gico"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', group: groupName })} size={20} label={groupName || $i18n.t('app.telegram.taskForm.groupPlaceholder')} /></span>
            <span class="grow">{groupName || $i18n.t('app.telegram.taskForm.groupPlaceholder')}</span>
            <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
        </button>

        <label for="task-schedule">{$i18n.t('app.telegram.taskForm.scheduleLabel')}</label>
        <select id="task-schedule" class="input" bind:value={freqPeriod}>
            <option value="day">{$i18n.t('app.telegram.taskForm.scheduleDay')}</option>
            <option value="week">{$i18n.t('app.telegram.taskForm.scheduleWeek')}</option>
            <option value="month">{$i18n.t('app.telegram.taskForm.scheduleMonth')}</option>
            <option value="year">{$i18n.t('app.telegram.taskForm.scheduleYear')}</option>
        </select>

        {#if error}<p class="error" role="alert">{error}</p>{/if}

        <button class="primary" type="button" onclick={save}><TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.taskForm.save')} />{$i18n.t('app.telegram.taskForm.save')}</button>
        <button class="close" type="button" onclick={onClose}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.taskForm.cancel')} />{$i18n.t('app.telegram.taskForm.cancel')}</button>
    </TelegramBottomSheet>
{/if}

<TelegramGraphicsPicker open={graphicOpen} title={$i18n.t('app.telegram.taskForm.graphicLabel')} initial={icon} onSelect={(key) => icon = key} onClose={() => graphicOpen = false} />
<TelegramGroupPicker open={groupPickerOpen} groups={suggestions} selected={groupName} title={$i18n.t('app.telegram.groupPicker.title')} onSelect={(group) => groupName = group} onClose={() => groupPickerOpen = false} />

<style>
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    label { display:block; margin:.6rem 0 .3rem; color:#33415f; font-weight:600; font-size:.85rem; }
    .input { box-sizing:border-box; width:100%; min-height:2.75rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    select.input { appearance:none; }
    .field { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.9rem; padding:.35rem .6rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; text-align:left; }
    .grow { flex:1; min-width:0; font-weight:600; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .primary { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .close { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #f1c7ca; border-radius:.7rem; background:#fff7f7; color:#a84a50; font:inherit; font-weight:600; cursor:pointer; }
</style>
