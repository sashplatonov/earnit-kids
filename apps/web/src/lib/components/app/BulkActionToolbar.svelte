<script lang="ts">
    import { createEventDispatcher } from 'svelte';

    export let selectedCount = 0;
    export let show = false;
    export let selectionLabel = '';
    export let selectAllLabel = '';
    export let deleteLabel = '';
    export let blockLabel = '';
    export let unblockLabel = '';
    export let changeGroupLabel = '';
    export let clearLabel = '';

    const dispatch = createEventDispatcher<{
        selectAll: void;
        delete: void;
        block: void;
        unblock: void;
        changeGroup: void;
        clear: void;
    }>();
</script>

{#if show}
<div class="bulk-toolbar" role="toolbar" aria-label={selectionLabel}>
    <div class="bulk-toolbar__meta">{selectionLabel}</div>
    <div class="bulk-toolbar__actions">
        <button class="btn btn--secondary btn--small" type="button" on:click={() => dispatch('selectAll')}>
            {selectAllLabel}
        </button>
        <button class="btn btn--danger btn--small" type="button" disabled={selectedCount === 0} on:click={() => dispatch('delete')}>
            {deleteLabel}
        </button>
        <button class="btn btn--secondary btn--small" type="button" disabled={selectedCount === 0} on:click={() => dispatch('block')}>
            {blockLabel}
        </button>
        <button class="btn btn--secondary btn--small" type="button" disabled={selectedCount === 0} on:click={() => dispatch('unblock')}>
            {unblockLabel}
        </button>
        <button class="btn btn--secondary btn--small" type="button" disabled={selectedCount === 0} on:click={() => dispatch('changeGroup')}>
            {changeGroupLabel}
        </button>
        <button class="btn btn--ghost btn--small" type="button" on:click={() => dispatch('clear')}>
            {clearLabel}
        </button>
    </div>
</div>
{/if}

<style>
    .bulk-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        margin: 0.85rem 0 0.25rem;
        padding: 0.75rem 0.9rem;
        border-radius: 16px;
        background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(20, 184, 166, 0.08));
        border: 1px solid rgba(94, 120, 160, 0.14);
    }

    .bulk-toolbar__meta {
        font-size: 0.92rem;
        font-weight: 800;
        color: #20304e;
    }

    .bulk-toolbar__actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        justify-content: flex-end;
    }

    @media (max-width: 640px) {
        .bulk-toolbar {
            align-items: flex-start;
            flex-direction: column;
        }

        .bulk-toolbar__actions {
            width: 100%;
            justify-content: flex-start;
        }
    }
</style>
