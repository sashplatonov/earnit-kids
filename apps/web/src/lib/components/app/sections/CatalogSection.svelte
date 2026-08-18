<script lang="ts">
    import type { MessageKey } from '$lib/i18n';
    import { useI18n } from '$lib/i18n/context';
    import type { Task } from '$lib/stores/app';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { showToast } from '$lib/stores/toasts';

    const i18n = useI18n();

    function tAdmin(key: string, variables?: Record<string, string | number>): string {
        return $i18n.t(`admin.${key}` as MessageKey, variables);
    }

    $: baseData = $appStore.baseData;
    $: catalogTasks = (baseData?.tasks ?? []) as Task[];

    let ageMin = 7;
    let ageMax = 18;

    $: filteredTasks = catalogTasks.filter((task) => {
        const min = Number(task.ageMin ?? 0);
        const max = Number(task.ageMax ?? 99);
        return min <= ageMax && max >= ageMin;
    });

    function addTaskFromCatalog(task: Task) {
        const newTask = { ...task, id: Date.now() };
        appStore.setState({ tasks: [...$appStore.tasks, newTask as typeof $appStore.tasks[0]] });
        void scheduleSave();
        showToast(tAdmin('catalog.taskAdded', { name: task.name }), 'success');
    }

    function formatAgeLabel(item: { ageMin?: number | null; ageMax?: number | null }) {
        return tAdmin('catalog.ageRange', {
            min: item.ageMin ?? 0,
            max: item.ageMax ?? 18,
        });
    }

    function formatFrequencyLabel(frequency: { limit?: number; period?: string } | null | undefined) {
        if (!frequency?.limit) return '';
        const periodMap: Record<string, string> = {
            day: 'frequencyDay',
            week: 'frequencyWeek',
            month: 'frequencyMonth',
            year: 'frequencyYear',
            season: 'frequencySeason',
        };
        const limit = Number(frequency.limit);
        const pluralCategory = new Intl.PluralRules($i18n.locale).select(limit);
        const periodKey = periodMap[frequency.period ?? 'week'];

        if (!periodKey) {
            return tAdmin('catalog.frequencyFallback', { limit: $i18n.formatNumber(limit) });
        }

        return tAdmin(`catalog.${periodKey}.${pluralCategory}`, { limit: $i18n.formatNumber(limit) });
    }

    function moneyLimitLabel(value: number | null | undefined) {
        return value != null && value > 0
            ? tAdmin('catalog.moneyLimit', { amount: $i18n.formatNumber(value) })
            : '';
    }
</script>

<section id="catalog-section" class="section catalog-section">
    <div class="container">
        <div class="catalog-hero">
            <div>
                <p class="catalog-hero__eyebrow">{tAdmin('catalog.eyebrow')}</p>
                <h2 class="section-title">{tAdmin('catalog.title')}</h2>
                <p class="section-subtitle">{tAdmin('catalog.subtitle')}</p>
            </div>
            <div class="catalog-hero__summary" aria-hidden="true">
                <div class="catalog-hero__summary-card">
                    <span>{tAdmin('catalog.tasksCount')}</span>
                    <strong>{$i18n.formatNumber(filteredTasks.length)}</strong>
                </div>
            </div>
        </div>

        <div class="filter-card">
            <div class="filter-card__header">
                <div class="filter-group">
                    <label for="catalog-age-min-filter">{tAdmin('catalog.filterLabel', { min: ageMin, max: ageMax })}</label>
                    <p class="hint">{tAdmin('catalog.filterHint')}</p>
                </div>
                <div class="catalog-pill-row" aria-hidden="true">
                    <span class="catalog-pill">{tAdmin('catalog.liveCatalog')}</span>
                    <span class="catalog-pill">{tAdmin('catalog.quickAdd')}</span>
                </div>
            </div>
            <div class="slider-container" style="max-width: 100%;">
                <div class="catalog-slider-row">
                    <input type="range" id="catalog-age-min-filter" class="age-slider"
                        bind:value={ageMin} min="7" max="18" />
                    <input type="range" id="catalog-age-max-filter" class="age-slider"
                        bind:value={ageMax} min="7" max="18" />
                </div>
            </div>
        </div>

        <div class="catalog-grid">
            <article class="catalog-column">
                <div class="catalog-column__header">
                    <div>
                        <p class="catalog-column__eyebrow">{tAdmin('catalog.tasksEyebrow')}</p>
                        <h3>{tAdmin('catalog.tasksTitle')}</h3>
                    </div>
                    <span class="catalog-column__count">{$i18n.formatNumber(filteredTasks.length)}</span>
                </div>
                <div id="catalog-tasks-list" class="catalog-stack">
                    {#each filteredTasks as task (task.id)}
                    <article class="catalog-card catalog-card--task">
                        <div class="catalog-card__body">
                            <div class="catalog-card__headline">
                                <div>
                                    <p class="catalog-card__group">{task.groupName ?? tAdmin('catalog.defaultTaskGroup')}</p>
                                    <h4 class="catalog-card__title">{task.name}</h4>
                                </div>
                                <div class="catalog-card__price">
                                    <strong>{$i18n.formatNumber(task.coins ?? 0)}</strong>
                                    <span>{tAdmin('catalog.coinsUnit')}</span>
                                </div>
                            </div>

                            {#if task.comment}
                            <p class="catalog-card__description">{task.comment}</p>
                            {/if}

                            <div class="catalog-card__meta">
                                <span class="catalog-meta-chip">{formatAgeLabel(task)}</span>
                                {#if formatFrequencyLabel(task.frequency)}
                                <span class="catalog-meta-chip">{formatFrequencyLabel(task.frequency)}</span>
                                {/if}
                                {#if moneyLimitLabel(task.moneyLimit)}
                                <span class="catalog-meta-chip">{moneyLimitLabel(task.moneyLimit)}</span>
                                {/if}
                            </div>
                        </div>

                        <button class="btn btn--primary btn--small catalog-card__action" type="button"
                            on:click={() => addTaskFromCatalog(task)}>
                            {tAdmin('catalog.addTask')}
                        </button>
                    </article>
                    {/each}
                    {#if filteredTasks.length === 0}
                    <p class="hint">{tAdmin('catalog.noTasks')}</p>
                    {/if}
                </div>
            </article>
        </div>
    </div>
</section>
