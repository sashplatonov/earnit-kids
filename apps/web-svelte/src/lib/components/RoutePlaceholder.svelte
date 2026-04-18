<script lang="ts">
    export let eyebrow = 'Migration route';
    export let title: string;
    export let description: string;
    export let links: Array<{ href: string; label: string }> = [];
    export let footnote = '';
</script>

<section class="panel">
    <p class="eyebrow">{eyebrow}</p>
    <h2>{title}</h2>
    <p>{description}</p>

    {#if links.length > 0}
        <div class="route-links">
            {#each links as link (link.href)}
                <a href={link.href} rel="external">{link.label}</a>
            {/each}
        </div>
    {/if}

    {#if footnote}
        <p>{footnote}</p>
    {/if}
</section>
