/**
 * Centralized Mini App semantic graphics library.
 *
 * This is the single source of truth for the task/reward `Графика` picker and
 * for any screen that needs a semantic entity graphic. Every entry maps a
 * stable semantic key (a `TelegramIconName`) to a Russian display label and a
 * category, so screens never embed ad-hoc icon decisions or emoji.
 */
import type { TelegramIconName } from './telegramIconMap';

export interface SemanticGraphicCategory {
    key: string;
    label: string;
}

export interface SemanticGraphic {
    key: TelegramIconName;
    label: string;
    category: string;
}

/** Deterministic fallback for unknown/empty graphic values. */
export const OTHER_GRAPHIC_KEY: TelegramIconName = 'circleDot';

export const SEMANTIC_GRAPHIC_CATEGORIES: readonly SemanticGraphicCategory[] = [
    { key: 'general', label: 'Общее' },
    { key: 'routine', label: 'Распорядок' },
    { key: 'learning', label: 'Учёба' },
    { key: 'home', label: 'Дом' },
    { key: 'activity', label: 'Спорт' },
    { key: 'fun', label: 'Награды и досуг' },
    { key: 'money', label: 'Монеты и прогресс' }
] as const;

export const SEMANTIC_GRAPHICS: readonly SemanticGraphic[] = [
    // Общее
    { key: 'circleDot', label: 'Другое', category: 'general' },
    { key: 'star', label: 'Звезда', category: 'general' },
    { key: 'target', label: 'Цель', category: 'general' },
    { key: 'checkCircle', label: 'Галочка', category: 'general' },
    { key: 'calendar', label: 'Календарь', category: 'general' },
    { key: 'clock', label: 'Часы', category: 'general' },
    // Распорядок
    { key: 'sunrise', label: 'Утро', category: 'routine' },
    { key: 'moon', label: 'Вечер', category: 'routine' },
    { key: 'bed', label: 'Сон', category: 'routine' },
    { key: 'brush', label: 'Зубы', category: 'routine' },
    { key: 'shower', label: 'Душ / гигиена', category: 'routine' },
    { key: 'shirt', label: 'Одежда', category: 'routine' },
    { key: 'utensils', label: 'Еда', category: 'routine' },
    { key: 'droplet', label: 'Вода', category: 'routine' },
    // Учёба
    { key: 'book', label: 'Чтение', category: 'learning' },
    { key: 'pencil', label: 'Письмо', category: 'learning' },
    { key: 'pencilLine', label: 'Карандаш', category: 'learning' },
    { key: 'calculator', label: 'Математика', category: 'learning' },
    { key: 'school', label: 'Школа', category: 'learning' },
    { key: 'languages', label: 'Язык', category: 'learning' },
    { key: 'music', label: 'Музыка', category: 'learning' },
    { key: 'flask', label: 'Наука', category: 'learning' },
    // Дом
    { key: 'home', label: 'Дом', category: 'home' },
    { key: 'sparkles', label: 'Уборка', category: 'home' },
    { key: 'box', label: 'Порядок', category: 'home' },
    { key: 'cookingPot', label: 'Посуда', category: 'home' },
    { key: 'table', label: 'Стол', category: 'home' },
    { key: 'sprout', label: 'Растения', category: 'home' },
    { key: 'paw', label: 'Питомец', category: 'home' },
    // Спорт
    { key: 'dumbbell', label: 'Спорт', category: 'activity' },
    { key: 'footprints', label: 'Бег', category: 'activity' },
    { key: 'bike', label: 'Велосипед', category: 'activity' },
    { key: 'volleyball', label: 'Мяч', category: 'activity' },
    // Награды и досуг
    { key: 'gift', label: 'Подарок', category: 'fun' },
    { key: 'dice', label: 'Настольная игра', category: 'fun' },
    { key: 'users', label: 'Семья', category: 'fun' },
    { key: 'heart', label: 'Совместное время', category: 'fun' },
    { key: 'film', label: 'Кино', category: 'fun' },
    { key: 'gamepad', label: 'Игры', category: 'fun' },
    { key: 'palette', label: 'Творчество', category: 'fun' },
    { key: 'penTool', label: 'Рисование', category: 'fun' },
    { key: 'blocks', label: 'Конструктор', category: 'fun' },
    { key: 'treePine', label: 'Парк', category: 'fun' },
    { key: 'iceCream', label: 'Мороженое', category: 'fun' },
    { key: 'cake', label: 'Десерт', category: 'fun' },
    { key: 'car', label: 'Поездка', category: 'fun' },
    // Монеты и прогресс
    { key: 'coin', label: 'Монета', category: 'money' },
    { key: 'piggy', label: 'Копилка', category: 'money' },
    { key: 'award', label: 'Награда', category: 'money' },
    { key: 'trophy', label: 'Кубок', category: 'money' },
    { key: 'medal', label: 'Медаль', category: 'money' }
] as const;

const GRAPHIC_BY_KEY = new Map<TelegramIconName, SemanticGraphic>(
    SEMANTIC_GRAPHICS.map((graphic) => [graphic.key, graphic])
);

/** Resolve a stored semantic key to a graphic entry, falling back to `Другое`. */
export function getSemanticGraphic(key: string | null | undefined): SemanticGraphic {
    const normalized = (key ?? '').trim();
    const entry = GRAPHIC_BY_KEY.get(normalized as TelegramIconName);
    if (entry) return entry;
    return { key: OTHER_GRAPHIC_KEY, label: 'Другое', category: 'general' };
}

/** Whether a string is a known semantic graphic key. */
export function isSemanticGraphicKey(key: string | null | undefined): key is TelegramIconName {
    if (!key) return false;
    return GRAPHIC_BY_KEY.has(key.trim() as TelegramIconName);
}

/** All graphics belonging to a category, in the canonical order. */
export function getGraphicsForCategory(category: string): readonly SemanticGraphic[] {
    return SEMANTIC_GRAPHICS.filter((graphic) => graphic.category === category);
}
