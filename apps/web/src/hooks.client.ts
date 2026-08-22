
let initialized = false;

export async function init(): Promise<void> {
    if (initialized) {
        return;
    }

    initialized = true;
}

type ClientErrorInput = {
    error: unknown;
    event: {
        url: URL;
    };
    message: string;
    status: number;
};

export function handleError({ message }: ClientErrorInput) {
    return { message };
}
