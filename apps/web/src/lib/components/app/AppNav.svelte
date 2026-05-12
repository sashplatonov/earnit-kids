<script lang="ts">
    import { resolve } from '$app/paths';
    import {
        ADMIN_MANAGEMENT_SECTIONS,
        ADMIN_PRIMARY_SECTIONS,
        APP_SECTION_META,
        CHILD_PRIMARY_SECTIONS,
        COMMON_OVERFLOW_SECTIONS,
        getAppSectionLabelKey,
        type AppSection,
    } from '$lib/app/routes';
    import { useI18n } from '$lib/i18n/context';
    import { logout } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import ChildSwitcher from './ChildSwitcher.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';

    export let isAdmin: boolean = false;
    export let isSuperAdmin: boolean = false;
    export let canAccessSuperAdmin: boolean = false;
    export let canManageParentAccess: boolean = false;
    export let activeSection: AppSection = 'analytics';
    export let requestsCount: number = 0;


    const i18n = useI18n();

    let moreOpen = false;
    let navElement: HTMLElement | null = null;

    $: primarySections = isAdmin ? ADMIN_PRIMARY_SECTIONS : CHILD_PRIMARY_SECTIONS;

    function closeMoreMenu() {
        moreOpen = false;
    }

    function handleWindowClick(event: MouseEvent) {
        const target = event.target;

        if (!(target instanceof Node)) {
            return;
        }

        if (moreOpen && navElement && !navElement.contains(target)) {
            moreOpen = false;
        }
    }

    function handleWindowKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape') {
            moreOpen = false;
        }
    }

    async function handleLogout() {
        moreOpen = false;

        const ok = await logout();
        if (!ok) {
            showToast($i18n.t('app.shell.loginError'), 'error');
            return;
        }

        location.href = $i18n.href('/login');
    }
</script>

<svelte:window on:click={handleWindowClick} on:keydown={handleWindowKeydown} />

<nav class="nav" bind:this={navElement} aria-label={$i18n.t('common.navigation.main')}>
    <div class="nav__primary">
        {#if isAdmin}
            <ChildSwitcher />
            <div class="nav__group nav__group--parent">
                {#each primarySections as section (section)}
                    <a
                        class="nav__btn"
                        class:active={activeSection === section}
                        href={$i18n.href(resolve('/app/[section]', { section }))}
                        aria-current={activeSection === section ? 'page' : undefined}
                        aria-label={section === 'requests' && requestsCount > 0
                            ? `${$i18n.t(getAppSectionLabelKey(section))}: ${$i18n.formatNumber(requestsCount)}`
                            : $i18n.t(getAppSectionLabelKey(section))}
                        on:click={closeMoreMenu}
                    >
                        <span class={`nav__btn-icon gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                        {#if section === 'requests' && requestsCount > 0}
                            <span class="nav__counter">{$i18n.formatNumber(requestsCount)}</span>
                        {/if}
                        <span class="nav__btn-label">
                            {$i18n.t(getAppSectionLabelKey(section))}
                        </span>
                    </a>
                {/each}
            </div>
        {:else}
            <div class="nav__group nav__group--child">
                {#each primarySections as section (section)}
                    <a
                        class="nav__btn"
                        class:active={activeSection === section}
                        href={$i18n.href(resolve('/app/[section]', { section }))}
                        aria-current={activeSection === section ? 'page' : undefined}
                        on:click={closeMoreMenu}
                    >
                        <span class={`nav__btn-icon gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                        <span class="nav__btn-label">{$i18n.t(getAppSectionLabelKey(section))}</span>
                    </a>
                {/each}
            </div>
        {/if}

        <div class="nav__more-wrapper">
            <button class="nav__more" id="nav-more-btn" aria-haspopup="menu"
                aria-expanded={moreOpen} aria-controls="nav-more-dropdown"
                aria-label={$i18n.t('app.shell.moreAria')} on:click={() => (moreOpen = !moreOpen)}>
                <span class="nav__btn-icon gamified-icon icon-dots" aria-hidden="true"></span>
                <span class="nav__btn-label">{$i18n.t('common.navigation.more')}</span>
            </button>
            {#if moreOpen}
                <div class="nav__dropdown" id="nav-more-dropdown" role="menu">
                    {#if isAdmin}
                        <div class="nav__dropdown-group-label" role="presentation">{$i18n.t('common.navigation.management')}</div>
                        {#if canManageParentAccess}
                            <a
                                class="nav__dropdown-item"
                                class:active={activeSection === 'settings'}
                                role="menuitem"
                                href={`${$i18n.href(resolve('/app/[section]', { section: 'settings' }))}#parent-access-section`}
                                on:click={closeMoreMenu}
                            >
                                <span class="gamified-icon icon-profile" aria-hidden="true"></span>
                                <span>{$i18n.t('app.parentAccess.title')}</span>
                            </a>
                        {/if}
                        {#each ADMIN_MANAGEMENT_SECTIONS as section (section)}
                            <a class="nav__dropdown-item" role="menuitem" href={$i18n.href(resolve('/app/[section]', { section }))} on:click={closeMoreMenu}>
                                <span class={`gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                                <span>{$i18n.t(getAppSectionLabelKey(section))}</span>
                            </a>
                        {/each}
                        <div class="nav__dropdown-divider" role="presentation"></div>
                    {/if}
                    {#if isSuperAdmin || canAccessSuperAdmin}
                        <div class="nav__dropdown-group-label" role="presentation">{$i18n.t('common.navigation.admin')}</div>
                        <a class="nav__dropdown-item" role="menuitem" href={$i18n.href('/super-admin')} on:click={closeMoreMenu}>
                            <span class="gamified-icon icon-shield" aria-hidden="true"></span>
                            <span>{$i18n.t('app.shell.superAdmin')}</span>
                        </a>
                        <div class="nav__dropdown-divider" role="presentation"></div>
                    {/if}

                    <div class="nav__dropdown-group-label" role="presentation">{$i18n.t('common.navigation.sections')}</div>
                    {#each COMMON_OVERFLOW_SECTIONS as section (section)}
                        <a class="nav__dropdown-item" role="menuitem" href={$i18n.href(resolve('/app/[section]', { section }))} on:click={closeMoreMenu}>
                            <span class={`gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                            <span>{$i18n.t(getAppSectionLabelKey(section))}</span>
                        </a>
                    {/each}
                    <div class="nav__dropdown-divider" role="presentation"></div>
                    <div style="padding: 0.5rem 1rem; display: flex; justify-content: center;">
                        <LocaleSwitcher compact={true} />
                    </div>
                    <div class="nav__dropdown-divider" role="presentation"></div>
                    <button class="nav__dropdown-item nav__dropdown-action" type="button" role="menuitem" on:click={handleLogout}>
                        <span class="gamified-icon icon-logout" aria-hidden="true"></span>
                        <span>{$i18n.t('app.shell.logout')}</span>
                    </button>
                </div>
            {/if}
        </div>
    </div>
</nav>
