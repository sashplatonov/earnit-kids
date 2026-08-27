<script lang="ts">
    import { onMount } from 'svelte';
    import WorkspaceRoleResolver from '$lib/features/workspace/WorkspaceRoleResolver.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import { useI18n } from '$lib/i18n/context';
    import type { PageData } from './$types';

    interface Props {
        data: PageData;
    }

    let { data }: Props = $props();
    const i18n = useI18n();

    onMount(() => {
        document.body.classList.add('workspace-page');
        return () => document.body.classList.remove('workspace-page');
    });
</script>

<svelte:head>
    <title>EarnIt Kids · {$i18n.t(data.role === 'child' ? 'app.shell.childWorkspaceTitle' : 'app.shell.parentWorkspaceTitle')}</title>
</svelte:head>

    {#if data.session?.languageSetupRequired && data.session.permission === 'family_admin'}
        <main class="language-setup" aria-labelledby="language-setup-title">
            <h1 id="language-setup-title">{$i18n.t('app.familyLocale.setupTitle')}</h1>
            <p>{$i18n.t('app.familyLocale.setupDescription')}</p>
            <LocaleSwitcher familyManaged />
        </main>
    {:else}
        <WorkspaceRoleResolver role={data.role} permission={data.session?.permission ?? null} publicOrigin={data.publicOrigin} allowPreview showBrowserPush={false} showSessionActions />
    {/if}

<style>
    :global(body.workspace-page) {
        --color-bg: #f8fafc;
        --color-bg-light: #f1f5fd;
        --color-bg-card: #ffffff;
        --color-bg-hover: #eef4ff;
        --color-text-high-contrast: #0f172a;
        --color-text-strong: #18243d;
        --color-text-soft: #33415f;
        --color-text-muted: #66718a;
        --color-border-strong: #dfe7f5;
        background: #f8fafc;
        padding-top: 0 !important;
    }
</style>
