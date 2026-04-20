<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { adminAddChild } from '$lib/services/api';
    import { refreshData, switchChild } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    type ChildCreateResponse = {
        id?: number | string;
        childId?: number | string;
        error?: string;
    };

    $: isOpen = $modalStore.open === 'add-child-modal';
    $: isAdmin = $appStore.isAdmin;

    let childName = '';
    let isSubmitting = false;

    $: if (!isOpen) {
        childName = '';
        isSubmitting = false;
    }

    function close() {
        if (!isSubmitting) {
            modalStore.close();
        }
    }

    async function save() {
        const trimmedName = childName.trim();

        if (!trimmedName) {
            showToast('Введите имя ребенка', 'error');
            return;
        }

        isSubmitting = true;

        try {
            const result = await adminAddChild(trimmedName) as ChildCreateResponse | null;
            const createdChildId = result?.id ?? result?.childId;

            if (!result || createdChildId == null) {
                throw new Error(result?.error || 'Не удалось добавить ребенка');
            }

            await refreshData();
            await switchChild(createdChildId);
            modalStore.close();
            showToast('Ребенок добавлен', 'success');
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Не удалось добавить ребенка';
            showToast(message, 'error');
        } finally {
            isSubmitting = false;
        }
    }
</script>

{#if isOpen && isAdmin}
<dialog class="modal" aria-modal="true" id="add-child-modal" open>
    <div class="modal__content">
        <h3>Добавить ребенка</h3>

        <div class="form-group">
            <label for="new-child-name">Имя ребенка</label>
            <input
                type="text"
                class="input"
                id="new-child-name"
                placeholder="Например: Саша"
                bind:value={childName}
                on:keydown={(event) => event.key === 'Enter' && !isSubmitting && save()}
            />
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="add-child-cancel" type="button" on:click={close}>Отмена</button>
            <button class="btn btn--primary" id="add-child-save" type="button" disabled={isSubmitting} on:click={save}>
                {isSubmitting ? 'Добавляем...' : 'Добавить'}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="presentation" on:click={close}></div>
{/if}