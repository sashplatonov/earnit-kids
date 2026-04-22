<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { buildTaskPayload } from '$lib/services/taskPayload';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tTasks(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`tasks.${key}` as MessageKey, variables);
    }

    $: isOpen = $modalStore.open === 'task-modal';
    $: modalData = $modalStore.data;
    $: isEdit = modalData?.mode === 'edit';
    $: existingTask = isEdit ? (modalData?.task as Record<string, unknown>) : null;

    let title = '';
    let groupName = '';
    let coins = 10;
    let comment = '';
    let freqLimit = '';
    let freqPeriod: 'day' | 'week' | 'month' | 'year' = 'week';

    $: if (isOpen) {
        if (existingTask) {
            title = (existingTask.title as string) ?? '';
            groupName = (existingTask.groupName as string) ?? '';
            coins = (existingTask.coins as number) ?? 10;
            comment = (existingTask.comment as string) ?? '';
            freqLimit = String((existingTask.frequency as Record<string, unknown>)?.limit ?? '');
            freqPeriod = ((existingTask.frequency as Record<string, unknown>)?.period as typeof freqPeriod) ?? 'week';
        } else {
            title = ''; groupName = ''; coins = 10; comment = ''; freqLimit = ''; freqPeriod = 'week';
        }
    }

    function close() { modalStore.close(); }

    async function save() {
        if (!title.trim()) { showToast(tTasks('modal.enterTitle'), 'error'); return; }
        const payload = buildTaskPayload({
            id: existingTask?.id as number | string | undefined,
            title,
            groupName,
            coins,
            comment,
            freqLimit,
            freqPeriod,
        });

        if (isEdit) {
            appStore.setState({
                tasks: $appStore.tasks.map(t => t.id == payload.id ? ({ ...t, ...payload } as typeof t) : t)
            });
        } else {
            const newTask = { ...payload, id: payload.id ?? Date.now() };
            appStore.setState({ tasks: [...$appStore.tasks, (newTask as unknown as typeof $appStore.tasks[number])] });
        }
        void scheduleSave();
        showToast(isEdit ? tTasks('modal.saved') : tTasks('modal.added'), 'success');
        close();
    }

    async function deleteTask() {
        if (!existingTask?.id) return;
        if (!confirm(tTasks('modal.confirmDelete'))) return;
        appStore.setState({ tasks: $appStore.tasks.filter(t => t.id != existingTask!.id) });
        void scheduleSave();
        showToast(tTasks('modal.deleted'), 'info');
        close();
    }
</script>

{#if isOpen}
<dialog class="modal" aria-modal="true" id="task-modal" open>
    <div class="modal__content">
        <h3 id="task-modal-title">{isEdit ? tTasks('modal.titleEdit') : tTasks('modal.titleAdd')}</h3>

        <div class="form-group">
            <label for="task-name">{tTasks('modal.nameLabel')}</label>
            <input type="text" class="input" id="task-name" placeholder={tTasks('modal.namePlaceholder')} bind:value={title} />
        </div>
        <div class="form-group">
            <label for="task-group">{tTasks('modal.groupLabel')}</label>
            <input type="text" class="input" id="task-group" placeholder={tTasks('modal.groupPlaceholder')} bind:value={groupName} />
        </div>
        <div class="form-group">
            <label for="task-coins">{tTasks('modal.coinsLabel')}</label>
            <input type="number" inputmode="numeric" class="input" id="task-coins" min="1" bind:value={coins} />
        </div>
        <div class="form-group">
            <label for="task-comment">{tTasks('modal.commentLabel')}</label>
            <textarea class="input textarea" id="task-comment" placeholder={tTasks('modal.commentPlaceholder')} bind:value={comment}></textarea>
        </div>
        <div class="form-group">
            <label for="task-freq-limit">{tTasks('modal.frequencyLabel')}</label>
            <div class="input-group">
                <input type="number" inputmode="numeric" class="input" id="task-freq-limit"
                    placeholder={tTasks('modal.noLimitPlaceholder')} min="0" bind:value={freqLimit} />
                <select class="input" id="task-freq-period" bind:value={freqPeriod}>
                    <option value="day">{tTasks('modal.periodDay')}</option>
                    <option value="week">{tTasks('modal.periodWeek')}</option>
                    <option value="month">{tTasks('modal.periodMonth')}</option>
                    <option value="year">{tTasks('modal.periodYear')}</option>
                </select>
            </div>
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="task-cancel" on:click={close}>{tTasks('modal.cancel')}</button>
            {#if isEdit}
            <button class="btn btn--danger" id="task-delete" on:click={deleteTask}>{tTasks('modal.delete')}</button>
            {/if}
            <button class="btn btn--primary" id="task-save" on:click={save}>{tTasks('modal.save')}</button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" on:click={close} on:keydown={e => e.key === 'Escape' && close()} role="button" tabindex="-1"></div>
{/if}
