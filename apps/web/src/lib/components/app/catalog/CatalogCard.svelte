<script lang="ts">
    export let kind: 'task' | 'shop';
    export let view: 'grid' | 'list' = 'list';
    export let disabled = false;
    export let selected = false;
    export let additionalClass = '';

    $: classes = [
        'card',
        `card--${kind}`,
        `${kind}-card`,
        view === 'list' ? `${kind}-card--list` : '',
        disabled ? 'card--disabled' : '',
        selected ? `${kind}-card--selected` : '',
        additionalClass,
    ].filter(Boolean).join(' ');
</script>

<slot {classes} />
