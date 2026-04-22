<script lang="ts">
    import { page } from '$app/stores';
    import { LOCALES, LOCALE_COOKIE_NAME, type Locale } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';

    export let compact: boolean = false;

    const i18n = useI18n();

    function handleChange(event: Event) {
        const nextLocale = (event.currentTarget as HTMLSelectElement).value as Locale;
        const nextPath = $i18n.swapLocale($page.url.pathname, nextLocale);

        document.cookie = `${LOCALE_COOKIE_NAME}=${encodeURIComponent(nextLocale)}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
        location.assign(`${nextPath}${$page.url.search}${$page.url.hash}`);
    }
</script>

<label class="locale-switcher" class:locale-switcher--compact={compact}>
    <span class="locale-switcher__label">{$i18n.t('common.locale.label')}</span>
    <select
        class="locale-switcher__select"
        aria-label={$i18n.t('common.locale.switchLabel')}
        on:change={handleChange}
        value={$i18n.locale}
    >
        {#each LOCALES as locale (locale)}
            <option value={locale}>{$i18n.t(`common.locale.${locale}`)}</option>
        {/each}
    </select>
</label>

<style>
    .locale-switcher {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        color: inherit;
        font-size: 0.85rem;
    }

    .locale-switcher--compact {
        font-size: 0.78rem;
    }

    .locale-switcher__label {
        font-weight: 700;
        color: inherit;
        white-space: nowrap;
    }

    .locale-switcher__select {
        border-radius: 999px;
        border: 1px solid rgba(125, 149, 187, 0.22);
        background: rgba(255, 255, 255, 0.9);
        color: inherit;
        font: inherit;
        padding: 0.35rem 0.75rem;
    }
</style>
