<script lang="ts">
    import { page } from '$app/stores';
    import { LOCALES, DEFAULT_LOCALE, LOCALE_COOKIE_NAME, stripLocaleFromPath, type Locale } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { updateFamilyLocale } from '$lib/services/api';

    interface Props {
        compact?: boolean;
        familyManaged?: boolean;
        mode?: 'family' | 'route';
        onLocaleChange?: ((locale: Locale) => Promise<void> | void) | null;
        readOnly?: boolean;
    }

    let {
        compact = false,
        familyManaged = false,
        mode = familyManaged ? 'family' : 'route',
        onLocaleChange = null,
        readOnly = false
    }: Props = $props();
    let busy = $state(false);
    let failedLocale: Locale | null = $state(null);
    let retryButton: HTMLButtonElement | undefined = $state();

    const i18n = useI18n();

    function labelFor(locale: Locale): string {
        return compact ? locale.toUpperCase() : $i18n.t(`common.locale.${locale}`);
    }

    // EXPLAIN: In route mode with no custom callback, render real <a> links
    // with ?lang= so the switcher works without JavaScript (Telegram WebView
    // CSP can block inline scripts and prevent hydration). The server hook
    // intercepts ?lang=, sets the locale cookie, and redirects.
    function routeHref(locale: Locale): string | null {
        if (mode !== 'route' || onLocaleChange || (familyManaged && !readOnly)) {
            return null;
        }
        const localizedPath = $i18n.swapLocale($page.url.pathname, locale);
        const nextPath = locale === DEFAULT_LOCALE ? stripLocaleFromPath(localizedPath) : localizedPath;
        const search = $page.url.search
            ? `${$page.url.search}&lang=${encodeURIComponent(locale)}`
            : `?lang=${encodeURIComponent(locale)}`;
        return `${nextPath}${search}${$page.url.hash}`;
    }

    async function handleChange(nextLocale: Locale) {
        if (mode === 'family' && familyManaged && !readOnly) {
            if (busy) return;
            busy = true;
            failedLocale = null;
            const result = await updateFamilyLocale(nextLocale);
            if (result.ok) {
                location.reload();
            } else {
                failedLocale = nextLocale;
                busy = false;
                await Promise.resolve();
                retryButton?.focus();
            }
            return;
        }

        if (onLocaleChange) {
            await onLocaleChange(nextLocale);
            return;
        }

        const localizedPath = $i18n.swapLocale($page.url.pathname, nextLocale);
        const nextPath = mode === 'route' && nextLocale === 'en' ? stripLocaleFromPath(localizedPath) : localizedPath;

        document.cookie = `${LOCALE_COOKIE_NAME}=${encodeURIComponent(nextLocale)}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
        location.assign(`${nextPath}${$page.url.search}${$page.url.hash}`);
    }
</script>

<div class="locale-switcher" class:locale-switcher--compact={compact} role="group" aria-label={$i18n.t('common.locale.switchLabel')}>
    <span class="locale-switcher__label">{$i18n.t(familyManaged ? 'common.locale.familyLabel' : 'common.locale.label')}</span>
    <div class="locale-switcher__options">
        {#each LOCALES as locale (locale)}
            {@const href = routeHref(locale)}
            {#if href}
                <a
                    class="locale-switcher__option"
                    class:locale-switcher__option--active={$i18n.locale === locale}
                    href={href}
                    aria-label={$i18n.t(`common.locale.select.${locale}`)}
                    aria-current={$i18n.locale === locale ? 'true' : undefined}
                >
                    <svg class="locale-switcher__flag" viewBox="0 0 24 16" aria-hidden="true" focusable="false">
                        {#if locale === 'ru'}
                            <rect width="24" height="5.34" fill="#fff" />
                            <rect y="5.33" width="24" height="5.34" fill="#2456a6" />
                            <rect y="10.66" width="24" height="5.34" fill="#d52b1e" />
                        {:else}
                            <rect width="24" height="16" fill="#fff" />
                            <path d="M0 0h24v2H0zm0 4h24v2H0zm0 4h24v2H0zm0 4h24v2H0z" fill="#b22234" />
                            <rect width="10.5" height="8.7" fill="#3c3b6e" />
                            <path d="M1.2 1.5h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zM2.3 3.4h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zM1.2 5.3h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1z" fill="#fff" />
                        {/if}
                    </svg>
                    <span>{labelFor(locale)}</span>
                </a>
            {:else}
                <button
                    class="locale-switcher__option"
                    class:locale-switcher__option--active={$i18n.locale === locale}
                    type="button"
                    aria-label={$i18n.t(`common.locale.select.${locale}`)}
                    aria-pressed={$i18n.locale === locale}
                    disabled={busy || readOnly || $i18n.locale === locale}
                    onclick={() => handleChange(locale)}
                >
                    <svg class="locale-switcher__flag" viewBox="0 0 24 16" aria-hidden="true" focusable="false">
                        {#if locale === 'ru'}
                            <rect width="24" height="5.34" fill="#fff" />
                            <rect y="5.33" width="24" height="5.34" fill="#2456a6" />
                            <rect y="10.66" width="24" height="5.34" fill="#d52b1e" />
                        {:else}
                            <rect width="24" height="16" fill="#fff" />
                            <path d="M0 0h24v2H0zm0 4h24v2H0zm0 4h24v2H0zm0 4h24v2H0z" fill="#b22234" />
                            <rect width="10.5" height="8.7" fill="#3c3b6e" />
                            <path d="M1.2 1.5h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zM2.3 3.4h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zM1.2 5.3h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1zm2.2 0h1v1h-1z" fill="#fff" />
                        {/if}
                    </svg>
                    <span>{labelFor(locale)}</span>
                </button>
            {/if}
        {/each}
    </div>
    {#if failedLocale}
        <div class="locale-switcher__error" role="alert">
            <span>{$i18n.t('common.locale.updateFailed')}</span>
            <button
                bind:this={retryButton}
                class="locale-switcher__retry"
                type="button"
                disabled={busy}
                onclick={() => failedLocale && handleChange(failedLocale)}
            >{$i18n.t('common.locale.retry')}</button>
        </div>
    {/if}
</div>

<style>
    .locale-switcher {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        color: inherit;
        font-size: 0.85rem;
    }

    .locale-switcher--compact {
        width: 100%;
        font-size: 0.78rem;
    }

    .locale-switcher--compact .locale-switcher__options {
        gap: 0;
        overflow: hidden;
        border: 1px solid rgba(125, 149, 187, 0.28);
        border-radius: 999px;
        background: #fff;
    }

    .locale-switcher--compact .locale-switcher__option {
        min-width: 0;
        min-height: 44px;
        border: 0;
        border-radius: 0;
        padding: 0.35rem 0.7rem;
    }

    .locale-switcher--compact .locale-switcher__option + .locale-switcher__option {
        border-left: 1px solid rgba(125, 149, 187, 0.2);
    }

    .locale-switcher--compact .locale-switcher__option--active {
        border-color: transparent;
        background: #202938;
        color: #fff;
    }

    .locale-switcher--compact .locale-switcher__option--active + .locale-switcher__option {
        border-left-color: transparent;
    }

    .locale-switcher__label {
        font-weight: 700;
        color: inherit;
        white-space: nowrap;
    }

    .locale-switcher__options {
        display: inline-flex;
        gap: 0.25rem;
    }

    .locale-switcher__option {
        min-height: 44px;
        min-width: 44px;
        border-radius: 999px;
        border: 1px solid rgba(125, 149, 187, 0.22);
        background: rgba(255, 255, 255, 0.9);
        color: inherit;
        font: inherit;
        padding: 0.35rem 0.65rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.35rem;
        cursor: pointer;
        text-decoration: none;
    }

    .locale-switcher__option:disabled {
        cursor: default;
        opacity: 1;
    }

    .locale-switcher__option--active {
        border-color: currentColor;
        font-weight: 700;
    }

    .locale-switcher__flag {
        width: 1.35rem;
        height: 0.9rem;
        border-radius: 0.1rem;
        box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.14);
        flex: 0 0 auto;
    }

    .locale-switcher__error {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
    }

    .locale-switcher__retry {
        min-height: 44px;
        border: 0;
        background: transparent;
        color: inherit;
        font: inherit;
        font-weight: 700;
        text-decoration: underline;
        cursor: pointer;
    }
</style>
