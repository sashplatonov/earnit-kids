export interface TaskPayloadInput {
    id?: number | string;
    title: string;
    groupName: string;
    coins: number;
    comment: string;
    freqLimit: string;
    freqPeriod: 'day' | 'week' | 'month' | 'year';
    isActive?: boolean;
}

export interface TaskPayload {
    id?: number | string;
    name: string;
    title: string;
    groupName: string | null;
    coins: number;
    comment: string | null;
    frequency: { limit: number; period: 'day' | 'week' | 'month' | 'year' } | null;
    /**
     * Optional because the backend defaults tasks to active when the field is absent.
     * We only send it when the user explicitly disables a task.
     */
    isActive?: boolean;
}

export function buildTaskPayload(input: TaskPayloadInput): TaskPayload {
    const name = input.title.trim();

    const payload: TaskPayload = {
        id: input.id,
        name,
        title: name,
        groupName: input.groupName.trim() || null,
        coins: Number(input.coins) || 10,
        comment: input.comment.trim() || null,
        frequency: input.freqLimit
            ? { limit: Number(input.freqLimit), period: input.freqPeriod }
            : null,
    };

    // Preserve previous behaviour: if user explicitly disables a task, send isActive=false.
    // Otherwise omit the field to keep payload minimal and compatible with older backend expectations.
    if (input.isActive === false) {
        payload.isActive = false;
    }

    return payload;
}