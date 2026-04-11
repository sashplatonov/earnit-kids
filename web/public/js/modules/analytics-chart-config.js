/** @file Analytics chart config frontend UI module */

function buildTrendLabels(trends) {
    return trends.map(trend => new Date(trend.date).toLocaleDateString('ru-RU', {
        day: 'numeric',
        month: 'short'
    }));
}

function buildTrendDataset({ label, values, borderColor, backgroundColor }) {
    return {
        label,
        data: values,
        borderColor,
        backgroundColor,
        fill: true,
        tension: 0.4,
        pointRadius: 3
    };
}

function buildTrendScale({ hideGrid = false } = {}) {
    return {
        grid: hideGrid ? { display: false } : { color: 'rgba(0,0,0,0.05)' },
        ticks: { color: 'rgba(0,0,0,0.5)', font: { size: 10 } },
        ...(hideGrid ? {} : { beginAtZero: true })
    };
}

export function getTrendChartConfig(trends) {
    return {
        type: 'line',
        data: {
            labels: buildTrendLabels(trends),
            datasets: [
                buildTrendDataset({
                    label: 'Заработано',
                    values: trends.map(trend => trend.earned),
                    borderColor: '#4ade80',
                    backgroundColor: 'rgba(74, 222, 128, 0.1)'
                }),
                buildTrendDataset({
                    label: 'Потрачено',
                    values: trends.map(trend => trend.spent),
                    borderColor: '#f87171',
                    backgroundColor: 'rgba(248, 113, 113, 0.1)'
                })
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
                y: buildTrendScale(),
                x: buildTrendScale({ hideGrid: true })
            }
        }
    };
}