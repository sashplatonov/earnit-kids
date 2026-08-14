<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminGetChildLink } from '$lib/services/api';
    import TelegramIcon from './TelegramIcon.svelte';
    let link = '';
    let error = '';
    async function createLink() {
        const id = $appStore.currentChildId;
        if (id == null) { error = 'Choose a child first.'; return; }
        error = '';
        const result = await adminGetChildLink(id);
        if (result) link = result.link;
        else error = 'Could not create an invite. Try again.';
    }
</script>

<section class="panel" aria-labelledby="invite-title">
    <h2 id="invite-title">Invite child</h2><p class="muted">Create a secure child sign-in link. Telegram linking is completed after the child opens it.</p>
    <button type="button" on:click={createLink}><TelegramIcon name="add" size={18} label="Create invite link" />Create invite link</button>
    {#if link}<label for="child-invite-link">Invite link</label><input id="child-invite-link" readonly value={link} on:focus={(event) => event.currentTarget.select()} />{/if}
    {#if error}<p class="error" role="alert">{error}</p>{/if}
</section>

<style>
    .panel { width:100%; margin-bottom:1rem; } h2 { margin:0 0 .5rem; color:#18243d; font-size:1.05rem; } .muted { color:#66718a; line-height:1.45; } button { min-height:2.75rem; padding:.6rem .8rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; cursor:pointer; } label { display:block; margin-top:.75rem; font-size:.85rem; color:#33415f; } input { box-sizing:border-box; width:100%; min-height:2.75rem; margin-top:.25rem; padding:.6rem; border:1px solid #dfe4ee; border-radius:.6rem; font:inherit; } .error { color:#a33b3b; }
</style>
