<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { loadAnalyticsData } from '$lib/services/api';
    import { modalStore } from '$lib/stores/modal';
    import { onMount } from 'svelte';

    type ChartInstance = { destroy(): void; data: unknown; update(): void };
    type ChartConstructor = new (ctx: CanvasRenderingContext2D, config: unknown) => ChartInstance;
    const ChartCtor = () => (window as unknown as Record<string, unknown>).Chart as ChartConstructor;

    $: isAdmin = $appStore.isAdmin;
    $: children = $appStore.children;
    $: hasChildren = children.length > 0;

    let timeframe: 'week' | 'month' | 'year' = 'month';
    let statsEarned = 0;
    let statsSpent = 0;
    let statsNet = 0;
    let levelValue = 'Lv 1';
    let levelBar = 0;
    let levelNote = 'до следующего уровня ... XP';
    let weekEarned = 0;
    let weekBar = 0;
    let weekGoal = '—';
    let streakValue = 0;
    let streakBar = 0;
    let streakNote = 'Начните сегодня!';
    let recommendations: Array<{ icon: string; text: string }> = [];

    // Chart instances
    let charts: Record<string, ChartInstance | null> = {};

    async function loadAndRender() {
        if (isAdmin && !hasChildren) {
            return;
        }

        const childId = $appStore.currentChildId;
        // Admin must have a selected child before we can load
        if (isAdmin && childId == null) return;

        const data = await loadAnalyticsData(childId, timeframe) as Record<string, unknown> | null;
        if (!data) return;

        statsEarned = (data.earned as number) ?? 0;
        statsSpent = (data.spent as number) ?? 0;
        statsNet = statsEarned - statsSpent;

        // Level/XP
        const xp = statsEarned;
        const xpPerLevel = 120;
        const level = Math.floor(xp / xpPerLevel) + 1;
        const xpInLevel = xp % xpPerLevel;
        levelValue = `Lv ${level}`;
        levelBar = Math.round((xpInLevel / xpPerLevel) * 100);
        levelNote = `до следующего уровня ${xpPerLevel - xpInLevel} XP`;

        weekEarned = (data.weekEarned as number) ?? 0;
        weekGoal = String((data.weekGoal as number) ?? '—');
        weekBar = (data.weekGoal as number) > 0
            ? Math.min(100, Math.round((weekEarned / (data.weekGoal as number)) * 100))
            : 0;

        streakValue = (data.streak as number) ?? 0;
        streakBar = Math.min(100, streakValue * 10);
        streakNote = streakValue > 0 ? `${streakValue} дней подряд!` : 'Начните сегодня!';

        recommendations = (data.recommendations as typeof recommendations) ?? [];

        // Render charts — retry if Chart.js CDN hasn't loaded yet
        if (ChartCtor()) {
            renderCharts(data);
        } else {
            let retries = 0;
            const tryRender = () => {
                if (ChartCtor()) {
                    renderCharts(data);
                } else if (retries++ < 12) {
                    setTimeout(tryRender, 500);
                }
            };
            setTimeout(tryRender, 500);
        }
    }

    function renderCharts(data: Record<string, unknown>) {
        const C = ChartCtor();
        if (!C) return;

        const taskCoinsData = (data.taskCoins as Array<{ label: string; value: number }>) ?? [];
        const taskCountData = (data.taskCount as Array<{ label: string; value: number }>) ?? [];
        const itemCoinsData = (data.itemCoins as Array<{ label: string; value: number }>) ?? [];
        const itemCountData = (data.itemCount as Array<{ label: string; value: number }>) ?? [];
        const trendData = (data.trend as Array<{ label: string; earned: number; spent: number }>) ?? [];

        const makeBar = (id: string, labels: string[], values: number[], color: string) => {
            charts[id]?.destroy();
            const canvas = document.getElementById(id) as HTMLCanvasElement | null;
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            if (!ctx) return;
            charts[id] = new C(ctx, {
                type: 'bar',
                data: { labels, datasets: [{ data: values, backgroundColor: color, borderRadius: 4 }] },
                options: { responsive: true, plugins: { legend: { display: false } } },
            });
        };

        makeBar('tasks-coins-chart', taskCoinsData.map(d => d.label), taskCoinsData.map(d => d.value), 'rgba(123,197,104,0.8)');
        makeBar('tasks-count-chart', taskCountData.map(d => d.label), taskCountData.map(d => d.value), 'rgba(123,197,104,0.5)');
        makeBar('items-coins-chart', itemCoinsData.map(d => d.label), itemCoinsData.map(d => d.value), 'rgba(255,174,66,0.8)');
        makeBar('items-count-chart', itemCountData.map(d => d.label), itemCountData.map(d => d.value), 'rgba(255,174,66,0.5)');

        // Trend chart (line)
        charts['achievements-trend-chart']?.destroy();
        const trendCanvas = document.getElementById('achievements-trend-chart') as HTMLCanvasElement | null;
        if (trendCanvas) {
            const ctx = trendCanvas.getContext('2d');
            if (ctx) {
                charts['achievements-trend-chart'] = new C(ctx, {
                    type: 'line',
                    data: {
                        labels: trendData.map(d => d.label),
                        datasets: [
                            { label: 'Заработано', data: trendData.map(d => d.earned), borderColor: 'rgba(123,197,104,1)', fill: false },
                            { label: 'Потрачено', data: trendData.map(d => d.spent), borderColor: 'rgba(255,174,66,1)', fill: false },
                        ],
                    },
                    options: { responsive: true },
                });
            }
        }
    }

    onMount(() => {
        void loadAndRender();
    });

    $: void ($appStore.currentChildId, timeframe, loadAndRender());
</script>

<svelte:head>
    <!-- Chart.js from CDN — matches legacy setup -->
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
</svelte:head>

<section id="analytics-section" class="section">
    <div class="container">
        {#if isAdmin && !hasChildren}
        <div class="empty-state" id="analytics-empty-state">
            <div class="empty-state__icon" aria-hidden="true">👨‍👩‍👧</div>
            <h2>Нет детей в профиле</h2>
            <p>Добавьте первого ребенка, чтобы видеть его достижения, создавать задания и следить за прогрессом.</p>
            <button class="btn btn--primary" id="analytics-add-child" type="button" on:click={() => modalStore.open('add-child-modal')}>
                Добавить ребенка
            </button>
        </div>
        {:else}
        <header class="section-header">
            <h2 class="section-title">Мои достижения</h2>
            <div class="analytics-filters">
                <div class="tab-group" id="analytics-timeframe-group">
                    <button class="tab-btn" class:active={timeframe === 'week'} data-timeframe="week"
                        on:click={() => timeframe = 'week'}>Неделя</button>
                    <button class="tab-btn" class:active={timeframe === 'month'} data-timeframe="month"
                        on:click={() => timeframe = 'month'}>Месяц</button>
                    <button class="tab-btn" class:active={timeframe === 'year'} data-timeframe="year"
                        on:click={() => timeframe = 'year'}>Год</button>
                </div>
            </div>
        </header>

        <!-- Summary -->
        <div class="analytics-summary">
            <div class="stat-card">
                <div class="stat-card__label">Заработано</div>
                <div class="stat-card__value earn" id="stats-earned">{statsEarned}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card__label">Потрачено</div>
                <div class="stat-card__value spend" id="stats-spent">{statsSpent}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card__label">Баланс</div>
                <div class="stat-card__value" id="stats-net">{statsNet} мон.</div>
            </div>
        </div>

        <!-- Mini progress -->
        <div class="analytics-mini" aria-label="Краткий прогресс">
            <article class="analytics-mini__item progress-card--level">
                <div class="analytics-mini__header">
                    <span>⭐ Уровень</span>
                    <span class="analytics-mini__value" id="progress-level-value">{levelValue}</span>
                </div>
                <div class="progress-track">
                    <span class="progress-fill" id="progress-level-bar" style="--progress: {levelBar};"></span>
                </div>
                <div class="analytics-mini__footer">
                    <p class="analytics-mini__hint" id="progress-level-note">{levelNote}</p>
                    <p class="analytics-mini__info">1 монета = 1 XP. Каждый уровень требует 120 XP.</p>
                </div>
            </article>

            <article class="analytics-mini__item">
                <div class="analytics-mini__header">
                    <span>📅 За неделю</span>
                    <span class="analytics-mini__value" id="progress-week-earned-value">{weekEarned}</span>
                </div>
                <div class="progress-track">
                    <span class="progress-fill" id="progress-week-earned-bar" style="--progress: {weekBar};"></span>
                </div>
                <p class="analytics-mini__hint" id="progress-week-earned-goal">Цель: {weekGoal} мон.</p>
            </article>

            <article class="analytics-mini__item">
                <div class="analytics-mini__header">
                    <span>🔥 Серия (Стрик)</span>
                    <span class="analytics-mini__value" id="progress-streak-value">{streakValue}</span>
                </div>
                <div class="progress-track">
                    <span class="progress-fill" id="progress-streak-bar" style="--progress: {streakBar};"></span>
                </div>
                <p class="analytics-mini__hint" id="progress-streak-note">{streakNote}</p>
            </article>
        </div>

        <!-- Charts grid -->
        <div class="analytics-sections">
            <div class="analytics-group">
                <h3 class="analytics-group-title">Задания <span id="tasks-total-coins" class="group-total-badge">{statsEarned > 0 ? `Всего: ${statsEarned}` : ''}</span></h3>
                <div class="analytics-grid">
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">По сумме монет</h4>
                        <div class="chart-container">
                            <canvas id="tasks-coins-chart"></canvas>
                        </div>
                    </div>
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">По количеству раз</h4>
                        <div class="chart-container">
                            <canvas id="tasks-count-chart"></canvas>
                        </div>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">Магазин и траты <span id="items-total-coins" class="group-total-badge">{statsSpent > 0 ? `Всего: ${statsSpent}` : ''}</span></h3>
                <div class="analytics-grid">
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">По сумме монет</h4>
                        <div class="chart-container">
                            <canvas id="items-coins-chart"></canvas>
                        </div>
                    </div>
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">По количеству раз</h4>
                        <div class="chart-container">
                            <canvas id="items-count-chart"></canvas>
                        </div>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">Динамика достижений</h3>
                <div class="card analytics-chart-card">
                    <div class="chart-container">
                        <canvas id="achievements-trend-chart"></canvas>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">Идеи для роста</h3>
                <div id="analytics-recommendations" class="recommendations-grid">
                    {#each recommendations as rec (rec.text)}
                    <div class="recommendation-card">
                        <span class="recommendation-icon">{rec.icon}</span>
                        <p>{rec.text}</p>
                    </div>
                    {/each}
                </div>
            </div>
        </div>
        {/if}
    </div>
</section>
