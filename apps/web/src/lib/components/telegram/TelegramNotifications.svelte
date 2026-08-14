<script lang="ts">
    import { onMount } from 'svelte';
    import { useI18n } from '$lib/i18n/context';
    import {
        getFamilyNotificationSettings,
        setFamilyNotificationPreference,
        type FamilyNotificationSettings,
        type NotificationPreference,
    } from '$lib/services/api';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let settings: FamilyNotificationSettings | null = null;
    let loading = false;
    let error = '';

    onMount(() => {
        void reload();
    });

    async function reload() {
        if (!open) return;
        loading = true;
        error = '';
        settings = await getFamilyNotificationSettings();
        if (!settings) error = $i18n.t('app.telegram.notifications.loadError');
        loading = false;
    }

    function parentLabel(key: string): string {
        switch (key) {
            case 'taskMarkedDone': return $i18n.t('app.telegram.notifications.taskMarkedDone');
            case 'rewardRequested': return $i18n.t('app.telegram.notifications.rewardRequested');
            case 'balanceChanged': return $i18n.t('app.telegram.notifications.balanceChanged');
            case 'parentInviteAccepted': return $i18n.t('app.telegram.notifications.parentInviteAccepted');
            case 'childTelegramLinked': return $i18n.t('app.telegram.notifications.childTelegramLinked');
            default: return key;
        }
    }

    function childLabel(key: string): string {
        switch (key) {
            case 'taskApproved': return $i18n.t('app.telegram.notifications.taskApproved');
            case 'taskRejected': return $i18n.t('app.telegram.notifications.taskRejected');
            case 'rewardApproved': return $i18n.t('app.telegram.notifications.rewardApproved');
            case 'rewardRejected': return $i18n.t('app.telegram.notifications.rewardRejected');
            case 'newTasks': return $i18n.t('app.telegram.notifications.newTasks');
            case 'rewardAvailable': return $i18n.t('app.telegram.notifications.rewardAvailable');
            default: return key;
        }
    }

    function isParentTask(pref: NotificationPreference): boolean {
        return pref.key === 'taskMarkedDone' || pref.key === 'rewardRequested';
    }

    async function toggleParent(pref: NotificationPreference) {
        const next = !pref.enabled;
        if (settings) {
            settings = {
                ...settings,
                parent: settings.parent.map((p) => p.key === pref.key ? { ...p, enabled: next } : p),
            };
        }
        const ok = await setFamilyNotificationPreference('parent', null, pref.key, next);
        if (!ok) {
            error = $i18n.t('app.telegram.notifications.saveError');
            void reload();
        }
    }

    async function toggleChild(childId: number, pref: NotificationPreference) {
        const next = !pref.enabled;
        if (settings) {
            settings = {
                ...settings,
                children: settings.children.map((child) =>
                    child.childId === childId
                        ? { ...child, preferences: child.preferences.map((p) => p.key === pref.key ? { ...p, enabled: next } : p) }
                        : child),
            };
        }
        const ok = await setFamilyNotificationPreference('child', childId, pref.key, next);
        if (!ok) {
            error = $i18n.t('app.telegram.notifications.saveError');
            void reload();
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="notifications-title" tabindex="-1">
        <h2 id="notifications-title">{$i18n.t('app.telegram.notifications.title')}</h2>

        {#if loading}
            <p class="muted">{$i18n.t('app.telegram.shell.loading')}</p>
        {:else if settings}
            <h3 class="sheet-subtitle">{$i18n.t('app.telegram.notifications.parent')}</h3>
            <div class="flat">
                <p class="group-label">{$i18n.t('app.telegram.notifications.parentGroupTasks')}</p>
                {#each settings.parent.filter(isParentTask) as pref (pref.key)}
                    <div class="row"><span class="grow"><span class="setting-title">{parentLabel(pref.key)}</span></span><button class="switch" class:on={pref.enabled} type="button" role="switch" aria-checked={pref.enabled} aria-label={parentLabel(pref.key)} on:click={() => toggleParent(pref)}></button></div>
                {/each}
                <p class="group-label">{$i18n.t('app.telegram.notifications.parentGroupFamily')}</p>
                {#each settings.parent.filter((pref) => !isParentTask(pref)) as pref (pref.key)}
                    <div class="row"><span class="grow"><span class="setting-title">{parentLabel(pref.key)}</span></span><button class="switch" class:on={pref.enabled} type="button" role="switch" aria-checked={pref.enabled} aria-label={parentLabel(pref.key)} on:click={() => toggleParent(pref)}></button></div>
                {/each}
            </div>

            {#each settings.children as child (child.childId)}
                <h3 class="sheet-subtitle">{$i18n.t('app.telegram.notifications.child', { name: child.childName })}</h3>
                <div class="flat">
                    <p class="group-label">{$i18n.t('app.telegram.notifications.childGroupDecisions')}</p>
                    {#each child.preferences.filter((pref) => ['taskApproved', 'taskRejected', 'rewardApproved', 'rewardRejected'].includes(pref.key)) as pref (pref.key)}
                        <div class="row"><span class="grow"><span class="setting-title">{childLabel(pref.key)}</span></span><button class="switch" class:on={pref.enabled} type="button" role="switch" aria-checked={pref.enabled} aria-label={childLabel(pref.key)} on:click={() => toggleChild(child.childId, pref)}></button></div>
                    {/each}
                    <p class="group-label">{$i18n.t('app.telegram.notifications.childGroupReminders')}</p>
                    {#each child.preferences.filter((pref) => ['newTasks', 'rewardAvailable'].includes(pref.key)) as pref (pref.key)}
                        <div class="row"><span class="grow"><span class="setting-title">{childLabel(pref.key)}</span></span><button class="switch" class:on={pref.enabled} type="button" role="switch" aria-checked={pref.enabled} aria-label={childLabel(pref.key)} on:click={() => toggleChild(child.childId, pref)}></button></div>
                    {/each}
                </div>
            {/each}
        {/if}

        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); max-height:82dvh; overflow:auto; }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .group-label { margin:.5rem 0 .1rem; color:#8a93a8; font-size:.75rem; font-weight:600; }
    .row { display:flex; align-items:center; gap:.6rem; min-height:2.9rem; padding:.3rem 0; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .grow { flex:1; min-width:0; }
    .setting-title { display:block; font-weight:600; font-size:.9rem; line-height:1.3; }
    .switch { width:2.6rem; height:1.5rem; flex:0 0 auto; padding:0; border:0; border-radius:999px; background:#d8dce5; cursor:pointer; position:relative; }
    .switch.on { background:#3867d6; }
    .switch:after { content:""; position:absolute; width:1.1rem; height:1.1rem; top:.2rem; left:.2rem; border-radius:50%; background:#fff; transition:left .15s ease; }
    .switch.on:after { left:1.3rem; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
