<script lang="ts">
    export let selectedPeriod: string;
    export let loading = false;
    export let updatedAt: string;
    export let onChange: (period: string) => void;

    const periods = ['7d', '30d', '90d', 'all'];
</script>

<div class="toolbar">
    <div class="segment">
        {#each periods as period (period)}
            <button
                type="button"
                class="seg"
                class:active={selectedPeriod === period}
                disabled={loading}
                aria-pressed={selectedPeriod === period}
                on:click={() => onChange(period)}
            >
                <slot name="label" period={period}>{period}</slot>
            </button>
        {/each}
    </div>
    <div class="updated">{updatedAt}</div>
</div>

<style>
    .toolbar { margin: 0 0 8px; }
    .segment { width: 100%; display: grid; grid-template-columns: repeat(4, 1fr); gap: 3px; padding: 3px; border-radius: 9px; background: #f0f2f8; }
    .seg { border: 0; background: transparent; color: #727b91; padding: 7px 4px; border-radius: 7px; font: inherit; font-size: 11px; font-weight: 700; cursor: pointer; min-height: 38px; }
    .seg.active { background: #fff; color: #6274e8; box-shadow: 0 1px 4px rgb(34 44 80 / 8%); }
    .seg:disabled { cursor: wait; opacity: .65; }
    .updated { margin: 5px 4px 0; text-align: right; color: #8c94a6; font-size: 9px; }
</style>
