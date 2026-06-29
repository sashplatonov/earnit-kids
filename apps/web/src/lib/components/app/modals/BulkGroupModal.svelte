<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import GroupInput from '../GroupInput.svelte';

    type BulkGroupModalData = {
        title?: string;
        description?: string;
        placeholder?: string;
        confirmLabel?: string;
        cancelLabel?: string;
        initialValue?: string;
        suggestions?: string[];
        onSubmit?: (groupName: string) => void;
        onCancel?: () => void;
    };

    $: isOpen = $modalStore.open === 'bulk-group-modal';
    $: modalData = (($modalStore.data ?? {}) as BulkGroupModalData);

    let groupName = '';

    $: if (isOpen) {
        groupName = modalData.initialValue ?? '';
    }

    function close() {
        modalStore.close();
    }

    function cancel() {
        modalData.onCancel?.();
        close();
    }

    function submit() {
        const trimmed = groupName.trim();
        if (!trimmed) {
            return;
        }
        modalData.onSubmit?.(trimmed);
        close();
    }

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            cancel();
        }
    }
</script>

{#if isOpen}
<dialog class="modal" aria-modal="true" id="bulk-group-modal" open on:keydown={handleKeydown}>
    <div class="modal__content bulk-group-modal__content">
        <h3>{modalData.title ?? 'Сменить группу'}</h3>
        {#if modalData.description}
            <p class="bulk-group-modal__description">{modalData.description}</p>
        {/if}

        <div class="form-group">
            <label for="bulk-group-input">Группа</label>
            <GroupInput
                id="bulk-group-input"
                placeholder={modalData.placeholder ?? ''}
                suggestions={modalData.suggestions ?? []}
                bind:value={groupName}
            />
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" type="button" on:click={cancel}>{modalData.cancelLabel ?? 'Отмена'}</button>
            <button class="btn btn--primary" type="button" on:click={submit} disabled={!groupName.trim()}>
                {modalData.confirmLabel ?? 'Сохранить'}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="button" tabindex="-1" on:click={cancel} on:keydown={handleKeydown}></div>
{/if}

<style>
    .bulk-group-modal__content {
        max-width: 32rem;
    }

    .bulk-group-modal__description {
        margin: 0 0 1rem;
        color: var(--muted-text, #6b7280);
    }
</style>
