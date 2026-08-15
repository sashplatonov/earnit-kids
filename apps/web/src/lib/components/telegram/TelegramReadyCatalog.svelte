<script lang="ts">
    import { createEventDispatcher } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type CatalogRewardTemplate, type CatalogTaskTemplate } from '$lib/stores/app';
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
    } from '$lib/services/catalogFilter';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramCatalogFilters from './TelegramCatalogFilters.svelte';

    export let kind: 'task' | 'reward' = 'task';

    const i18n = useI18n();
    const dispatch = createEventDispatcher<{
        add: { template: CatalogTaskTemplate | CatalogRewardTemplate; groupName: string | null };
        addMany: { templates: Array<CatalogTaskTemplate | CatalogRewardTemplate>; groupName: string | null };
        openDetails: { template: CatalogTaskTemplate | CatalogRewardTemplate };
    }>();

    $: templates = kind === 'task'
        ? ($appStore.catalog.tasks as CatalogTaskTemplate[])
        : ($appStore.catalog.rewards as CatalogRewardTemplate[]);
    $: familyItems = kind === 'task' ? $appStore.tasks : $appStore.shopItems;
    $: groups = catalogGroups(templates as Array<{ groupName?: string }>);

    let query = '';
    let filters: CatalogFilters = { ...EMPTY_FILTERS };
    let selectedGroup = '';
    let filterMode: 'age' | 'filters' | null = null;
    let bulkMode = false;
    let selectedIds: string[] = [];

    $: filtered = filterCatalog(templates, filters, query)
        .filter((item) => !selectedGroup || item.groupName === selectedGroup);
    $: nonAgeCount = nonAgeFilterCount(filters);
    $: ageLabel = filters.age === '6-8'
        ? $i18n.t('app.telegram.readyCatalog.age6_8')
        : filters.age === '9-11'
            ? $i18n.t('app.telegram.readyCatalog.age9_11')
            : filters.age === '12-14'
                ? $i18n.t('app.telegram.readyCatalog.age12_14')
                : $i18n.t('app.telegram.readyCatalog.age');
    $: filtersLabel = nonAgeCount > 0
        ? $i18n.t('app.telegram.readyCatalog.filtersCount', { count: nonAgeCount })
        : $i18n.t('app.telegram.readyCatalog.filters');

    function isAdded(template: { id: string; title: string }): boolean {
        return isAlreadyAdded(template, familyItems);
    }
    function toggleSelect(id: string) {
        selectedIds = selectedIds.includes(id)
            ? selectedIds.filter((entry) => entry !== id)
            : [...selectedIds, id];
    }
    function addOne(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        dispatch('add', { template, groupName: null });
    }
    function addSelected() {
        const selected = filtered.filter((item) => selectedIds.includes(item.id));
        dispatch('addMany', { templates: selected, groupName: null });
    }
    function openDetails(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        dispatch('openDetails', { template });
    }
    function amountLabel(template: CatalogTaskTemplate | CatalogRewardTemplate): string {
        const amount = kind === 'task' ? (template as CatalogTaskTemplate).coins : (template as CatalogRewardTemplate).price;
        return $i18n.t('app.telegram.readyCatalog.coins', { count: amount });
    }
    function freqLabel(template: CatalogTaskTemplate | CatalogRewardTemplate): string {
        return formatFrequency(template.frequencyLimit, template.frequencyPeriod);
    }
</script>

<div class="catalog">
    <div class="page-header">
        <div>
            <h1 id="catalog-title">{kind === 'task' ? $i18n.t('app.telegram.readyCatalog.taskTitle') : $i18n.t('app.telegram.readyCatalog.rewardTitle')}</h1>
            <p class="desc">{kind === 'task' ? $i18n.t('app.telegram.readyCatalog.taskDescription') : $i18n.t('app.telegram.readyCatalog.rewardDescription')}</p>
        </div>
        <button class="bulk-toggle" type="button" on:click={() => { bulkMode = !bulkMode; selectedIds = []; }}>
            {bulkMode ? $i18n.t('app.telegram.readyCatalog.done') : $i18n.t('app.telegram.readyCatalog.selectSeveral')}
        </button>
    </div>

    <div class="search">
        <TelegramIcon name="search" size={18} label={$i18n.t('app.telegram.readyCatalog.search')} />
        <input type="search" bind:value={query} placeholder={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.searchTasks') : $i18n.t('app.telegram.readyCatalog.searchRewards')} aria-label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.searchTasks') : $i18n.t('app.telegram.readyCatalog.searchRewards')} />
    </div>

    <div class="filterbar">
        <button class="filter-btn" class:active={filters.age != null} type="button" on:click={() => filterMode = 'age'}>
            <TelegramIcon name="child" size={18} label={$i18n.t('app.telegram.readyCatalog.age')} />
            <span>{ageLabel}</span>
        </button>
        <button class="filter-btn" class:active={nonAgeCount > 0} type="button" on:click={() => filterMode = 'filters'}>
            <TelegramIcon name="filter" size={18} label={$i18n.t('app.telegram.readyCatalog.filters')} />
            <span>{filtersLabel}</span>
        </button>
    </div>

    {#if !filtered.length}
        <div class="empty">
            <p class="empty-title">{$i18n.t('app.telegram.readyCatalog.noResults')}</p>
            <p class="empty-hint">{$i18n.t('app.telegram.readyCatalog.noResultsHint')}</p>
            <button class="reset" type="button" on:click={() => { query = ''; filters = { ...EMPTY_FILTERS }; selectedGroup = ''; }}>{$i18n.t('app.telegram.readyCatalog.reset')}</button>
        </div>
    {:else}
        <div class="list" aria-label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.catalogTasks') : $i18n.t('app.telegram.readyCatalog.catalogRewards')}>
            {#each filtered as template (template.id)}
                <div class="row">
                    {#if bulkMode}
                        <button class="check" class:on={selectedIds.includes(template.id)} type="button" aria-pressed={selectedIds.includes(template.id)} aria-label={stripEmoji(template.title)} on:click={() => toggleSelect(template.id)}>
                            {#if selectedIds.includes(template.id)}<TelegramIcon name="check" size={16} label={stripEmoji(template.title)} />{/if}
                        </button>
                    {/if}
                    <button class="row-main" type="button" aria-label={stripEmoji(template.title)} on:click={() => openDetails(template)}>
                        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind, title: template.title, group: template.groupName, semantic: template.semanticGraphicKey ?? null })} size={20} label={kind === 'task' ? $i18n.t('app.telegram.readyCatalog.catalogTasks') : $i18n.t('app.telegram.readyCatalog.catalogRewards')} /></span>
                        <span class="entity-text">
                            <span class="title">{template.title}</span>
                            <span class="meta">{amountLabel(template)} · {stripEmoji(template.groupName || '')}</span>
                            <span class="meta">{freqLabel(template)}</span>
                        </span>
                    </button>
                    {#if !bulkMode}
                        {#if isAdded(template)}
                            <span class="added"><TelegramIcon name="check" size={16} label={$i18n.t('app.telegram.readyCatalog.added')} /><span>{$i18n.t('app.telegram.readyCatalog.added')}</span></span>
                        {:else}
                            <button class="add" type="button" on:click={() => addOne(template)}><TelegramIcon name="add" size={16} label={$i18n.t('app.telegram.readyCatalog.add')} /><span>{$i18n.t('app.telegram.readyCatalog.add')}</span></button>
                        {/if}
                    {/if}
                </div>
            {/each}
        </div>
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

<TelegramGroupSubnav
    {groups}
    selected={selectedGroup}
    kind={kind === 'task' ? 'tasks' : 'shop'}
    allLabel={$i18n.t('app.telegram.groupSubnav.all')}
    moreLabel={$i18n.t('app.telegram.groupSubnav.more')}
    allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')}
    onSelect={(group) => selectedGroup = group}
/>

<TelegramCatalogFilters open={filterMode != null} mode={filterMode ?? 'age'} {filters} onApply={(next) => filters = next} onClose={() => filterMode = null} />

<style>
    .catalog { width:100%; }
    .page-header { display:flex; align-items:flex-start; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { margin:0; color:#18243d; font-size:1.35rem; }
    .desc { margin:.15rem 0 0; color:#66718a; font-size:.85rem; }
    .bulk-toggle { min-height:2.5rem; padding:.35rem .6rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; white-space:nowrap; }
    .search { display:flex; align-items:center; gap:.5rem; min-height:2.75rem; padding:0 .7rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; margin-bottom:.5rem; }
    .search input { flex:1; min-width:0; border:0; outline:0; background:transparent; color:#18243d; font:inherit; }
    .filterbar { display:grid; grid-template-columns:1fr 1fr; gap:.5rem; margin-bottom:.6rem; }
    .filter-btn { display:flex; align-items:center; gap:.4rem; min-height:2.5rem; padding:0 .6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#566176; font:inherit; font-weight:700; font-size:.85rem; cursor:pointer; min-width:0; }
    .filter-btn span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .filter-btn.active { color:#2854ba; border-color:#c4c8ff; background:#f7f7ff; }
    .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.4rem; min-height:3.5rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row-main { display:flex; align-items:center; gap:.6rem; flex:1; min-width:0; min-height:3.5rem; padding:.3rem 0; border:0; background:transparent; text-align:left; cursor:pointer; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; }
    .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; }
    .meta { display:block; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .add { display:inline-flex; align-items:center; gap:.3rem; min-height:2.5rem; padding:.35rem .6rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; white-space:nowrap; }
    .added { display:inline-flex; align-items:center; gap:.3rem; min-height:2.5rem; padding:.35rem .6rem; border:1px solid #cbe8d7; border-radius:.7rem; background:#eaf7ef; color:#168552; font:inherit; font-weight:700; white-space:nowrap; }
    .check { width:2.25rem; height:2.25rem; flex:0 0 auto; display:grid; place-items:center; border:1.5px solid #b9c1cf; border-radius:.5rem; background:#fff; color:#fff; cursor:pointer; }
    .check.on { background:#3867d6; border-color:#3867d6; }
    .empty { padding:2rem 1rem; text-align:center; }
    .empty-title { margin:0; color:#18243d; font-weight:700; }
    .empty-hint { margin:.3rem 0 0; color:#66718a; font-size:.85rem; }
    .reset { min-height:2.5rem; margin-top:.75rem; padding:0 .9rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .bulkbar { position:fixed; left:.5rem; right:.5rem; bottom:5.5rem; z-index:25; display:flex; align-items:center; gap:.6rem; padding:.5rem .6rem; border-radius:.9rem; background:#172033; color:#fff; box-shadow:0 .5rem 1.5rem rgb(24 36 61 / 25%); }
    .bulkbar .grow { flex:1; min-width:0; font-size:.85rem; }
    .bulk-add { min-height:2.5rem; padding:0 .7rem; border:0; border-radius:.6rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; white-space:nowrap; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
