<script lang="ts">
    import '$lib/public-site.css';
    import { page } from '$app/stores';
    import { useI18n } from '$lib/i18n/context';
    import { trackPublicPageView } from '$lib/observability/publicAnalytics';
    import PublicSiteHeader from '$lib/components/public/PublicSiteHeader.svelte';
    import PublicSiteFooter from '$lib/components/public/PublicSiteFooter.svelte';
    import type { LayoutData } from './$types';

    export let data: LayoutData;

    const i18n = useI18n();

    $: appConfig = data.appConfig;
    $: telegramUrl = appConfig?.telegramMiniAppUrl;
    $: pathname = $page.url.pathname;
    $: trackPublicPageView(pathname);
</script>

<div class="public-shell">
    <a class="skip-link" href="#public-main">{$i18n.t('public.shell.skipToContent')}</a>
    <PublicSiteHeader appConfig={data.appConfig} />
    <main id="public-main" class="public-inner" aria-label={$i18n.t('public.shell.mainAria')}>
        <slot />
    </main>
    <PublicSiteFooter />

    {#if telegramUrl}
        <a class="public-mobile-cta" href={telegramUrl} rel="external noopener">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 11 18-8-7 18-3-7z"/></svg>
            {$i18n.t('public.cta.openTelegram')}
        </a>
    {/if}
</div>