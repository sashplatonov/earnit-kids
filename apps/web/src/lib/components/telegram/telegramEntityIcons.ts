/**
 * Centralized Mini App entity/action graphic system.
 *
 * Action and navigation icons live in `telegramIconMap`. This module resolves
 * semantic *entity* graphics (task/reward/category/request/activity) from a
 * title, group name and kind, with deterministic fallbacks — so individual
 * screens never embed arbitrary icon decisions.
 */
import { telegramIconMap, type TelegramIconName } from './telegramIconMap';

export type EntityKind = 'task' | 'reward' | 'category' | 'request' | 'activity' | 'child';

export interface EntityIconInput {
    kind: EntityKind;
    title?: string | null;
    group?: string | null;
    /** Explicit semantic override (an entity icon name, e.g. 'sun'). */
    semantic?: string | null;
}

// Multi-codepoint emoji (ZWJ sequences, variation selectors) are intentional here.
// eslint-disable-next-line no-misleading-character-class
const EMOJI_LEAD = /^[\p{Extended_Pictographic}\u{200D}\u{FE0F}\s]+/u;

/** Remove leading emoji/decorative glyphs so titles render as clean text. */
export function stripLeadingEmoji(value: string): string {
    return value.replace(EMOJI_LEAD, '').trim();
}

const TITLE_ICON_RULES: ReadonlyArray<{ test: RegExp; icon: TelegramIconName }> = [
    { test: /крепост|castle/i, icon: 'castle' },
    { test: /настольн|board ?game|dice/i, icon: 'dice' },
    { test: /книг|чтени|почитат|read|book/i, icon: 'book' },
    { test: /письм|писат|красив|почерк|записк|write|handwriting/i, icon: 'pencil' },
    { test: /стол|рабочее место|desk/i, icon: 'desk' },
    { test: /разобрат|вещ|зон|organiz|tidy/i, icon: 'box' },
    { test: /умыт|одет|причес|утро|утрен|morning|sunrise|проснул|зарядк|routine|wash|dress/i, icon: 'sun' },
    { test: /убор|мыт|прибра|убра|чист|clean|brush|sparkl/i, icon: 'sparkles' },
    { test: /лаборатор|наук|эксперимент|science|lab/i, icon: 'flask' },
    { test: /наград|подар|reward|gift/i, icon: 'gift' },
    { test: /мам|пап|семь|родител|family|parent/i, icon: 'users' },
    { test: /дом|home|house|комнат/i, icon: 'home' },
    { test: /игр|play/i, icon: 'dice' },
    { test: /спорт|физ|здоров|sport|exercise/i, icon: 'activity' },
];

const GROUP_ICON_RULES: ReadonlyArray<{ test: RegExp; icon: TelegramIconName }> = [
    { test: /утро|morning|sunrise/i, icon: 'sun' },
    { test: /учёб|учеб|study|school|learning/i, icon: 'book' },
    { test: /дом|home|порядок|order/i, icon: 'home' },
    { test: /семь|family/i, icon: 'users' },
    { test: /игр|game/i, icon: 'dice' },
];

function fallbackIcon(kind: EntityKind): TelegramIconName {
    switch (kind) {
        case 'reward':
            return 'reward';
        case 'category':
            return 'tag';
        case 'request':
            return 'request';
        case 'activity':
            return 'activity';
        case 'child':
            return 'child';
        default:
            return 'task';
    }
}

/** Resolve the semantic entity icon for a task/reward/category/request/activity. */
export function getTelegramEntityIcon(input: EntityIconInput): TelegramIconName {
    const title = stripLeadingEmoji(input.title ?? '');
    const group = stripLeadingEmoji(input.group ?? '');
    const semantic = input.semantic?.trim().toLowerCase() ?? '';
    if (semantic && semantic in telegramIconMap) return semantic as TelegramIconName;
    for (const rule of TITLE_ICON_RULES) {
        if (rule.test.test(title)) return rule.icon;
    }
    for (const rule of GROUP_ICON_RULES) {
        if (rule.test.test(group)) return rule.icon;
    }
    return fallbackIcon(input.kind);
}

/** Action icons are the shared icon vocabulary — resolved centrally for consistency. */
export function getTelegramActionIcon(name: TelegramIconName): TelegramIconName {
    return name;
}
