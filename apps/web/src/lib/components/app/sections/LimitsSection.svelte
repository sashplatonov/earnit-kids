<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { adminSaveLimits } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tAdmin(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.${key}` as MessageKey, variables);
    }

    $: isAdmin = $appStore.isAdmin;
    $: currentChildId = $appStore.currentChildId;

    let monthlyLimit = $appStore.monthlyLimit;
    let dailyCoinLimit = $appStore.dailyCoinLimit;

    $: { monthlyLimit = $appStore.monthlyLimit; dailyCoinLimit = $appStore.dailyCoinLimit; }

    async function saveLimits() {
        const ok = await adminSaveLimits(currentChildId, { monthlyLimit, dailyCoinLimit });
        if (ok) {
            appStore.setState({ monthlyLimit, dailyCoinLimit });
            showToast(tAdmin('limits.savedToast'), 'success');
        }
    }
</script>

<section class="section" id="limits-section">
    <div class="section__header">
        <h2>{tAdmin('limits.title')}</h2>
        <p class="section__subtitle">{tAdmin('limits.subtitle')}</p>
    </div>

    <div class="cards" id="limits-cards" style="grid-template-columns: 1fr;">
        <div class="card admin-only" style="max-width: 600px; margin: 0 auto;">
            <div class="card__header">
                <h3 class="card__title">{tAdmin('limits.cardTitle')}</h3>
                <div class="card__icon">
                    <span class="gamified-icon icon-chart" aria-hidden="true"></span>
                </div>
            </div>

            <div class="form-group" style="margin-top: 1rem;">
                <label for="settings-child-monthly-limit-inline">{tAdmin('limits.monthlyLabel')}</label>
                <input type="number" class="input" id="settings-child-monthly-limit-inline"
                    min="0" placeholder={tAdmin('limits.monthlyPlaceholder')}
                    bind:value={monthlyLimit} />
                <p class="hint">{tAdmin('limits.monthlyHint')}</p>
            </div>

            <div class="form-group">
                <label for="settings-child-day-coin-limit-inline">{tAdmin('limits.dailyLabel')}</label>
                <input type="number" class="input" id="settings-child-day-coin-limit-inline"
                    min="0" placeholder={tAdmin('limits.dailyPlaceholder')}
                    bind:value={dailyCoinLimit} />
                <p class="hint">{tAdmin('limits.dailyHint')}</p>
            </div>

            <div class="card__actions">
                <button class="btn btn--primary btn--small" id="settings-save-limits-btn"
                    on:click={saveLimits}>
                    {tAdmin('limits.save')}
                </button>
            </div>
        </div>
    </div>
</section>
