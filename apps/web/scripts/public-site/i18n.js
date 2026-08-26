export const LOCALES = ['en', 'ru'];
export const DEFAULT_LOCALE = 'en';

const messages = {
    en: {
        languageGroup: 'Language',
        skipLink: 'Skip to content',
        brandLabel: 'EarnIt Kids - home',
        navLabel: 'Main menu',
        login: 'Sign in',
        telegram: 'Telegram Mini App',
        unavailableMiniApp: 'The Telegram Mini App link is not configured yet',
        configureMiniApp: 'Add a real Telegram Mini App link in config.js',
        oauthError: 'Google sign-in is temporarily unavailable. Use the browser sign-in link to try again.',
        footer: 'Tasks, coins and rewards - without notes and endless reminders.',
        pageTitles: { index: 'Home', how: 'How it works', tasks: 'Tasks', rewards: 'Rewards', parents: 'For parents', faq: 'Questions' },
        descriptions: {
            index: 'Tasks, coins and rewards for children in Telegram without the daily hassle.',
            how: 'How EarnIt Kids works for parents and children in Telegram.',
            tasks: 'Tasks, groups, completion history and coin limits explained.',
            rewards: 'How family rewards and spending coins work in EarnIt Kids.',
            parents: 'Approvals, children, limits, notifications and family settings.',
            faq: 'Answers to parents’ frequently asked questions about EarnIt Kids.',
        },
    },
    ru: {
        languageGroup: 'Язык',
        skipLink: 'К содержанию',
        brandLabel: 'EarnIt Kids - главная',
        navLabel: 'Основное меню',
        login: 'Войти',
        telegram: 'Telegram MiniApp',
        unavailableMiniApp: 'Ссылка на Telegram Mini App пока не настроена',
        configureMiniApp: 'Укажите реальную ссылку Telegram Mini App в config.js',
        oauthError: 'Вход через Google временно недоступен. Используйте ссылку для входа в браузере и попробуйте ещё раз.',
        footer: 'Задания, монеты и награды - без записок и бесконечных напоминаний.',
        pageTitles: { index: 'Главная', how: 'Как работает', tasks: 'Задания', rewards: 'Награды', parents: 'Для родителей', faq: 'Вопросы' },
        descriptions: {
            index: 'Задания, монеты и награды для детей в Telegram без лишней рутины.',
            how: 'Как EarnIt Kids работает для родителя и ребёнка в Telegram.',
            tasks: 'Как устроены задания, группы, история выполнения и лимиты монет.',
            rewards: 'Как устроены семейные награды и трата монет в EarnIt Kids.',
            parents: 'Подтверждения, дети, лимиты, уведомления и семейные настройки EarnIt Kids.',
            faq: 'Ответы на частые вопросы родителей об EarnIt Kids.',
        },
    },
};

export function normalizeLocale(value) {
    if (typeof value !== 'string') return null;
    const normalized = value.trim().toLowerCase();
    if (normalized === 'ru' || normalized.startsWith('ru-')) return 'ru';
    if (normalized === 'en' || normalized.startsWith('en-')) return 'en';
    return null;
}

export function detectLocale(navigatorRef = globalThis.navigator) {
    const languages = Array.isArray(navigatorRef?.languages) ? navigatorRef.languages : [];
    for (const language of [...languages, navigatorRef?.language]) {
        const locale = normalizeLocale(language);
        if (locale) return locale;
    }
    return DEFAULT_LOCALE;
}

export function resolveLocale(search = '', navigatorRef = globalThis.navigator) {
    const queryLocale = new URLSearchParams(search).get('lang');
    return normalizeLocale(queryLocale) || detectLocale(navigatorRef);
}

export function getMessage(locale, key) {
    return messages[locale]?.[key] ?? messages[DEFAULT_LOCALE][key] ?? key;
}

export function withLanguage(href, locale, currentOrigin = 'https://example.test') {
    const url = new URL(href, currentOrigin);
    if (url.origin !== currentOrigin) return href;
    url.searchParams.set('lang', locale);
    return `${url.pathname}${url.search}${url.hash}`;
}

export function localizePublicLinks(documentRef, locale, origin) {
    documentRef.querySelectorAll('a[href]').forEach((link) => {
        const href = link.getAttribute('href');
        if (!href || link.hasAttribute('data-miniapp-link') || link.hasAttribute('data-browser-workspace-link')) return;
        link.setAttribute('href', withLanguage(href, locale, origin));
    });
}

export function applyLocale(documentRef, windowRef, locale) {
    const dictionary = messages[locale] || messages[DEFAULT_LOCALE];
    documentRef.documentElement.lang = locale;
    const pageKey = documentRef.body.dataset.pageKey;
    const title = documentRef.querySelector('title');
    if (title && pageKey) title.textContent = `${dictionary.pageTitles[pageKey]} - EarnIt Kids`;
    const description = documentRef.querySelector('meta[name="description"]');
    if (description && pageKey) description.setAttribute('content', dictionary.descriptions[pageKey]);
    documentRef.querySelectorAll('[data-i18n]').forEach((element) => {
        element.textContent = getMessage(locale, element.dataset.i18n);
    });
    documentRef.querySelectorAll('[data-i18n-aria-label]').forEach((element) => {
        element.setAttribute('aria-label', getMessage(locale, element.dataset.i18nAriaLabel));
    });
    documentRef.querySelectorAll('[data-i18n-title]').forEach((element) => {
        element.title = getMessage(locale, element.dataset.i18nTitle);
    });
    documentRef.querySelectorAll('[data-language]').forEach((button) => {
        button.setAttribute('aria-pressed', button.dataset.language === locale ? 'true' : 'false');
    });
    localizePublicLinks(documentRef, locale, windowRef.location.origin);
    return dictionary;
}

export { messages };
