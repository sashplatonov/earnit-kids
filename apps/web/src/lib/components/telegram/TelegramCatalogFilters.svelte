<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';
    import type { CatalogFilters, AgeFilter, DifficultyFilter, FrequencyFilter } from '$lib/services/catalogFilter';

    export let open = false;
    export let mode: 'age' | 'filters' = 'age';
    export let filters: CatalogFilters;
    export let onApply: (filters: CatalogFilters) => void = () => {};
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let draft: CatalogFilters = { ...filters };

    $: if (open) draft = { ...filters };

    function setAge(age: AgeFilter) {
        draft = { ...draft, age };
    }
    function setDifficulty(difficulty: DifficultyFilter) {
        draft = { ...draft, difficulty };
    }
    function setFrequency(frequency: FrequencyFilter) {
        draft = { ...draft, frequency };
    }
    function setPurchase(purchase: CatalogFilters['purchase']) {
        draft = { ...draft, purchase };
    }
    function reset() {
        draft = { age: null, difficulty: null, frequency: null, purchase: null };
    }
    function apply() {
        onApply(draft);
        onClose();
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="catalog-filter-title" tabindex="-1">
        <h2 id="catalog-filter-title">{mode === 'age' ? $i18n.t('app.telegram.readyCatalog.age') : $i18n.t('app.telegram.readyCatalog.filters')}</h2>

        {#if mode === 'age'}
            <div class="options">
                <button type="button" class="option" class:active={draft.age === '6-8'} on:click={() => setAge('6-8')}>
                    <span class="gico"><TelegramIcon name="child" size={20} label={$i18n.t('app.telegram.readyCatalog.age6_8')} /></span>
                    <span class="grow">{$i18n.t('app.telegram.readyCatalog.age6_8')}</span>
                    {#if draft.age === '6-8'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.age6_8')} />{/if}
                </button>
                <button type="button" class="option" class:active={draft.age === '9-11'} on:click={() => setAge('9-11')}>
                    <span class="gico"><TelegramIcon name="users" size={20} label={$i18n.t('app.telegram.readyCatalog.age9_11')} /></span>
                    <span class="grow">{$i18n.t('app.telegram.readyCatalog.age9_11')}</span>
                    {#if draft.age === '9-11'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.age9_11')} />{/if}
                </button>
                <button type="button" class="option" class:active={draft.age === '12-14'} on:click={() => setAge('12-14')}>
                    <span class="gico"><TelegramIcon name="child" size={20} label={$i18n.t('app.telegram.readyCatalog.age12_14')} /></span>
                    <span class="grow">{$i18n.t('app.telegram.readyCatalog.age12_14')}</span>
                    {#if draft.age === '12-14'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.age12_14')} />{/if}
                </button>
            </div>
            <button class="reset" type="button" on:click={reset}>{$i18n.t('app.telegram.readyCatalog.reset')}</button>
        {:else}
            <div class="section">
                <h3>{$i18n.t('app.telegram.readyCatalog.difficulty')}</h3>
                <div class="options">
                    <button type="button" class="option" class:active={draft.difficulty === 'simple'} on:click={() => setDifficulty('simple')}>
                        <span class="gico"><TelegramIcon name="sparkles" size={20} label={$i18n.t('app.telegram.readyCatalog.difficultySimple')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.difficultySimple')}</span>
                        {#if draft.difficulty === 'simple'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.difficultySimple')} />{/if}
                    </button>
                    <button type="button" class="option" class:active={draft.difficulty === 'normal'} on:click={() => setDifficulty('normal')}>
                        <span class="gico"><TelegramIcon name="target" size={20} label={$i18n.t('app.telegram.readyCatalog.difficultyNormal')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.difficultyNormal')}</span>
                        {#if draft.difficulty === 'normal'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.difficultyNormal')} />{/if}
                    </button>
                    <button type="button" class="option" class:active={draft.difficulty === 'advanced'} on:click={() => setDifficulty('advanced')}>
                        <span class="gico"><TelegramIcon name="trophy" size={20} label={$i18n.t('app.telegram.readyCatalog.difficultyAdvanced')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.difficultyAdvanced')}</span>
                        {#if draft.difficulty === 'advanced'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.difficultyAdvanced')} />{/if}
                    </button>
                </div>
            </div>

            <div class="section">
                <h3>{$i18n.t('app.telegram.readyCatalog.frequency')}</h3>
                <div class="options">
                    <button type="button" class="option" class:active={draft.frequency === 'daily'} on:click={() => setFrequency('daily')}>
                        <span class="gico"><TelegramIcon name="sun" size={20} label={$i18n.t('app.telegram.readyCatalog.frequencyDaily')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.frequencyDaily')}</span>
                        {#if draft.frequency === 'daily'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.frequencyDaily')} />{/if}
                    </button>
                    <button type="button" class="option" class:active={draft.frequency === 'weekly'} on:click={() => setFrequency('weekly')}>
                        <span class="gico"><TelegramIcon name="calendar" size={20} label={$i18n.t('app.telegram.readyCatalog.frequencyWeekly')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.frequencyWeekly')}</span>
                        {#if draft.frequency === 'weekly'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.frequencyWeekly')} />{/if}
                    </button>
                    <button type="button" class="option" class:active={draft.frequency === 'unlimited'} on:click={() => setFrequency('unlimited')}>
                        <span class="gico"><TelegramIcon name="infinity" size={20} label={$i18n.t('app.telegram.readyCatalog.frequencyUnlimited')} /></span>
                        <span class="grow">{$i18n.t('app.telegram.readyCatalog.frequencyUnlimited')}</span>
                        {#if draft.frequency === 'unlimited'}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.frequencyUnlimited')} />{/if}
                    </button>
                </div>
            </div>

            <div class="actions">
                <button class="primary" type="button" on:click={apply}>{$i18n.t('app.telegram.readyCatalog.apply')}</button>
                <button class="reset" type="button" on:click={reset}>{$i18n.t('app.telegram.readyCatalog.reset')}</button>
            </div>
        {/if}

        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.readyCatalog.done')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); max-height:80vh; overflow-y:auto; }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    h3 { margin:1rem 0 .4rem; color:#33415f; font-size:.85rem; font-weight:700; }
    .options { display:grid; gap:.25rem; }
    .option { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-bottom:1px solid #edf0f5; border-radius:0; background:#fff; color:#18243d; font:inherit; font-weight:600; text-align:left; cursor:pointer; }
    .option:last-child { border-bottom:0; }
    .option.active { color:#2854ba; }
    .gico { display:grid; place-items:center; width:2rem; height:2rem; flex:0 0 auto; border-radius:.55rem; background:#eef0ff; color:#5b63e9; }
    .grow { flex:1; min-width:0; }
    .reset { width:100%; min-height:2.5rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.5rem; margin-top:.75rem; }
    .primary { min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
