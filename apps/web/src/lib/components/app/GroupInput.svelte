<script lang="ts">
    export let suggestions: string[] = [];
    export let value: string = '';
    export let placeholder: string = '';
    export let id: string = 'group-input';

    let adding = false;
    let draft = '';

    function startAdd() {
        draft = '';
        adding = true;
    }

    function confirmAdd() {
        const trimmed = draft.trim();
        if (trimmed) value = trimmed;
        adding = false;
    }

    function cancelAdd() {
        adding = false;
    }

    function onAddKeydown(e: KeyboardEvent) {
        if (e.key === 'Enter') confirmAdd();
        if (e.key === 'Escape') cancelAdd();
    }
</script>

{#if suggestions.length > 0}
<div class="group-input-layout">
    {#if adding}
        <div class="group-input-add-row">
            <input
                type="text"
                class="input group-input-add-field"
                placeholder={placeholder}
                bind:value={draft}
                on:keydown={onAddKeydown}
            />
            <button type="button" class="btn btn--primary btn--small" on:click={confirmAdd}>✓</button>
            <button type="button" class="btn btn--secondary btn--small" on:click={cancelAdd}>✕</button>
        </div>
    {:else}
        <div class="group-input-row">
            <select
                class="input group-input-select"
                id={id}
                bind:value
                on:change={() => {}}
            >
                <option value="">{placeholder || 'No group'}</option>
                {#each suggestions as group (group)}
                <option value={group}>{group}</option>
                {/each}
            </select>
            <button
                type="button"
                class="btn btn--add group-input-add-btn"
                title="Add new group"
                on:click={startAdd}
            >+</button>
        </div>
    {/if}
</div>
{:else}
<div class="group-input-row">
    <input
        type="text"
        class="input"
        {id}
        {placeholder}
        bind:value
    />
</div>
{/if}

<style>
    .group-input-layout {
        display: flex;
        flex-direction: column;
    }

    .group-input-row {
        display: flex;
        gap: 0.4rem;
        align-items: center;
    }

    .group-input-row .group-input-select {
        flex: 1;
        cursor: pointer;
    }

    .group-input-add-btn {
        flex-shrink: 0;
        width: 2.2rem;
        height: 2.2rem;
        border-radius: 50%;
        padding: 0;
        font-size: 1.3rem;
        font-weight: 700;
        line-height: 1;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .group-input-add-row {
        display: flex;
        gap: 0.4rem;
        align-items: center;
    }

    .group-input-add-row .group-input-add-field {
        flex: 1;
    }
</style>
