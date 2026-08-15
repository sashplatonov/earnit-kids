<script lang="ts">
    import { useI18n } from '$lib/i18n/context';

    const i18n = useI18n();
</script>

<footer class="public-site-footer">
    <div class="public-footer-row">
        <div class="public-footer-brand">
            <span class="public-site-brand-dot" aria-hidden="true"></span>
            {$i18n.t('common.brand.name')}
        </div>
        <div>{$i18n.t('public.shell.footerTagline')}</div>
    </div>
</footer>

<style>
    .public-site-footer {
        padding: 1.875rem 0 2.5rem;
        color: #687386;
        font-size: .75rem;
    }

    .public-footer-row {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        align-items: center;
        width: min(calc(100% - 2rem), 70rem);
        margin: 0 auto;
        border-top: 1px solid #dde3eb;
        padding-top: 1.25rem;
    }

    .public-footer-brand {
        display: flex;
        align-items: center;
        gap: .5rem;
        color: #1c2534;
        font-weight: 800;
    }

    .public-footer-brand :global(.public-site-brand-dot) {
        width: .5rem;
        height: .5rem;
        border-radius: 50%;
        background: #275fd6;
    }

    @media (max-width: 640px) {
        .public-footer-row {
            flex-direction: column;
            align-items: flex-start;
            gap: .4375rem;
            padding-top: 1rem;
        }
    }
</style>