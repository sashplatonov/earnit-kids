<script lang="ts">
    import { resolve } from '$app/paths';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { adminAwardCoins } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    export let balance: number = 0;
    export let earnedCount: number = 0;
    export let earnedLimitNote: string = '';
    export let isAdmin: boolean = false;
    export let childNickname: string = '';

    const i18n = useI18n();

    let menuOpen = false;
    let menuElement: HTMLElement | null = null;

    $: historyCount = earnedCount || $appStore.history.length;
    $: resolvedLimitNote = earnedLimitNote.trim().length > 0
        ? earnedLimitNote
        : $appStore.dailyCoinLimit > 0
            ? `${$i18n.formatNumber($appStore.dailyCoinLimit)} / ${$i18n.t('common.units.perDay')}`
            : '';
    $: showLimitNote = resolvedLimitNote.trim().length > 0;

    function openSettings() {
        location.assign($i18n.href(resolve('/app/[section]', { section: 'settings' })));
    }

    function openHistory() {
        location.assign($i18n.href(resolve('/app/[section]', { section: 'history' })));
    }

    function handleBalanceKeydown(event: KeyboardEvent) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            openHistory();
        }
    }

    function closeMenu() {
        menuOpen = false;
    }

    function handleWindowClick(event: MouseEvent) {
        const target = event.target;
        if (!(target instanceof Node)) return;
        if (menuOpen && menuElement && !menuElement.contains(target)) {
            menuOpen = false;
        }
    }

    function handleWindowKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            menuOpen = false;
        }
    }

    async function handleAwardCoins() {
        const currentChildId = $appStore.currentChildId;
        if (!currentChildId) { showToast($i18n.t('app.shell.selectChildFirst'), 'error'); return; }
        const amountStr = prompt($i18n.t('app.shell.promptAmount'));
        if (amountStr === null) return;
        const amount = Number.parseInt(amountStr, 10);
        if (!Number.isFinite(amount) || amount === 0) { showToast($i18n.t('app.shell.invalidAmount'), 'error'); return; }
        const defaultDescription = amount > 0
            ? $i18n.t('app.shell.defaultPositiveDescription')
            : $i18n.t('app.shell.defaultNegativeDescription');
        const description = prompt($i18n.t('app.shell.promptDescription'), defaultDescription)?.trim()
            || defaultDescription;
        const result = await adminAwardCoins(currentChildId, amount, description);
        if (result) {
            applyDataSnapshot(result as Record<string, unknown>);
            showToast(
                amount > 0
                    ? $i18n.t('app.shell.awardPositiveSuccess', { amount: $i18n.formatNumber(amount) })
                    : $i18n.t('app.shell.awardNegativeSuccess', { amount: $i18n.formatNumber(amount) }),
                'success'
            );
        } else {
            showToast($i18n.t('app.shell.awardError'), 'error');
        }
    }
</script>

<svelte:window on:click={handleWindowClick} on:keydown={handleWindowKeydown} />

<header class="header" class:header--admin={isAdmin}>
    <div class="header__system-row">
        <div class="header__logo">
            <span class="header__icon gamified-icon icon-coin-stack" aria-hidden="true"></span>
            <div class="header__titles">
                <h1>{$i18n.t('common.brand.name')}{#if isAdmin}<span class="header__admin-badge admin-only">{$i18n.t('app.shell.parentBadge')}</span>{/if}</h1>
                {#if childNickname}
                    <div id="child-nickname-display" class="header__child-nickname">{childNickname}</div>
                {/if}
            </div>
        </div>
        <div class="header__actions">
            <LocaleSwitcher compact={true} />
            <div class="header__overflow-menu" bind:this={menuElement}>
                <button
                    class="btn btn--secondary btn--small header__overflow-btn"
                    type="button"
                    aria-haspopup="menu"
                    aria-expanded={menuOpen}
                    aria-controls="header-overflow-dropdown"
                    aria-label={$i18n.t('common.navigation.more')}
                    on:click={() => (menuOpen = !menuOpen)}
                >
                    <span class="gamified-icon icon-dots" aria-hidden="true"></span>
                </button>
                {#if menuOpen}
                    <div class="header__overflow-dropdown" id="header-overflow-dropdown" role="menu">
                        <div class="header__overflow-item" role="presentation" on:click={closeMenu}>
                            <LocaleSwitcher compact={true} />
                        </div>
                    </div>
                {/if}
            </div>
            <button class="btn btn--secondary btn--small header__install hidden" id="pwa-install-btn" type="button">
                <span class="gamified-icon icon-link" aria-hidden="true"></span>
                <span>{$i18n.t('common.actions.install')}</span>
            </button>
            {#if isAdmin}
            <button class="btn btn--secondary btn--small header__profile" id="header-profile-btn" type="button"
                on:click={openSettings}>
                <span class="gamified-icon icon-profile" aria-hidden="true"></span>
                <span class="header__profile-label">{$i18n.t('app.shell.profile')}</span>
            </button>
            {/if}
        </div>
    </div>
    <div class="header__status-row">
        <div class="header__install-hint hidden" id="pwa-install-ios-hint" aria-live="polite"></div>
        <div class="header__balance" tabindex="0" role="button" aria-label={$i18n.t('app.shell.openHistory')}
            on:click={openHistory} on:keydown={handleBalanceKeydown}>
            <div class="header__balance-main" title={$i18n.t('app.shell.balanceTitle')}>
                <span class="balance__coin gamified-icon icon-coin-stack" aria-hidden="true"></span>
                <span class="balance__value" id="balance">{$i18n.formatNumber(balance)}</span>
            </div>
            <div class="header__earned" aria-live="polite" title={$i18n.t('app.shell.historyCountTitle')}>
                <span class="header__earned-main">
                    <span class="gamified-icon icon-history-menu" aria-hidden="true"></span>
                    <span id="header-earned-count">{$i18n.formatNumber(historyCount)}</span>
                </span>
                {#if showLimitNote}
                <span class="header__earned-limit" id="header-earned-limit-note"
                    title={$i18n.t('app.shell.dailyLimitTitle')}
                >{resolvedLimitNote}</span>
                {/if}
            </div>
            <span class="header__balance-delta hidden" id="header-balance-delta" aria-live="polite"></span>
            {#if isAdmin}
            <button class="btn btn--success btn--small admin-only" type="button" title={$i18n.t('app.shell.awardCoinsTitle')}
                on:click|stopPropagation={handleAwardCoins}>+</button>
            {/if}
        </div>
    </div>
</header>
