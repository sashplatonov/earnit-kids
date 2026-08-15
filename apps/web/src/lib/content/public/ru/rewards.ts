import type { PublicPageMeta } from '../types';

export const meta = {
    title: 'Награды — EarnIt Kids',
    description: 'Награды, которые вы заранее определили вместе с ребёнком.',
} as const satisfies PublicPageMeta;

export const hero = {
    eyebrow: 'Награды',
    title: 'Награды, о которых договорились заранее',
    text: 'Ребёнок копит монеты и выбирает награду. Вы решаете, подходит ли момент. Никаких сюрпризов на ходу.',
} as const;