<script lang="ts">
    import { useI18n } from '$lib/i18n/context';
    import { getEffectiveGroupOrder, normalizeGroupLabel, orderGroups, sortItemsByGroup } from '$lib/telegram/services/groupOrder';
    import type { MessageKey } from '$lib/i18n';
    import type { PageData } from './$types';

    type PrintChild = {
        id: string | number;
        nickname: string;
        balance: number;
        taskGroupOrder?: string[];
        childTaskGroupOrder?: string[];
    };

    type PrintTask = {
        id: string | number;
        name: string;
        coins: number;
        groupName?: string | null;
        comment?: string | null;
        moneyLimit?: number | null;
        ageMin?: number | null;
        ageMax?: number | null;
        lastCompletedAt?: unknown;
        frequency?: { limit?: number; period?: string } | null;
        isActive?: boolean;
    };

    type CatalogGroup<T> = {
        label: string;
        items: T[];
    };

    type PrintChip = {
        label: string;
        className?: string;
    };

    export let data: PageData;

    const i18n = useI18n();

    $: tasks = data.tasks as unknown as PrintTask[];
    $: children = data.children as unknown as PrintChild[];
    $: currentChild = (children.find((child) => String(child.id) === String(data.childId)) ?? children[0] ?? null) as PrintChild | null;
    $: taskGroups = groupItems(
        tasks,
        orderGroups(
            [...new Set(tasks.map((task) => normalizeGroupLabel(task.groupName)))],
            getEffectiveGroupOrder(currentChild, 'tasks', data.isAdmin),
        ),
        (task) => normalizeGroupLabel(task.groupName),
    );

    function groupItems<T>(items: T[], orderedGroups: string[], getGroupLabel: (item: T) => string): CatalogGroup<T>[] {
        const sorted = sortItemsByGroup(items, orderedGroups, getGroupLabel);
        const groups: CatalogGroup<T>[] = [];

        for (const item of sorted) {
            const label = getGroupLabel(item);
            const existingGroup = groups.find((group) => group.label === label);
            if (existingGroup) {
                existingGroup.items = [...existingGroup.items, item];
            } else {
                groups.push({ label, items: [item] });
            }
        }

        return groups;
    }

    function formatFrequency(frequency: { limit?: number; period?: string } | null | undefined) {
        const limit = frequency?.limit;
        const period = frequency?.period;

        if (!limit || !period) {
            return '';
        }

        const periodMap: Record<string, string> = {
            day: 'frequencyDay',
            week: 'frequencyWeek',
            month: 'frequencyMonth',
            year: 'frequencyYear',
            season: 'frequencySeason',
        };
        const numericLimit = Number(limit);
        const pluralCategory = new Intl.PluralRules($i18n.locale).select(numericLimit);
        const periodKey = periodMap[period];

        if (!periodKey) {
            return $i18n.t(`tasks.frequencyFallback` as MessageKey, { limit: $i18n.formatNumber(numericLimit) });
        }

        return $i18n.t(`tasks.${periodKey}.${pluralCategory}` as MessageKey, { limit: $i18n.formatNumber(numericLimit) });
    }

    function formatLastDate(value: unknown) {
        if (typeof value !== 'string' || !value) {
            return '';
        }

        const parsed = new Date(value);
        return Number.isNaN(parsed.getTime()) ? '' : $i18n.formatShortDate(parsed);
    }

    function taskChips(task: PrintTask): PrintChip[] {
        const chips: PrintChip[] = [
            { label: task.groupName ?? $i18n.t('tasks.section.noGroup') },
        ];

        const frequency = formatFrequency(task.frequency);
        if (frequency) {
            chips.push({ label: frequency });
        }

        if (task.moneyLimit != null) {
            chips.push({
                label: $i18n.t('tasks.section.moneyLimit', { amount: $i18n.formatNumber(task.moneyLimit) }),
                className: 'catalog-card__chip--money',
            });
        }

        if (task.ageMin != null || task.ageMax != null) {
            chips.push({
                label: $i18n.t('tasks.section.ageRange', { min: task.ageMin ?? 0, max: task.ageMax ?? 18 }),
            });
        }

        const date = formatLastDate(task.lastCompletedAt);
        if (date) {
            chips.push({ label: $i18n.t('tasks.section.lastCompleted', { date }) });
        }

        if (task.isActive === false) {
            chips.push({ label: $i18n.t('tasks.section.blocked') });
        }

        return chips;
    }
</script>

<svelte:head>
    <title>{data.metaTitle} | EarnIt Kids</title>
</svelte:head>

<div class="print-page">
    {#if data.childName}
        <header class="print-hero">
            <p class="print-hero__eyebrow">EarnIt Kids</p>
            <h1>{data.childName}</h1>
        </header>
    {/if}

    {#if tasks.length === 0}
        <section class="print-empty">
            <h2>{$i18n.t('common.printCatalog')}</h2>
            <p>{$i18n.t('common.printCatalogEmpty')}</p>
        </section>
    {:else}
        {#if tasks.length > 0}
            <section class="catalog-section">
                <div class="catalog-section__head">
                    <p class="catalog-section__eyebrow">{$i18n.t('common.navigation.tasks')}</p>
                    <h2>{$i18n.t('tasks.section.title')}</h2>
                </div>

                <div class="group-stack">
                    {#each taskGroups as group (group.label)}
                        <section class="group-panel group-panel--tasks">
                            <h3>{group.label}</h3>
                            <div class="card-grid">
                                {#each group.items as task (task.id)}
                                    <article class="catalog-card">
                                        <div class="catalog-card__topline">
                                            <span class="catalog-card__value">+{$i18n.formatNumber(task.coins)}</span>
                                        </div>
                                        <h4>{task.name}</h4>
                                        <div class="catalog-card__chips">
                                            {#each taskChips(task) as chip, chipIndex (`${chip.label}-${chipIndex}`)}
                                                <span class={`catalog-card__chip ${chip.className ?? ''}`}>{chip.label}</span>
                                            {/each}
                                        </div>
                                        <p>{task.comment || $i18n.t('tasks.section.defaultComment')}</p>
                                    </article>
                                {/each}
                            </div>
                        </section>
                    {/each}
                </div>
            </section>
        {/if}
    {/if}
</div>

<style>
    :global(body) {
        margin: 0;
        background:
            radial-gradient(circle at 10% 14%, rgba(31, 41, 55, 0.08) 0 0.22rem, transparent 0.24rem 2.8rem),
            radial-gradient(circle at 84% 18%, rgba(31, 41, 55, 0.08) 0 0.18rem, transparent 0.2rem 2.5rem),
            radial-gradient(circle at 22% 76%, rgba(31, 41, 55, 0.06) 0 0.16rem, transparent 0.18rem 2.7rem),
            linear-gradient(35deg, transparent 49.6%, rgba(31, 41, 55, 0.07) 49.6% 50.4%, transparent 50.4%) 0 0 / 6.5rem 6.5rem,
            linear-gradient(-35deg, transparent 49.6%, rgba(31, 41, 55, 0.07) 49.6% 50.4%, transparent 50.4%) 0 0 / 6.5rem 6.5rem,
            radial-gradient(circle at 50% 50%, rgba(31, 41, 55, 0.05) 0 0.12rem, transparent 0.14rem) 1.2rem 1.5rem / 4.8rem 4.8rem,
            radial-gradient(circle at 50% 50%, rgba(31, 41, 55, 0.05) 0 0.12rem, transparent 0.14rem) 3.6rem 3rem / 5.5rem 5.5rem,
            linear-gradient(180deg, #fffaf0 0%, #fff 32%, #f7f6f2 100%);
        color: #1f2937;
        font-family: "Avenir Next", "Nunito", "Segoe UI", sans-serif;
    }

    .print-page {
        max-width: 72rem;
        margin: 0 auto;
        padding: 1rem 1rem 2rem;
    }

    .print-hero {
        margin-bottom: 0.95rem;
    }

    .print-hero__eyebrow,
    .catalog-section__eyebrow {
        margin: 0 0 0.4rem;
        font-size: 0.78rem;
        font-weight: 700;
        letter-spacing: 0.16em;
        text-transform: uppercase;
        color: #8b5e34;
    }

    .print-hero h1,
    .catalog-section h2,
    .group-panel h3,
    .catalog-card h4 {
        margin: 0;
    }

    .print-hero h1 {
        font-size: clamp(2rem, 4vw, 3rem);
        line-height: 1;
    }

    .print-empty {
        padding: 2rem;
        border: 1px solid rgba(139, 92, 36, 0.18);
        border-radius: 1.5rem;
        background: rgba(255, 255, 255, 0.8);
    }

    .catalog-section + .catalog-section {
        margin-top: 2rem;
    }

    .catalog-section__head {
        margin-bottom: 1rem;
    }

    .catalog-section h2 {
        font-size: clamp(1.55rem, 3vw, 2.2rem);
    }

    .group-stack {
        display: grid;
        gap: 1rem;
    }

    .group-panel {
        border-radius: 1.75rem;
        padding: 1.1rem;
        border: 1px solid rgba(15, 23, 42, 0.08);
        background: rgba(255, 255, 255, 0.86);
        box-shadow: 0 18px 45px rgba(148, 163, 184, 0.15);
        break-inside: avoid;
    }

    .group-panel--tasks {
        background: linear-gradient(180deg, rgba(219, 234, 186, 0.42), rgba(255, 255, 255, 0.94));
    }

    .group-panel h3 {
        margin-bottom: 0.85rem;
        font-size: 1.05rem;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
        gap: 0.9rem;
    }

    .catalog-card {
        border-radius: 1.35rem;
        padding: 1rem;
        border: 1px solid rgba(15, 23, 42, 0.08);
        background: #fff;
        break-inside: avoid;
    }

    .catalog-card__topline {
        display: flex;
        justify-content: flex-end;
        margin-bottom: 0.4rem;
    }

    .catalog-card__value {
        display: inline-flex;
        align-items: center;
        min-height: 2rem;
        padding: 0 0.8rem;
        border-radius: 999px;
        background: #111827;
        color: #fff;
        font-weight: 700;
        font-size: 0.95rem;
    }

    .catalog-card h4 {
        font-size: 1.08rem;
        line-height: 1.2;
    }

    .catalog-card__chips {
        display: flex;
        flex-wrap: wrap;
        gap: 0.45rem;
        margin: 0.8rem 0;
    }

    .catalog-card__chip {
        padding: 0.3rem 0.65rem;
        border-radius: 999px;
        background: #f3f4f6;
        color: #374151;
        font-size: 0.77rem;
        line-height: 1.2;
    }

    .catalog-card__chip--money {
        display: inline-flex;
        align-items: center;
        gap: 0.34rem;
    }

    .catalog-card__chip--money::before {
        content: "";
        width: 0.92em;
        height: 0.68em;
        border-radius: 0.18em;
        flex: none;
        background:
            radial-gradient(circle at 50% 50%, rgba(17, 24, 39, 0.22) 0 18%, transparent 20%),
            linear-gradient(180deg, #dbf5d7 0%, #b8e3b2 100%);
        border: 1px solid rgba(60, 122, 74, 0.45);
        box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.28);
    }

    .catalog-card p {
        margin: 0;
        color: #4b5563;
        line-height: 1.45;
    }

    @media print {
        @page {
            size: A4 portrait;
            margin: 4mm 4mm 5mm;
        }

        :global(body) {
            background:
                linear-gradient(35deg, transparent 49.7%, rgba(17, 24, 39, 0.06) 49.7% 50.3%, transparent 50.3%) 0 0 / 44mm 44mm,
                linear-gradient(-35deg, transparent 49.7%, rgba(17, 24, 39, 0.06) 49.7% 50.3%, transparent 50.3%) 0 0 / 44mm 44mm,
                radial-gradient(circle at 8mm 8mm, rgba(17, 24, 39, 0.12) 0 0.7mm, transparent 0.75mm),
                radial-gradient(circle at 22mm 16mm, rgba(17, 24, 39, 0.1) 0 0.55mm, transparent 0.6mm),
                radial-gradient(circle at 34mm 9mm, rgba(17, 24, 39, 0.08) 0 0.45mm, transparent 0.5mm),
                linear-gradient(180deg, #fff 0%, #fcfcfb 100%);
            color: #111827;
            print-color-adjust: exact;
            -webkit-print-color-adjust: exact;
        }

        .print-page {
            max-width: none;
            padding: 0;
        }

        .print-hero {
            margin-top: 0;
            margin-bottom: 1.8mm;
        }

        .print-hero__eyebrow,
        .catalog-section__eyebrow {
            margin-bottom: 0.05rem;
            font-size: 7.5pt;
            letter-spacing: 0.12em;
        }

        .print-hero h1 {
            font-size: 18pt;
            line-height: 0.96;
        }

        .catalog-section + .catalog-section {
            margin-top: 2.5mm;
        }

        .catalog-section {
            break-inside: auto;
            page-break-inside: auto;
        }

        .catalog-section__head {
            margin-bottom: 1.8mm;
            break-after: avoid;
            page-break-after: avoid;
        }

        .catalog-section h2 {
            font-size: 14pt;
        }

        .group-stack {
            gap: 1.7mm;
        }

        .group-panel {
            break-inside: auto;
            page-break-inside: auto;
            border-radius: 3.5mm;
            padding: 1.9mm 2mm 2.1mm;
            box-shadow: none;
            background: #fff;
        }

        .group-panel h3 {
            margin-bottom: 1.5mm;
            font-size: 9.8pt;
            break-after: avoid;
            page-break-after: avoid;
        }

        .card-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 1.8mm;
            align-items: start;
        }

        .catalog-card {
            break-inside: avoid;
            page-break-inside: avoid;
            box-shadow: none;
        }

        .catalog-card {
            min-height: 0;
            border-radius: 3mm;
            padding: 2.1mm;
        }

        .catalog-card__topline {
            margin-bottom: 0.8mm;
        }

        .catalog-card__value {
            min-height: 6mm;
            padding: 0 2.1mm;
            font-size: 8.8pt;
        }

        .catalog-card h4 {
            font-size: 9.4pt;
            line-height: 1.16;
        }

        .catalog-card__chips {
            gap: 0.9mm;
            margin: 1.4mm 0 1.5mm;
        }

        .catalog-card__chip {
            padding: 0.7mm 1.4mm;
            font-size: 6.7pt;
        }

        .catalog-card p {
            font-size: 7.4pt;
            line-height: 1.2;
            orphans: 2;
            widows: 2;
        }

        .print-empty {
            padding: 7mm;
            border-radius: 5mm;
        }

        .print-empty h2 {
            font-size: 14pt;
        }

        .print-empty p {
            font-size: 9pt;
        }

        .group-panel--tasks {
            background: #fff;
        }

        .catalog-card__chip--money::before {
            print-color-adjust: exact;
            -webkit-print-color-adjust: exact;
        }

        .group-panel--tasks {
            border-color: rgba(112, 145, 57, 0.35);
        }
    }
</style>
