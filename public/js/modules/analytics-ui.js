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

function clampProgress(value) {
    if (!Number.isFinite(value)) return 0;
    return Math.max(0, Math.min(1, value));
}

function normalizeMiniInput(data) {
    return {
        summary: data?.summary || {},
        recommendations: Array.isArray(data?.recommendations) ? data.recommendations : [],
        topItems: Array.isArray(data?.topItems) ? data.topItems : [],
        trends: Array.isArray(data?.trends) ? data.trends : []
    };
}

function getDayProgress(totalEarned) {
    return clampProgress((totalEarned || 0) / 200);
}

function getShopReadiness(topItems, netChange) {
    if (!topItems.length) return 0;
    const availableItems = topItems.filter(item => Number(item.coins) <= Number(netChange || 0)).length;
    return clampProgress(availableItems / topItems.length);
}

function getRecentActivity(trends) {
    return trends.slice(-7).filter(day => Number(day.earned || 0) > 0).length;
}

export function computeMiniAnalytics(data) {
    const { summary, recommendations, topItems, trends } = normalizeMiniInput(data);
    const dayProgress = getDayProgress(summary.totalEarned);
    const shopReadiness = getShopReadiness(topItems, summary.netChange);
    const recentActivity = getRecentActivity(trends);
    const streakProgress = clampProgress(recentActivity / 7);

    return {
        dayProgress,
        shopReadiness,
        streakProgress,
        dayLabel: `${Math.round(dayProgress * 100)}%`,
        shopLabel: `${Math.round(shopReadiness * 100)}%`,
        streakLabel: `${recentActivity} дн.`,
        dayHint: dayProgress < 1
            ? 'Завершите ещё одно задание, чтобы закрыть цель дня.'
            : 'Цель дня закрыта. Отличная работа!',
        shopHint: recommendations.length ? `Добавьте награду «${recommendations[0].name}», чтобы расширить магазин.` : 'Магазин выглядит сбалансированным.',
        streakHint: recentActivity
            ? `Активных дней за неделю: ${recentActivity} из 7.`
            : 'Начните с одного задания сегодня, чтобы запустить серию.'
    };
}

function setMiniProgress({ key, value, label, hint }) {
    const progress = document.querySelector(`[data-progress-${key}]`);
    if (progress) progress.style.setProperty('--progress', value.toFixed(2));
    const valueEl = document.querySelector(`[data-mini-value-${key}]`);
    if (valueEl) valueEl.textContent = label;
    const hintEl = document.querySelector(`[data-mini-hint-${key}]`);
    if (hintEl) hintEl.textContent = hint;
}

function updateMiniAnalyticsUI(data) {
    const mini = computeMiniAnalytics(data);
    setMiniProgress({ key: 'day', value: mini.dayProgress, label: mini.dayLabel, hint: mini.dayHint });
    setMiniProgress({ key: 'shop', value: mini.shopReadiness, label: mini.shopLabel, hint: mini.shopHint });
    setMiniProgress({ key: 'streak', value: mini.streakProgress, label: mini.streakLabel, hint: mini.streakHint });
}

function updateSummaryUI(summary, comparison) {
    const earnedEl = document.getElementById('stats-earned');
    const spentEl = document.getElementById('stats-spent');
    const netEl = document.getElementById('stats-net');

    const tTotal = document.getElementById('tasks-total-coins');
    const iTotal = document.getElementById('items-total-coins');

    // Net change calculation fix:
    // Total Earned = Current Balance + Total Spent (this period)
    // This ensures consistency even if some history is missing.
    const currentBalance = (state.isAdmin && state.currentChildId)
        ? (state.children.find(c => c.id == state.currentChildId)?.balance || 0)
        : state.balance;

    const effectiveTotalEarned = currentBalance + summary.totalSpent;

    if (earnedEl) {
        earnedEl.textContent = `${effectiveTotalEarned} ${CONFIG.CURRENCY_SYMBOL || 'мон.'}`;
        if (comparison) {
            addComparisonLabel(earnedEl, { current: effectiveTotalEarned, previous: comparison.totalEarned });
        }
    }

    if (spentEl) {
        spentEl.textContent = `${summary.totalSpent} ${CONFIG.CURRENCY_SYMBOL || 'мон.'}`;
        if (comparison) {
            addComparisonLabel(spentEl, { current: summary.totalSpent, previous: comparison.totalSpent, reverse: true });
        }
    }

    if (netEl) {
        netEl.textContent = `${currentBalance} ${CONFIG.CURRENCY_SYMBOL || 'мон.'}`;
        netEl.className = 'stat-card__value ' + (currentBalance >= 0 ? 'earn' : 'spend');
    }

    if (tTotal) tTotal.textContent = `Всего: ${effectiveTotalEarned} ${CONFIG.CURRENCY_SYMBOL || 'мон.'}`;
    if (iTotal) iTotal.textContent = `Всего: ${summary.totalSpent} ${CONFIG.CURRENCY_SYMBOL || 'мон.'}`;
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
        wrapper.innerHTML = '<div style="color: rgba(255,255,255,0.5); text-align: center; padding: 2rem;">Нет данных</div>';
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
                    labels: { color: 'rgba(255,255,255,0.7)', font: { size: 10 } }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    grid: { color: 'rgba(255,255,255,0.05)' },
                    ticks: { color: 'rgba(255,255,255,0.5)', font: { size: 10 } }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: 'rgba(255,255,255,0.5)', font: { size: 10 } }
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
