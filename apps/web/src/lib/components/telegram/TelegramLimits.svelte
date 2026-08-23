<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import type { Child } from '$lib/stores/app';
    import { adminSaveLimits } from '$lib/services/api';
    import { refreshData } from '$lib/services/bootstrap';
    import TelegramIcon from './TelegramIcon.svelte';
    import { MAX_CHILD_LIMIT as MAX_LIMIT, stepLimit, effectiveLimit } from './telegramLimits';

    export let open = false;
    export let child: Child | null = null;
    export let onClose: () => void = () => {};
    export let onSaved: () => void = () => {};

    const i18n = useI18n();

    let earningEnabled = false;
    let earningMax = 0;
    let rewardEnabled = false;
    let rewardMax = 0;
    let busy = false;
    let error = '';
    let saved = false;

    $: if (open && child) {
        earningEnabled = (child.dailyCoinLimit ?? 0) > 0;
        earningMax = child.dailyCoinLimit ?? 0;
        rewardEnabled = (child.dailyRewardLimit ?? 0) > 0;
        rewardMax = child.dailyRewardLimit ?? 0;
        error = '';
        saved = false;
    }

    function stepEarning(delta: number) {
        earningMax = stepLimit(earningMax, delta);
    }

    function stepReward(delta: number) {
        rewardMax = stepLimit(rewardMax, delta);
    }

    async function save() {
        if (!child) return;
        busy = true;
        error = '';
        saved = false;
        const ok = await adminSaveLimits(child.id, {
            name: child.nickname,
            dailyCoinLimit: effectiveLimit(earningEnabled, earningMax),
            monthlyLimit: child.monthlyLimit ?? 10000,
            dailyRewardLimit: effectiveLimit(rewardEnabled, rewardMax),
        });
        busy = false;
        if (ok) {
            await refreshData();
            saved = true;
            onSaved();
        } else {
            error = $i18n.t('app.telegram.limits.error');
        }
    }
</script>

{#if open && child}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="limits-title" tabindex="-1">
        <h2 id="limits-title">{$i18n.t('app.telegram.limits.title')}</h2>

        <div class="summary">
            <span class="setting-icon"><TelegramIcon name="gauge" size={20} label={$i18n.t('app.telegram.limits.title')} /></span>
            <span class="grow"><span class="setting-title">{$i18n.t('app.telegram.limits.summaryTitle', { name: child.nickname })}</span>
                <span class="setting-meta">{$i18n.t('app.telegram.limits.earningMax', { max: earningMax })}</span>
                <span class="setting-meta">{$i18n.t('app.telegram.limits.rewardsMax', { max: rewardMax })}</span>
            </span>
        </div>

        <div class="block">
            <div class="row"><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.limits.restrictEarning')}</span><span class="setting-meta">{$i18n.t('app.telegram.limits.restrictEarningMeta')}</span></span><button class="switch" class:on={earningEnabled} type="button" role="switch" aria-checked={earningEnabled} aria-label={$i18n.t('app.telegram.limits.restrictEarning')} on:click={() => earningEnabled = !earningEnabled}></button></div>
            {#if earningEnabled}
                <p class="field-label">{$i18n.t('app.telegram.limits.maximum')}</p>
                <div class="stepper">
                    <button type="button" aria-label="-5" on:click={() => stepEarning(-5)}>-5</button>
                    <button type="button" aria-label="-1" on:click={() => stepEarning(-1)}>-1</button>
                    <input class="stepper-value" type="number" inputmode="numeric" min="0" max={MAX_LIMIT} aria-label={$i18n.t('app.telegram.limits.earningMax', { max: '' })} bind:value={earningMax} />
                    <button type="button" aria-label="+1" on:click={() => stepEarning(1)}>+1</button>
                    <button type="button" aria-label="+5" on:click={() => stepEarning(5)}>+5</button>
                </div>
            {/if}
        </div>

        <div class="block">
            <div class="row"><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.limits.restrictRewards')}</span><span class="setting-meta">{$i18n.t('app.telegram.limits.restrictRewardsMeta')}</span></span><button class="switch" class:on={rewardEnabled} type="button" role="switch" aria-checked={rewardEnabled} aria-label={$i18n.t('app.telegram.limits.restrictRewards')} on:click={() => rewardEnabled = !rewardEnabled}></button></div>
            {#if rewardEnabled}
                <p class="field-label">{$i18n.t('app.telegram.limits.maximum')}</p>
                <div class="stepper">
                    <button type="button" aria-label="-5" on:click={() => stepReward(-5)}>-5</button>
                    <button type="button" aria-label="-1" on:click={() => stepReward(-1)}>-1</button>
                    <input class="stepper-value" type="number" inputmode="numeric" min="0" max={MAX_LIMIT} aria-label={$i18n.t('app.telegram.limits.rewardsMax', { max: '' })} bind:value={rewardMax} />
                    <button type="button" aria-label="+1" on:click={() => stepReward(1)}>+1</button>
                    <button type="button" aria-label="+5" on:click={() => stepReward(5)}>+5</button>
                </div>
            {/if}
        </div>

        {#if error}<p class="error" role="alert">{error}</p>{/if}
        {#if saved}<p class="saved" role="status">{$i18n.t('app.telegram.limits.saved')}</p>{/if}

        <button class="primary" type="button" disabled={busy} on:click={save}>{$i18n.t('app.telegram.limits.save')}</button>
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.emailSettings.cancel')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    @media (min-width: 700px) { .sheet { inset:50% auto auto 50%; width:min(38rem,calc(100% - 3rem)); max-height:min(82dvh,46rem); overflow-y:auto; padding:1.4rem; border-radius:1.25rem; box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%); transform:translate(-50%,-50%); } }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .summary { display:flex; align-items:center; gap:.6rem; padding:.6rem; border:1px solid #e6e9f0; border-radius:.8rem; background:#f8f9fc; }
    .grow { flex:1; min-width:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-title { display:block; font-weight:600; }
    .setting-meta { display:block; margin-top:.1rem; color:#66718a; font-size:.78rem; }
    .block { margin-top:.7rem; padding:.6rem; border:1px solid #e6e9f0; border-radius:.8rem; }
    .row { display:flex; align-items:center; gap:.6rem; }
    .field-label { margin:.7rem 0 .3rem; color:#4d5870; font-size:.8rem; font-weight:600; }
    .switch { width:2.6rem; height:1.5rem; flex:0 0 auto; padding:0; border:0; border-radius:999px; background:#d8dce5; cursor:pointer; position:relative; }
    .switch.on { background:#3867d6; }
    .switch:after { content:""; position:absolute; width:1.1rem; height:1.1rem; top:.2rem; left:.2rem; border-radius:50%; background:#fff; transition:left .15s ease; }
    .switch.on:after { left:1.3rem; }
    .stepper { display:grid; grid-template-columns:repeat(5, minmax(0,1fr)); gap:.4rem; }
    .stepper button { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; font-weight:700; cursor:pointer; }
    .stepper-value { min-height:2.75rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#18243d; font:inherit; font-weight:700; text-align:center; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .saved { margin:.6rem 0 0; color:#17884b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .primary:disabled { cursor:wait; opacity:.6; }
    .close { width:100%; min-height:2.75rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
