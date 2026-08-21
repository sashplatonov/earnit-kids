<script lang="ts">
    import { tick } from 'svelte';

    export let open = false;
    export let labelledBy = '';
    export let busy = false;
    export let closeOnBackdrop = true;
    export let onClose: () => void = () => {};

    let dialog: HTMLDivElement;

    const focusableSelector = [
        'a[href]', 'button:not([disabled])', 'input:not([disabled])',
        'select:not([disabled])', 'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])',
    ].join(',');

    function focusFirst() {
        const first = dialog?.querySelector<HTMLElement>(focusableSelector);
        (first ?? dialog)?.focus();
    }

    function manageFocus(node: HTMLDivElement) {
        const returnFocus = typeof document !== 'undefined' && document.activeElement instanceof HTMLElement
            ? document.activeElement
            : null;
        void tick().then(() => focusFirst());
        return {
            destroy() {
                if (returnFocus?.isConnected) returnFocus.focus();
            },
        };
    }

    function close() {
        if (!busy) onClose();
    }

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            event.preventDefault();
            close();
            return;
        }
        if (event.key !== 'Tab') return;

        const focusable = [...dialog.querySelectorAll<HTMLElement>(focusableSelector)];
        if (!focusable.length) {
            event.preventDefault();
            dialog.focus();
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    function handleBackdropClick() {
        if (closeOnBackdrop) close();
    }
</script>

{#if open}
    <div class="backdrop" role="presentation" on:click={handleBackdropClick}></div>
    <div
        class="sheet"
        bind:this={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy || undefined}
        tabindex="-1"
        use:manageFocus
        on:keydown={handleKeydown}
    >
        <slot />
    </div>
{/if}

<style>
    .backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { box-sizing:border-box; position:fixed; inset:auto 0 0; z-index:41; width:100%; max-height:calc(100dvh - 1rem); overflow-y:auto; overscroll-behavior:contain; padding:1rem max(1rem, env(safe-area-inset-right)) calc(1rem + env(safe-area-inset-bottom)) max(1rem, env(safe-area-inset-left)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); -webkit-overflow-scrolling:touch; }
</style>
