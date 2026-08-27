<script lang="ts">
    import { run } from 'svelte/legacy';

    import TelegramIcon from './TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';
    import { loadGroupUsage, rankGroups, recordGroupUsage, type GroupUsageKind } from './telegramGroupUsage';

    interface Props {
        groups?: string[];
        selected?: string;
        kind?: GroupUsageKind;
        allLabel?: string;
        moreLabel?: string;
        allGroupsTitle?: string;
        onSelect?: (group: string) => void;
    }

    let {
        groups = [],
        selected = '',
        kind = 'tasks',
        allLabel = '',
        moreLabel = '',
        allGroupsTitle = '',
        onSelect = () => {}
    }: Props = $props();

    const i18n = useI18n();

    const MAX_RANKED = 2;

    let ranked: string[] = $state([]);
    let moreOpen = $state(false);
    let closeLabel = $derived($i18n.t('app.telegram.groupSubnav.close'));

    // Recompute ranking at a stable boundary (screen entry / data refresh),
    // never immediately after a tap, so the submenu does not visibly jump.
    run(() => {
        if (moreOpen === false) {
            ranked = rankGroups(groups, loadGroupUsage(kind));
        }
    });

    let visible = $derived(ranked.slice(0, MAX_RANKED));
    // The "Ещё" sheet shows the remaining groups in canonical/default order
    // (the order passed in `groups`), not in usage-ranked order.
    let hidden = $derived(groups.filter((group) => !visible.includes(group)));

    function choose(group: string) {
        recordGroupUsage(kind, group);
        moreOpen = false;
        onSelect(group);
    }

    function chooseAll() {
        recordGroupUsage(kind, '');
        moreOpen = false;
        onSelect('');
    }

    let hasHidden = $derived(hidden.length > 0);
</script>

{#if groups.length > 1}
    <div class="group-subnav" role="group" aria-label={allGroupsTitle}>
        <div class="subnav-row">
            <button
                type="button"
                class="chip chip--all"
                class:active={selected === ''}
                aria-pressed={selected === ''}
                onclick={chooseAll}
            >{allLabel}</button>
            {#each visible as group (group)}
                <button
                    type="button"
                    class="chip chip--grow"
                    class:active={selected === group}
                    aria-pressed={selected === group}
                    onclick={() => choose(group)}
                ><span class="chip-label">{group}</span></button>
            {/each}
            {#if hasHidden}
                <button
                    type="button"
                    class="chip chip--more"
                    aria-haspopup="dialog"
                    aria-expanded={moreOpen}
                    onclick={() => moreOpen = true}
                >{moreLabel}<TelegramIcon name="chevronDown" size={14} label={moreLabel} /></button>
            {/if}
        </div>
    </div>

    {#if moreOpen}
        <div class="sheet-backdrop" role="presentation" onclick={() => moreOpen = false}></div>
        <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="group-subnav-title" tabindex="-1">
            <h2 id="group-subnav-title">{allGroupsTitle}</h2>
            <div class="flat">
                <button type="button" class="sheet-item" class:active={selected === ''} onclick={chooseAll}>{allLabel}</button>
                {#each groups as group (group)}
                    <button type="button" class="sheet-item" class:active={selected === group} onclick={() => choose(group)}>{group}</button>
                {/each}
            </div>
            <button class="close" type="button" onclick={() => moreOpen = false}>{closeLabel}</button>
        </div>
    {/if}
{/if}

<style>
    .group-subnav { margin: .15rem 0 .6rem; }
    .subnav-row { display: flex; align-items: center; gap: .4rem; padding: .1rem 0 .35rem; }
    .chip { display: inline-flex; align-items: center; justify-content: center; gap: .15rem; flex: 0 0 auto; min-height: 2.75rem; padding: 0 .75rem; border: 1px solid #dfe4ee; border-radius: 999px; background: #fff; color: #66718a; font: inherit; font-size: .82rem; font-weight: 600; cursor: pointer; touch-action: manipulation; white-space: nowrap; }
    .chip.active { border-color: #b9c7ef; background: #eef2ff; color: #2854ba; }
    .chip--grow { flex: 1 1 0; min-width: 0; }
    .chip-label { display: block; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .chip--more { color: #3867d6; flex: 0 0 auto; }
    .sheet-backdrop { position: fixed; inset: 0; z-index: 40; background: rgb(15 24 45 / 35%); }
    .sheet { position: fixed; inset: auto 0 0; z-index: 41; padding: 1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius: 1.1rem 1.1rem 0 0; background: #fff; box-shadow: 0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin: 0 0 .75rem; color: #18243d; font-size: 1.15rem; }
    .flat { display: grid; gap: .25rem; max-height: 45vh; overflow-y: auto; }
    .sheet-item { display: flex; align-items: center; min-height: 2.75rem; padding: 0 .6rem; border: 0; border-bottom: 1px solid #edf0f5; border-radius: 0; background: #fff; color: #18243d; font: inherit; font-weight: 600; text-align: left; cursor: pointer; }
    .sheet-item:last-child { border-bottom: 0; }
    .sheet-item.active { color: #2854ba; }
    .close { width: 100%; min-height: 2.75rem; margin-top: .6rem; border: 1px solid #dfe4ee; border-radius: .7rem; background: #fff; color: #33415f; font: inherit; cursor: pointer; }
</style>
