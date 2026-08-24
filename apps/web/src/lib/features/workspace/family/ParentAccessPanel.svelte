<script lang="ts">
    import ParentInvitationFlow from '../access/ParentInvitationFlow.svelte';
    export let open = true;
    export let embedded = false;
    export let compact = false;
</script>

{#if open}<div class:embedded class:compact class="panel"><ParentInvitationFlow hideTitle={embedded} {compact} /></div>{/if}

<style>
    .panel{display:grid;padding:1rem;border:1px solid #e1e6ef;border-radius:1rem;background:#fff;box-shadow:0 .8rem 2rem rgb(27 39 73 / 8%)}
    .panel.embedded{padding:0;border:0;border-radius:0;box-shadow:none}
    @media(max-width:520px){.panel{padding:.75rem;box-shadow:none}}
</style>
