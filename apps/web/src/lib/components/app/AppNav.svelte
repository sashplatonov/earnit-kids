<script lang="ts">
    import { get } from 'svelte/store';
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
    import { importTasks, importShopItems, logout } from '$lib/services/api';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import { buildPrintCatalogUrl } from '$lib/services/printCatalog';
    import { appStore } from '$lib/stores/app';
    import { modalStore } from '$lib/stores/modal';
    import { showToast } from '$lib/stores/toasts';
    import ChildSwitcher from './ChildSwitcher.svelte';
    import LocaleSwitcher from '$lib/components/LocaleSwitcher.svelte';
    import type { MessageKey } from '$lib/i18n';

    export let isAdmin: boolean = false;
    export let isSuperAdmin: boolean = false;
    export let canAccessSuperAdmin: boolean = false;
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

    function handlePrint() {
        moreOpen = false;

        let childId: string | number | null = null;
        if (isAdmin) {
            const state = get(appStore);
            childId = state.currentChildId ?? state.children[0]?.id ?? null;
            if (childId == null) {
                showToast($i18n.t('common.errors.selectChildFirst' as MessageKey), 'error');
                return;
            }
        }

        const printUrl = buildPrintCatalogUrl($i18n.href('/print/catalog'), childId);
        const printWindow = window.open(printUrl, '_blank', 'noopener');
        if (!printWindow) {
            showToast($i18n.t('common.printPopupBlocked' as MessageKey), 'error');
        }
    }

    function handleImportCsv() {
        moreOpen = false;

        const state = get(appStore);
        let childId: unknown = state.currentChildId;
        if (childId == null) {
            childId = state.children[0]?.id ?? null;
        }

        if (childId == null) {
            showToast($i18n.t('common.errors.selectChildFirst' as MessageKey), 'error');
            return;
        }

        const kind = activeSection === 'shop' ? 'shop' : 'tasks';

        modalStore.open('csv-import-modal', {
            kind,
            onSubmit: async ({ kind: importKind, rows }: { kind: 'tasks' | 'shop'; rows: Array<Record<string, unknown>> }) => {
                const importFn = importKind === 'tasks' ? importTasks : importShopItems;
                const result = await importFn({ childId, rows });

                if (result.ok && result.data && typeof result.data === 'object') {
                    applyDataSnapshot(result.data as Record<string, unknown>);
                }

                return result as { ok: boolean; error?: string };
            },
        });
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

                    <div class="nav__dropdown-group-label" role="presentation">Import / Export</div>
                    <button class="nav__dropdown-item" type="button" role="menuitem" on:click={handleImportCsv}>
                        <svg viewBox="0 0 24 24" aria-hidden="true" class="nav__dropdown-icon">
                            <path d="M12 4v11"></path>
                            <path d="m8 11 4 4 4-4"></path>
                            <path d="M5 19h14"></path>
                        </svg>
                        <span>{$i18n.t(`common.importCsv` as MessageKey)}</span>
                    </button>
                    <button
                        class="nav__dropdown-item"
                        type="button"
                        role="menuitem"
                        on:click={handlePrint}
                    >
                        <svg viewBox="0 0 24 24" aria-hidden="true" class="nav__dropdown-icon">
                            <path d="M7 9V4h10v5"></path>
                            <rect x="5" y="13" width="14" height="7" rx="1.8"></rect>
                            <rect x="4" y="9" width="16" height="6" rx="2"></rect>
                            <path d="M8 16h8"></path>
                        </svg>
                        <span>{$i18n.t(`common.printPdf` as MessageKey)}</span>
                    </button>
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

<style>
    .nav__dropdown-icon {
        width: 1.05rem;
        height: 1.05rem;
        flex: none;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.85;
        stroke-linecap: round;
        stroke-linejoin: round;
    }

    .nav__dropdown-icon rect {
        fill: none;
        stroke: currentColor;
    }

</style>
