<script lang="ts">
    import { run } from 'svelte/legacy';

    import '../app.css';
    import { FALLBACK_I18N_PAYLOAD, type I18nPayload } from '$lib/i18n';
    import { provideI18n, updateI18n } from '$lib/i18n/context';
    import { registerServiceWorker } from '$lib/features/workspace/pwa/registerServiceWorker';
    import WorkspacePwaUpdate from '$lib/features/workspace/pwa/WorkspacePwaUpdate.svelte';

    interface Props {
        data: { i18n?: I18nPayload } & Record<string, unknown>;
        children?: import('svelte').Snippet;
    }

    let { data, children }: Props = $props();

    const i18n = provideI18n(FALLBACK_I18N_PAYLOAD);

    run(() => {
        if (data.i18n) {
            updateI18n(i18n, data.i18n);
        }
    });
    registerServiceWorker();
</script>

{@render children?.()}
<WorkspacePwaUpdate />
