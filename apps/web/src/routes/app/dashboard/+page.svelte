<script lang="ts">
    import { onMount } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { goto } from '$app/navigation';

    const i18n = useI18n();

    function t(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.dashboard.${key}` as MessageKey, variables);
    }

    $: isAdmin = $appStore.isAdmin;

    export let data;
    $: overview = data.overview;
    $: coinEconomy = data.coinEconomy;
    $: rewardShop = data.rewardShop;

    // Tab definitions with semantic icons
    const tabs = [
        { id: 'overview', label: t('tabs.overview'), icon: '📊' },
        { id: 'coins', label: t('tabs.coins'), icon: '🪙' },
        { id: 'rewards', label: t('tabs.rewards'), icon: '🎁' },
        { id: 'tasks', label: t('tabs.tasks'), icon: '✅' },
        { id: 'activity', label: t('tabs.activity'), icon: '📈' },
    ] as const;

    type TabId = typeof tabs[number]['id'];

    let activeTab: TabId = 'overview';
    let selectedPeriod = '30d';

    // Redirect non-admins on mount
    onMount(() => {
        if (!isAdmin) {
            // eslint-disable-next-line svelte/no-navigation-without-resolve
            goto('/app/settings', { replaceState: true }).resolve();
        }
    });

    function switchTab(tabId: TabId) {
        activeTab = tabId;
    }

    function changePeriod(period: string) {
        selectedPeriod = period;
        // Period change logic will be implemented with actual data loading
    }
</script>

<svelte:head>
    <title>{t('title')} | EarnIt Kids</title>
</svelte:head>

{#if !isAdmin}
    <div class="redirect-message">
        <p>{t('redirecting')}</p>
    </div>
{:else}
    <main class="dashboard-container">
        <header class="dashboard-header">
            <h1>{t('title')}</h1>
            <p class="subtitle">{t('subtitle')}</p>
        </header>

        <!-- Period selector toolbar -->
        <div class="toolbar">
            <div class="segment">
                <button 
                    class="seg" 
                    class:active={selectedPeriod === '7d'}
                    on:click={() => changePeriod('7d')}
                >
                    {t('periods.7d')}
                </button>
                <button 
                    class="seg" 
                    class:active={selectedPeriod === '30d'}
                    on:click={() => changePeriod('30d')}
                >
                    {t('periods.30d')}
                </button>
                <button 
                    class="seg" 
                    class:active={selectedPeriod === '90d'}
                    on:click={() => changePeriod('90d')}
                >
                    {t('periods.90d')}
                </button>
                <button 
                    class="seg" 
                    class:active={selectedPeriod === 'all'}
                    on:click={() => changePeriod('all')}
                >
                    {t('periods.all')}
                </button>
            </div>
            <div class="updated">
                {t('updatedAt', { time: new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }) })}
            </div>
        </div>

        <!-- Tab navigation -->
        <div class="tabs-wrap">
            <div class="tabs" role="tablist" aria-label={t('aria.tabs')}>
                {#each tabs as tab (tab.id)}
                    <button
                        class="tab"
                        class:active={activeTab === tab.id}
                        role="tab"
                        aria-selected={activeTab === tab.id}
                        aria-controls={`panel-${tab.id}`}
                        on:click={() => switchTab(tab.id as TabId)}
                    >
                        <span class="tab-ico" aria-hidden="true">{tab.icon}</span>
                        <span class="tab-label">{tab.label}</span>
                    </button>
                {/each}
            </div>
        </div>

        <!-- Tab panels -->
        <div class="tab-panels">
            <!-- Overview Tab -->
            <section 
                id="panel-overview" 
                class="tab-panel" 
                class:active={activeTab === 'overview'}
                role="tabpanel"
                aria-labelledby="tab-overview"
            >
                <h2 class="section-title">{t('tabs.overview')}</h2>
                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.totalFamilies')}</div>
                        <div class="kpi-value">{overview?.overview?.totalFamilies ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.lifetime')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.activeFamilies')}</div>
                        <button class="info" aria-label={t('tooltips.activeFamilies.label')} title={t('tooltips.activeFamilies.label')}>i</button>
                        <div class="kpi-value">{overview?.overview?.activeFamilies ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriod })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.totalChildren')}</div>
                        <div class="kpi-value">{overview?.overview?.totalChildren ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.lifetime')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.activeChildren')}</div>
                        <button class="info" aria-label={t('tooltips.activeChildren.label')} title={t('tooltips.activeChildren.label')}>i</button>
                        <div class="kpi-value">{overview?.overview?.activeChildren ?? '—'}</div>
                        <div class="kpi-foot">{t('periods.30d')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.coinsEarned')}</div>
                        <div class="kpi-value">{overview?.overview?.coinsEarned ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriod })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.coinsSpent')}</div>
                        <div class="kpi-value">{overview?.overview?.coinsSpent ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriod })}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.rewardsReceived')}</div>
                        <button class="info" aria-label={t('tooltips.rewardsReceived.label')} title={t('tooltips.rewardsReceived.label')}>i</button>
                        <div class="kpi-value">{overview?.overview?.rewardPurchases ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.successful')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('kpis.taskCompletions')}</div>
                        <button class="info" aria-label={t('tooltips.taskCompletions.label')} title={t('tooltips.taskCompletions.label')}>i</button>
                        <div class="kpi-value">{overview?.overview?.taskCompletions ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.inPeriod', { period: selectedPeriod })}</div>
                    </div>
                </div>

                <div class="tooltip-box" id="overview-tooltips">
                    <p class="tooltip-placeholder">{t('tooltips.selectInfo')}</p>
                </div>

                <h2 class="section-title">{t('sections.keySignals')}</h2>
                <div class="rows">
                    <div class="rank">
                        <div class="rank-icon">🪙</div>
                        <div class="rank-content">
                            <b>{t('signals.earningNotSpending.title')}</b>
                            <small>{t('signals.earningNotSpending.desc')}</small>
                        </div>
                        <div class="rank-val">—%</div>
                    </div>
                    <div class="rank">
                        <div class="rank-icon">⏱️</div>
                        <div class="rank-content">
                            <b>{t('signals.decisionTime.title')}</b>
                            <small>{t('signals.decisionTime.desc')}</small>
                        </div>
                        <div class="rank-val">— {t('units.minutes')}</div>
                    </div>
                </div>
            </section>

            <!-- Coins Tab -->
            <section 
                id="panel-coins" 
                class="tab-panel" 
                class:active={activeTab === 'coins'}
                role="tabpanel"
                aria-labelledby="tab-coins"
            >
                <h2 class="section-title">{t('sections.coinEconomy')}</h2>
                <div class="compare">
                    <div class="compare-top">
                        <div class="number-block">
                            <span>{t('coins.earned')}</span>
                            <b>{coinEconomy?.coins?.earned?.toLocaleString() ?? '—'}</b>
                        </div>
                        <div class="number-block">
                            <span>{t('coins.spent')}</span>
                            <b>{coinEconomy?.coins?.spent?.toLocaleString() ?? '—'}</b>
                        </div>
                    </div>
                    <div class="bar">
                        <div class="bar-fill" style="width: {coinEconomy?.coins?.spendRate ?? 0}%"></div>
                    </div>
                    <div class="bar-label">
                        <span>
                            {t('coins.spendEarn.label')} 
                            <button class="mini-info" aria-label={t('tooltips.spendEarn.label')}>i</button>
                        </span>
                        <b>{coinEconomy?.coins?.spendRate ?? '—'}%</b>
                    </div>
                    <div class="insight">{t('coins.insight')}</div>
                </div>

                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <strong>{t('metrics.medianBalance.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.medianBalance.label')}>i</button>
                            <small>{t('metrics.medianBalance.desc')}</small>
                        </div>
                        <div class="metric-value">{coinEconomy?.balances?.medianBalance ?? '—'} 🪙</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('metrics.timeToFirstReward.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.timeToFirstReward.label')}>i</button>
                            <small>{t('metrics.timeToFirstReward.desc')}</small>
                        </div>
                        <div class="metric-value">— {t('units.days')}</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('metrics.earningNotSpending.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.earningNotSpending.label')}>i</button>
                            <small>{t('metrics.earningNotSpending.desc')}</small>
                        </div>
                        <div class="metric-value">{coinEconomy?.balances?.zeroBalancePercent ?? '—'}%</div>
                    </div>
                </div>
            </section>

            <!-- Rewards Tab -->
            <section 
                id="panel-rewards" 
                class="tab-panel" 
                class:active={activeTab === 'rewards'}
                role="tabpanel"
                aria-labelledby="tab-rewards"
            >
                <h2 class="section-title">{t('tabs.rewards')}</h2>
                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('rewards.requests')}</div>
                        <div class="kpi-value">{rewardShop?.rewardShopMetrics?.rewardRequests ?? '—'}</div>
                        <div class="kpi-foot">{t('periods.30d')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">{t('rewards.issued')}</div>
                        <button class="info" aria-label={t('tooltips.rewardsIssued.label')}>i</button>
                        <div class="kpi-value">{rewardShop?.rewardShopMetrics?.approvedRewards ?? '—'}</div>
                        <div class="kpi-foot">{t('kpis.successful')}</div>
                    </div>
                </div>

                <h2 class="section-title">{t('sections.prices')}</h2>
                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.medianPrice.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.medianPrice.label')}>i</button>
                            <small>{t('rewards.medianPrice.desc')}</small>
                        </div>
                        <div class="metric-value">{rewardShop?.rewardShopMetrics?.medianPrice ?? '—'} 🪙</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.chosenPrice.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.chosenPrice.label')}>i</button>
                            <small>{t('rewards.chosenPrice.desc')}</small>
                        </div>
                        <div class="metric-value">{rewardShop?.rewardShopMetrics?.medianPurchasedPrice ?? '—'} 🪙</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('rewards.failed.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.rewardsFailed.label')}>i</button>
                            <small>{t('rewards.failed.desc')}</small>
                        </div>
                        <div class="metric-value">{rewardShop?.rewardShopMetrics?.rejectionRate ?? '—'}%</div>
                    </div>
                </div>

                <h2 class="section-title">{t('sections.popular')}</h2>
                <div class="rows">
                    <div class="rank">
                        <div class="rank-icon">🎲</div>
                        <div class="rank-content">
                            <b>{t('popular.entertainment.title')}</b>
                            <small>— {t('units.issues')}</small>
                        </div>
                        <div class="rank-val">#1</div>
                    </div>
                    <div class="rank">
                        <div class="rank-icon">👨‍👩‍👧</div>
                        <div class="rank-content">
                            <b>{t('popular.familyTime.title')}</b>
                            <small>— {t('units.issues')}</small>
                        </div>
                        <div class="rank-val">#2</div>
                    </div>
                </div>
            </section>

            <!-- Tasks Tab -->
            <section 
                id="panel-tasks" 
                class="tab-panel" 
                class:active={activeTab === 'tasks'}
                role="tabpanel"
                aria-labelledby="tab-tasks"
            >
                <h2 class="section-title">{t('tabs.tasks')}</h2>
                <div class="kpis">
                    <div class="kpi">
                        <div class="kpi-label">{t('tasks.completed')}</div>
                        <div class="kpi-value">—</div>
                        <div class="kpi-foot">{t('periods.30d')}</div>
                    </div>
                    <div class="kpi">
                        <div class="kpi-label">Approval rate</div>
                        <button class="info" aria-label="Процент одобренных заданий">i</button>
                        <div class="kpi-value">—%</div>
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
                        <div class="metric-value">—%</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('tasks.customContent.title')}</strong>
                            <small>{t('tasks.customContent.desc')}</small>
                        </div>
                        <div class="metric-value">—%</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('tasks.coinsPerTask.title')}</strong>
                            <small>{t('tasks.coinsPerTask.desc')}</small>
                        </div>
                        <div class="metric-value">— 🪙</div>
                    </div>
                </div>
            </section>

            <!-- Activity Tab -->
            <section 
                id="panel-activity" 
                class="tab-panel" 
                class:active={activeTab === 'activity'}
                role="tabpanel"
                aria-labelledby="tab-activity"
            >
                <h2 class="section-title">{t('sections.activation')}</h2>
                <div class="funnel">
                    <div class="step">
                        <div class="step-line">
                            <b>{t('funnel.registered')}</b>
                            <span>—</span>
                        </div>
                        <div class="track">
                            <div class="fill" style="width: 100%"></div>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-line">
                            <b>{t('funnel.addedChild')}</b>
                            <span>—</span>
                        </div>
                        <div class="track">
                            <div class="fill" style="width: 0%"></div>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-line">
                            <b>{t('funnel.hasTask')}</b>
                            <span>—</span>
                        </div>
                        <div class="track">
                            <div class="fill" style="width: 0%"></div>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-line">
                            <b>{t('funnel.earnedCoins')}</b>
                            <span>—</span>
                        </div>
                        <div class="track">
                            <div class="fill" style="width: 0%"></div>
                        </div>
                    </div>
                    <div class="step">
                        <div class="step-line">
                            <b>{t('funnel.receivedReward')}</b>
                            <span>—</span>
                        </div>
                        <div class="track">
                            <div class="fill" style="width: 0%"></div>
                        </div>
                    </div>
                </div>

                <h2 class="section-title">{t('sections.parentCycle')}</h2>
                <div class="metric-list">
                    <div class="metric">
                        <div>
                            <strong>{t('parent.decisionTime.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.decisionTime.label')}>i</button>
                            <small>{t('parent.decisionTime.desc')}</small>
                        </div>
                        <div class="metric-value">— {t('units.minutes')}</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('parent.pendingBacklog.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.pendingBacklog.label')}>i</button>
                            <small>{t('parent.pendingBacklog.desc')}</small>
                        </div>
                        <div class="metric-value">—</div>
                    </div>
                    <div class="metric">
                        <div>
                            <strong>{t('parent.activeDays.title')}</strong>
                            <button class="mini-info" aria-label={t('tooltips.activeDays.label')}>i</button>
                            <small>{t('parent.activeDays.desc')}</small>
                        </div>
                        <div class="metric-value">—</div>
                    </div>
                </div>
            </section>
        </div>

        <div class="footer-note">
            <b>{t('footer.keyUxTitle')}</b> {t('footer.keyUxText')}
        </div>
    </main>
{/if}

<style>
    :global(.dashboard-container) {
        padding: 14px;
        background: var(--bg, #f6f7fb);
        min-height: 100vh;
    }

    .dashboard-header {
        margin-bottom: 14px;
    }

    .dashboard-header h1 {
        font-size: 25px;
        margin: 0;
        font-weight: 750;
    }

    .subtitle {
        font-size: 12px;
        color: var(--muted, #8791a6);
        margin-top: 3px;
    }

    .toolbar {
        display: flex;
        align-items: center;
        gap: 7px;
        margin: 13px 0 9px;
    }

    .segment {
        display: flex;
        gap: 4px;
        background: #eceff6;
        padding: 3px;
        border-radius: 12px;
        overflow: auto;
        min-width: 0;
    }

    .seg {
        border: 0;
        background: transparent;
        border-radius: 9px;
        padding: 8px 10px;
        color: #687289;
        font-size: 11px;
        white-space: nowrap;
        cursor: pointer;
    }

    .seg.active {
        background: #fff;
        color: #4456d8;
        font-weight: 750;
        box-shadow: 0 1px 3px rgba(30, 40, 70, 0.08);
    }

    .updated {
        margin-left: auto;
        color: var(--muted, #8791a6);
        font-size: 10px;
        white-space: nowrap;
    }

    .tabs-wrap {
        position: sticky;
        top: 0;
        background: var(--bg, #f6f7fb);
        padding: 6px 0 8px;
        z-index: 2;
    }

    .tabs {
        width: 100%;
        display: grid;
        grid-template-columns: repeat(5, minmax(0, 1fr));
        gap: 4px;
    }

    .tab {
        min-width: 0;
        min-height: 52px;
        border: 1px solid var(--line, #e5e8f0);
        background: #fff;
        color: #687289;
        border-radius: 11px;
        padding: 5px 2px 4px;
        font-size: 10px;
        line-height: 1.05;
        font-weight: 700;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 4px;
        overflow: hidden;
        cursor: pointer;
    }

    .tab-ico {
        display: block;
        font-size: 17px;
        line-height: 1;
    }

    .tab-label {
        display: block;
        max-width: 100%;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: clip;
    }

    .tab.active {
        background: var(--primary, #5e6fec);
        color: #fff;
        border-color: var(--primary, #5e6fec);
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
        font-size: 18px;
        font-weight: 800;
        margin: 16px 0 9px;
    }

    .kpis {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
    }

    .kpi {
        background: #fff;
        border: 1px solid var(--line, #e5e8f0);
        border-radius: 14px;
        padding: 11px;
        min-height: 91px;
        position: relative;
    }

    .kpi-label {
        font-size: 11px;
        color: var(--muted, #8791a6);
        line-height: 1.25;
    }

    .info {
        position: absolute;
        top: 11px;
        right: 11px;
        width: 20px;
        height: 20px;
        border: 0;
        border-radius: 50%;
        background: var(--soft, #eef0ff);
        color: var(--primary, #5e6fec);
        font-size: 12px;
        font-weight: 800;
        display: grid;
        place-items: center;
        cursor: pointer;
    }

    .kpi-value {
        font-size: 23px;
        font-weight: 820;
        margin-top: 6px;
        letter-spacing: -0.03em;
    }

    .kpi-foot {
        font-size: 10px;
        color: #99a1b1;
        margin-top: 4px;
    }

    .tooltip-box {
        margin-top: 8px;
        background: #202633;
        color: #fff;
        border-radius: 13px;
        padding: 11px 12px;
        font-size: 11px;
        line-height: 1.45;
        box-shadow: 0 8px 22px rgba(25, 31, 45, 0.18);
    }

    .tooltip-placeholder {
        margin: 0;
        color: #ccd2ff;
    }

    .rows {
        background: #fff;
        border: 1px solid var(--line, #e5e8f0);
        border-radius: 15px;
        padding: 4px 12px;
    }

    .rank {
        display: grid;
        grid-template-columns: 28px minmax(0, 1fr) auto;
        gap: 8px;
        align-items: center;
        padding: 10px 0;
        border-bottom: 1px solid var(--line, #e5e8f0);
    }

    .rank:last-child {
        border-bottom: 0;
    }

    .rank-icon {
        font-size: 20px;
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
        background: #fff;
        border: 1px solid var(--line, #e5e8f0);
        border-radius: 15px;
        padding: 12px;
    }

    .compare-top {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 8px;
    }

    .number-block {
        padding: 8px;
        border-radius: 11px;
        background: #f8f9fc;
    }

    .number-block span {
        font-size: 11px;
        color: var(--muted, #8791a6);
    }

    .number-block b {
        font-size: 20px;
        display: block;
        margin-top: 3px;
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

    .bar-label b {
        font-size: 12px;
    }

    .mini-info {
        display: inline-flex;
        width: 18px;
        height: 18px;
        border: 0;
        border-radius: 50%;
        background: var(--soft, #eef0ff);
        color: var(--primary, #5e6fec);
        font-size: 11px;
        font-weight: 800;
        align-items: center;
        justify-content: center;
        cursor: pointer;
        margin-left: 4px;
    }

    .insight {
        margin-top: 9px;
        padding: 9px 10px;
        border-radius: 11px;
        background: var(--orangeSoft, #fff5e3);
        color: var(--orange, #805717);
        font-size: 11px;
        line-height: 1.35;
    }

    .metric-list {
        background: #fff;
        border: 1px solid var(--line, #e5e8f0);
        border-radius: 15px;
        overflow: hidden;
    }

    .metric {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 10px;
        align-items: center;
        padding: 11px 12px;
        border-bottom: 1px solid var(--line, #e5e8f0);
    }

    .metric:last-child {
        border-bottom: 0;
    }

    .metric strong {
        font-size: 14px;
        display: block;
    }

    .metric small {
        display: block;
        color: var(--muted, #8791a6);
        font-size: 11px;
        margin-top: 2px;
    }

    .metric-value {
        text-align: right;
        font-weight: 800;
        font-size: 14px;
    }

    .funnel {
        background: #fff;
        border: 1px solid var(--line, #e5e8f0);
        border-radius: 15px;
        padding: 12px;
    }

    .step {
        margin-bottom: 10px;
    }

    .step:last-child {
        margin: 0;
    }

    .step-line {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
    }

    .step-line b {
        font-size: 12px;
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

    .footer-note {
        padding: 12px;
        margin-top: 18px;
        border: 1px dashed #ccd2df;
        border-radius: 13px;
        color: #687287;
        font-size: 11px;
        line-height: 1.45;
    }

    .redirect-message {
        padding: 20px;
        text-align: center;
        color: var(--muted, #8791a6);
    }

    @media (max-width: 350px) {
        .tabs {
            gap: 3px;
        }

        .tab {
            font-size: 9px;
            padding-left: 1px;
            padding-right: 1px;
        }

        .tab-ico {
            font-size: 16px;
        }
    }
</style>
