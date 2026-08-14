export type CsvImportKind = 'tasks' | 'shop';

export type CsvImportColumn = {
    key: string;
    label: string;
    required: boolean;
    example?: string;
};

export type CsvImportSchema = {
    kind: CsvImportKind;
    title: string;
    columns: CsvImportColumn[];
};

export type CsvImportValidationError = {
    row: number;
    field: string;
    message: string;
};

export type CsvImportPreviewRow = {
    rowNumber: number;
    values: Record<string, string>;
    normalized: Record<string, unknown> | null;
    errors: CsvImportValidationError[];
};

export type CsvImportParseResult = {
    kind: CsvImportKind;
    separator: ',' | ';';
    headers: string[];
    rows: CsvImportPreviewRow[];
    normalizedRows: Array<Record<string, unknown>>;
    errors: CsvImportValidationError[];
};

type CsvRecord = {
    lineNumber: number;
    text: string;
};

const SHOP_IMPORT_TYPES = new Set(['micro', 'small', 'large']);

const TASK_COLUMNS: CsvImportColumn[] = [
    { key: 'title', label: 'title', required: true, example: 'Wash dishes' },
    { key: 'coins', label: 'coins', required: true, example: '10' },
    { key: 'groupName', label: 'groupName', required: false },
    { key: 'comment', label: 'comment', required: false },
    { key: 'frequencyLimit', label: 'frequencyLimit', required: false },
    { key: 'frequencyPeriod', label: 'frequencyPeriod', required: false },
    { key: 'moneyLimit', label: 'moneyLimit', required: false },
    { key: 'icon', label: 'icon', required: false },
    { key: 'isActive', label: 'isActive', required: false },
];

const SHOP_COLUMNS: CsvImportColumn[] = [
    { key: 'name', label: 'name', required: true, example: 'Tablet time' },
    { key: 'price', label: 'price', required: true, example: '50' },
    { key: 'groupName', label: 'groupName', required: false },
    { key: 'comment', label: 'comment', required: false },
    { key: 'frequencyLimit', label: 'frequencyLimit', required: false },
    { key: 'frequencyPeriod', label: 'frequencyPeriod', required: false },
    { key: 'moneyLimit', label: 'moneyLimit', required: false },
    { key: 'type', label: 'type', required: false },
    { key: 'icon', label: 'icon', required: false },
    { key: 'isActive', label: 'isActive', required: false },
];

export const CSV_IMPORT_SCHEMAS: Record<CsvImportKind, CsvImportSchema> = {
    tasks: {
        kind: 'tasks',
        title: 'tasks',
        columns: TASK_COLUMNS,
    },
    shop: {
        kind: 'shop',
        title: 'shop',
        columns: SHOP_COLUMNS,
    },
};

function escapeCsvCell(value: string): string {
    if (/[",\n;]/.test(value)) {
        return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
}

export function buildCsvTemplate(kind: CsvImportKind, separator: ',' | ';' = ','): string {
    const schema = CSV_IMPORT_SCHEMAS[kind];
    const header = schema.columns.map((column) => column.label).join(separator);
    const sample = schema.columns.map((column) => escapeCsvCell(column.example ?? '')).join(separator);
    return `${header}\n${sample}`;
}

function normalizeHeader(header: string): string {
    return normalizeCsvToken(header).toLowerCase();
}

function trimCell(value: string): string {
    return value.trim();
}

/** Accept values copied from Markdown tables or formatted notes, e.g. **"true"**. */
function normalizeCsvToken(value: string): string {
    let normalized = value.trim().replace(/^\uFEFF/, '');
    if (normalized.startsWith('**') && normalized.endsWith('**')) {
        normalized = normalized.slice(2, -2).trim();
    }
    if (normalized.length >= 2 && normalized.startsWith('"') && normalized.endsWith('"')) {
        normalized = normalized.slice(1, -1).trim();
    }
    return normalized;
}

function splitCsvLine(line: string, separator: ',' | ';'): string[] {
    const cells: string[] = [];
    let current = '';
    let quoted = false;

    for (let index = 0; index < line.length; index += 1) {
        const char = line[index];
        const next = line[index + 1];

        if (char === '"') {
            if (quoted && next === '"') {
                current += '"';
                index += 1;
                continue;
            }

            quoted = !quoted;
            continue;
        }

        if (char === separator && !quoted) {
            cells.push(current);
            current = '';
            continue;
        }

        current += char;
    }

    cells.push(current);
    return cells.map(trimCell);
}

function splitCsvRecords(text: string): CsvRecord[] {
    const normalizedText = text
        .replace(/\r\n/g, '\n')
        .replace(/\r/g, '\n');

    const records: CsvRecord[] = [];
    let current = '';
    let quoted = false;
    let lineNumber = 1;
    let recordLineNumber = 1;

    for (let index = 0; index < normalizedText.length; index += 1) {
        const char = normalizedText[index];
        const next = normalizedText[index + 1];

        if (char === '"') {
            current += char;
            if (quoted && next === '"') {
                current += next;
                index += 1;
                continue;
            }

            quoted = !quoted;
            continue;
        }

        if (char === '\n' && !quoted) {
            const record = current.trimEnd();
            if (record.trim().length > 0) {
                records.push({
                    lineNumber: recordLineNumber,
                    text: record,
                });
            }
            current = '';
            lineNumber += 1;
            recordLineNumber = lineNumber;
            continue;
        }

        current += char;
        if (char === '\n') {
            lineNumber += 1;
        }
    }

    const record = current.trimEnd();
    if (record.trim().length > 0) {
        records.push({
            lineNumber: recordLineNumber,
            text: record,
        });
    }

    return records;
}

function detectSeparator(headerLine: string): ',' | ';' {
    const commaCount = (headerLine.match(/,/g) ?? []).length;
    const semicolonCount = (headerLine.match(/;/g) ?? []).length;
    return semicolonCount > commaCount ? ';' : ',';
}

function parseNumber(value: string): number | null {
    if (!value) {
        return null;
    }

    const normalized = Number(value.replace(',', '.'));
    return Number.isFinite(normalized) ? normalized : null;
}

function parseBoolean(value: string): boolean | null {
    const normalized = normalizeCsvToken(value).toLowerCase();
    if (!normalized) {
        return null;
    }

    if (['1', 'true', 'yes', 'y', 'да'].includes(normalized)) return true;
    if (['0', 'false', 'no', 'n', 'нет'].includes(normalized)) return false;
    return null;
}

function parseFrequencyPeriod(value: string): string | null {
    const normalized = normalizeCsvToken(value).toLowerCase();
    return ['day', 'week', 'month', 'year', 'season'].includes(normalized) ? normalized : null;
}

function parseShopType(value: string): string | null {
    if (!value) {
        return null;
    }

    const normalized = value.trim().toLowerCase();
    return SHOP_IMPORT_TYPES.has(normalized) ? normalized : null;
}

function buildRowValues(headers: string[], cells: string[]): Record<string, string> {
    return headers.reduce<Record<string, string>>((acc, header, index) => {
        acc[normalizeHeader(header)] = cells[index] ?? '';
        return acc;
    }, {});
}

function alignCsvCells(kind: CsvImportKind, headers: string[], cells: string[], separator: ',' | ';'): string[] {
    const expectedColumnCount = CSV_IMPORT_SCHEMAS[kind].columns.length;
    if (cells.length <= expectedColumnCount) {
        return cells;
    }

    // Recover common hand-written CSV where only the free-text comment contains
    // an unquoted separator. Quoted CSV is unaffected because it already yields
    // the expected number of cells.
    const commentIndex = headers.findIndex((header) => normalizeHeader(header) === 'comment');
    if (commentIndex < 0) {
        return cells;
    }

    const overflow = cells.length - expectedColumnCount;
    return [
        ...cells.slice(0, commentIndex),
        cells.slice(commentIndex, commentIndex + overflow + 1).join(`${separator} `),
        ...cells.slice(commentIndex + overflow + 1),
    ];
}

function parseTaskRow(rowNumber: number, values: Record<string, string>) {
    const title = values.title ?? '';
    const coins = parseNumber(values.coins ?? '');
    const frequencyLimit = parseNumber(values.frequencylimit ?? '');
    const moneyLimit = parseNumber(values.moneylimit ?? '');
    const isActive = parseBoolean(values.isactive ?? '');
    const normalized = {
        rowNumber,
        title,
        coins: coins == null ? null : Math.trunc(coins),
        groupName: values.groupname || null,
        comment: values.comment || null,
        frequencyLimit: frequencyLimit == null ? null : Math.trunc(frequencyLimit),
        frequencyPeriod: parseFrequencyPeriod(values.frequencyperiod ?? ''),
        moneyLimit: moneyLimit == null ? null : Math.trunc(moneyLimit),
        icon: values.icon || null,
        isActive,
    };

    return normalized;
}

function parseShopRow(rowNumber: number, values: Record<string, string>) {
    const price = parseNumber(values.price ?? '');
    const frequencyLimit = parseNumber(values.frequencylimit ?? '');
    const moneyLimit = parseNumber(values.moneylimit ?? '');
    const isActive = parseBoolean(values.isactive ?? '');
    return {
        rowNumber,
        name: values.name ?? '',
        price: price == null ? null : Math.trunc(price),
        groupName: values.groupname || null,
        comment: values.comment || null,
        frequencyLimit: frequencyLimit == null ? null : Math.trunc(frequencyLimit),
        frequencyPeriod: parseFrequencyPeriod(values.frequencyperiod ?? ''),
        moneyLimit: moneyLimit == null ? null : Math.trunc(moneyLimit),
        type: parseShopType(values.type ?? ''),
        icon: values.icon || null,
        isActive,
    };
}

function rowHasErrors(row: CsvImportPreviewRow): boolean {
    return row.errors.length > 0;
}

export function parseCsvImport(kind: CsvImportKind, text: string): CsvImportParseResult {
    const schema = CSV_IMPORT_SCHEMAS[kind];
    const records = splitCsvRecords(text);
    if (records.length === 0) {
        return {
            kind,
            separator: ',',
            headers: [],
            rows: [],
            normalizedRows: [],
            errors: [{ row: 0, field: 'rows', message: 'CSV is empty' }],
        };
    }

    const separator = detectSeparator(records[0].text);
    const headers = splitCsvLine(records[0].text, separator);
    const normalizedHeaders = headers.map(normalizeHeader);
    const requiredHeaders = schema.columns.filter((column) => column.required).map((column) => column.key);
    const missingHeaders = requiredHeaders.filter((key) => !normalizedHeaders.includes(key));

    const errors: CsvImportValidationError[] = missingHeaders.map((field) => ({
        row: 0,
        field,
        message: `Missing required column: ${field}`,
    }));

    const rows: CsvImportPreviewRow[] = [];
    const normalizedRows: Array<Record<string, unknown>> = [];
    const seenKeys = new Set<string>();

    for (let recordIndex = 1; recordIndex < records.length; recordIndex += 1) {
        const record = records[recordIndex];
        const rawCells = alignCsvCells(kind, headers, splitCsvLine(record.text, separator), separator);
        const values = buildRowValues(headers, rawCells);
        const rowNumber = record.lineNumber;
        const rowErrors: CsvImportValidationError[] = [];

        if (kind === 'tasks') {
            const normalized = parseTaskRow(rowNumber, values);
            if (!normalized.title.trim()) {
                rowErrors.push({ row: rowNumber, field: 'title', message: 'title is required' });
            }
            if (normalized.coins == null || normalized.coins <= 0) {
                rowErrors.push({ row: rowNumber, field: 'coins', message: 'coins must be positive' });
            }
            if (normalized.frequencyLimit != null && normalized.frequencyLimit <= 0) {
                rowErrors.push({ row: rowNumber, field: 'frequencyLimit', message: 'frequencyLimit must be positive' });
            }
            if (normalized.moneyLimit != null && normalized.moneyLimit < 0) {
                rowErrors.push({ row: rowNumber, field: 'moneyLimit', message: 'moneyLimit must not be negative' });
            }
            const duplicateKey = normalized.title.trim().toLowerCase();
            if (seenKeys.has(duplicateKey)) {
                rowErrors.push({ row: rowNumber, field: 'title', message: 'duplicate title' });
            }
            seenKeys.add(duplicateKey);

            rows.push({
                rowNumber,
                values,
                normalized,
                errors: rowErrors,
            });
            if (!rowHasErrors(rows[rows.length - 1])) {
                normalizedRows.push(normalized);
            } else {
                errors.push(...rowErrors);
            }
            continue;
        }

        const normalized = parseShopRow(rowNumber, values);
        if (!normalized.name.trim()) {
            rowErrors.push({ row: rowNumber, field: 'name', message: 'name is required' });
        }
        if (normalized.price == null || normalized.price <= 0) {
            rowErrors.push({ row: rowNumber, field: 'price', message: 'price must be positive' });
        }
        if (normalized.frequencyLimit != null && normalized.frequencyLimit <= 0) {
            rowErrors.push({ row: rowNumber, field: 'frequencyLimit', message: 'frequencyLimit must be positive' });
        }
        if (normalized.moneyLimit != null && normalized.moneyLimit < 0) {
            rowErrors.push({ row: rowNumber, field: 'moneyLimit', message: 'moneyLimit must not be negative' });
        }
        const duplicateKey = normalized.name.trim().toLowerCase();
        if (seenKeys.has(duplicateKey)) {
            rowErrors.push({ row: rowNumber, field: 'name', message: 'duplicate name' });
        }
        seenKeys.add(duplicateKey);

        rows.push({
            rowNumber,
            values,
            normalized,
            errors: rowErrors,
        });
        if (!rowHasErrors(rows[rows.length - 1])) {
            normalizedRows.push(normalized);
        } else {
            errors.push(...rowErrors);
        }
    }

    return {
        kind,
        separator,
        headers,
        rows,
        normalizedRows,
        errors,
    };
}
