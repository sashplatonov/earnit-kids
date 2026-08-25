<script lang="ts">
    import PublicShell from '$lib/components/PublicShell.svelte';
    import { useI18n } from '$lib/i18n/context';
    const i18n = useI18n();
</script>

<PublicShell title={$i18n.t('public.faq.metaTitle')} description={$i18n.t('public.faq.metaDescription')}>
    <section class="faq-shell" aria-labelledby="faq-title">
        <div class="article-header"><span class="public-panel__badge">{$i18n.t('public.faq.updatesAria')}</span><h1 id="faq-title">{$i18n.t('public.faq.title')}</h1><p>{$i18n.t('public.faq.intro')}</p></div>
        <div class="faq-grid">
            <article class="faq-card"><details open><summary>{$i18n.t('public.faq.q1Question')}</summary><p>{$i18n.t('public.faq.q1Answer')}</p></details></article>
            <article class="faq-card"><details><summary>{$i18n.t('public.faq.q2Question')}</summary><p>{$i18n.t('public.faq.q2Answer')}</p></details></article>
            <article class="faq-card"><details><summary>{$i18n.t('public.faq.q3Question')}</summary><p>{$i18n.t('public.faq.q3Answer')}</p></details></article>
        </div>
    </section>
</PublicShell>
