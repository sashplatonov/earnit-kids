export const telegramUi = {
    colors: {
        border: '#e6e9f0',
        divider: '#edf0f5',
        focus: '#80aaff',
        surface: '#fff',
        text: '#18243d',
        muted: '#66718a',
    },
    row: {
        minHeight: '4rem',
        targetSize: '2.75rem',
        iconSize: '2.25rem',
    },
} as const;

export type TelegramAsyncState = 'loading' | 'success' | 'empty' | 'error';
