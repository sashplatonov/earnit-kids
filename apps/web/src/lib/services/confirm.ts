import { modalStore } from '$lib/stores/modal';

export type ConfirmTone = 'danger' | 'warning' | 'neutral';

export type ConfirmActionOptions = {
    title: string;
    description?: string;
    confirmLabel: string;
    cancelLabel: string;
    tone?: ConfirmTone;
};

export async function confirmAction(options: ConfirmActionOptions): Promise<boolean> {
    return await new Promise<boolean>((resolve) => {
        modalStore.open('confirm-modal', {
            ...options,
            onConfirm: () => resolve(true),
            onCancel: () => resolve(false),
        });
    });
}
