<script lang="ts">
    import { modalStore } from '$lib/stores/modal';

    type RequestNoteModalData = {
        title?: string;
        description?: string;
        placeholder?: string;
        initialValue?: string;
        skipLabel?: string;
        saveLabel?: string;
        onSubmit?: (note: string) => void;
        onSkip?: () => void;
    };

    $: isOpen = $modalStore.open === 'request-note-modal';
    $: modalData = (($modalStore.data ?? {}) as RequestNoteModalData);

    let note = '';

    $: if (isOpen) {
        note = modalData.initialValue ?? '';
    }

    function close() {
        modalStore.close();
    }

    function skip() {
        modalData.onSkip?.();
        close();
    }

    function save() {
        modalData.onSubmit?.(note.trim());
        close();
    }

    function handleBackdropKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            skip();
        }
    }
</script>

{#if isOpen}
<dialog class="modal" aria-modal="true" id="request-note-modal" open on:keydown={handleBackdropKeydown}>
    <div class="modal__content request-note-modal__content">
        <h3 id="request-note-modal-title">{modalData.title ?? 'Заметка к заявке'}</h3>
        <p class="request-note-modal__description">
            {modalData.description ?? 'Добавь заметку, если хочешь.'}
        </p>

        <div class="form-group">
            <textarea
                class="input textarea request-note-modal__textarea"
                id="request-note-input"
                rows="4"
                maxlength="120"
                placeholder={modalData.placeholder ?? ''}
                bind:value={note}
            ></textarea>
        </div>

        <div class="request-note-modal__meta">
            <span>{note.trim().length}/120</span>
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="request-note-skip" type="button" on:click={skip}>
                {modalData.skipLabel ?? 'Пропустить'}
            </button>
            <button class="btn btn--primary" id="request-note-save" type="button" on:click={save}>
                {modalData.saveLabel ?? 'Сохранить'}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="button" tabindex="-1" on:click={skip} on:keydown={handleBackdropKeydown}></div>
{/if}

<style>
    .request-note-modal__content {
        max-width: 32rem;
    }

    .request-note-modal__description {
        margin: 0 0 0.85rem;
    }

    .request-note-modal__textarea {
        min-height: 7.5rem;
    }

    .request-note-modal__meta {
        display: flex;
        justify-content: flex-end;
        margin-top: -0.35rem;
        margin-bottom: 0.85rem;
        color: var(--muted-text, #6b7280);
        font-size: 0.82rem;
    }
</style>