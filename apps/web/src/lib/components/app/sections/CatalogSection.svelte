<script lang="ts">
    import type { ShopItem, Task } from '$lib/stores/app';
    import { appStore } from '$lib/stores/app';
    import { scheduleSave } from '$lib/services/save';
    import { showToast } from '$lib/stores/toasts';

    $: baseData = $appStore.baseData;
    $: catalogTasks = (baseData?.tasks ?? []) as Task[];
    $: catalogProducts = (baseData?.products ?? []) as ShopItem[];

    let ageMin = 7;
    let ageMax = 18;

    $: filteredTasks = catalogTasks.filter((task) => {
        const min = Number(task.ageMin ?? 0);
        const max = Number(task.ageMax ?? 99);
        return min <= ageMax && max >= ageMin;
    });

    $: filteredProducts = catalogProducts.filter((product) => {
        const min = Number(product.ageMin ?? 0);
        const max = Number(product.ageMax ?? 99);
        return min <= ageMax && max >= ageMin;
    });

    function addTaskFromCatalog(task: Task) {
        const newTask = { ...task, id: Date.now() };
        appStore.setState({ tasks: [...$appStore.tasks, newTask as typeof $appStore.tasks[0]] });
        void scheduleSave();
        showToast(`Задание «${task.name}» добавлено`, 'success');
    }

    function addProductFromCatalog(product: ShopItem) {
        const newItem = { ...product, id: Date.now() };
        appStore.setState({ shopItems: [...$appStore.shopItems, newItem as typeof $appStore.shopItems[0]] });
        void scheduleSave();
        showToast(`Товар «${product.name}» добавлен`, 'success');
    }

    function formatAgeLabel(item: { ageMin?: number | null; ageMax?: number | null }) {
        const min = item.ageMin ?? 0;
        const max = item.ageMax ?? 18;
        return `${min}-${max} лет`;
    }

    function formatFrequencyLabel(frequency: { limit?: number; period?: string } | null | undefined) {
        if (!frequency?.limit) return '';

        const periodMap: Record<string, string> = {
            day: 'в день',
            week: 'в неделю',
            month: 'в месяц',
            year: 'в год',
        };

        return `${frequency.limit} ${periodMap[frequency.period ?? 'week'] ?? 'за период'}`;
    }

    function moneyLimitLabel(value: number | null | undefined) {
        return value != null && value > 0 ? `До ${value} EUR` : '';
    }
</script>

<section id="catalog-section" class="section catalog-section">
    <div class="container">
        <div class="catalog-hero">
            <div>
                <p class="catalog-hero__eyebrow">Подборка для семьи</p>
                <h2 class="section-title">Общий каталог</h2>
                <p class="section-subtitle">Выбирайте готовые задания и товары с понятными условиями и возрастными рамками.</p>
            </div>
            <div class="catalog-hero__summary" aria-hidden="true">
                <div class="catalog-hero__summary-card">
                    <span>Заданий</span>
                    <strong>{filteredTasks.length}</strong>
                </div>
                <div class="catalog-hero__summary-card">
                    <span>Товаров</span>
                    <strong>{filteredProducts.length}</strong>
                </div>
            </div>
        </div>

        <div class="filter-card">
            <div class="filter-card__header">
                <div class="filter-group">
                    <label for="catalog-age-min-filter">
                        Возрастной диапазон: от <span id="age-min-val">{ageMin}</span>
                        до <span id="age-max-val">{ageMax}</span> лет
                    </label>
                    <p class="hint">Показываем только те позиции, которые подходят ребёнку по возрасту.</p>
                </div>
                <div class="catalog-pill-row" aria-hidden="true">
                    <span class="catalog-pill">Живой каталог</span>
                    <span class="catalog-pill">Быстрое добавление</span>
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
                        <p class="catalog-column__eyebrow">Каталог задач</p>
                        <h3>Задания</h3>
                    </div>
                    <span class="catalog-column__count">{filteredTasks.length}</span>
                </div>
                <div id="catalog-tasks-list" class="catalog-stack">
                    {#each filteredTasks as task (task.id)}
                    <article class="catalog-card catalog-card--task">
                        <div class="catalog-card__body">
                            <div class="catalog-card__headline">
                                <div>
                                    <p class="catalog-card__group">{task.groupName ?? 'Задание'}</p>
                                    <h4 class="catalog-card__title">{task.name}</h4>
                                </div>
                                <div class="catalog-card__price">
                                    <strong>{task.coins}</strong>
                                    <span>монет</span>
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
                            Добавить задание
                        </button>
                    </article>
                    {/each}
                    {#if filteredTasks.length === 0}
                    <p class="hint">Нет заданий для выбранного возраста.</p>
                    {/if}
                </div>
            </article>
            <article class="catalog-column">
                <div class="catalog-column__header">
                    <div>
                        <p class="catalog-column__eyebrow">Каталог наград</p>
                        <h3>Товары</h3>
                    </div>
                    <span class="catalog-column__count">{filteredProducts.length}</span>
                </div>
                <div id="catalog-products-list" class="catalog-stack">
                    {#each filteredProducts as product (product.id)}
                    <article class="catalog-card catalog-card--product">
                        <div class="catalog-card__body">
                            <div class="catalog-card__headline">
                                <div>
                                    <p class="catalog-card__group">{product.groupName ?? 'Товар'}</p>
                                    <h4 class="catalog-card__title">{product.name}</h4>
                                </div>
                                <div class="catalog-card__price">
                                    <strong>{product.price}</strong>
                                    <span>монет</span>
                                </div>
                            </div>

                            {#if product.comment}
                            <p class="catalog-card__description">{product.comment}</p>
                            {/if}

                            <div class="catalog-card__meta">
                                <span class="catalog-meta-chip">{formatAgeLabel(product)}</span>
                                {#if moneyLimitLabel(product.moneyLimit)}
                                <span class="catalog-meta-chip">{moneyLimitLabel(product.moneyLimit)}</span>
                                {/if}
                            </div>
                        </div>

                        <button class="btn btn--primary btn--small catalog-card__action" type="button"
                            on:click={() => addProductFromCatalog(product)}>
                            Добавить товар
                        </button>
                    </article>
                    {/each}
                    {#if filteredProducts.length === 0}
                    <p class="hint">Нет товаров для выбранного возраста.</p>
                    {/if}
                </div>
            </article>
        </div>
    </div>
</section>
