<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';
    import type { CatalogSortMode } from '$lib/telegram/services/catalogSort';

    export let mode: CatalogSortMode = 'group';
    export let onChange: (mode: CatalogSortMode) => void = () => {};
    const i18n = useI18n();
</script>

<div class="sort-control" role="group" aria-label={$i18n.t('app.telegram.sort.title')}>
    <TelegramIcon name="filter" size={15} label={$i18n.t('app.telegram.sort.title')} />
    <button type="button" class:active={mode === 'group'} aria-pressed={mode === 'group'} on:click={() => onChange('group')}>{$i18n.t('app.telegram.sort.groups')}</button>
    <button type="button" class:active={mode === 'coins'} aria-pressed={mode === 'coins'} on:click={() => onChange('coins')}>{$i18n.t('app.telegram.sort.coins')}</button>
</div>

<style>
    .sort-control { display:flex; align-items:center; gap:.2rem; width:max-content; max-width:100%; margin:0 0 .55rem auto; padding:.18rem; border:1px solid #e1e6ef; border-radius:.65rem; background:#f8f9fc; color:#66718a; }
    button { box-sizing:border-box; min-width:44px; min-height:44px; padding:0 .45rem; border:0; border-radius:.45rem; background:transparent; color:#66718a; font:inherit; font-size:.75rem; font-weight:700; cursor:pointer; white-space:nowrap; }
    button.active { background:#fff; color:#2854ba; box-shadow:0 1px 3px rgb(24 36 61 / 10%); }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:1px; }
</style>
