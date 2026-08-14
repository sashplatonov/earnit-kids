<script lang="ts">
    import { onMount } from 'svelte';
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { loadParentMemberships } from '$lib/services/api';
    import type { ParentMembership } from '$lib/types/auth';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let parents: ParentMembership[] = [];
    let loading = false;
    let error = '';

    onMount(() => {
        void reload();
    });

    async function reload() {
        if (!open) return;
        loading = true;
        error = '';
        const result = await loadParentMemberships();
        parents = result.ok ? result.data ?? [] : [];
        if (!result.ok) error = $i18n.t('app.telegram.roles.loadError');
        loading = false;
    }

    function permissionLabel(permission: ParentMembership['permission']): string {
        switch (permission) {
            case 'family_admin': return $i18n.t('app.telegram.roles.permissionFamilyAdmin');
            case 'editor': return $i18n.t('app.telegram.roles.permissionEditor');
            default: return $i18n.t('app.telegram.roles.permissionViewer');
        }
    }

    $: children = $appStore.children;
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="roles-title" tabindex="-1">
        <h2 id="roles-title">{$i18n.t('app.telegram.roles.title')}</h2>

        <h3 class="sheet-subtitle">{$i18n.t('app.telegram.roles.parents')}</h3>
        {#if loading}
            <p class="muted">{$i18n.t('app.telegram.shell.loading')}</p>
        {:else}
            <div class="flat">
                {#if !parents.length}
                    <p class="muted">{$i18n.t('app.telegram.family.noChildren')}</p>
                {:else}
                    {#each parents as parent (parent.id)}
                        <div class="row"><span class="setting-icon"><TelegramIcon name="users" size={18} label={$i18n.t('app.telegram.roles.parents')} /></span><span class="grow"><span class="setting-title">{parent.email}</span></span><span class="manage-badge badge-active">{permissionLabel(parent.permission)}</span></div>
                    {/each}
                {/if}
            </div>
        {/if}

        <h3 class="sheet-subtitle">{$i18n.t('app.telegram.roles.children')}</h3>
        <div class="flat">
            {#if !children.length}
                <p class="muted">{$i18n.t('app.telegram.family.noChildren')}</p>
            {:else}
                {#each children as child (child.id)}
                    <div class="row"><span class="setting-icon"><TelegramIcon name="child" size={18} label={$i18n.t('app.telegram.roles.children')} /></span><span class="grow"><span class="setting-title">{child.nickname}</span></span></div>
                {/each}
            {/if}
        </div>

        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .sheet-subtitle { margin:1rem 0 .4rem; color:#4d5870; font-size:.85rem; }
    .flat { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .row { display:flex; align-items:center; gap:.6rem; min-height:3rem; padding:.35rem 0; border-bottom:1px solid #edf0f5; }
    .row:last-child { border-bottom:0; }
    .grow { flex:1; min-width:0; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .setting-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:600; }
    .manage-badge { padding:.2rem .55rem; border-radius:999px; background:#f1f3f7; color:#66718a; font-size:.78rem; font-weight:700; white-space:nowrap; }
    .badge-active { background:#eaf7ef; color:#17884b; }
    .muted { color:#66718a; }
    .error { color:#a33b3b; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
