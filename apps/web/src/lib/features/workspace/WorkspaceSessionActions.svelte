<script lang="ts">
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';
    import { logout } from '$lib/services/api';

    const i18n = useI18n();
    export let inline = false;
    let pending = false;
    let error = false;

    async function signOut() {
        if (pending) return;

        pending = true;
        error = false;
        if (await logout()) {
            window.location.assign('/');
            return;
        }

        pending = false;
        error = true;
    }
</script>

<div class:session-actions-inline={inline} class="session-actions" role={inline ? undefined : 'group'} aria-label={$i18n.t('app.sessionActions.label')}>
    <button class="logout" type="button" disabled={pending} on:click={() => void signOut()}>
        <TelegramIcon name="logout" size={18} label={undefined} />
        {pending ? $i18n.t('app.sessionActions.pending') : $i18n.t('app.sessionActions.logout')}
    </button>
    {#if error}
        <p class="error" role="alert">{$i18n.t('app.sessionActions.error')}</p>
    {/if}
</div>

<style>
    .session-actions { box-sizing:border-box; display:flex; flex-wrap:wrap; align-items:center; justify-content:flex-end; gap:.6rem; width:min(100% - 2rem, 48rem); margin:0 auto; padding:.75rem 0 0; }
    .session-actions-inline { width:auto; margin:0; padding:0; }
    .logout { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; min-width:44px; min-height:44px; padding:.55rem .9rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; white-space:nowrap; }
    .logout:hover:not(:disabled) { background:#f5f7fb; }
    .logout:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .logout:disabled { cursor:wait; opacity:.7; }
    .error { flex-basis:100%; margin:0; color:#b42318; font-size:.875rem; text-align:right; }
    .session-actions-inline .error { position:absolute; top:calc(100% + .5rem); right:0; width:max-content; max-width:18rem; }
    @media (max-width:700px) { .session-actions { width:min(100% - 1.5rem, 48rem); padding-top:.65rem; } .session-actions-inline { width:auto; padding-top:0; } }
</style>
