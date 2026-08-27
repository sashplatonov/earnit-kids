<script lang="ts">
    import { onMount, tick } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { afterNavigate, goto } from '$app/navigation';
    import { navigating } from '$app/stores';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    import TelegramCoin from '$lib/components/telegram/TelegramCoin.svelte';
    import TelegramParentReturn from '$lib/components/telegram/TelegramParentReturn.svelte';
    import DashboardPeriodControl from '$lib/features/telegram/dashboard/DashboardPeriodControl.svelte';
    import DashboardTabNavigation from '$lib/features/telegram/dashboard/DashboardTabNavigation.svelte';
    import DashboardTooltip from '$lib/features/telegram/dashboard/DashboardTooltip.svelte';
    import type { PageData } from './$types';

    const i18n = useI18n();

    function t(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.dashboard.${key}` as MessageKey, variables);
    }

    type NumberMetrics = Record<string, number | undefined>;
    type Ranking = { category: string; count: number; percent: number; rank: number };
    type TaskPattern = { groupName: string; count: number; percent: number };
    type FunnelStage = { key: string; label: string; count: number; percentFromPrevious: number; percentFromInitial: number };
    type TrendPoint = { date: string; activeFamilies: number; coinsEarned: number; coinsSpent: number };
    type AnalyticsSectionData = {
        coins?: NumberMetrics;
        balances?: NumberMetrics;
        metrics?: NumberMetrics;
        taskMetrics?: NumberMetrics;
        parentBehaviorMetrics?: NumberMetrics;
        childBehaviorMetrics?: NumberMetrics;
        retentionMetrics?: NumberMetrics;
        rankings?: Ranking[];
        topPatterns?: TaskPattern[];
        stages?: FunnelStage[];
        points?: TrendPoint[];
    };


    let { data }: { data: PageData } = $props();
    let overview = $state(data.overview);
    let coinEconomy: AnalyticsSectionData | null = $state(data.coinEconomy);
    let taskEconomy: AnalyticsSectionData | null = $state(data.taskEconomy);
    let parentBehavior: AnalyticsSectionData | null = $state(data.parentBehavior);
    let childBehavior: AnalyticsSectionData | null = $state(data.childBehavior);
    let activationFunnel: AnalyticsSectionData | null = $state(data.activationFunnel);
    let retention: AnalyticsSectionData | null = $state(data.retention);
    let rewards: AnalyticsSectionData | null = $state(data.rewards);
    let trends = $state<AnalyticsSectionData | null>(data.trends ?? null);
    let dashboardStatus = $state(data.dashboardStatus ?? (overview == null ? 'unavailable' : 'available'));
    let trendsStatus: 'available' | 'unavailable' = $state(data.trendsStatus ?? (trends == null ? 'unavailable' : 'available'));
    let unavailableSections = data.unavailableSections ?? [];

    let loadedSections: string[] = [];
    let loadingSections: string[] = $state([]);


    // EXPLAIN: Tab definitions with semantic SVG icons (from the shared
    // EXPLAIN: TelegramIcon set) instead of emoji, so the tab bar reads clearly.
    const tabs = [
        { id: 'overview', label: t('tabs.overview'), icon: 'gauge' },
        { id: 'coins', label: t('tabs.coins'), icon: 'coin' },
        { id: 'rewards', label: t('tabs.rewards'), icon: 'gift' },
        { id: 'tasks', label: t('tabs.tasks'), icon: 'task' },
        { id: 'activity', label: t('tabs.activity'), icon: 'activity' },
    ] as const;

    type TabId = typeof tabs[number]['id'];

    let activeTab: TabId = $state('overview');

    const activitySubtabs = [
        { id: 'activation', label: t('activityTabs.activation') },
        { id: 'retention', label: t('activityTabs.retention') },
        { id: 'needs', label: t('activityTabs.needs') },
    ] as const;

    type ActivitySubtabId = typeof activitySubtabs[number]['id'];

    let activeActivitySubtab: ActivitySubtabId = $state('activation');
    let periodLoading = $state(false);

    const analyticsEndpoints: Record<string, string> = {
        coinEconomy: '/api/admin/analytics/coin-economy',
        tasks: '/api/admin/analytics/task-economy',
        parentBehavior: '/api/admin/analytics/parent-behavior',
        childBehavior: '/api/admin/analytics/child-behavior',
        activation: '/api/admin/analytics/activation-funnel',
        retention: '/api/admin/analytics/retention',
        rewards: '/api/admin/analytics/rewards',
        trends: '/api/admin/analytics/trends',
    };

    // Redirect non-admins to the Telegram Mini App home
    onMount(() => {
        if (!isAdmin) {
            // eslint-disable-next-line svelte/no-navigation-without-resolve
            goto('/telegram', { replaceState: true });
        }
    });

    afterNavigate(() => {
        overview = data.overview;
        coinEconomy = data.coinEconomy;
        taskEconomy = data.taskEconomy;
        parentBehavior = data.parentBehavior;
        childBehavior = data.childBehavior;
        activationFunnel = data.activationFunnel;
        retention = data.retention;
        rewards = data.rewards;
        trends = data.trends;
        dashboardStatus = data.dashboardStatus ?? (overview == null ? 'unavailable' : 'available');
        trendsStatus = data.trendsStatus ?? (trends == null ? 'unavailable' : 'available');
        unavailableSections = data.unavailableSections ?? [];
        loadedSections = [];
        loadingSections = [];
    });

    function switchTab(tabId: TabId) {
        activeTab = tabId;
        if (tabId === 'coins') void loadSections(['coinEconomy', 'childBehavior']);
        if (tabId === 'rewards') void loadSections(['rewards']);
        if (tabId === 'tasks') void loadSections(['tasks']);
        if (tabId === 'activity') void loadSections(['activation']);
    }

    function handleTabKeydown(event: KeyboardEvent, tabId: TabId) {
        const currentIndex = tabs.findIndex((tab) => tab.id === tabId);
        let nextIndex = currentIndex;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % tabs.length;
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
        if (event.key === 'Home') nextIndex = 0;
        if (event.key === 'End') nextIndex = tabs.length - 1;
        if (nextIndex === currentIndex) return;
        event.preventDefault();
        const nextTab = tabs[nextIndex];
        activeTab = nextTab.id;
        document.getElementById(`tab-${nextTab.id}`)?.focus();
    }

    function switchActivitySubtab(subtabId: ActivitySubtabId) {
        activeActivitySubtab = subtabId;
        if (subtabId === 'activation') void loadSections(['activation']);
        if (subtabId === 'retention') void loadSections(['retention', 'trends']);
        if (subtabId === 'needs') void loadSections(['parentBehavior', 'childBehavior']);
    }

    function handleActivitySubtabKeydown(event: KeyboardEvent, subtabId: ActivitySubtabId) {
        if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
            event.preventDefault();
            switchActivitySubtab(subtabId);
            return;
        }

        const currentIndex = activitySubtabs.findIndex((subtab) => subtab.id === subtabId);
        let nextIndex = currentIndex;
        if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % activitySubtabs.length;
        if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + activitySubtabs.length) % activitySubtabs.length;
        if (event.key === 'Home') nextIndex = 0;
        if (event.key === 'End') nextIndex = activitySubtabs.length - 1;
        if (nextIndex === currentIndex) return;
        event.preventDefault();
        const nextSubtab = activitySubtabs[nextIndex];
        switchActivitySubtab(nextSubtab.id);
        document.getElementById(`activity-subtab-${nextSubtab.id}`)?.focus();
    }

    async function navigateToPeriod(period: string) {
        try {
            // eslint-disable-next-line svelte/no-navigation-without-resolve
            await goto(`/telegram/dashboard?period=${period}`, { replaceState: true });
        } catch (error) {
            console.error('Statistics period navigation failed:', error);
        } finally {
            // EXPLAIN: Query-only navigation retains this component, so onMount
            // does not run again to clear the loading state.
            periodLoading = false;
            document.body.classList.remove('admin-loading');
        }
    }

    function changePeriod(period: string) {
        if (period === selectedPeriod || periodLoading) return;
        periodLoading = true;
        document.body.classList.add('admin-loading');
        void navigateToPeriod(period);
    }

    function sectionUnavailable(section: string): boolean {
        return unavailableSections.includes(section);
    }

    async function loadSections(sections: string[]): Promise<void> {
        const pending = sections.filter((section) =>
            !loadedSections.includes(section) && !loadingSections.includes(section));
        if (pending.length === 0) return;

        loadingSections = [...loadingSections, ...pending];
        await Promise.all(pending.map((section) => loadSection(section)));
    }

    async function loadSection(section: string): Promise<void> {
        const endpoint = analyticsEndpoints[section];
        const hasPeriod = section !== 'activation';
        try {
            const response = await fetch(`${endpoint}${hasPeriod ? `?period=${selectedPeriod}` : ''}`);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const payload: AnalyticsSectionData = await response.json();
            applySection(section, payload);
            loadedSections = [...loadedSections, section];
        } catch (error) {
            console.error(`Admin analytics ${section} fetch failed:`, error);
            unavailableSections = [...new Set([...unavailableSections, section])];
            if (section === 'trends') trendsStatus = 'unavailable';
        } finally {
            loadingSections = loadingSections.filter((item) => item !== section);
        }
    }

    function applySection(section: string, payload: AnalyticsSectionData): void {
        if (section === 'coinEconomy') coinEconomy = payload;
        if (section === 'tasks') taskEconomy = payload;
        if (section === 'parentBehavior') parentBehavior = payload;
        if (section === 'childBehavior') childBehavior = payload;
        if (section === 'activation') activationFunnel = payload;
        if (section === 'retention') retention = payload;
        if (section === 'rewards') rewards = payload;
        if (section === 'trends') {
            trends = payload;
            trendsStatus = 'available';
        }
    }

    function retry() {
        if (periodLoading) return;
        periodLoading = true;
        document.body.classList.add('admin-loading');
        void navigateToPeriod(selectedPeriod);
    }


    function barHeight(value: number, max: number): number {
        if (!max) return 0;
        return Math.max(2, Math.round((value / max) * 100));
    }

    // EXPLAIN: ADM-21 tap-accessible tooltip state
    let activeTooltip: string | null = $state(null);
    let tooltipTrigger: HTMLButtonElement | null = null;

    async function toggleTooltip(key: string, event: MouseEvent) {
        const trigger = event.currentTarget as HTMLButtonElement;
        if (activeTooltip === key) {
            closeTooltip();
            return;
        }
        tooltipTrigger = trigger;
        activeTooltip = key;
        await tick();
        document.querySelector<HTMLButtonElement>('.tooltip-close')?.focus();
    }

    async function closeTooltip() {
        activeTooltip = null;
        await tick();
        tooltipTrigger?.focus();
    }

    function handleTooltipKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && activeTooltip) {
            event.preventDefault();
            closeTooltip();
        }
    }

    function formatValue(val: number | string | null | undefined, isPercent = false): string {
        if (val === null || val === undefined) return '—';
        if (typeof val === 'number' && val === 0) return '0';
        return isPercent ? `${val}%` : typeof val === 'number' ? $i18n.formatNumber(val) : val;
    }

    function formatWholeValue(val: number | string | null | undefined): string {
        if (val === null || val === undefined) return '—';
        if (typeof val === 'number') return $i18n.formatNumber(Math.round(val));
        const numericValue = Number(val);
        return Number.isFinite(numericValue) ? $i18n.formatNumber(Math.round(numericValue)) : val;
    }

    // EXPLAIN: ADM-22 dynamic product insight hints derived from transparent metrics
    function buildCoinInsight(): string | null {
        const spendRate = coinEconomy?.coins?.spendRate;
        const earningNotSpending = childBehavior?.childBehaviorMetrics?.percentChildrenEarningNotSpending;
        const zeroBalance = coinEconomy?.balances?.zeroBalancePercent;

        if (earningNotSpending != null && earningNotSpending > 30) {
            return t('insights.earningNotSpending', { percent: Math.round(earningNotSpending) });
        }
        if (spendRate != null && spendRate < 40) {
            return t('insights.lowSpendRate', { percent: Math.round(spendRate) });
        }
        if (spendRate != null && spendRate > 90) {
            return t('insights.highSpendRate', { percent: Math.round(spendRate) });
        }
        if (zeroBalance != null && zeroBalance > 50) {
            return t('insights.manyZeroBalance', { percent: Math.round(zeroBalance) });
        }
        return null;
    }

    // EXPLAIN: ADM-21 tooltip content (what it shows, how it's calculated, example, interpretation)
    const tooltipContent: Record<string, { title: string; body: string }> = {
        activeFamilies: {
            title: t('tooltips.activeFamilies.label'),
            body: t('tooltips.activeFamilies.body'),
        },
        activeChildren: {
            title: t('tooltips.activeChildren.label'),
            body: t('tooltips.activeChildren.body'),
        },
        rewardsReceived: {
            title: t('tooltips.rewardsReceived.label'),
            body: t('tooltips.rewardsReceived.body'),
        },
        taskCompletions: {
            title: t('tooltips.taskCompletions.label'),
            body: t('tooltips.taskCompletions.body'),
        },
        spendEarn: {
            title: t('tooltips.spendEarn.label'),
            body: t('tooltips.spendEarn.body'),
        },
        medianBalance: {
            title: t('tooltips.medianBalance.label'),
            body: t('tooltips.medianBalance.body'),
        },
        timeToFirstReward: {
            title: t('tooltips.timeToFirstReward.label'),
            body: t('tooltips.timeToFirstReward.body'),
        },
        earningNotSpending: {
            title: t('tooltips.earningNotSpending.label'),
            body: t('tooltips.earningNotSpending.body'),
        },
        approvalRate: {
            title: t('tooltips.approvalRate.label'),
            body: t('tooltips.approvalRate.body'),
        },
        decisionTime: {
            title: t('tooltips.decisionTime.label'),
            body: t('tooltips.decisionTime.body'),
        },
        pendingBacklog: {
            title: t('tooltips.pendingBacklog.label'),
            body: t('tooltips.pendingBacklog.body'),
        },
        active7d: {
            title: t('tooltips.active7d.label'),
            body: t('tooltips.active7d.body'),
        },
        active30d: {
            title: t('tooltips.active30d.label'),
            body: t('tooltips.active30d.body'),
        },
    };
    // EXPLAIN: Server authorization is authoritative on direct dashboard
    // navigations; the store is populated later by the Mini App bootstrap.
    let isAdmin = $derived(data.isAdmin === true || $appStore.isAdmin);
    // EXPLAIN: The selected period comes from the URL (?period=...) and is
    // EXPLAIN: resolved server-side, so changing it reloads real data.
    let selectedPeriod = $derived(data.period ?? '30d');
    let selectedPeriodLabel = $derived(t(`periods.${selectedPeriod}`));
    // EXPLAIN: Trend bar helpers for ADM-14
    let maxActiveFamilies = $derived(Math.max(1, ...(trends?.points ?? []).map((p: { activeFamilies: number }) => p.activeFamilies)));
    let maxCoins = $derived(Math.max(1, ...(trends?.points ?? []).map((p: { coinsEarned: number; coinsSpent: number }) => Math.max(p.coinsEarned, p.coinsSpent))));
</script>

<svelte:head>
    <title>{t('title')} | EarnIt Kids</title>
</svelte:head>

{#if !isAdmin}
    <div class="redirect-message">
        <p>{t('redirecting')}</p>
    </div>
{:else}
    <main class="dashboard-container" aria-busy={periodLoading || loadingSections.length > 0 || $navigating !== null}>
        <TelegramParentReturn href="/telegram" />
        <header class="dashboard-header">
            <h1>{t('title')}</h1>
            <p class="subtitle">{t('subtitle')}</p>
        </header>

        <DashboardPeriodControl
            selectedPeriod={selectedPeriod}
            loading={periodLoading}
            updatedAt={t('updatedAt', { time: $i18n.formatDate(new Date(), { hour: '2-digit', minute: '2-digit' }) })}
            onChange={changePeriod}
        >
            {#snippet label({ period })}
                        <span  >{t(`periods.${period}`)}</span>
                    {/snippet}
        </DashboardPeriodControl>

        {#if periodLoading || $navigating !== null}
            <div class="period-loading" role="status" aria-live="polite">
                <span class="spinner" aria-hidden="true"></span>
                <span>{t('loading')}</span>
            </div>
        {/if}

        {#if dashboardStatus === 'unavailable'}
            <div class="empty-state" role="status">
                <span class="empty-ico" aria-hidden="true"><TelegramIcon name="alert" size={18} /></span>
                <b>{t('empty.unavailableTitle')}</b>
                <small>{t('empty.unavailableDesc')}</small>
                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
            </div>
        {:else if sectionUnavailable('overview')}
            <div class="empty-state" role="status">
                <span class="empty-ico" aria-hidden="true"><TelegramIcon name="alert" size={18} /></span>
                <b>{t('empty.sectionUnavailableTitle')}</b>
                <small>{t('empty.sectionUnavailableDesc')}</small>
                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
            </div>
        {:else if (overview?.overview?.totalFamilies ?? 0) === 0}
            <div class="empty-state" role="status">
                <span class="empty-ico" aria-hidden="true"><TelegramIcon name="box" size={18} /></span>
                <b>{t('empty.title')}</b>
                <small>{t('empty.desc')}</small>
            </div>
        {:else if (overview?.overview?.activeFamilies ?? 0) === 0}
            <div class="empty-state" role="status">
                <span class="empty-ico" aria-hidden="true"><TelegramIcon name="activity" size={18} /></span>
                <b>{t('empty.noActivityTitle')}</b>
                <small>{t('empty.noActivityDesc')}</small>
            </div>
        {/if}

        <DashboardTabNavigation
            {tabs}
            activeTab={activeTab}
            ariaLabel={t('aria.tabs')}
            onSelect={(tabId) => switchTab(tabId as TabId)}
            onKeydown={(event, tabId) => handleTabKeydown(event, tabId as TabId)}
        />

        {#if loadingSections.length > 0}
            <div class="section-loading" role="status" aria-live="polite">
                <span class="spinner" aria-hidden="true"></span>
                <span>{t('loading')}</span>
            </div>
        {/if}

        <!-- Tab panels -->
        <div class="tab-panels">
            <!-- Overview Tab -->
            <div
                id="panel-overview"
                class="tab-panel"
                class:active={activeTab === 'overview'}
                role="tabpanel"
                aria-labelledby="tab-overview"
            >
                {#if sectionUnavailable('overview')}
                    <div class="empty-state" role="status">
                        <b>{t('empty.sectionUnavailableTitle')}</b>
                        <small>{t('empty.sectionUnavailableDesc')}</small>
                        <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                    </div>
                {:else}
                {#if buildCoinInsight()}
                    <div class="overview-signal insight" role="status">
                        <strong>{t('overview.signalTitle')}</strong>
                        <span>{buildCoinInsight()}</span>
                    </div>
                {/if}
                <h2 class="section-title">{t('tabs.overview')}</h2>

                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.totalFamilies')}</div>
                        <div class="kpi-value">{formatValue(overview?.overview?.totalFamilies)}</div>
                        <div class="kpi-foot">{t('kpis.lifetime')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-head">
                            <div class="kpi-label">{t('kpis.activeFamilies')}</div>
                            <button class="info" aria-label={t('tooltips.activeFamilies.label')} onclick={(event) => toggleTooltip('activeFamilies', event)}><TelegramIcon name="help" size={15} /></button>
                        </div>
                        <div class="kpi-value">{formatValue(overview?.overview?.activeFamilies)}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriodLabel })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.totalChildren')}</div>
                        <div class="kpi-value">{formatValue(overview?.overview?.totalChildren)}</div>
                        <div class="kpi-foot">{t('kpis.lifetime')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-head">
                            <div class="kpi-label">{t('kpis.activeChildren')}</div>
                            <button class="info" aria-label={t('tooltips.activeChildren.label')} onclick={(event) => toggleTooltip('activeChildren', event)}><TelegramIcon name="help" size={15} /></button>
                        </div>
                        <div class="kpi-value">{formatValue(overview?.overview?.activeChildren)}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriodLabel })}</div>
                    </div>
                </div>

                {#if coinEconomy?.coins?.earned != null || coinEconomy?.coins?.spent != null || rewards?.metrics?.issuedCount != null}
                    <h2 class="section-title shop-state-title">{t('overview.shopStateTitle')}</h2>
                    <div class="metric-list overview-shop-state">
                        {#if coinEconomy?.coins?.earned != null}
                            <div class="metric">
                                <div>
                                    <strong>{t('kpis.coinsEarned')}</strong>
                                    <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                                </div>
                                <div class="metric-value">{formatValue(coinEconomy.coins.earned)} <TelegramCoin size={17} /></div>
                            </div>
                        {/if}
                        {#if coinEconomy?.coins?.spent != null}
                            <div class="metric">
                                <div>
                                    <strong>{t('kpis.coinsSpent')}</strong>
                                    <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                                </div>
                                <div class="metric-value">{formatValue(coinEconomy.coins.spent)} <TelegramCoin size={17} /></div>
                            </div>
                        {/if}
                        {#if rewards?.metrics?.issuedCount != null}
                            <div class="metric">
                                <div>
                                    <strong>{t('overview.rewardReceived')}</strong>
                                    <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                                </div>
                                <div class="metric-value">{formatValue(rewards.metrics.issuedCount)}</div>
                            </div>
                        {/if}
                    </div>
                {/if}

                {/if}
            </div>

            <!-- Coins Tab -->
            <div
                id="panel-coins"
                class="tab-panel"
                class:active={activeTab === 'coins'}
                role="tabpanel"
                aria-labelledby="tab-coins"
            >
                {#if sectionUnavailable('coinEconomy')}
                    <div class="empty-state" role="status">
                        <b>{t('empty.sectionUnavailableTitle')}</b>
                        <small>{t('empty.sectionUnavailableDesc')}</small>
                        <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                    </div>
                {:else}
                <h2 class="section-title">{t('sections.coinEconomy')}</h2>
                <div class="coin-cards">
                    <div class="coin-card coin-card-earned">
                        <span>{t('coins.earned')}</span>
                        <b>{formatValue(coinEconomy?.coins?.earned)} <TelegramCoin size={18} /></b>
                        <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                    </div>
                    <div class="coin-card coin-card-spent">
                        <span>{t('coins.spent')}</span>
                        <b>{formatValue(coinEconomy?.coins?.spent)} <TelegramCoin size={18} /></b>
                        <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                    </div>
                </div>

                <div class="compare coin-health">
                    <div class="coin-health-heading">
                        <div>
                            <strong>{t('coins.spendEarn.label')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.spendEarn.label')} onclick={(event) => toggleTooltip('spendEarn', event)}><TelegramIcon name="help" size={14} /></button>
                            <small>{t('kpis.inPeriod', { period: selectedPeriodLabel })}</small>
                        </div>
                        <b>{formatValue(coinEconomy?.coins?.spendRate, true)}</b>
                    </div>
                    <div class="bar">
                        <div class="bar-fill" style="width: {coinEconomy?.coins?.spendRate ?? 0}%"></div>
                    </div>
                    {#if buildCoinInsight()}
                        <div class="insight">{buildCoinInsight()}</div>
                    {/if}
                </div>

                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <div class="metric-title">
                                <strong>{t('metrics.medianBalance.title')}</strong>
                                <button class="mini-info" aria-label={t('tooltips.medianBalance.label')} onclick={(event) => toggleTooltip('medianBalance', event)}><TelegramIcon name="help" size={14} /></button>
                            </div>
                            <small>{t('metrics.medianBalance.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(coinEconomy?.balances?.medianBalance)} <TelegramCoin size={16} /></div>
                    </div>
                    <div class="metric">
                        <div>
                            <div class="metric-title">
                                <strong>{t('metrics.timeToFirstReward.title')}</strong>
                                <button class="mini-info" aria-label={t('tooltips.timeToFirstReward.label')} onclick={(event) => toggleTooltip('timeToFirstReward', event)}><TelegramIcon name="help" size={14} /></button>
                            </div>
                            <small>{t('metrics.timeToFirstReward.desc')}</small>
                        </div>
                        <div class="metric-value">{formatWholeValue(coinEconomy?.balances?.timeToFirstReward)} {t('units.days')}</div>
                    </div>
                    <div class="metric">
                        <div>
                            <div class="metric-title">
                                <strong>{t('metrics.earningNotSpending.title')}</strong>
                                <button class="mini-info" aria-label={t('tooltips.earningNotSpending.label')} onclick={(event) => toggleTooltip('earningNotSpending', event)}><TelegramIcon name="help" size={14} /></button>
                            </div>
                            <small>{t('metrics.earningNotSpending.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(coinEconomy?.balances?.zeroBalancePercent, true)}</div>
                    </div>
                </div>

                {/if}
            </div>

            <!-- Rewards Tab -->
            <div
                id="panel-rewards"
                class="tab-panel"
                class:active={activeTab === 'rewards'}
                role="tabpanel"
                aria-labelledby="tab-rewards"
            >
                {#if sectionUnavailable('rewards')}
                    <div class="empty-state" role="status">
                        <b>{t('empty.sectionUnavailableTitle')}</b>
                        <small>{t('empty.sectionUnavailableDesc')}</small>
                        <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                    </div>
                {:else}
                <h2 class="section-title">{t('tabs.rewards')}</h2>
                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.rewardRequests')}</div>
                        <div class="kpi-value">{formatValue(rewards?.metrics?.requestCount)}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriodLabel })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.rewardsIssued')}</div>
                        <div class="kpi-value">{formatValue(rewards?.metrics?.issuedCount)}</div>
                        <div class="kpi-foot">{t('kpis.successful')}</div>
                    </div>
                </div>

                <h2 class="section-title">{t('rewards.prices')}</h2>
                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.medianPrice.title')}</strong>
                            <small>{t('rewards.medianPrice.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(rewards?.metrics?.medianPrice)} <TelegramCoin size={16} /></div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.selectedPrice.title')}</strong>
                            <small>{t('rewards.selectedPrice.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(rewards?.metrics?.selectedPrice)} <TelegramCoin size={16} /></div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.failed.title')}</strong>
                            <small>{t('rewards.failed.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(rewards?.metrics?.failedRate, true)}</div>
                    </div>
                </div>

                <h2 class="section-title">{t('rewards.whatChildrenPick')}</h2>
                {#if rewards?.rankings && rewards.rankings.length > 0}
                    <div class="rows">
                        {#each rewards.rankings as rank (rank.category)}
                            <div class="rank">
                                <div class="rank-icon" aria-hidden="true"><TelegramIcon name="gift" size={18} /></div>
                                <div class="rank-content">
                                    <b>{rank.category}</b>
                                    <small>{rank.count} · {rank.percent}%</small>
                                </div>
                                <div class="rank-val">#{rank.rank}</div>
                            </div>
                        {/each}
                    </div>
                {:else}
                    <div class="empty-note">{t('rewards.rankingsEmpty')}</div>
                {/if}
                {/if}
            </div>

            <!-- Tasks Tab -->
            <div
                id="panel-tasks"
                class="tab-panel"
                class:active={activeTab === 'tasks'}
                role="tabpanel"
                aria-labelledby="tab-tasks"
            >
                {#if sectionUnavailable('tasks')}
                    <div class="empty-state" role="status">
                        <b>{t('empty.sectionUnavailableTitle')}</b>
                        <small>{t('empty.sectionUnavailableDesc')}</small>
                        <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                    </div>
                {:else}
                <h2 class="section-title">{t('tabs.tasks')}</h2>
                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('tasks.completed')}</div>
                        <div class="kpi-value">{formatValue(taskEconomy?.taskMetrics?.taskCompletions)}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriodLabel })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-head">
                            <div class="kpi-label">{t('tasks.approvalRate.label')}</div>
                            <button class="info" aria-label={t('tooltips.approvalRate.label')} onclick={(event) => toggleTooltip('approvalRate', event)}><TelegramIcon name="help" size={15} /></button>
                        </div>
                        <div class="kpi-value">{formatValue(taskEconomy?.taskMetrics?.approvalRate, true)}</div>
                        <div class="kpi-foot">{t('tasks.approvedByParents')}</div>
                    </div>
                </div>

                <h2 class="section-title">{t('sections.content')}</h2>
                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <strong>{t('tasks.catalogUsage.title')}</strong>
                            <small>{t('tasks.catalogUsage.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(taskEconomy?.taskMetrics?.familiesWithTasksPercent, true)}</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('tasks.customContent.title')}</strong>
                            <small>{t('tasks.customContent.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(taskEconomy?.taskMetrics?.tasksConfigured)}</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('tasks.coinsPerTask.title')}</strong>
                            <small>{t('tasks.coinsPerTask.desc')}</small>
                        </div>
                        <div class="metric-value">{formatValue(taskEconomy?.taskMetrics?.medianCoinsPerTask)} <TelegramCoin size={16} /></div>
                    </div>
                </div>

                {#if taskEconomy?.topPatterns && taskEconomy.topPatterns.length > 0}
                    <h2 class="section-title">{t('sections.popular')}</h2>
                    <div class="rows">
                        {#each taskEconomy.topPatterns as pattern, i (pattern.groupName)}
                            <div class="rank">
                                <div class="rank-icon" aria-hidden="true"><TelegramIcon name="task" size={18} /></div>
                                <div class="rank-content">
                                    <b>{pattern.groupName}</b>
                                    <small>{pattern.count} · {pattern.percent}%</small>
                                </div>
                                <div class="rank-val">#{i + 1}</div>
                            </div>
                        {/each}
                    </div>
                {/if}
                {#if (taskEconomy?.taskMetrics?.medianCompletionsPerChild ?? 0) > 0}
                    <div class="insight" role="status">
                        {t('tasks.completionSignal', { value: formatValue(taskEconomy?.taskMetrics?.medianCompletionsPerChild) })}
                    </div>
                {/if}
                {/if}
            </div>

            <!-- Activity Tab -->
            <div
                id="panel-activity"
                class="tab-panel"
                class:active={activeTab === 'activity'}
                role="tabpanel"
                aria-labelledby="tab-activity"
            >
                <div class="activity-subtabs" role="tablist" aria-label={t('aria.activitySubtabs')}>
                    {#each activitySubtabs as subtab (subtab.id)}
                        <button
                            type="button"
                            id={`activity-subtab-${subtab.id}`}
                            class="activity-subtab"
                            class:active={activeActivitySubtab === subtab.id}
                            role="tab"
                            aria-selected={activeActivitySubtab === subtab.id}
                            aria-controls={`panel-activity-${subtab.id}`}
                            tabindex={activeActivitySubtab === subtab.id ? 0 : -1}
                            onclick={() => switchActivitySubtab(subtab.id)}
                            onkeydown={(event) => handleActivitySubtabKeydown(event, subtab.id)}
                        >{subtab.label}</button>
                    {/each}
                </div>

                {#if activeActivitySubtab === 'activation'}
                    <div
                        id="panel-activity-activation"
                        class="activity-panel"
                        role="tabpanel"
                        aria-labelledby="activity-subtab-activation"
                    >
                        <h2 class="section-title">{t('sections.activation')}</h2>
                        {#if sectionUnavailable('activation')}
                            <div class="empty-state" role="status">
                                <b>{t('empty.sectionUnavailableTitle')}</b>
                                <small>{t('empty.sectionUnavailableDesc')}</small>
                                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                            </div>
                        {:else if activationFunnel?.stages && activationFunnel.stages.length > 0}
                            <div class="funnel">
                                {#each activationFunnel.stages as stage (stage.key)}
                                    <div class="step">
                                        <div class="step-line">
                                            <b>{stage.label}</b>
                                            <span class="step-metrics">
                                                <span class="step-count">{stage.count}</span>
                                                <span class="step-percent">{stage.percentFromPrevious}%</span>
                                            </span>
                                        </div>
                                        <div class="track">
                                            <div class="fill" style="width: {stage.percentFromInitial}%"></div>
                                        </div>
                                    </div>
                                {/each}
                            </div>
                        {:else}
                            <div class="funnel">
                                <div class="empty-state funnel-empty" role="status">
                                    <div>
                                        <b>{t('funnel.noData')}</b>
                                        <small>{t('funnel.noDataDesc')}</small>
                                    </div>
                                </div>
                            </div>
                        {/if}
                    </div>
                {/if}

                {#if activeActivitySubtab === 'retention'}
                    <div
                        id="panel-activity-retention"
                        class="activity-panel"
                        role="tabpanel"
                        aria-labelledby="activity-subtab-retention"
                    >
                        <h2 class="section-title">{t('sections.retention')}</h2>
                        {#if sectionUnavailable('retention')}
                            <div class="empty-state" role="status">
                                <b>{t('empty.sectionUnavailableTitle')}</b>
                                <small>{t('empty.sectionUnavailableDesc')}</small>
                                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                            </div>
                        {:else}
                        <div class="metric-list">
                            <div class="metric">
                                <div>
                                    <strong>{t('retention.newFamilies.title')}</strong>
                                    <small>{t('retention.newFamilies.desc')}</small>
                                </div>
                                <div class="metric-value">{retention?.retentionMetrics?.newFamilies ?? '—'}</div>
                            </div>
                            <div class="metric">
                                <div>
                                    <strong>{t('retention.returningFamilies.title')}</strong>
                                    <small>{t('retention.returningFamilies.desc')}</small>
                                </div>
                                <div class="metric-value">{retention?.retentionMetrics?.returningFamilies ?? '—'}</div>
                            </div>
                            <div class="metric">
                                <div>
                                    <div class="metric-title">
                                        <strong>{t('retention.active7d.title')}</strong>
                                        <button class="mini-info" aria-label={t('tooltips.active7d.label')} onclick={(event) => toggleTooltip('active7d', event)}><TelegramIcon name="help" size={14} /></button>
                                    </div>
                                    <small>{t('retention.active7d.desc')}</small>
                                </div>
                                <div class="metric-value">{retention?.retentionMetrics?.active7d ?? '—'}</div>
                            </div>
                            <div class="metric">
                                <div>
                                    <div class="metric-title">
                                        <strong>{t('retention.active30d.title')}</strong>
                                        <button class="mini-info" aria-label={t('tooltips.active30d.label')} onclick={(event) => toggleTooltip('active30d', event)}><TelegramIcon name="help" size={14} /></button>
                                    </div>
                                    <small>{t('retention.active30d.desc')}</small>
                                </div>
                                <div class="metric-value">{retention?.retentionMetrics?.active30d ?? '—'}</div>
                            </div>
                        </div>
                        {/if}

                        <h2 class="section-title">{t('sections.trends')}</h2>
                        {#if trendsStatus === 'unavailable'}
                            <div class="empty-state" role="status">
                                <b>{t('empty.sectionUnavailableTitle')}</b>
                                <small>{t('empty.sectionUnavailableDesc')}</small>
                                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                            </div>
                        {:else if trends?.points && trends.points.length > 0}
                            <div class="trend">
                                <div class="trend-head">
                                    <b>{t('trends.activeFamilies.title')}</b>
                                    <small>{t('trends.activeFamilies.desc')}</small>
                                </div>
                                <div class="bars">
                                    {#each trends.points as point (point.date)}
                                        <div class="bar-col" title="{point.date}: {point.activeFamilies}">
                                            <div class="bar" style="height: {barHeight(point.activeFamilies, maxActiveFamilies)}%"></div>
                                            <small class="bar-label">{point.date.slice(5)}</small>
                                        </div>
                                    {/each}
                                </div>
                            </div>
                            <div class="trend trend-coins">
                                <div class="trend-head">
                                    <b>{t('trends.earnedSpent.title')}</b>
                                    <small>{t('trends.earnedSpent.desc')}</small>
                                </div>
                                <div class="bars">
                                    {#each trends.points as point (point.date)}
                                        <div class="bar-col" title="{point.date}: {point.coinsEarned} / {point.coinsSpent}">
                                            <div class="bar earned" style="height: {barHeight(point.coinsEarned, maxCoins)}%"></div>
                                            <div class="bar spent" style="height: {barHeight(point.coinsSpent, maxCoins)}%"></div>
                                            <small class="bar-label">{point.date.slice(5)}</small>
                                        </div>
                                    {/each}
                                </div>
                            </div>
                        {:else}
                            <div class="empty-note">{t('trends.empty')}</div>
                        {/if}
                    </div>
                {/if}

                {#if activeActivitySubtab === 'needs'}
                    <div
                        id="panel-activity-needs"
                        class="activity-panel"
                        role="tabpanel"
                        aria-labelledby="activity-subtab-needs"
                    >
                        <h2 class="section-title">{t('sections.parentNeeds')}</h2>
                        {#if sectionUnavailable('parentBehavior')}
                            <div class="empty-state" role="status">
                                <b>{t('empty.sectionUnavailableTitle')}</b>
                                <small>{t('empty.sectionUnavailableDesc')}</small>
                                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                            </div>
                        {:else}
                            <div class="metric-list">
                                <div class="metric">
                                    <div>
                                        <strong>{t('parent.catalogUsage.title')}</strong>
                                        <small>{t('parent.catalogUsage.desc')}</small>
                                    </div>
                                    <div class="metric-value">{parentBehavior?.parentBehaviorMetrics?.familiesUsingCatalogPercent ?? '—'}%</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <strong>{t('parent.customContent.title')}</strong>
                                        <small>{t('parent.customContent.desc')}</small>
                                    </div>
                                    <div class="metric-value">{parentBehavior?.parentBehaviorMetrics?.familiesUsingCustomContentPercent ?? '—'}%</div>
                                </div>
                            </div>

                            <h3 class="section-title">{t('sections.parentCycle')}</h3>
                            <div class="metric-list">
                                <div class="metric">
                                    <div>
                                        <div class="metric-title">
                                            <strong>{t('parent.decisionTime.title')}</strong>
                                            <button class="mini-info" aria-label={t('tooltips.decisionTime.label')} onclick={(event) => toggleTooltip('decisionTime', event)}><TelegramIcon name="help" size={14} /></button>
                                        </div>
                                        <small>{t('parent.decisionTime.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(parentBehavior?.parentBehaviorMetrics?.medianApprovalDelayHours)} {t('units.hours')}</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <div class="metric-title">
                                            <strong>{t('parent.pendingBacklog.title')}</strong>
                                            <button class="mini-info" aria-label={t('tooltips.pendingBacklog.label')} onclick={(event) => toggleTooltip('pendingBacklog', event)}><TelegramIcon name="help" size={14} /></button>
                                        </div>
                                        <small>{t('parent.pendingBacklog.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(parentBehavior?.parentBehaviorMetrics?.pendingRequestsCount)}</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <strong>{t('parent.familiesWithPending.title')}</strong>
                                        <small>{t('parent.familiesWithPending.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(parentBehavior?.parentBehaviorMetrics?.familiesWithPendingRequests)}</div>
                                </div>
                            </div>
                        {/if}

                        <h2 class="section-title">{t('sections.childNeeds')}</h2>
                        {#if sectionUnavailable('childBehavior')}
                            <div class="empty-state" role="status">
                                <b>{t('empty.sectionUnavailableTitle')}</b>
                                <small>{t('empty.sectionUnavailableDesc')}</small>
                                <button class="retry-btn" type="button" onclick={retry}>{t('empty.retry')}</button>
                            </div>
                        {:else}
                            <div class="metric-list">
                                <div class="metric">
                                    <div>
                                        <strong>{t('child.activeDays.title')}</strong>
                                        <small>{t('child.activeDays.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(childBehavior?.childBehaviorMetrics?.medianActiveDaysPerChild)}</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <strong>{t('child.tasksBeforeReward.title')}</strong>
                                        <small>{t('child.tasksBeforeReward.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(childBehavior?.childBehaviorMetrics?.medianTasksBeforeReward)}</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <div class="metric-title">
                                            <strong>{t('child.earningNotSpending.title')}</strong>
                                            <button class="mini-info" aria-label={t('tooltips.earningNotSpending.label')} onclick={(event) => toggleTooltip('earningNotSpending', event)}><TelegramIcon name="help" size={14} /></button>
                                        </div>
                                        <small>{t('child.earningNotSpending.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(childBehavior?.childBehaviorMetrics?.percentChildrenEarningNotSpending, true)}</div>
                                </div>
                                <div class="metric">
                                    <div>
                                        <strong>{t('child.requestedNotReceived.title')}</strong>
                                        <small>{t('child.requestedNotReceived.desc')}</small>
                                    </div>
                                    <div class="metric-value">{formatValue(childBehavior?.childBehaviorMetrics?.percentChildrenRequestedNotReceived, true)}</div>
                                </div>
                            </div>
                        {/if}
                    </div>
                {/if}
            </div>
        </div>

        <DashboardTooltip
            {activeTooltip}
            content={tooltipContent}
            close={closeTooltip}
            closeLabel={t('tooltips.close')}
        />
</main>
{/if}

<svelte:window onkeydown={handleTooltipKeydown} />

<style>
    :global(.dashboard-container) {
        --dashboard-surface: #fff;
        --dashboard-line: #dfe4ee;
        --dashboard-shadow: none;
        padding: 12px 16px 0;
        max-width: 800px;
        margin: 0 auto;
        width: 100%;
        overflow-x: clip;
        padding-bottom: calc(92px + env(safe-area-inset-bottom));
        background: #fff;
        min-height: 100dvh;
    }

    .dashboard-header {
        margin-bottom: 14px;
        position: relative;
    }

    .section-loading {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        min-height: 40px;
        color: #66718a;
        font-size: 13px;
    }

    .dashboard-header h1 {
        font-size: 22px;
        line-height: 1.1;
        margin: 0;
        font-weight: 800;
        letter-spacing: -0.02em;
    }

    .subtitle {
        font-size: 13px;
        color: #78849f;
        margin: 5px 0 0;
    }

    .period-loading {
        display: flex;
        align-items: center;
        gap: 7px;
        min-height: 32px;
        margin: 0 0 8px;
        padding: 0 10px;
        border: 1px solid #dfe4ee;
        border-radius: 10px;
        background: #fff;
        color: #56627c;
        font-size: 12px;
        font-weight: 650;
    }

    .spinner {
        width: 14px;
        height: 14px;
        flex: 0 0 auto;
        border: 2px solid #d9def0;
        border-top-color: var(--primary, #5e6fec);
        border-radius: 50%;
        animation: dashboard-spin .7s linear infinite;
    }

    @keyframes dashboard-spin {
        to { transform: rotate(360deg); }
    }

    .activity-subtabs {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 3px;
        margin: 4px 0 14px;
        padding: 4px;
        background: #e4e8f1;
        border-radius: 15px;
    }

    .activity-subtab {
        min-width: 0;
        min-height: 44px;
        padding: 8px 6px;
        border: 0;
        border-radius: 11px;
        background: transparent;
        color: #687289;
        font-size: 12px;
        font-weight: 650;
        white-space: nowrap;
        cursor: pointer;
    }

    .activity-subtab.active {
        background: #fff;
        color: #4456d8;
        font-weight: 750;
        box-shadow: 0 2px 5px rgba(30, 40, 70, 0.12);
    }

    .tab-panels {
        margin-top: 8px;
    }

    .tab-panel {
        display: none;
    }

    .tab-panel.active {
        display: block;
    }

    .section-title {
        font-size: 16px;
        font-weight: 800;
        color: #1b2338;
        margin: 11px 0 7px;
    }

    .kpis {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 6px;
        margin-bottom: 10px;
    }

    .kpi {
        position: relative;
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 14px;
        box-shadow: var(--dashboard-shadow);
        padding: 9px 10px 8px;
        min-height: 76px;
        display: flex;
        flex-direction: column;
    }

    .kpi-label {
        max-width: calc(100% - 28px);
        font-size: 11px;
        color: #77839e;
        line-height: 1.25;
    }

    .kpi-head {
        display: flex;
        align-items: flex-start;
        gap: 5px;
    }

    .info {
        position: absolute;
        top: 3px;
        right: 3px;
        width: 44px;
        height: 44px;
        flex-shrink: 0;
        border: 0;
        border-radius: 8px;
        background: transparent;
        color: var(--primary, #5e6fec);
        display: grid;
        place-items: center;
        cursor: pointer;
    }

    .info :global(svg),
    .mini-info :global(svg) {
        padding: 4px;
        width: 24px;
        height: 24px;
        border-radius: 8px;
        background: var(--soft, #eef0ff);
        transition: background 0.15s, color 0.15s;
    }

    .kpi-value {
        font-size: 24px;
        font-weight: 820;
        margin-top: 3px;
        line-height: 1;
        letter-spacing: -0.03em;
    }

    .kpi-foot {
        font-size: 10px;
        color: #7f8ba5;
        margin-top: 3px;
    }

    .rows {
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        padding: 2px 11px;
    }

    .rank {
        display: grid;
        grid-template-columns: 28px minmax(0, 1fr) auto;
        gap: 8px;
        align-items: center;
        padding: 8px 0;
        border-bottom: 1px solid var(--line, #e5e8f0);
    }

    .rank:last-child {
        border-bottom: 0;
    }

    .rank-icon {
        font-size: 18px;
    }

    .rank-content b {
        font-size: 13px;
        display: block;
    }

    .rank-content small {
        display: block;
        color: var(--muted, #8791a6);
        font-size: 10px;
    }

    .rank-val {
        font-size: 12px;
        font-weight: 800;
    }

    .compare {
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        padding: 10px;
    }

    .coin-cards {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 8px;
        margin-bottom: 8px;
    }

    .coin-card {
        min-width: 0;
        padding: 10px;
        border: 1px solid var(--dashboard-line);
        border-radius: 14px;
        box-shadow: var(--dashboard-shadow);
    }

    .coin-card span,
    .coin-card small {
        display: block;
        color: var(--muted, #8791a6);
        font-size: 11px;
    }

    .coin-card b {
        display: block;
        margin: 5px 0 3px;
        font-size: 23px;
        letter-spacing: -0.03em;
    }

    .coin-card b :global(svg) {
        display: inline-block;
        vertical-align: -2px;
    }

    .coin-card-earned {
        background: #f1f3ff;
    }

    .coin-card-spent {
        background: #fff5e8;
    }

    .coin-health {
        margin-bottom: 8px;
    }

    .coin-health-heading {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 10px;
    }

    .coin-health-heading strong {
        display: block;
        font-size: 13px;
    }

    .coin-health-heading small {
        display: block;
        margin-top: 2px;
        color: var(--muted, #8791a6);
        font-size: 11px;
    }

    .coin-health-heading > b {
        font-size: 16px;
    }

    .bar {
        height: 9px;
        background: #edf0f5;
        border-radius: 99px;
        overflow: hidden;
        margin-top: 12px;
    }

    .bar-fill {
        display: block;
        height: 100%;
        background: var(--primary, #5e6fec);
    }

    .bar-label {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 10px;
        color: var(--muted, #8791a6);
        margin-top: 5px;
    }

    .mini-info {
        display: inline-flex;
        width: 44px;
        height: 44px;
        border: 0;
        border-radius: 8px;
        background: transparent;
        color: var(--muted, #8791a6);
        align-items: center;
        justify-content: center;
        cursor: pointer;
        margin-left: 4px;
        vertical-align: middle;
        transition: background 0.2s;
    }

    .mini-info:hover {
        color: var(--primary, #5e6fec);
    }

    .mini-info:hover :global(svg),
    .info:hover :global(svg) {
        background: #e5e8ff;
    }

    .mini-info:focus-visible {
        outline: 3px solid #273fd0;
        outline-offset: 2px;
    }

    .insight {
        margin: 10px 0 0;
        padding: 9px 10px;
        border: 1px solid #f0dfa7;
        border-radius: 12px;
        background: #fff8e8;
        color: #7a6618;
        font-size: 12px;
        line-height: 1.4;
    }

    .insight strong,
    .insight span {
        display: block;
    }

    .insight strong {
        margin-bottom: 4px;
        font-size: 13px;
        font-weight: 800;
    }

    .overview-signal {
        margin: 0 0 13px;
    }

    .overview-shop-state {
        margin-top: 0;
    }

    .metric-list {
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 14px;
        box-shadow: var(--dashboard-shadow);
        overflow: hidden;
    }

    .metric {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 10px;
        align-items: center;
        padding: 10px 11px;
        border-bottom: 1px solid var(--dashboard-line);
    }

    .metric:last-child {
        border-bottom: 0;
    }

    .metric strong {
        font-size: 13px;
        display: block;
    }

    .metric-title {
        display: flex;
        align-items: center;
        gap: 6px;
    }

    .metric small {
        display: block;
        color: var(--muted, #8791a6);
        font-size: 11px;
        margin-top: 4px;
    }

    .metric-value {
        text-align: right;
        font-weight: 800;
        font-size: 18px;
        white-space: nowrap;
    }

    .metric-value :global(svg) {
        display: inline-block;
        vertical-align: -4px;
    }

    .funnel {
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        padding: 10px;
    }

    .step {
        margin-bottom: 10px;
    }

    .step:last-child {
        margin: 0;
    }

    .step-line {
        display: flex;
        align-items: baseline;
        gap: 10px;
        justify-content: space-between;
        min-width: 0;
        font-size: 12px;
    }

    .step-line b {
        min-width: 0;
        font-size: 12px;
        overflow-wrap: anywhere;
    }

    .step-metrics {
        display: inline-flex;
        flex: 0 0 auto;
        gap: 6px;
        color: var(--muted, #8791a6);
        white-space: nowrap;
    }

    .step-count {
        color: var(--text, #20283d);
        font-weight: 750;
    }

    .step-percent {
        min-width: 3.5em;
        text-align: right;
    }

    .track {
        height: 8px;
        background: #eef1f6;
        border-radius: 99px;
        overflow: hidden;
        margin-top: 5px;
    }

    .fill {
        height: 100%;
        background: var(--primary, #5e6fec);
    }

    .funnel-empty {
        min-height: 76px;
        display: flex;
        align-items: center;
        justify-content: center;
        text-align: center;
        border: 1px dashed var(--dashboard-line);
        border-radius: 14px;
        padding: 12px;
    }

    .funnel-empty small {
        display: block;
        color: var(--muted, #8791a6);
        margin-top: 4px;
    }

    .trend {
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        padding: 13px;
        margin-bottom: 10px;
    }

    .trend-coins {
        margin-bottom: 0;
    }

    .trend-head {
        margin-bottom: 8px;
    }

    .trend-head b {
        font-size: 13px;
        display: block;
    }

    .trend-head small {
        color: var(--muted, #8791a6);
        font-size: 11px;
    }

    .bars {
        display: flex;
        align-items: flex-end;
        gap: 4px;
        height: 90px;
        overflow-x: auto;
        padding-bottom: 4px;
    }

    .bar-col {
        flex: 0 0 18px;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: flex-end;
        height: 100%;
    }

    .bar {
        width: 100%;
        background: var(--primary, #5e6fec);
        border-radius: 2px 2px 0 0;
    }

    .bar.earned {
        background: var(--primary, #5e6fec);
    }

    .bar.spent {
        background: var(--orange, #f0a35e);
    }

    .bar-label {
        font-size: 9px;
        color: var(--muted, #8791a6);
        margin-top: 4px;
        white-space: nowrap;
        opacity: 0.8;
    }

    .empty-note {
        padding: 11px;
        background: var(--dashboard-surface);
        border: 1px solid var(--dashboard-line);
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        color: var(--muted, #8791a6);
        font-size: 12px;
        line-height: 1.35;
        text-align: center;
    }

    .empty-state {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 11px;
        margin: 6px 0 12px;
        background: var(--dashboard-surface);
        border: 1px dashed #cbd2e0;
        border-radius: 18px;
        box-shadow: var(--dashboard-shadow);
        min-height: 76px;
        text-align: left;
    }

    .empty-ico {
        width: 34px;
        height: 34px;
        border-radius: 10px;
        background: var(--soft, #eef0ff);
        display: grid;
        place-items: center;
        font-size: 18px;
        flex-shrink: 0;
    }

    .empty-state b {
        font-size: 12px;
        min-width: 0;
    }

    .empty-state small {
        min-width: 0;
        color: var(--muted, #8791a6);
        font-size: 11px;
        line-height: 1.3;
        flex: 1;
    }

    .redirect-message {
        padding: 20px;
        text-align: center;
        color: var(--muted, #8791a6);
    }

    @media (max-width: 350px) {
        :global(.dashboard-container) {
            padding-left: 10px;
            padding-right: 10px;
        }

    }
</style>
