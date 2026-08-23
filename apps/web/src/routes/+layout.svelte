<script lang="ts">
    import '../app.css';
    import { FALLBACK_I18N_PAYLOAD, type I18nPayload } from '$lib/i18n';
    import { provideI18n, updateI18n } from '$lib/i18n/context';
    import { registerServiceWorker } from '$lib/features/workspace/pwa/registerServiceWorker';
    import WorkspacePwaUpdate from '$lib/features/workspace/pwa/WorkspacePwaUpdate.svelte';

    export let data: { i18n?: I18nPayload } & Record<string, unknown>;

    const i18n = provideI18n(FALLBACK_I18N_PAYLOAD);

    $: if (data.i18n) {
        updateI18n(i18n, data.i18n);
    }
    registerServiceWorker();
</script>

<slot />
<WorkspacePwaUpdate />
