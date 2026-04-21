<script lang="ts">
    import { page } from '$app/state';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();
</script>

<svelte:head>
    <title>{page.status} | {$i18n.t('common.brand.name')}</title>
</svelte:head>

<div class="error-shell">
    <section class="panel">
        <p class="eyebrow">{$i18n.t('errors.page.eyebrow')}</p>
        <h2>{page.status}</h2>
        <p>{page.error?.message ?? $i18n.t('errors.page.fallback')}</p>
        <a class="cta-link" href={$i18n.href('/')} rel="external">{$i18n.t('common.errors.backToHome')}</a>
    </section>
</div>
