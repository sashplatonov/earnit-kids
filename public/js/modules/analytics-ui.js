import { state } from './state.js';
import { fetchAnalyticsData } from './api.js';
import { showToast } from './utils.js';

export async function loadAnalytics(timeframe = 'month') {
    const data = await fetchAnalyticsData(timeframe, state.currentChildId);
    if (!data) return showToast('Не удалось загрузить данные о достижениях', 'error');

    updateSummaryUI(data.summary);
    renderAllCharts(data.topTasks, data.topItems);
}

function updateSummaryUI(summary) {
    const earnedEl = document.getElementById('stats-earned');
    const spentEl = document.getElementById('stats-spent');
    const netEl = document.getElementById('stats-net');

    if (earnedEl) earnedEl.textContent = `${summary.totalEarned} 🪙`;
    if (spentEl) spentEl.textContent = `${summary.totalSpent} 🪙`;
    if (netEl) {
        netEl.textContent = `${summary.netChange} 🪙`;
        netEl.className = 'stat-card__value ' + (summary.netChange >= 0 ? 'earn' : 'spend');
    }

    const tTotal = document.getElementById('tasks-total-coins');
    const iTotal = document.getElementById('items-total-coins');
    if (tTotal) tTotal.textContent = `Всего: ${summary.totalEarned} 🪙`;
    if (iTotal) iTotal.textContent = `Всего: ${summary.totalSpent} 🪙`;
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
