<script lang="ts">
    import { modalStore } from '$lib/stores/modal';
    import { useI18n } from '$lib/i18n/context';
    import type { ConfirmTone } from '$lib/services/confirm';

    type ConfirmModalData = {
        title?: string;
        description?: string;
        confirmLabel?: string;
        cancelLabel?: string;
        tone?: ConfirmTone;
        onConfirm?: () => void | Promise<void>;
        onCancel?: () => void | Promise<void>;
    };

    let isOpen = $derived($modalStore.open === 'confirm-modal');
    let modalData = $derived((($modalStore.data ?? {}) as ConfirmModalData));
    let tone = $derived(modalData.tone ?? 'neutral');

    let pending = $state(false);
    const i18n = useI18n();

    function close() {
        modalStore.close();
    }

    async function cancel() {
        if (pending) return;
        pending = true;
        try {
            await modalData.onCancel?.();
        } finally {
            pending = false;
            close();
        }
    }

    async function confirm() {
        if (pending) return;
        pending = true;
        try {
            await modalData.onConfirm?.();
        } finally {
            pending = false;
            close();
        }
    }

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            void cancel();
        }
    }
</script>

{#if isOpen}
<div class="sheet-backdrop" role="presentation" onclick={() => void cancel()} onkeydown={handleKeydown}></div>
<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="confirm-title" tabindex="-1">
    <h2 id="confirm-title">{modalData.title ?? $i18n.t('app.telegram.confirm.title')}</h2>
    {#if modalData.description}
        <p class="confirm-description">{modalData.description}</p>
    {/if}

    <div class="confirm-actions">
        <button class="confirm-cancel" type="button" onclick={() => void cancel()} disabled={pending}>
            {modalData.cancelLabel ?? $i18n.t('app.telegram.confirm.cancel')}
        </button>
        <button class="confirm-confirm" class:confirm-confirm--danger={tone === 'danger'} type="button" onclick={() => void confirm()} disabled={pending}>
            {modalData.confirmLabel ?? $i18n.t('app.telegram.confirm.confirm')}
        </button>
    </div>

    <button class="close" type="button" onclick={() => void cancel()}>{$i18n.t('app.telegram.header.close')}</button>
</div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); max-width:28rem; margin:0 auto; }
    .sheet h2 { margin:0 0 .5rem; color:#18243d; font-size:1.1rem; font-weight:700; }
    .confirm-description { margin:0 0 1rem; color:#66718a; font-size:.9rem; line-height:1.45; }
    .confirm-actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; margin-top:.75rem; }
    .confirm-cancel { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; font-weight:600; cursor:pointer; }
    .confirm-cancel:disabled { opacity:.5; cursor:not-allowed; }
    .confirm-confirm { min-height:2.75rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .confirm-confirm:disabled { cursor:wait; opacity:.6; }
    .confirm-confirm--danger { background:#fff0f1; color:#c63c42; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
