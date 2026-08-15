<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type CatalogRewardTemplate, type CatalogTaskTemplate } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { mapGroupKeyToFamily, templateToReward, templateToTask } from '$lib/services/catalogFilter';
    import { recordReadyCatalogEvent } from '$lib/services/readyCatalogTelemetry';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramReadyCatalog from './TelegramReadyCatalog.svelte';
    import TelegramCatalogDetails from './TelegramCatalogDetails.svelte';
    import TelegramCatalogGroupMap from './TelegramCatalogGroupMap.svelte';

    export let kind: 'task' | 'reward' = 'task';
    export let onBack: () => void = () => {};

    const i18n = useI18n();

    $: familyItems = kind === 'task' ? $appStore.tasks : $appStore.shopItems;
    $: familyGroups = [...new Set(familyItems.map((item) => item.groupName).filter((group): group is string => Boolean(group)))];

    let detailsOpen = false;
    let detailsTemplate: CatalogTaskTemplate | CatalogRewardTemplate | null = null;
    let groupMapOpen = false;
    let pendingTemplates: Array<CatalogTaskTemplate | CatalogRewardTemplate> = [];
    let pendingGroupName: string | null = null;
    let bulkSummaryOpen = false;
    let bulkSummary: { willAdd: number; already: number; groups: Record<string, number> } | null = null;

    function openDetails(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        detailsTemplate = template;
        detailsOpen = true;
    }

    function addOne(template: CatalogTaskTemplate | CatalogRewardTemplate) {
        const mapped = mapGroupKeyToFamily(template.groupKey, familyGroups);
        if (mapped) {
            commitAdd([template], mapped);
        } else {
            pendingTemplates = [template];
            pendingGroupName = template.groupName ?? null;
            groupMapOpen = true;
        }
    }

    function addMany(templates: Array<CatalogTaskTemplate | CatalogRewardTemplate>) {
        const mapped = mapGroupKeyToFamily(templates[0]?.groupKey, familyGroups);
        if (mapped) {
            commitAdd(templates, mapped);
        } else {
            pendingTemplates = templates;
            pendingGroupName = templates[0]?.groupName ?? null;
            groupMapOpen = true;
        }
    }

    function chooseGroup(groupName: string | null) {
        groupMapOpen = false;
        commitAdd(pendingTemplates, groupName);
    }

    function commitAdd(templates: Array<CatalogTaskTemplate | CatalogRewardTemplate>, groupName: string | null) {
        const existing = new Set(
            familyItems.map((item) => String((item as { sourceCatalogItemId?: string | null }).sourceCatalogItemId ?? ''))
        );
        const fresh = templates.filter((template) => !existing.has(template.id));
        const already = templates.length - fresh.length;

        if (fresh.length === 0) {
            // Everything already added — nothing to do.
            recordReadyCatalogEvent({ name: 'catalog_duplicate_skipped', type: kind === 'task' ? 'TASK' : 'REWARD', bulkCount: templates.length });
            return;
        }

        if (fresh.length === 1) {
            // Single add: no confirmation.
            applyAdd(fresh, groupName);
            return;
        }

        // Bulk add: show summary.
        const groups: Record<string, number> = {};
        for (const template of fresh) {
            const name = groupName ?? template.groupName ?? '';
            groups[name] = (groups[name] ?? 0) + 1;
        }
        bulkSummary = { willAdd: fresh.length, already, groups };
        bulkSummaryOpen = true;
        pendingTemplates = fresh;
        pendingGroupName = groupName;
    }

    function applyAdd(templates: Array<CatalogTaskTemplate | CatalogRewardTemplate>, groupName: string | null) {
        if (kind === 'task') {
            const newTasks = templates.map((template) => templateToTask(template as CatalogTaskTemplate, groupName));
            appStore.setState({ tasks: [...$appStore.tasks, ...newTasks] });
        } else {
            const newItems = templates.map((template) => templateToReward(template as CatalogRewardTemplate, groupName));
            appStore.setState({ shopItems: [...$appStore.shopItems, ...newItems] });
        }
        void scheduleSave();
        detailsOpen = false;
        bulkSummaryOpen = false;
    }

    function confirmBulk() {
        applyAdd(pendingTemplates, pendingGroupName);
    }

    function back() {
        detailsOpen = false;
        groupMapOpen = false;
        bulkSummaryOpen = false;
        onBack();
    }
</script>

<div class="catalog-screen">
    <button class="back" type="button" on:click={back}>
        <TelegramIcon name="back" size={18} label={$i18n.t('app.telegram.readyCatalog.back')} />
        {kind === 'task' ? $i18n.t('app.telegram.readyCatalog.myTasks') : $i18n.t('app.telegram.readyCatalog.myRewards')}
    </button>

    <TelegramReadyCatalog
        {kind}
        on:add={(event) => addOne(event.detail.template)}
        on:addMany={(event) => addMany(event.detail.templates)}
        on:openDetails={(event) => openDetails(event.detail.template)}
    />
</div>

<TelegramCatalogDetails open={detailsOpen} {kind} template={detailsTemplate} onAdd={(template) => addOne(template)} onClose={() => detailsOpen = false} />

<TelegramCatalogGroupMap open={groupMapOpen} groupName={pendingGroupName} {familyGroups} onChoose={chooseGroup} onClose={() => groupMapOpen = false} />

{#if bulkSummaryOpen && bulkSummary}
    <div class="sheet-backdrop" role="presentation" on:click={() => bulkSummaryOpen = false}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="bulk-summary-title" tabindex="-1">
        <h2 id="bulk-summary-title">
            {$i18n.t('app.telegram.readyCatalog.bulkTitle', {
                count: bulkSummary.willAdd,
                items: kind === 'task' ? $i18n.t('app.telegram.readyCatalog.itemsTaskGen') : $i18n.t('app.telegram.readyCatalog.itemsRewardGen'),
            })}
        </h2>
        <div class="summary">
            <p>{$i18n.t('app.telegram.readyCatalog.bulkWillAdd', { count: bulkSummary.willAdd })}</p>
            {#if bulkSummary.already > 0}
                <p>{$i18n.t('app.telegram.readyCatalog.bulkAlready', { count: bulkSummary.already })}</p>
            {/if}
        </div>
        <h3>{$i18n.t('app.telegram.readyCatalog.bulkGroups')}</h3>
        <div class="groups">
            {#each Object.entries(bulkSummary.groups) as [group, count] (group)}
                <p>{group || $i18n.t('app.telegram.readyCatalog.withoutGroup')} · {count}</p>
            {/each}
        </div>
        <button class="primary" type="button" on:click={confirmBulk}>
            {$i18n.t('app.telegram.readyCatalog.bulkAdd', { count: bulkSummary.willAdd })}
        </button>
        <button class="close" type="button" on:click={() => bulkSummaryOpen = false}>{$i18n.t('app.telegram.readyCatalog.back')}</button>
    </div>
{/if}

<style>
    .catalog-screen { width:100%; }
    .back { display:inline-flex; align-items:center; gap:.3rem; min-height:2.5rem; padding:.35rem .5rem; border:0; border-radius:.6rem; background:transparent; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; margin-bottom:.4rem; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .5rem; color:#18243d; font-size:1.15rem; }
    h3 { margin:.75rem 0 .3rem; color:#33415f; font-size:.85rem; font-weight:700; }
    .summary p, .groups p { margin:.2rem 0; color:#33415f; }
    .primary { width:100%; min-height:2.75rem; margin-top:.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
