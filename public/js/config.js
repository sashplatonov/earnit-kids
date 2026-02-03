const CONFIG = {
    // Currency & Budget
    MONTHLY_LIMIT: 10000,
    CURRENCY_SYMBOL: '🪙',

    // Limits
    MAX_DAILY_COINS: 10,

    // Shop
    SHOP_ITEM_TYPES: {
        micro: { label: '🧁 Микро', value: 'micro' },
        small: { label: '📚 Малая', value: 'small' },
        large: { label: '💅 Крупная (1/мес)', value: 'large', limit: 1 }, // limit per month for this type
        activity: { label: '🤝 С родителями', value: 'activity' }
    },

    // Task Frequency Periods
    PERIODS: {
        day: { label: 'в день', display: 'день' },
        week: { label: 'в неделю', display: 'неделю' },
        month: { label: 'в месяц', display: 'месяц' },
        year: { label: 'в год', display: 'год' }
    },

    // UI
    TOAST_DURATION: 3000,
    HISTORY_LIMIT: 50,

    // Icons
    ICONS: {
        EARN: '💰',
        SPEND: '🛍️',
        REQUEST: '⏳',
        INCOMING: '📩',
        EMPTY_TASKS: '📋',
        EMPTY_REQUESTS: '📭',
        EMPTY_SHOP: '🛒',
        EMPTY_HISTORY: '📊',
        SUCCESS: '✓',
        ERROR: '✕',
        INFO: 'ℹ'
    }
};

// Export for Node.js if needed (though this is primarily client-side here)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CONFIG;
}
