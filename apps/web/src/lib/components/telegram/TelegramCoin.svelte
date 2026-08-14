<script lang="ts">
    export let size = 18;
    export let label: string | undefined = undefined;
</script>

<svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    role="img"
    aria-hidden={label ? undefined : 'true'}
    aria-label={label}
    fill="none"
>
    <circle cx="12" cy="12" r="10" fill="#f2b84b" stroke="#e0a328" stroke-width="1.6" />
    <circle cx="12" cy="12" r="4.4" fill="none" stroke="#b07f1a" stroke-width="1.5" opacity="0.55" />
</svg>
