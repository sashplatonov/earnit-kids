import { state } from './state.js';
import { fetchAnalyticsData } from './api.js';
import { showToast } from './utils.js';

export async function loadAnalytics(timeframe = 'month') {
    const data = await fetchAnalyticsData(timeframe, state.currentChildId);
    if (!data) return showToast('Не удалось загрузить данные о достижениях', 'error');

    updateSummaryUI(data.summary, data.comparison);
    renderAllCharts(data.topTasks, data.topItems);
    renderTrendChart(data.trends);
    renderRecommendations(data.recommendations);
}

function updateSummaryUI(summary, comparison) {
    const earnedEl = document.getElementById('stats-earned');
    const spentEl = document.getElementById('stats-spent');
    const netEl = document.getElementById('stats-net');

    if (earnedEl) {
        earnedEl.innerHTML = `${summary.totalEarned} 🪙`;
        if (comparison) {
            addComparisonLabel(earnedEl, { current: summary.totalEarned, previous: comparison.totalEarned });
        }
    }

    if (spentEl) {
        spentEl.innerHTML = `${summary.totalSpent} 🪙`;
        if (comparison) {
            addComparisonLabel(spentEl, { current: summary.totalSpent, previous: comparison.totalSpent, reverse: true });
        }
    }

    if (netEl) {
        netEl.textContent = `${summary.netChange} 🪙`;
        netEl.className = 'stat-card__value ' + (summary.netChange >= 0 ? 'earn' : 'spend');
    }

    const tTotal = document.getElementById('tasks-total-coins');
    const iTotal = document.getElementById('items-total-coins');
    if (tTotal) tTotal.textContent = `Всего: ${summary.totalEarned} 🪙`;
    if (iTotal) iTotal.textContent = `Всего: ${summary.totalSpent} 🪙`;
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
        tooltip: (i) => `Сумма: ${i.coins} 🪙`
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
        tooltip: (i) => `Сумма: ${i.coins} 🪙`
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
            <div class="recommendation-card__coins">+${rec.coins} 🪙</div>
            <div class="recommendation-card__reason">${rec.reason}</div>
        `;
        container.appendChild(card);
    });
}
