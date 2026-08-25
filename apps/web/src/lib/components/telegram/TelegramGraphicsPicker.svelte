<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import type { MessageKey } from '$lib/i18n';
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
        ? recentGraphics.filter((graphic) => graphicLabel(graphic.key).toLowerCase().includes(normalizedQuery))
        : recentGraphics;

    function visible(graphics: readonly SemanticGraphic[]): readonly SemanticGraphic[] {
        return normalizedQuery
            ? graphics.filter((graphic) => graphicLabel(graphic.key).toLowerCase().includes(normalizedQuery))
            : graphics;
    }

    function choose(key: string) {
        recordRecent(key);
        onSelect(key);
        onClose();
    }

    function graphicLabel(key: string): string { return $i18n.t(`app.telegram.graphics.labels.${key}` as MessageKey); }
    function categoryLabel(key: string): string { return $i18n.t(`app.telegram.graphics.categories.${key}` as MessageKey); }
</script>

{#if open}
    <div class="picker-backdrop" role="presentation" on:click={onClose}></div>
    <div class="picker" role="dialog" aria-modal="true" aria-labelledby="graphics-title" tabindex="-1">
        <h2 id="graphics-title">{title}</h2>
        <input class="search" type="search" bind:value={query} placeholder={$i18n.t('app.telegram.graphics.search')} aria-label={$i18n.t('app.telegram.graphics.search')} />

        {#if filteredRecent.length}
            <h3 class="picker-subtitle">{$i18n.t('app.telegram.graphics.recent')}</h3>
            <div class="group">
                {#each filteredRecent as graphic (graphic.key)}
                    <button class:selected={graphic.key === initial} type="button" on:click={() => choose(graphic.key)}>
                        <span class="gico"><TelegramIcon name={graphic.key} size={20} label={graphicLabel(graphic.key)} /></span>
                        <span class="grow"><span class="glabel">{graphicLabel(graphic.key)}</span></span>
                        {#if graphic.key === initial}<span class="badge">{$i18n.t('app.telegram.graphics.selected')}</span>{/if}
                    </button>
                {/each}
            </div>
        {/if}

        {#each SEMANTIC_GRAPHIC_CATEGORIES as category (category.key)}
            {#if visible(getGraphicsForCategory(category.key)).length}
                <h3 class="picker-subtitle">{categoryLabel(category.key)}</h3>
                <div class="group">
                    {#each visible(getGraphicsForCategory(category.key)) as graphic (graphic.key)}
                        <button class:selected={graphic.key === initial} type="button" on:click={() => choose(graphic.key)}>
                            <span class="gico"><TelegramIcon name={graphic.key} size={20} label={graphicLabel(graphic.key)} /></span>
                            <span class="grow"><span class="glabel">{graphicLabel(graphic.key)}</span></span>
                            {#if graphic.key === initial}<span class="badge">{$i18n.t('app.telegram.graphics.selected')}</span>{/if}
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
    .group { border:1px solid #e6e9f0; border-radius:.9rem; background:#fff; padding:0 .6rem; }
    .group button { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem 0; border:0; border-bottom:1px solid #edf0f5; background:transparent; color:#18243d; font:inherit; text-align:left; cursor:pointer; }
    .group button:last-child { border-bottom:0; }
    .group button.selected { color:#2854ba; }
    .gico { display:grid; place-items:center; width:2.25rem; height:2.25rem; flex:0 0 auto; border-radius:.65rem; background:#eef0ff; color:#5b63e9; }
    .grow { flex:1; min-width:0; }
    .glabel { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-weight:600; }
    .badge { flex:0 0 auto; padding:.2rem .55rem; border-radius:999px; background:#eef2ff; color:#2854ba; font-size:.72rem; font-weight:700; }
    .close { width:100%; min-height:2.75rem; margin-top:.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
