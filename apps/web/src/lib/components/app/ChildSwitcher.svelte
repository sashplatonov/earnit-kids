<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { switchChild } from '$lib/services/bootstrap';

    $: children = $appStore.children;
    $: currentChildId = $appStore.currentChildId;
    $: currentChild = children.find(c => String(c.id) === String(currentChildId));

    let open = false;

    function toggle() { open = !open; }

    async function select(id: string | number) {
        open = false;
        if (String(id) === String(currentChildId)) return;
        await switchChild(id);
    }

    function handleOutsideClick(event: MouseEvent) {
        const target = event.target as Node;
        const el = document.querySelector('.nav__child-switcher');
        if (el && !el.contains(target)) open = false;
    }
</script>

<svelte:window on:click={handleOutsideClick} />

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
    </ul>
    {/if}
</div>
