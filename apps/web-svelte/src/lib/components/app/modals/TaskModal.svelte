<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { adminSaveTask, adminDeleteTask } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

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
        if (!title.trim()) { showToast('Введите название', 'error'); return; }
        const payload = {
            id: existingTask?.id,
            title: title.trim(),
            groupName: groupName.trim() || null,
            coins: Number(coins) || 10,
            comment: comment.trim() || null,
            frequency: freqLimit ? { limit: Number(freqLimit), period: freqPeriod } : null,
        };

        const res = await adminSaveTask(payload) as Record<string, unknown> | null;
        if (res) {
            const saved = { ...payload, id: res.id ?? payload.id };
            if (isEdit) {
                appStore.setState({
                    tasks: $appStore.tasks.map(t => t.id == saved.id ? ({ ...t, ...saved } as typeof t) : t)
                });
            } else {
                appStore.setState({ tasks: [...$appStore.tasks, saved as typeof $appStore.tasks[number]] });
            }
            showToast(isEdit ? 'Задание сохранено' : 'Задание добавлено', 'success');
            close();
        }
    }

    async function deleteTask() {
        if (!existingTask?.id) return;
        if (!confirm('Удалить задание?')) return;
        const ok = await adminDeleteTask(existingTask.id);
        if (ok) {
            appStore.setState({ tasks: $appStore.tasks.filter(t => t.id != existingTask!.id) });
            showToast('Задание удалено', 'info');
            close();
        }
    }
</script>

{#if isOpen}
<dialog class="modal" role="dialog" aria-modal="true" id="task-modal" open>
    <div class="modal__content">
        <h3 id="task-modal-title">{isEdit ? 'Редактировать задание' : 'Добавить задание'}</h3>

        <div class="form-group">
            <label for="task-name">Название</label>
            <input type="text" class="input" id="task-name" placeholder="Помыть посуду" bind:value={title} />
        </div>
        <div class="form-group">
            <label for="task-group">Группа</label>
            <input type="text" class="input" id="task-group" placeholder="Напр: Дом, Учеба..." bind:value={groupName} />
        </div>
        <div class="form-group">
            <label for="task-coins">Монеты</label>
            <input type="number" inputmode="numeric" class="input" id="task-coins" min="1" bind:value={coins} />
        </div>
        <div class="form-group">
            <label for="task-comment">Комментарий</label>
            <textarea class="input textarea" id="task-comment" placeholder="Дополнительная информация..." bind:value={comment}></textarea>
        </div>
        <div class="form-group">
            <label for="task-freq-limit">Частота (раз в период)</label>
            <div class="input-group">
                <input type="number" inputmode="numeric" class="input" id="task-freq-limit"
                    placeholder="Без лимита" min="0" bind:value={freqLimit} />
                <select class="input" id="task-freq-period" bind:value={freqPeriod}>
                    <option value="day">в день</option>
                    <option value="week">в неделю</option>
                    <option value="month">в месяц</option>
                    <option value="year">в год</option>
                </select>
            </div>
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="task-cancel" on:click={close}>Отмена</button>
            {#if isEdit}
            <button class="btn btn--danger" id="task-delete" on:click={deleteTask}>Удалить</button>
            {/if}
            <button class="btn btn--primary" id="task-save" on:click={save}>Сохранить</button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" on:click={close} on:keydown={e => e.key === 'Escape' && close()} role="button" tabindex="-1"></div>
{/if}
