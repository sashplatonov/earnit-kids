<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import type { ConfirmTone } from '$lib/services/confirm';

    type ConfirmModalData = {
        title?: string;
        description?: string;
        confirmLabel?: string;
        cancelLabel?: string;
        tone?: ConfirmTone;
        onConfirm?: () => void | Promise<void>;
        onCancel?: () => void | Promise<void>;
    };

    $: isOpen = $modalStore.open === 'confirm-modal';
    $: modalData = (($modalStore.data ?? {}) as ConfirmModalData);
    $: tone = modalData.tone ?? 'danger';

    let pending = false;

    function close() {
        modalStore.close();
    }

    async function cancel() {
        if (pending) return;
        pending = true;
        try {
            await modalData.onCancel?.();
        } finally {
            pending = false;
            close();
        }
    }

    async function confirm() {
        if (pending) return;
        pending = true;
        try {
            await modalData.onConfirm?.();
        } finally {
            pending = false;
            close();
        }
    }

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            void cancel();
        }
    }
</script>

{#if isOpen}
<dialog class="modal" aria-modal="true" id="confirm-modal" open on:keydown={handleKeydown}>
    <div class="modal__content confirm-modal__content" data-tone={tone}>
        <h3 id="confirm-modal-title">{modalData.title ?? 'Подтвердите действие'}</h3>
        {#if modalData.description}
            <p class="confirm-modal__description">{modalData.description}</p>
        {/if}

        <div class="modal__actions">
            <button class="btn btn--secondary" id="confirm-modal-cancel" type="button" on:click={() => void cancel()} disabled={pending}>
                {modalData.cancelLabel ?? 'Отмена'}
            </button>
            <button class="btn btn--danger" id="confirm-modal-confirm" type="button" on:click={() => void confirm()} disabled={pending}>
                {modalData.confirmLabel ?? 'Подтвердить'}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="button" tabindex="-1" on:click={() => void cancel()} on:keydown={handleKeydown}></div>
{/if}

<style>
    .confirm-modal__content {
        max-width: 32rem;
    }

    .confirm-modal__description {
        margin: 0 0 1rem;
        color: var(--muted-text, #6b7280);
    }

    .confirm-modal__content[data-tone='warning'] :global(.btn--danger) {
        background: var(--warning, #d97706);
    }

    .confirm-modal__content[data-tone='neutral'] :global(.btn--danger) {
        background: var(--primary, #2563eb);
    }
</style>
