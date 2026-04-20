<script lang="ts">
    import { createEventDispatcher } from 'svelte';
    import { tabStore } from '$lib/stores/tabs';
    import { logout } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';
    import ChildSwitcher from './ChildSwitcher.svelte';

    export let isAdmin: boolean = false;
    export let activeTab: string = 'analytics';
    export let requestsCount: number = 0;

    const dispatch = createEventDispatcher<{ switch: string }>();

    let moreOpen = false;
    let navElement: HTMLElement | null = null;

    function switchTab(tab: string) {
        tabStore.setTab(tab as Parameters<typeof tabStore.setTab>[0]);
        activeTab = tab;
        moreOpen = false;
        dispatch('switch', tab);
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
    <div class="nav__primary" role="tablist">
        {#if isAdmin}
            <ChildSwitcher />
            <div class="nav__group nav__group--parent">
                <button class="nav__btn" class:active={activeTab === 'analytics'}
                    role="tab" aria-selected={activeTab === 'analytics'} aria-controls="analytics-section"
                    data-tab="analytics" on:click={() => switchTab('analytics')}>
                    <span class="nav__btn-icon gamified-icon icon-chart" aria-hidden="true"></span>
                    <span class="nav__btn-label">Достижения</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'tasks'}
                    role="tab" aria-selected={activeTab === 'tasks'} aria-controls="section-tasks"
                    data-tab="tasks" on:click={() => switchTab('tasks')}>
                    <span class="nav__btn-icon gamified-icon icon-tasks" aria-hidden="true"></span>
                    <span class="nav__btn-label">Задания</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'requests'}
                    role="tab" aria-selected={activeTab === 'requests'} aria-controls="section-requests"
                    data-tab="requests" on:click={() => switchTab('requests')}>
                    <span class="nav__btn-icon gamified-icon icon-envelope" aria-hidden="true"></span>
                    <span class="nav__btn-label">Заявки {#if requestsCount > 0}<span class="nav__counter">{requestsCount}</span>{/if}</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'shop'}
                    role="tab" aria-selected={activeTab === 'shop'} aria-controls="section-shop"
                    data-tab="shop" on:click={() => switchTab('shop')}>
                    <span class="nav__btn-icon gamified-icon icon-shop" aria-hidden="true"></span>
                    <span class="nav__btn-label">Награды</span>
                </button>
            </div>
        {:else}
            <div class="nav__group nav__group--child">
                <button class="nav__btn" class:active={activeTab === 'analytics'}
                    role="tab" aria-selected={activeTab === 'analytics'} aria-controls="analytics-section"
                    data-tab="analytics" on:click={() => switchTab('analytics')}>
                    <span class="nav__btn-icon gamified-icon icon-chart" aria-hidden="true"></span>
                    <span class="nav__btn-label">Достижения</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'tasks'}
                    role="tab" aria-selected={activeTab === 'tasks'} aria-controls="section-tasks"
                    data-tab="tasks" on:click={() => switchTab('tasks')}>
                    <span class="nav__btn-icon gamified-icon icon-tasks" aria-hidden="true"></span>
                    <span class="nav__btn-label">Задания</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'shop'}
                    role="tab" aria-selected={activeTab === 'shop'} aria-controls="section-shop"
                    data-tab="shop" on:click={() => switchTab('shop')}>
                    <span class="nav__btn-icon gamified-icon icon-shop" aria-hidden="true"></span>
                    <span class="nav__btn-label">Награды</span>
                </button>
                <button class="nav__btn" class:active={activeTab === 'requests'}
                    role="tab" aria-selected={activeTab === 'requests'} aria-controls="section-requests"
                    data-tab="requests" on:click={() => switchTab('requests')}>
                    <span class="nav__btn-icon gamified-icon icon-envelope" aria-hidden="true"></span>
                    <span class="nav__btn-label">Заявки</span>
                </button>
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
                        <button class="nav__dropdown-item" role="menuitem" data-tab="limits" on:click={() => switchTab('limits')}>
                            <span class="gamified-icon icon-chart" aria-hidden="true"></span>
                            <span>Лимиты</span>
                        </button>
                        <button class="nav__dropdown-item" role="menuitem" data-tab="catalog" on:click={() => switchTab('catalog')}>
                            <span class="gamified-icon icon-tasks" aria-hidden="true"></span>
                            <span>Каталог</span>
                        </button>
                        <div class="nav__dropdown-divider" role="presentation"></div>
                    {/if}
                    <div class="nav__dropdown-group-label" role="presentation">Разделы</div>
                    <button class="nav__dropdown-item" role="menuitem" data-tab="history" on:click={() => switchTab('history')}>
                        <span class="gamified-icon icon-history-menu" aria-hidden="true"></span>
                        <span>История</span>
                    </button>
                    <button class="nav__dropdown-item" role="menuitem" data-tab="friends" on:click={() => switchTab('friends')}>
                        <span class="gamified-icon icon-star" aria-hidden="true"></span>
                        <span>Друзья</span>
                    </button>
                    <div class="nav__dropdown-divider" role="presentation"></div>
                    <div class="nav__dropdown-group-label" role="presentation">Настройки</div>
                    <button class="nav__dropdown-item" role="menuitem" data-tab="rules" on:click={() => switchTab('rules')}>
                        <span class="gamified-icon icon-rules-menu" aria-hidden="true"></span>
                        <span>Правила</span>
                    </button>
                    <button class="nav__dropdown-item" role="menuitem" data-tab="settings" on:click={() => switchTab('settings')}>
                        <span class="gamified-icon icon-settings-menu" aria-hidden="true"></span>
                        <span>Настройки</span>
                    </button>
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
