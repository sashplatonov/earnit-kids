<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import TelegramCoin from './TelegramCoin.svelte';
    import TelegramIcon from './TelegramIcon.svelte';
    import type { CatalogSortMode } from '$lib/telegram/services/catalogSort';

    export let mode: CatalogSortMode = 'group';
    export let onChange: (mode: CatalogSortMode) => void = () => {};
    const i18n = useI18n();
</script>

<div class="sort-control" role="group" aria-label={$i18n.t('app.telegram.sort.title')}>
    <button type="button" class:active={mode === 'group'} aria-label={$i18n.t('app.telegram.sort.groups')} aria-pressed={mode === 'group'} on:click={() => onChange('group')}>
        <TelegramIcon name="table" size={16} />
    </button>
    <button type="button" class="coins" class:active={mode === 'coins'} aria-label={$i18n.t('app.telegram.sort.coins')} aria-pressed={mode === 'coins'} on:click={() => onChange('coins')}>
        <TelegramCoin size={14} />
        <TelegramIcon name="arrowDown" size={13} />
    </button>
</div>

<style>
    .sort-control { display:flex; align-items:center; gap:.12rem; flex:0 0 auto; width:max-content; padding:.12rem; border:1px solid #e1e6ef; border-radius:.65rem; background:#f8f9fc; color:#66718a; }
    button { display:grid; place-items:center; box-sizing:border-box; width:44px; min-width:44px; height:44px; min-height:44px; padding:0; border:0; border-radius:.45rem; background:transparent; color:#71809a; cursor:pointer; }
    button.coins { display:flex; justify-content:center; gap:.05rem; }
    button.active { background:#fff; color:#3867d6; box-shadow:0 1px 3px rgb(24 36 61 / 10%); }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:1px; }
</style>
