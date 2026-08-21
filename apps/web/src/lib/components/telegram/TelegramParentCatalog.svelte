<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { appStore, type CatalogTaskTemplate, type Child } from '$lib/stores/app';
    import type { CatalogRewardTemplate } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { catalogRewards } from '$lib/telegram/stores/rewards';
    import { scheduleSave } from '$lib/services/save';
    import { mapGroupKeyToFamily, templateToReward, templateToTask } from '$lib/telegram/services/catalogFilter';
    import { applyGroupOrderToChildren, getEffectiveGroupOrder, type GroupOrderSection } from '$lib/telegram/services/groupOrder';
    import { recordReadyCatalogEvent } from '$lib/telegram/services/readyCatalogTelemetry';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramReadyCatalog from './TelegramReadyCatalog.svelte';
    import TelegramCatalogDetails from './TelegramCatalogDetails.svelte';
    import TelegramCatalogGroupMap from './TelegramCatalogGroupMap.svelte';

    export let kind: 'task' | 'reward' = 'task';
    export let onBack: () => void = () => {};

    const i18n = useI18n();

    $: isAdmin = $appStore.isAdmin;
    $: resolvedChildId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
    $: currentChild = ($appStore.children.find((child) => String(child.id) === String(resolvedChildId))
        ?? $appStore.children[0]
        ?? null) as Child | null;

    $: familyItems = kind === 'task' ? $appStore.tasks : $shopItems;
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
        const section: GroupOrderSection = kind === 'task' ? 'tasks' : 'shop';
        const nextItems = kind === 'task'
            ? templates.map((template) => templateToTask(template as CatalogTaskTemplate, groupName))
            : templates.map((template) => templateToReward(template as CatalogRewardTemplate, groupName));

        const patch: Partial<import('$lib/stores/app').AppState> = { children: $appStore.children };
        if (kind === 'task') {
            patch.tasks = [...$appStore.tasks, ...(nextItems as import('$lib/stores/app').Task[])];
        } else {
            // shopItems are now managed in specialized stores, but we maintain patch for any generic state updates if needed
            // However, since we've decoupled, we primarily update the specialized store
            shopItems.update((items) => [...items, ...(nextItems as import('$lib/telegram/stores/types').ShopItem[])]);
            catalogRewards.update((rewards) => [...rewards, ...(templates as CatalogRewardTemplate[])]);
        }

        if (groupName && currentChild && resolvedChildId && !getEffectiveGroupOrder(currentChild, section, isAdmin).includes(groupName)) {
            const nextOrder = [...getEffectiveGroupOrder(currentChild, section, isAdmin), groupName];
            patch.children = applyGroupOrderToChildren($appStore.children, resolvedChildId, section, isAdmin, nextOrder);
        }

        appStore.setState(patch);
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
        <div class="header">
            <button class="back" type="button" aria-label={$i18n.t('app.telegram.readyCatalog.back')} on:click={back}>
                <TelegramIcon name="back" size={18} label={$i18n.t('app.telegram.readyCatalog.back')} />
            </button>
            <div class="title-block">
                <h1 id="catalog-title">{kind === 'task' ? $i18n.t('app.telegram.readyCatalog.taskTitle') : $i18n.t('app.telegram.readyCatalog.rewardTitle')}</h1>
                <p class="desc">{kind === 'task' ? $i18n.t('app.telegram.readyCatalog.taskDescription') : $i18n.t('app.telegram.readyCatalog.rewardDescription')}</p>
            </div>
        </div>
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
    .header { display:flex; align-items:center; gap:.5rem; margin-bottom:.4rem; padding:.55rem .35rem; border:1px solid #e3e9f8; border-radius:.7rem; background:#fff; }
    .back { display:inline-flex; align-items:center; justify-content:center; gap:.3rem; min-width:2.25rem; min-height:2.25rem; padding:.3rem .4rem; border:0; border-radius:.6rem; background:transparent; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; flex:0 0 auto; }
    .title-block { flex:1; min-width:0; }
    .title-block h1 { margin:0; color:#18243d; font-size:1.05rem; line-height:1.2; }
    .title-block .desc { margin:.05rem 0 0; color:#8a93a8; font-size:.72rem; line-height:1.2; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .5rem; color:#18243d; font-size:1.15rem; }
    h3 { margin:.75rem 0 .3rem; color:#33415f; font-size:.85rem; font-weight:700; }
    .summary p, .groups p { margin:.2rem 0; color:#33415f; }
    .primary { width:100%; min-height:2.75rem; margin-top:.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
