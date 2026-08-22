<script lang="ts">
    import { requestItem, requestItemWithNote } from '$lib/telegram/services/shopApi';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { appStore } from '$lib/stores/app';
    import type { ShopItem } from '$lib/telegram/stores/types';
    import { shopItems } from '$lib/telegram/stores/shopItems';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';
    import TelegramListSurface from './ui/TelegramListSurface.svelte';
    import TelegramEntityRow from './ui/TelegramEntityRow.svelte';
    import TelegramCatalogToolbar from './TelegramCatalogToolbar.svelte';
    import { sortCatalogItems, type CatalogSortMode } from '$lib/telegram/services/catalogSort';
    import { getEffectiveGroupOrder, orderGroups } from '$lib/telegram/services/groupOrder';

    const i18n = useI18n();

    let selectedGroup = '';
    let sortMode: CatalogSortMode = 'group';
    let selected: { id: number | string; title: string } | null = null;
    let busy = false;
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = 'idle';
    let message = '';
    $: activeShop = $shopItems.filter((item) => item.isActive !== false);
    $: currentChild = $appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null;
    $: hiddenGroups = currentChild?.hiddenShopGroupOrder ?? [];
    $: visibleShop = activeShop.filter((item) => !hiddenGroups.includes(item.groupName?.trim() ?? ''));
    $: affordable = visibleShop.filter((item) => $appStore.balance >= item.price).sort((first, second) => first.price - second.price);
    $: nextGoal = visibleShop.filter((item) => $appStore.balance < item.price).sort((first, second) => first.price - second.price)[0] ?? null;
    $: goalPercent = nextGoal && nextGoal.price > 0 ? Math.min(100, Math.round(($appStore.balance / nextGoal.price) * 100)) : 0;
    $: goalMissing = nextGoal ? Math.max(0, nextGoal.price - $appStore.balance) : 0;
    $: pendingIds = $appStore.requests.filter((request) => (request.requestType === 'shop_purchase' || request.itemId != null) && request.status === 'pending').map((request) => request.itemId).filter((id): id is string | number => id != null);
    $: rawGroups = [...new Set(visibleShop.map((item) => item.groupName?.trim()).filter((group): group is string => Boolean(group)))];
    $: orderedGroups = orderGroups(rawGroups, getEffectiveGroupOrder(currentChild, 'shop', false));
    $: items = sortCatalogItems(selectedGroup ? visibleShop.filter((item) => item.groupName?.trim() === selectedGroup) : visibleShop, sortMode, orderedGroups, (item) => item.groupName?.trim() ?? '', (item) => item.price);
    const isPending = (id: number | string) => pendingIds.some((pendingId) => String(pendingId) === String(id));
    const isAffordable = (item: ShopItem) => $appStore.balance >= item.price;
    const isLimitReached = (item: ShopItem) => item.periodProgress?.available === false;
    async function submit(note: string | null) {
        if (!selected || busy) return;
        busy = true; status = 'pending'; message = $i18n.t('app.telegram.childTasks.sendingRequest');
        const result = note ? await requestItemWithNote(selected.id, note, $appStore.currentChildId) : await requestItem(selected.id, $appStore.currentChildId);
        busy = false;
        if (result.ok) { if (result.data && typeof result.data === 'object') applyDataSnapshot(result.data as Record<string, unknown>); status = 'success'; message = $i18n.t('app.telegram.childRewards.rewardRequestSent'); selected = null; }
        else if (result.errorCode === 'STALE_STATE') { await refreshData(); status = 'stale'; message = $i18n.t('app.telegram.childRewards.rewardChanged'); selected = null; }
        else { status = 'error'; message = result.error; selected = null; }
    }
</script>

<section aria-labelledby="child-rewards-title">
    <div class="heading">
        <h2 id="child-rewards-title">{$i18n.t('app.telegram.childRewards.rewardsTitle')}</h2>
        <span class:goal-progress-label={nextGoal}>{nextGoal ? stripLeadingEmoji(nextGoal.name) : $i18n.t('app.telegram.childRewards.spendYourCoins')}</span>
    </div>
    {#if nextGoal}<div class="goal-progress" role="progressbar" aria-valuemin="0" aria-valuemax={nextGoal.price} aria-valuenow={$appStore.balance} aria-label={nextGoal.name}><span style={`width: ${goalPercent}%`}></span></div>{/if}
    <TelegramGroupSubnav groups={rawGroups} selected={selectedGroup} kind="shop" allLabel={$i18n.t('app.telegram.groupSubnav.all')} moreLabel={$i18n.t('app.telegram.groupSubnav.more')} allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')} onSelect={(group) => selectedGroup = group} />
    {#if visibleShop.length}<TelegramCatalogToolbar count={items.length} countLabel={$i18n.t('app.telegram.sort.rewardsShown')} mode={sortMode} onChange={(mode) => sortMode = mode} />{/if}{#if selectedGroup && !items.length}<p class="empty">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>{:else if !items.length}<p class="empty">{$i18n.t('app.telegram.childRewards.noRewards')}</p>{:else}<TelegramListSurface label={$i18n.t('app.telegram.childRewards.rewardsTitle')}>{#each items as item (item.id)}<TelegramEntityRow interactive><span slot="icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName, semantic: item.icon ?? null })} size={20} label={$i18n.t('app.telegram.childRewards.rewardsTitle')} /></span><button slot="title" class="row-main" type="button" aria-label={stripLeadingEmoji(item.name)} on:click={() => { selected = { id: item.id, title: item.name }; status = 'idle'; }}><span class="title">{stripLeadingEmoji(item.name)}</span><span class="row-metadata"><span class="meta"><TelegramCoin size={13} />{item.price} · {item.groupName || $i18n.t('app.telegram.tasks.ungrouped')}</span>{#if item.lastPurchasedAt}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.lastUsed', { when: formatLastUsedTime(item.lastPurchasedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.neverUsed')}</span>{/if}</span></button><button slot="interactive" class="row-action grant" type="button" aria-label={$i18n.t('app.telegram.childRewards.askForReward')} disabled={isPending(item.id) || !isAffordable(item) || isLimitReached(item)} on:click={() => { selected = { id: item.id, title: item.name }; status = 'idle'; }}><TelegramIcon name={isPending(item.id) ? 'refresh' : 'requestReward'} size={16} label={isPending(item.id) ? $i18n.t('app.telegram.childTasks.pending') : $i18n.t('app.telegram.childRewards.askForReward')} /></button></TelegramEntityRow>{/each}</TelegramListSurface>{/if}
    {#if nextGoal}<div class="goal" aria-label={$i18n.t('app.telegram.childRewards.nextGoal')}><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: nextGoal.name, group: nextGoal.groupName })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span><div class="goal-text"><span class="goal-title">{stripLeadingEmoji(nextGoal.name)}</span><span class="goal-meta"><TelegramCoin size={13} />{nextGoal.price} · {$i18n.t('app.telegram.childRewards.moreToGo', { count: goalMissing })}</span></div></div>{/if}
    <TelegramActionStatus state={status} message={message} />
</section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} actionLabel={$i18n.t('app.telegram.childRewards.askForReward')} bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    .goal-progress { height:.4rem; margin:-.2rem 0 .75rem; overflow:hidden; border-radius:999px; background:#e8e9f4; } .goal-progress span { display:block; height:100%; border-radius:inherit; background:#5b63e9; transition:width .3s ease; }
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { flex-shrink:0; margin:0; color:#18243d; font-size:1.2rem; } .heading > span { color:#66718a; font-size:.8rem; } .heading > .goal-progress-label { flex:1; min-width:0; overflow:hidden; color:#18243d; font-weight:600; line-height:1.2; text-align:right; text-overflow:ellipsis; white-space:nowrap; } .empty { color:#66718a; text-align:center; padding:1rem 0; } .grant { border:1px solid #3867d6; border-radius:.5rem; background:#3867d6; color:#fff; } .grant:disabled { opacity:.5; cursor:not-allowed; } .goal { display:flex; align-items:center; gap:.6rem; margin:.35rem 0 .7rem; padding:.55rem .7rem; border:1px solid #e1e6ef; border-radius:.8rem; background:#fff; } .goal-text { flex:1; min-width:0; } .goal-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#18243d; font-weight:600; font-size:.95rem; } .goal-meta { display:flex; align-items:center; gap:.3rem; margin-top:.2rem; color:#66718a; font-size:.8rem; }
</style>
