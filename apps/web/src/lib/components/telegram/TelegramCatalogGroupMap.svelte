<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import TelegramIcon from './TelegramIcon.svelte';

    interface Props {
        open?: boolean;
        groupName?: string | null;
        familyGroups?: string[];
        onChoose?: (groupName: string | null) => void;
        onClose?: () => void;
    }

    let {
        open = false,
        groupName = null,
        familyGroups = [],
        onChoose = () => {},
        onClose = () => {}
    }: Props = $props();

    const i18n = useI18n();
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" onclick={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="group-map-title" tabindex="-1">
        <h2 id="group-map-title">{$i18n.t('app.telegram.readyCatalog.whereToAdd')}</h2>
        <div class="list">
            {#each familyGroups as group (group)}
                <button class="item" type="button" onclick={() => onChoose(group)}>{group}</button>
            {/each}
            <button class="item" type="button" onclick={() => onChoose(null)}>{$i18n.t('app.telegram.readyCatalog.withoutGroup')}</button>
            {#if groupName && !familyGroups.includes(groupName)}
                <button class="item item--create" type="button" onclick={() => onChoose(groupName)}>
                    <TelegramIcon name="add" size={16} label={$i18n.t('app.telegram.readyCatalog.createGroup', { name: groupName })} />
                    {$i18n.t('app.telegram.readyCatalog.createGroup', { name: groupName })}
                </button>
            {/if}
        </div>
        <button class="close" type="button" onclick={onClose}>{$i18n.t('app.telegram.readyCatalog.cancel')}</button>
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .list { display:grid; gap:.25rem; max-height:50vh; overflow-y:auto; }
    .item { display:flex; align-items:center; gap:.5rem; width:100%; min-height:2.75rem; padding:0 .6rem; border:0; border-bottom:1px solid #edf0f5; border-radius:0; background:#fff; color:#18243d; font:inherit; font-weight:600; text-align:left; cursor:pointer; }
    .item:last-child { border-bottom:0; }
    .item--create { color:#3867d6; }
    .close { width:100%; min-height:2.75rem; margin-top:.6rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    button:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
