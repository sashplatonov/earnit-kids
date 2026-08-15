import type { PublicPageMeta } from '../types';

export const meta = {
    title: 'Задания — EarnIt Kids',
    description: 'Простые задания для детей и понятные шаги для родителей в Telegram.',
} as const satisfies PublicPageMeta;

export const hero = {
    eyebrow: 'Задания',
    title: 'Задания, которые не нужно повторять',
    text: 'Ребёнок сам видит, что осталось сделать. А вы видите, кто уже отметил выполнение и ждёт подтверждения.',
} as const;