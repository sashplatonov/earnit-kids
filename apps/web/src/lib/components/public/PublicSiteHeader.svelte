<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { trackPublicCtaClick } from '$lib/observability/publicAnalytics';
    import type { AppConfig } from '$lib/types/config';

    export let appConfig: AppConfig;

    const i18n = useI18n();

    let menuOpen = false;

    const navItems = [
        { href: '/', label: $i18n.t('common.navigation.home') },
        { href: '/how', label: $i18n.t('common.navigation.how') },
        { href: '/tasks', label: $i18n.t('common.navigation.tasks') },
        { href: '/rewards', label: $i18n.t('common.navigation.rewards') },
        { href: '/parents', label: $i18n.t('common.navigation.parents') },
        { href: '/faq', label: $i18n.t('common.navigation.faq') },
    ];

    $: telegramUrl = appConfig.telegramMiniAppUrl;

    function isActive(href: string): boolean {
        if (typeof window === 'undefined') return false;
        return window.location.pathname === href;
    }

    function closeMenu() {
        menuOpen = false;
    }

    function onKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && menuOpen) {
            closeMenu();
            document.getElementById('public-menu-button')?.focus();
        }
    }

    function trackCta(placement: 'header' | 'mobile_menu') {
        trackPublicCtaClick(placement, window.location.pathname);
    }
</script>

<svelte:window on:keydown={onKeydown} />

<header class="public-site-header" aria-label={$i18n.t('public.shell.siteAria')}>
    <div class="public-header-row">
        <a class="public-site-brand" href="/">
            <img class="public-brand-icon" src="/img/public/app-icon.png" alt="" width="38" height="38" />
            {$i18n.t('common.brand.name')}
        </a>

        <nav class="public-top-nav" aria-label={$i18n.t('common.navigation.siteSections')}>
            {#each navItems as item (item.href)}
                <a href={item.href} aria-current={isActive(item.href) ? 'page' : undefined}>{item.label}</a>
            {/each}
        </nav>

        {#if telegramUrl}
            <a class="public-cta-link" href={telegramUrl} rel="external noopener" on:click={() => trackCta('header')}>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 11 18-8-7 18-3-7z"/></svg>
                {$i18n.t('public.cta.openTelegram')}
            </a>
        {/if}

        <button
            id="public-menu-button"
            class="public-menu-button"
            type="button"
            aria-expanded={menuOpen}
            aria-controls="public-mobile-menu"
            aria-label={$i18n.t('public.shell.menuButton')}
            on:click={() => (menuOpen = !menuOpen)}
        >
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
            <span aria-hidden="true"></span>
        </button>
    </div>

    {#if menuOpen}
        <div class="public-menu-backdrop" on:click={closeMenu} on:keydown={(e) => e.key === 'Enter' && closeMenu()} role="presentation" tabindex="-1"></div>
        <nav id="public-mobile-menu" class="public-mobile-menu" aria-label={$i18n.t('common.navigation.siteSections')}>
            {#each navItems as item (item.href)}
                <a href={item.href} on:click={closeMenu} aria-current={isActive(item.href) ? 'page' : undefined}>{item.label}</a>
            {/each}
            {#if telegramUrl}
                <a class="public-cta-link" href={telegramUrl} rel="external noopener" on:click={() => { closeMenu(); trackCta('mobile_menu'); }}>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 11 18-8-7 18-3-7z"/></svg>
                    {$i18n.t('public.cta.openTelegram')}
                </a>
            {/if}
        </nav>
    {/if}
</header>

<style>
    .public-site-header {
        position: sticky;
        top: 0;
        z-index: 30;
        background: rgb(247 248 251 / 94%);
        backdrop-filter: blur(14px);
        border-bottom: 1px solid rgb(221 227 235 / 90%);
    }

    .public-header-row {
        display: flex;
        align-items: center;
        gap: 1rem;
        min-height: 4.375rem;
        width: min(calc(100% - 2rem), 70rem);
        margin: 0 auto;
    }

    .public-site-brand {
        display: flex;
        align-items: center;
        gap: .625rem;
        font-weight: 850;
        white-space: nowrap;
        letter-spacing: -.02em;
        color: #1c2534;
    }

    .public-brand-icon {
        width: 2.375rem;
        height: 2.375rem;
        border-radius: .75rem;
    }

    .public-cta-link svg {
        width: 1.125rem;
        height: 1.125rem;
        stroke: currentColor;
        fill: none;
        stroke-width: 2;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .public-top-nav {
        display: flex;
        gap: .1875rem;
        overflow-x: auto;
        padding: .375rem;
        background: #eef2f7;
        border: 1px solid #e1e6ed;
        border-radius: .8125rem;
        margin-left: auto;
        scrollbar-width: none;
    }

    .public-top-nav::-webkit-scrollbar {
        display: none;
    }

    .public-top-nav a {
        white-space: nowrap;
        padding: .5rem .6875rem;
        border-radius: .5625rem;
        color: #5b6678;
        font-size: .8125rem;
        font-weight: 750;
    }

    .public-top-nav a:hover {
        color: #275fd6;
        background: #f9fbff;
    }

    .public-top-nav a[aria-current="page"] {
        color: #275fd6;
        background: #fff;
        box-shadow: 0 1px 4px rgb(37 61 97 / 9%);
    }

    .public-cta-link {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-height: 2.75rem;
        padding: .625rem .9375rem;
        border-radius: .75rem;
        background: #275fd6;
        color: #fff;
        font-weight: 800;
        white-space: nowrap;
        margin-left: .1875rem;
    }

    .public-cta-link:hover {
        background: #1d4fb9;
    }

    .public-menu-button {
        display: none;
        flex-direction: column;
        justify-content: center;
        gap: .25rem;
        width: 2.75rem;
        height: 2.75rem;
        border: 1px solid #dde3eb;
        border-radius: .75rem;
        background: #fff;
        cursor: pointer;
        margin-left: auto;
    }

    .public-menu-button span {
        display: block;
        width: 1.25rem;
        height: 2px;
        margin: 0 auto;
        background: #1c2534;
        transition: transform .2s, opacity .2s;
    }

    .public-menu-button[aria-expanded="true"] span:nth-child(1) {
        transform: translateY(6px) rotate(45deg);
    }

    .public-menu-button[aria-expanded="true"] span:nth-child(2) {
        opacity: 0;
    }

    .public-menu-button[aria-expanded="true"] span:nth-child(3) {
        transform: translateY(-6px) rotate(-45deg);
    }

    .public-menu-backdrop {
        position: fixed;
        inset: 0;
        z-index: 40;
        background: rgb(15 24 45 / 35%);
    }

    .public-mobile-menu {
        position: fixed;
        top: 4.375rem;
        right: 0;
        left: 0;
        z-index: 41;
        display: flex;
        flex-direction: column;
        gap: .25rem;
        padding: 1rem 1.25rem calc(1rem + env(safe-area-inset-bottom));
        background: #fff;
        box-shadow: 0 1rem 3rem rgb(27 39 73 / 18%);
    }

    .public-mobile-menu a {
        min-height: 2.75rem;
        padding: .75rem .625rem;
        border-radius: .75rem;
        color: #33415f;
        font-weight: 700;
    }

    .public-mobile-menu a:hover {
        background: #f2f5ff;
        color: #275fd6;
    }

    .public-mobile-menu a[aria-current="page"] {
        background: #edf3ff;
        color: #275fd6;
    }

    .public-mobile-menu .public-cta-link {
        margin-top: .5rem;
        margin-left: 0;
        text-align: center;
    }

    :global(button:focus-visible),
    a:focus-visible {
        outline: 3px solid rgb(128 170 255 / 50%);
        outline-offset: 2px;
    }

    @media (max-width: 900px) {
        .public-top-nav,
        .public-header-row > .public-cta-link {
            display: none;
        }
        .public-menu-button {
            display: flex;
        }
    }
</style>