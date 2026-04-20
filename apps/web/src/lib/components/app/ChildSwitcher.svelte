<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { switchChild } from '$lib/services/bootstrap';
    import { modalStore } from '$lib/stores/modal';

    $: children = $appStore.children;
    $: currentChildId = $appStore.currentChildId;
    $: currentChild = children.find(c => String(c.id) === String(currentChildId));
    $: hasChildren = children.length > 0;

    let open = false;

    function toggle() { open = !open; }

    async function select(id: string | number) {
        open = false;
        if (String(id) === String(currentChildId)) return;
        await switchChild(id);
    }

    function openAddChild() {
        open = false;
        modalStore.open('add-child-modal');
    }

    function handleOutsideClick(event: MouseEvent) {
        const target = event.target as Node;
        const el = document.querySelector('.nav__child-switcher');
        if (el && !el.contains(target)) open = false;
    }
</script>

<svelte:window on:click={handleOutsideClick} />

{#if !hasChildren}
<button
    type="button"
    class="btn btn--primary btn--small"
    id="child-switcher-add-child"
    aria-label="Добавить ребенка"
    on:click={openAddChild}
>
    + Ребенок
</button>
{:else}
<div class="nav__child-switcher child-menu" class:active={open}>
    <button
        type="button"
        class="child-menu-btn"
        aria-haspopup="listbox"
        aria-expanded={open}
        on:click|stopPropagation={toggle}
    >
        <span class="child-menu-btn__icon"><span class="gamified-icon icon-child" aria-hidden="true"></span></span>
        <span class="child-menu-btn__name">{currentChild?.nickname ?? 'Выберите ребенка'}</span>
        <span class="child-menu-btn__arrow" aria-hidden="true">▼</span>
    </button>

    {#if open}
    <ul class="child-menu-dropdown" role="listbox" aria-label="Выбор ребенка">
        {#each children as child (child.id)}
        <li
            class="child-menu-item"
            class:active={String(child.id) === String(currentChildId)}
            role="option"
            aria-selected={String(child.id) === String(currentChildId)}
            on:click|stopPropagation={() => select(child.id)}
            on:keydown={e => (e.key === 'Enter' || e.key === ' ') && select(child.id)}
            tabindex="0"
        >
            <span class="child-menu-item__name">{child.nickname}</span>
            <span class="child-menu-item__balance">
                {child.balance}<span class="gamified-icon icon-coin-stack" aria-hidden="true"></span>
            </span>
        </li>
        {/each}
        <li class="child-menu-divider" role="presentation"></li>
        <li
            class="child-menu-item add-child-item"
            role="option"
            aria-selected="false"
            tabindex="0"
            on:click|stopPropagation={openAddChild}
            on:keydown={(event) => (event.key === 'Enter' || event.key === ' ') && openAddChild()}
        >
            <span class="child-menu-item__name">Добавить ребенка</span>
            <span class="child-menu-item__balance">+</span>
        </li>
    </ul>
    {/if}
</div>
{/if}
