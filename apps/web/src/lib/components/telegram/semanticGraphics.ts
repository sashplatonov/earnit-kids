/** Stable semantic graphics and catalog keys for the Telegram Mini App. */
import type { TelegramIconName } from './telegramIconMap';

export interface SemanticGraphicCategory { key: string; }
export interface SemanticGraphic { key: TelegramIconName; category: string; }

export const OTHER_GRAPHIC_KEY: TelegramIconName = 'circleDot';

export const SEMANTIC_GRAPHIC_CATEGORIES: readonly SemanticGraphicCategory[] = [
    { key: 'general' }, { key: 'routine' }, { key: 'learning' }, { key: 'home' },
    { key: 'activity' }, { key: 'fun' }, { key: 'money' },
] as const;

export const SEMANTIC_GRAPHICS: readonly SemanticGraphic[] = [
    { key: 'circleDot', category: 'general' }, { key: 'star', category: 'general' }, { key: 'target', category: 'general' },
    { key: 'checkCircle', category: 'general' }, { key: 'calendar', category: 'general' }, { key: 'clock', category: 'general' },
    { key: 'sunrise', category: 'routine' }, { key: 'moon', category: 'routine' }, { key: 'bed', category: 'routine' },
    { key: 'brush', category: 'routine' }, { key: 'shower', category: 'routine' }, { key: 'shirt', category: 'routine' },
    { key: 'utensils', category: 'routine' }, { key: 'droplet', category: 'routine' },
    { key: 'book', category: 'learning' }, { key: 'pencil', category: 'learning' }, { key: 'pencilLine', category: 'learning' },
    { key: 'calculator', category: 'learning' }, { key: 'school', category: 'learning' }, { key: 'languages', category: 'learning' },
    { key: 'music', category: 'learning' }, { key: 'flask', category: 'learning' },
    { key: 'home', category: 'home' }, { key: 'sparkles', category: 'home' }, { key: 'box', category: 'home' },
    { key: 'cookingPot', category: 'home' }, { key: 'table', category: 'home' }, { key: 'sprout', category: 'home' }, { key: 'paw', category: 'home' },
    { key: 'dumbbell', category: 'activity' }, { key: 'footprints', category: 'activity' }, { key: 'bike', category: 'activity' }, { key: 'volleyball', category: 'activity' },
    { key: 'gift', category: 'fun' }, { key: 'dice', category: 'fun' }, { key: 'users', category: 'fun' }, { key: 'heart', category: 'fun' },
    { key: 'film', category: 'fun' }, { key: 'gamepad', category: 'fun' }, { key: 'palette', category: 'fun' }, { key: 'penTool', category: 'fun' },
    { key: 'blocks', category: 'fun' }, { key: 'treePine', category: 'fun' }, { key: 'iceCream', category: 'fun' }, { key: 'cake', category: 'fun' }, { key: 'car', category: 'fun' },
    { key: 'coin', category: 'money' }, { key: 'piggy', category: 'money' }, { key: 'award', category: 'money' }, { key: 'trophy', category: 'money' }, { key: 'medal', category: 'money' },
] as const;

const GRAPHIC_BY_KEY = new Map<TelegramIconName, SemanticGraphic>(SEMANTIC_GRAPHICS.map((graphic) => [graphic.key, graphic]));

export function getSemanticGraphic(key: string | null | undefined): SemanticGraphic {
    return GRAPHIC_BY_KEY.get((key ?? '').trim() as TelegramIconName) ?? { key: OTHER_GRAPHIC_KEY, category: 'general' };
}

export function isSemanticGraphicKey(key: string | null | undefined): key is TelegramIconName {
    return Boolean(key && GRAPHIC_BY_KEY.has(key.trim() as TelegramIconName));
}

export function getGraphicsForCategory(category: string): readonly SemanticGraphic[] {
    return SEMANTIC_GRAPHICS.filter((graphic) => graphic.category === category);
}
