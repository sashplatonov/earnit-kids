import { state } from './state.js';
import { fetchAnalyticsData } from './api.js';
import { showToast } from './utils.js';

let charts = {}; // No longer used for Chart.js instances, but kept for compatibility if needed.


export async function loadAnalytics(timeframe = 'month') {
    if (!state.isAdmin) return;

    const childId = state.currentChildId;
    const data = await fetchAnalyticsData(timeframe, childId);

    if (!data) {
        showToast('Не удалось загрузить данные аналитики', 'error');
        return;
    }

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

    // Update group total badges
    const tasksTotalEl = document.getElementById('tasks-total-coins');
    const itemsTotalEl = document.getElementById('items-total-coins');
    if (tasksTotalEl) tasksTotalEl.textContent = `Всего: ${summary.totalEarned} 🪙`;
    if (itemsTotalEl) itemsTotalEl.textContent = `Всего: ${summary.totalSpent} 🪙`;
}

function renderAllCharts(tasks, items) {
    // Earned Coins (Tasks)
    renderGenericChart(
        'tasks-coins-chart',
        'tasksCoins',
        tasks,
        'coins',
        'Монеты',
        'rgba(74, 222, 128, 0.5)',
        '#4ade80',
        (item) => `Выполнено: ${item.count} раз`
    );

    // Done Count (Tasks)
    renderGenericChart(
        'tasks-count-chart',
        'tasksCount',
        [...tasks].sort((a, b) => b.count - a.count),
        'count',
        'Количество раз',
        'rgba(56, 189, 248, 0.5)',
        '#38bdf8',
        (item) => `Сумма: ${item.coins} 🪙`
    );

    // Spent Coins (Items)
    renderGenericChart(
        'items-coins-chart',
        'itemsCoins',
        items,
        'coins',
        'Монеты',
        'rgba(248, 113, 113, 0.5)',
        '#f87171',
        (item) => `Куплено: ${item.count} раз`
    );

    // Bought Count (Items)
    renderGenericChart(
        'items-count-chart',
        'itemsCount',
        [...items].sort((a, b) => b.count - a.count),
        'count',
        'Количество раз',
        'rgba(251, 146, 60, 0.5)',
        '#fb923c',
        (item) => `Сумма: ${item.coins} 🪙`
    );
}


function renderGenericChart(containerId, chartKey, data, dataKey, label, bgColor, borderColor, tooltipCallback) {
    const canvas = document.getElementById(containerId);
    if (!canvas) return;

    // We'll replace the canvas visual with a custom HTML container
    const parentContainer = canvas.parentElement;

    // Create or get the HTML chart wrapper
    let htmlWrapper = parentContainer.querySelector('.html-chart-wrapper');
    if (!htmlWrapper) {
        htmlWrapper = document.createElement('div');
        htmlWrapper.className = 'html-chart-wrapper';
        parentContainer.appendChild(htmlWrapper);
        canvas.style.display = 'none'; // Hide the original canvas
    }

    // Clear previous
    htmlWrapper.innerHTML = '';

    // Allow natural height based on content
    parentContainer.style.height = 'auto';
    parentContainer.style.minHeight = '150px';

    if (data.length === 0) {
        htmlWrapper.innerHTML = '<div style="color: rgba(255,255,255,0.5); text-align: center; padding: 2rem;">Нет данных</div>';
        return;
    }

    // Find max value to calculate percentage relative widths
    const maxVal = Math.max(...data.map(item => item[dataKey]));

    const chartHtml = document.createElement('div');
    chartHtml.className = 'html-chart';

    data.forEach(item => {
        const val = item[dataKey];
        // Ensure at least 5% width so the text has a bit of background, 
        // or just let it overflow if very small.
        let widthPct = maxVal > 0 ? (val / maxVal) * 100 : 0;

        const row = document.createElement('div');
        row.className = 'html-chart-row';
        row.title = tooltipCallback(item); // Native browser tooltip for extra info

        const labelDiv = document.createElement('div');
        labelDiv.className = 'html-chart-label';
        labelDiv.textContent = item.name;

        const barContainer = document.createElement('div');
        barContainer.className = 'html-chart-bar-container';

        const bar = document.createElement('div');
        bar.className = 'html-chart-bar';
        bar.style.width = `max(30px, ${widthPct}%)`; // Ensure min width to show the number
        bar.style.backgroundColor = bgColor;
        bar.style.border = `1px solid ${borderColor}`;

        const valSpan = document.createElement('span');
        valSpan.className = 'html-chart-value';
        valSpan.textContent = val;

        bar.appendChild(valSpan);
        barContainer.appendChild(bar);

        row.appendChild(labelDiv);
        row.appendChild(barContainer);

        chartHtml.appendChild(row);
    });

    htmlWrapper.appendChild(chartHtml);
}
