<script lang="ts">
    import { resolve } from '$app/paths';
    import type { AppSection } from '$lib/app/routes';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { loadAnalyticsData } from '$lib/services/api';
    import { appStore } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { onDestroy, onMount, tick } from 'svelte';
    import {
        buildAnalyticsViewModel,
        type AnalyticsDailyQuest,
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
    let dailyQuests: AnalyticsDailyQuest[] = [];
    let recommendations: AnalyticsRecommendationCard[] = [];
    let detailsExpanded = false;
    let activeView: AnalyticsViewModel | null = null;
    let charts: Record<string, ChartInstance | null> = {};
    let chartRetryHandle: ReturnType<typeof setTimeout> | null = null;
    let loadRequestVersion = 0;

    $: hasTaskCharts = activeView != null && (activeView.taskCoins.length > 0 || activeView.taskCount.length > 0);
    $: hasItemCharts = activeView != null && (activeView.itemCoins.length > 0 || activeView.itemCount.length > 0);
    $: hasTrendChart = activeView != null && activeView.trend.length > 0;

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
            formatNumber(value: number) {
                return $i18n.formatNumber(value);
            },
            t(key, variables) {
                return tAnalytics(`model.${key}`, variables);
            },
        };
    }

    function resetAnalyticsState() {
        statsEarned = 0;
        statsSpent = 0;
        statsNet = 0;
        levelValue = tAnalytics('progress.levelValue', { level: 1 });
        levelBar = 0;
        levelNote = tAnalytics('progress.levelNote', { remaining: 120 });
        weekEarned = 0;
        weekBar = 0;
        weekNote = tAnalytics('model.noActivityWeek');
        streakValue = 0;
        streakBar = 0;
        streakNote = tAnalytics('model.streakToday');
        dailyQuests = [];
        recommendations = [];
        activeView = null;
        destroyCharts();
    }

    function clearChartRetry() {
        if (chartRetryHandle != null) {
            clearTimeout(chartRetryHandle);
            chartRetryHandle = null;
        }
    }

    function destroyCharts() {
        clearChartRetry();
        Object.values(charts).forEach((chart) => chart?.destroy());
        charts = {};
    }

    function questIcon(id: string): string {
        switch (id) {
            case 'complete-tasks':
                return '✅';
            case 'earn-coins':
                return '🪙';
            case 'keep-streak':
                return '🔥';
            case 'reward-target':
                return '🎁';
            case 'next-task':
                return '⚡';
            default:
                return '✨';
        }
    }

    function questStatusLabel(status: AnalyticsDailyQuest['status']): string {
        switch (status) {
            case 'completed':
                return tAnalytics('quests.status.completed');
            case 'in-progress':
                return tAnalytics('quests.status.inProgress');
            case 'ready':
                return tAnalytics('quests.status.ready');
            default:
                return tAnalytics('quests.status.notStarted');
        }
    }

    async function handleQuestAction(target: AnalyticsDailyQuest['actionTarget']) {
        if (target === 'details') {
            detailsExpanded = true;
            await scheduleChartRender();
            await tick();
            document.querySelector<HTMLElement>('.analytics-details')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
            return;
        }

        const section: AppSection = target === 'shop' ? 'shop' : 'tasks';
        location.assign($i18n.href(resolve('/app/[section]', { section })));
    }

    async function toggleDetails() {
        detailsExpanded = !detailsExpanded;

        if (detailsExpanded) {
            await scheduleChartRender();
            return;
        }

        destroyCharts();
    }

    async function scheduleChartRender() {
        const view = activeView;
        clearChartRetry();

        if (!detailsExpanded || view == null) {
            return;
        }

        await tick();

        let retries = 0;
        const tryRender = () => {
            if (!detailsExpanded || activeView !== view) {
                return;
            }

            if (ChartCtor()) {
                renderCharts(view);
                chartRetryHandle = null;
                return;
            }

            if (retries >= 12) {
                chartRetryHandle = null;
                return;
            }

            retries += 1;
            chartRetryHandle = setTimeout(tryRender, 250);
        };

        tryRender();
    }

    async function loadAndRender() {
        const requestVersion = ++loadRequestVersion;

        if (isAdmin && !hasChildren) {
            resetAnalyticsState();
            return;
        }

        const childId = $appStore.currentChildId;
        if (isAdmin && childId == null) {
            resetAnalyticsState();
            return;
        }

        const data = await loadAnalyticsData(childId, timeframe);
        if (requestVersion !== loadRequestVersion) {
            return;
        }
        if (!data) {
            resetAnalyticsState();
            return;
        }

        const view = buildAnalyticsViewModel(data, {
            currentBalance: $appStore.balance,
            isAdmin,
            shopItems: $appStore.shopItems,
            tasks: $appStore.tasks,
            i18n: createViewModelI18n(),
        });

        activeView = view;
        statsEarned = view.earned;
        statsSpent = view.spent;
        statsNet = view.net;

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
        streakBar = Math.min(100, Math.round((streakValue / 3) * 100));
        streakNote = view.streakNote;

        dailyQuests = view.dailyQuests;
        recommendations = view.recommendations;

        if (detailsExpanded) {
            await scheduleChartRender();
        } else {
            destroyCharts();
        }
    }

    function renderCharts(view: AnalyticsViewModel) {
        const C = ChartCtor();
        if (!C || !detailsExpanded) {
            return;
        }

        destroyCharts();

        const chartTextColor = 'rgba(66, 74, 92, 0.86)';
        const chartGridColor = 'rgba(116, 134, 170, 0.16)';
        const makeBar = (id: string, labels: string[], values: number[], color: string) => {
            const canvas = document.getElementById(id) as HTMLCanvasElement | null;
            if (!canvas || labels.length === 0 || values.length === 0) {
                return;
            }

            const ctx = canvas.getContext('2d');
            if (!ctx) {
                return;
            }

            charts[id] = new C(ctx, {
                type: 'bar',
                data: { labels, datasets: [{ data: values, backgroundColor: color, borderRadius: 4 }] },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { ticks: { color: chartTextColor, font: { size: 10 } }, grid: { display: false } },
                        y: { ticks: { color: chartTextColor, font: { size: 10 } }, grid: { color: chartGridColor } },
                    },
                },
            });
        };

        makeBar('tasks-coins-chart', view.taskCoins.map((datum) => datum.label), view.taskCoins.map((datum) => datum.value), 'rgba(123,197,104,0.8)');
        makeBar('tasks-count-chart', view.taskCount.map((datum) => datum.label), view.taskCount.map((datum) => datum.value), 'rgba(123,197,104,0.5)');
        makeBar('items-coins-chart', view.itemCoins.map((datum) => datum.label), view.itemCoins.map((datum) => datum.value), 'rgba(255,174,66,0.8)');
        makeBar('items-count-chart', view.itemCount.map((datum) => datum.label), view.itemCount.map((datum) => datum.value), 'rgba(255,174,66,0.5)');

        if (view.trend.length === 0) {
            return;
        }

        const trendCanvas = document.getElementById('achievements-trend-chart') as HTMLCanvasElement | null;
        if (!trendCanvas) {
            return;
        }

        const ctx = trendCanvas.getContext('2d');
        if (!ctx) {
            return;
        }

        charts['achievements-trend-chart'] = new C(ctx, {
            type: 'line',
            data: {
                labels: view.trend.map((datum) => datum.label),
                datasets: [
                    {
                        label: tAnalytics('charts.datasetEarned'),
                        data: view.trend.map((datum) => datum.earned),
                        borderColor: 'rgba(69,155,88,1)',
                        backgroundColor: 'rgba(69,155,88,0.16)',
                        tension: 0.28,
                        fill: false,
                    },
                    {
                        label: tAnalytics('charts.datasetSpent'),
                        data: view.trend.map((datum) => datum.spent),
                        borderColor: 'rgba(208,115,35,1)',
                        backgroundColor: 'rgba(208,115,35,0.16)',
                        tension: 0.28,
                        fill: false,
                    },
                ],
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: true,
                        position: 'bottom',
                        labels: {
                            color: chartTextColor,
                            boxWidth: 10,
                            boxHeight: 10,
                            padding: 12,
                            font: { size: 12, weight: 700 },
                        },
                    },
                },
                scales: {
                    x: { ticks: { color: chartTextColor, font: { size: 10 } }, grid: { display: false } },
                    y: { ticks: { color: chartTextColor, font: { size: 10 } }, grid: { color: chartGridColor } },
                },
            },
        });
    }

    onMount(() => {
        void loadAndRender();
    });

    onDestroy(() => {
        destroyCharts();
    });

    $: void ($appStore.currentChildId, $appStore.balance, $appStore.tasks, $appStore.shopItems, $i18n.locale, timeframe, loadAndRender());
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
        <div class="analytics-quest-shell">
            <section class="analytics-quest-panel" aria-labelledby="analytics-quests-title">
                <div class="analytics-quest-panel__header">
                    <div>
                        <p class="analytics-quest-panel__eyebrow">{tAnalytics('quests.eyebrow')}</p>
                        <h3 class="analytics-quest-panel__title" id="analytics-quests-title">{tAnalytics('quests.title')}</h3>
                    </div>
                    <p class="analytics-quest-panel__hint">{tAnalytics('quests.hint')}</p>
                </div>

                <div class="analytics-quest-list" id="daily-quest-list" aria-label={tAnalytics('quests.ariaLabel')}>
                    {#each dailyQuests as quest (quest.id)}
                    <article class="analytics-quest-card" data-quest-id={quest.id}>
                        <div class="analytics-quest-card__icon" aria-hidden="true">{questIcon(quest.id)}</div>

                        <div class="analytics-quest-card__body">
                            <div class="analytics-quest-card__top">
                                <div class="analytics-quest-card__copy">
                                    <div class="analytics-quest-card__title-row">
                                        <h4 class="analytics-quest-card__title">{quest.title}</h4>
                                        <span class={`analytics-quest-card__status analytics-quest-card__status--${quest.status}`}>{questStatusLabel(quest.status)}</span>
                                    </div>
                                    <p class="analytics-quest-card__description">{quest.description}</p>
                                </div>
                                <span class="analytics-quest-card__reward">{quest.rewardLabel}</span>
                            </div>

                            <div class="analytics-quest-card__progress-row">
                                <div
                                    class="analytics-quest-card__track progress-track"
                                    role="progressbar"
                                    aria-valuemin={0}
                                    aria-valuemax={quest.target}
                                    aria-valuenow={Math.min(quest.current, quest.target)}
                                    aria-label={tAnalytics('quests.progressLabel', {
                                        title: quest.title,
                                        current: $i18n.formatNumber(quest.current),
                                        target: $i18n.formatNumber(quest.target),
                                    })}
                                >
                                    <span class="analytics-quest-card__fill progress-fill" style={`--progress: ${quest.percent};`}></span>
                                </div>
                                <span class="analytics-quest-card__metric">
                                    {tAnalytics('quests.progressValue', {
                                        current: $i18n.formatNumber(quest.current),
                                        target: $i18n.formatNumber(quest.target),
                                    })}
                                </span>
                            </div>
                        </div>

                        <button
                            class="btn btn--secondary analytics-quest-card__action"
                            type="button"
                            data-quest-action-target={quest.actionTarget}
                            on:click={() => void handleQuestAction(quest.actionTarget)}
                        >
                            {quest.actionLabel}
                        </button>
                    </article>
                    {/each}
                </div>
            </section>

            <div class="analytics-summary-strip" aria-label={tAnalytics('progress.ariaLabel')}>
                <article class="analytics-summary-strip__item">
                    <span class="analytics-summary-strip__label">{tAnalytics('summary.earned')}</span>
                    <strong class="analytics-summary-strip__value analytics-summary-strip__value--earn" id="stats-earned">{statsEarned}</strong>
                </article>

                <article class="analytics-summary-strip__item">
                    <span class="analytics-summary-strip__label">{tAnalytics('summary.spent')}</span>
                    <strong class="analytics-summary-strip__value analytics-summary-strip__value--spend" id="stats-spent">{statsSpent}</strong>
                </article>

                <article class="analytics-summary-strip__item">
                    <span class="analytics-summary-strip__label">{tAnalytics('summary.balance')}</span>
                    <strong class="analytics-summary-strip__value" id="stats-net">{statsNet}</strong>
                </article>

                <article class="analytics-summary-strip__item analytics-summary-strip__item--progress">
                    <div class="analytics-summary-strip__header">
                        <span class="analytics-summary-strip__label">⭐ {tAnalytics('progress.level')}</span>
                        <strong class="analytics-summary-strip__value" id="progress-level-value">{levelValue}</strong>
                    </div>
                    <div class="progress-track analytics-summary-strip__track">
                        <span class="progress-fill analytics-summary-strip__fill" id="progress-level-bar" style={`--progress: ${levelBar};`}></span>
                    </div>
                    <p class="analytics-summary-strip__note" id="progress-level-note">{levelNote}</p>
                </article>

                <article class="analytics-summary-strip__item analytics-summary-strip__item--progress">
                    <div class="analytics-summary-strip__header">
                        <span class="analytics-summary-strip__label">📅 {tAnalytics('progress.week')}</span>
                        <strong class="analytics-summary-strip__value" id="progress-week-earned-value">{weekEarned}</strong>
                    </div>
                    <div class="progress-track analytics-summary-strip__track">
                        <span class="progress-fill analytics-summary-strip__fill" id="progress-week-earned-bar" style={`--progress: ${weekBar};`}></span>
                    </div>
                    <p class="analytics-summary-strip__note" id="progress-week-earned-goal">{weekNote}</p>
                </article>

                <article class="analytics-summary-strip__item analytics-summary-strip__item--progress">
                    <div class="analytics-summary-strip__header">
                        <span class="analytics-summary-strip__label">🔥 {tAnalytics('progress.streak')}</span>
                        <strong class="analytics-summary-strip__value" id="progress-streak-value">{streakValue}</strong>
                    </div>
                    <div class="progress-track analytics-summary-strip__track">
                        <span class="progress-fill analytics-summary-strip__fill" id="progress-streak-bar" style={`--progress: ${streakBar};`}></span>
                    </div>
                    <p class="analytics-summary-strip__note" id="progress-streak-note">{streakNote}</p>
                </article>
            </div>
        </div>

        <section class="analytics-details" aria-labelledby="analytics-details-heading">
            <div class="analytics-details__head">
                <div>
                    <h3 class="analytics-details__title" id="analytics-details-heading">{tAnalytics('details.title')}</h3>
                    <p class="analytics-details__hint">{tAnalytics('details.hint')}</p>
                </div>
                <button
                    class="analytics-details__toggle"
                    id="analytics-details-toggle"
                    type="button"
                    aria-expanded={detailsExpanded}
                    aria-controls="analytics-details-panel"
                    on:click={() => void toggleDetails()}
                >
                    {detailsExpanded ? tAnalytics('details.hide') : tAnalytics('details.show')}
                </button>
            </div>

            {#if detailsExpanded}
            <div class="analytics-details__panel" id="analytics-details-panel" aria-label={tAnalytics('details.panelLabel')}>
                <div class="analytics-sections">
                    <div class="analytics-group">
                        <h3 class="analytics-group-title">{tAnalytics('charts.tasks')} <span id="tasks-total-coins" class="group-total-badge">{statsEarned > 0 ? tAnalytics('charts.totalBadge', { value: statsEarned }) : ''}</span></h3>
                        {#if hasTaskCharts}
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
                        {:else}
                        <p class="analytics-empty-detail">{tAnalytics('details.emptyCharts')}</p>
                        {/if}
                    </div>

                    <div class="analytics-group">
                        <h3 class="analytics-group-title">{tAnalytics('charts.items')} <span id="items-total-coins" class="group-total-badge">{statsSpent > 0 ? tAnalytics('charts.totalBadge', { value: statsSpent }) : ''}</span></h3>
                        {#if hasItemCharts}
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
                        {:else}
                        <p class="analytics-empty-detail">{tAnalytics('details.emptyCharts')}</p>
                        {/if}
                    </div>

                    <div class="analytics-group">
                        <h3 class="analytics-group-title">{tAnalytics('charts.trend')}</h3>
                        {#if hasTrendChart}
                        <div class="card analytics-chart-card">
                            <div class="chart-container">
                                <canvas id="achievements-trend-chart"></canvas>
                            </div>
                        </div>
                        {:else}
                        <p class="analytics-empty-detail">{tAnalytics('details.emptyCharts')}</p>
                        {/if}
                    </div>

                    <div class="analytics-group">
                        <h3 class="analytics-group-title">{tAnalytics('charts.recommendations')}</h3>
                        {#if recommendations.length > 0}
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
                        {:else}
                        <p class="analytics-empty-detail">{tAnalytics('details.emptyRecommendations')}</p>
                        {/if}
                    </div>
                </div>
            </div>
            {/if}
        </section>
        {/if}
    </div>
</section>

<style>
    #analytics-section {
        --analytics-border: rgba(255, 255, 255, 0.08);
        --analytics-surface: rgba(255, 255, 255, 0.04);
        --analytics-surface-strong: rgba(255, 255, 255, 0.07);
        --analytics-shadow: 0 18px 40px -32px rgba(11, 25, 48, 0.55);
    }

    #analytics-section .section-header {
        margin-bottom: 1rem;
    }

    #analytics-section .analytics-filters {
        margin-bottom: 0;
    }

    #analytics-section .analytics-quest-shell {
        display: grid;
        gap: 1rem;
        margin-bottom: 1rem;
    }

    #analytics-section .analytics-quest-panel,
    #analytics-section .analytics-summary-strip,
    #analytics-section .analytics-details {
        background: var(--analytics-surface);
        border: 1px solid var(--analytics-border);
        border-radius: 1rem;
        box-shadow: var(--analytics-shadow);
        backdrop-filter: blur(14px);
    }

    #analytics-section .analytics-quest-panel {
        padding: 1rem;
    }

    #analytics-section .analytics-quest-panel__header {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        margin-bottom: 0.9rem;
        align-items: end;
    }

    #analytics-section .analytics-quest-panel__eyebrow {
        margin: 0 0 0.25rem;
        font-size: 0.75rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--color-warning);
    }

    #analytics-section .analytics-quest-panel__title,
    #analytics-section .analytics-details__title {
        margin: 0;
        font-size: 1.2rem;
        color: var(--color-text-high-contrast);
    }

    #analytics-section .analytics-quest-panel__hint,
    #analytics-section .analytics-details__hint,
    #analytics-section .analytics-empty-detail {
        margin: 0;
        color: var(--color-text-muted);
        line-height: 1.4;
    }

    #analytics-section .analytics-quest-list {
        display: grid;
        gap: 0.75rem;
    }

    #analytics-section .analytics-quest-card {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        gap: 0.85rem;
        align-items: center;
        padding: 0.95rem 1rem;
        border-radius: 0.95rem;
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.04));
        border: 1px solid rgba(255, 255, 255, 0.1);
    }

    #analytics-section .analytics-quest-card__icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 2.75rem;
        height: 2.75rem;
        border-radius: 0.9rem;
        background: rgba(255, 255, 255, 0.12);
        font-size: 1.2rem;
        box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.22);
    }

    #analytics-section .analytics-quest-card__body {
        min-width: 0;
        display: grid;
        gap: 0.7rem;
    }

    #analytics-section .analytics-quest-card__top {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        align-items: start;
    }

    #analytics-section .analytics-quest-card__copy {
        min-width: 0;
    }

    #analytics-section .analytics-quest-card__title-row {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        align-items: center;
        margin-bottom: 0.25rem;
    }

    #analytics-section .analytics-quest-card__title {
        margin: 0;
        font-size: 1rem;
        color: var(--color-text-high-contrast);
    }

    #analytics-section .analytics-quest-card__description {
        margin: 0;
        color: var(--color-text-muted);
        line-height: 1.35;
        font-size: 0.92rem;
    }

    #analytics-section .analytics-quest-card__status,
    #analytics-section .analytics-quest-card__reward {
        display: inline-flex;
        align-items: center;
        padding: 0.24rem 0.58rem;
        border-radius: 999px;
        font-size: 0.74rem;
        font-weight: 700;
        white-space: nowrap;
    }

    #analytics-section .analytics-quest-card__reward {
        background: rgba(255, 215, 0, 0.13);
        color: var(--color-warning);
        border: 1px solid rgba(255, 215, 0, 0.18);
    }

    #analytics-section .analytics-quest-card__status--not-started {
        background: rgba(148, 163, 184, 0.16);
        color: var(--color-text-muted);
    }

    #analytics-section .analytics-quest-card__status--in-progress {
        background: rgba(92, 199, 243, 0.14);
        color: #74d1f5;
    }

    #analytics-section .analytics-quest-card__status--ready {
        background: rgba(141, 223, 183, 0.16);
        color: #8ddfb7;
    }

    #analytics-section .analytics-quest-card__status--completed {
        background: rgba(251, 191, 36, 0.16);
        color: #ffd86b;
    }

    #analytics-section .progress-track {
        position: relative;
        width: 100%;
        height: 0.5rem;
        border-radius: 999px;
        background: rgba(148, 163, 184, 0.22);
        overflow: hidden;
    }

    #analytics-section .progress-fill {
        display: block;
        height: 100%;
        width: calc(var(--progress, 0) * 1%);
        background: linear-gradient(135deg, var(--color-success), #facc15);
        border-radius: inherit;
        transition: width 0.25s ease;
    }

    #analytics-section .analytics-quest-card__progress-row {
        display: flex;
        gap: 0.75rem;
        align-items: center;
    }

    #analytics-section .analytics-quest-card__track {
        flex: 1;
        min-width: 0;
    }

    #analytics-section .analytics-quest-card__metric {
        flex: none;
        font-size: 0.8rem;
        font-weight: 700;
        color: var(--color-text-soft);
        min-width: 4.5rem;
        text-align: right;
    }

    #analytics-section .analytics-quest-card__action {
        min-width: 8.5rem;
        justify-self: end;
        white-space: nowrap;
    }

    #analytics-section .analytics-summary-strip {
        padding: 0.95rem;
        display: grid;
        gap: 0.65rem;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        align-content: start;
    }

    #analytics-section .analytics-summary-strip__item {
        min-width: 0;
        padding: 0.75rem 0.8rem;
        border-radius: 0.85rem;
        background: var(--analytics-surface-strong);
        border: 1px solid rgba(255, 255, 255, 0.08);
        display: grid;
        gap: 0.35rem;
    }

    #analytics-section .analytics-summary-strip__item--progress {
        gap: 0.45rem;
    }

    #analytics-section .analytics-summary-strip__header {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
        align-items: baseline;
    }

    #analytics-section .analytics-summary-strip__label {
        font-size: 0.76rem;
        color: var(--color-text-muted);
        line-height: 1.2;
    }

    #analytics-section .analytics-summary-strip__value {
        font-size: 1rem;
        font-weight: 800;
        color: var(--color-text-high-contrast);
        line-height: 1.1;
    }

    #analytics-section .analytics-summary-strip__value--earn {
        color: var(--color-success);
    }

    #analytics-section .analytics-summary-strip__value--spend {
        color: var(--color-danger);
    }

    #analytics-section .analytics-summary-strip__note {
        margin: 0;
        font-size: 0.74rem;
        line-height: 1.3;
        color: var(--color-text-muted);
    }

    #analytics-section .analytics-details {
        padding: 1rem;
    }

    #analytics-section .analytics-details__head {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: center;
    }

    #analytics-section .analytics-details__toggle {
        border: 1px solid rgba(255, 255, 255, 0.12);
        background: rgba(255, 255, 255, 0.08);
        color: var(--color-text-high-contrast);
        border-radius: 999px;
        padding: 0.6rem 0.95rem;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
    }

    #analytics-section .analytics-details__panel {
        margin-top: 1rem;
    }

    #analytics-section .analytics-sections {
        display: grid;
        gap: 1rem;
    }

    #analytics-section .analytics-group {
        margin: 0;
    }

    #analytics-section .analytics-group-title {
        color: var(--color-warning);
        margin: 0 0 0.8rem;
        font-size: 1rem;
        border-left: 4px solid var(--color-warning);
        padding-left: 0.75rem;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.75rem;
    }

    #analytics-section .group-total-badge {
        font-size: 0.8rem;
        background: rgba(255, 215, 0, 0.15);
        color: var(--color-warning);
        padding: 0.2rem 0.6rem;
        border-radius: 0.5rem;
        font-weight: 700;
    }

    #analytics-section .analytics-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
        gap: 0.85rem;
    }

    #analytics-section .analytics-chart-card {
        background: rgba(255, 255, 255, 0.03);
        padding: 0.9rem;
        min-height: 260px;
        display: flex;
        flex-direction: column;
    }

    #analytics-section .card__subtitle {
        font-size: 0.82rem;
        color: var(--color-text-muted);
        margin: 0 0 0.45rem;
        font-weight: 600;
    }

    #analytics-section .chart-container {
        flex: 1;
        position: relative;
        min-height: 210px;
    }

    #analytics-section .analytics-empty-detail {
        padding: 0.9rem 1rem;
        border-radius: 0.85rem;
        background: rgba(255, 255, 255, 0.03);
        border: 1px dashed rgba(255, 255, 255, 0.1);
    }

    #analytics-section .recommendations-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: 0.85rem;
    }

    @media (min-width: 960px) {
        #analytics-section .analytics-quest-shell {
            grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.9fr);
            align-items: start;
        }

        #analytics-section .analytics-summary-strip {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
    }

    @media (max-width: 760px) {
        /* Quest panel header: hint below title on narrow */
        #analytics-section .analytics-quest-panel__header {
            flex-direction: column;
            align-items: stretch;
            gap: 0.35rem;
        }

        /* Details head: keep row but allow wrap */
        #analytics-section .analytics-details__head {
            flex-wrap: wrap;
        }

        /* Quest card: icon + body on same row, action button below */
        #analytics-section .analytics-quest-card {
            grid-template-columns: 2.75rem 1fr;
            grid-template-rows: auto auto;
            grid-template-areas:
                'icon body'
                'cta  cta';
            gap: 0.55rem 0.75rem;
            padding: 0.8rem;
        }

        #analytics-section .analytics-quest-card__icon {
            grid-area: icon;
            align-self: start;
            width: 2.4rem;
            height: 2.4rem;
            font-size: 1.05rem;
        }

        #analytics-section .analytics-quest-card__body {
            grid-area: body;
        }

        #analytics-section .analytics-quest-card__action {
            grid-area: cta;
            width: 100%;
            justify-self: stretch;
            min-width: 0;
        }

        #analytics-section .analytics-quest-card__top {
            flex-direction: row;
            flex-wrap: wrap;
            align-items: start;
        }

        #analytics-section .analytics-quest-card__metric {
            min-width: 0;
            text-align: left;
        }

        /* Summary strip: 3-col for stat items, progress spans full width */
        #analytics-section .analytics-summary-strip {
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }

        #analytics-section .analytics-summary-strip__item--progress {
            grid-column: 1 / -1;
        }
    }

    @media (max-width: 480px) {
        #analytics-section .section-header {
            gap: 0.45rem;
            margin-bottom: 0.7rem;
        }

        #analytics-section .section-title {
            font-size: 0.95rem;
        }

        #analytics-section .analytics-filters {
            justify-content: stretch;
        }

        #analytics-section .analytics-filters .tab-group {
            width: 100%;
            padding: 0.16rem;
            border-radius: 0.6rem;
        }

        #analytics-section .analytics-filters .tab-btn {
            flex: 1;
            padding: 0.34rem 0.38rem;
            font-size: 0.72rem;
            border-radius: 0.48rem;
        }

        #analytics-section .analytics-quest-panel,
        #analytics-section .analytics-summary-strip,
        #analytics-section .analytics-details {
            padding: 0.72rem;
            border-radius: 0.82rem;
        }

        #analytics-section .analytics-quest-panel__title,
        #analytics-section .analytics-details__title {
            font-size: 1rem;
        }

        #analytics-section .analytics-quest-panel__hint,
        #analytics-section .analytics-details__hint,
        #analytics-section .analytics-quest-card__description,
        #analytics-section .analytics-empty-detail {
            font-size: 0.8rem;
        }

        #analytics-section .analytics-quest-card {
            padding: 0.72rem;
            gap: 0.65rem;
        }

        #analytics-section .analytics-quest-card__title {
            font-size: 0.92rem;
        }

        #analytics-section .analytics-quest-card__status,
        #analytics-section .analytics-quest-card__reward {
            font-size: 0.68rem;
            padding: 0.18rem 0.48rem;
        }

        #analytics-section .analytics-quest-card__metric,
        #analytics-section .analytics-summary-strip__label,
        #analytics-section .analytics-summary-strip__note,
        #analytics-section .card__subtitle,
        #analytics-section .group-total-badge {
            font-size: 0.68rem;
        }

        #analytics-section .analytics-summary-strip__value {
            font-size: 0.88rem;
        }

        #analytics-section .analytics-group-title {
            margin-bottom: 0.5rem;
            padding-left: 0.5rem;
            border-left-width: 3px;
            font-size: 0.86rem;
            line-height: 1.15;
        }

        #analytics-section .analytics-grid,
        #analytics-section .recommendations-grid,
        #analytics-section .analytics-sections {
            gap: 0.55rem;
        }

        #analytics-section .analytics-chart-card {
            min-height: 188px;
            padding: 0.58rem;
            border-radius: 0.7rem;
        }

        #analytics-section .chart-container {
            min-height: 142px;
        }

        #analytics-section .analytics-summary-strip {
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }

        #analytics-section .analytics-summary-strip__item--progress {
            grid-column: 1 / -1;
        }

        #analytics-section .analytics-details__toggle {
            white-space: nowrap;
            flex-shrink: 0;
        }
    }
</style>
