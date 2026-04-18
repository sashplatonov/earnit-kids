/** Toast notification store */
import { writable } from 'svelte/store';

export type ToastVariant = 'success' | 'error' | 'info';

export interface Toast {
    id: number;
    message: string;
    variant: ToastVariant;
}

let _nextId = 0;

function createToastStore() {
    const { subscribe, update } = writable<Toast[]>([]);

    function show(message: string, variant: ToastVariant = 'info', durationMs = 3200) {
        const id = ++_nextId;
        update(list => [...list, { id, message, variant }]);
        setTimeout(() => dismiss(id), durationMs);
    }

    function dismiss(id: number) {
        update(list => list.filter(t => t.id !== id));
    }

    return { subscribe, show, dismiss };
}

export const toastStore = createToastStore();

/** Shorthand helpers that match legacy showToast() call signature */
export const showToast = (msg: string, variant: ToastVariant = 'info') =>
    toastStore.show(msg, variant);
