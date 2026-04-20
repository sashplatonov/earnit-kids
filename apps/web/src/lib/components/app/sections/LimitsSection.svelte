<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminSaveLimits } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    $: isAdmin = $appStore.isAdmin;
    $: currentChildId = $appStore.currentChildId;

    let monthlyLimit = $appStore.monthlyLimit;
    let dailyCoinLimit = $appStore.dailyCoinLimit;

    $: { monthlyLimit = $appStore.monthlyLimit; dailyCoinLimit = $appStore.dailyCoinLimit; }

    async function saveLimits() {
        const ok = await adminSaveLimits(currentChildId, { monthlyLimit, dailyCoinLimit });
        if (ok) {
            appStore.setState({ monthlyLimit, dailyCoinLimit });
            showToast('Лимиты сохранены', 'success');
        }
    }
</script>

<section class="section" id="limits-section">
    <div class="section__header">
        <h2>Лимиты</h2>
        <p class="section__subtitle">Деньги и монеты под контролем — изменения мгновенно применяются ко всем детям.</p>
    </div>

    <div class="cards" id="limits-cards" style="grid-template-columns: 1fr;">
        <div class="card admin-only" style="max-width: 600px; margin: 0 auto;">
            <div class="card__header">
                <h3 class="card__title">Лимиты</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-chart" aria-hidden="true"></span>
                </div>
            </div>

            <div class="form-group" style="margin-top: 1rem;">
                <label for="settings-child-monthly-limit-inline">Деньги — лимит трат в месяц</label>
                <input type="number" class="input" id="settings-child-monthly-limit-inline"
                    min="0" placeholder="10000"
                    bind:value={monthlyLimit} />
                <p class="hint">Контролируйте расход на любые покупки.</p>
            </div>

            <div class="form-group">
                <label for="settings-child-day-coin-limit-inline">Монеты — лимит заработка в день</label>
                <input type="number" class="input" id="settings-child-day-coin-limit-inline"
                    min="0" placeholder="0 (Без лимита)"
                    bind:value={dailyCoinLimit} />
                <p class="hint">Оставьте пустым, чтобы не ограничивать.</p>
            </div>

            <div class="card__actions">
                <button class="btn btn--primary btn--small" id="settings-save-limits-btn"
                    on:click={saveLimits}>
                    Сохранить лимиты
                </button>
            </div>
        </div>
    </div>
</section>
