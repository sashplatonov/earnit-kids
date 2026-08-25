<script lang="ts">
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();
</script>

<header class="public-site-header" aria-label={$i18n.t('common.navigation.publicAria')}>
    <a class="public-site-brand" href={$i18n.href('/')}>
        <span class="public-site-brand-dot" aria-hidden="true"></span>
        {$i18n.t('common.brand.name')}
    </a>
    <nav class="public-top-nav" aria-label={$i18n.t('common.navigation.siteSections')}>
        <a href={$i18n.href('/')}>{$i18n.t('common.navigation.home')}</a>
        <a href={$i18n.href('/features/tasks')}>{$i18n.t('common.navigation.tasks')}</a>
        <a href={$i18n.href('/features/shop')}>{$i18n.t('common.navigation.shop')}</a>
        <a href={$i18n.href('/about')}>{$i18n.t('common.navigation.about')}</a>
        <a href={$i18n.href('/faq')}>{$i18n.t('common.navigation.faq')}</a>
    </nav>
    <LocaleSwitcher compact={true} />
    <a class="public-top-nav-cta" href="/public/index.html">{$i18n.t('common.actions.login')} →</a>
</header>
