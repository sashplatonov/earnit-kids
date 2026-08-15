import type { PublicPageMeta } from '../types';

export const meta = {
    title: 'Для родителей — EarnIt Kids',
    description: 'Как родитель контролирует задания, награды и подтверждение в EarnIt Kids.',
} as const satisfies PublicPageMeta;

export const hero = {
    eyebrow: 'Для родителей',
    title: 'Вы управляете правилами, а не повторяете их',
    text: 'Родитель создаёт задания и награды, подтверждает выполнение и контролирует начисление монет. Ребёнок не может начислять их себе сам.',
} as const;