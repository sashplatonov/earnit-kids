<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminAwardCoins } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { showToast } from '$lib/stores/toasts';

    export let balance: number = 0;
    export let earnedCount: number = 0;
    export let earnedLimitNote: string = 'Лимит: ∞';
    export let isAdmin: boolean = false;
    export let childNickname: string = '';

    async function handleAwardCoins() {
        const currentChildId = $appStore.currentChildId;
        if (!currentChildId) { showToast('Сначала выберите ребенка', 'error'); return; }
        const amountStr = prompt('Количество монет (можно отрицательное):');
        if (amountStr === null) return;
        const amount = parseInt(amountStr);
        if (!amount) { showToast('Некорректная сумма', 'error'); return; }
        const description = prompt('Описание:', amount > 0 ? 'Бонус от родителей' : 'Списано родителями')?.trim()
            || (amount > 0 ? 'Начисление' : 'Списание');
        const result = await adminAwardCoins(currentChildId, amount, description);
        if (result) {
            applyDataSnapshot(result as Record<string, unknown>);
            showToast(amount > 0 ? `+${amount} монет начислено` : `${amount} монет списано`, 'success');
        } else {
            showToast('Ошибка начисления', 'error');
        }
    }
</script>

<header class="header" class:header--admin={isAdmin}>
    <div class="header__system-row">
        <div class="header__logo">
            <span class="header__icon gamified-icon icon-coin-stack" aria-hidden="true"></span>
            <div class="header__titles">
                <h1>EarnIt Kids{#if isAdmin}<span class="header__admin-badge admin-only">Родитель</span>{/if}</h1>
                {#if childNickname}
                    <div id="child-nickname-display" class="header__child-nickname">{childNickname}</div>
                {/if}
            </div>
        </div>
        <div class="header__actions">
            <button class="btn btn--secondary btn--small header__install hidden" id="pwa-install-btn" type="button">
                <span class="gamified-icon icon-link" aria-hidden="true"></span>
                <span>Установить</span>
            </button>
            <button class="btn btn--secondary btn--small header__profile" id="header-profile-btn" type="button">
                <span class="gamified-icon icon-profile" aria-hidden="true"></span>
                <span class="header__profile-label">Профиль</span>
            </button>
        </div>
    </div>
    <div class="header__status-row">
        <div class="header__install-hint hidden" id="pwa-install-ios-hint" aria-live="polite"></div>
        <div class="header__balance" tabindex="0" role="button" aria-label="Открыть историю">
            <div class="header__balance-main">
                <span class="balance__coin gamified-icon icon-coin-stack" aria-hidden="true"></span>
                <span class="balance__value" id="balance">{balance}</span>
            </div>
            <div class="header__earned" aria-live="polite">
                <span class="header__earned-main">
                    <span class="gamified-icon icon-trophy" aria-hidden="true"></span>
                    <span id="header-earned-count">{earnedCount}</span>
                </span>
                <span class="header__earned-limit" id="header-earned-limit-note">{earnedLimitNote}</span>
            </div>
            <span class="header__balance-delta hidden" id="header-balance-delta" aria-live="polite"></span>
            {#if isAdmin}
            <button class="btn btn--success btn--small admin-only" type="button" title="Начислить монеты"
                on:click={handleAwardCoins}>+</button>
            {/if}
        </div>
    </div>
</header>
