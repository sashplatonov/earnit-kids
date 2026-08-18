<script lang="ts">
    import { appStore, type ShopItem } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { buyItem, saveChildGroupOrder } from '$lib/telegram/services/shopApi';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { confirmAction } from '$lib/services/confirm';
    import { scheduleSave } from '$lib/services/save';
    import { orderGroups } from '$lib/telegram/services/groupOrder';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRewardForm from './TelegramRewardForm.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import TelegramGroupManager from './TelegramGroupManager.svelte';
    import TelegramParentCatalog from './TelegramParentCatalog.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';

    const i18n = useI18n();

    $: rawGroups = [...new Set($shopItems.map((item) => item.groupName).filter((group): group is string => Boolean(group)))];
    $: currentChild = $appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null;
    $: groups = orderGroups(rawGroups, currentChild?.shopGroupOrder);
    $: hiddenGroups = currentChild?.hiddenShopGroupOrder ?? [];
    let selectedGroup = '';
    let catalogOpen = false;
    $: filteredItems = selectedGroup
        ? $shopItems.filter((item) => item.groupName === selectedGroup)
        : $shopItems;
    $: canEdit = $appStore.permission !== 'viewer';
    let groupMessage = '';
    let groupEditorOpen = false;
    let groupSaving = false;
    let openMenuId: string | number | null = null;
    let menuTrigger: HTMLButtonElement | null = null;
    let formOpen = false;
    let editingItem: ShopItem | null = null;
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
    function toggleArchive(item: ShopItem) {
        closeMenu(true);
        const nextActive = item.isActive === false;
        const nextItems = $shopItems.map((entry) => entry.id == item.id ? ({ ...entry, isActive: nextActive } as typeof entry) : entry);
        appStore.setState({
            shopItems: nextItems,
        });
        shopItems.set(nextItems);
        void scheduleSave();
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
        const nextItems = $shopItems.filter((entry) => entry.id != item.id);
        appStore.setState({ shopItems: nextItems });
        shopItems.set(nextItems);
        void scheduleSave();
    }
    // EXPLAIN: Parent directly grants a reward to the current child, spending
    // EXPLAIN: coins without a child request. Reuses buyItem (POST /purchase).
    let grantingId: string | number | null = null;
    let grantError = '';
    let confirmGrant: ShopItem | null = null;
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
        const result = await buyItem(item.id, $appStore.currentChildId) as Record<string, unknown> | null;
        grantingId = null;
        if (result) {
            applyDataSnapshot(result);
        } else {
            grantError = $i18n.t('app.telegram.rewards.grantError');
            await refreshData();
        }
    }
    async function saveGroups(event: CustomEvent<{ groups: string[]; hiddenGroups: string[] }>) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await saveChildGroupOrder($appStore.currentChildId, 'shop', event.detail.groups, event.detail.hiddenGroups);
        groupSaving = false;
        groupMessage = result.ok ? $i18n.t('app.telegram.tasks.groupsSaved') : $i18n.t('app.telegram.tasks.groupsSaveError');
        if (result.ok) {
            groupEditorOpen = false;
            appStore.setState({
                children: $appStore.children.map((child) =>
                    String(child.id) === String($appStore.currentChildId)
                        ? { ...child, shopGroupOrder: event.detail.groups, hiddenShopGroupOrder: event.detail.hiddenGroups }
                        : child
                ),
            });
        }
    }
    function handleDeleteGroup(event: CustomEvent<{ group: string; moveTo: string | null }>) {
        const { group, moveTo } = event.detail;
        const nextItems = $shopItems.map((item) =>
            item.groupName === group ? { ...item, groupName: moveTo ?? null } as typeof item : item
        );
        appStore.setState({ shopItems: nextItems });
        shopItems.set(nextItems);
        void scheduleSave();
        const nextGroups = groups.filter((g) => g !== group);
        const nextHidden = hiddenGroups.filter((g) => g !== group);
        void saveGroups(new CustomEvent('save', { detail: { groups: nextGroups, hiddenGroups: nextHidden } }));
    }
</script>

<svelte:window on:click={() => openMenuId = null} on:keydown={handleWindowKeydown} />

<div class="rewards">
    <div class="page-header">
        <h1 id="rewards-title">{$i18n.t('app.telegram.rewards.title')}</h1>
        <div class="header-actions">
            {#if canEdit}<button class="add" type="button" aria-label={$i18n.t('app.telegram.rewards.addReward')} on:click={add}><TelegramIcon name="add" size={18} label={$i18n.t('app.telegram.rewards.addReward')} /></button>{/if}
            <button class="catalog" type="button" on:click={() => catalogOpen = true}><TelegramIcon name="gift" size={18} label={$i18n.t('app.telegram.readyCatalog.catalogRewards')} /><span>{$i18n.t('app.telegram.tasks.catalogShort')}</span></button>
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
        {#if selectedGroup && !filteredItems.length}
            <p class="muted empty-group">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>
        {:else}
        <div class="list" aria-label={$i18n.t('app.telegram.rewards.title')}>
            {#each filteredItems as item (item.id)}
                <div class:archived={item.isActive === false} class="row">
                    <button class="row-main" type="button" aria-label={$i18n.t('app.telegram.tasks.editItem', { name: stripLeadingEmoji(item.name) })} on:click={() => edit(item)}>
                        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName, semantic: item.icon ?? null })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span>
                        <span class="entity-text">
                            <span class="title">{stripLeadingEmoji(item.name)}</span>
                            <span class="meta"><TelegramCoin size={13} />{item.price} · {stripLeadingEmoji(item.groupName || $i18n.t('app.telegram.tasks.ungrouped'))}</span>
                            {#if item.lastPurchasedAt}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.lastUsed', { when: formatLastUsedTime(item.lastPurchasedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.neverUsed')}</span>{/if}
                        </span>
                    </button>
                    {#if canEdit}
                        <button class="grant" type="button" disabled={item.isActive === false || grantingId != null} on:click|stopPropagation={() => confirmGrant = item}><TelegramIcon name="award" size={16} label={$i18n.t('app.telegram.rewards.grantShort')} /></button>
                        <div class="menu-wrap">
                            <button class="more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(item.name) })} aria-haspopup="menu" aria-expanded={openMenuId === item.id} on:click|stopPropagation={(event) => toggleMenu(item.id, event.currentTarget as HTMLButtonElement)}><TelegramIcon name="more" size={20} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                            {#if openMenuId === item.id}
                                <div class="menu" role="menu" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: stripLeadingEmoji(item.name) })}>
                                    <button role="menuitem" type="button" on:click={() => edit(item)}><TelegramIcon name="edit" size={16} label={$i18n.t('app.telegram.tasks.edit')} /><span>{$i18n.t('app.telegram.tasks.edit')}</span></button>
                                    <button role="menuitem" type="button" on:click={() => toggleArchive(item)}><TelegramIcon name="archive" size={16} label={item.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')} /><span>{item.isActive === false ? $i18n.t('app.telegram.tasks.unarchive') : $i18n.t('app.telegram.tasks.archive')}</span></button>
                                    <div class="menu-divider" role="presentation"></div>
                                    <button role="menuitem" class="danger" type="button" on:click={() => void remove(item)}><TelegramIcon name="delete" size={16} label={$i18n.t('app.telegram.tasks.delete')} /><span>{$i18n.t('app.telegram.tasks.delete')}</span></button>
                                </div>
                            {/if}
                        </div>
                    {/if}
                </div>
            {/each}
        </div>
        {/if}
    {/if}

    {#if !catalogOpen && canEdit}
        <button class="groups" type="button" on:click={() => groupEditorOpen = true}>
            <TelegramIcon name="filter" size={16} label={$i18n.t('app.telegram.tasks.manageGroups')} />
            <span>{$i18n.t('app.telegram.tasks.manageGroups')}</span>
        </button>
        {#if groupMessage}<span role="status" class="group-message">{groupMessage}</span>{/if}
    {/if}
    {#if grantError}<p class="error" role="alert">{grantError}</p>{/if}
</div>
<TelegramRewardForm open={formOpen} item={editingItem} groupSuggestions={groups} onClose={() => formOpen = false} />
<TelegramGroupManager open={groupEditorOpen} kind="shop" onClose={() => groupEditorOpen = false} on:save={saveGroups} on:deleteGroup={handleDeleteGroup} />

{#if confirmGrant}
    <div class="sheet-backdrop" role="presentation" on:click={() => confirmGrant = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="reward-grant-title" tabindex="-1">
        <h2 id="reward-grant-title">{$i18n.t('app.telegram.rewards.grantShort')}</h2>
        <div class="grant-row">
            <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: confirmGrant.name, group: confirmGrant.groupName, semantic: confirmGrant.icon ?? null })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span>
            <span class="grow"><span class="title">{stripLeadingEmoji(confirmGrant.name)}</span><span class="meta"><TelegramCoin size={13} />{confirmGrant.price} · {stripLeadingEmoji(confirmGrant.groupName || $i18n.t('app.telegram.tasks.ungrouped'))}</span></span>
        </div>
        <div class="delta"><span>{$i18n.t('app.telegram.rewards.grantChild')}</span><b>{$appStore.childNickname || $i18n.t('app.telegram.header.child')}</b></div>
        <div class="delta"><span>{$i18n.t('app.telegram.rewards.grantBalance')}</span><b>{$appStore.balance} → {Math.max(0, ($appStore.balance ?? 0) - (confirmGrant.price ?? 0))}</b></div>
        <div class="actions">
            <button class="cancel" type="button" on:click={() => confirmGrant = null}>{$i18n.t('app.telegram.rewards.cancel')}</button>
            <button class="primary" type="button" disabled={grantingId != null} on:click={() => confirmGrant && void grantToChild(confirmGrant)}>{$i18n.t('app.telegram.rewards.grantFor', { amount: confirmGrant.price })}</button>
        </div>
    </div>
{/if}

<style>
    .rewards { width:100%; }
    .page-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { margin:0; color:#18243d; font-size:1.35rem; }
    .header-actions { display:flex; align-items:center; gap:.25rem; }
    .add { display:inline-flex; align-items:center; justify-content:center; gap:.35rem; min-width:2.75rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    .catalog { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .empty-group { padding:1rem 0; text-align:center; }
    .row { display:flex; align-items:stretch; gap:.25rem; min-height:4rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row.archived { opacity:.6; }
    .row-main { display:flex; align-items:stretch; gap:.6rem; flex:1; min-width:0; padding:.5rem 0; border:0; background:transparent; text-align:left; cursor:pointer; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; margin-top:.2rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:center; }
    .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; }
    .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .meta--last { color:#8a93a8; font-size:.75rem; }
    .more { width:2.75rem; height:2.75rem; flex:0 0 auto; display:grid; place-items:center; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .grant { width:2rem; height:2rem; flex:0 0 auto; align-self:center; display:grid; place-items:center; box-sizing:border-box; aspect-ratio:1/1; border:1px solid #3867d6; border-radius:.5rem; background:#3867d6; color:#fff; cursor:pointer; }
    .grant:disabled { opacity:.5; cursor:not-allowed; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:11rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .menu button:disabled { opacity:.5; cursor:not-allowed; }
    .menu-divider { height:1px; margin:.25rem 0; background:#edf0f5; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    .sheet h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .grant-row { display:flex; align-items:center; gap:.6rem; padding:.4rem 0; }
    .grant-row .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .grant-row .grow { flex:1; min-width:0; }
    .grant-row .title { display:block; color:#18243d; font-weight:600; font-size:.95rem; line-height:1.3; }
    .grant-row .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .delta { display:flex; align-items:center; justify-content:space-between; gap:.6rem; margin-top:.5rem; padding:.6rem .7rem; border-radius:.6rem; background:#f4f6f9; color:#33415f; font-size:.9rem; }
    .delta b { font-weight:700; }
    .actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.9rem; }
    .actions .cancel { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .actions .primary { min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:750; cursor:pointer; }
    .actions .primary:disabled { cursor:wait; opacity:.6; }
    .muted { color:#66718a; }
    .error { margin:.75rem 0 0; padding:.6rem .75rem; border-radius:.75rem; background:#fff0f0; color:#a33b3b; font-size:.875rem; }
    button.groups { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; color:#18243d; font:inherit; font-weight:700; cursor:pointer; }
    button.groups span { display:inline-flex; align-items:center; }
    .group-message { display:block; margin-top:.4rem; text-align:center; color:#66718a; font-size:.85rem; }
</style>
