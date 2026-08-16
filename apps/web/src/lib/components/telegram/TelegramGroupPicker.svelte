<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getTelegramEntityIcon } from './telegramEntityIcons';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let groups: string[] = [];
    export let selected = '';
    export let title = '';
    export let onSelect: (group: string) => void = () => {};
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    let query = '';
    $: if (open) query = '';
    $: filtered = query.trim()
        ? groups.filter((group) => group.toLowerCase().includes(query.trim().toLowerCase()))
        : groups;
    $: showSearch = groups.length > 8;

    function choose(group: string) {
        onSelect(group);
        onClose();
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="group-picker-title" tabindex="-1">
        <h2 id="group-picker-title">{title}</h2>

        {#if showSearch}
            <div class="search">
                <TelegramIcon name="search" size={18} label={$i18n.t('app.telegram.readyCatalog.search')} />
                <input type="search" bind:value={query} placeholder={$i18n.t('app.telegram.groupPicker.search')} aria-label={$i18n.t('app.telegram.groupPicker.search')} />
            </div>
        {/if}

        <div class="flat">
            {#each filtered as group (group)}
                <button type="button" class="pick" class:active={selected === group} on:click={() => choose(group)}>
                    <span class="gico"><TelegramIcon name={getTelegramEntityIcon({ kind: 'task', group })} size={20} label={group} /></span>
                    <span class="grow">{group}</span>
                    {#if selected === group}<TelegramIcon name="check" size={18} label={$i18n.t('app.telegram.groupPicker.selected')} />{/if}
                </button>
            {/each}
            {#if !filtered.length}
                <p class="muted">{$i18n.t('app.telegram.groupPicker.noResults')}</p>
            {/if}
        </div>

        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.groupPicker.close')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); max-height:80vh; overflow-y:auto; }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .search { display:flex; align-items:center; gap:.5rem; min-height:2.75rem; margin-bottom:.5rem; padding:0 .7rem; border:1px solid #dfe4ee; border-radius:.75rem; background:#fff; }
    .search input { flex:1; min-width:0; border:0; outline:0; background:transparent; color:#18243d; font:inherit; }
    .flat { display:grid; gap:.25rem; }
    .pick { display:flex; align-items:center; gap:.6rem; width:100%; min-height:2.75rem; padding:.4rem .6rem; border:0; border-bottom:1px solid #edf0f5; border-radius:0; background:#fff; color:#18243d; font:inherit; font-weight:600; text-align:left; cursor:pointer; }
    .pick:last-child { border-bottom:0; }
    .pick.active { color:#2854ba; }
    .gico { display:grid; place-items:center; width:2rem; height:2rem; flex:0 0 auto; border-radius:.55rem; background:#eef0ff; color:#5b63e9; }
    .grow { flex:1; min-width:0; }
    .muted { color:#66718a; text-align:center; padding:1rem 0; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
