<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore, type Child } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { useFamilyActions } from '$lib/telegram/services/familyActions';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import type { ChildTelegramConnection } from '$lib/services/api';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import TelegramParentAccess from './TelegramParentAccess.svelte';
    import TelegramNotifications from './TelegramNotifications.svelte';
    import TelegramMyAccount from './TelegramMyAccount.svelte';
    import TelegramEmailSettings from './TelegramEmailSettings.svelte';
    import TelegramLimits from './TelegramLimits.svelte';
    import TelegramImport from './TelegramImport.svelte';
    import type { MembershipPermission } from '$lib/types/auth';

    const i18n = useI18n();
    const familyActions = useFamilyActions();

    export let permission: MembershipPermission | null = null;
    export let demoMode = false;

    let inviteOpen = false;
    let newChildName = '';
    let addChildBusy = false;
    let addChildError = '';
    let accessOpen = false;
    let notificationsOpen = false;
    let myAccountOpen = false;
    let emailSettingsOpen = false;
    let limitsOpen = false;
    let importOpen = false;
    let familyLocaleOpen = false;
    let limitsChild: Child | null = null;
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
    let inviteLink = '';
    let inviteBusy = false;
    let copied = false;
    let magicLink = '';
    let magicLinkPending = false;
    let magicLinkBusy = false;
    let magicLinkError = '';
    let magicLinkCopied = false;
    let unavailableNotice = '';

    onMount(() => {
        void loadInactive();
    });

    async function select(id: string | number) {
        if ($appStore.currentChildId == id) return;
        switching = true;
        switchError = '';
        await familyActions.selectChild(id);
        switching = false;
        if ($appStore.currentChildId != id) switchError = $i18n.t('app.telegram.family.switchError');
    }

    async function addChild() {
        const name = newChildName.trim();
        if (!name) {
            addChildError = $i18n.t('app.telegram.family.addChildNameRequired');
            return;
        }
        addChildBusy = true;
        addChildError = '';
        const result = await familyActions.addChild(name);
        addChildBusy = false;
        if (result) {
            newChildName = '';
            inviteOpen = false;
            await familyActions.refresh();
        } else {
            addChildError = $i18n.t('app.telegram.family.addChildError');
        }
    }

    async function loadInactive() {
        inactiveChildren = await familyActions.getInactive();
    }

    async function applyStatus(child: Child, active: boolean) {
        statusBusy = true;
        statusError = '';
        const ok = await familyActions.setChildActive(child.id, active);
        if (ok) {
            await familyActions.refresh();
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
        inviteLink = '';
        copied = false;
        magicLink = '';
        magicLinkPending = false;
        magicLinkError = '';
        magicLinkCopied = false;
        telegram = null;
        manageChild = child;
        if (!demoMode) {
            void loadTelegram(child.id);
            void loadMagicLinkStatus(child.id);
        }
    }

    async function loadTelegram(childId: string | number) {
        telegram = await familyActions.getTelegram(childId);
    }

    async function createInvite() {
        if (!manageChild) return;
        if (demoMode) { inviteLink = `https://t.me/earnit_demo?child=${encodeURIComponent(String(manageChild.id))}`; return; }
        inviteBusy = true;
        telegramError = '';
        inviteLink = '';
        copied = false;
        const result = await familyActions.createTelegramInvite(manageChild.id);
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
        if (demoMode) { telegram = null; return; }
        telegramBusy = true;
        telegramError = '';
        const ok = await familyActions.unlinkTelegram(manageChild.id);
        if (ok) await loadTelegram(manageChild.id);
        else telegramError = $i18n.t('app.telegram.family.telegramError');
        telegramBusy = false;
    }

    async function loadMagicLinkStatus(childId: string | number) {
        const links = await familyActions.getMagicLinkStatus(childId);
        magicLinkPending = Array.isArray(links) && links.some((link) => (
            typeof link === 'object' && link !== null && 'status' in link
            && String(link.status).toLowerCase() === 'pending'
        ));
    }

    async function createMagicLink() {
        if (!manageChild) return;
        if (demoMode) { magicLink = `https://earnit-kids.example/demo/child/${encodeURIComponent(String(manageChild.id))}`; magicLinkPending = true; return; }
        magicLinkBusy = true;
        magicLinkError = '';
        const result = await familyActions.issueMagicLink(manageChild.id);
        magicLinkBusy = false;
        if (result?.link) {
            magicLink = result.link;
            magicLinkPending = true;
        } else magicLinkError = $i18n.t('app.telegram.family.magicLinkError');
    }

    async function revokeMagicLink() {
        if (!manageChild) return;
        if (demoMode) { magicLink = ''; magicLinkPending = false; return; }
        magicLinkBusy = true;
        magicLinkError = '';
        const ok = await familyActions.revokeMagicLink(manageChild.id);
        magicLinkBusy = false;
        if (ok) {
            magicLink = '';
            magicLinkPending = false;
        } else magicLinkError = $i18n.t('app.telegram.family.magicLinkError');
    }

    async function copyMagicLink() {
        try {
            await navigator.clipboard.writeText(magicLink);
            magicLinkCopied = true;
        } catch {
            magicLinkCopied = false;
        }
    }
</script>

<div class="family">
    <h1 id="family-title">{$i18n.t('app.telegram.family.title')}</h1>

    <div class="quick-actions">
        {#if $appStore.isAdmin}
            <a class="quick-action" href="/telegram/dashboard">
                <span class="setting-icon"><TelegramIcon name="statistics" size={20} label={$i18n.t('admin.settings.dashboardTitle')} /></span>
                <span><strong>{$i18n.t('admin.settings.dashboardTitle')}</strong><small>{$i18n.t('app.telegram.family.statisticsMeta')}</small></span>
            </a>
        {/if}
        <button class="quick-action" type="button" aria-expanded={inviteOpen} on:click={() => inviteOpen = !inviteOpen}>
            <span class="setting-icon"><TelegramIcon name="addChild" size={20} label={$i18n.t('app.telegram.family.addChild')} /></span>
            <span><strong>{$i18n.t('app.telegram.family.addChild')}</strong><small>{$i18n.t('app.telegram.family.addChildMeta')}</small></span>
        </button>
    </div>

    <h2 class="section-title">{$i18n.t('app.telegram.family.children')}</h2>
    {#if !$appStore.children.length}
        <p class="muted">{$i18n.t('app.telegram.family.noChildren')}</p>
    {:else}
        <div class="children-list">
            {#each $appStore.children as child (child.id)}
                <div class="childrow-wrap">
                    <button class:current={$appStore.currentChildId == child.id} class="childrow" type="button" disabled={switching} on:click={() => { select(child.id); openManage(child); }} aria-pressed={$appStore.currentChildId == child.id}>
                        <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                        <span class="grow"><span class="name">{child.nickname}</span>{#if $appStore.currentChildId == child.id}<span class="badge"><TelegramIcon name="checkCircle" size={12} label={$i18n.t('app.telegram.family.currentChild')} />{$i18n.t('app.telegram.family.currentChild')}</span>{/if}</span>
                        {#if $appStore.currentChildId != child.id}<span class="balance"><TelegramCoin size={14} />{child.balance}</span>{/if}
                    </button>
                    <button class="childrow-more" type="button" aria-label={$i18n.t('app.telegram.tasks.actionsFor', { name: child.nickname })} on:click={() => openManage(child)}><TelegramIcon name="more" size={18} label={$i18n.t('app.telegram.tasks.moreActions')} /></button>
                </div>
            {/each}
        </div>
    {/if}

    {#if inviteOpen}
        <div class="invite">
            <p class="muted">{$i18n.t('app.telegram.family.addChildNameHint')}</p>
            <input id="family-new-child-name" class="invite-input" bind:value={newChildName} placeholder={$i18n.t('app.telegram.family.addChildNamePlaceholder')} disabled={addChildBusy} />
            <button type="button" disabled={addChildBusy} on:click={addChild}><TelegramIcon name="addChild" size={18} label={$i18n.t('app.telegram.family.addChild')} />{addChildBusy ? $i18n.t('app.telegram.family.addingChild') : $i18n.t('app.telegram.family.addChild')}</button>
            {#if addChildError}<p class="error" role="alert">{addChildError}</p>{/if}
        </div>
    {/if}
    {#if switchError}<p class="error" role="alert">{switchError}</p>{/if}

    {#if inactiveChildren.length}
        <div class="inactive-notice">
            {#each inactiveChildren as child (child.id)}
                <div class="inactive-row">
                    <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                    <span class="grow"><span class="name">{child.nickname}</span><span class="inactive-meta">{$i18n.t('app.telegram.family.inactiveMeta')}</span></span>
                    <button class="reactivate" type="button" disabled={statusBusy} on:click={() => applyStatus(child, true)}><TelegramIcon name="play" size={16} label={$i18n.t('app.telegram.family.reactivate')} />{$i18n.t('app.telegram.family.reactivate')}</button>
                </div>
            {/each}
        </div>
    {/if}

    <h2 class="section-title">{$i18n.t('app.telegram.family.familySettings')}</h2>
    <div class="settings">
        {#if permission === 'family_admin' || $appStore.permission === 'family_admin'}
            <div class="setting-group">
                <button class="setting" type="button" aria-expanded={familyLocaleOpen} aria-controls="family-language-panel" on:click={() => familyLocaleOpen = !familyLocaleOpen}>
                    <span class="setting-icon"><TelegramIcon name="languages" size={20} label={$i18n.t('app.telegram.family.familyLanguage')} /></span>
                    <span class="grow"><span class="setting-title">{$i18n.t('app.telegram.family.familyLanguage')}</span><span class="setting-meta">{$i18n.t('app.telegram.family.familyLanguageMeta', { locale: $i18n.t(`common.locale.${$i18n.locale}`) })}</span></span>
                    <TelegramIcon name="chevronDown" size={18} label={$i18n.t('common.actions.open')} />
                </button>
                {#if familyLocaleOpen}
                    <div id="family-language-panel" class="family-locale-panel">
                        <LocaleSwitcher familyManaged={!demoMode} mode={demoMode ? 'route' : 'family'} compact />
                    </div>
                {/if}
            </div>
        {/if}
        <button class="setting" type="button" on:click={() => accessOpen = true}><span class="setting-icon"><TelegramIcon name="shield" size={20} label={$i18n.t('app.telegram.parents.title')} /></span><span class="grow">{$i18n.t('app.telegram.parents.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => myAccountOpen = true}><span class="setting-icon"><TelegramIcon name="users" size={20} label={$i18n.t('app.telegram.myAccount.title')} /></span><span class="grow">{$i18n.t('app.telegram.myAccount.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => notificationsOpen = true}><span class="setting-icon"><TelegramIcon name="bell" size={20} label={$i18n.t('app.telegram.family.notifications')} /></span><span class="grow">{$i18n.t('app.telegram.family.notifications')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        <button class="setting" type="button" on:click={() => importOpen = true}><span class="setting-icon"><TelegramIcon name="upload" size={20} label={$i18n.t('app.telegram.import.title')} /></span><span class="grow">{$i18n.t('app.telegram.import.title')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
    </div>
    {#if unavailableNotice}<p class="demo-unavailable" role="alert">{unavailableNotice}</p>{/if}
</div>

<TelegramParentAccess open={accessOpen} demoMode={demoMode} onClose={() => accessOpen = false} />
<TelegramNotifications open={notificationsOpen} demoMode={demoMode} onClose={() => notificationsOpen = false} />
<TelegramMyAccount open={myAccountOpen} demoMode={demoMode} onClose={() => myAccountOpen = false} onOpenEmail={() => { myAccountOpen = false; emailSettingsOpen = true; }} />
<TelegramEmailSettings open={emailSettingsOpen} demoMode={demoMode} onClose={() => emailSettingsOpen = false} />
<TelegramImport open={importOpen} demoMode={demoMode} onClose={() => importOpen = false} />

{#if manageChild}
    <div class="sheet-backdrop" role="presentation" on:click={() => manageChild = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="child-manage-title" tabindex="-1">
        <h2 id="child-manage-title">{manageChild.nickname}</h2>

        <div class="settings">
            <div class="setting"><span class="setting-icon"><TelegramIcon name="send" size={20} label={$i18n.t('app.telegram.family.telegram')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.family.telegram')}</span><span class="setting-meta">{telegram?.linked ? $i18n.t('app.telegram.family.telegramLinked') : $i18n.t('app.telegram.family.telegramNotLinked')}</span></span><span class="manage-badge" class:badge-active={telegram?.linked}>{telegram?.linked ? $i18n.t('app.telegram.family.telegramLinked') : $i18n.t('app.telegram.family.telegramNotLinked')}</span></div>
            <button class="setting" type="button" on:click={() => { limitsChild = manageChild; manageChild = null; limitsOpen = true; }}><span class="setting-icon"><TelegramIcon name="gauge" size={20} label={$i18n.t('app.telegram.family.limits')} /></span><span class="grow"><span class="setting-title">{$i18n.t('app.telegram.family.limits')}</span><span class="setting-meta">{$i18n.t('app.telegram.family.limitsMeta')}</span></span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('common.actions.open')} /></button>
        </div>

        <section class="magic-link" aria-labelledby="child-magic-link-title">
            <h3 id="child-magic-link-title" class="sheet-subtitle">{$i18n.t('app.telegram.family.magicLinkTitle')}</h3>
            <p class="confirm-meta">{$i18n.t('app.telegram.family.magicLinkHint')}</p>
            {#if magicLink}
                    <button class="reactivate-full" type="button" on:click={copyMagicLink}><TelegramIcon name="copy" size={18} />{magicLinkCopied ? $i18n.t('app.telegram.family.copied') : $i18n.t('app.telegram.family.copyLink')}</button>
            {:else}
                <button class="reactivate-full" type="button" disabled={magicLinkBusy} on:click={createMagicLink}><TelegramIcon name="key" size={18} />{magicLinkBusy ? $i18n.t('app.telegram.family.creating') : $i18n.t('app.telegram.family.createMagicLink')}</button>
            {/if}
            {#if magicLinkPending}
                <button class="deactivate" type="button" disabled={magicLinkBusy} on:click={revokeMagicLink}><TelegramIcon name="unlink" size={18} />{$i18n.t('app.telegram.family.revokeMagicLink')}</button>
            {/if}
            {#if magicLinkError}<p class="error" role="alert">{magicLinkError}</p>{/if}
        </section>

        <section class="magic-link" aria-labelledby="child-telegram-title">
            <h3 id="child-telegram-title" class="sheet-subtitle">{$i18n.t('app.telegram.family.childTelegramTitle')}</h3>
            <p class="confirm-meta">{$i18n.t('app.telegram.family.childTelegramHint', { name: manageChild.nickname })}</p>
            {#if inviteLink}
                <p class="manage-meta"><strong>{$i18n.t('app.telegram.family.linkReady')}</strong></p>
                <div class="invite-actions">
                    <button class="reactivate-full" type="button" on:click={copyInvite}><TelegramIcon name="copy" size={18} label={$i18n.t('app.telegram.family.copyLink')} />{copied ? $i18n.t('app.telegram.family.copied') : $i18n.t('app.telegram.family.copyLink')}</button>
                    <button class="close" type="button" on:click={() => { inviteLink = ''; copied = false; }}><TelegramIcon name="refresh" size={16} label={$i18n.t('app.telegram.family.createNew')} />{$i18n.t('app.telegram.family.createNew')}</button>
                </div>
            {:else if telegram?.linked}
                <div class="telegram-actions">
                    <button class="reactivate-full" type="button" disabled={inviteBusy} on:click={createInvite}><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.family.relinkTelegram')} />{$i18n.t('app.telegram.family.relinkTelegram')}</button>
                    <button class="deactivate" type="button" disabled={telegramBusy} on:click={unlinkTelegram}><TelegramIcon name="unlink" size={18} label={$i18n.t('app.telegram.family.unlinkTelegram')} />{$i18n.t('app.telegram.family.unlinkTelegram')}</button>
                </div>
            {:else}
                <button class="reactivate-full" type="button" disabled={inviteBusy} on:click={createInvite}><TelegramIcon name="send" size={18} label={$i18n.t('app.telegram.family.linkTelegram')} />{inviteBusy ? $i18n.t('app.telegram.family.creating') : $i18n.t('app.telegram.family.linkTelegram')}</button>
            {/if}
            {#if telegramError}<p class="error" role="alert">{telegramError}</p>{/if}
        </section>

        <h3 class="sheet-subtitle">{$i18n.t('app.telegram.family.status')}</h3>
        <div class="manage-status"><span class="manage-label">{$i18n.t('app.telegram.family.visibleInAppAndBot')}</span><span class:badge-active={manageChild.status !== 'INACTIVE'} class="manage-badge">{manageChild.status === 'INACTIVE' ? $i18n.t('app.telegram.family.inactive') : $i18n.t('app.telegram.family.active')}</span></div>
        {#if manageChild.status !== 'INACTIVE'}
            <button class="deactivate" type="button" on:click={() => { statusError = ''; confirmChild = manageChild; }}><TelegramIcon name="pause" size={18} label={$i18n.t('app.telegram.family.deactivate')} />{$i18n.t('app.telegram.family.deactivate')}</button>
        {:else}
            <button class="reactivate-full" type="button" disabled={statusBusy} on:click={() => manageChild && applyStatus(manageChild, true)}><TelegramIcon name="play" size={18} label={$i18n.t('app.telegram.family.reactivate')} />{$i18n.t('app.telegram.family.reactivate')}</button>
        {/if}
        {#if telegramError || statusError}<p class="error" role="alert">{telegramError || statusError}</p>{/if}
        <button class="close" type="button" on:click={() => manageChild = null}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.header.close')} />{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

{#if confirmChild}
    <div class="sheet-backdrop" role="presentation" on:click={() => confirmChild = null}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="child-confirm-title" tabindex="-1">
        <h2 id="child-confirm-title">{$i18n.t('app.telegram.family.deactivateTitle', { name: confirmChild.nickname })}</h2>
        <p class="confirm-meta">{$i18n.t('app.telegram.family.deactivateDescription')}</p>
        <button class="deactivate" type="button" disabled={statusBusy} on:click={() => confirmChild && applyStatus(confirmChild, false)}><TelegramIcon name="pause" size={18} label={$i18n.t('app.telegram.family.deactivateConfirm')} />{$i18n.t('app.telegram.family.deactivateConfirm')}</button>
        <button class="close" type="button" disabled={statusBusy} on:click={() => confirmChild = null}><TelegramIcon name="close" size={16} label={$i18n.t('app.telegram.family.cancel')} />{$i18n.t('app.telegram.family.cancel')}</button>
    </div>
{/if}

<TelegramLimits open={limitsOpen} child={limitsChild} demoMode={demoMode} onClose={() => limitsOpen = false} onSaved={() => { if (limitsChild) void loadTelegram(limitsChild.id); }} />

<style>
    .family { width:100%; }
    h1 { margin:0 0 .5rem; color:#18243d; font-size:1.35rem; }
    .section-title { margin:.9rem 0 .45rem; color:#18243d; font-size:1rem; }
    .quick-actions { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:.5rem; margin-bottom:.85rem; }
    .quick-action { box-sizing:border-box; display:flex; align-items:center; gap:.6rem; min-width:0; min-height:3.8rem; padding:.55rem .7rem; border:1px solid #dfe4ee; border-radius:.9rem; background:#fff; color:#18243d; font:inherit; text-align:left; text-decoration:none; cursor:pointer; }
    .quick-action:hover { border-color:#b9c0ff; background:#fafbff; }
    .quick-action:only-child { grid-column:1 / -1; }
    .quick-action > span:last-child { min-width:0; }
    .quick-action strong, .quick-action small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .quick-action strong { font-size:.86rem; }
    .quick-action small { margin-top:.12rem; color:#8a93a8; font-size:.68rem; }
    .children-list { display:grid; gap:.5rem; }
    .childrow-wrap { display:flex; align-items:center; padding:0 .7rem; border:1px solid #dfe4ee; border-radius:.9rem; background:#fff; }
    .childrow { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3.25rem; padding:.4rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; cursor:pointer; }
    .childrow:last-child { border-bottom:0; }
    .childrow.current { color:#18243d; }
    .avatar { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:50%; background:#eef0ff; color:#5b63e9; font-weight:800; }
    .grow { flex:1; min-width:0; }
    .name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:700; }
    .badge { display:inline-flex; align-items:center; gap:.2rem; margin-top:.15rem; color:#3867d6; font-size:.72rem; font-weight:700; }
    .balance { display:inline-flex; align-items:center; gap:.3rem; color:#573d00; font-weight:700; white-space:nowrap; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .invite { margin-top:.6rem; padding:.7rem; border:1px solid #e6e9f0; border-radius:.75rem; background:#fff; }
    .invite .muted { margin:0 0 .5rem; color:#66718a; font-size:.85rem; line-height:1.4; }
    .invite-input { box-sizing:border-box; width:100%; min-height:2.5rem; margin-bottom:.5rem; padding:.5rem .7rem; border:1px solid #cfd6e4; border-radius:.6rem; font:inherit; }
    .invite button { display:inline-flex; align-items:center; gap:.4rem; min-height:2.75rem; padding:.45rem .7rem; border:1px solid #3867d6; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; cursor:pointer; }
    .invite button:disabled { cursor:wait; opacity:.6; }
    @media (max-width:360px) { .quick-actions { grid-template-columns:1fr; } }
    .settings { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .setting { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#33415f; font:inherit; text-align:left; }
    .setting:last-child { border-bottom:0; }
    button.setting { cursor:pointer; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-group { border-bottom:1px solid #edf0f5; }
    .setting-group > .setting { border-bottom:0; }
    .family-locale-panel { padding:.35rem 0 .65rem 2.7rem; overflow:hidden; }
    .family-locale-panel :global(.locale-switcher) { display:flex; flex-wrap:wrap; justify-content:space-between; }
    .family-locale-panel :global(.locale-switcher__options) { flex-wrap:wrap; }
    .setting .grow { font-weight:600; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
    .demo-unavailable { margin:.65rem 0 0; padding:.65rem .75rem; border:1px solid #ead9a4; border-radius:.7rem; background:#fff9e8; color:#705719; font-size:.82rem; line-height:1.4; }
    .childrow-wrap .childrow { flex:1; min-width:0; border-bottom:0; }
    .childrow-more { display:grid; place-items:center; width:2.75rem; height:2.75rem; flex:0 0 auto; border:0; background:transparent; color:#66718a; cursor:pointer; }
    .inactive-notice { margin-top:.6rem; padding:.15rem .8rem; border:1px solid #e2e6ef; border-radius:.9rem; background:#f7f8fc; color:#66718a; }
    .inactive-row { display:flex; align-items:center; gap:.6rem; min-height:3.25rem; padding:.4rem 0; }
    .inactive-row:last-child { border-bottom:0; }
    .inactive-row .avatar { opacity:.55; }
    .inactive-meta { display:block; margin-top:.1rem; color:#8a93a8; font-size:.72rem; }
    .reactivate { display:inline-flex; align-items:center; justify-content:center; flex:0 0 auto; gap:.35rem; min-height:2.75rem; padding:.3rem .7rem; border:1px solid #3867d6; border-radius:.7rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; white-space:nowrap; cursor:pointer; }
    .reactivate:disabled { cursor:wait; opacity:.6; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    @media (min-width: 700px) {
        .sheet { inset:50% auto auto 50%; width:min(38rem, calc(100% - 3rem)); max-height:min(82dvh, 46rem); overflow-y:auto; padding:1.4rem; border-radius:1.25rem; box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%); transform:translate(-50%, -50%); }
    }
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
    .close { display:flex; align-items:center; justify-content:center; gap:.4rem; width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #f1c7ca; border-radius:.7rem; background:#fff7f7; color:#a84a50; font:inherit; font-weight:600; cursor:pointer; }
    .close:disabled { cursor:wait; opacity:.6; }
    .setting-title { display:block; font-weight:600; }
    .setting-meta { display:block; margin-top:.1rem; color:#66718a; font-size:.78rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .telegram-actions { display:grid; grid-template-columns:1fr 1fr; gap:.6rem; }
    .telegram-actions .reactivate-full, .telegram-actions .deactivate { margin-top:0; }
    .magic-link { margin-top:.75rem; padding-top:.75rem; border-top:1px solid #edf0f5; }
    .magic-link .sheet-subtitle { margin:0 0 .4rem; }
    .invite-actions { display:grid; grid-template-columns:minmax(0,1fr); gap:.6rem; }
</style>
