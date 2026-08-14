<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore, type Child } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { switchChild, refreshData } from '$lib/services/bootstrap';
    import {
        adminGetChildLink,
        adminGetInactiveChildren,
        adminSetChildActive,
        adminGetChildTelegram,
        adminCreateChildTelegramInvite,
        adminUnlinkChildTelegram,
        type ChildTelegramConnection,
    } from '$lib/services/api';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramRolesAccess from './TelegramRolesAccess.svelte';
    import TelegramNotifications from './TelegramNotifications.svelte';
    import TelegramMyAccount from './TelegramMyAccount.svelte';
    import TelegramEmailSettings from './TelegramEmailSettings.svelte';
    import TelegramParents from './TelegramParents.svelte';
    import TelegramLimits from './TelegramLimits.svelte';
    import TelegramImport from './TelegramImport.svelte';

    const i18n = useI18n();

    let inviteOpen = false;
    let rolesOpen = false;
    let notificationsOpen = false;
    let myAccountOpen = false;
    let emailSettingsOpen = false;
    let parentsOpen = false;
    let limitsOpen = false;
    let importOpen = false;
    let limitsChild: Child | null = null;
    let link = '';
    let linkBusy = false;
    let linkError = '';
    let switching = false;
    let switchError = '';
    let inactiveChildren: Child[] = [];
    let manageChild: Child | null = null;
    let confirmChild: Child | null = null;
    let statusBusy = false;
    let statusError = '';
    let telegram: ChildTelegramConnection | null = null;
    let telegramBusy = false;
    let telegramError = '';
    let inviteView = false;
    let inviteLink = '';
    let inviteBusy = false;
    let copied = false;

    onMount(() => {
        void loadInactive();
    });

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

    async function loadInactive() {
        inactiveChildren = await adminGetInactiveChildren();
    }

    async function applyStatus(child: Child, active: boolean) {
        statusBusy = true;
        statusError = '';
        const ok = await adminSetChildActive(child.id, active);
        if (ok) {
            await refreshData();
            await loadInactive();
            manageChild = null;
            confirmChild = null;
        } else {
            statusError = $i18n.t('app.telegram.family.statusError');
        }
        statusBusy = false;
    }

    function openManage(child: Child) {
        statusError = '';
        telegramError = '';
        inviteView = false;
        inviteLink = '';
        copied = false;
        telegram = null;
        manageChild = child;
        void loadTelegram(child.id);
    }

    async function loadTelegram(childId: string | number) {
        telegram = await adminGetChildTelegram(childId);
    }

    async function createInvite() {
        if (!manageChild) return;
        inviteBusy = true;
        telegramError = '';
        const result = await adminCreateChildTelegramInvite(manageChild.id);
        inviteBusy = false;
        if (result) inviteLink = result.launchUrl;
        else telegramError = $i18n.t('app.telegram.family.telegramError');
    }

    async function copyInvite() {
        try {
            await navigator.clipboard.writeText(inviteLink);
            copied = true;
        } catch {
            copied = false;
        }
    }

    async function unlinkTelegram() {
        if (!manageChild) return;
        telegramBusy = true;
        telegramError = '';
        const ok = await adminUnlinkChildTelegram(manageChild.id);
        if (ok) await loadTelegram(manageChild.id);
        else telegramError = $i18n.t('app.telegram.family.telegramError');
        telegramBusy = false;
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
                <div class="childrow-wrap">
                    <button class:current={$appStore.currentChildId == child.id} class="childrow" type="button" disabled={switching} on:click={() => select(child.id)} aria-pressed={$appStore.currentChildId == child.id}>
                        <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                        <span class="grow"><span class="name">{child.nickname}</span>{#if $appStore.currentChildId == child.id}<span class="badge"><TelegramIcon name="checkCircle" size={12} label={$i18n.t('app.telegram.family.currentChild')} />{$i18n.t('app.telegram.family.currentChild')}</span>{/if}</span>
                        <span class="balance"><TelegramCoin size={14} />{child.balance}</span>
                    </button>
                    <button class="childrow-more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: child.nickname })} on:click={() => openManage(child)}><TelegramIcon name="more" size={18} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                </div>
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

    {#if inactiveChildren.length}
        <h2 class="section-title">{$i18n.t('app.telegram.family.inactiveChildren')} · {inactiveChildren.length}</h2>
        <div class="flat">
            {#each inactiveChildren as child (child.id)}
                <div class="inactive-row">
                    <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                    <span class="grow"><span class="name">{child.nickname}</span></span>
                    <button class="reactivate" type="button" disabled={statusBusy} on:click={() => applyStatus(child, true)}>{$i18n.t('app.telegram.family.reactivate')}</button>
                </div>
            {/each}
        </div>
    {/if}

    <h2 class="section-title">{$i18n.t('app.telegram.family.familySettings')}</h2>
    <div class="settings">
        <button class="setting" type="button" on:click={() => rolesOpen = true}><span class="setting-icon"><TelegramIcon name="shield" size={20} label={$i18n.t('app.telegram.family.rolesAndAccess')} /></span><span class="grow">{$i18n.t('app.telegram.family.rolesAndAccess')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => inviteOpen = true}><span class="setting-icon"><TelegramIcon name="link" size={20} label={$i18n.t('app.telegram.family.invitations')} /></span><span class="grow">{$i18n.t('app.telegram.family.invitations')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => notificationsOpen = true}><span class="setting-icon"><TelegramIcon name="bell" size={20} label={$i18n.t('app.telegram.family.notifications')} /></span><span class="grow">{$i18n.t('app.telegram.family.notifications')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => myAccountOpen = true}><span class="setting-icon"><TelegramIcon name="users" size={20} label={$i18n.t('app.telegram.myAccount.title')} /></span><span class="grow">{$i18n.t('app.telegram.myAccount.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => parentsOpen = true}><span class="setting-icon"><TelegramIcon name="shield" size={20} label={$i18n.t('app.telegram.parents.title')} /></span><span class="grow">{$i18n.t('app.telegram.parents.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => importOpen = true}><span class="setting-icon"><TelegramIcon name="upload" size={20} label={$i18n.t('app.telegram.import.title')} /></span><span class="grow">{$i18n.t('app.telegram.import.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
    </div>
</div>

<TelegramRolesAccess open={rolesOpen} on:close={() => rolesOpen = false} />
<TelegramNotifications open={notificationsOpen} on:close={() => notificationsOpen = false} />
<TelegramMyAccount open={myAccountOpen} on:close={() => myAccountOpen = false} on:openEmail={() => { myAccountOpen = false; emailSettingsOpen = true; }} />
<TelegramEmailSettings open={emailSettingsOpen} on:close={() => emailSettingsOpen = false} />
<TelegramParents open={parentsOpen} on:close={() => parentsOpen = false} />
<TelegramImport open={importOpen} on:close={() => importOpen = false} />
<TelegramLimits open={limitsOpen} child={limitsChild} on:close={() => limitsOpen = false} on:saved={() => { if (manageChild) void loadTelegram(manageChild.id); }} />

{#if manageChild}
    <div class="sheet-backdrop" role="presentation" on:click={() => manageChild = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="child-manage-title" tabindex="-1">
        <h2 id="child-manage-title">{manageChild.nickname}</h2>

        <div class="settings">
            <div class="setting"><span class="setting-icon"><TelegramIcon name="send" size={20} label={$i18n.t('app.telegram.family.telegram')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.family.telegram')}</span><span class="setting-meta">{telegram?.linked ? $i18n.t('app.telegram.family.telegramLinked') : $i18n.t('app.telegram.family.telegramNotLinked')}</span></span><span class="manage-badge" class:badge-active={telegram?.linked}>{telegram?.linked ? $i18n.t('app.telegram.family.telegramLinked') : $i18n.t('app.telegram.family.telegramNotLinked')}</span></div>
            <button class="setting" type="button" on:click={() => { limitsChild = manageChild; limitsOpen = true; }}><span class="setting-icon"><TelegramIcon name="gauge" size={20} label={$i18n.t('app.telegram.family.limits')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.family.limits')}</span><span class="setting-meta">{$i18n.t('app.telegram.family.limitsMeta')}</span></span><TelegramIcon name="arrowRight" size={18} label="Open" /></button>
        </div>

        {#if inviteView}
            <div class="invite-block">
                <p class="confirm-meta">{$i18n.t('app.telegram.family.childTelegramTitle')}</p>
                <p class="confirm-meta">{$i18n.t('app.telegram.family.childTelegramHint', { name: manageChild.nickname })}</p>
                {#if !inviteLink}
                    <button class="reactivate-full" type="button" disabled={inviteBusy} on:click={createInvite}><TelegramIcon name="link" size={18} label={$i18n.t('app.telegram.family.createTelegramLink')} />{$i18n.t('app.telegram.family.createTelegramLink')}</button>
                {:else}
                    <p class="manage-meta"><strong>{$i18n.t('app.telegram.family.linkReady')}</strong></p>
                    <div class="invite-actions">
                        <button class="reactivate-full" type="button" on:click={copyInvite}><TelegramIcon name="copy" size={18} label={$i18n.t('app.telegram.family.copyLink')} />{copied ? $i18n.t('app.telegram.family.copied') : $i18n.t('app.telegram.family.copyLink')}</button>
                        <button class="close" type="button" on:click={() => { inviteLink = ''; copied = false; }}>{$i18n.t('app.telegram.family.createNew')}</button>
                    </div>
                {/if}
            </div>
        {:else}
            {#if telegram?.linked}
                <div class="telegram-actions">
                    <button class="reactivate-full" type="button" on:click={() => { inviteView = true; inviteLink = ''; copied = false; }}><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.family.relinkTelegram')} />{$i18n.t('app.telegram.family.relinkTelegram')}</button>
                    <button class="deactivate" type="button" disabled={telegramBusy} on:click={unlinkTelegram}><TelegramIcon name="unlink" size={18} label={$i18n.t('app.telegram.family.unlinkTelegram')} />{$i18n.t('app.telegram.family.unlinkTelegram')}</button>
                </div>
            {:else}
                <button class="reactivate-full" type="button" on:click={() => { inviteView = true; }}><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.family.linkTelegram')} />{$i18n.t('app.telegram.family.linkTelegram')}</button>
            {/if}
        {/if}

        <h3 class="sheet-subtitle">{$i18n.t('app.telegram.family.status')}</h3>
        <div class="manage-status"><span class="manage-label">{$i18n.t('app.telegram.family.visibleInAppAndBot')}</span><span class:badge-active={manageChild.status !== 'INACTIVE'} class="manage-badge">{manageChild.status === 'INACTIVE' ? $i18n.t('app.telegram.family.inactive') : $i18n.t('app.telegram.family.active')}</span></div>
        {#if manageChild.status !== 'INACTIVE'}
            <button class="deactivate" type="button" on:click={() => { statusError = ''; confirmChild = manageChild; }}><TelegramIcon name="pause" size={18} label={$i18n.t('app.telegram.family.deactivate')} />{$i18n.t('app.telegram.family.deactivate')}</button>
        {:else}
            <button class="reactivate-full" type="button" disabled={statusBusy} on:click={() => manageChild && applyStatus(manageChild, true)}><TelegramIcon name="play" size={18} label={$i18n.t('app.telegram.family.reactivate')} />{$i18n.t('app.telegram.family.reactivate')}</button>
        {/if}
        {#if telegramError || statusError}<p class="error" role="alert">{telegramError || statusError}</p>{/if}
        <button class="close" type="button" on:click={() => manageChild = null}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

{#if confirmChild}
    <div class="sheet-backdrop" role="presentation" on:click={() => confirmChild = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="child-confirm-title" tabindex="-1">
        <h2 id="child-confirm-title">{$i18n.t('app.telegram.family.deactivateTitle', { name: confirmChild.nickname })}</h2>
        <p class="confirm-meta">{$i18n.t('app.telegram.family.deactivateDescription')}</p>
        <button class="deactivate" type="button" disabled={statusBusy} on:click={() => confirmChild && applyStatus(confirmChild, false)}>{$i18n.t('app.telegram.family.deactivateConfirm')}</button>
        <button class="close" type="button" disabled={statusBusy} on:click={() => confirmChild = null}>{$i18n.t('app.telegram.family.cancel')}</button>
    </div>
{/if}

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
    .childrow-wrap { display:flex; align-items:center; border-bottom:1px solid #edf0f5; }
    .childrow-wrap:last-child { border-bottom:0; }
    .childrow-wrap .childrow { flex:1; min-width:0; border-bottom:0; }
    .childrow-more { display:grid; place-items:center; width:2.5rem; height:2.5rem; flex:0 0 auto; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .inactive-row { display:flex; align-items:center; gap:.6rem; min-height:3.25rem; padding:.4rem 0; border-bottom:1px solid #edf0f5; }
    .inactive-row:last-child { border-bottom:0; }
    .inactive-row .avatar { opacity:.55; }
    .reactivate { min-height:2.25rem; padding:.3rem .7rem; border:1px solid #3867d6; border-radius:.6rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; }
    .reactivate:disabled { cursor:wait; opacity:.6; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    .sheet h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .manage-status { display:flex; align-items:center; justify-content:space-between; gap:.6rem; padding:.6rem 0; border-bottom:1px solid #edf0f5; }
    .manage-label { color:#33415f; font-weight:600; }
    .manage-badge { padding:.2rem .55rem; border-radius:999px; background:#f1f3f7; color:#66718a; font-size:.78rem; font-weight:700; }
    .manage-badge.badge-active { background:#eaf7ef; color:#17884b; }
    .manage-meta { margin:.5rem 0; color:#66718a; font-size:.85rem; line-height:1.4; }
    .confirm-meta { margin:.25rem 0 .9rem; color:#66718a; font-size:.9rem; line-height:1.45; }
    .deactivate { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.5rem; padding:.55rem .7rem; border:0; border-radius:.7rem; background:#fff0f1; color:#c63c42; font:inherit; font-weight:700; cursor:pointer; }
    .deactivate:disabled { cursor:wait; opacity:.6; }
    .reactivate-full { display:inline-flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.5rem; padding:.55rem .7rem; border:1px solid #3867d6; border-radius:.7rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; cursor:pointer; }
    .reactivate-full:disabled { cursor:wait; opacity:.6; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .close:disabled { cursor:wait; opacity:.6; }
    .setting-title { display:block; font-weight:600; }
    .setting-meta { display:block; margin-top:.1rem; color:#66718a; font-size:.78rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .telegram-actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; }
    .telegram-actions .reactivate-full, .telegram-actions .deactivate { margin-top:0; }
    .invite-block { margin-top:.75rem; padding-top:.75rem; border-top:1px solid #edf0f5; }
    .invite-block .manage-meta { margin-top:.5rem; }
    .invite-actions { display:grid; gap:.6rem; }
</style>
