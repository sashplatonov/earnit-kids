<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import {
        SEMANTIC_GRAPHIC_CATEGORIES,
        SEMANTIC_GRAPHICS,
        getGraphicsForCategory,
        type SemanticGraphic,
    } from './semanticGraphics';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let title = '';
    export let initial: string | null = null;
    export let onSelect: (key: string) => void = () => {};
    export let onClose: () => void = () => {};

    const i18n = useI18n();
    const RECENT_KEY = 'earnit:recent-graphics';

    let query = '';

    function loadRecent(): string[] {
        try {
            const raw = localStorage.getItem(RECENT_KEY);
            const parsed: unknown = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
        } catch {
            return [];
        }
    }

    function recordRecent(key: string) {
        try {
            const recent = [key, ...loadRecent().filter((item) => item !== key)].slice(0, 6);
            localStorage.setItem(RECENT_KEY, JSON.stringify(recent));
        } catch {
            // storage unavailable — recents are best-effort only
        }
    }

    $: normalizedQuery = query.trim().toLowerCase();
    let recentGraphics: SemanticGraphic[] = [];
    $: if (open) {
        recentGraphics = loadRecent()
            .map((key) => SEMANTIC_GRAPHICS.find((graphic) => graphic.key === key))
            .filter((graphic): graphic is SemanticGraphic => graphic !== undefined);
    }
    $: filteredRecent = normalizedQuery
        ? recentGraphics.filter((graphic) => graphic.label.toLowerCase().includes(normalizedQuery))
        : recentGraphics;

    function visible(graphics: readonly SemanticGraphic[]): readonly SemanticGraphic[] {
        return normalizedQuery
            ? graphics.filter((graphic) => graphic.label.toLowerCase().includes(normalizedQuery))
            : graphics;
    }

    function choose(key: string) {
        recordRecent(key);
        onSelect(key);
        onClose();
    }
</script>

{#if open}
    <div class="picker-backdrop" role="presentation" on:click={onClose}></div>
    <div class="picker" role="dialog" aria-modal="true" aria-labelledby="graphics-title" tabindex="-1">
        <h2 id="graphics-title">{title}</h2>
        <input class="search" type="search" bind:value={query} placeholder={$i18n.t('app.telegram.graphics.search')} aria-label={$i18n.t('app.telegram.graphics.search')} />

        {#if filteredRecent.length}
            <h3 class="picker-subtitle">{$i18n.t('app.telegram.graphics.recent')}</h3>
            <div class="grid">
                {#each filteredRecent as graphic (graphic.key + graphic.label)}
                    <button class:selected={graphic.key === initial} type="button" on:click={() => choose(graphic.key)}>
                        <span class="gico"><TelegramIcon name={graphic.key} size={20} label={graphic.label} /></span>
                        <span class="glabel">{graphic.label}</span>
                    </button>
                {/each}
            </div>
        {/if}

        {#each SEMANTIC_GRAPHIC_CATEGORIES as category (category.key)}
            {#if visible(getGraphicsForCategory(category.key)).length}
                <h3 class="picker-subtitle">{category.label}</h3>
                <div class="grid">
                    {#each visible(getGraphicsForCategory(category.key)) as graphic (category.key + graphic.label)}
                        <button class:selected={graphic.key === initial} type="button" on:click={() => choose(graphic.key)}>
                            <span class="gico"><TelegramIcon name={graphic.key} size={20} label={graphic.label} /></span>
                            <span class="glabel">{graphic.label}</span>
                        </button>
                    {/each}
                </div>
            {/if}
        {/each}

        <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.header.close')}</button>
    </div>
{/if}

<style>
    .picker-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .picker { position:fixed; inset:auto 0 0; z-index:41; max-height:82dvh; overflow:auto; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .search { box-sizing:border-box; width:100%; min-height:2.75rem; margin-bottom:.5rem; padding:.6rem .7rem; border:1px solid #cfd6e4; border-radius:.7rem; font:inherit; }
    .picker-subtitle { margin:1rem 0 .45rem; color:#4d5870; font-size:.85rem; }
    .grid { display:grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap:.5rem; }
    .grid button { display:flex; flex-direction:column; align-items:center; gap:.35rem; min-height:4.6rem; padding:.5rem .25rem; border:1px solid #e6e9f0; border-radius:.8rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
    .grid button.selected { border-color:#3867d6; background:#f2f5ff; color:#18243d; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .glabel { display:block; width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; text-align:center; font-size:.72rem; font-weight:600; }
    .close { width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
