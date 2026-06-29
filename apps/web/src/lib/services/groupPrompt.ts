import { modalStore } from '$lib/stores/modal';

export type GroupPromptOptions = {
    title: string;
    description?: string;
    placeholder: string;
    confirmLabel: string;
    cancelLabel: string;
    initialValue?: string;
    suggestions?: string[];
};

export async function requestGroupName(options: GroupPromptOptions): Promise<string | null> {
    return await new Promise<string | null>((resolve) => {
        modalStore.open('bulk-group-modal', {
            ...options,
            onSubmit: (groupName: string) => resolve(groupName),
            onCancel: () => resolve(null),
        });
    });
}
