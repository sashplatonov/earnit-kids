<script lang="ts">
    import { appStore } from '$lib/stores/app';
    import { adminSaveTask, adminSaveShopItem } from '$lib/services/api';
    import { showToast } from '$lib/stores/toasts';

    $: baseData = $appStore.baseData;
    $: catalogTasks = baseData?.tasks ?? [];
    $: catalogProducts = baseData?.products ?? [];

    let ageMin = 7;
    let ageMax = 18;

    $: filteredTasks = catalogTasks.filter((t: Record<string, unknown>) => {
        const min = (t.ageMin as number) ?? 0;
        const max = (t.ageMax as number) ?? 99;
        return min <= ageMax && max >= ageMin;
    });

    $: filteredProducts = catalogProducts.filter((p: Record<string, unknown>) => {
        const min = (p.ageMin as number) ?? 0;
        const max = (p.ageMax as number) ?? 99;
        return min <= ageMax && max >= ageMin;
    });

    async function addTaskFromCatalog(task: Record<string, unknown>) {
        const res = await adminSaveTask({ ...task, id: undefined }) as Record<string, unknown> | null;
        if (res) {
            const newTask = { ...task, id: res.id ?? Date.now() };
            appStore.setState({ tasks: [...$appStore.tasks, newTask as typeof $appStore.tasks[0]] });
            showToast(`Задание «${task.title}» добавлено`, 'success');
        }
    }

    async function addProductFromCatalog(product: Record<string, unknown>) {
        const res = await adminSaveShopItem({ ...product, id: undefined }) as Record<string, unknown> | null;
        if (res) {
            const newItem = { ...product, id: res.id ?? Date.now() };
            appStore.setState({ shopItems: [...$appStore.shopItems, newItem as typeof $appStore.shopItems[0]] });
            showToast(`Товар «${product.title}» добавлен`, 'success');
        }
    }
</script>

<section id="catalog-section" class="section hidden">
    <div class="container">
        <h2 class="section-title">Общий Каталог</h2>
        <p class="section-subtitle">Выберите задания и товары для вашего магазина</p>

        <div class="filter-card">
            <div class="filter-group">
                <label>
                    Возраст: от <span id="age-min-val">{ageMin}</span>
                    до <span id="age-max-val">{ageMax}</span> лет
                </label>
                <div style="display: flex; gap: 1rem; margin-top: 0.5rem;">
                    <input type="range" id="catalog-age-min-filter" class="age-slider"
                        bind:value={ageMin} min="7" max="18" />
                    <input type="range" id="catalog-age-max-filter" class="age-slider"
                        bind:value={ageMax} min="7" max="18" />
                </div>
            </div>
            <p class="hint">Показываем задания для этого возраста</p>
        </div>

        <div class="catalog-grid">
            <div class="catalog-column">
                <h3>Задания</h3>
                <div id="catalog-tasks-list" class="items-list">
                    {#each filteredTasks as task (task.id)}
                    <div class="catalog-item">
                        <span class="catalog-item__title">{task.title as string}</span>
                        <span class="catalog-item__coins">{task.coins as number} мон.</span>
                        <button class="btn btn--primary btn--small" on:click={() => addTaskFromCatalog(task as Record<string, unknown>)}>
                            + Добавить
                        </button>
                    </div>
                    {/each}
                    {#if filteredTasks.length === 0}
                    <p class="hint">Нет заданий для выбранного возраста.</p>
                    {/if}
                </div>
            </div>
            <div class="catalog-column">
                <h3>Товары</h3>
                <div id="catalog-products-list" class="items-list">
                    {#each filteredProducts as product (product.id)}
                    <div class="catalog-item">
                        <span class="catalog-item__title">{product.title as string}</span>
                        <span class="catalog-item__coins">{product.coins as number} мон.</span>
                        <button class="btn btn--primary btn--small" on:click={() => addProductFromCatalog(product as Record<string, unknown>)}>
                            + Добавить
                        </button>
                    </div>
                    {/each}
                    {#if filteredProducts.length === 0}
                    <p class="hint">Нет товаров для выбранного возраста.</p>
                    {/if}
                </div>
            </div>
        </div>
    </div>
</section>
