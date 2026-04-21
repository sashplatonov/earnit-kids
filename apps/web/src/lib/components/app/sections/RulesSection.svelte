<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tAdmin(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.${key}` as MessageKey, variables);
    }

    $: isAdmin = $appStore.isAdmin;
    $: rules = $appStore.rules ?? '';

    let editing = false;
    let draftRules = '';

    function startEdit() {
        draftRules = rules;
        editing = true;
    }

    async function saveRules() {
        appStore.setState({ rules: draftRules.trim() || null });
        await scheduleSave();
        showToast(tAdmin('rules.savedToast'), 'success');
        editing = false;
    }
</script>

<section class="section" id="rules-section">
    <div class="section__header">
        <h2>{tAdmin('rules.title')}</h2>
        {#if isAdmin}
        <div class="section__buttons admin-only">
            {#if !editing}
            <button class="btn btn--secondary" id="edit-rules-btn" on:click={startEdit}>{tAdmin('rules.edit')}</button>
            {:else}
            <button class="btn btn--primary btn--small" on:click={saveRules}>{tAdmin('rules.save')}</button>
            <button class="btn btn--ghost btn--small" on:click={() => editing = false}>{tAdmin('rules.cancel')}</button>
            {/if}
        </div>
        {/if}
    </div>

    {#if editing}
    <textarea class="input rules-editor" rows="8" bind:value={draftRules}
        placeholder={tAdmin('rules.placeholder')}></textarea>
    {:else}
    <div class="rules-content" id="rules-display">
        {#if rules}
        <p class="rules-content__text">{rules}</p>
        {:else}
        <p class="hint rules-content__hint">{tAdmin('rules.empty')}</p>
        {/if}
    </div>
    {/if}
</section>

<style>
    .rules-editor {
        width: 100%;
        margin-top: 1rem;
    }

    .rules-content__text {
        white-space: pre-wrap;
    }

    .rules-content__hint {
        margin-top: 1rem;
    }
</style>
