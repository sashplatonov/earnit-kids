<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { modalStore } from '$lib/stores/modal';
    import { appStore } from '$lib/stores/app';
    import { adminAddChild } from '$lib/services/api';
    import { refreshData, switchChild } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tApp(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`app.${key}` as MessageKey, variables);
    }

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
            showToast(tApp('addChildModal.emptyName'), 'error');
            return;
        }

        isSubmitting = true;

        try {
            const result = await adminAddChild(trimmedName) as ChildCreateResponse | null;
            const createdChildId = result?.id ?? result?.childId;

            if (!result || createdChildId == null) {
                throw new Error(result?.error || tApp('addChildModal.fallbackError'));
            }

            await refreshData();
            await switchChild(createdChildId);
            modalStore.close();
            showToast(tApp('addChildModal.addedToast'), 'success');
        } catch (error) {
            const message = error instanceof Error ? error.message : tApp('addChildModal.fallbackError');
            showToast(message, 'error');
        } finally {
            isSubmitting = false;
        }
    }
</script>

{#if isOpen && isAdmin}
<dialog class="modal" aria-modal="true" id="add-child-modal" open>
    <div class="modal__content">
        <h3>{tApp('addChildModal.title')}</h3>

        <div class="form-group">
            <label for="new-child-name">{tApp('addChildModal.nameLabel')}</label>
            <input
                type="text"
                class="input"
                id="new-child-name"
                placeholder={tApp('addChildModal.placeholder')}
                bind:value={childName}
                on:keydown={(event) => event.key === 'Enter' && !isSubmitting && save()}
            />
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" id="add-child-cancel" type="button" on:click={close}>{tApp('addChildModal.cancel')}</button>
            <button class="btn btn--primary" id="add-child-save" type="button" disabled={isSubmitting} on:click={save}>
                {isSubmitting ? tApp('addChildModal.saving') : tApp('addChildModal.save')}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="presentation" on:click={close}></div>
{/if}