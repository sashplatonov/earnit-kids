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

export const steps = [
    {
        title: 'Создайте',
        text: 'Родитель создаёт задания и награды. Например: «Помыть посуду — 10 монет» или «Прочитать книгу — 50 монет».',
    },
    {
        title: 'Просмотрите',
        text: 'Ребёнок видит только свои задания и награды в приложении. Всё прозрачно и понятно.',
    },
    {
        title: 'Запросите',
        text: 'Когда дело сделано, ребёнок нажимает кнопку «Готово». Родителю приходит уведомление о выполнении.',
    },
    {
        title: 'Получите',
        text: 'Родитель подтверждает выполнение одним тапом — и монеты залетают на баланс ребёнка.',
    },
] as const;

export const telegramAdvantage = {
    title: 'Почему в Telegram?',
    text: 'Вам не нужно скачивать отдельное приложение и создавать новый аккаунт. Telegram уже открыт у всех членов семьи.',
    features: [
        'Автоматические уведомления родителю о выполнении задач',
        'Подтверждение выполнения одним нажатием',
        'Все настройки семьи хранятся в Mini App',
    ],
} as const;

export const carousel = {
    title: 'Вот как это выглядит',
    items: [
        {
            image: '/img/public/screenshots/parent-home.png',
            caption: 'Главный экран родителя: баланс и входящие запросы',
        },
        {
            image: '/img/public/screenshots/parent-tasks.png',
            caption: 'Список заданий и настройки вознаграждений',
        },
        {
            image: '/img/public/screenshots/parent-family.png',
            caption: 'Управление семьёй и детьми',
        },
        {
            image: '/img/public/screenshots/child-today.png',
            caption: 'Экран ребёнка: задачи на сегодня',
        },
    ],
} as const;