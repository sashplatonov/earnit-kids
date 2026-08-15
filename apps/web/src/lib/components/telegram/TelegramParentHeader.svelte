<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { switchChild } from '$lib/services/bootstrap';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';

    const i18n = useI18n();

    let open = false;
    let switching = false;
    let error = '';
    $: current = $appStore.children.find((child) => $appStore.currentChildId == child.id);
    $: childCount = $appStore.children.length;

    async function select(id: string | number) {
        if ($appStore.currentChildId == id) {
            open = false;
            return;
        }
        switching = true;
        error = '';
        await switchChild(id);
        switching = false;
        open = false;
        if ($appStore.currentChildId != id) error = $i18n.t('app.telegram.family.switchError');
    }
</script>

<header class="parent-header">
    <button
        class="child-select"
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={$i18n.t('app.telegram.header.switchChild')}
        disabled={switching || childCount === 0}
        on:click={() => open = !open}
    >
        <TelegramIcon name="child" size={18} label={$i18n.t('app.telegram.header.child')} />
        <span class="child-name">{current?.nickname ?? $i18n.t('app.telegram.header.child')}</span>
        <TelegramIcon name="chevronDown" size={16} label={$i18n.t('app.telegram.header.openChildList')} />
    </button>
    <span class="balance" aria-label={$i18n.t('app.telegram.header.balance', { balance: $appStore.balance })}><TelegramCoin size={16} label={$i18n.t('app.telegram.header.coins')} />{$appStore.balance}</span>
</header>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={() => open = false}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="child-switch-title" tabindex="-1">
        <h2 id="child-switch-title">{$i18n.t('app.telegram.header.switchChild')}</h2>
        <div class="children" role="listbox" aria-label={$i18n.t('app.telegram.header.children')}>
            {#each $appStore.children as child (child.id)}
                <button class:current={$appStore.currentChildId == child.id} type="button" role="option" aria-selected={$appStore.currentChildId == child.id} disabled={switching} on:click={() => select(child.id)}>
                    <span class="avatar">{child.nickname.charAt(0).toUpperCase()}</span>
                    <span class="grow"><span class="name">{child.nickname}</span>{#if $appStore.currentChildId == child.id}<span class="badge">{$i18n.t('app.telegram.family.currentChild')}</span>{/if}</span>
                    <span class="child-balance"><TelegramCoin size={14} />{child.balance}</span>
                </button>
            {/each}
        </div>
        {#if error}<p class="error" role="alert">{error}</p>{/if}
        <button class="close" type="button" on:click={() => open = false} disabled={switching}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .parent-header { display:flex; align-items:center; justify-content:space-between; gap:.75rem; margin-bottom:.65rem; }
    .child-select { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; padding:.3rem .6rem; border:0; background:transparent; color:#18243d; font:inherit; font-weight:700; cursor:pointer; }
    .child-select:disabled { cursor:default; opacity:.6; }
    .child-name { max-width:11rem; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
    .balance { display:inline-flex; align-items:center; gap:.35rem; padding:.45rem .65rem; border-radius:999px; background:#fff4c2; color:#573d00; font-weight:700; white-space:nowrap; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .children { display:grid; grid-template-columns:minmax(0,1fr); gap:.5rem; }
    .children button { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.75rem; padding:.55rem .7rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; text-align:left; }
    .children button.current { border-color:#3867d6; background:#f2f5ff; }
    .avatar { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:50%; background:#eef0ff; color:#5b63e9; font-weight:800; }
    .grow { flex:1; min-width:0; }
    .name { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:700; }
    .badge { display:block; margin-top:.1rem; color:#3867d6; font-size:.72rem; font-weight:700; }
    .child-balance { display:inline-flex; align-items:center; gap:.3rem; color:#573d00; font-weight:700; white-space:nowrap; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .close { width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
