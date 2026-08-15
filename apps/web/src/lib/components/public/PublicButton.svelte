<script lang="ts">
    export let href: string | undefined = undefined;
    export let variant: 'primary' | 'secondary' = 'primary';
    export let rel: string | undefined = undefined;

    const tag = href ? 'a' : 'button';
</script>

<svelte:element
    this={tag}
    class="public-button public-button--{variant}"
    {href}
    {rel}
>
    <slot />
</svelte:element>

<style>
    .public-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: .5rem;
        min-height: 2.75rem;
        padding: .625rem .9375rem;
        border-radius: .75rem;
        border: 1px solid transparent;
        font-weight: 800;
        font: inherit;
        font-weight: 800;
        cursor: pointer;
        text-decoration: none;
        transition: background .16s, border-color .16s, transform .16s;
    }

    .public-button--primary {
        background: #275fd6;
        color: #fff;
    }

    .public-button--primary:hover {
        background: #1d4fb9;
        transform: translateY(-1px);
    }

    .public-button--secondary {
        background: #fff;
        border-color: #dde3eb;
        color: #1c2534;
    }

    .public-button--secondary:hover {
        border-color: #c7d1df;
    }

    .public-button:focus-visible {
        outline: 3px solid rgb(128 170 255 / 50%);
        outline-offset: 2px;
    }

    .public-button :global(svg) {
        width: 1.125rem;
        height: 1.125rem;
        stroke: currentColor;
        fill: none;
        stroke-width: 2;
        stroke-linecap: round;
        stroke-linejoin: round;
    }
</style>