/** @file Ui Config frontend UI module */
export const CONFIG = window.CONFIG || {
    MONTHLY_LIMIT: 10000,
    PERIODS: {
        day: { display: 'день' },
        week: { display: 'нед' },
        month: { display: 'мес' },
        year: { display: 'год' }
    },
    SHOP_ITEM_TYPES: {
        small: { label: 'Мелочь' },
        medium: { label: 'Среднее' },
        large: { label: 'Крупное' }
    }
};
