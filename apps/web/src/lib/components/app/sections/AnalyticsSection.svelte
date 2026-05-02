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
        type AchievementBadge,
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
    let achievements: AchievementBadge[] = [];
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
        achievements = [];
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

    function questIcon(id: string, variant: string): string {
        if (variant === 'task') return '⚡';
        if (variant === 'reward') return '🎁';
        return '🔥';
    }


    async function handleQuestAction(target: AnalyticsDailyQuest['actionTarget']) {
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
        achievements = view.achievements;
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
        <div class="analytics-next-shell">
            <section class="analytics-next-panel" aria-labelledby="analytics-next-title">
                <div class="analytics-next-panel__header">
                    <div class="analytics-next-panel__text">
                        <p class="analytics-next-panel__eyebrow">{tAnalytics('quests.eyebrow')}</p>
                        <h3 class="analytics-next-panel__title" id="analytics-next-title">{tAnalytics('quests.title')}</h3>
                    </div>
                    <p class="analytics-next-panel__hint">{tAnalytics('quests.hint')}</p>
                </div>

                <div class="analytics-next-cards" aria-label={tAnalytics('quests.ariaLabel')}>
                    {#each dailyQuests as quest (quest.id)}
                    <article
                        class="analytics-next-card"
                        class:analytics-next-card--task={quest.variant === 'task'}
                        class:analytics-next-card--reward={quest.variant === 'reward'}
                        class:analytics-next-card--streak={quest.variant === 'streak'}
                        data-quest-id={quest.id}
                    >
                        <div class="analytics-next-card__icon" aria-hidden="true">{questIcon(quest.id, quest.variant)}</div>

                        <div class="analytics-next-card__body">
                            <div class="analytics-next-card__header-row">
                                <h4 class="analytics-next-card__title">{quest.title}</h4>
                                {#if quest.rewardLabel}
                                <span class="analytics-next-card__badge">{quest.rewardLabel}</span>
                                {/if}
                            </div>
                            {#if quest.subtitle}
                            <p class="analytics-next-card__subtitle">{quest.subtitle}</p>
                            {/if}
                            <p class="analytics-next-card__desc">{quest.description}</p>

                            {#if quest.target > 0 && quest.variant !== 'task'}
                            <div
                                class="analytics-next-card__track progress-track"
                                role="progressbar"
                                aria-valuemin={0}
                                aria-valuemax={quest.target}
                                aria-valuenow={Math.min(quest.current, quest.target)}
                                aria-label="{quest.title}: {quest.current} / {quest.target}"
                            >
                                <span class="analytics-next-card__fill progress-fill" style={`--progress: ${quest.percent};`}></span>
                            </div>
                            {/if}
                        </div>

                        <button
                            class="analytics-next-card__action"
                            type="button"
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

                <article class="analytics-summary-strip__item analytics-summary-strip__item--achievements">
                    <div class="ach-row" aria-label={tAnalytics('achievements.ariaLabel')}>
                        {#each achievements as badge (badge.id)}
                        <div
                            class="ach-badge"
                            class:ach-badge--earned={badge.earned}
                            class:ach-badge--over={badge.earned && badge.percent >= 100}
                            title={badge.earnedBadge ? `${badge.description} — ${badge.earnedBadge} ✅` : badge.description}
                        >
                            <div class="ach-badge__top">
                                <div class="ach-badge__icon" aria-hidden="true">{badge.icon}</div>
                                <div class="ach-badge__header">
                                    <span class="ach-badge__name">{badge.name}</span>
                                    <span class="ach-badge__tier" aria-hidden="true">{badge.tierIcon}</span>
                                    {#if badge.earned}
                                    <span class="ach-badge__check">✅</span>
                                    {/if}
                                </div>
                            </div>
                            <div
                                class="ach-badge__track progress-track"
                                class:ach-badge__track--over={badge.earned && badge.percent >= 100}
                                role="progressbar"
                                aria-valuemin={0}
                                aria-valuemax={badge.target}
                                aria-valuenow={Math.min(badge.current, badge.target)}
                                aria-label={badge.description}
                            >
                                <span class="ach-badge__fill progress-fill" style={`--progress: ${Math.min(badge.percent, 100)};`}></span>
                            </div>
                            <span class="ach-badge__metric">
                                {badge.current}/{badge.target}
                            </span>
                        </div>
                        {/each}
                    </div>
                </article>
            </div>
        </div>

        <section class="analytics-details" aria-labelledby="analytics-details-heading">
            <div class="analytics-details__head">
                <div class="analytics-details__title-group">
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

    #analytics-section .analytics-next-shell {
        display: grid;
        gap: 1rem;
        margin-bottom: 1rem;
    }

    #analytics-section .analytics-next-panel,
    #analytics-section .analytics-summary-strip,
    #analytics-section .analytics-details {
        background: var(--analytics-surface);
        border: 1px solid var(--analytics-border);
        border-radius: 1rem;
        box-shadow: var(--analytics-shadow);
        backdrop-filter: blur(14px);
    }

    #analytics-section .analytics-next-panel {
        padding: 1rem;
    }

    #analytics-section .analytics-next-panel__header {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        margin-bottom: 0.75rem;
        align-items: end;
    }

    #analytics-section .analytics-next-panel__eyebrow {
        margin: 0 0 0.2rem;
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: var(--color-warning);
    }

    #analytics-section .analytics-next-panel__title,
    #analytics-section .analytics-details__title {
        margin: 0;
        font-size: 1.15rem;
        color: var(--color-text-high-contrast);
    }

    #analytics-section .analytics-next-panel__hint,
    #analytics-section .analytics-details__hint,
    #analytics-section .analytics-empty-detail {
        margin: 0;
        color: var(--color-text-muted);
        line-height: 1.4;
        font-size: 0.8rem;
    }

    /* ── 3-Card Grid ─────────────────────────────────────────────────── */

    #analytics-section .analytics-next-cards {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 0.65rem;
    }

    #analytics-section .analytics-next-card {
        display: flex;
        flex-direction: column;
        gap: 0.6rem;
        padding: 0.85rem 0.9rem;
        border-radius: 0.9rem;
        background: linear-gradient(180deg, rgba(255, 255, 255, 0.07), rgba(255, 255, 255, 0.03));
        border: 1px solid rgba(255, 255, 255, 0.1);
        transition: border-color 0.2s, background 0.2s;
    }

    #analytics-section .analytics-next-card--task {
        border-left: 3px solid var(--color-success);
    }

    #analytics-section .analytics-next-card--reward {
        border-left: 3px solid var(--color-warning);
    }

    #analytics-section .analytics-next-card--streak {
        border-left: 3px solid #f97316;
    }

    #analytics-section .analytics-next-card__icon {
        font-size: 1.4rem;
        line-height: 1;
    }

    #analytics-section .analytics-next-card__body {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
    }

    #analytics-section .analytics-next-card__header-row {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 0.4rem;
    }

    #analytics-section .analytics-next-card__title {
        margin: 0;
        font-size: 0.9rem;
        font-weight: 700;
        color: var(--color-text-high-contrast);
        line-height: 1.25;
        overflow: hidden;
        text-overflow: ellipsis;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
    }

    #analytics-section .analytics-next-card__badge {
        flex-shrink: 0;
        font-size: 0.68rem;
        font-weight: 700;
        padding: 0.18rem 0.5rem;
        border-radius: 999px;
        white-space: nowrap;
        line-height: 1.35;
    }

    #analytics-section .analytics-next-card--task .analytics-next-card__badge {
        background: rgba(123, 197, 104, 0.16);
        color: var(--color-success);
    }

    #analytics-section .analytics-next-card--reward .analytics-next-card__badge {
        background: rgba(255, 215, 0, 0.14);
        color: var(--color-warning);
    }

    #analytics-section .analytics-next-card--streak .analytics-next-card__badge {
        background: rgba(249, 115, 22, 0.14);
        color: #fb923c;
    }

    #analytics-section .analytics-next-card__subtitle {
        margin: 0;
        font-size: 0.7rem;
        color: var(--color-text-muted);
        line-height: 1.2;
    }

    #analytics-section .analytics-next-card__desc {
        margin: 0;
        font-size: 0.78rem;
        color: var(--color-text-soft);
        line-height: 1.35;
        flex: 1;
    }

    #analytics-section .analytics-next-card__track {
        margin-top: 0.1rem;
    }

    #analytics-section .analytics-next-card__action {
        margin-top: auto;
        width: 100%;
        padding: 0.45rem 0.6rem;
        border: none;
        border-radius: 0.55rem;
        font: inherit;
        font-size: 0.78rem;
        font-weight: 700;
        cursor: pointer;
        color: #fff;
        transition: opacity 0.15s, transform 0.1s;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.3rem;
        min-height: 2.2rem;
    }

    #analytics-section .analytics-next-card--task .analytics-next-card__action {
        background: linear-gradient(135deg, var(--color-success), #4ade80);
    }

    #analytics-section .analytics-next-card--reward .analytics-next-card__action {
        background: linear-gradient(135deg, #eab308, #facc15);
        color: #422006;
    }

    #analytics-section .analytics-next-card--streak .analytics-next-card__action {
        background: linear-gradient(135deg, #f97316, #fb923c);
    }

    #analytics-section .analytics-next-card__action:hover {
        opacity: 0.9;
        transform: translateY(-1px);
    }

    #analytics-section .analytics-next-card__action:active {
        transform: translateY(0);
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

    #analytics-section .analytics-summary-strip__item--achievements {
        grid-column: 1 / -1;
        padding: 0;
        background: none;
        border: none;
        gap: 0;
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
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
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

    /* ── Achievement Badge Row ────────────────────────────────────────── */

    #analytics-section .ach-row {
        display: grid;
        grid-template-columns: repeat(5, minmax(0, 1fr));
        gap: 0.5rem;
    }

    #analytics-section .ach-badge {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
        padding: 0.55rem 0.6rem;
        border-radius: 0.7rem;
        background: var(--analytics-surface-strong);
        border: 1px solid rgba(255, 255, 255, 0.06);
        transition: border-color 0.25s, box-shadow 0.25s;
    }

    #analytics-section .ach-badge__top {
        display: flex;
        align-items: center;
        gap: 0.4rem;
    }

    #analytics-section .ach-badge__icon {
        font-size: 1rem;
        line-height: 1;
        flex-shrink: 0;
    }

    #analytics-section .ach-badge__header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.3rem;
        flex: 1;
        min-width: 0;
    }

    #analytics-section .ach-badge__name {
        font-size: 0.7rem;
        font-weight: 700;
        color: var(--color-text-soft);
        line-height: 1.2;
    }

    /* Earned state */
    #analytics-section .ach-badge--earned {
        border-color: rgba(255, 215, 0, 0.35);
        box-shadow: 0 0 12px -4px rgba(255, 215, 0, 0.18);
    }

    /* Over-fulfilled */
    #analytics-section .ach-badge--over {
        border-color: rgba(255, 215, 0, 0.5);
        box-shadow: 0 0 18px -4px rgba(255, 215, 0, 0.35);
        background: linear-gradient(180deg, rgba(255, 215, 0, 0.06), var(--analytics-surface-strong));
    }

    #analytics-section .ach-badge--earned .ach-badge__name {
        color: var(--color-warning);
    }

    #analytics-section .ach-badge__check {
        font-size: 0.65rem;
        flex-shrink: 0;
    }

    #analytics-section .ach-badge__track {
        position: relative;
        height: 0.35rem;
        background: rgba(148, 163, 184, 0.18);
    }

    #analytics-section .ach-badge__track--over {
        background: rgba(255, 215, 0, 0.12);
    }

    #analytics-section .ach-badge__fill {
        display: block;
        height: 100%;
        width: calc(var(--progress, 0) * 1%);
        max-width: 100%;
        background: linear-gradient(135deg, var(--color-success), #facc15);
        border-radius: inherit;
        transition: width 0.3s ease;
    }

    #analytics-section .ach-badge--earned .ach-badge__fill {
        background: linear-gradient(135deg, #facc15, #fbbf24);
        box-shadow: 0 0 6px -1px rgba(255, 215, 0, 0.4);
    }

    /* Tier medal — next to title in header */
    #analytics-section .ach-badge__tier {
        font-size: 0.9rem;
        line-height: 1;
        flex-shrink: 0;
        filter: grayscale(0.4);
        transition: filter 0.2s, transform 0.2s;
    }

    #analytics-section .ach-badge--earned .ach-badge__tier {
        filter: none;
        transform: scale(1.15);
    }

    #analytics-section .ach-badge__metric {
        font-size: 0.64rem;
        font-weight: 700;
        color: var(--color-text-muted);
        text-align: right;
    }

    #analytics-section .ach-badge--earned .ach-badge__metric {
        color: var(--color-warning);
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

    #analytics-section .analytics-details__title-group {
        min-width: 0;
        flex: 1;
        overflow: hidden;
    }

    #analytics-section .analytics-next-panel__text {
        min-width: 0;
        flex: 1;
        overflow: hidden;
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
        min-height: 2.75rem;
        display: inline-flex;
        align-items: center;
        flex-shrink: 0;
        white-space: nowrap;
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

    /* ── Responsive ──────────────────────────────────────────────────── */

    @media (max-width: 900px) {
        #analytics-section .analytics-next-cards {
            grid-template-columns: 1fr;
            gap: 0.5rem;
        }

        #analytics-section .analytics-next-card {
            flex-direction: row;
            flex-wrap: wrap;
            align-items: center;
            gap: 0.5rem 0.75rem;
            padding: 0.7rem 0.8rem;
        }

        #analytics-section .analytics-next-card__icon {
            font-size: 1.2rem;
            flex-shrink: 0;
        }

        #analytics-section .analytics-next-card__body {
            flex: 1;
            min-width: 0;
        }

        #analytics-section .analytics-next-card__action {
            width: auto;
            flex: 0 0 auto;
            padding: 0.4rem 0.85rem;
            font-size: 0.74rem;
            min-height: 2rem;
        }

        /* Achievement row: 3 + 2 on narrow screens */
        #analytics-section .ach-row {
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }
    }

    @media (max-width: 760px) {
        #analytics-section .analytics-next-panel__header {
            flex-direction: column;
            align-items: stretch;
            gap: 0.3rem;
        }

        #analytics-section .analytics-details__head {
            flex-wrap: wrap;
        }
    }

    @media (max-width: 480px) {
        #analytics-section .section-header {
            margin-bottom: 0.5rem;
        }

        #analytics-section .section-title {
            font-size: 0.9rem;
        }

        #analytics-section .analytics-filters {
            justify-content: stretch;
        }

        #analytics-section .analytics-filters .tab-group {
            width: 100%;
            padding: 0.14rem;
            border-radius: 0.55rem;
        }

        #analytics-section .analytics-filters .tab-btn {
            flex: 1;
            padding: 0.3rem 0.35rem;
            font-size: 0.7rem;
            border-radius: 0.45rem;
        }

        #analytics-section .analytics-next-panel,
        #analytics-section .analytics-summary-strip,
        #analytics-section .analytics-details {
            padding: 0.65rem;
            border-radius: 0.75rem;
        }

        #analytics-section .analytics-next-panel__title,
        #analytics-section .analytics-details__title {
            font-size: 0.95rem;
        }

        #analytics-section .analytics-next-panel__hint,
        #analytics-section .analytics-details__hint {
            font-size: 0.74rem;
        }

        #analytics-section .analytics-next-card {
            padding: 0.6rem 0.7rem;
            gap: 0.4rem 0.6rem;
        }

        #analytics-section .analytics-next-card__title {
            font-size: 0.82rem;
        }

        #analytics-section .analytics-next-card__desc {
            font-size: 0.72rem;
        }

        #analytics-section .analytics-next-card__badge {
            font-size: 0.64rem;
            padding: 0.14rem 0.4rem;
        }

        #analytics-section .analytics-next-card__action {
            font-size: 0.7rem;
            padding: 0.35rem 0.7rem;
            min-height: 1.85rem;
        }

        /* Achievement row: 2 per row on mobile */
        #analytics-section .ach-row {
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 0.35rem;
        }

        #analytics-section .ach-badge {
            padding: 0.4rem 0.45rem;
            gap: 0.25rem;
        }

        #analytics-section .ach-badge__icon {
            font-size: 0.85rem;
        }

        #analytics-section .ach-badge__name {
            font-size: 0.62rem;
        }

        #analytics-section .ach-badge__tier {
            font-size: 0.85rem;
        }

        #analytics-section .ach-badge__metric {
            font-size: 0.58rem;
        }

        #analytics-section .analytics-summary-strip {
            grid-template-columns: repeat(3, minmax(0, 1fr));
        }
    }
</style>
