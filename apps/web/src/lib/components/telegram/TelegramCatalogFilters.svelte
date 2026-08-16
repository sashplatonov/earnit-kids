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
            <div class="pills">
                <button type="button" class="pill" class:on={draft.age === '6-8'} on:click={() => setAge('6-8')}>{$i18n.t('app.telegram.readyCatalog.age6_8')}</button>
                <button type="button" class="pill" class:on={draft.age === '9-11'} on:click={() => setAge('9-11')}>{$i18n.t('app.telegram.readyCatalog.age9_11')}</button>
                <button type="button" class="pill" class:on={draft.age === '12-14'} on:click={() => setAge('12-14')}>{$i18n.t('app.telegram.readyCatalog.age12_14')}</button>
            </div>
            <button class="reset" type="button" on:click={reset}>{$i18n.t('app.telegram.readyCatalog.reset')}</button>
        {:else}
            <div class="section">
                <h3>{$i18n.t('app.telegram.readyCatalog.difficulty')}</h3>
                <div class="pills">
                    <button type="button" class="pill" class:on={draft.difficulty === 'simple'} on:click={() => setDifficulty('simple')}>{$i18n.t('app.telegram.readyCatalog.difficultySimple')}</button>
                    <button type="button" class="pill" class:on={draft.difficulty === 'normal'} on:click={() => setDifficulty('normal')}>{$i18n.t('app.telegram.readyCatalog.difficultyNormal')}</button>
                    <button type="button" class="pill" class:on={draft.difficulty === 'advanced'} on:click={() => setDifficulty('advanced')}>{$i18n.t('app.telegram.readyCatalog.difficultyAdvanced')}</button>
                </div>
            </div>

            <div class="section">
                <h3>{$i18n.t('app.telegram.readyCatalog.frequency')}</h3>
                <div class="pills">
                    <button type="button" class="pill" class:on={draft.frequency === 'daily'} on:click={() => setFrequency('daily')}>{$i18n.t('app.telegram.readyCatalog.frequencyDaily')}</button>
                    <button type="button" class="pill" class:on={draft.frequency === 'weekly'} on:click={() => setFrequency('weekly')}>{$i18n.t('app.telegram.readyCatalog.frequencyWeekly')}</button>
                    <button type="button" class="pill" class:on={draft.frequency === 'unlimited'} on:click={() => setFrequency('unlimited')}>{$i18n.t('app.telegram.readyCatalog.frequencyUnlimited')}</button>
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
    .pills { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:.5rem; }
    .pill { min-height:2.5rem; padding:0 .4rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#566176; font:inherit; font-weight:700; font-size:.82rem; cursor:pointer; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .pill.on { color:#2854ba; border-color:#c4c8ff; background:#f7f7ff; }
    .reset { width:100%; min-height:2.5rem; margin-top:.5rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.5rem; margin-top:.75rem; }
    .primary { min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
