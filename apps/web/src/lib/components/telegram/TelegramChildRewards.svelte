<script lang="ts">
    import { requestItem, requestItemWithNote } from '$lib/services/api';
    import { applyDataSnapshot, refreshData } from '$lib/services/bootstrap';
    import { appStore, type ShopItem } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramGroupedCatalog, { type CatalogItem } from './TelegramGroupedCatalog.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRequestSheet from './TelegramRequestSheet.svelte';
    import TelegramActionStatus from './TelegramActionStatus.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';

    const i18n = useI18n();

    let selected: CatalogItem | null = null;
    let busy = false;
    let status: 'idle' | 'pending' | 'success' | 'error' | 'stale' = 'idle';
    let message = '';
    $: activeShop = $appStore.shopItems.filter((item) => item.isActive !== false);
    $: affordable = activeShop.filter((item) => $appStore.balance >= item.price).sort((first, second) => first.price - second.price);
    $: nextGoal = activeShop.filter((item) => $appStore.balance < item.price).sort((first, second) => first.price - second.price)[0] ?? null;
    $: goalPercent = nextGoal && nextGoal.price > 0 ? Math.min(100, Math.round(($appStore.balance / nextGoal.price) * 100)) : 0;
    $: goalMissing = nextGoal ? Math.max(0, nextGoal.price - $appStore.balance) : 0;
    $: pendingIds = $appStore.requests.filter((request) => (request.requestType === 'shop_purchase' || request.itemId != null) && request.status === 'pending').map((request) => request.itemId).filter((id): id is string | number => id != null);
    $: items = affordable.map((item): CatalogItem => ({ id: item.id, title: item.name, amount: item.price, group: item.groupName, available: true }));
    async function submit(note: string | null) {
        if (!selected || busy) return;
        busy = true; status = 'pending'; message = $i18n.t('app.telegram.childTasks.sendingRequest');
        const result = note ? await requestItemWithNote(selected.id, note) : await requestItem(selected.id);
        busy = false;
        if (result.ok) { if (result.data && typeof result.data === 'object') applyDataSnapshot(result.data as Record<string, unknown>); status = 'success'; message = $i18n.t('app.telegram.childRewards.rewardRequestSent'); selected = null; }
        else if (result.errorCode === 'STALE_STATE') { await refreshData(); status = 'stale'; message = $i18n.t('app.telegram.childRewards.rewardChanged'); selected = null; }
        else { status = 'error'; message = result.error; selected = null; }
    }
</script>

<section aria-labelledby="child-rewards-title"><div class="heading"><h2 id="child-rewards-title">{$i18n.t('app.telegram.childRewards.rewardsTitle')}</h2><span>{$i18n.t('app.telegram.childRewards.spendYourCoins')}</span></div>{#if !affordable.length && !nextGoal}<p class="empty">{$i18n.t('app.telegram.childRewards.noRewards')}</p>{:else}<TelegramGroupedCatalog kind="reward" items={items} {pendingIds} on:request={(event) => { selected = event.detail; status = 'idle'; }} />{/if}{#if nextGoal}<div class="goal" aria-label={$i18n.t('app.telegram.childRewards.nextGoal')}><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: nextGoal.name, group: nextGoal.groupName })} size={20} label={$i18n.t('app.telegram.rewards.reward')} /></span><div class="goal-text"><span class="goal-title">{stripLeadingEmoji(nextGoal.name)}</span><div class="goal-track" role="progressbar" aria-valuemin="0" aria-valuemax={nextGoal.price} aria-valuenow={$appStore.balance} aria-label={nextGoal.name}><span style={`width: ${goalPercent}%`}></span></div><span class="goal-meta"><TelegramCoin size={13} />{nextGoal.price} · {$i18n.t('app.telegram.childRewards.moreToGo', { count: goalMissing })}</span></div></div>{/if}<TelegramActionStatus state={status} message={message} /></section>
<TelegramRequestSheet open={selected !== null} title={selected?.title ?? ''} actionLabel={$i18n.t('app.telegram.childRewards.askForReward')} bind:busy on:close={() => selected = null} on:submit={(event) => submit(event.detail)} />

<style>
    section { margin-bottom:1.25rem; } .heading { display:flex; align-items:baseline; justify-content:space-between; gap:.75rem; margin-bottom:.7rem; } h2 { margin:0; color:#18243d; font-size:1.2rem; } span { color:#66718a; font-size:.8rem; } .empty { color:#66718a; text-align:center; padding:1rem 0; } .goal { display:flex; align-items:center; gap:.6rem; margin-top:.6rem; padding:.7rem; border:1px solid #e1e6ef; border-radius:.8rem; background:#fff; } .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } .goal-text { flex:1; min-width:0; } .goal-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#18243d; font-weight:600; font-size:.95rem; } .goal-track { height:.4rem; margin-top:.35rem; overflow:hidden; border-radius:999px; background:#e8e9f4; } .goal-track span { display:block; height:100%; border-radius:inherit; background:#5b63e9; transition:width .3s ease; } .goal-meta { display:flex; align-items:center; gap:.3rem; margin-top:.3rem; color:#66718a; font-size:.8rem; }
</style>
