<script lang="ts">
    import { createEventDispatcher } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramRequestList from './TelegramRequestList.svelte';
    import TelegramIcon from './TelegramIcon.svelte';

    const dispatch = createEventDispatcher<{ navigate: 'tasks' | 'rewards' }>();
    let showAll = false;
    $: pending = $appStore.requests.filter((request) => request.status === 'pending');
    $: visibleRequests = showAll ? pending : pending.slice(0, 2);
</script>

<section class="home" aria-labelledby="parent-home-title">
    <div class="intro">
        <div><p class="eyebrow">Selected child</p><h2 id="parent-home-title">{$appStore.childNickname || 'Child'}</h2></div>
        <strong aria-label={`Balance: ${$appStore.balance} coins`}><TelegramCoin size={16} />{$appStore.balance}</strong>
    </div>
    <TelegramRequestList requests={visibleRequests} canDecide childId={$appStore.currentChildId} />
    {#if pending.length > 2 && !showAll}
        <button class="see-all" type="button" on:click={() => showAll = true}>See all requests <TelegramIcon name="arrowRight" size={18} label="See all requests" /></button>
    {:else if !pending.length}
        <p class="empty">Nothing needs attention right now.</p>
    {/if}
    <div class="quick-actions" aria-label="Quick actions">
        <button type="button" on:click={() => dispatch('navigate', 'tasks')}><TelegramIcon name="task" size={20} label="Manage tasks" />Tasks</button>
        <button type="button" on:click={() => dispatch('navigate', 'rewards')}><TelegramIcon name="reward" size={20} label="Manage rewards" />Rewards</button>
    </div>
</section>

<style>
    .home { display:grid; gap:1rem; }
    .intro { display:flex; align-items:center; justify-content:space-between; gap:.75rem; padding:.75rem 0; border-bottom:1px solid #e5e9f1; }
    .eyebrow { margin:0; color:#66718a; font-size:.75rem; text-transform:uppercase; letter-spacing:.08em; }
    h2 { margin:.2rem 0 0; color:#18243d; }
    strong { display:inline-flex; align-items:center; gap:.35rem; padding:.55rem .75rem; border-radius:999px; background:#fff4c2; color:#573d00; white-space:nowrap; }
    .see-all, .quick-actions button { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; min-height:44px; padding:.6rem .8rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .quick-actions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:.55rem; }
    .quick-actions button { border-color:#3867d6; }
    .empty { margin:0; padding:.75rem; color:#66718a; }
</style>
