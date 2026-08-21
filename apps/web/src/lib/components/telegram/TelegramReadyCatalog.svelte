<script lang="ts">
    import { createEventDispatcher, onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type CatalogTaskTemplate } from '$lib/stores/app';
    import type { CatalogRewardTemplate } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { catalogRewards } from '$lib/telegram/stores/rewards';
    import { scheduleSave } from '$lib/services/save';
    import {
        EMPTY_FILTERS,
        catalogGroups,
        filterCatalog,
        formatFrequency,
        isAlreadyAdded,
        nonAgeFilterCount,
        stripEmoji,
        type CatalogFilters,
    } from '$lib/telegram/services/catalogFilter';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import { recordReadyCatalogEvent } from '$lib/telegram/services/readyCatalogTelemetry';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramCatalogFilters from './TelegramCatalogFilters.svelte';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';

    export let kind: 'task' | 'reward' = 'task';

    const i18n = useI18n();
    const dispatch = createEventDispatcher<{
        add: { template: CatalogTaskTemplate | CatalogRewardTemplate; groupName: string | null };
        addMany: { templates: Array<CatalogTaskTemplate | CatalogRewardTemplate>; groupName: string | null };
        openDetails: { template: CatalogTaskTemplate | CatalogRewardTemplate };
    }>();

    $: templates = kind === 'task'
        ? ($appStore.catalog.tasks as CatalogTaskTemplate[])
        : ($catalogRewards as CatalogRewardTemplate[]);
    $: familyItems = kind === 'task' ? $appStore.tasks : $shopItems;
    $: groups = catalogGroups(templates as Array<{ groupName?: string }>);

    let query = '';
    let filters: CatalogFilters = { ...EMPTY_FILTERS };
    let selectedGroup = '';
    let filterOpen = false;
    let bulkMode = false;
    let selectedIds: string[] = [];

    $: filtered = filterCatalog(templates, filters, query)
        .filter((item) => !selectedGroup || item.groupName === selectedGroup);
    $: activeFilterCount = nonAgeFilterCount(filters) + (filters.age != null ? 1 : 0);
    $: filtersLabel = activeFilterCount > 0
        ? $i18n.t('app.telegram.readyCatalog.filtersCount', { count: activeFilterCount })
        : $i18n.t('app.telegram.readyCatalog.filters');

    function isAdded(template: { id: string; title: string }): boolean {
        return isAlreadyAdded(template, familyItems);
    }
    function track(name: 'catalog_opened' | 'catalog_search_used' | 'catalog_filter_selected' | 'catalog_item_added' | 'catalog_bulk_add' | 'catalog_duplicate_skipped' | 'catalog_details_opened', extra: Record<string, unknown> = {}) {
        recordReadyCatalogEvent({ name, type: kind === 'task' ? 'TASK' : 'REWARD', ...extra });
    }
    function toggleSelect(id: string) {
        selectedIds = selectedIds.includes(id)
            ? selectedIds.filter((entry) => entry !== id)
            : [...selectedIds, id];
    }
    function addOne(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        track('catalog_item_added', { catalogGroupKey: template.groupKey });
        dispatch('add', { template, groupName: null });
    }
    function addSelected() {
        const selected = filtered.filter((item) => selectedIds.includes(item.id));
        track('catalog_bulk_add', { bulkCount: selected.length });
        dispatch('addMany', { templates: selected, groupName: null });
        selectedIds = [];
        bulkMode = false;
    }
    function openDetails(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        track('catalog_details_opened', { catalogGroupKey: template.groupKey });
        dispatch('openDetails', { template });
    }
    function amountLabel(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        const amount = kind === 'task' ? (template as CatalogTaskTemplate).coins : (template as CatalogRewardTemplate).price;
        return { amount, coins: amount };
    }
    function freqLabel(template: CatalogTaskTemplate | CatalogRewardTemplate): string {
        return formatFrequency(template.frequencyLimit, template.frequencyPeriod);
    }

    onMount(() => {
        recordReadyCatalogEvent({ name: 'catalog_opened', type: kind === 'task' ? 'TASK' : 'REWARD' });
    });
</script>

<div class="catalog">
    <div class="action-row">
        <button class="bulk-toggle" type="button" aria-pressed={bulkMode} on:click={() => { bulkMode = !bulkMode; selectedIds = []; }}>
            {#if bulkMode}
                <TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.readyCatalog.done')} />
            {:else}
                <TelegramIcon name="selectSeveral" size={18} label={$i18n.t('app.telegram.readyCatalog.selectSeveral')} />
            {/if}
        </button>
        <button class="filter-btn" class:active={activeFilterCount > 0} type="button" on:click={() => filterOpen = true}>
            <TelegramIcon name="filter" size={18} label={$i18n.t('app.telegram.readyCatalog.filters')} />
            <span>{filtersLabel}</span>
        </button>
        <div class="search">
            <TelegramIcon name="search" size={18} label={$i18n.t('app.telegram.readyCatalog.search')} />
            <input type="search" bind:value={query} on:input={() => track('catalog_search_used')} placeholder={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.searchTasks') : $i18n.t('app.telegram.readyCatalog.searchRewards')} aria-label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.searchTasks') : $i18n.t('app.telegram.readyCatalog.searchRewards')} />
        </div>
    </div>

    <TelegramGroupSubnav
        {groups}
        selected={selectedGroup}
        kind={kind === 'task' ? 'tasks' : 'shop'}
        allLabel={$i18n.t('app.telegram.groupSubnav.all')}
        moreLabel={$i18n.t('app.telegram.groupSubnav.more')}
        allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')}
        onSelect={(group) => selectedGroup = group}
    />

    {#if !filtered.length}
        <div class="empty">
            <p class="empty-title">{$i18n.t('app.telegram.readyCatalog.noResults')}</p>
            <p class="empty-hint">{$i18n.t('app.telegram.readyCatalog.noResultsHint')}</p>
            <button class="reset" type="button" on:click={() => { query = ''; filters = { ...EMPTY_FILTERS }; selectedGroup = ''; }}>{$i18n.t('app.telegram.readyCatalog.reset')}</button>
        </div>
    {:else}
        <TelegramListSurface label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.catalogTasks') : $i18n.t('app.telegram.readyCatalog.catalogRewards')}>
            {#each filtered as template (template.id)}
                <TelegramEntityRow interactive hasSelection={bulkMode}>
                    <svelte:fragment slot="selection">
                        {#if bulkMode}
                            <label class="check-wrap">
                            <input class="check" type="checkbox" checked={selectedIds.includes(template.id)} aria-label={stripEmoji(template.title)} on:change={() => toggleSelect(template.id)} />
                            </label>
                        {/if}
                    </svelte:fragment>
                    <span slot="icon"><TelegramIcon name={getTelegramEntityIcon({ kind, title: template.title, group: template.groupName, semantic: template.semanticGraphicKey ?? null })} size={20} label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.catalogTasks') : $i18n.t('app.telegram.readyCatalog.catalogRewards')} /></span>
                    <button slot="title" class="row-main" type="button" aria-label={stripEmoji(template.title)} on:click={() => openDetails(template)}>
                        <span class="title">{template.title}</span>
                        <span class="row-metadata">
                            <span class="meta"><TelegramCoin size={13} /><span>{amountLabel(template).amount}</span> · <span>{stripEmoji(template.groupName || '')}</span></span>
                            <span class="meta">{freqLabel(template)}</span>
                        </span>
                    </button>
                    <svelte:fragment slot="interactive">
                        {#if !bulkMode}
                            {#if isAdded(template)}
                                <button class="row-action added" type="button" disabled><TelegramIcon name="check" size={16} label={$i18n.t('app.telegram.readyCatalog.added')} /></button>
                            {:else}
                                <button class="row-action add" type="button" aria-label={$i18n.t('app.telegram.readyCatalog.add')} on:click={() => addOne(template)}><TelegramIcon name="add" size={16} label={$i18n.t('app.telegram.readyCatalog.add')} /></button>
                            {/if}
                        {/if}
                    </svelte:fragment>
                </TelegramEntityRow>
            {/each}
        </TelegramListSurface>
    {/if}

    {#if bulkMode && selectedIds.length > 0}
        <div class="bulkbar">
            <span class="grow">{$i18n.t('app.telegram.readyCatalog.selected', { count: selectedIds.length })}</span>
            <button class="bulk-add" type="button" on:click={addSelected}>
                {kind === 'task'
                    ? $i18n.t('app.telegram.readyCatalog.addSelectedTasks', { count: selectedIds.length })
                    : $i18n.t('app.telegram.readyCatalog.addSelectedRewards', { count: selectedIds.length })}
            </button>
        </div>
    {/if}
</div>

<TelegramCatalogFilters open={filterOpen} {filters} onApply={(next) => { filters = next; track('catalog_filter_selected'); }} onClose={() => filterOpen = false} />

<style>
    .catalog { width:100%; }
    .action-row { display:flex; align-items:center; gap:.4rem; margin-bottom:.4rem; }
    .bulk-toggle { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border:0; border-radius:.5rem; background:transparent; color:#3867d6; cursor:pointer; }
    .bulk-toggle:hover { background:#f2f5ff; }
    .search { display:flex; align-items:center; gap:.45rem; flex:1; min-width:0; min-height:2.25rem; padding:0 .55rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; }
    .search input { flex:1; min-width:0; border:0; outline:0; background:transparent; color:#18243d; font:inherit; font-size:.85rem; }
    .filter-btn { display:flex; align-items:center; justify-content:center; gap:.35rem; min-height:2rem; padding:0 .5rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#566176; font:inherit; font-weight:700; font-size:.75rem; cursor:pointer; min-width:0; }
    .filter-btn span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .filter-btn.active { color:#2854ba; border-color:#c4c8ff; background:#f7f7ff; }
    .check-wrap { width:2.75rem; min-height:2.75rem; flex:0 0 auto; display:flex; align-items:center; justify-content:center; cursor:pointer; }
    .check { appearance:none; -webkit-appearance:none; width:1.375rem; height:1.375rem; margin:0; flex:0 0 auto; border:1.5px solid #b9c1cf; border-radius:.4rem; background:#fff; display:grid; place-content:center; cursor:pointer; }
    .check::before { content:""; width:.5rem; height:.3rem; border-left:2px solid #fff; border-bottom:2px solid #fff; transform:rotate(-45deg) scale(0); transform-origin:center; }
    .check:checked { background:#3867d6; border-color:#3867d6; }
    .check:checked::before { transform:rotate(-45deg) scale(1); }
    .check:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .add { border:1px solid #3867d6; background:#3867d6; color:#fff; }
    .added { border:1px solid #cbe8d7; background:#eaf7ef; color:#168552; cursor:default; }
    .empty { padding:2rem 1rem; text-align:center; }
    .empty-title { margin:0; color:#18243d; font-weight:700; }
    .empty-hint { margin:.3rem 0 0; color:#66718a; font-size:.85rem; }
    .reset { min-height:2.5rem; margin-top:.75rem; padding:0 .9rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .bulkbar { position:fixed; left:.5rem; right:.5rem; bottom:5.5rem; z-index:25; display:flex; align-items:center; gap:.6rem; padding:.5rem .6rem; border-radius:.9rem; background:#172033; color:#fff; box-shadow:0 .5rem 1.5rem rgb(24 36 61 / 25%); }
    .bulkbar .grow { flex:1; min-width:0; font-size:.85rem; }
    .bulk-add { min-height:2.5rem; padding:0 .7rem; border:0; border-radius:.6rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; white-space:nowrap; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
