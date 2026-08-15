import type { PublicPageMeta } from '../types';

export const meta = {
    title: 'Как работает — EarnIt Kids',
    description: 'Как EarnIt Kids работает для родителя и ребёнка в Telegram.',
} as const satisfies PublicPageMeta;

export const hero = {
    eyebrow: 'Без долгой настройки',
    title: 'Четыре шага — и всё понятно',
    text: 'Решения всё равно за вами. EarnIt Kids просто помнит договорённости: что сделать, сколько монет за это дают и кто уже что подтвердил.',
} as const;