<script lang="ts">
    import { telegramIconMap, type TelegramIconName } from './telegramIconMap';

    interface Props {
        name: TelegramIconName;
        size?: number;
        strokeWidth?: number;
        label?: string | undefined;
    }

    let {
        name,
        size = 20,
        strokeWidth = 2,
        label = undefined
    }: Props = $props();

    let Icon = $derived(telegramIconMap[name]);
</script>

<Icon {size} {strokeWidth} aria-hidden={label ? undefined : 'true'} aria-label={label} />
