<script lang="ts">
    import { page } from '$app/state';
</script>

<svelte:head>
    <title>{page.status} | EarnIt Kids Svelte Migration</title>
</svelte:head>

<div class="error-shell">
    <section class="panel">
        <p class="eyebrow">Error state</p>
        <h2>{page.status}</h2>
        <p>{page.error?.message ?? 'Unexpected route error'}</p>
        <a class="cta-link" href="/" rel="external">Back to root</a>
    </section>
</div>
