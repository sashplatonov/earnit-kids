<script lang="ts">
    import { createEventDispatcher, onMount, onDestroy } from 'svelte';
    import { isNoopGroupDrop, moveGroup, reorderGroupsBySlot } from '$lib/services/groupOrder';

    export let isOpen = false;
    export let isAdmin = false;
    export let isSaving = false;
    export let groups: string[] = [];
    export let hasStoredOrder = false;
    export let title = '';
    export let hintAdmin = '';
    export let hintChild = '';
    export let descriptionAdmin = '';
    export let descriptionChild = '';

    const dispatch = createEventDispatcher<{
        save: string[];
        reset: void;
    }>();

    let draft: string[] = [];
    let dragSourceIndex: number | null = null;
    let dropSlotIndex: number | null = null;
    let activePointerId: number | null = null;
    let dragGhostX = 0;
    let dragGhostY = 0;
    let rowRefs: Array<HTMLDivElement | null> = [];
    let dragStarted = false;
    let startX = 0;
    let startY = 0;
    let isTouchDrag = false;
        let ghostEl: HTMLDivElement | null = null;

        onMount(() => {
            if (typeof document !== 'undefined') {
                ghostEl = document.createElement('div');
                ghostEl.className = 'group-order-drag-ghost';
                ghostEl.setAttribute('aria-hidden', 'true');
                ghostEl.style.position = 'fixed';
                ghostEl.style.pointerEvents = 'none';
                ghostEl.style.display = 'none';
                document.body.appendChild(ghostEl);
            }
        });

        onDestroy(() => {
            if (ghostEl && ghostEl.parentNode) {
                ghostEl.parentNode.removeChild(ghostEl);
            }
            ghostEl = null;
        });

    $: if (!isOpen) {
        draft = [...groups];
        rowRefs = [];
        clearDragState();
    }

    function clearDragState() {
        dragSourceIndex = null;
        dropSlotIndex = null;
        activePointerId = null;
        dragStarted = false;
        isTouchDrag = false;
    }

    function openEditor() {
        isOpen = true;
    }

    function closeEditor() {
        isOpen = false;
    }

    function saveOrder() {
        dispatch('save', draft);
    }

    function resetOrder() {
        dispatch('reset');
    }

    function startPointerDrag(event: PointerEvent, index: number) {
        if (isSaving) {
            return;
        }

        activePointerId = event.pointerId;
        dragSourceIndex = index;
        dropSlotIndex = index;
        startX = event.clientX;
        startY = event.clientY;
        dragGhostX = event.clientX;
        dragGhostY = event.clientY;
        dragStarted = false; // wait until movement threshold
        isTouchDrag = typeof event.pointerType === 'string' ? event.pointerType === 'touch' : false;
        event.preventDefault();
    }

    function handlePointerMove(event: PointerEvent) {
        if (dragSourceIndex == null || activePointerId !== event.pointerId) {
            return;
        }

        dragGhostX = event.clientX;
        dragGhostY = event.clientY;

        // Start drag only after user moves a bit to avoid accidental taps
        if (!dragStarted) {
            const dx = event.clientX - startX;
            const dy = event.clientY - startY;
            const dist = Math.sqrt(dx * dx + dy * dy);
            const threshold = isTouchDrag ? 12 : 4;
            if (dist < threshold) {
                return;
            }
            dragStarted = true;
        }

        dropSlotIndex = resolveDropSlot(event.clientY);
        event.preventDefault();
    }

    function handlePointerUp(event: PointerEvent) {
        if (dragSourceIndex == null || activePointerId !== event.pointerId) {
            clearDragState();
            return;
        }

        if (dragStarted) {
            finishPointerDrag();
        } else {
            // It was a tap, not a drag
            clearDragState();
        }

        event.preventDefault();
    }

    function handleWindowKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && dragSourceIndex != null) {
            clearDragState();
        }
    }

    function resolveDropSlot(clientY: number): number {
        const pad = (typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(pointer: coarse)').matches) ? 18 : 6;
        for (let index = 0; index < draft.length; index += 1) {
            const row = rowRefs[index];
            if (!row) {
                continue;
            }

            const rect = row.getBoundingClientRect();
            const midpoint = rect.top + rect.height / 2;
            if (clientY < midpoint + pad) {
                return index;
            }
        }

        return draft.length;
    }

    function finishPointerDrag() {
        if (dragSourceIndex == null || dropSlotIndex == null || isNoopGroupDrop(dragSourceIndex, dropSlotIndex)) {
            clearDragState();
            return;
        }

        draft = reorderGroupsBySlot(draft, dragSourceIndex, dropSlotIndex);
        clearDragState();
    }

    function moveByKeyboard(event: KeyboardEvent, index: number) {
        if (isSaving) {
            return;
        }

        if (event.key === 'ArrowUp') {
            draft = moveGroup(draft, index, -1);
            event.preventDefault();
        }

        if (event.key === 'ArrowDown') {
            draft = moveGroup(draft, index, 1);
            event.preventDefault();
        }
    }

    function showDropSlot(slotIndex: number) {
        return dragSourceIndex != null
            && dragStarted
            && dropSlotIndex === slotIndex
            && !isNoopGroupDrop(dragSourceIndex, slotIndex);
    }

    $: if (ghostEl) {
        if (dragStarted && dragSourceIndex != null) {
            ghostEl.style.display = 'block';
            ghostEl.style.left = `${dragGhostX + (isTouchDrag ? 26 : 18)}px`;
            ghostEl.style.top = `${dragGhostY - (isTouchDrag ? 40 : 10)}px`;
            ghostEl.textContent = draft[dragSourceIndex] ?? '';
        } else {
            ghostEl.style.display = 'none';
        }
    }
</script>

<svelte:window on:pointermove={handlePointerMove} on:pointerup={handlePointerUp} on:pointercancel={handlePointerUp} on:keydown={handleWindowKeydown} />

<div class="group-order-toolbar">
    <p class="group-order-toolbar__hint">
        {isAdmin ? hintAdmin : hintChild}
    </p>
    {#if !isOpen}
    <div class="group-order-toolbar__actions">
        <button class="btn btn--secondary btn--small" type="button" on:click={openEditor} disabled={isSaving}>
            {isAdmin ? 'Настроить порядок' : 'Настроить под себя'}
        </button>
        {#if hasStoredOrder}
        <button class="btn btn--secondary btn--small" type="button" on:click={resetOrder} disabled={isSaving}>
            {isAdmin ? 'Сбросить' : 'К родительскому'}
        </button>
        {/if}
    </div>
    {/if}
</div>

{#if isOpen}
<div class="group-order-panel" aria-live="polite">
    <div class="group-order-panel__header">
        <h3 class="group-order-panel__title">{title}</h3>
        <p class="group-order-panel__description">
            {isAdmin ? descriptionAdmin : descriptionChild}
        </p>
    </div>

    <div class="group-order-list" role="list">
        {#each draft as group, index (group)}
            {#if showDropSlot(index)}
            <div class="group-order-drop-slot" aria-hidden="true">
                <span class="group-order-drop-slot__line"></span>
                <span class="group-order-drop-slot__label">Отпусти здесь</span>
            </div>
            {/if}

            <div
                class="group-order-row"
                class:group-order-row--dragging={dragSourceIndex === index}
                bind:this={rowRefs[index]}
                role="listitem"
                on:pointerdown={(e) => { if ((e as PointerEvent).pointerType === 'touch' && activePointerId == null) startPointerDrag(e as PointerEvent, index); }}
            >
                <span class="group-order-row__index">{index + 1}</span>
                <div class="group-order-row__content">
                    <span class="group-order-row__name">{group}</span>
                    <span class="group-order-row__meta">
                        {dragSourceIndex === index ? 'Перемещается' : 'Тяни за ручку или меняй стрелками'}
                    </span>
                </div>
                <button
                    class="group-order-row__handle"
                    type="button"
                    aria-label={`Перетащить группу ${group}`}
                    disabled={isSaving}
                    on:pointerdown={(event) => { event.stopPropagation(); startPointerDrag(event, index); }}
                    on:keydown={(event) => moveByKeyboard(event, index)}
                >
                    <span class="group-order-row__handle-grip" aria-hidden="true"></span>
                    <span class="group-order-row__handle-label">Тащить</span>
                </button>
            </div>
        {/each}

        {#if showDropSlot(draft.length)}
        <div class="group-order-drop-slot group-order-drop-slot--end" aria-hidden="true">
            <span class="group-order-drop-slot__line"></span>
            <span class="group-order-drop-slot__label">Отпусти в конец</span>
        </div>
        {/if}
    </div>

    <div class="group-order-panel__actions">
        <button class="btn btn--secondary btn--small" type="button" on:click={closeEditor} disabled={isSaving}>
            Отмена
        </button>
        {#if hasStoredOrder}
        <button class="btn btn--secondary btn--small" type="button" on:click={resetOrder} disabled={isSaving}>
            {isAdmin ? 'Сбросить' : 'К родительскому'}
        </button>
        {/if}
        <button class="btn btn--primary btn--small" type="button" on:click={saveOrder} disabled={isSaving}>
            {isSaving ? 'Сохраняю...' : 'Сохранить'}
        </button>
    </div>
</div>
{/if}

<!-- Drag ghost is rendered into document.body via portal to avoid transform offset issues -->