<script lang="ts">
    import ParentInvitationFlow from '../access/ParentInvitationFlow.svelte';
    interface Props {
        open?: boolean;
        embedded?: boolean;
        compact?: boolean;
        hideTitle?: boolean | undefined;
    }

    let {
        open = true,
        embedded = false,
        compact = false,
        hideTitle = undefined
    }: Props = $props();
</script>

{#if open}<div class:embedded class:compact class="panel"><ParentInvitationFlow hideTitle={hideTitle ?? embedded} {compact} /></div>{/if}

<style>
    .panel{display:grid;padding:1rem;border:1px solid #e1e6ef;border-radius:1rem;background:#fff;box-shadow:0 .8rem 2rem rgb(27 39 73 / 8%)}
    .panel.embedded{padding:0;border:0;border-radius:0;box-shadow:none}
    @media(max-width:520px){.panel{padding:.75rem;box-shadow:none}}
</style>
