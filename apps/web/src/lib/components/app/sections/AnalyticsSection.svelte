<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { loadAnalyticsData } from '$lib/services/api';
    import { modalStore } from '$lib/stores/modal';
    import { onMount } from 'svelte';
    import {
        buildAnalyticsViewModel,
        type AnalyticsRecommendationCard,
        type AnalyticsViewModel,
        type AnalyticsViewModelI18n,
    } from './analyticsViewModel';

    type ChartInstance = { destroy(): void; data: unknown; update(): void };
    type ChartConstructor = new (ctx: CanvasRenderingContext2D, config: unknown) => ChartInstance;
    const ChartCtor = () => (window as unknown as Record<string, unknown>).Chart as ChartConstructor;
    const i18n = useI18n();

    $: isAdmin = $appStore.isAdmin;
    $: children = $appStore.children;
    $: hasChildren = children.length > 0;

    let timeframe: 'week' | 'month' | 'year' = 'month';
    let statsEarned = 0;
    let statsSpent = 0;
    let statsNet = 0;
    let levelValue = tAnalytics('progress.levelValue', { level: 1 });
    let levelBar = 0;
    let levelNote = tAnalytics('progress.levelNote', { remaining: 120 });
    let weekEarned = 0;
    let weekBar = 0;
    let weekNote = tAnalytics('model.noActivityWeek');
    let streakValue = 0;
    let streakBar = 0;
    let streakNote = tAnalytics('model.streakToday');
    let recommendations: AnalyticsRecommendationCard[] = [];

    // Chart instances
    let charts: Record<string, ChartInstance | null> = {};

    function tAnalytics(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`analytics.${key}` as MessageKey, variables);
    }

    function createViewModelI18n(): AnalyticsViewModelI18n {
        return {
            locale: $i18n.locale,
            formatShortDate(value: string) {
                const parsed = new Date(`${value}T00:00:00Z`);
                return Number.isNaN(parsed.getTime()) ? value : $i18n.formatShortDate(parsed);
            },
            t(key, variables) {
                return tAnalytics(`model.${key}`, variables);
            },
        };
    }

    async function loadAndRender() {
        if (isAdmin && !hasChildren) {
            return;
        }

        const childId = $appStore.currentChildId;
        // Admin must have a selected child before we can load
        if (isAdmin && childId == null) return;

        const data = await loadAnalyticsData(childId, timeframe);
        if (!data) return;

        const view = buildAnalyticsViewModel(data, {
            currentBalance: $appStore.balance,
            tasks: $appStore.tasks,
            i18n: createViewModelI18n(),
        });

        statsEarned = view.earned;
        statsSpent = view.spent;
        statsNet = view.net;

        // Level/XP
        const xp = statsEarned;
        const xpPerLevel = 120;
        const level = Math.floor(xp / xpPerLevel) + 1;
        const xpInLevel = xp % xpPerLevel;
        levelValue = tAnalytics('progress.levelValue', { level });
        levelBar = Math.round((xpInLevel / xpPerLevel) * 100);
        levelNote = tAnalytics('progress.levelNote', { remaining: xpPerLevel - xpInLevel });

        weekEarned = view.weekEarned;
        weekBar = view.weekBar;
        weekNote = view.weekNote;

        streakValue = view.streakValue;
        streakBar = Math.min(100, streakValue * 10);
        streakNote = view.streakNote;

        recommendations = view.recommendations;

        // Render charts — retry if Chart.js CDN hasn't loaded yet
        if (ChartCtor()) {
            renderCharts(view);
        } else {
            let retries = 0;
            const tryRender = () => {
                if (ChartCtor()) {
                    renderCharts(view);
                } else if (retries++ < 12) {
                    setTimeout(tryRender, 500);
                }
            };
            setTimeout(tryRender, 500);
        }
    }

    function renderCharts(view: AnalyticsViewModel) {
        const C = ChartCtor();
        if (!C) return;

        const taskCoinsData = view.taskCoins;
        const taskCountData = view.taskCount;
        const itemCoinsData = view.itemCoins;
        const itemCountData = view.itemCount;
        const trendData = view.trend;

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
                            { label: tAnalytics('charts.datasetEarned'), data: trendData.map(d => d.earned), borderColor: 'rgba(123,197,104,1)', fill: false },
                            { label: tAnalytics('charts.datasetSpent'), data: trendData.map(d => d.spent), borderColor: 'rgba(255,174,66,1)', fill: false },
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

    $: void ($appStore.currentChildId, $appStore.balance, $appStore.tasks, $i18n.locale, timeframe, loadAndRender());
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
            <h2>{tAnalytics('empty.title')}</h2>
            <p>{tAnalytics('empty.description')}</p>
            <button class="btn btn--primary" id="analytics-add-child" type="button" on:click={() => modalStore.open('add-child-modal')}>
                {tAnalytics('empty.action')}
            </button>
        </div>
        {:else}
        <header class="section-header">
            <h2 class="section-title">{tAnalytics('section.title')}</h2>
            <div class="analytics-filters">
                <div class="tab-group" id="analytics-timeframe-group">
                    <button class="tab-btn" class:active={timeframe === 'week'} data-timeframe="week"
                        on:click={() => timeframe = 'week'}>{tAnalytics('timeframe.week')}</button>
                    <button class="tab-btn" class:active={timeframe === 'month'} data-timeframe="month"
                        on:click={() => timeframe = 'month'}>{tAnalytics('timeframe.month')}</button>
                    <button class="tab-btn" class:active={timeframe === 'year'} data-timeframe="year"
                        on:click={() => timeframe = 'year'}>{tAnalytics('timeframe.year')}</button>
                </div>
            </div>
        </header>

        <!-- Summary -->
        <div class="analytics-summary">
            <div class="stat-card">
                <div class="stat-card__label">{tAnalytics('summary.earned')}</div>
                <div class="stat-card__value earn" id="stats-earned">{statsEarned}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card__label">{tAnalytics('summary.spent')}</div>
                <div class="stat-card__value spend" id="stats-spent">{statsSpent}</div>
            </div>
            <div class="stat-card">
                <div class="stat-card__label">{tAnalytics('summary.balance')}</div>
                <div class="stat-card__value" id="stats-net">{statsNet}</div>
            </div>
        </div>

        <!-- Mini progress -->
        <div class="analytics-mini" aria-label={tAnalytics('progress.ariaLabel')}>
            <article class="analytics-mini__item progress-card--level">
                <div class="analytics-mini__header">
                    <span>⭐ {tAnalytics('progress.level')}</span>
                    <span class="analytics-mini__value" id="progress-level-value">{levelValue}</span>
                </div>
                <div class="progress-track">
                    <span class="progress-fill" id="progress-level-bar" style="--progress: {levelBar};"></span>
                </div>
                <div class="analytics-mini__footer">
                    <p class="analytics-mini__hint" id="progress-level-note">{levelNote}</p>
                    <p class="analytics-mini__info">{tAnalytics('progress.levelInfo')}</p>
                </div>
            </article>

            <article class="analytics-mini__item">
                <div class="analytics-mini__header">
                    <span>📅 {tAnalytics('progress.week')}</span>
                    <span class="analytics-mini__value" id="progress-week-earned-value">{weekEarned}</span>
                </div>
                <div class="progress-track">
                    <span class="progress-fill" id="progress-week-earned-bar" style="--progress: {weekBar};"></span>
                </div>
                <p class="analytics-mini__hint" id="progress-week-earned-goal">{weekNote}</p>
            </article>

            <article class="analytics-mini__item">
                <div class="analytics-mini__header">
                    <span>🔥 {tAnalytics('progress.streak')}</span>
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
                <h3 class="analytics-group-title">{tAnalytics('charts.tasks')} <span id="tasks-total-coins" class="group-total-badge">{statsEarned > 0 ? tAnalytics('charts.totalBadge', { value: statsEarned }) : ''}</span></h3>
                <div class="analytics-grid">
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">{tAnalytics('charts.byCoins')}</h4>
                        <div class="chart-container">
                            <canvas id="tasks-coins-chart"></canvas>
                        </div>
                    </div>
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">{tAnalytics('charts.byCount')}</h4>
                        <div class="chart-container">
                            <canvas id="tasks-count-chart"></canvas>
                        </div>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">{tAnalytics('charts.items')} <span id="items-total-coins" class="group-total-badge">{statsSpent > 0 ? tAnalytics('charts.totalBadge', { value: statsSpent }) : ''}</span></h3>
                <div class="analytics-grid">
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">{tAnalytics('charts.byCoins')}</h4>
                        <div class="chart-container">
                            <canvas id="items-coins-chart"></canvas>
                        </div>
                    </div>
                    <div class="card analytics-chart-card">
                        <h4 class="card__subtitle">{tAnalytics('charts.byCount')}</h4>
                        <div class="chart-container">
                            <canvas id="items-count-chart"></canvas>
                        </div>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">{tAnalytics('charts.trend')}</h3>
                <div class="card analytics-chart-card">
                    <div class="chart-container">
                        <canvas id="achievements-trend-chart"></canvas>
                    </div>
                </div>
            </div>

            <div class="analytics-group">
                <h3 class="analytics-group-title">{tAnalytics('charts.recommendations')}</h3>
                <div id="analytics-recommendations" class="recommendations-grid">
                    {#each recommendations as rec (rec.id)}
                    <article class="card card--task recommendation-card">
                        <div class="card__badge-row">
                            <span class="card__badge card__badge--group">{rec.groupName}</span>
                            {#if rec.reason}
                            <span class="card__badge card__badge--type">{rec.reason}</span>
                            {/if}
                        </div>
                        <div class="card__header">
                            <h4 class="card__title recommendation-card__title">{rec.title}</h4>
                            {#if rec.coins != null && rec.coins > 0}
                            <div class="card__coins recommendation-card__coins">
                                <span class="gamified-icon icon-coin" aria-hidden="true"></span>
                                <span>{rec.coins}</span>
                            </div>
                            {:else}
                            <div class="recommendation-card__icon-pill" aria-hidden="true">{rec.icon}</div>
                            {/if}
                        </div>
                        <p class="card__comment recommendation-card__description">{rec.description}</p>
                    </article>
                    {/each}
                </div>
            </div>
        </div>
        {/if}
    </div>
</section>
