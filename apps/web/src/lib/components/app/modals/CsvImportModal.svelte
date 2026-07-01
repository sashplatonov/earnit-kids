<script lang="ts">
    import { browser } from '$app/environment';
    import { modalStore } from '$lib/stores/modal';
    import {
        CSV_IMPORT_SCHEMAS,
        buildCsvTemplate,
        parseCsvImport,
        type CsvImportKind,
        type CsvImportValidationError,
    } from '$lib/services/csvImport';
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';

    type CsvImportSubmitResult = {
        ok: true;
        data?: unknown;
    } | {
        ok: false;
        error: string;
        validationErrors?: CsvImportValidationError[];
    };

    type CsvImportModalData = {
        kind?: CsvImportKind;
        onSubmit?: (payload: { kind: CsvImportKind; rows: Array<Record<string, unknown>> }) => Promise<CsvImportSubmitResult> | CsvImportSubmitResult;
        onCancel?: () => void;
    };

    const i18n = useI18n();

    $: isOpen = $modalStore.open === 'csv-import-modal';
    $: modalData = (($modalStore.data ?? {}) as CsvImportModalData);

    let kind: CsvImportKind = 'tasks';
    let sourceText = '';
    let serverError = '';
    let helperMessage = '';
    let serverValidationErrors: CsvImportValidationError[] = [];
    let pending = false;
    let wasOpen = false;
    let showFormat = false;

    $: if (isOpen && !wasOpen) {
        kind = modalData.kind ?? 'tasks';
        sourceText = '';
        serverError = '';
        helperMessage = '';
        serverValidationErrors = [];
        pending = false;
        showFormat = false;
        wasOpen = true;
        void wasOpen;
    }
    $: if (!isOpen && wasOpen) {
        sourceText = '';
        serverError = '';
        helperMessage = '';
        serverValidationErrors = [];
        pending = false;
        showFormat = false;
        wasOpen = false;
        void wasOpen;
    }

    $: schema = CSV_IMPORT_SCHEMAS[kind];
    $: parsed = parseCsvImport(kind, sourceText);
    $: templateText = buildCsvTemplate(kind);
    $: hasBlockingErrors = parsed.errors.length > 0 || parsed.rows.some((row) => row.errors.length > 0);
    $: rowCount = parsed.normalizedRows.length;

    function t(kindKey: CsvImportKind, key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`${kindKey}.${key}` as MessageKey, variables);
    }

    function close() {
        modalStore.close();
    }

    function cancel() {
        modalData.onCancel?.();
        close();
    }

    async function pasteFromClipboard() {
        if (!browser || !navigator.clipboard?.readText) {
            serverError = t(kind, 'import.clipboardUnavailable');
            return;
        }

        try {
            sourceText = await navigator.clipboard.readText();
            serverError = '';
            helperMessage = '';
            serverValidationErrors = [];
        } catch {
            serverError = t(kind, 'import.clipboardFailed');
        }
    }

    async function copyTemplate() {
        if (!browser || !navigator.clipboard?.writeText) {
            serverError = t(kind, 'import.clipboardUnavailable');
            helperMessage = '';
            return;
        }

        try {
            await navigator.clipboard.writeText(templateText);
            serverError = '';
            helperMessage = t(kind, 'import.copySuccess');
        } catch {
            serverError = t(kind, 'import.clipboardFailed');
            helperMessage = '';
        }
    }

    function clearText() {
        sourceText = '';
        serverError = '';
        helperMessage = '';
        serverValidationErrors = [];
    }

    async function submit() {
        if (pending || hasBlockingErrors || rowCount === 0) {
            return;
        }

        pending = true;
        serverError = '';
        helperMessage = '';
        serverValidationErrors = [];

        try {
            const result = await modalData.onSubmit?.({
                kind,
                rows: parsed.normalizedRows,
            });

            if (result && !result.ok) {
                serverError = result.error;
                serverValidationErrors = result.validationErrors ?? [];
                return;
            }

            close();
        } finally {
            pending = false;
        }
    }

    function handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Escape' && !pending) {
            cancel();
        }
    }

    function switchKind(nextKind: CsvImportKind) {
        if (kind === nextKind) {
            return;
        }

        kind = nextKind;
        serverError = '';
        serverValidationErrors = [];
    }

    function allErrors(): CsvImportValidationError[] {
        const local = [
            ...parsed.errors,
            ...parsed.rows.flatMap((row) => row.errors),
            ...serverValidationErrors,
        ];
        return local;
    }

    function previewValue(row: { values: Record<string, string> }, columnKey: string): string {
        return row.values[columnKey] ?? row.values[columnKey.trim().toLowerCase()] ?? '';
    }

    function enumFieldHints(activeKind: CsvImportKind): Array<{ key: string; values: string }> {
        if (activeKind === 'tasks') {
            return [
                { key: 'frequencyPeriod', values: 'day, week, month, year, season' },
                { key: 'isActive', values: 'true, false, 1, 0, yes, no, да, нет' },
            ];
        }

        return [
            { key: 'frequencyPeriod', values: 'day, week, month, year, season' },
            { key: 'type', values: 'micro, small, large' },
            { key: 'isActive', values: 'true, false, 1, 0, yes, no, да, нет' },
        ];
    }
</script>

{#if isOpen}
<dialog class="modal csv-import-modal" aria-modal="true" id="csv-import-modal" open on:keydown={handleKeydown}>
    <div class="modal__content csv-import-modal__content">
        <!-- CSV illustration -->
        <div class="csv-import-modal__illustration" aria-hidden="true">
            <svg viewBox="0 0 120 64" fill="none" xmlns="http://www.w3.org/2000/svg">
                <!-- Document body -->
                <rect x="2" y="2" width="116" height="60" rx="6" fill="rgba(87,121,206,0.08)" stroke="rgba(87,121,206,0.2)" stroke-width="1.5"/>
                <!-- Title bar -->
                <rect x="2" y="2" width="116" height="16" rx="6" fill="rgba(87,121,206,0.14)"/>
                <rect x="2" y="12" width="116" height="6" fill="rgba(87,121,206,0.14)"/>
                <!-- CSV icon badge -->
                <rect x="10" y="4" width="28" height="11" rx="4" fill="rgba(87,121,206,0.3)"/>
                <text x="24" y="12" text-anchor="middle" font-size="7" font-weight="700" fill="rgba(87,121,206,0.9)" font-family="ui-monospace,monospace">CSV</text>
                <!-- Column headers -->
                <rect x="10" y="22" width="24" height="5" rx="2" fill="rgba(87,121,206,0.18)"/>
                <rect x="37" y="22" width="18" height="5" rx="2" fill="rgba(87,121,206,0.12)"/>
                <rect x="58" y="22" width="28" height="5" rx="2" fill="rgba(87,121,206,0.1)"/>
                <rect x="89" y="22" width="20" height="5" rx="2" fill="rgba(120,140,175,0.08)"/>
                <!-- Row 1 -->
                <rect x="10" y="31" width="24" height="4" rx="2" fill="rgba(120,140,175,0.1)"/>
                <rect x="37" y="31" width="18" height="4" rx="2" fill="rgba(120,140,175,0.08)"/>
                <rect x="58" y="31" width="28" height="4" rx="2" fill="rgba(120,140,175,0.06)"/>
                <rect x="89" y="31" width="20" height="4" rx="2" fill="rgba(120,140,175,0.04)"/>
                <!-- Row 2 -->
                <rect x="10" y="39" width="24" height="4" rx="2" fill="rgba(120,140,175,0.08)"/>
                <rect x="37" y="39" width="18" height="4" rx="2" fill="rgba(120,140,175,0.06)"/>
                <rect x="58" y="39" width="28" height="4" rx="2" fill="rgba(120,140,175,0.04)"/>
                <rect x="89" y="39" width="20" height="4" rx="2" fill="rgba(120,140,175,0.03)"/>
                <!-- Row 3 -->
                <rect x="10" y="47" width="24" height="4" rx="2" fill="rgba(120,140,175,0.06)"/>
                <rect x="37" y="47" width="18" height="4" rx="2" fill="rgba(120,140,175,0.04)"/>
                <rect x="58" y="47" width="28" height="4" rx="2" fill="rgba(120,140,175,0.03)"/>
                <!-- Arrow flowing in -->
                <path d="M2 14C-4 14 -4 20 2 20H8L14 17L14 23" stroke="rgba(87,121,206,0.35)" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
            </svg>
        </div>

        <!-- Header: title + kind switches -->
        <div class="csv-import-modal__header">
            <h3>{t(kind, 'import.title')}</h3>
            <div class="csv-import-modal__switches" role="tablist" aria-label={t(kind, 'import.kindLabel')}>
                <button type="button" class:csv-import-modal__switch--active={kind === 'tasks'} class="csv-import-modal__switch" role="tab" aria-selected={kind === 'tasks'} on:click={() => switchKind('tasks')}>
                    {t('tasks', 'import.kindTasks')}
                </button>
                <button type="button" class:csv-import-modal__switch--active={kind === 'shop'} class="csv-import-modal__switch" role="tab" aria-selected={kind === 'shop'} on:click={() => switchKind('shop')}>
                    {t('shop', 'import.kindShop')}
                </button>
            </div>
        </div>

        <div class="csv-import-modal__body">
            <p class="csv-import-modal__description">{t(kind, 'import.description')}</p>

            <!-- Collapsible format reference -->
            <details class="csv-import-modal__format" bind:open={showFormat}>
                <summary class="csv-import-modal__format-summary">
                    <span>{t(kind, 'import.formatTitle')}</span>
                    <span class="csv-import-modal__chips-inline">
                        {#each schema.columns as column (column.key)}
                            <span class="csv-import-modal__chip" class:csv-import-modal__chip--required={column.required}>{column.label}</span>
                        {/each}
                    </span>
                </summary>
                <pre class="csv-import-modal__format-code">{templateText}</pre>
                <div class="csv-import-modal__enum-hints">
                    <div class="csv-import-modal__meta-label">{t(kind, 'import.enumHintsTitle')}</div>
                    <ul class="csv-import-modal__enum-list">
                        {#each enumFieldHints(kind) as hint (hint.key)}
                            <li><code>{hint.key}</code>: {hint.values}</li>
                        {/each}
                    </ul>
                </div>
                <button class="btn btn--secondary btn--small" type="button" on:click={copyTemplate}>
                    {t(kind, 'import.copyFormat')}
                </button>
            </details>

            <!-- Textarea + inline toolbar -->
            <div class="form-group">
                <label for="csv-import-input">{t(kind, 'import.inputLabel')}</label>
                <textarea
                    id="csv-import-input"
                    class="input csv-import-modal__textarea"
                    bind:value={sourceText}
                    rows="5"
                    autocomplete="off"
                    spellcheck="false"
                    placeholder={t(kind, 'import.placeholder')}
                ></textarea>
            </div>

            <div class="csv-import-modal__toolbar">
                <div class="csv-import-modal__toolbar-actions">
                    <button class="btn btn--secondary btn--small" type="button" on:click={pasteFromClipboard}>
                        {t(kind, 'import.paste')}
                    </button>
                    {#if sourceText}
                        <button class="btn btn--secondary btn--small" type="button" on:click={clearText}>
                            {t(kind, 'import.clear')}
                        </button>
                    {/if}
                </div>
                <div class="csv-import-modal__toolbar-meta">
                    {#if helperMessage}
                        <span class="csv-import-modal__success">{helperMessage}</span>
                    {/if}
                    <span class="csv-import-modal__separator">{t(kind, 'import.separator', { separator: parsed.separator })}</span>
                    {#if sourceText}
                        <span class="csv-import-modal__row-count" class:csv-import-modal__row-count--invalid={hasBlockingErrors}>
                            {rowCount} {t(kind, 'import.rowsDetected')}
                        </span>
                    {/if}
                </div>
            </div>

            {#if serverError}
                <p class="csv-import-modal__error">{serverError}</p>
            {/if}

            <!-- Validation errors — only when there's actual input -->
            {#if sourceText && allErrors().length > 0}
                <div class="csv-import-modal__errors" aria-live="polite">
                    <p class="csv-import-modal__errors-title">{t(kind, 'import.errorsTitle')} ({allErrors().length})</p>
                    <ul class="csv-import-modal__errors-list">
                        {#each allErrors() as error, index (index)}
                            <li>row {error.row}: {error.field} — {error.message}</li>
                        {/each}
                    </ul>
                </div>
            {/if}

            <!-- Preview table — only when rows exist -->
            {#if parsed.rows.length > 0}
                <div class="csv-import-modal__preview">
                    <div class="csv-import-modal__meta-label">{t(kind, 'import.previewTitle')} ({Math.min(parsed.rows.length, 4)}/{parsed.rows.length})</div>
                    <div class="csv-import-modal__preview-table-wrap">
                        <table class="csv-import-modal__preview-table">
                            <thead>
                                <tr>
                                    <th>#</th>
                                    {#each schema.columns as column (column.key)}
                                        <th>{column.label}</th>
                                    {/each}
                                </tr>
                            </thead>
                            <tbody>
                                {#each parsed.rows.slice(0, 4) as row (row.rowNumber)}
                                    <tr class:csv-import-modal__preview-row--invalid={row.errors.length > 0}>
                                        <td>{row.rowNumber}</td>
                                        {#each schema.columns as column (column.key)}
                                            <td>{previewValue(row, column.key)}</td>
                                        {/each}
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                </div>
            {/if}
        </div>

        <div class="modal__actions">
            <button class="btn btn--secondary" type="button" on:click={cancel} disabled={pending}>
                {t(kind, 'import.cancel')}
            </button>
            <button class="btn btn--primary" type="button" on:click={submit} disabled={pending || hasBlockingErrors || rowCount === 0}>
                {pending ? t(kind, 'import.submitting') : t(kind, 'import.submit')}
            </button>
        </div>
    </div>
</dialog>
<div class="modal-backdrop" role="button" tabindex="-1" on:click={cancel} on:keydown={handleKeydown}></div>
{/if}

<style>
    .csv-import-modal {
        overscroll-behavior: contain;
    }

    .csv-import-modal__content {
        max-width: 56rem;
        width: min(56rem, calc(100vw - 2rem));
        height: min(46rem, calc(100vh - 2rem));
        display: grid;
        grid-template-rows: auto auto minmax(0, 1fr) auto;
        gap: 0.75rem;
        overflow: hidden;
    }

    .csv-import-modal__body {
        min-height: 0;
        overflow: auto;
        padding-right: 0.2rem;
    }

    .csv-import-modal__header {
        display: flex;
        justify-content: space-between;
        gap: 0.75rem;
        align-items: center;
    }

    .csv-import-modal__header h3 {
        margin: 0;
    }

    .csv-import-modal__description {
        margin: 0 0 0.75rem;
        color: var(--muted-text, #6b7280);
        font-size: 0.9rem;
    }

    .csv-import-modal__switches {
        display: inline-flex;
        gap: 0.35rem;
        flex-shrink: 0;
    }

    .csv-import-modal__switch {
        border: 1px solid rgba(120, 140, 175, 0.22);
        background: rgba(246, 248, 252, 0.94);
        border-radius: 999px;
        padding: 0.32rem 0.65rem;
        font: inherit;
        font-size: 0.85rem;
        cursor: pointer;
    }

    .csv-import-modal__switch--active {
        background: rgba(87, 121, 206, 0.14);
        border-color: rgba(87, 121, 206, 0.3);
    }

    /* CSV illustration */
    .csv-import-modal__illustration {
        display: flex;
        justify-content: center;
        margin-bottom: 0.65rem;
    }

    .csv-import-modal__illustration svg {
        width: 7rem;
        height: auto;
        opacity: 0.85;
    }

    /* Collapsible format reference */
    .csv-import-modal__format {
        margin-bottom: 0.75rem;
        padding: 0.5rem 0.75rem;
        border: 1px solid rgba(120, 140, 175, 0.14);
        border-radius: 0.8rem;
        background: rgba(248, 250, 252, 0.92);
        overflow: hidden;
    }

    .csv-import-modal__format[open] {
        padding-bottom: 0.65rem;
    }

    .csv-import-modal__format-summary {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        cursor: pointer;
        font-size: 0.84rem;
        font-weight: 600;
        color: var(--muted-text, #6b7280);
        padding: 0.15rem 0;
        user-select: none;
    }

    .csv-import-modal__format-summary::marker {
        font-size: 0.75rem;
    }

    .csv-import-modal__format-code {
        margin: 0.45rem 0;
        padding: 0.5rem 0.65rem;
        overflow-x: auto;
        border-radius: 0.6rem;
        background: rgba(15, 23, 42, 0.04);
        font-family: ui-monospace, SFMono-Regular, SF Mono, Consolas, Liberation Mono, monospace;
        font-size: 0.8rem;
        line-height: 1.4;
        white-space: pre;
        word-break: normal;
        overflow-wrap: normal;
        max-width: 100%;
    }

    .csv-import-modal__chips-inline {
        display: flex;
        flex-wrap: wrap;
        gap: 0.25rem;
        flex-shrink: 0;
    }

    .csv-import-modal__chip {
        border-radius: 999px;
        background: rgba(120, 140, 175, 0.1);
        padding: 0.15rem 0.45rem;
        font-size: 0.72rem;
        font-weight: 400;
    }

    .csv-import-modal__chip--required {
        background: rgba(87, 121, 206, 0.14);
        font-weight: 600;
    }

    .csv-import-modal__meta-label {
        font-size: 0.8rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--muted-text, #6b7280);
        margin-bottom: 0.35rem;
    }

    .csv-import-modal__enum-hints {
        margin: 0 0 0.6rem;
    }

    .csv-import-modal__enum-list {
        margin: 0;
        padding-left: 1rem;
        color: var(--muted-text, #4b5563);
        font-size: 0.82rem;
        line-height: 1.45;
    }

    .csv-import-modal__enum-list code {
        font-family: ui-monospace, SFMono-Regular, SF Mono, Consolas, Liberation Mono, monospace;
        font-size: 0.8rem;
    }

    .csv-import-modal__textarea {
        min-height: 8rem;
        max-height: 14rem;
        font-family: ui-monospace, SFMono-Regular, SF Mono, Consolas, Liberation Mono, monospace;
        font-size: 0.88rem;
        resize: vertical;
    }

    .csv-import-modal__toolbar {
        display: flex;
        justify-content: space-between;
        gap: 0.6rem;
        align-items: center;
        margin: 0.35rem 0 0.65rem;
    }

    .csv-import-modal__toolbar-actions {
        display: flex;
        gap: 0.4rem;
    }

    .csv-import-modal__toolbar-meta {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
        justify-content: flex-end;
    }

    .csv-import-modal__separator {
        color: var(--muted-text, #6b7280);
        font-size: 0.8rem;
    }

    .csv-import-modal__row-count {
        font-size: 0.82rem;
        font-weight: 600;
        color: #15803d;
    }

    .csv-import-modal__row-count--invalid {
        color: var(--danger, #d64545);
    }

    .csv-import-modal__success {
        color: #15803d;
        font-size: 0.82rem;
        font-weight: 600;
    }

    .csv-import-modal__error {
        margin: 0 0 0.65rem;
        color: var(--danger, #d64545);
        font-size: 0.88rem;
    }

    .csv-import-modal__errors {
        margin: 0 0 0.65rem;
        padding: 0.55rem 0.75rem;
        border-radius: 0.7rem;
        border: 1px solid rgba(214, 69, 69, 0.18);
        background: rgba(214, 69, 69, 0.07);
        font-size: 0.85rem;
    }

    .csv-import-modal__errors-title {
        margin: 0 0 0.3rem;
        font-weight: 700;
        font-size: 0.82rem;
    }

    .csv-import-modal__errors-list {
        margin: 0;
        padding-left: 1.1rem;
        max-height: 7.5rem;
        overflow: auto;
    }

    .csv-import-modal__errors li {
        margin-bottom: 0.15rem;
    }

    .csv-import-modal__preview {
        margin-bottom: 0.25rem;
    }

    .csv-import-modal__preview-table-wrap {
        overflow: auto;
        max-height: 10rem;
        border: 1px solid rgba(120, 140, 175, 0.14);
        border-radius: 0.7rem;
    }

    .csv-import-modal__preview-table {
        width: max-content;
        min-width: 100%;
        border-collapse: collapse;
        table-layout: auto;
        font-size: 0.85rem;
    }

    .csv-import-modal__preview-table th,
    .csv-import-modal__preview-table td {
        width: max-content;
        min-width: max-content;
        padding: 0.4rem 0.55rem;
        border-bottom: 1px solid rgba(120, 140, 175, 0.1);
        text-align: left;
        vertical-align: top;
        white-space: nowrap;
    }

    .csv-import-modal__preview-table th:first-child,
    .csv-import-modal__preview-table td:first-child {
        min-width: 3rem;
    }

    .csv-import-modal__preview-table th {
        white-space: nowrap;
    }

    .csv-import-modal__preview-row--invalid {
        background: rgba(214, 69, 69, 0.05);
    }

    @media (max-width: 640px) {
        .csv-import-modal__content {
            width: min(calc(100vw - 1rem), 56rem);
            height: min(42rem, calc(100vh - 1rem));
        }

        .csv-import-modal__header,
        .csv-import-modal__toolbar {
            flex-direction: column;
            align-items: stretch;
        }

        .csv-import-modal__toolbar-meta {
            justify-content: flex-start;
        }

        .csv-import-modal__format-summary {
            flex-direction: column;
            align-items: flex-start;
            gap: 0.3rem;
        }
    }
</style>
