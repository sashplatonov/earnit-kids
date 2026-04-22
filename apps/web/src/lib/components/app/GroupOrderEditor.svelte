<script lang="ts">
    import { createEventDispatcher, onMount, onDestroy } from 'svelte';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { isNoopGroupDrop, moveGroup, reorderGroupsBySlot } from '$lib/services/groupOrder';

    const i18n = useI18n();

    function tApp(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`app.${key}` as MessageKey, variables);
    }

    export let isOpen = false;
    export let isAdmin = false;
    export let isSaving = false;
    export let groups: string[] = [];
    export let title = '';
    export let hintAdmin = '';
    export let hintChild = '';
    export let descriptionAdmin = '';
    export let descriptionChild = '';
    export let hideToolbarOnMobile = false;

    const dispatch = createEventDispatcher<{
        save: string[];
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
    let modalHost: HTMLDivElement | null = null;
    let modalPanel: HTMLDivElement | null = null;

    onMount(() => {
        if (typeof document !== 'undefined') {
            ghostEl = document.createElement('div');
            ghostEl.className = 'group-order-drag-ghost';
            ghostEl.setAttribute('aria-hidden', 'true');
            ghostEl.style.position = 'fixed';
            ghostEl.style.pointerEvents = 'none';
            ghostEl.style.display = 'none';
            document.body.appendChild(ghostEl);

            modalHost = document.createElement('div');
            modalHost.className = 'group-order-modal-host';
            document.body.appendChild(modalHost);
        }
    });

    onDestroy(() => {
        if (ghostEl && ghostEl.parentNode) {
            ghostEl.parentNode.removeChild(ghostEl);
        }
        if (modalHost && modalHost.parentNode) {
            modalHost.parentNode.removeChild(modalHost);
        }
        ghostEl = null;
        modalHost = null;
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

    export function openEditor() {
        isOpen = true;
    }

    function closeEditor() {
        isOpen = false;
    }

    function saveOrder() {
        dispatch('save', draft);
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
            return;
        }

        if (event.key === 'Escape' && isOpen) {
            closeEditor();
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

    $: if (modalHost && modalPanel && modalPanel.parentNode !== modalHost) {
        modalHost.appendChild(modalPanel);
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

<div class="group-order-toolbar" class:group-order-toolbar--mobile-hidden={hideToolbarOnMobile}>
    <p class="group-order-toolbar__hint">
        {isAdmin ? hintAdmin : hintChild}
    </p>
    {#if !isOpen}
    <div class="group-order-toolbar__actions">
        <button class="btn btn--secondary btn--small" type="button" on:click={openEditor} disabled={isSaving}>
            {isAdmin ? tApp('groupOrder.configureAdmin') : tApp('groupOrder.configureChild')}
        </button>
    </div>
    {/if}
</div>

{#if isOpen}
<div class="group-order-modal" bind:this={modalPanel}>
    <button class="group-order-modal__backdrop" type="button" aria-label={tApp('groupOrder.cancel')} on:click={closeEditor} disabled={isSaving}></button>
    <div
        class="group-order-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="group-order-title"
        aria-describedby="group-order-description"
    >
        <div class="group-order-panel__header">
            <div class="group-order-panel__heading">
                <h3 class="group-order-panel__title" id="group-order-title">{title}</h3>
                <p class="group-order-panel__description" id="group-order-description">
                    {isAdmin ? descriptionAdmin : descriptionChild}
                </p>
            </div>
            <button class="group-order-panel__close" type="button" aria-label={tApp('groupOrder.cancel')} on:click={closeEditor} disabled={isSaving}>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M6 6l12 12"></path>
                    <path d="M18 6 6 18"></path>
                </svg>
            </button>
        </div>

        <div class="group-order-panel__body">
            <div class="group-order-list" role="list">
                {#each draft as group, index (group)}
                    {#if showDropSlot(index)}
                    <div class="group-order-drop-slot" aria-hidden="true">
                        <span class="group-order-drop-slot__line"></span>
                        <span class="group-order-drop-slot__label">{tApp('groupOrder.dropHere')}</span>
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
                                {dragSourceIndex === index ? tApp('groupOrder.moving') : tApp('groupOrder.dragHint')}
                            </span>
                        </div>
                        <button
                            class="group-order-row__handle"
                            type="button"
                            aria-label={tApp('groupOrder.dragAria', { group })}
                            disabled={isSaving}
                            on:pointerdown={(event) => { event.stopPropagation(); startPointerDrag(event, index); }}
                            on:keydown={(event) => moveByKeyboard(event, index)}
                        >
                            <span class="group-order-row__handle-grip" aria-hidden="true"></span>
                            <span class="group-order-row__handle-label">{tApp('groupOrder.dragHandle')}</span>
                        </button>
                    </div>
                {/each}

                {#if showDropSlot(draft.length)}
                <div class="group-order-drop-slot group-order-drop-slot--end" aria-hidden="true">
                    <span class="group-order-drop-slot__line"></span>
                    <span class="group-order-drop-slot__label">{tApp('groupOrder.dropAtEnd')}</span>
                </div>
                {/if}
            </div>
        </div>

        <div class="group-order-panel__actions">
            <button class="btn btn--secondary btn--small" type="button" on:click={closeEditor} disabled={isSaving}>
                {tApp('groupOrder.cancel')}
            </button>
            <button class="btn btn--primary btn--small" type="button" on:click={saveOrder} disabled={isSaving}>
                {isSaving ? tApp('groupOrder.saving') : tApp('groupOrder.save')}
            </button>
        </div>
    </div>
</div>
{/if}

<style>
    .group-order-modal {
        position: fixed;
        inset: 0;
        z-index: 1200;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1rem;
        background: rgba(18, 28, 46, 0.38);
        backdrop-filter: blur(8px);
    }

    .group-order-modal__backdrop {
        position: absolute;
        inset: 0;
        border: 0;
        background: transparent;
        cursor: pointer;
    }

    .group-order-panel {
        position: relative;
        width: min(36rem, calc(100vw - 2rem));
        max-height: min(42rem, calc(100dvh - 2rem));
        display: grid;
        grid-template-rows: auto minmax(0, 1fr) auto;
        overflow: hidden;
        border: 1px solid rgba(120, 140, 175, 0.2);
        border-radius: 1.1rem;
        background: rgba(255, 255, 255, 0.98);
        box-shadow: 0 24px 80px rgba(26, 39, 67, 0.24);
    }

    .group-order-panel__header {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: start;
        gap: 1rem;
        padding: 1.1rem 1.1rem 0.85rem;
        border-bottom: 1px solid rgba(120, 140, 175, 0.14);
    }

    .group-order-panel__title {
        margin: 0;
        color: #1f2d46;
        font-size: 1.08rem;
        line-height: 1.25;
    }

    .group-order-panel__description {
        margin: 0.35rem 0 0;
        color: rgba(54, 68, 96, 0.68);
        font-size: 0.9rem;
        line-height: 1.35;
    }

    .group-order-panel__close {
        width: 2.25rem;
        height: 2.25rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 1px solid rgba(120, 140, 175, 0.18);
        border-radius: 999px;
        background: rgba(246, 248, 252, 0.94);
        color: rgba(54, 68, 96, 0.74);
        cursor: pointer;
    }

    .group-order-panel__close svg {
        width: 1rem;
        height: 1rem;
        fill: none;
        stroke: currentColor;
        stroke-width: 2;
        stroke-linecap: round;
    }

    .group-order-panel__body {
        min-height: 0;
        overflow-y: scroll;
        scrollbar-gutter: stable;
        scrollbar-width: thin;
        scrollbar-color: rgba(87, 121, 206, 0.58) rgba(231, 236, 246, 0.9);
        padding: 0.85rem 1.1rem;
    }

    .group-order-panel__body::-webkit-scrollbar {
        width: 0.45rem;
    }

    .group-order-panel__body::-webkit-scrollbar-track {
        border-radius: 999px;
        background: rgba(231, 236, 246, 0.9);
    }

    .group-order-panel__body::-webkit-scrollbar-thumb {
        border-radius: 999px;
        background: rgba(87, 121, 206, 0.58);
    }

    .group-order-list {
        display: grid;
        gap: 0.5rem;
    }

    .group-order-row {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        align-items: center;
        gap: 0.75rem;
        padding: 0.7rem;
        border: 1px solid rgba(120, 140, 175, 0.16);
        border-radius: 0.85rem;
        background: rgba(248, 250, 253, 0.92);
    }

    .group-order-row--dragging {
        opacity: 0.6;
    }

    .group-order-row__index {
        width: 1.8rem;
        height: 1.8rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 999px;
        background: rgba(87, 121, 206, 0.12);
        color: #2d436e;
        font-size: 0.82rem;
        font-weight: 800;
    }

    .group-order-row__content {
        min-width: 0;
        display: grid;
        gap: 0.15rem;
    }

    .group-order-row__name {
        overflow: hidden;
        color: #20304e;
        font-weight: 800;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .group-order-row__meta {
        color: rgba(54, 68, 96, 0.58);
        font-size: 0.78rem;
    }

    .group-order-row__handle {
        width: 2.15rem;
        height: 2.15rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border: 0;
        border-radius: 999px;
        background: transparent;
        color: rgba(54, 68, 96, 0.62);
        cursor: grab;
        touch-action: none;
        box-shadow: none;
    }

    .group-order-row__handle:hover,
    .group-order-row__handle:focus-visible {
        background: rgba(87, 121, 206, 0.1);
        color: #2d436e;
        outline: none;
    }

    .group-order-row__handle:active {
        cursor: grabbing;
    }

    .group-order-row__handle-grip {
        width: 0.9rem;
        height: 1.1rem;
        background:
            radial-gradient(circle, currentColor 0.08rem, transparent 0.09rem) 0 0 / 0.45rem 0.45rem,
            radial-gradient(circle, currentColor 0.08rem, transparent 0.09rem) 0.45rem 0 / 0.45rem 0.45rem;
        opacity: 0.8;
    }

    .group-order-row__handle-label {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
    }

    .group-order-drop-slot {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
        align-items: center;
        gap: 0.5rem;
        color: #5779ce;
        font-size: 0.76rem;
        font-weight: 800;
    }

    .group-order-drop-slot__line {
        height: 2px;
        border-radius: 999px;
        background: currentColor;
    }

    .group-order-drop-slot__label {
        white-space: nowrap;
    }

    .group-order-panel__actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.6rem;
        padding: 0.85rem 1.1rem 1.1rem;
        border-top: 1px solid rgba(120, 140, 175, 0.14);
        background: rgba(255, 255, 255, 0.98);
    }

    @media (max-width: 640px) {
        .group-order-toolbar--mobile-hidden {
            display: none;
        }

        .group-order-modal {
            align-items: center;
            justify-content: center;
            padding: 0.5rem;
        }

        .group-order-panel {
            width: min(30rem, calc(100vw - 1rem));
            max-height: min(30rem, calc(100dvh - 1rem));
            border-radius: 0.85rem;
            grid-template-rows: auto minmax(0, 1fr) auto;
        }

        .group-order-panel__header {
            align-items: center;
            gap: 0.65rem;
            padding: 0.58rem 0.62rem 0.48rem;
        }

        .group-order-panel__title {
            font-size: 0.94rem;
            line-height: 1.15;
        }

        .group-order-panel__description {
            display: none;
        }

        .group-order-panel__close {
            width: 2rem;
            height: 2rem;
        }

        .group-order-panel__body {
            padding: 0.45rem 0.42rem 0.5rem 0.55rem;
            overscroll-behavior: contain;
        }

        .group-order-list {
            gap: 0.32rem;
        }

        .group-order-row {
            grid-template-columns: 1.45rem minmax(0, 1fr) 2rem;
            gap: 0.48rem;
            min-height: 2.7rem;
            padding: 0.36rem 0.4rem;
            border-radius: 0.62rem;
        }

        .group-order-row__index {
            width: 1.45rem;
            height: 1.45rem;
            font-size: 0.72rem;
        }

        .group-order-row__content {
            gap: 0.05rem;
        }

        .group-order-row__name {
            font-size: 0.84rem;
            line-height: 1.12;
        }

        .group-order-row__meta {
            font-size: 0.66rem;
            line-height: 1.1;
        }

        .group-order-row__handle {
            width: 2rem;
            height: 2rem;
            border-radius: 999px;
        }

        .group-order-row__handle-grip {
            width: 0.72rem;
            height: 0.92rem;
            background:
                radial-gradient(circle, currentColor 0.07rem, transparent 0.08rem) 0 0 / 0.36rem 0.36rem,
                radial-gradient(circle, currentColor 0.07rem, transparent 0.08rem) 0.36rem 0 / 0.36rem 0.36rem;
        }

        .group-order-drop-slot {
            gap: 0.32rem;
            font-size: 0.64rem;
        }

        .group-order-panel__actions {
            position: sticky;
            bottom: 0;
            gap: 0.45rem;
            padding: 0.52rem 0.62rem calc(0.58rem + env(safe-area-inset-bottom));
        }

        .group-order-panel__actions .btn {
            flex: 1 1 0;
            min-height: 2.3rem;
            padding: 0.48rem 0.68rem;
            font-size: 0.82rem;
        }
    }
</style>
