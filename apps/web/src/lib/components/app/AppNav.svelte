<script lang="ts">
    import { resolve } from '$app/paths';
    import {
        ADMIN_MANAGEMENT_SECTIONS,
        ADMIN_PRIMARY_SECTIONS,
        APP_SECTION_META,
        CHILD_PRIMARY_SECTIONS,
        COMMON_OVERFLOW_SECTIONS,
        type AppSection,
    } from '$lib/app/routes';
    import { logout } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import ChildSwitcher from './ChildSwitcher.svelte';

    export let isAdmin: boolean = false;
    export let activeSection: AppSection = 'analytics';
    export let requestsCount: number = 0;

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
            showToast('Не удалось выйти', 'error');
            return;
        }

        location.href = '/login.html';
    }
</script>

<svelte:window on:click={handleWindowClick} on:keydown={handleWindowKeydown} />

<nav class="nav" bind:this={navElement} aria-label="Основная навигация">
    <div class="nav__primary">
        {#if isAdmin}
            <ChildSwitcher />
            <div class="nav__group nav__group--parent">
                {#each primarySections as section (section)}
                    <a
                        class="nav__btn"
                        class:active={activeSection === section}
                        href={resolve('/app/[section]', { section })}
                        aria-current={activeSection === section ? 'page' : undefined}
                        on:click={closeMoreMenu}
                    >
                        <span class={`nav__btn-icon gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                        <span class="nav__btn-label">
                            {APP_SECTION_META[section].label}
                            {#if section === 'requests' && requestsCount > 0}
                                <span class="nav__counter">{requestsCount}</span>
                            {/if}
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
                        href={resolve('/app/[section]', { section })}
                        aria-current={activeSection === section ? 'page' : undefined}
                        on:click={closeMoreMenu}
                    >
                        <span class={`nav__btn-icon gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                        <span class="nav__btn-label">{APP_SECTION_META[section].label}</span>
                    </a>
                {/each}
            </div>
        {/if}

        <div class="nav__more-wrapper">
            <button class="nav__more" id="nav-more-btn" aria-haspopup="menu"
                aria-expanded={moreOpen} aria-controls="nav-more-dropdown"
                aria-label="Дополнительные разделы" on:click={() => (moreOpen = !moreOpen)}>
                <span class="nav__btn-icon gamified-icon icon-dots" aria-hidden="true"></span>
                <span class="nav__btn-label">Еще</span>
            </button>
            {#if moreOpen}
                <div class="nav__dropdown" id="nav-more-dropdown" role="menu">
                    {#if isAdmin}
                        <div class="nav__dropdown-group-label" role="presentation">Управление</div>
                        {#each ADMIN_MANAGEMENT_SECTIONS as section (section)}
                            <a class="nav__dropdown-item" role="menuitem" href={resolve('/app/[section]', { section })} on:click={closeMoreMenu}>
                                <span class={`gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                                <span>{APP_SECTION_META[section].label}</span>
                            </a>
                        {/each}
                        <div class="nav__dropdown-divider" role="presentation"></div>
                    {/if}
                    <div class="nav__dropdown-group-label" role="presentation">Разделы</div>
                    {#each COMMON_OVERFLOW_SECTIONS as section (section)}
                        <a class="nav__dropdown-item" role="menuitem" href={resolve('/app/[section]', { section })} on:click={closeMoreMenu}>
                            <span class={`gamified-icon ${APP_SECTION_META[section].iconClass}`} aria-hidden="true"></span>
                            <span>{APP_SECTION_META[section].label}</span>
                        </a>
                    {/each}
                    <div class="nav__dropdown-divider" role="presentation"></div>
                    <button class="nav__dropdown-item nav__dropdown-action" type="button" role="menuitem" on:click={handleLogout}>
                        <span class="gamified-icon icon-logout" aria-hidden="true"></span>
                        <span>Выйти</span>
                    </button>
                </div>
            {/if}
        </div>
    </div>
</nav>
