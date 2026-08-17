<script lang="ts">
    import { requestItem, requestItemWithNote } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { appStore, type ShopItem } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import TelegramGroupSubnav from './TelegramGroupSubnav.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    import { formatLastUsedTime } from './telegramLastUsed';

    const i18n = useI18n();

    let selectedGroup = '';
    let selected: { id: number | string; title: string } | null = null;
    let busy = false;
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = 'idle';
    let message = '';
    $: activeShop = $appStore.shopItems.filter((item) => item.isActive !== false);
    $: currentChild = $appStore.children.find((child) => String(child.id) === String($appStore.currentChildId)) ?? null;
    $: hiddenGroups = currentChild?.hiddenShopGroupOrder ?? [];
    $: visibleShop = activeShop.filter((item) => !hiddenGroups.includes(item.groupName?.trim() ?? ''));
    $: affordable = visibleShop.filter((item) => $appStore.balance >= item.price).sort((first, second) => first.price - second.price);
    $: nextGoal = visibleShop.filter((item) => $appStore.balance < item.price).sort((first, second) => first.price - second.price)[0] ?? null;
    $: goalPercent = nextGoal && nextGoal.price > 0 ? Math.min(100, Math.round(($appStore.balance / nextGoal.price) * 100)) : 0;
    $: goalMissing = nextGoal ? Math.max(0, nextGoal.price - $appStore.balance) : 0;
    $: pendingIds = $appStore.requests.filter((request) => (request.requestType === 'shop_purchase' || request.itemId != null) && request.status === 'pending').map((request) => request.itemId).filter((id): id is string | number => id != null);
    $: rawGroups = [...new Set(visibleShop.map((item) => item.groupName?.trim()).filter((group): group is string => Boolean(group)))];
    $: items = selectedGroup ? visibleShop.filter((item) => item.groupName?.trim() === selectedGroup) : visibleShop;
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

<section aria-labelledby="child-rewards-title"><div class="heading"><h2 id="child-rewards-title">{$i18n.t('app.telegram.childRewards.rewardsTitle')}</h2><span>{$i18n.t('app.telegram.childRewards.spendYourCoins')}</span></div>{#if selectedGroup && !items.length}<p class="empty">{$i18n.t('app.telegram.groupSubnav.emptyGroup')}</p>{:else if !items.length}<p class="empty">{$i18n.t('app.telegram.childRewards.noRewards')}</p>{:else}<TelegramGroupSubnav groups={rawGroups} selected={selectedGroup} kind="shop" allLabel={$i18n.t('app.telegram.groupSubnav.all')} moreLabel={$i18n.t('app.telegram.groupSubnav.more')} allGroupsTitle={$i18n.t('app.telegram.groupSubnav.allGroups')} onSelect={(group) => selectedGroup = group} /><div class="list" aria-label={$i18n.t('app.telegram.childRewards.rewardsTitle')}>{#each items as item (item.id)}<div class="row"><button class="row-main" type="button" aria-label={stripLeadingEmoji(item.name)} on:click={() => { selected = { id: item.id, title: item.name }; status = 'idle'; }}><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName, semantic: item.icon ?? null })} size={20} label={$i18n.t('app.telegram.childRewards.rewardsTitle')} /></span><span class="entity-text"><span class="title">{stripLeadingEmoji(item.name)}</span><span class="meta"><TelegramCoin size={13} />{item.price} · {stripLeadingEmoji(item.groupName || $i18n.t('app.telegram.tasks.ungrouped'))}</span>{#if item.lastPurchasedAt}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.lastUsed', { when: formatLastUsedTime(item.lastPurchasedAt, $i18n.locale) })}</span>{:else}<span class="meta meta--last">{$i18n.t('app.telegram.rewards.neverUsed')}</span>{/if}</span></button><button class="check" type="button" aria-label={$i18n.t('app.telegram.childRewards.askForReward')} disabled={isPending(item.id) || !isAffordable(item) || isLimitReached(item)} on:click={() => { selected = { id: item.id, title: item.name }; status = 'idle'; }}><TelegramIcon name={isPending(item.id) ? 'refresh' : 'requestReward'} size={16} label={isPending(item.id) ? $i18n.t('app.telegram.childTasks.pending') : $i18n.t('app.telegram.childRewards.askForReward')} /></button></div>{/each}</div>{/if}{#if nextGoal}<div class="goal" aria-label={$i18n.t('app.telegram.childRewards.nextGoal')}><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: nextGoal.name, group: nextGoal.groupName })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span><div class="goal-text"><span class="goal-title">{stripLeadingEmoji(nextGoal.name)}</span><div class="goal-track" role="progressbar" aria-valuemin="0" aria-valuemax={nextGoal.price} aria-valuenow={$appStore.balance} aria-label={nextGoal.name}><span style={`width: ${goalPercent}%`}></span></div><span class="goal-meta"><TelegramCoin size={13} />{nextGoal.price} · {$i18n.t('app.telegram.childRewards.moreToGo', { count: goalMissing })}</span></div></div>{/if}<TelegramActionStatus state={status} message={message} /></section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} actionLabel={$i18n.t('app.telegram.childRewards.askForReward')} bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { margin:0; color:#18243d; font-size:1.2rem; } span { color:#66718a; font-size:.8rem; } .empty { color:#66718a; text-align:center; padding:1rem 0; } .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; } .row { display:flex; align-items:stretch; gap:.25rem; min-height:4rem; border-bottom:1px solid #edf0f5; } .row:last-child { border-bottom:0; } .row-main { display:flex; align-items:stretch; gap:.6rem; flex:1; min-width:0; padding:.5rem 0; border:0; background:transparent; text-align:left; cursor:pointer; } .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; margin-top:.2rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } .entity-text { flex:1; min-width:0; display:flex; flex-direction:column; justify-content:center; } .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; } .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; } .meta--last { color:#8a93a8; font-size:.75rem; } .check { width:2rem; height:2rem; flex:0 0 auto; align-self:center; display:grid; place-items:center; box-sizing:border-box; aspect-ratio:1/1; border:1px solid #3867d6; border-radius:.5rem; background:#3867d6; color:#fff; cursor:pointer; } .check:disabled { opacity:.5; cursor:not-allowed; } .goal { display:flex; align-items:center; gap:.6rem; margin-top:.6rem; padding:.7rem; border:1px solid #e1e6ef; border-radius:.8rem; background:#fff; } .goal .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } .goal-text { flex:1; min-width:0; } .goal-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#18243d; font-weight:600; font-size:.95rem; } .goal-track { height:.4rem; margin-top:.35rem; overflow:hidden; border-radius:999px; background:#e8e9f4; } .goal-track span { display:block; height:100%; border-radius:inherit; background:#5b63e9; transition:width .3s ease; } .goal-meta { display:flex; align-items:center; gap:.3rem; margin-top:.3rem; color:#66718a; font-size:.8rem; }
</style>
