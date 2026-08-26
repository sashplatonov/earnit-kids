<script lang="ts">
    import LiveCoinShopDemo from '$lib/features/live-demo/LiveCoinShopDemo.svelte';
    import type { PageData } from './$types';

    export let data: PageData;
</script>

<svelte:head>
    <meta name="robots" content="noindex" />
</svelte:head>

<LiveCoinShopDemo publicOrigin={data.publicOrigin} />
