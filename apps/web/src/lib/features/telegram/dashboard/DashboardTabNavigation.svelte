<script lang="ts">
    import TelegramCoin from '$lib/components/telegram/TelegramCoin.svelte';
    import TelegramIcon from '$lib/components/telegram/TelegramIcon.svelte';

    type DashboardTab = {
        id: string;
        label: string;
        icon: string;
    };

    interface Props {
        tabs: readonly DashboardTab[];
        activeTab: string;
        onSelect: (tabId: string) => void;
        onKeydown: (event: KeyboardEvent, tabId: string) => void;
        ariaLabel: string;
    }

    let {
        tabs,
        activeTab,
        onSelect,
        onKeydown,
        ariaLabel
    }: Props = $props();
</script>

<div class="tabs-wrap">
    <div class="tabs" role="tablist" aria-label={ariaLabel}>
        {#each tabs as tab (tab.id)}
            <button
                type="button"
                id={`tab-${tab.id}`}
                class="tab"
                class:active={activeTab === tab.id}
                role="tab"
                aria-selected={activeTab === tab.id}
                aria-controls={`panel-${tab.id}`}
                tabindex={activeTab === tab.id ? 0 : -1}
                onclick={() => onSelect(tab.id)}
                onkeydown={(event) => onKeydown(event, tab.id)}
            >
                <span class="tab-ico" aria-hidden="true">
                    {#if tab.id === 'coins'}
                        <TelegramCoin size={17} />
                    {:else}
                        <TelegramIcon name={tab.icon as import('$lib/components/telegram/telegramIconMap').TelegramIconName} size={17} strokeWidth={2} />
                    {/if}
                </span>
                <span class="tab-label">{tab.label}</span>
            </button>
        {/each}
    </div>
</div>

<style>
    .tabs-wrap { position: fixed; left: 50%; bottom: 0; z-index: 20; width: min(800px, 100%); transform: translateX(-50%); padding: 7px 12px calc(7px + env(safe-area-inset-bottom)); background: rgb(255 255 255 / 94%); border-top: 1px solid #e5e8f0; }
    .tabs { width: 100%; display: grid; grid-template-columns: repeat(5, 1fr); gap: 5px; }
    .tab { min-width: 0; min-height: 56px; border: 0; border-radius: 10px; background: transparent; color: #8a92a5; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; }
    .tab.active { background: #5e6fec; color: #fff; }
    .tab-ico, .tab-label { display: block; }
    .tab-label { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .tab:focus-visible { outline: 3px solid #273fd0; outline-offset: 2px; }
</style>
