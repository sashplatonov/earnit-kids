<script lang="ts">
    import { appStore, type ShopItem } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import ShopModal from '$lib/components/app/modals/ShopModal.svelte';
    import GroupOrderEditor from '$lib/components/app/GroupOrderEditor.svelte';
    import { saveChildGroupOrder } from '$lib/services/api';
    import { confirmAction } from '$lib/services/confirm';
    import { scheduleSave } from '$lib/services/save';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import { getTelegramEntityIcon, stripLeadingEmoji } from './telegramEntityIcons';
    $: groups = [...new Set($appStore.shopItems.map((item) => item.groupName).filter((group): group is string => Boolean(group)))];
    $: canEdit = $appStore.permission !== 'viewer';
    let groupMessage = '';
    let groupEditorOpen = false;
    let groupSaving = false;
    let openMenuId: string | number | null = null;
    function add() { modalStore.open('shop-modal', { mode: 'add', groupSuggestions: groups, telegramChildId: $appStore.currentChildId }); }
    function edit(item: unknown) { openMenuId = null; modalStore.open('shop-modal', { mode: 'edit', item, groupSuggestions: groups, telegramChildId: $appStore.currentChildId }); }
    function toggleArchive(item: ShopItem) {
        const nextActive = item.isActive === false;
        appStore.setState({
            shopItems: $appStore.shopItems.map((entry) => entry.id == item.id ? ({ ...entry, isActive: nextActive } as typeof entry) : entry),
        });
        void scheduleSave();
    }
    async function remove(item: ShopItem) {
        const confirmed = await confirmAction({
            title: 'Delete reward?',
            description: `"${stripLeadingEmoji(item.name)}" will be removed.`,
            confirmLabel: 'Delete',
            cancelLabel: 'Cancel',
            tone: 'danger',
        });
        if (!confirmed) return;
        appStore.setState({ shopItems: $appStore.shopItems.filter((entry) => entry.id != item.id) });
        void scheduleSave();
    }
    async function saveGroups(event: CustomEvent<string[]>) {
        if ($appStore.currentChildId == null) return;
        groupSaving = true;
        const result = await saveChildGroupOrder($appStore.currentChildId, 'shop', event.detail);
        groupSaving = false;
        groupMessage = result.ok ? 'Groups saved.' : 'Groups could not be saved. Refresh and try again.';
        if (result.ok) groupEditorOpen = false;
    }
</script>

<svelte:window on:click={() => openMenuId = null} />

<div class="rewards">
    <div class="page-header">
        <h1 id="rewards-title">Rewards</h1>
        {#if canEdit}<button class="add" type="button" on:click={add}><TelegramIcon name="add" size={18} label="Add reward" /><span>Add</span></button>{/if}
    </div>

    {#if !$appStore.shopItems.length}
        <p class="muted">No rewards for this child yet.</p>
    {:else}
        <div class="list" aria-label="Rewards">
            {#each $appStore.shopItems as item (item.id)}
                <div class:archived={item.isActive === false} class="row">
                    <button class="row-main" type="button" aria-label={`Edit ${stripLeadingEmoji(item.name)}`} on:click={() => edit(item)}>
                        <span class="entity-icon"><TelegramIcon name={getTelegramEntityIcon({ kind: 'reward', title: item.name, group: item.groupName })} size={20} label="Reward" /></span>
                        <span class="entity-text">
                            <span class="title">{stripLeadingEmoji(item.name)}</span>
                            <span class="meta"><TelegramCoin size={13} />{item.price} · {stripLeadingEmoji(item.groupName || 'Ungrouped')}</span>
                        </span>
                    </button>
                    {#if canEdit}
                        <div class="menu-wrap">
                            <button class="more" type="button" aria-label={`Actions for ${stripLeadingEmoji(item.name)}`} aria-haspopup="menu" aria-expanded={openMenuId === item.id} on:click|stopPropagation={() => openMenuId = openMenuId === item.id ? null : item.id}><TelegramIcon name="more" size={20} label="More actions" /></button>
                            {#if openMenuId === item.id}
                                <div class="menu" role="menu" aria-label={`Actions for ${stripLeadingEmoji(item.name)}`}>
                                    <button role="menuitem" type="button" on:click={() => edit(item)}><TelegramIcon name="edit" size={16} label="Edit" /><span>Edit</span></button>
                                    <button role="menuitem" type="button" on:click={() => toggleArchive(item)}><TelegramIcon name="archive" size={16} label={item.isActive === false ? 'Unarchive' : 'Archive'} /><span>{item.isActive === false ? 'Unarchive' : 'Archive'}</span></button>
                                    <button role="menuitem" class="danger" type="button" on:click={() => void remove(item)}><TelegramIcon name="delete" size={16} label="Delete" /><span>Delete</span></button>
                                </div>
                            {/if}
                        </div>
                    {/if}
                </div>
            {/each}
        </div>
    {/if}

    {#if canEdit}
        <details class="groups">
            <summary><TelegramIcon name="filter" size={16} label="Groups" />Manage groups</summary>
            <p>{groups.length ? groups.join(' · ') : 'No named groups yet.'}</p>
            <button type="button" on:click={() => groupEditorOpen = true}><TelegramIcon name="edit" size={18} label="Reorder groups" />Reorder groups</button>
            {#if groupMessage}<span role="status">{groupMessage}</span>{/if}
        </details>
    {/if}
</div>
<ShopModal />
<GroupOrderEditor bind:isOpen={groupEditorOpen} isAdmin={canEdit} isSaving={groupSaving} {groups} title="Reward groups" descriptionAdmin="Drag groups into the order your child sees." descriptionChild="" on:save={saveGroups} />

<style>
    .rewards { width:100%; }
    .page-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.45rem; }
    h1 { margin:0; color:#18243d; font-size:1.35rem; }
    .add { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.45rem .65rem; border:0; border-radius:.7rem; background:transparent; color:#3867d6; font:inherit; font-weight:750; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .list { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.25rem; min-height:3.5rem; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .row.archived { opacity:.6; }
    .row-main { display:flex; align-items:center; gap:.6rem; flex:1; min-width:0; min-height:3.5rem; padding:.3rem 0; border:0; background:transparent; text-align:left; cursor:pointer; }
    .entity-icon { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .entity-text { min-width:0; }
    .title { display:block; color:#18243d; font-size:.95rem; font-weight:600; line-height:1.3; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; line-clamp:2; -webkit-box-orient:vertical; }
    .meta { display:flex; align-items:center; gap:.3rem; margin-top:.15rem; color:#66718a; font-size:.8rem; }
    .more { width:2.75rem; height:2.75rem; display:grid; place-items:center; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .menu-wrap { position:relative; }
    .menu { position:absolute; right:0; top:calc(100% - .5rem); z-index:30; min-width:11rem; padding:.35rem; border:1px solid #e0e4ec; border-radius:.75rem; background:#fff; box-shadow:0 .75rem 2rem rgb(24 36 61 / 14%); }
    .menu button { display:flex; align-items:center; gap:.55rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-radius:.5rem; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .menu button:hover { background:#f2f5ff; }
    .menu button.danger { color:#c63c42; }
    .muted { color:#66718a; }
    details.groups { margin-top:.75rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; padding:.4rem .6rem; }
    details.groups summary { display:flex; align-items:center; gap:.4rem; min-height:2.75rem; color:#18243d; font-weight:700; cursor:pointer; list-style:none; }
    details.groups summary::-webkit-details-marker { display:none; }
    details.groups p { margin:.25rem 0; color:#66718a; font-size:.85rem; }
    details.groups button { display:inline-flex; align-items:center; gap:.4rem; min-height:2.75rem; padding:.4rem .7rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
