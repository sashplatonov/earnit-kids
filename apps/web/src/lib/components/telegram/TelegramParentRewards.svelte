<script lang="ts">
    import { stopPropagation } from 'svelte/legacy';

    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import type { ShopItem } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { useRewardActions } from '$lib/telegram/services/rewardActions';
    import { confirmAction } from '$lib/services/confirm';
    import { orderGroups } from '$lib/telegram/services/groupOrder';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRewardForm from './TelegramRewardForm.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramGroupManager from './TelegramGroupManager.svelte';
    import TelegramParentCatalog from './TelegramParentCatalog.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import TelegramBottomSheet from './ui/TelegramBottomSheet.svelte';
    import TelegramCatalogToolbar from './TelegramCatalogToolbar.svelte';
    import { sortCatalogItems, type CatalogSortMode } from '$lib/telegram/services/catalogSort';

    const i18n = useI18n();
    const rewardActions = useRewardActions();

    let rawGroups = $derived([...new Set($shopItems.map((item) => item.groupName).filter((group): group is string => Boolean(group)))]);
    let currentChild = $derived($appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null);
    let groups = $derived(orderGroups(rawGroups, currentChild?.shopGroupOrder));
    let hiddenGroups = $derived(currentChild?.hiddenShopGroupOrder ?? []);
    let selectedGroup = $state('');
    let sortMode: CatalogSortMode = $state('group');
    let catalogOpen = $state(false);
    let filteredItems = $derived(sortCatalogItems(selectedGroup
        ? $shopItems.filter((item) => item.groupName === selectedGroup)
        : $shopItems, sortMode, groups, (item) => item.groupName ?? '', (item) => item.price));
    let canEdit = $derived($appStore.permission !== 'viewer');
    let groupMessage = $state('');
    let groupEditorOpen = $state(false);
    let groupSaving = false;
    let openMenuId: string | number | null = $state(null);
    let menuTrigger: HTMLButtonElement | null = null;
    let formOpen = $state(false);
    let editingItem: ShopItem | null = $state(null);
    function toggleMenu(id: string | number, button: HTMLButtonElement) {
        if (openMenuId === id) closeMenu(true);
        else { menuTrigger = button; openMenuId = id; }
    }
    function closeMenu(restoreFocus = false) {
        openMenuId = null;
        if (restoreFocus && menuTrigger?.isConnected) menuTrigger.focus();
        menuTrigger = null;
    }
    function handleWindowKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && openMenuId != null) {
            event.preventDefault();
            closeMenu(true);
        }
    }
    function add() { editingItem = null; formOpen = true; }
    function edit(item: unknown) { closeMenu(); editingItem = item as ShopItem; formOpen = true; }
    async function toggleArchive(item: ShopItem) {
        closeMenu(true);
        await rewardActions.archiveReward(item);
    }
    async function remove(item: ShopItem) {
        closeMenu();
        const confirmed = await confirmAction({
            title: $i18n.t('app.telegram.rewards.deleteTitle'),
            description: $i18n.t('app.telegram.tasks.deleteDescription', { name: stripLeadingEmoji(item.name) }),
            confirmLabel: $i18n.t('app.telegram.tasks.delete'),
            cancelLabel: $i18n.t('app.telegram.tasks.cancel'),
            tone: 'danger',
        });
        if (!confirmed) return;
        await rewardActions.deleteReward(item);
    }
    // EXPLAIN: Parent directly grants a reward to the current child, spending
    // EXPLAIN: coins without a child request. Reuses buyItem (POST /purchase).
    let grantingId: string | number | null = $state(null);
    let grantError = $state('');
    let confirmGrant: ShopItem | null = $state(null);
    async function grantToChild(item: ShopItem) {
        if ($appStore.currentChildId == null || grantingId != null) return;
        closeMenu();
        confirmGrant = null;
        grantingId = item.id;
        grantError = '';
        if (($appStore.balance ?? 0) < (item.price ?? 0)) {
            grantingId = null;
            grantError = $i18n.t('app.telegram.rewards.notEnoughCoins');
            return;
        }
        const result = await rewardActions.buy({ itemId: item.id, childId: $appStore.currentChildId }) as Record<string, unknown> | null;
        grantingId = null;
        if (result) {
            rewardActions.applySnapshot(result);
        } else {
            grantError = $i18n.t('app.telegram.rewards.grantError');
            await rewardActions.refresh();
        }
    }
    async function saveGroups(event: { groups: string[]; hiddenGroups: string[] }) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await rewardActions.saveGroups($appStore.currentChildId, event.groups, event.hiddenGroups);
        groupSaving = false;
        groupMessage = result.ok ? $i18n.t('app.telegram.tasks.groupsSaved') : $i18n.t('app.telegram.tasks.groupsSaveError');
        if (result.ok) {
            groupEditorOpen = false;
            appStore.setState({
                children: $appStore.children.map((child) =>
                    String(child.id) === String($appStore.currentChildId)
                        ? { ...child, shopGroupOrder: event.groups, hiddenShopGroupOrder: event.hiddenGroups }
                        : child
                ),
            });
        }
    }
    async function handleDeleteGroup(event: { group: string; moveTo: string | null }) {
        const { group, moveTo } = event;
        await rewardActions.deleteGroup(group, moveTo);
        const nextGroups = groups.filter((g) => g !== group);
        const nextHidden = hiddenGroups.filter((g) => g !== group);
        await saveGroups({ groups: nextGroups, hiddenGroups: nextHidden });
    }
</script>

<svelte:window onclick={() => openMenuId = null} onkeydown={handleWindowKeydown} />

<div class="rewards">
    <div class="page-header">
        <h1 id="rewards-title">{$i18n.t('app.telegram.rewards.title')}</h1>
        <div class="header-actions">
            {#if canEdit}<button class="add" type="button" aria-label={$i18n.t('app.telegram.rewards.addReward')} onclick={add}><TelegramIcon name="add" size={18} label={$i18n.t('app.telegram.rewards.addReward')} /></button>{/if}
            <button class="catalog" type="button" onclick={() => catalogOpen = true}><TelegramIcon name="gift" size={18} label={$i18n.t('app.telegram.readyCatalog.catalogRewards')} /><span>{$i18n.t('app.telegram.tasks.catalogShort')}</span></button>
        </div>
    </div>

    {#if catalogOpen}
        <TelegramParentCatalog kind="reward" onBack={() => catalogOpen = false} />
    {:else if !$shopItems.length}
        <p class="muted">{$i18n.t('app.telegram.rewards.noRewards')}</p>
    {:else}
        <TelegramGroupSubnav
            {groups}
            selected={selectedGroup}
            kind="shop"
            allLabel={$i18n.t('app.telegram.groupSubnav.all')}
            moreLabel={$i18n.t('app.telegram.groupSubnav.more')}
            allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')}
            onSelect={(group) => selectedGroup = group}
        />
        <TelegramCatalogToolbar count={filteredItems.length} countLabel={$i18n.t('app.telegram.sort.rewardsShown')} mode={sortMode} onChange={(mode) => sortMode = mode} />
        {#if selectedGroup && !filteredItems.length}
            <p class="muted empty-group">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>
        {:else}
        <TelegramListSurface label={$i18n.t('app.telegram.rewards.title')}>
            {#each filteredItems as item (item.id)}
                <TelegramEntityRow isInteractive={canEdit} archived={item.isActive === false} compact>
                    {#snippet icon()}
                                                        <span ><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName, semantic: item.icon ?? null })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span>
                                                    {/snippet}
                    {#snippet title()}
                                                        <button  class="row-main" type="button" aria-label={$i18n.t('app.telegram.tasks.editItem', { name: stripLeadingEmoji(item.name) })} onclick={() => edit(item)}>
                            <span class="title">{stripLeadingEmoji(item.name)}</span>
                            <span class="row-metadata"><span class="meta"><TelegramCoin size={13} />{item.price} · {item.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span>{#if item.lastPurchasedAt}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.lastUsed', { when: formatLastUsedTime(item.lastPurchasedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.neverUsed')}</span>{/if}</span>
                        </button>
                                                    {/snippet}
                    {#snippet interactive()}

                        {#if canEdit}
                            <button class="row-action grant" type="button" disabled={item.isActive === false || grantingId != null} onclick={stopPropagation(() => confirmGrant = item)}><TelegramIcon name="award" size={16} label={$i18n.t('app.telegram.rewards.grantShort')} /></button>
                            <div class="menu-wrap">
                                <button class="row-action more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(item.name) })} aria-haspopup="menu" aria-expanded={openMenuId === item.id} onclick={stopPropagation((event) => toggleMenu(item.id, event.currentTarget as HTMLButtonElement))}><TelegramIcon name="more" size={20} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                                {#if openMenuId === item.id}
                                    <div class="menu" role="menu" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(item.name) })}>
                                        <button role="menuitem" type="button" onclick={() => edit(item)}><TelegramIcon name="edit" size={16} label={$i18n.t('app.telegram.tasks.edit')} /><span>{$i18n.t('app.telegram.tasks.edit')}</span></button>
                                        <button role="menuitem" type="button" onclick={() => toggleArchive(item)}><TelegramIcon name="archive" size={16} label={item.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')} /><span>{item.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')}</span></button>
                                        <div class="menu-divider" role="presentation"></div>
                                        <button role="menuitem" class="danger" type="button" onclick={() => void remove(item)}><TelegramIcon name="delete" size={16} label={$i18n.t('app.telegram.tasks.delete')} /><span>{$i18n.t('app.telegram.tasks.delete')}</span></button>
                                    </div>
                                {/if}
                            </div>
                        {/if}

                                                    {/snippet}
                </TelegramEntityRow>
            {/each}
        </TelegramListSurface>
        {/if}
    {/if}

    {#if !catalogOpen && canEdit}
        <button class="groups" type="button" onclick={() => groupEditorOpen = true}>
            <TelegramIcon name="filter" size={16} label={$i18n.t('app.telegram.tasks.manageGroups')} />
            <span>{$i18n.t('app.telegram.tasks.manageGroups')}</span>
        </button>
        {#if groupMessage}<span role="status" class="group-message">{groupMessage}</span>{/if}
    {/if}
    {#if grantError}<p class="error" role="alert">{grantError}</p>{/if}
</div>
<TelegramRewardForm open={formOpen} item={editingItem} groupSuggestions={groups} onClose={() => formOpen = false} />
<TelegramGroupManager open={groupEditorOpen} kind="shop" onClose={() => groupEditorOpen = false} onsave={saveGroups} ondeleteGroup={handleDeleteGroup} />

{#if confirmGrant}
    <TelegramBottomSheet open labelledBy="reward-grant-title" busy={grantingId != null} onClose={() => confirmGrant = null}>
        <h2 id="reward-grant-title">{$i18n.t('app.telegram.rewards.grantShort')}</h2>
        <div class="grant-row">
            <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: confirmGrant.name, group: confirmGrant.groupName, semantic: confirmGrant.icon ?? null })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span>
            <span class="grow"><span class="title">{stripLeadingEmoji(confirmGrant.name)}</span><span class="meta"><TelegramCoin size={13} />{confirmGrant.price} · {confirmGrant.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span></span>
        </div>
        <div class="delta"><span>{$i18n.t('app.telegram.rewards.grantChild')}</span><b>{$appStore.childNickname || $i18n.t('app.telegram.header.child')}</b></div>
        <div class="delta"><span>{$i18n.t('app.telegram.rewards.grantBalance')}</span><b>{$appStore.balance} → {Math.max(0, ($appStore.balance ?? 0) - (confirmGrant.price ?? 0))}</b></div>
        <div class="actions">
            <button class="cancel" type="button" onclick={() => confirmGrant = null}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.rewards.cancel')} />{$i18n.t('app.telegram.rewards.cancel')}</button>
            <button class="primary" type="button" disabled={grantingId != null} onclick={() => confirmGrant && void grantToChild(confirmGrant)}><TelegramIcon name="award" size={16} label={$i18n.t('app.telegram.rewards.grantFor', { amount: confirmGrant.price })} />{$i18n.t('app.telegram.rewards.grantFor', { amount: confirmGrant.price })}</button>
        </div>
    </TelegramBottomSheet>
{/if}

<style>
    .rewards { box-sizing:border-box; width:100%; max-width:100%; min-width:0; }
    .page-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { min-width:0; margin:0; color:#18243d; font-size:1.35rem; }
    .header-actions { display:flex; align-items:center; gap:.25rem; min-width:0; flex-shrink:1; }
    .add { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .catalog { display:inline-flex; align-items:center; gap:.35rem; min-width:0; min-height:2.75rem; padding:.45rem .65rem; overflow:hidden; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .catalog span { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .empty-group { padding:1rem 0; text-align:center; }

    .more { color:#66718a; }
    .grant { border:1px solid #3867d6; border-radius:.5rem; background:#3867d6; color:#fff; }
    .grant:disabled { opacity:.5; cursor:not-allowed; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:11rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .menu button:disabled { opacity:.5; cursor:not-allowed; }
    .menu-divider { height:1px; margin:.25rem 0; background:#edf0f5; }
    .grant-row { display:flex; align-items:center; gap:.6rem; padding:.4rem 0; }
    .grant-row .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .grant-row .grow { flex:1; min-width:0; }
    .grant-row .title { display:block; color:#18243d; font-weight:600; font-size:.95rem; line-height:1.3; }
    .grant-row .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .delta { display:flex; align-items:center; justify-content:space-between; gap:.6rem; margin-top:.5rem; padding:.6rem .7rem; border-radius:.6rem; background:#f4f6f9; color:#33415f; font-size:.9rem; }
    .delta b { font-weight:700; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.9rem; }
    .actions .cancel { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions .primary { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .actions .primary:disabled { cursor:wait; opacity:.6; }
    .muted { color:#66718a; }
    .error { margin:.75rem 0 0; padding:.6rem .75rem; border-radius:.75rem; background:#fff0f0; color:#a33b3b; font-size:.875rem; }
    button.groups { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; color:#18243d; font:inherit; font-weight:700; cursor:pointer; }
    button.groups span { display:inline-flex; align-items:center; }
    .group-message { display:block; margin-top:.4rem; text-align:center; color:#66718a; font-size:.85rem; }
</style>
