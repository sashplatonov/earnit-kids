<script lang="ts">
    import { telegramIconMap, type TelegramIconName } from './telegramIconMap';

    export let name: TelegramIconName;
    export let size = 20;
    export let strokeWidth = 2;
    export let label: string | undefined = undefined;

    $: Icon = telegramIconMap[name];
</script>

<Icon {size} {strokeWidth} aria-hidden={label ? undefined : 'true'} aria-label={label} />
