<script lang="ts">
    import { toastStore } from '$lib/stores/toasts';
</script>

<div class="toast-host" aria-live="polite" aria-atomic="false">
    {#each $toastStore as toast (toast.id)}
    <div class="toast toast--{toast.variant}" role="alert">
        {toast.message}
    </div>
    {/each}
</div>

<style>
    .toast-host {
        position: fixed;
        bottom: 1.5rem;
        right: 1.5rem;
        z-index: 9999;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        pointer-events: none;
    }
    .toast {
        padding: 0.75rem 1.25rem;
        border-radius: 8px;
        color: #fff;
        font-size: 0.95rem;
        font-weight: 500;
        box-shadow: 0 4px 16px rgba(0,0,0,0.2);
        animation: slide-in 0.25s ease;
    }
    .toast--success { background: var(--color-success, #4caf50); }
    .toast--error   { background: var(--color-danger, #e53935); }
    .toast--info    { background: var(--color-primary, #7bc568); }

    @keyframes slide-in {
        from { transform: translateX(110%); opacity: 0; }
        to   { transform: translateX(0);    opacity: 1; }
    }
</style>
