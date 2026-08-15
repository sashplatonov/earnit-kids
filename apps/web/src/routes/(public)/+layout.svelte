<script lang="ts">
    import '$lib/public-site.css';
    import { useI18n } from '$lib/i18n/context';
    import PublicSiteHeader from '$lib/components/public/PublicSiteHeader.svelte';
    import PublicSiteFooter from '$lib/components/public/PublicSiteFooter.svelte';
    import type { LayoutData } from './$types';

    export let data: LayoutData;

    const i18n = useI18n();
</script>

<div class="public-shell">
    <a class="skip-link" href="#public-main">{$i18n.t('public.shell.skipToContent')}</a>
    <PublicSiteHeader appConfig={data.appConfig} />
    <main id="public-main" class="public-inner" aria-label={$i18n.t('public.shell.mainAria')}>
        <slot />
    </main>
    <PublicSiteFooter />
</div>