<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { switchChild } from '$lib/services/bootstrap';
    import { adminGetChildLink } from '$lib/services/api';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';

    const i18n = useI18n();

    let inviteOpen = false;
    let link = '';
    let linkBusy = false;
    let linkError = '';
    let switching = false;
    let switchError = '';

    async function select(id: string | number) {
        if ($appStore.currentChildId == id) return;
        switching = true;
        switchError = '';
        await switchChild(id);
        switching = false;
        if ($appStore.currentChildId != id) switchError = $i18n.t('app.telegram.family.switchError');
    }

    async function createLink() {
        const id = $appStore.currentChildId;
        if (id == null) {
            linkError = $i18n.t('app.telegram.family.chooseChildFirst');
            return;
        }
        linkBusy = true;
        linkError = '';
        const result = await adminGetChildLink(id);
        linkBusy = false;
        if (result) link = result.link;
        else linkError = $i18n.t('app.telegram.family.linkError');
    }
</script>

<div class="family">
    <h1 id="family-title">{$i18n.t('app.telegram.family.title')}</h1>

    <h2 class="section-title">{$i18n.t('app.telegram.family.children')}</h2>
    {#if !$appStore.children.length}
        <p class="muted">{$i18n.t('app.telegram.family.noChildren')}</p>
    {:else}
        <div class="flat">
            {#each $appStore.children as child (child.id)}
                <button class:current={$appStore.currentChildId == child.id} class="childrow" type="button" disabled={switching} on:click={() => select(child.id)} aria-pressed={$appStore.currentChildId == child.id}>
                    <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                    <span class="grow"><span class="name">{child.nickname}</span>{#if $appStore.currentChildId == child.id}<span class="badge"><TelegramIcon name="checkCircle" size={12} label={$i18n.t('app.telegram.family.currentChild')} />{$i18n.t('app.telegram.family.currentChild')}</span>{/if}</span>
                    <span class="balance"><TelegramCoin size={14} />{child.balance}</span>
                </button>
            {/each}
        </div>
    {/if}

    <button class="add-child" type="button" aria-expanded={inviteOpen} on:click={() => inviteOpen = !inviteOpen}><TelegramIcon name="addChild" size={20} label={$i18n.t('app.telegram.family.addChild')} /><span>{$i18n.t('app.telegram.family.addChild')}</span></button>

    {#if inviteOpen}
        <div class="invite">
            <p class="muted">{$i18n.t('app.telegram.family.createLinkHint')}</p>
            <button type="button" on:click={createLink} disabled={linkBusy}><TelegramIcon name="link" size={18} label={$i18n.t('app.telegram.family.createLink')} />{linkBusy ? $i18n.t('app.telegram.family.creating') : $i18n.t('app.telegram.family.createLink')}</button>
            {#if link}<label for="family-invite-link">{$i18n.t('app.telegram.family.inviteLink')}</label><input id="family-invite-link" readonly value={link} on:focus={(event) => event.currentTarget.select()} />{/if}
            {#if linkError}<p class="error" role="alert">{linkError}</p>{/if}
        </div>
    {/if}
    {#if switchError}<p class="error" role="alert">{switchError}</p>{/if}

    <h2 class="section-title">{$i18n.t('app.telegram.family.familySettings')}</h2>
    <div class="settings">
        <div class="setting"><span class="setting-icon"><TelegramIcon name="shield" size={20} label={$i18n.t('app.telegram.family.rolesAndAccess')} /></span><span class="grow">{$i18n.t('app.telegram.family.rolesAndAccess')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></div>
        <button class="setting" type="button" on:click={() => inviteOpen = true}><span class="setting-icon"><TelegramIcon name="link" size={20} label={$i18n.t('app.telegram.family.invitations')} /></span><span class="grow">{$i18n.t('app.telegram.family.invitations')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <div class="setting"><span class="setting-icon"><TelegramIcon name="bell" size={20} label={$i18n.t('app.telegram.family.notifications')} /></span><span class="grow">{$i18n.t('app.telegram.family.notifications')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></div>
    </div>
</div>

<style>
    .family { width:100%; }
    h1 { margin:0 0 .5rem; color:#18243d; font-size:1.35rem; }
    .section-title { margin:.9rem 0 .45rem; color:#18243d; font-size:1rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .childrow { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3.25rem; padding:.4rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .childrow:last-child { border-bottom:0; }
    .childrow.current { color:#18243d; }
    .avatar { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:50%; background:#eef0ff; color:#5b63e9; font-weight:800; }
    .grow { flex:1; min-width:0; }
    .name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:700; }
    .badge { display:inline-flex; align-items:center; gap:.2rem; margin-top:.15rem; color:#3867d6; font-size:.72rem; font-weight:700; }
    .balance { display:inline-flex; align-items:center; gap:.3rem; color:#573d00; font-weight:700; white-space:nowrap; }
    .add-child { display:inline-flex; align-items:center; justify-content:center; gap:.5rem; width:100%; min-height:2.75rem; margin-top:.6rem; padding:.5rem .7rem; border:1px solid #3867d6; border-radius:.75rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .invite { margin-top:.6rem; padding:.7rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; }
    .invite .muted { margin:0 0 .5rem; color:#66718a; font-size:.85rem; line-height:1.4; }
    .invite button { display:inline-flex; align-items:center; gap:.4rem; min-height:2.75rem; padding:.45rem .7rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; cursor:pointer; }
    .invite button:disabled { cursor:wait; opacity:.6; }
    .invite label { display:block; margin-top:.6rem; font-size:.85rem; color:#33415f; }
    .invite input { box-sizing:border-box; width:100%; min-height:2.5rem; margin-top:.25rem; padding:.5rem; border:1px solid #dfe4ee; border-radius:.6rem; font:inherit; }
    .settings { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .setting { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; }
    .setting:last-child { border-bottom:0; }
    button.setting { cursor:pointer; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting .grow { font-weight:600; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
</style>
