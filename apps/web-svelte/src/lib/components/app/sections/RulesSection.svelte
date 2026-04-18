<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminSaveRules } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    $: isAdmin = $appStore.isAdmin;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    $: rules = ($appStore as any).rules as string ?? '';

    let editing = false;
    let draftRules = '';

    function startEdit() {
        draftRules = rules;
        editing = true;
    }

    async function saveRules() {
        const ok = await adminSaveRules(draftRules);
        if (ok) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            appStore.setState({ rules: draftRules } as any);
            showToast('Правила сохранены', 'success');
            editing = false;
        }
    }
</script>

<section class="section hidden" id="rules-section">
    <div class="section__header">
        <h2>Правила и цели</h2>
        {#if isAdmin}
        <div class="section__buttons admin-only">
            {#if !editing}
            <button class="btn btn--secondary" id="edit-rules-btn" on:click={startEdit}>Редактировать</button>
            {:else}
            <button class="btn btn--primary btn--small" on:click={saveRules}>Сохранить</button>
            <button class="btn btn--ghost btn--small" on:click={() => editing = false}>Отмена</button>
            {/if}
        </div>
        {/if}
    </div>

    {#if editing}
    <textarea class="input" rows="8" style="width:100%; margin-top:1rem;" bind:value={draftRules}
        placeholder="Напишите правила и цели для ребенка..."></textarea>
    {:else}
    <div class="rules-content" id="rules-display">
        {#if rules}
        <p style="white-space: pre-wrap;">{rules}</p>
        {:else}
        <p class="hint" style="margin-top:1rem;">Правила ещё не добавлены.</p>
        {/if}
    </div>
    {/if}
</section>
