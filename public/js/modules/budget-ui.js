/** @file Budget Ui frontend UI module */
function isEntryValid(entry, childId, monthKey) {
    if (childId && String(entry.childId) !== String(childId)) return false;
    if (entry.type !== 'spend' || !entry.date) return false;
    const dateStr = typeof entry.date === 'string' ? entry.date : new Date(entry.date).toISOString();
    return dateStr.startsWith(monthKey);
}

function processMonthlyEntry(entry, filters) {
    const { childId, monthKey, state } = filters;
    if (!isEntryValid(entry, childId, monthKey)) return null;

    let largePurchase = null;
    if (entry.itemId) {
        const item = state.shopItems.find(i => String(i.id) === String(entry.itemId));
        if (item && item.type === 'large') largePurchase = item.name;
    }

    const amount = Number(entry.moneyAmount || entry.rsdAmount || 0);
    return { amount, largePurchase };
}

function getMonthlyStats(state, monthKey, childId = null) {
    let moneySpent = 0;
    let largePurchase = null;

    (state.history || []).forEach(entry => {
        const result = processMonthlyEntry(entry, { childId, monthKey, state });
        if (!result) return;
        moneySpent += result.amount;
        if (result.largePurchase) largePurchase = result.largePurchase;
    });

    return { moneySpent, largePurchase };
}

function getDailyStats(state, childId = null) {
    const today = new Date().toISOString().slice(0, 10);
    let earnedToday = 0;

    (state.history || []).forEach((entry) => {
        if (childId && entry.childId != childId) return;
        if (entry.type !== 'earn' || !entry.date) return;

        const dateStr = typeof entry.date === 'string' ? entry.date : new Date(entry.date).toISOString();
        if (dateStr.startsWith(today)) {
            earnedToday += (entry.amount || 0);
        }
    });

    return { earnedToday };
}

function updateHeaderEarnedDisplay(earnedToday, dailyLimit) {
    const countEl = document.getElementById('header-earned-count');
    const limitNoteEl = document.getElementById('header-earned-limit-note');
    const track = document.getElementById('header-earned-progress');

    if (countEl) countEl.textContent = earnedToday;
    if (limitNoteEl) {
        limitNoteEl.textContent = dailyLimit > 0 ? `Лимит: ${dailyLimit}` : 'Лимит: ∞';
    }

    if (!track) return;
    if (dailyLimit <= 0) {
        track.style.width = '0%';
        track.style.background = 'rgba(255, 255, 255, 0.5)';
        return;
    }

    const progress = Math.min((earnedToday / dailyLimit) * 100, 100);
    track.style.width = `${progress}%`;
    track.style.background = progress >= 100
        ? 'linear-gradient(90deg, #f87171, #ef4444)'
        : 'linear-gradient(90deg, #a3f7bf, #22c55e)';
}

function updateMonthlyTexts(stats, monthlyLimit) {
    const spentEl = document.getElementById('money-spent') || document.getElementById('rsd-spent');
    if (spentEl) spentEl.textContent = stats.moneySpent.toLocaleString();

    const limitEl = document.getElementById('money-limit') || document.getElementById('rsd-limit');
    if (limitEl) limitEl.textContent = monthlyLimit.toLocaleString();

    const remainingMoneyEl = document.getElementById('money-remaining');
    if (remainingMoneyEl) {
        const remaining = Math.max(0, monthlyLimit - stats.moneySpent);
        remainingMoneyEl.textContent = `(осталось ${remaining.toLocaleString()})`;
        remainingMoneyEl.style.color = remaining === 0 ? '#ff4757' : 'rgba(255,255,255,0.6)';
    }
}

function updateMonthlyBudgetUI(stats, monthlyLimit) {
    updateMonthlyTexts(stats, monthlyLimit);

    const bar = document.getElementById('money-progress') || document.getElementById('rsd-progress');
    if (!bar) return;

    if (monthlyLimit <= 0) {
        bar.style.width = '0%';
        bar.className = 'progress-bar';
        if (bar.parentElement) bar.parentElement.style.opacity = 0.45;
        return;
    }

    const progress = Math.min((stats.moneySpent / monthlyLimit) * 100, 100);
    bar.style.width = `${progress}%`;
    bar.className = 'progress-bar';
    if (progress > 90) bar.classList.add('danger');
    else if (progress > 70) bar.classList.add('warning');
    if (bar.parentElement) bar.parentElement.style.opacity = 1;
}

function updateDailyTexts(dailyStats, dailyLimit) {
    const earnedTodayEl = document.getElementById('coins-earned-today');
    if (earnedTodayEl) earnedTodayEl.textContent = dailyStats.earnedToday;

    const dailyLimitEl = document.getElementById('coins-daily-limit');
    if (dailyLimitEl) dailyLimitEl.textContent = dailyLimit > 0 ? `Лимит: ${dailyLimit}` : 'Лимит: ∞';

    const remainingEl = document.getElementById('coins-daily-remaining');
    if (!remainingEl) return;

    if (dailyLimit <= 0) {
        remainingEl.textContent = 'Лимит не установлен';
        remainingEl.style.color = 'rgba(255,255,255,0.55)';
        return;
    }
    const remaining = Math.max(0, dailyLimit - dailyStats.earnedToday);
    remainingEl.textContent = `Осталось: ${remaining} 🪙`;
    remainingEl.style.color = remaining === 0 ? '#ff6b6b' : 'rgba(255,255,255,0.7)';
}

function applyDailyBarProgress(dailyBar, dailyStats, dailyLimit) {
    if (dailyLimit <= 0) {
        dailyBar.style.width = '0%';
        dailyBar.classList.remove('warning', 'danger');
        if (dailyBar.parentElement) dailyBar.parentElement.style.opacity = 0.45;
        return;
    }
    const progress = Math.min((dailyStats.earnedToday / dailyLimit) * 100, 100);
    dailyBar.style.width = `${progress}%`;
    dailyBar.classList.remove('warning', 'danger');
    if (progress >= 100) dailyBar.classList.add('danger');
    else if (progress > 80) dailyBar.classList.add('warning');
    if (dailyBar.parentElement) dailyBar.parentElement.style.opacity = 1;
}

function updateDailyLimitUI(dailyStats, dailyLimit) {
    updateDailyTexts(dailyStats, dailyLimit);
    const dailyBar = document.getElementById('coins-daily-progress');
    if (dailyBar) {
        dailyBar.className = 'progress-bar';
        applyDailyBarProgress(dailyBar, dailyStats, dailyLimit);
    }
}

function toSafeLimit(value, fallback = 0) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return fallback;
    return numeric;
}

function getChildDailyLimit(child, fallback) {
    if (!child) return toSafeLimit(fallback, 0);
    if (child.dailyCoinLimit !== undefined && child.dailyCoinLimit !== null) {
        return toSafeLimit(child.dailyCoinLimit, 0);
    }
    if (child.daily_coin_limit !== undefined && child.daily_coin_limit !== null) {
        return toSafeLimit(child.daily_coin_limit, 0);
    }
    return toSafeLimit(fallback, 0);
}

function resolveDailyLimit(state, activeChildId) {
    if (state.isAdmin) {
        const child = (state.children || []).find((item) => String(item.id) === String(activeChildId));
        return getChildDailyLimit(child, state.dailyCoinLimit);
    }
    if (state.children && state.children.length > 0) {
        return getChildDailyLimit(state.children[0], state.dailyCoinLimit);
    }
    return toSafeLimit(state.dailyCoinLimit, 0);
}

function updateLargePurchaseUI(stats) {
    const largeEl = document.getElementById('large-purchase');
    const largeIcon = document.getElementById('large-icon');

    if (!largeEl || !largeIcon) return;

    if (stats.largePurchase) {
        largeEl.textContent = stats.largePurchase;
        largeIcon.textContent = '✅';
        largeIcon.style.background = 'rgba(16, 185, 129, 0.2)';
        return;
    }

    largeEl.textContent = 'Нет';
    largeIcon.textContent = '⬜';
    largeIcon.style.background = 'rgba(255, 255, 255, 0.1)';
}

export function updateBudgetStatsUI(state, config, activeChildId) {
    const currentMonth = new Date().toISOString().slice(0, 7);
    const scopedChildId = state.isAdmin ? activeChildId : null;

    // Safety check for history
    if (!state.history) state.history = [];

    const stats = getMonthlyStats(state, currentMonth, scopedChildId);

    const monthlyLimitRaw = (state.monthlyLimit !== undefined && state.monthlyLimit !== null)
        ? state.monthlyLimit
        : config.MONTHLY_LIMIT;
    const monthlyLimit = toSafeLimit(monthlyLimitRaw, config.MONTHLY_LIMIT);

    updateMonthlyBudgetUI(stats, monthlyLimit);

    const dailyStats = getDailyStats(state, scopedChildId);
    const dailyLimit = resolveDailyLimit(state, scopedChildId);
    updateDailyLimitUI(dailyStats, dailyLimit);
    updateHeaderEarnedDisplay(dailyStats.earnedToday, dailyLimit);
    updateLargePurchaseUI(stats);
}
