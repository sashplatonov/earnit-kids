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
</div>