<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { useI18n } from '$lib/i18n/context';
    import { importTasks } from '$lib/services/api';
    import { importShopItems } from '$lib/telegram/services/shopApi';
    import { applyDataSnapshot } from '$lib/services/bootstrap';
    import {
        CSV_IMPORT_SCHEMAS,
        buildCsvTemplate,
        parseCsvImport,
        type CsvImportKind,
        type CsvImportValidationError,
    } from '$lib/telegram/services/csvImport';
    import TelegramIcon from './TelegramIcon.svelte';

    export let open = false;
    export let onClose: () => void = () => {};

    const i18n = useI18n();

    type Screen = 'pick' | 'tasks' | 'shop';

    let screen: Screen = 'pick';
    let csvText = '';
    let showFormat = false;
    let showErrors = false;
    let copied = false;
    let busy = false;
    let error = '';
    let serverErrors: CsvImportValidationError[] = [];
    let imported = false;
    let fileInput: HTMLInputElement | undefined = undefined;

    $: if (open) {
        screen = 'pick';
        csvText = '';
        showFormat = false;
        showErrors = false;
        copied = false;
        busy = false;
        error = '';
        serverErrors = [];
        imported = false;
    }

    $: parsed = screen !== 'pick' && csvText.trim()
        ? parseCsvImport(screen as CsvImportKind, csvText)
        : null;

    $: readyCount = parsed ? parsed.normalizedRows.length : 0;
    $: validRowCount = parsed ? parsed.rows.filter((row) => row.errors.length === 0).length : 0;
    $: invalidRowCount = parsed ? parsed.rows.length - validRowCount : 0;
    $: headerIssueCount = parsed ? parsed.errors.filter((e) => e.row === 0).length : 0;
    $: errorCount = invalidRowCount + headerIssueCount;
    $: allErrors = dedupeErrors([...serverErrors, ...(parsed?.errors ?? [])]);

    function dedupeErrors(errors: CsvImportValidationError[]): CsvImportValidationError[] {
        const seen: Record<string, boolean> = {};
        const result: CsvImportValidationError[] = [];
        for (const item of errors) {
            const key = `${item.row}-${item.field}-${item.message}`;
            if (!seen[key]) {
                seen[key] = true;
                result.push(item);
            }
        }
        return result;
    }

    function translateError(message: string): string {
        if (message === 'CSV is empty') return $i18n.t('app.telegram.import.errorCsvEmpty');
        if (message.startsWith('Missing required column:')) {
            return $i18n.t('app.telegram.import.errorMissingColumn', {
                field: message.replace('Missing required column:', '').trim(),
            });
        }
        switch (message) {
            case 'title is required': return $i18n.t('app.telegram.import.errorTitleRequired');
            case 'name is required': return $i18n.t('app.telegram.import.errorNameRequired');
            case 'coins must be positive': return $i18n.t('app.telegram.import.errorCoinsPositive');
            case 'price must be positive': return $i18n.t('app.telegram.import.errorPricePositive');
            case 'frequencyLimit must be positive': return $i18n.t('app.telegram.import.errorFrequencyPositive');
            case 'moneyLimit must not be negative': return $i18n.t('app.telegram.import.errorMoneyNegative');
            case 'duplicate title': return $i18n.t('app.telegram.import.errorDuplicateTitle');
            case 'duplicate name': return $i18n.t('app.telegram.import.errorDuplicateName');
            default: return message;
        }
    }

    const FIELD_KEYS: Record<string, string> = {
        title: 'app.telegram.import.fieldTitle',
        coins: 'app.telegram.import.fieldCoins',
        name: 'app.telegram.import.fieldName',
        price: 'app.telegram.import.fieldPrice',
        groupName: 'app.telegram.import.fieldGroupName',
        comment: 'app.telegram.import.fieldComment',
        frequencyLimit: 'app.telegram.import.fieldFrequencyLimit',
        frequencyPeriod: 'app.telegram.import.fieldFrequencyPeriod',
        moneyLimit: 'app.telegram.import.fieldMoneyLimit',
        icon: 'app.telegram.import.fieldIcon',
        isActive: 'app.telegram.import.fieldIsActive',
        type: 'app.telegram.import.fieldType',
    };

    function formatTitle(kind: CsvImportKind): string {
        return kind === 'tasks'
            ? $i18n.t('app.telegram.import.taskFormatTitle')
            : $i18n.t('app.telegram.import.rewardFormatTitle');
    }

    function buildFormatDescription(kind: CsvImportKind): string {
        const schema = CSV_IMPORT_SCHEMAS[kind];
        const lines: string[] = [];
        lines.push(formatTitle(kind));
        lines.push('');
        lines.push($i18n.t('app.telegram.import.acceptedFields'));
        lines.push('');
        for (const column of schema.columns) {
            const flag = column.required
                ? $i18n.t('app.telegram.import.required')
                : $i18n.t('app.telegram.import.optional');
            const descKey = FIELD_KEYS[column.key] as
                | 'app.telegram.import.fieldTitle'
                | 'app.telegram.import.fieldCoins'
                | 'app.telegram.import.fieldName'
                | 'app.telegram.import.fieldPrice'
                | 'app.telegram.import.fieldGroupName'
                | 'app.telegram.import.fieldComment'
                | 'app.telegram.import.fieldFrequencyLimit'
                | 'app.telegram.import.fieldFrequencyPeriod'
                | 'app.telegram.import.fieldMoneyLimit'
                | 'app.telegram.import.fieldIcon'
                | 'app.telegram.import.fieldIsActive'
                | 'app.telegram.import.fieldType'
                | undefined;
            const desc = descKey ? $i18n.t(descKey) : '';
            lines.push(`${column.key} - ${flag}${desc ? `, ${desc}` : ''}`);
        }
        lines.push('');
        lines.push($i18n.t('app.telegram.import.example'));
        lines.push(buildCsvTemplate(kind));
        lines.push('');
        lines.push($i18n.t('app.telegram.import.escapeRule'));
        return lines.join('\n');
    }

    async function copyFormat() {
        if (screen === 'pick') return;
        try {
            await navigator.clipboard.writeText(buildFormatDescription(screen as CsvImportKind));
            copied = true;
            setTimeout(() => { copied = false; }, 1600);
        } catch {
            error = $i18n.t('app.telegram.import.importError');
        }
    }

    function onFileChange(event: Event) {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => {
            csvText = String(reader.result ?? '');
            showErrors = false;
        };
        reader.readAsText(file);
        input.value = '';
    }

    function pickFile() {
        fileInput?.click();
    }

    async function runImport() {
        if (!parsed || readyCount === 0) return;
        const childId = $appStore.currentChildId ?? $appStore.children[0]?.id ?? null;
        if (childId == null) {
            error = $i18n.t('app.telegram.family.chooseChildFirst');
            return;
        }
        busy = true;
        error = '';
        serverErrors = [];
        const importFn = screen === 'tasks' ? importTasks : importShopItems;
        const result = await importFn({ childId, rows: parsed.normalizedRows });
        busy = false;
        if (result.ok) {
            imported = true;
            if (result.data && typeof result.data === 'object') {
                applyDataSnapshot(result.data as Record<string, unknown>);
            }
        } else {
            error = $i18n.t('app.telegram.import.serverError');
            serverErrors = result.validationErrors ?? [];
        }
    }
</script>

{#if open}
    <div class="sheet-backdrop" role="presentation" on:click={onClose}></div>
    <div class="sheet" role="dialog" aria-modal="true" aria-labelledby="import-title" tabindex="-1">
        <h2 id="import-title">{$i18n.t('app.telegram.import.title')}</h2>

        {#if screen === 'pick'}
            <h3 class="sheet-subtitle">{$i18n.t('app.telegram.import.whatToImport')}</h3>
            <div class="mode-list">
                <button class="mode" type="button" on:click={() => screen = 'tasks'}><span class="setting-icon"><TelegramIcon name="task" size={20} label={$i18n.t('app.telegram.import.kindTasks')} /></span><span class="grow">{$i18n.t('app.telegram.import.kindTasks')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.import.kindTasks')} /></button>
                <button class="mode" type="button" on:click={() => screen = 'shop'}><span class="setting-icon"><TelegramIcon name="reward" size={20} label={$i18n.t('app.telegram.import.kindRewards')} /></span><span class="grow">{$i18n.t('app.telegram.import.kindRewards')}</span><TelegramIcon name="arrowRight" size={18} label={$i18n.t('app.telegram.import.kindRewards')} /></button>
            </div>
            <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.import.cancel')}</button>
        {:else}
            <h3 class="sheet-subtitle">
                {screen === 'tasks'
                    ? $i18n.t('app.telegram.import.taskTitle')
                    : $i18n.t('app.telegram.import.rewardTitle')}
            </h3>

            <div class="file-row">
                <button class="secondary" type="button" on:click={pickFile}><TelegramIcon name="upload" size={18} label={$i18n.t('app.telegram.import.chooseCsv')} />{$i18n.t('app.telegram.import.chooseCsv')}</button>
                <span class="muted">{$i18n.t('app.telegram.import.orPaste')}</span>
            </div>
            <input
                bind:this={fileInput}
                class="hidden-file"
                type="file"
                accept=".csv,text/csv,text/plain"
                on:change={onFileChange}
                aria-label={$i18n.t('app.telegram.import.chooseCsv')}
            />
            <textarea
                class="csv-input"
                bind:value={csvText}
                rows="4"
                placeholder={$i18n.t('app.telegram.import.pastePlaceholder')}
                on:input={() => { showErrors = false; }}
            ></textarea>

            {#if csvText.trim() && parsed}
                <p class="field-label">{$i18n.t('app.telegram.import.formatTitle')}</p>
                <div class="format-row">
                    <button class="linkish" type="button" on:click={() => showFormat = !showFormat}>{showFormat ? '−' : '+'} {$i18n.t('app.telegram.import.viewFormat')}</button>
                    <button class="linkish" type="button" on:click={copyFormat}><TelegramIcon name="copy" size={16} label={$i18n.t('app.telegram.import.copyFormat')} />{$i18n.t('app.telegram.import.copyFormat')}</button>
                </div>
                {#if copied}
                    <p class="copied" role="status">{$i18n.t('app.telegram.import.copied')}</p>
                {/if}
                {#if showFormat}
                    <pre class="format-view">{buildFormatDescription(parsed.kind)}</pre>
                {/if}

                <p class="field-label">{$i18n.t('app.telegram.import.validation')}</p>
                <div class="validation-row">
                    <span class="ready-badge">{$i18n.t('app.telegram.import.readyCount', { ready: readyCount })}</span>
                    <span class="error-badge">{$i18n.t('app.telegram.import.errorCount', { errors: errorCount })}</span>
                    {#if errorCount > 0}
                        <button class="linkish" type="button" on:click={() => showErrors = !showErrors}>{$i18n.t('app.telegram.import.viewErrors')}</button>
                    {/if}
                </div>
                {#if showErrors && allErrors.length > 0}
                    <div class="errors-block">
                        <p class="errors-title">{$i18n.t('app.telegram.import.errorsTitle')}</p>
                        {#each allErrors as item (item.row + '-' + item.field + '-' + item.message)}
                            <p class="error-line">Строка {item.row}: {item.field} — {translateError(item.message)}</p>
                        {/each}
                    </div>
                {/if}
            {/if}

            {#if !csvText.trim()}
                <p class="muted empty-hint">{$i18n.t('app.telegram.import.noCsv')}</p>
            {/if}

            {#if error}<p class="error" role="alert">{error}</p>{/if}
            {#if imported}<p class="saved" role="status">{$i18n.t('app.telegram.import.importSuccess', { count: readyCount })}</p>{/if}

            <button class="primary" type="button" disabled={busy || readyCount === 0} on:click={runImport}>
                {busy
                    ? $i18n.t('app.telegram.import.importing')
                    : $i18n.t('app.telegram.import.importButton', { count: readyCount })}
            </button>
            <div class="button-row">
                <button class="close" type="button" on:click={() => screen = 'pick'}>{$i18n.t('app.telegram.import.back')}</button>
                <button class="close" type="button" on:click={onClose}>{$i18n.t('app.telegram.import.close')}</button>
            </div>
        {/if}
    </div>
{/if}

<style>
    .sheet-backdrop { position:fixed; inset:0; z-index:40; background:rgb(15 24 45 / 35%); }
    .sheet { position:fixed; inset:auto 0 0; z-index:41; max-height:84vh; overflow-y:auto; padding:1rem max(1rem, env(safe-area-inset-left)) calc(1rem + env(safe-area-inset-bottom)); border-radius:1.1rem 1.1rem 0 0; background:#fff; box-shadow:0 -1rem 3rem rgb(27 39 73 / 18%); }
    @media (min-width: 700px) { .sheet { inset:50% auto auto 50%; width:min(42rem,calc(100% - 3rem)); max-height:min(82dvh,46rem); padding:1.4rem; border-radius:1.25rem; box-shadow:0 1.5rem 4rem rgb(27 39 73 / 22%); transform:translate(-50%,-50%); } }
    h2 { margin:0 0 .75rem; color:#18243d; font-size:1.15rem; }
    .sheet-subtitle { margin:0 0 .5rem; color:#4d5870; font-size:.85rem; }
    .mode-list { display:grid; grid-template-columns:minmax(0,1fr); gap:.5rem; }
    .mode { display:flex; align-items:center; gap:.6rem; width:100%; min-height:3rem; padding:.35rem .6rem; border:1px solid #e6e9f0; border-radius:.8rem; background:#fff; font:inherit; color:#18243d; cursor:pointer; }
    .mode:active { background:#f4f6fb; }
    .grow { flex:1; min-width:0; font-weight:600; text-align:left; }
    .setting-icon { display:grid; place-items:center; width:2.1rem; height:2.1rem; flex:0 0 auto; border-radius:.6rem; background:#eef0ff; color:#5b63e9; }
    .file-row { display:flex; align-items:center; gap:.6rem; margin:.4rem 0; }
    .secondary { display:inline-flex; align-items:center; gap:.4rem; min-height:2.6rem; padding:0 .9rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; font-weight:600; cursor:pointer; }
    .hidden-file { position:absolute; width:1px; height:1px; opacity:0; overflow:hidden; }
    .csv-input { width:100%; box-sizing:border-box; min-height:4.5rem; padding:.6rem; border:1px solid #cfd6e4; border-radius:.7rem; background:#fff; color:#18243d; font:inherit; font-size:.85rem; resize:vertical; }
    .field-label { margin:.7rem 0 .3rem; color:#4d5870; font-size:.8rem; font-weight:600; }
    .format-row { display:flex; align-items:center; gap:.9rem; flex-wrap:wrap; }
    .linkish { display:inline-flex; align-items:center; gap:.35rem; padding:0; border:0; background:none; color:#3867d6; font:inherit; font-size:.85rem; font-weight:600; cursor:pointer; }
    .copied { margin:.35rem 0 0; color:#17884b; font-size:.82rem; }
    .format-view { margin:.4rem 0 0; padding:.6rem; border:1px solid #e6e9f0; border-radius:.7rem; background:#f8f9fc; color:#33415f; font-family:ui-monospace, SFMono-Regular, Menlo, monospace; font-size:.75rem; white-space:pre-wrap; word-break:break-word; }
    .validation-row { display:flex; align-items:center; gap:.5rem; flex-wrap:wrap; }
    .ready-badge { padding:.2rem .55rem; border-radius:999px; background:#eaf7ef; color:#17884b; font-size:.78rem; font-weight:700; }
    .error-badge { padding:.2rem .55rem; border-radius:999px; background:#fdecec; color:#a33b3b; font-size:.78rem; font-weight:700; }
    .errors-block { margin-top:.4rem; padding:.6rem; border:1px solid #f0d8d8; border-radius:.7rem; background:#fdf6f6; }
    .errors-title { margin:0 0 .3rem; color:#a33b3b; font-size:.8rem; font-weight:700; }
    .error-line { margin:.15rem 0; color:#a33b3b; font-size:.8rem; }
    .empty-hint { margin-top:.5rem; }
    .muted { color:#66718a; font-size:.85rem; }
    .error { margin:.6rem 0 0; color:#a33b3b; }
    .saved { margin:.6rem 0 0; color:#17884b; }
    .primary { width:100%; min-height:2.75rem; margin-top:.9rem; border:0; border-radius:.7rem; background:#3867d6; color:#fff; font:inherit; font-weight:700; cursor:pointer; }
    .primary:disabled { cursor:not-allowed; opacity:.55; }
    .button-row { display:grid; grid-template-columns:1fr 1fr; gap:.5rem; margin-top:.5rem; }
    .close { min-height:2.75rem; border:1px solid #dfe4ee; border-radius:.7rem; background:#fff; color:#33415f; font:inherit; cursor:pointer; }
</style>
