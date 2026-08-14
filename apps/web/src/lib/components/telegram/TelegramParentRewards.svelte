<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import ShopModal from '$lib/components/app/modals/ShopModal.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import { saveChildGroupOrder } from '$lib/services/api';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    $: groups = [...new Set($appStore.shopItems.map((item) => item.groupName).filter((group): group is string => Boolean(group)))];
    $: canEdit = $appStore.permission !== 'viewer';
    let groupMessage = '';
    let groupEditorOpen = false;
    let groupSaving = false;
    function add() { modalStore.open('shop-modal', { mode: 'add', groupSuggestions: groups, telegramChildId: $appStore.currentChildId }); }
    function edit(item: unknown) { modalStore.open('shop-modal', { mode: 'edit', item, groupSuggestions: groups, telegramChildId: $appStore.currentChildId }); }
    async function saveGroups(event: CustomEvent<string[]>) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await saveChildGroupOrder($appStore.currentChildId, 'shop', event.detail);
        groupSaving = false;
        groupMessage = result.ok ? 'Groups saved.' : 'Groups could not be saved. Refresh and try again.';
        if (result.ok) groupEditorOpen = false;
    }
</script>

<section class="panel" aria-labelledby="rewards-title">
    <div class="section-heading"><div><p class="eyebrow">Manage catalog</p><h2 id="rewards-title">Rewards</h2></div>{#if canEdit}<button class="primary" type="button" on:click={add}><TelegramIcon name="add" size={18} label="Add reward" />Add reward</button>{/if}</div>
    {#if !$appStore.shopItems.length}<p class="muted">No rewards for this child yet.</p>{:else}<div class="items">{#each $appStore.shopItems as item (item.id)}<article class:archived={item.isActive === false}><div class="entity-main"><span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName })} size={20} label="Reward" /></span><div><h3>{stripLeadingEmoji(item.name)}</h3><p class="meta"><TelegramCoin size={13} />{item.price} · {stripLeadingEmoji(item.groupName || 'Ungrouped')}</p></div></div>{#if canEdit}<button type="button" aria-label={`Edit ${stripLeadingEmoji(item.name)}`} on:click={() => edit(item)}><TelegramIcon name="edit" size={18} label={`Edit ${stripLeadingEmoji(item.name)}`} />Edit</button>{/if}</article>{/each}</div>{/if}
    {#if canEdit}<details class="groups"><summary>Manage groups</summary><p>{groups.length ? groups.join(' · ') : 'No named groups yet.'}</p><button type="button" on:click={() => groupEditorOpen = true}><TelegramIcon name="edit" size={18} label="Reorder groups" />Reorder groups</button>{#if groupMessage}<span role="status">{groupMessage}</span>{/if}</details>{/if}
</section>
<ShopModal />
<GroupOrderEditor bind:isOpen={groupEditorOpen} isAdmin={canEdit} isSaving={groupSaving} {groups} title="Reward groups" descriptionAdmin="Drag groups into the order your child sees." descriptionChild="" on:save={saveGroups} />

<style>
    .panel { width:100%; }.section-heading { display:flex; align-items:center; justify-content:space-between; gap:.75rem; }.eyebrow { margin:0; color:#66718a; font-size:.75rem; text-transform:uppercase; letter-spacing:.08em; } h2 { margin:.15rem 0 .65rem; color:#18243d; } .items { display:grid; gap:.5rem; } article { display:flex; justify-content:space-between; gap:.75rem; align-items:center; width:100%; padding:.75rem; border:1px solid #e5e9f1; border-radius:.75rem; background:#fff; } article.archived { opacity:.6; } .entity-main { display:flex; align-items:center; gap:.6rem; min-width:0; } .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; } h3 { margin:0; color:#18243d; font-size:1rem; } .meta { display:flex; align-items:center; gap:.3rem; margin:.25rem 0 0; color:#66718a; font-size:.875rem; } button { min-height:2.75rem; padding:.6rem .8rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; } button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; } .primary { border-color:#3867d6; background:#3867d6; color:#fff; } .muted { color:#66718a; }
</style>
