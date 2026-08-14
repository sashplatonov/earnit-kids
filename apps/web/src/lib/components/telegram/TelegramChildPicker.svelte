<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { switchChild } from '$lib/services/bootstrap';
    let switching = false;
    let error = '';
    async function select(id: string | number) {
        switching = true; error = '';
        await switchChild(id);
        switching = false;
        if ($appStore.currentChildId != id) error = 'This child is no longer available. Refresh and try again.';
    }
</script>

<section class="panel" aria-labelledby="child-picker-title">
    <h2 id="child-picker-title">Selected child</h2>
    {#if $appStore.children.length === 0}<p class="muted">No children yet.</p>{/if}
    <div class="children" role="listbox" aria-label="Choose child">
        {#each $appStore.children as child (child.id)}
            <button class:selected={$appStore.currentChildId == child.id} type="button" role="option" aria-selected={$appStore.currentChildId == child.id} disabled={switching} on:click={() => select(child.id)}>
                <span>{child.nickname}</span><span>{child.balance} 🪙</span>
            </button>
        {/each}
    </div>
    {#if switching}<p class="muted" role="status">Refreshing child data…</p>{/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
</section>

<style>
    .panel { padding: 1rem; border: 1px solid #dfe4ee; border-radius: 1rem; background: #fff; margin-bottom: .75rem; }
    h2 { margin: 0 0 .75rem; font-size: 1.05rem; color: #18243d; }
    .children { display: grid; gap: .5rem; }
    button { display: flex; justify-content: space-between; min-height: 2.75rem; padding: .7rem .8rem; border: 1px solid #dfe4ee; border-radius: .7rem; background: #fff; color: #33415f; font: inherit; text-align: left; }
    button.selected { border-color: #3867d6; box-shadow: inset 3px 0 #3867d6; }
    .muted { color: #66718a; }.error { color: #a33b3b; }
</style>
