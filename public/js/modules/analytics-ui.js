/** @file Analytics Ui frontend UI module */
import { state } from './state.js';
import { fetchAnalyticsData } from './api.js';
import { showToast } from './utils.js';

export async function loadAnalytics(timeframe = 'month') {
    const data = await fetchAnalyticsData(timeframe, state.currentChildId);
    if (!data) return showToast('Не удалось загрузить данные о достижениях', 'error');

    updateSummaryUI(data.summary, data.comparison);
    updateMiniAnalyticsUI(data);
    renderAllCharts(data.topTasks, data.topItems);
    renderTrendChart(data.trends);
    renderRecommendations(data.recommendations);
}

const WEEKLY_EARN_GOAL = CONFIG.WEEKLY_GOAL || 220;
const XP_PER_LEVEL = CONFIG.XP_PER_LEVEL || 120;

function updateElementText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function updateBar(id, percent) {
    const fill = document.getElementById(id);
    if (fill) {
        fill.style.setProperty('--progress', (Math.min(Math.max(percent, 0), 100) / 100).toFixed(2));
        fill.style.width = `${Math.min(Math.max(percent, 0), 100)}%`;
    }
}

function parseNumberFromText(text) {
    if (!text) return 0;
    const numeric = text.replace(/[^0-9.-]/g, '');
    const parsed = parseFloat(numeric);
    return Number.isFinite(parsed) ? parsed : 0;
}

function animateNumberText(id, target, options = {}) {
    const config = typeof options === 'function' ? { formatter: options } : options;
    const formatter = config.formatter ?? ((value) => Math.round(value).toString());
    const duration = config.duration ?? 600;
    const el = document.getElementById(id);
    if (!el) return;
    const startValue = parseFloat(el.dataset.lastValue ?? parseNumberFromText(el.textContent)) || 0;
    const startTime = performance.now();
    const easeOut = (t) => 1 - Math.pow(1 - t, 3);

    const animate = (timestamp) => {
        const progress = Math.min((timestamp - startTime) / duration, 1);
        const current = startValue + (target - startValue) * easeOut(progress);
        el.textContent = formatter(current);
        if (progress < 1) {
            requestAnimationFrame(animate);
        } else {
            el.dataset.lastValue = target;
        }
    };
    requestAnimationFrame(animate);
}

function getWeeklyStats(history) {
    const now = new Date();
    const startOfWeek = new Date(now);
    startOfWeek.setHours(0, 0, 0, 0);
    startOfWeek.setDate(startOfWeek.getDate() - 6);
    const rangeStart = startOfWeek.getTime();
    const rangeEnd = now.getTime();

    return (history || []).reduce((acc, entry) => {
        const entryDate = entry.date ? new Date(entry.date).getTime() : 0;
        if (!entryDate || entryDate < rangeStart || entryDate > rangeEnd) return acc;
        if (entry.type === 'earn') acc.earned += entry.amount || 0;
        if (entry.type === 'spend') acc.spent += entry.amount || 0;
        return acc;
    }, { earned: 0, spent: 0 });
}

function computeStreak(history) {
    let streak = 0;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    let pointer = new Date(today);
    const hasEarnOn = (dateStr) => (history || []).some((entry) => {
        return entry.type === 'earn' && entry.date && entry.date.startsWith(dateStr);
    });
    while (true) {
        const key = pointer.toISOString().slice(0, 10);
        if (!hasEarnOn(key)) break;
        streak += 1;
        pointer.setDate(pointer.getDate() - 1);
    }
    return streak;
}

function computeLevel(history) {
    const totalXp = (history || []).reduce((sum, entry) => entry.type === 'earn' ? sum + (entry.amount || 0) : sum, 0);
    const level = Math.max(1, Math.floor(totalXp / XP_PER_LEVEL) + 1);
    const xpInLevel = totalXp % XP_PER_LEVEL;
    const xpToNext = XP_PER_LEVEL - xpInLevel;
    const progress = xpInLevel / XP_PER_LEVEL * 100;
    return { level, xpToNext, progress: Number.isFinite(progress) ? progress : 0 };
}

function filterHistoryByChild(history, childId) {
    if (!Array.isArray(history)) return [];
    if (!childId) return history;
    return history.filter(entry => entry.childId == childId);
}

function updateMiniAnalyticsUI() {
    const childId = state.isAdmin ? state.currentChildId : null;
    const history = filterHistoryByChild(state.history, childId);

    // Week stats
    const weekStats = getWeeklyStats(history);
    animateNumberText('progress-week-earned-value', weekStats.earned);
    updateElementText('progress-week-earned-goal', `Цель: ${WEEKLY_EARN_GOAL} `);
    const goalEl = document.getElementById('progress-week-earned-goal');
    if (goalEl) {
        goalEl.innerHTML += `<span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1rem; height: 1rem; vertical-align: middle;"></span>`;
    }
    updateBar('progress-week-earned-bar', (weekStats.earned / WEEKLY_EARN_GOAL) * 100);

    // Streak
    const streak = computeStreak(history);
    animateNumberText('progress-streak-value', streak);
    updateBar('progress-streak-bar', Math.min(streak / 7, 1) * 100);
    updateElementText('progress-streak-note', streak > 0 ? `${streak} дн. подряд!` : 'Начните сегодня!');

    // Level
    const levelInfo = computeLevel(history);
    animateNumberText('progress-level-value', levelInfo.level, v => `Lv ${Math.floor(v)}`);
    updateElementText('progress-level-note', `до некст уровня ${levelInfo.xpToNext} XP`);
    updateBar('progress-level-bar', levelInfo.progress);
}

function updateSummaryUI(summary, comparison) {
    const earnedEl = document.getElementById('stats-earned');
    const spentEl = document.getElementById('stats-spent');
    const netEl = document.getElementById('stats-net');

    const tTotal = document.getElementById('tasks-total-coins');
    const iTotal = document.getElementById('items-total-coins');

    const currentBalance = (state.isAdmin && state.currentChildId)
        ? (state.children.find(c => c.id == state.currentChildId)?.balance || 0)
        : state.balance;

    const effectiveTotalEarned = currentBalance + summary.totalSpent;

    const iconHtml = ` <span class="gamified-icon icon-coin-stack" aria-hidden="true" style="width: 1.2rem; height: 1.2rem; vertical-align: middle;"></span>`;

    if (earnedEl) {
        earnedEl.innerHTML = `${effectiveTotalEarned}${iconHtml}`;
    }

    if (spentEl) {
        spentEl.innerHTML = `${summary.totalSpent}${iconHtml}`;
    }

    if (netEl) {
        netEl.innerHTML = `${currentBalance}${iconHtml}`;
        netEl.className = 'stat-card__value ' + (currentBalance >= 0 ? 'earn' : 'spend');
    }

    if (tTotal) tTotal.innerHTML = `Всего: ${effectiveTotalEarned}${iconHtml}`;
    if (iTotal) iTotal.innerHTML = `Всего: ${summary.totalSpent}${iconHtml}`;
}

function addComparisonLabel(parent, { current, previous, reverse = false }) {
    if (previous === 0) return;
    const diff = current - previous;
    const pct = Math.round((diff / previous) * 100);
    if (pct === 0) return;

    const info = document.createElement('div');
    info.className = 'stat-card__comparison';

    const isGood = reverse ? pct < 0 : pct > 0;
    const color = isGood ? '#4ade80' : '#f87171';
    const arrow = pct > 0 ? '↑' : '↓';

    info.style.color = color;
    info.style.fontSize = '0.75rem';
    info.style.fontWeight = '600';
    info.textContent = `${arrow} ${Math.abs(pct)}% к прошл. периоду`;
    parent.appendChild(info);
}

function renderAllCharts(tasks, items) {
    const common = { data: tasks, dataKey: 'coins', label: 'Монеты', bgColor: 'rgba(74, 222, 128, 0.5)', borderColor: '#4ade80' };
    renderGenericChart('tasks-coins-chart', { ...common, tooltip: (i) => `Выполнено: ${i.count} раз` });

    renderGenericChart('tasks-count-chart', {
        data: [...tasks].sort((a, b) => b.count - a.count),
        dataKey: 'count', label: 'Количество раз',
        bgColor: 'rgba(56, 189, 248, 0.5)', borderColor: '#38bdf8',
        tooltip: (i) => `Сумма: ${i.coins} мон.`
    });

    renderGenericChart('items-coins-chart', {
        data: items, dataKey: 'coins', label: 'Монеты',
        bgColor: 'rgba(248, 113, 113, 0.5)', borderColor: '#f87171',
        tooltip: (i) => `Куплено: ${i.count} раз`
    });

    renderGenericChart('items-count-chart', {
        data: [...items].sort((a, b) => b.count - a.count),
        dataKey: 'count', label: 'Количество раз',
        bgColor: 'rgba(251, 146, 60, 0.5)', borderColor: '#fb923c',
        tooltip: (i) => `Сумма: ${i.coins} мон.`
    });
}

function createChartBar(val, widthPct, { bgColor, borderColor }) {
    const bar = document.createElement('div');
    bar.className = 'html-chart-bar';
    bar.style.width = `max(30px, ${widthPct}%)`;
    bar.style.backgroundColor = bgColor;
    bar.style.border = `1px solid ${borderColor}`;

    const valSpan = document.createElement('span');
    valSpan.className = 'html-chart-value'; valSpan.textContent = val;
    bar.appendChild(valSpan);
    return bar;
}

function createChartRow(item, opts) {
    const val = item[opts.dataKey];
    const widthPct = opts.maxVal > 0 ? (val / opts.maxVal) * 100 : 0;
    const row = document.createElement('div');
    row.className = 'html-chart-row'; row.title = opts.tooltip(item);

    const labelDiv = document.createElement('div');
    labelDiv.className = 'html-chart-label'; labelDiv.textContent = item.name;

    const barContainer = document.createElement('div');
    barContainer.className = 'html-chart-bar-container';
    barContainer.appendChild(createChartBar(val, widthPct, opts));

    row.appendChild(labelDiv); row.appendChild(barContainer);
    return row;
}

function renderGenericChart(containerId, options) {
    const canvas = document.getElementById(containerId);
    if (!canvas) return;

    const parent = canvas.parentElement;
    let wrapper = parent.querySelector('.html-chart-wrapper');
    if (!wrapper) {
        wrapper = document.createElement('div');
        wrapper.className = 'html-chart-wrapper';
        parent.appendChild(wrapper);
        canvas.style.display = 'none';
    }

    wrapper.innerHTML = '';
    parent.style.height = 'auto'; parent.style.minHeight = '150px';

    if (options.data.length === 0) {
        wrapper.innerHTML = '<div style="color: var(--color-text-muted); text-align: center; padding: 2rem;">Нет данных</div>';
        return;
    }

    const maxVal = Math.max(...options.data.map(item => item[options.dataKey]));
    const chartHtml = document.createElement('div');
    chartHtml.className = 'html-chart';

    options.data.forEach(item => {
        chartHtml.appendChild(createChartRow(item, { ...options, maxVal }));
    });
    wrapper.appendChild(chartHtml);
}

function renderTrendChart(trends) {
    const canvas = document.getElementById('achievements-trend-chart');
    if (!canvas || !window.Chart) return;

    if (window.myTrendChart) window.myTrendChart.destroy();
    window.myTrendChart = new Chart(canvas, getTrendChartConfig(trends));
}

function getTrendChartConfig(trends) {
    return {
        type: 'line',
        data: {
            labels: trends.map(t => new Date(t.date).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })),
            datasets: [
                {
                    label: 'Заработано',
                    data: trends.map(t => t.earned),
                    borderColor: '#4ade80',
                    backgroundColor: 'rgba(74, 222, 128, 0.1)',
                    fill: true, tension: 0.4, pointRadius: 3
                },
                {
                    label: 'Потрачено',
                    data: trends.map(t => t.spent),
                    borderColor: '#f87171',
                    backgroundColor: 'rgba(248, 113, 113, 0.1)',
                    fill: true, tension: 0.4, pointRadius: 3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: true,
                    position: 'top',
                    align: 'end',
                    labels: {
                        color: 'rgba(0,0,0,0.7)',
                        font: { size: 11, weight: '600' },
                        usePointStyle: true,
                        boxWidth: 8
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: 'rgba(0,0,0,0.05)' },
                    ticks: { color: 'rgba(0,0,0,0.5)', font: { size: 10 } }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: 'rgba(0,0,0,0.5)', font: { size: 10 } }
                }
            }
        }
    };
}

function renderRecommendations(recommendations) {
    const container = document.getElementById('analytics-recommendations');
    if (!container) return;

    if (recommendations.length === 0) {
        container.innerHTML = '<div style="color: rgba(255,255,255,0.5); text-align: center; width: 100%;">Пока нет рекомендаций</div>';
        return;
    }

    container.innerHTML = '';
    recommendations.forEach(rec => {
        const card = document.createElement('div');
        card.className = 'recommendation-card';
        card.innerHTML = `
            <div class="recommendation-card__title">${rec.name}</div>
            <div class="recommendation-card__coins">+${rec.coins}<span class="gamified-icon icon-coin-stack" aria-hidden="true"></span></div>
            <div class="recommendation-card__reason">${rec.reason}</div>
        `;
        container.appendChild(card);
    });
}
