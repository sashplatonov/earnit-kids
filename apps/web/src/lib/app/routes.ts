export const SHARED_APP_SECTIONS = [
    'analytics',
    'tasks',
    'shop',
    'requests',
    'history',
    'friends',
    'rules',
    'settings',
] as const;

export const ADMIN_ONLY_APP_SECTIONS = [
    'limits',
    'catalog',
] as const;

export const APP_SECTIONS = [
    ...SHARED_APP_SECTIONS,
    ...ADMIN_ONLY_APP_SECTIONS,
] as const;

export type AppSection = (typeof APP_SECTIONS)[number];

type AppSectionMeta = {
    label: string;
    title: string;
    iconClass: string;
};

export const APP_SECTION_META: Record<AppSection, AppSectionMeta> = {
    analytics: {
        label: 'Достижения',
        title: 'Достижения',
        iconClass: 'icon-chart',
    },
    tasks: {
        label: 'Задания',
        title: 'Задания',
        iconClass: 'icon-tasks',
    },
    shop: {
        label: 'Награды',
        title: 'Магазин наград',
        iconClass: 'icon-shop',
    },
    requests: {
        label: 'Заявки',
        title: 'Заявки',
        iconClass: 'icon-envelope',
    },
    history: {
        label: 'История',
        title: 'История',
        iconClass: 'icon-history-menu',
    },
    friends: {
        label: 'Друзья',
        title: 'Друзья',
        iconClass: 'icon-star',
    },
    rules: {
        label: 'Правила',
        title: 'Правила',
        iconClass: 'icon-rules-menu',
    },
    settings: {
        label: 'Настройки',
        title: 'Настройки',
        iconClass: 'icon-settings-menu',
    },
    limits: {
        label: 'Лимиты',
        title: 'Лимиты',
        iconClass: 'icon-chart',
    },
    catalog: {
        label: 'Каталог',
        title: 'Каталог',
        iconClass: 'icon-tasks',
    },
};

export const ADMIN_PRIMARY_SECTIONS: AppSection[] = [
    'analytics',
    'tasks',
    'requests',
    'shop',
];

export const CHILD_PRIMARY_SECTIONS: AppSection[] = [
    'analytics',
    'tasks',
    'shop',
    'requests',
];

export const COMMON_OVERFLOW_SECTIONS: AppSection[] = [
    'history',
    'friends',
    'rules',
    'settings',
];

export const ADMIN_MANAGEMENT_SECTIONS: AppSection[] = [
    'limits',
    'catalog',
];

export function isAdminRole(role?: string): boolean {
    return role === 'admin' || role === 'parent';
}

export function isAppSection(value: string): value is AppSection {
    return (APP_SECTIONS as readonly string[]).includes(value);
}

export function isSectionAllowed(section: AppSection, role?: string): boolean {
    return isAdminRole(role) || (SHARED_APP_SECTIONS as readonly string[]).includes(section);
}

export function getDefaultAppSection(role?: string): AppSection {
    return isAdminRole(role) ? 'analytics' : 'tasks';
}

export function toAppPath(section: AppSection): `/app/${AppSection}` {
    return `/app/${section}`;
}

export function getAppSectionTitle(section: AppSection): string {
    return APP_SECTION_META[section].title;
}

export function getAppSectionFromPath(pathname: string): AppSection | null {
    const segments = pathname.split('/').filter(Boolean);

    if (segments[0] !== 'app') {
        return null;
    }

    const section = segments[1];
    return section && isAppSection(section) ? section : null;
}