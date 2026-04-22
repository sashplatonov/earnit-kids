import { DEFAULT_LOCALE, localizePath, stripLocaleFromPath, type Locale, type MessageKey } from '$lib/i18n';

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
    labelKey: MessageKey;
    titleKey: MessageKey;
    iconClass: string;
};

export const APP_SECTION_META: Record<AppSection, AppSectionMeta> = {
    analytics: {
        labelKey: 'app.sections.analyticsLabel',
        titleKey: 'app.sections.analyticsTitle',
        iconClass: 'icon-chart',
    },
    tasks: {
        labelKey: 'app.sections.tasksLabel',
        titleKey: 'app.sections.tasksTitle',
        iconClass: 'icon-tasks',
    },
    shop: {
        labelKey: 'app.sections.shopLabel',
        titleKey: 'app.sections.shopTitle',
        iconClass: 'icon-shop',
    },
    requests: {
        labelKey: 'app.sections.requestsLabel',
        titleKey: 'app.sections.requestsTitle',
        iconClass: 'icon-envelope',
    },
    history: {
        labelKey: 'app.sections.historyLabel',
        titleKey: 'app.sections.historyTitle',
        iconClass: 'icon-history-menu',
    },
    friends: {
        labelKey: 'app.sections.friendsLabel',
        titleKey: 'app.sections.friendsTitle',
        iconClass: 'icon-star',
    },
    rules: {
        labelKey: 'app.sections.rulesLabel',
        titleKey: 'app.sections.rulesTitle',
        iconClass: 'icon-rules-menu',
    },
    settings: {
        labelKey: 'app.sections.settingsLabel',
        titleKey: 'app.sections.settingsTitle',
        iconClass: 'icon-settings-menu',
    },
    limits: {
        labelKey: 'app.sections.limitsLabel',
        titleKey: 'app.sections.limitsTitle',
        iconClass: 'icon-chart',
    },
    catalog: {
        labelKey: 'app.sections.catalogLabel',
        titleKey: 'app.sections.catalogTitle',
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

export function toAppPath(section: AppSection, locale: Locale = DEFAULT_LOCALE): string {
    return localizePath(`/app/${section}`, locale);
}

export function getAppSectionTitleKey(section: AppSection): MessageKey {
    return APP_SECTION_META[section].titleKey;
}

export function getAppSectionLabelKey(section: AppSection): MessageKey {
    return APP_SECTION_META[section].labelKey;
}

export function getAppSectionFromPath(pathname: string): AppSection | null {
    const segments = stripLocaleFromPath(pathname).split('/').filter(Boolean);

    if (segments[0] !== 'app') {
        return null;
    }

    const section = segments[1];
    return section && isAppSection(section) ? section : null;
}