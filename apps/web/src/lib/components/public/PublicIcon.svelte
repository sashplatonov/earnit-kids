<script lang="ts">
    export let name: string;
</script>

{#if name === 'list'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h1M12 8h4M8 12h1M12 12h4M8 16h1M12 16h4"/></svg>
{:else if name === 'clock'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6v5h5"/><path d="M5 11a8 8 0 1 1 2 6"/><path d="M12 8v5l3 2"/></svg>
{:else if name === 'coins'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v10M9 9h4a2 2 0 1 1 0 4h-2a2 2 0 1 0 0 4h4"/></svg>
{:else if name === 'gift'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="8" width="18" height="13" rx="2"/><path d="M12 8v13M3 12h18M7 8c-3 0-3-4-1-4 3 0 5 4 5 4M17 8c3 0 3-4 1-4-3 0-5 4-5 4"/></svg>
{:else if name === 'shield'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2 4 5v6c0 5 3 9 8 11 5-2 8-6 8-11V5z"/></svg>
{:else if name === 'bell'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>
{:else if name === 'family'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/><path d="M3 20c.5-4 2.5-6 6-6s5.5 2 6 6M14 15c3 0 5 1.5 6 4"/></svg>
{:else if name === 'check'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4 10-10"/></svg>
{:else if name === 'telegram'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 11 18-8-7 18-3-7z"/></svg>
{:else if name === 'arrow'}
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M14 7l5 5-5 5"/></svg>
{/if}
