<script lang="ts">
    import PublicShell from '$lib/components/PublicShell.svelte';
    import { useI18n } from '$lib/i18n/context';

    export let kind: 'tasks' | 'shop';
    const i18n = useI18n();
    $: isTasks = kind === 'tasks';
</script>

<PublicShell
    title={$i18n.t(isTasks ? 'public.features.tasksTitle' : 'public.features.shopTitle')}
    description={$i18n.t(isTasks ? 'public.features.tasksDescription' : 'public.features.shopDescription')}
>
    <section class="feature-layout" aria-labelledby="feature-title">
        <div class="feature-layout__content">
            <span class="public-panel__badge">{$i18n.t(isTasks ? 'public.features.tasksBadge' : 'public.features.shopBadge')}</span>
            <h1 id="feature-title">{$i18n.t(isTasks ? 'public.features.tasksHeading' : 'public.features.shopHeading')}</h1>
            <p>{$i18n.t(isTasks ? 'public.features.tasksSubheading' : 'public.features.shopSubheading')}</p>
            <p>{$i18n.t(isTasks ? 'public.features.tasksBody' : 'public.features.shopBody')}</p>
            <div class="feature-bullets">
                <ul>
                    <li>{$i18n.t(isTasks ? 'public.features.tasksBulletOne' : 'public.features.shopBulletOne')}</li>
                    <li>{$i18n.t(isTasks ? 'public.features.tasksBulletTwo' : 'public.features.shopBulletTwo')}</li>
                    <li>{$i18n.t(isTasks ? 'public.features.tasksBulletThree' : 'public.features.shopBulletThree')}</li>
                </ul>
            </div>
            <div class="feature-layout__actions">
                <a class="btn btn--primary" href="/telegram">{$i18n.t('public.cta.openTelegram')} →</a>
                <a class="btn btn--ghost" href={$i18n.href('/faq')}>{$i18n.t('common.navigation.faq')}</a>
            </div>
        </div>
        <div class="feature-layout__panel">
            <img src={isTasks ? '/img/feature-tasks.svg' : '/img/feature-shop.svg'} alt={$i18n.t(isTasks ? 'public.features.tasksImageAlt' : 'public.features.shopImageAlt')} />
        </div>
    </section>
    <section class="feature-points" aria-label={$i18n.t('public.features.pageAria')}>
        <article class="feature-point"><h2>{$i18n.t('public.features.childCardTitle')}</h2><p>{$i18n.t('public.features.childCardText')}</p></article>
        <article class="feature-point"><h2>{$i18n.t('public.features.parentCardTitle')}</h2><p>{$i18n.t('public.features.parentCardText')}</p></article>
        <article class="feature-point"><h2>{$i18n.t('public.features.familyCardTitle')}</h2><p>{$i18n.t('public.features.familyCardText')}</p></article>
    </section>
</PublicShell>
