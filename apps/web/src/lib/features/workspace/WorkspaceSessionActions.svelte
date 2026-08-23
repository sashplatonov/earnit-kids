<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { logout } from '$lib/services/api';

    const i18n = useI18n();
    let pending = false;
    let error = false;

    async function signOut() {
        if (pending) return;

        pending = true;
        error = false;
        if (await logout()) {
            window.location.assign('/public/index.html');
            return;
        }

        pending = false;
        error = true;
    }
</script>

<aside class="session-actions" aria-label={$i18n.t('app.sessionActions.label')}>
    <button class="logout" type="button" disabled={pending} on:click={() => void signOut()}>
        {pending ? $i18n.t('app.sessionActions.pending') : $i18n.t('app.sessionActions.logout')}
    </button>
    {#if error}
        <p class="error" role="alert">{$i18n.t('app.sessionActions.error')}</p>
    {/if}
</aside>

<style>
    .session-actions { box-sizing:border-box; display:flex; flex-wrap:wrap; align-items:center; justify-content:flex-end; gap:.6rem; width:min(100% - 2rem, 48rem); margin:0 auto; padding:.75rem 0 0; }
    .logout { min-width:44px; min-height:44px; padding:.55rem .9rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; }
    .logout:hover:not(:disabled) { background:#f5f7fb; }
    .logout:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .logout:disabled { cursor:wait; opacity:.7; }
    .error { flex-basis:100%; margin:0; color:#b42318; font-size:.875rem; text-align:right; }
    @media (max-width:700px) { .session-actions { width:min(100% - 1.5rem, 48rem); padding-top:.65rem; } }
</style>
