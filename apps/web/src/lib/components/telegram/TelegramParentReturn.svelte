<script lang="ts">
    import TelegramIcon from './TelegramIcon.svelte';
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();

    interface Props {
        href?: string | null;
        onClick?: (() => void) | null;
    }

    let { href = null, onClick = null }: Props = $props();
</script>

{#if href}
    <a class="parent-return" href={href} aria-label={$i18n.t('app.telegram.parentReturn')}>
        <TelegramIcon name="back" size={16} label={$i18n.t('app.telegram.parentReturn')} />
        <span>{$i18n.t('app.telegram.parentReturn')}</span>
    </a>
{:else if onClick}
    <button class="parent-return" type="button" onclick={onClick} aria-label={$i18n.t('app.telegram.parentReturn')}>
        <TelegramIcon name="back" size={16} label={$i18n.t('app.telegram.parentReturn')} />
        <span>{$i18n.t('app.telegram.parentReturn')}</span>
    </button>
{/if}

<style>
    .parent-return { display:inline-flex; align-items:center; gap:.35rem; min-height:2.75rem; margin:0 0 .5rem; padding:.3rem .6rem; border:1px solid #dfe4ee; border-radius:.6rem; background:#fff; color:#3867d6; font:inherit; font-weight:700; text-decoration:none; cursor:pointer; }
    .parent-return:hover { background:#f2f5ff; }
    .parent-return:focus-visible { outline:3px solid #80aaff; outline-offset:2px; }
</style>
