<script lang="ts">
    import TelegramSortControl from './TelegramSortControl.svelte';
    import type { CatalogSortMode } from '$lib/telegram/services/catalogSort';

    interface Props {
        count?: number;
        countLabel?: string;
        mode?: CatalogSortMode;
        onChange?: (mode: CatalogSortMode) => void;
    }

    let {
        count = 0,
        countLabel = '',
        mode = 'group',
        onChange = () => {}
    }: Props = $props();
</script>

<div class="catalog-toolbar">
    <span class="count">{countLabel.replace('{count}', String(count))}</span>
    <TelegramSortControl {mode} {onChange} />
</div>

<style>
    .catalog-toolbar { display:flex; align-items:center; justify-content:space-between; gap:.6rem; min-width:0; margin:.15rem 0 .55rem; }
    .count { min-width:0; overflow:hidden; color:#929db2; font-size:.75rem; font-weight:650; line-height:1.2; text-overflow:ellipsis; white-space:nowrap; }
</style>
