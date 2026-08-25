<script lang="ts">
    import { page } from '$app/stores';
    import { LOCALES, LOCALE_COOKIE_NAME, type Locale } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { fetchWithCsrf } from '$lib/services/api';

    export let compact: boolean = false;
    export let familyManaged: boolean = false;
    let busy = false;

    const i18n = useI18n();

    async function handleChange(nextLocale: Locale) {
        if (familyManaged) {
            if (busy) return;
            busy = true;
            await fetchWithCsrf('/api/family/locale', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ locale: nextLocale }),
            });
            location.reload();
            return;
        }

        const nextPath = $i18n.swapLocale($page.url.pathname, nextLocale);

        document.cookie = `${LOCALE_COOKIE_NAME}=${encodeURIComponent(nextLocale)}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
        location.assign(`${nextPath}${$page.url.search}${$page.url.hash}`);
    }
</script>

<div class="locale-switcher" class:locale-switcher--compact={compact} role="group" aria-label={$i18n.t('common.locale.switchLabel')}>
    <span class="locale-switcher__label">{$i18n.t('common.locale.label')}</span>
    <div class="locale-switcher__options">
        {#each LOCALES as locale (locale)}
            <button
                class="locale-switcher__option"
                class:locale-switcher__option--active={$i18n.locale === locale}
                type="button"
                aria-label={$i18n.t(`common.locale.select.${locale}`)}
                aria-pressed={$i18n.locale === locale}
                disabled={busy || $i18n.locale === locale}
                on:click={() => handleChange(locale)}
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
                <span>{$i18n.t(`common.locale.${locale}`)}</span>
            </button>
        {/each}
    </div>
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
        font-size: 0.78rem;
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
</style>
