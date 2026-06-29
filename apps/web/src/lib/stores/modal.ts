/** Modal store — open/close modal state */
import { writable } from 'svelte/store';

export type ModalName =
    | 'task-modal'
    | 'shop-modal'
    | 'csv-import-modal'
    | 'request-note-modal'
    | 'confirm-modal'
    | 'add-child-modal'
    | 'rules-modal'
    | string;

interface ModalState {
    open: ModalName | null;
    data: Record<string, unknown>;
}

function createModalStore() {
    const { subscribe, update } = writable<ModalState>({ open: null, data: {} });

    return {
        subscribe,
        open(name: ModalName, data: Record<string, unknown> = {}) {
            update(() => ({ open: name, data }));
        },
        close() {
            update(() => ({ open: null, data: {} }));
        },
    };
}

export const modalStore = createModalStore();
