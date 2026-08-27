<script lang="ts">
    import LiveCoinShopDemo from '$lib/features/live-demo/LiveCoinShopDemo.svelte';
    import type { PageData } from './$types';

    interface Props {
        data: PageData;
    }

    let { data }: Props = $props();
</script>

<svelte:head>
    <meta name="robots" content="noindex" />
</svelte:head>

<LiveCoinShopDemo publicOrigin={data.publicOrigin} />
